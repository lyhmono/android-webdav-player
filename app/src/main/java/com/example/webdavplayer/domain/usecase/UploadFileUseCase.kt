package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.repository.BrowseRepository
import okio.Source
import javax.inject.Inject

/** 单文件上传用例（P0，§6 T07）。批量上传属 P1。 */
class UploadFileUseCase @Inject constructor(
    private val repository: BrowseRepository,
) {
    suspend operator fun invoke(
        serverId: String,
        parentPath: String,
        fileName: String,
        source: Source,
        size: Long? = null,
    ): Result<Unit> = Result.runCatching {
        val path = if (parentPath.endsWith("/")) "$parentPath$fileName" else "$parentPath/$fileName"
        repository.upload(serverId, path, source, size)
    }
}
