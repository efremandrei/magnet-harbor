package com.efremushkin.magnetharbor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAtEpochMillis DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE magnetUri = :magnetUri)")
    suspend fun exists(magnetUri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE magnetUri = :magnetUri")
    suspend fun delete(magnetUri: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMillis DESC LIMIT 100")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert
    suspend fun insert(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
