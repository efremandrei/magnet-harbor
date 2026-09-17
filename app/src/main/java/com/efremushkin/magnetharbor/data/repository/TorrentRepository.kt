package com.efremushkin.magnetharbor.data.repository

import com.efremushkin.magnetharbor.data.local.FavoriteDao
import com.efremushkin.magnetharbor.data.local.SearchHistoryDao
import com.efremushkin.magnetharbor.data.local.SearchHistoryEntity
import com.efremushkin.magnetharbor.data.local.toFavoriteEntity
import com.efremushkin.magnetharbor.data.local.toModel
import com.efremushkin.magnetharbor.data.model.TorrentResult
import com.efremushkin.magnetharbor.data.source.SearchBatch
import com.efremushkin.magnetharbor.data.source.SearchCoordinator
import com.efremushkin.magnetharbor.data.source.SearchSourceConfig
import com.efremushkin.magnetharbor.data.source.SourceHealth
import com.efremushkin.magnetharbor.data.source.TorrentSourceFactory
import kotlinx.coroutines.flow.map

class TorrentRepository(
    private val favoriteDao: FavoriteDao,
    private val historyDao: SearchHistoryDao,
) {
    val favorites = favoriteDao.observeAll().map { items -> items.map { it.toModel() } }
    val history = historyDao.observeRecent()

    suspend fun search(query: String, sources: List<SearchSourceConfig>): SearchBatch {
        historyDao.insert(
            SearchHistoryEntity(
                query = query.trim(),
                searchedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        val liveSources = sources.filter { it.enabled }.map(TorrentSourceFactory::create)
        if (liveSources.isEmpty()) return SearchBatch(emptyList(), emptyList())
        return SearchCoordinator(liveSources).search(query, liveSources.mapTo(mutableSetOf()) { it.id })
    }

    suspend fun testSource(config: SearchSourceConfig): SourceHealth = TorrentSourceFactory.create(config).testConnection()

    suspend fun toggleFavorite(result: TorrentResult) {
        if (favoriteDao.exists(result.magnetUri)) favoriteDao.delete(result.magnetUri)
        else favoriteDao.upsert(result.toFavoriteEntity())
    }

    suspend fun clearHistory() = historyDao.clear()
}
