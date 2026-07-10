package com.example.webdavplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.webdavplayer.data.local.entity.PlaybackProgressEntity

/**
 * 播放进度 DAO（C3 / §5.2）。按 (serverId, path) 读写断点。
 */
@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE serverId = :s AND path = :p")
    suspend fun get(s: String, p: String): PlaybackProgressEntity?

    @Upsert
    suspend fun upsert(e: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE serverId = :s AND path = :p")
    suspend fun delete(s: String, p: String)

    @Query("DELETE FROM playback_progress")
    suspend fun clearAll()
}
