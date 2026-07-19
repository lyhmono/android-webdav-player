package com.example.webdavplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.webdavplayer.data.local.entity.DirectoryMetaEntity

/** 目录缓存元数据 DAO（§1.3 优化：TTL 条件刷新）。 */
@Dao
interface DirectoryMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: DirectoryMetaEntity)

    @Query(
        "SELECT * FROM directory_meta " +
            "WHERE serverId = :serverId AND parentPath = :parentPath LIMIT 1",
    )
    suspend fun get(serverId: String, parentPath: String): DirectoryMetaEntity?

    @Query("DELETE FROM directory_meta WHERE serverId = :serverId AND parentPath = :parentPath")
    suspend fun clearDirectory(serverId: String, parentPath: String)

    @Query("DELETE FROM directory_meta WHERE serverId = :serverId")
    suspend fun clearServer(serverId: String)
}
