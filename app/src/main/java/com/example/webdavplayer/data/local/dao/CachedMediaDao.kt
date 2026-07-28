package com.example.webdavplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.webdavplayer.data.local.entity.CachedMediaEntity

/** 离线缓存 DAO（P2：离线缓存功能）。 */
@Dao
interface CachedMediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedMediaEntity)

    @Query("SELECT * FROM cached_media ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<CachedMediaEntity>

    @Query("SELECT * FROM cached_media ORDER BY downloadedAt DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CachedMediaEntity>>

    /** 返回本地文件路径对应的缓存的服务器路径，供 URI 回填使用。 */
    @Query("SELECT * FROM cached_media WHERE serverId = :serverId AND path = :path LIMIT 1")
    suspend fun getByServerPath(serverId: String, path: String): CachedMediaEntity?

    @Query("DELETE FROM cached_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM cached_media WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CachedMediaEntity?
}
