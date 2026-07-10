package com.example.webdavplayer.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.webdavplayer.data.local.entity.RemoteFileEntity

/** 远程文件缓存 DAO（大目录单层缓存，§1.3）。 */
@Dao
interface RemoteFileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<RemoteFileEntity>)

    /** 供 Paging3 使用的分页源（同目录一层）。 */
    @Query(
        "SELECT * FROM remote_files " +
            "WHERE serverId = :serverId AND parentPath = :parentPath " +
            "ORDER BY isDirectory DESC, name COLLATE NOCASE ASC"
    )
    fun pagingSource(serverId: String, parentPath: String): PagingSource<Int, RemoteFileEntity>

    @Query("DELETE FROM remote_files WHERE serverId = :serverId AND parentPath = :parentPath")
    suspend fun clearDirectory(serverId: String, parentPath: String)

    @Query("DELETE FROM remote_files WHERE serverId = :serverId")
    suspend fun clearServer(serverId: String)
}
