package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.CachedMedia
import kotlinx.coroutines.flow.Flow

/**
 * 离线缓存仓库（P2）。
 *
 * 文件保存到 `context.cacheDir/cache/` 目录，Room 记录元数据。
 * 下载/删除均为 IO 操作，应在协程中调用。
 */
interface CacheRepository {
    /** 下载远程文件到本地缓存（阻塞 IO）。 */
    suspend fun download(serverId: String, path: String): Result<CachedMedia>

    /** 获取缓存文件的本机绝对路径；未命中返回 null。 */
    suspend fun getLocalFilePath(serverId: String, path: String): String?

    /** 观察所有已缓存项（按下载时间倒序）。 */
    fun observeAll(): Flow<List<CachedMedia>>

    /** 删除缓存（清理本地文件 + Room 记录）。 */
    suspend fun delete(id: String)
}
