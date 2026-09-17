package com.efremushkin.magnetharbor.data.source

import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult
import kotlinx.coroutines.delay

/**
 * Deterministic sample data for exercising the complete app without relying on a live index.
 * Replace or supplement these instances with documented API/Torznab adapters.
 */
class DemoTorrentSource(
    override val id: String,
    override val displayName: String,
    private val latencyMillis: Long,
) : TorrentSource {
    override suspend fun search(query: String, page: Int): List<TorrentResult> {
        delay(latencyMillis)
        val safeQuery = query.trim().ifEmpty { "Open source" }

        return when (id) {
            "public-archives" -> listOf(
                result(
                    title = "$safeQuery — public archive collection",
                    hash = "1111111111111111111111111111111111111111",
                    sizeBytes = 734_003_200,
                    seeders = 42,
                    leechers = 4,
                    category = TorrentCategory.BOOKS,
                ),
                result(
                    title = "$safeQuery — open media pack",
                    hash = "2222222222222222222222222222222222222222",
                    sizeBytes = 2_684_354_560,
                    seeders = 18,
                    leechers = 2,
                    category = TorrentCategory.VIDEO,
                ),
            )

            else -> listOf(
                result(
                    title = "$safeQuery — Linux image",
                    hash = "3333333333333333333333333333333333333333",
                    sizeBytes = 4_831_838_208,
                    seeders = 126,
                    leechers = 11,
                    category = TorrentCategory.SOFTWARE,
                ),
                result(
                    title = "$safeQuery — community audio",
                    hash = "4444444444444444444444444444444444444444",
                    sizeBytes = 188_743_680,
                    seeders = 0,
                    leechers = 0,
                    category = TorrentCategory.AUDIO,
                ),
            )
        }
    }

    private fun result(
        title: String,
        hash: String,
        sizeBytes: Long,
        seeders: Int,
        leechers: Int,
        category: TorrentCategory,
    ) = TorrentResult(
        title = title,
        magnetUri = "magnet:?xt=urn:btih:$hash&dn=${title.replace(' ', '+')}",
        source = displayName,
        sizeBytes = sizeBytes,
        seeders = seeders,
        leechers = leechers,
        category = category,
    )
}
