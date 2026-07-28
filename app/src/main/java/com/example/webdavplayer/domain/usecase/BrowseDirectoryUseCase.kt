package com.example.webdavplayer.domain.usecase

import androidx.paging.PagingData
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.repository.BrowseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 浏览目录用例（§6 T06）。
 * 返回来自 Room 的分页流（秒开 + 虚拟列表），刷新由 [refresh] 触发。
 */
class BrowseDirectoryUseCase @Inject constructor(
    private val repository: BrowseRepository,
) {
    /** 观察目录分页数据（来自 Room 缓存）。 */
    operator fun invoke(serverId: String, path: String): Flow<PagingData<RemoteFile>> =
        repository.getDirectory(serverId, path)

    /** 触发远端刷新（PROPFIND Depth:1 → 写 Room）。应在 viewModelScope 中调用。 */
    suspend fun refresh(serverId: String, path: String): Result<Unit> =
        com.example.webdavplayer.common.Result.runCatching { repository.refreshDirectory(serverId, path) }

    /**
     * 缓存优先刷新（§1.3 优化）：仅当缓存为空或超龄才打 PROPFIND。
     * 进目录时调用，秒显 Room 缓存并避免每次导航都整目录重拉。
     */
    suspend fun refreshIfStale(
        serverId: String,
        path: String,
        maxAgeMs: Long = BrowseRepository.DEFAULT_STALE_MS,
    ): Result<Unit> =
        com.example.webdavplayer.common.Result.runCatching {
            repository.refreshIfStale(serverId, path, maxAgeMs)
        }

    /** 取目录最后一次成功刷新的时间戳（毫秒）；从未刷新过返回 null。 */
    suspend fun lastRefreshedAt(serverId: String, path: String): Long? =
        repository.getLastRefreshedAt(serverId, path)
}
