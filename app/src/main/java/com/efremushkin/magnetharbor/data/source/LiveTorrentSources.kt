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
        SourceKind.API_BAY -> ApiBayTorrentSource(config)
        SourceKind.TORRENT_CSV -> TorrentCsvSource(config)
        SourceKind.YTS_API -> YtsApiSource(config)
        SourceKind.ONE_THREE_THREE_SEVEN_X -> OneThreeThreeSevenXSource(config)
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

/** Public API-compatible search endpoint for The Pirate Bay index. */
private class ApiBayTorrentSource(config: SearchSourceConfig) : HttpTorrentSource(config) {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        val response = request("https://apibay.org/q.php?q=${encoded(query)}")
        val array = JSONArray(response)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val hash = item.optString("info_hash").trim()
                if (hash.isBlank()) continue
                add(TorrentResult(
                    title = item.optString("name").ifBlank { "Untitled result" },
                    magnetUri = "magnet:?xt=urn:btih:$hash&dn=${encoded(item.optString("name"))}",
                    source = displayName,
                    sizeBytes = item.optLong("size").takeIf { it > 0 },
                    seeders = item.optInt("seeders").takeIf { it >= 0 },
                    leechers = item.optInt("leechers").takeIf { it >= 0 },
                    category = TorrentCategory.fromSourceValue(item.optString("category")),
                    publishedAtEpochMillis = item.optLong("added").takeIf { it > 0 }?.times(1000),
                ))
            }
        }
    }

    override suspend fun performHealthCheck() {
        request("https://apibay.org/q.php?q=magnet-harbor-health")
    }
}

/** 1337xx HTML search provider. The site may reject automated requests. */
private class OneThreeThreeSevenXSource(config: SearchSourceConfig) : HttpTorrentSource(config) {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        val root = config.endpoint.trim().removeSuffix("/")
        require(root.startsWith("https://") || root.startsWith("http://")) { "Endpoint must start with http:// or https://" }
        val searchUrl = "$root/search/${encoded(query)}/${page.coerceAtLeast(1)}/"
        val html = request(searchUrl)
        val rows = ROW.findAll(html).toList().take(50)
        return buildList {
            for (row in rows) {
                val detailsUrl = row.groupValues[1].let { if (it.startsWith("http")) it else root + it }
                val title = row.groupValues[2].htmlText().trim()
                if (title.isBlank()) continue
                val magnet = runCatching { MAGNET.find(request(detailsUrl))?.groupValues?.get(1) }.getOrNull()
                    ?.replace("&amp;", "&") ?: continue
                add(TorrentResult(
                    title = title,
                    magnetUri = magnet,
                    source = displayName,
                    sizeBytes = parseSize(row.groupValues[3]),
                    seeders = row.groupValues[4].trim().toIntOrNull(),
                    leechers = row.groupValues[5].trim().toIntOrNull(),
                    category = TorrentCategory.fromSourceValue(row.groupValues[6]),
                ))
            }
        }
    }
    override suspend fun performHealthCheck() { request(config.endpoint.trim().removeSuffix("/")) }

    private companion object {
        val ROW = Regex("""<tr[^>]*>\s*<td[^>]*class=["']name["'][^>]*>.*?<a[^>]+href=["']([^"']+)["'][^>]*>[^<]*</a>.*?<a[^>]+href=["'][^"']+["'][^>]*>(.*?)</a>.*?</td>.*?<td[^>]*class=["']size["'][^>]*>(.*?)</td>.*?<td[^>]*class=["']seeds["'][^>]*>(.*?)</td>.*?<td[^>]*class=["']leeches["'][^>]*>(.*?)</td>.*?<a[^>]+href=["']/sub/([^/"']+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val MAGNET = Regex("""href=["'](magnet:\?[^"']+)["']""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }
}

private fun String.htmlText(): String = replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
private fun parseSize(value: String): Long? = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(KB|MB|GB|TB)""", RegexOption.IGNORE_CASE).find(value)?.let {
    val number = it.groupValues[1].toDoubleOrNull() ?: return@let null
    val multiplier = when (it.groupValues[2].uppercase()) { "KB" -> 1L shl 10; "MB" -> 1L shl 20; "GB" -> 1L shl 30; "TB" -> 1L shl 40; else -> 1L }
    (number * multiplier).toLong()
}

/** TorrentCSV's public JSON API. */
private class TorrentCsvSource(config: SearchSourceConfig) : HttpTorrentSource(config) {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        val response = request("https://torrents-csv.com/service/search?q=${encoded(query)}&size=100&page=${page.coerceAtLeast(1)}")
        val torrents = JSONObject(response).optJSONArray("torrents") ?: return emptyList()
        return buildList {
            for (index in 0 until torrents.length()) {
                val item = torrents.optJSONObject(index) ?: continue
                val hash = item.optString("infohash").trim()
                val title = item.optString("name").trim()
                if (hash.isBlank() || title.isBlank()) continue
                add(TorrentResult(title, "magnet:?xt=urn:btih:$hash&dn=${encoded(title)}", displayName,
                    item.optLong("size_bytes").takeIf { it > 0 }, item.optInt("seeders").takeIf { it >= 0 },
                    item.optInt("leechers").takeIf { it >= 0 }, TorrentCategory.fromSourceValue(title),
                    item.optLong("created_unix").takeIf { it > 0 }?.times(1000)))
            }
        }
    }
    override suspend fun performHealthCheck() { request("https://torrents-csv.com/service/search?q=health&size=1&page=1") }
}

/** YTS's public movie API, with magnets built from returned info hashes. */
private class YtsApiSource(config: SearchSourceConfig) : HttpTorrentSource(config) {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        val response = request("https://movies-api.accel.li/api/v2/list_movies.json?query_term=${encoded(query)}&limit=50&page=${page.coerceAtLeast(1)}")
        val movies = JSONObject(response).optJSONObject("data")?.optJSONArray("movies") ?: return emptyList()
        return buildList {
            for (i in 0 until movies.length()) {
                val movie = movies.optJSONObject(i) ?: continue
                val title = movie.optString("title_long").ifBlank { movie.optString("title") }
                val torrents = movie.optJSONArray("torrents") ?: continue
                for (j in 0 until torrents.length()) {
                    val torrent = torrents.optJSONObject(j) ?: continue
                    val hash = torrent.optString("hash").trim()
                    if (title.isBlank() || hash.isBlank()) continue
                    val label = listOf(torrent.optString("quality"), torrent.optString("type"), torrent.optString("video_codec")).filter { it.isNotBlank() }.joinToString(" ")
                    add(TorrentResult("$title $label".trim(), "magnet:?xt=urn:btih:$hash&dn=${encoded(title)}", displayName,
                        torrent.optLong("size_bytes").takeIf { it > 0 }, torrent.optInt("seeds").takeIf { it >= 0 },
                        torrent.optInt("peers").takeIf { it >= 0 }, TorrentCategory.VIDEO,
                        torrent.optLong("date_uploaded_unix").takeIf { it > 0 }?.times(1000)))
                }
            }
        }
    }
    override suspend fun performHealthCheck() { request("https://movies-api.accel.li/api/v2/list_movies.json?limit=1") }
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
