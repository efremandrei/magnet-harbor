package com.efremushkin.magnetharbor.data.repository

import com.efremushkin.magnetharbor.data.local.FavoriteDao
import com.efremushkin.magnetharbor.data.local.SearchHistoryDao
import com.efremushkin.magnetharbor.data.local.SearchHistoryEntity
import com.efremushkin.magnetharbor.data.local.toFavoriteEntity
import com.efremushkin.magnetharbor.data.local.toModel
import com.efremushkin.magnetharbor.data.model.TorrentResult
import com.efremushkin.magnetharbor.data.source.SearchBatch
import com.efremushkin.magnetharbor.data.source.SearchCoordinator
import kotlinx.coroutines.flow.map

class TorrentRepository(
    private val searchCoordinator: SearchCoordinator,
    private val favoriteDao: FavoriteDao,
    private val historyDao: SearchHistoryDao,
) {
    val favorites = favoriteDao.observeAll().map { items -> items.map { it.toModel() } }
    val history = historyDao.observeRecent()

    suspend fun search(query: String, sourceIds: Set<String>): SearchBatch {
        historyDao.insert(
            SearchHistoryEntity(
                query = query.trim(),
                searchedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return searchCoordinator.search(query, sourceIds)
    }

    suspend fun toggleFavorite(result: TorrentResult) {
        if (favoriteDao.exists(result.magnetUri)) favoriteDao.delete(result.magnetUri)
        else favoriteDao.upsert(result.toFavoriteEntity())
    }

    suspend fun clearHistory() = historyDao.clear()
}
