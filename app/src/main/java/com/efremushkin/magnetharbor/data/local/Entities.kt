package com.efremushkin.magnetharbor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val magnetUri: String,
    val title: String,
    val source: String,
    val sizeBytes: Long?,
    val seeders: Int?,
    val leechers: Int?,
    val category: String,
    val addedAtEpochMillis: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchedAtEpochMillis: Long,
)

fun TorrentResult.toFavoriteEntity(now: Long = System.currentTimeMillis()) = FavoriteEntity(
    magnetUri = magnetUri,
    title = title,
    source = source,
    sizeBytes = sizeBytes,
    seeders = seeders,
    leechers = leechers,
    category = category.name,
    addedAtEpochMillis = now,
)

fun FavoriteEntity.toModel() = TorrentResult(
    title = title,
    magnetUri = magnetUri,
    source = source,
    sizeBytes = sizeBytes,
    seeders = seeders,
    leechers = leechers,
    category = runCatching { TorrentCategory.valueOf(category) }.getOrDefault(TorrentCategory.OTHER),
)
