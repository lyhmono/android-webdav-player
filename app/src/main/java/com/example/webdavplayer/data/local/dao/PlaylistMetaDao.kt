package com.example.webdavplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.webdavplayer.data.local.entity.PlaylistMetaEntity
import kotlinx.coroutines.flow.Flow

/**
 * 播放模式元信息 DAO（C2 / §4.2）。单例行（id=0）。
 */
@Dao
interface PlaylistMetaDao {
    @Query("SELECT * FROM playlist_meta WHERE id = 0")
    fun observe(): Flow<PlaylistMetaEntity?>

    @Query("SELECT * FROM playlist_meta WHERE id = 0")
    suspend fun get(): PlaylistMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(meta: PlaylistMetaEntity)
}
