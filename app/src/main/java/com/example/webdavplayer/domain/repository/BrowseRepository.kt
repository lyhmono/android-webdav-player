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
