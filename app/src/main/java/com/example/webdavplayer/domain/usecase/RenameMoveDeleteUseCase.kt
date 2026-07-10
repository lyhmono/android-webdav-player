package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.repository.BrowseRepository
import javax.inject.Inject

/**
 * 文件管理用例：重命名 / 移动 / 删除（P0，§6 T07）。
 * 新建目录 / 复制 / 批量属 P1，此处仅留这三样。
 */
class RenameMoveDeleteUseCase @Inject constructor(
    private val repository: BrowseRepository,
) {
    suspend fun rename(serverId: String, fromPath: String, toName: String): Result<Unit> =
        Result.runCatching { repository.rename(serverId, fromPath, toName) }

    suspend fun move(serverId: String, fromPath: String, toPath: String): Result<Unit> =
        Result.runCatching { repository.move(serverId, fromPath, toPath) }

    suspend fun delete(serverId: String, path: String): Result<Unit> =
        Result.runCatching { repository.delete(serverId, path) }
}
