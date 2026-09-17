package com.efremushkin.magnetharbor.data.source

import android.util.Xml
import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult
import com.efremushkin.magnetharbor.data.model.fromSourceValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

interface TestableTorrentSource : TorrentSource {
    suspend fun testConnection(): SourceHealth
}

object TorrentSourceFactory {
    fun create(config: SearchSourceConfig): TestableTorrentSource = when (config.kind) {
        SourceKind.TORZNAB -> TorznabTorrentSource(config)
        SourceKind.PROWLARR -> ProwlarrTorrentSource(config)
    }
}

private abstract class HttpTorrentSource(
    protected val config: SearchSourceConfig,
) : TestableTorrentSource {
    override val id: String = config.id
    override val displayName: String = config.name

    protected suspend fun request(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json, application/xml, text/xml;q=0.9")
                headers.forEach(::setRequestProperty)
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                check(code in 200..299) { "HTTP $code${body.takeIf { it.isNotBlank() }?.let { ": ${it.take(120)}" }.orEmpty()}" }
                body
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun testConnection(): SourceHealth {
        val started = System.nanoTime()
        return runCatching { performHealthCheck() }
            .fold(
                onSuccess = {
                    SourceHealth(id, displayName, true, (System.nanoTime() - started) / 1_000_000, "Connected")
                },
                onFailure = {
                    SourceHealth(id, displayName, false, null, it.message ?: "Connection failed")
                },
            )
    }

    protected abstract suspend fun performHealthCheck()

    protected fun encoded(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}

/** Generic Torznab adapter. Point it at a full Torznab endpoint or a service root. */
private class TorznabTorrentSource(config: SearchSourceConfig) : HttpTorrentSource(config) {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        val offset = ((page - 1).coerceAtLeast(0) * 100)
        return parseTorznab(request(searchUrl(query, offset)))
    }

    override suspend fun performHealthCheck() {
        request(searchUrl("magnet-harbor-health", 0))
    }

    private fun searchUrl(query: String, offset: Int): String {
        val root = config.endpoint.trim().removeSuffix("/")
        require(root.startsWith("https://") || root.startsWith("http://")) { "Endpoint must start with http:// or https://" }
        val endpoint = when {
            root.contains("?") -> root
            root.endsWith("/api", true) -> root
            root.contains("/api/", true) -> root
            else -> "$root/api"
        }
        val separator = if (endpoint.contains("?")) "&" else "?"
        val key = config.apiKey.trim().takeIf { it.isNotEmpty() }?.let { "&apikey=${encoded(it)}" }.orEmpty()
        return "$endpoint${separator}t=search&q=${encoded(query)}&limit=100&offset=$offset$key"
    }

    private fun parseTorznab(xml: String): List<TorrentResult> {
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val results = mutableListOf<TorrentResult>()
        var event = parser.eventType
        var item: MutableMap<String, String>? = null
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "item" -> item = mutableMapOf()
                    "title", "guid", "link", "pubdate", "category" -> {
                        item?.set(parser.name.lowercase(), parser.nextText())
                    }
                    "enclosure" -> item?.set("enclosure", parser.getAttributeValue(null, "url").orEmpty())
                    "attr" -> if (item != null) {
                        val name = parser.getAttributeValue(null, "name").orEmpty().lowercase()
                        item?.set(name, parser.getAttributeValue(null, "value").orEmpty())
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("item", true)) {
                    item?.toTorrentResult(displayName)?.let(results::add)
                    item = null
                }
            }
            event = parser.next()
        }
        return results
    }
}

/** Prowlarr's documented API search response adapter. */
private class ProwlarrTorrentSource(config: SearchSourceConfig) : HttpTorrentSource(config) {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        val offset = ((page - 1).coerceAtLeast(0) * 100)
        val response = request(searchUrl(query, offset), authHeaders())
        return parseProwlarr(response)
    }

    override suspend fun performHealthCheck() {
        request("${config.endpoint.trim().removeSuffix("/")}/api/v1/health", authHeaders())
    }

    private fun searchUrl(query: String, offset: Int): String {
        val root = config.endpoint.trim().removeSuffix("/")
        require(root.startsWith("https://") || root.startsWith("http://")) { "Endpoint must start with http:// or https://" }
        return "$root/api/v1/search?query=${encoded(query)}&type=search&limit=100&offset=$offset"
    }

    private fun authHeaders(): Map<String, String> = config.apiKey.trim()
        .takeIf { it.isNotEmpty() }
        ?.let { mapOf("X-Api-Key" to it) }
        ?: emptyMap()

    private fun parseProwlarr(json: String): List<TorrentResult> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val magnet = item.firstString("magnetUrl", "downloadUrl", "guid", "link") ?: continue
                if (!magnet.startsWith("magnet:?")) continue
                add(
                    TorrentResult(
                        title = item.optString("title").ifBlank { "Untitled result" },
                        magnetUri = magnet,
                        source = item.optString("indexer").ifBlank { displayName },
                        sizeBytes = item.optLongOrNull("size"),
                        seeders = item.optIntOrNull("seeders"),
                        leechers = item.optIntOrNull("leechers"),
                        category = item.categoryFromJson(),
                        publishedAtEpochMillis = item.optEpochMillis("publishDate", "publishedDate"),
                    ),
                )
            }
        }
    }
}

private fun MutableMap<String, String>.toTorrentResult(source: String): TorrentResult? {
    val magnet = firstNotBlank("magneturl", "magneturi", "enclosure", "guid", "link") ?: return null
    if (!magnet.startsWith("magnet:?")) return null
    return TorrentResult(
        title = get("title").orEmpty().ifBlank { "Untitled result" },
        magnetUri = magnet,
        source = source,
        sizeBytes = firstNotBlank("size")?.toLongOrNull(),
        seeders = firstNotBlank("seeders")?.toIntOrNull(),
        leechers = firstNotBlank("peers", "leechers")?.toIntOrNull(),
        category = TorrentCategory.fromSourceValue(firstNotBlank("category")),
        publishedAtEpochMillis = firstNotBlank("pubdate")?.let(::parseEpochMillis),
    )
}

private fun MutableMap<String, String>.firstNotBlank(vararg names: String): String? = names
    .asSequence().mapNotNull { get(it)?.takeIf(String::isNotBlank) }.firstOrNull()

private fun JSONObject.firstString(vararg names: String): String? = names
    .asSequence().map { optString(it).trim() }.firstOrNull { it.isNotEmpty() }

private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
private fun JSONObject.optIntOrNull(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optEpochMillis(vararg names: String): Long? = names.asSequence()
    .map { optString(it) }.firstOrNull { it.isNotBlank() }?.let(::parseEpochMillis)

private fun JSONObject.categoryFromJson(): TorrentCategory {
    val categories = optJSONArray("categories")
    val text = buildString {
        append(optString("category"))
        for (i in 0 until (categories?.length() ?: 0)) append(' ').append(categories?.opt(i))
    }
    return TorrentCategory.fromSourceValue(text)
}

private fun parseEpochMillis(value: String): Long? = runCatching {
    value.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1_000 else it }
        ?: Instant.parse(value).toEpochMilli()
}.getOrNull()
