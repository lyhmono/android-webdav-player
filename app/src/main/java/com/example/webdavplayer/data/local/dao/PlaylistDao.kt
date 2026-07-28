package com.example.webdavplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.webdavplayer.data.local.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放列表项 DAO（P1 持久化缝，§4.1 / T12）。
 * P0 未使用；预留以备 Room 化播放列表。
 */
@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist_items ORDER BY addedAt ASC")
    fun observe(): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items")
    suspend fun getAll(): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<PlaylistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playlist_items WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM playlist_items")
    suspend fun clear()
}
