package com.example.webdavplayer.domain.usecase

import androidx.paging.PagingData
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
}
