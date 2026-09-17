package com.efremushkin.magnetharbor.data.source

import com.efremushkin.magnetharbor.data.model.TorrentResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

data class SourceFailure(val sourceName: String, val message: String)

data class SearchBatch(
    val results: List<TorrentResult>,
    val failures: List<SourceFailure>,
)

class SearchCoordinator(private val sources: List<TorrentSource>) {
    suspend fun search(query: String, enabledSourceIds: Set<String>): SearchBatch = supervisorScope {
        require(query.isNotBlank()) { "Search query cannot be blank" }

        val outcomes = sources
            .filter { it.id in enabledSourceIds }
            .map { source ->
                async {
                    runCatching { source.search(query.trim()) }
                        .fold(
                            onSuccess = { SourceOutcome(source.displayName, it, null) },
                            onFailure = {
                                SourceOutcome(
                                    sourceName = source.displayName,
                                    results = emptyList(),
                                    failure = SourceFailure(
                                        sourceName = source.displayName,
                                        message = it.message ?: it::class.simpleName.orEmpty(),
                                    ),
                                )
                            },
                        )
                }
            }
            .awaitAll()

        SearchBatch(
            results = deduplicate(outcomes.flatMap(SourceOutcome::results)),
            failures = outcomes.mapNotNull(SourceOutcome::failure),
        )
    }

    internal fun deduplicate(results: List<TorrentResult>): List<TorrentResult> = results
        .groupBy { result ->
            result.infoHash ?: "${result.title.trim().lowercase()}|${result.sizeBytes}"
        }
        .values
        .map { duplicates ->
            val best = duplicates.maxByOrNull { it.seeders ?: -1 } ?: duplicates.first()
            best.copy(
                seeders = duplicates.mapNotNull { it.seeders }.maxOrNull() ?: best.seeders,
                leechers = duplicates.mapNotNull { it.leechers }.maxOrNull() ?: best.leechers,
                sourceNames = duplicates.flatMap { it.sourceNames + it.source }.toSet(),
            )
        }

    private data class SourceOutcome(
        val sourceName: String,
        val results: List<TorrentResult>,
        val failure: SourceFailure?,
    )
}
