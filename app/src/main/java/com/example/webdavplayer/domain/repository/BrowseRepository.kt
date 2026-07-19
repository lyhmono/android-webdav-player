package com.example.webdavplayer.domain.repository

import androidx.paging.PagingData
import com.example.webdavplayer.domain.model.RemoteFile
import kotlinx.coroutines.flow.Flow
import okio.Source

/**
 * 浏览仓库接口（§4.2 / §6 T06）。
 * 真相源 = Room 缓存；大目录用 Paging3 虚拟列表（§1.3）。
 */
interface BrowseRepository {
    companion object {
        /** 目录缓存默认有效时长（毫秒）：5 分钟。超时或为空则触发刷新。 */
        const val DEFAULT_STALE_MS: Long = 5 * 60 * 1000L
    }

    /**
     * 观察某目录下的分页文件列表（来自 Room，秒开 + 虚拟滚动）。
     * UI 收集为 [androidx.paging.compose.LazyPagingItems]。
     */
    fun getDirectory(serverId: String, path: String): Flow<PagingData<RemoteFile>>

    /**
     * 从服务器刷新该目录（PROPFIND Depth:1），结果写入 Room。
     * 禁止在主线程调用（§8）。
     */
    suspend fun refreshDirectory(serverId: String, path: String)

    /**
     * 缓存优先刷新（§1.3 优化）：仅当缓存为空或已超过 [maxAgeMs] 时才打 PROPFIND。
     * 进目录时调用，可秒显 Room 缓存、并避免每次导航都整目录重拉。
     */
    suspend fun refreshIfStale(serverId: String, path: String, maxAgeMs: Long = DEFAULT_STALE_MS)

    /**
     * 目录缓存是否仍然新鲜：已刷新、未超龄、且缓存非空。
     * 供 [refreshIfStale] 与 UI（“更新于 Xs 前”）使用。
     */
    suspend fun isCacheFresh(serverId: String, path: String, maxAgeMs: Long = DEFAULT_STALE_MS): Boolean

    /** 取目录最后一次成功刷新的时间戳（毫秒）；从未刷新过返回 null。 */
    suspend fun getLastRefreshedAt(serverId: String, path: String): Long?

    /** 同步列举目录全部条目（用于“长按目录识别视频”等需要完整列表的场景）。 */
    suspend fun listDirectory(serverId: String, path: String): List<RemoteFile>

    /** 重命名（同目录内改名）。 */
    suspend fun rename(serverId: String, fromPath: String, toName: String)

    /** 移动（可跨目录）。 */
    suspend fun move(serverId: String, fromPath: String, toPath: String)

    /** 删除文件或目录。 */
    suspend fun delete(serverId: String, path: String)

    /** 单文件上传（P0）。 */
    suspend fun upload(serverId: String, path: String, source: Source, size: Long?)
}
