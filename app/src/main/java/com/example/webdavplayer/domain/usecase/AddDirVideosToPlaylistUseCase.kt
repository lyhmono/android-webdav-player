package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.repository.BrowseRepository
import com.example.webdavplayer.domain.repository.PlaylistRepository
import javax.inject.Inject

/**
 * 长按目录 → 自动识别视频 → 加入播放列表（§1.5 / §6 T08）。
 *
 * 过滤规则（双重保险）：contentType 以 video/ 开头（含其下任意子类型），或扩展名命中视频白名单。
 * [replace]=false 时追加；UI 可提供“替换/追加”开关。
 */
class AddDirVideosToPlaylistUseCase @Inject constructor(
    private val browseRepository: BrowseRepository,
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(
        serverId: String,
        dirPath: String,
        replace: Boolean = false,
    ): Result<List<PlaylistItem>> = Result.runCatching {
        val files = browseRepository.listDirectory(serverId, dirPath)
        val videos = files.filter { it.mediaType == MediaType.VIDEO }

        val items = videos.map { file ->
            val itemPath = joinPath(dirPath, file.name)
            PlaylistItem(
                id = "$serverId:$itemPath",
                serverId = file.serverId,
                path = itemPath,
                name = file.name,
                mediaType = MediaType.VIDEO,
                durationMs = 0L,
                addedAt = System.currentTimeMillis(),
            )
        }
        playlistRepository.addItems(items, replace)
        items
    }

    private fun joinPath(parent: String, name: String): String =
        if (parent.endsWith("/")) "$parent$name" else "$parent/$name"
}
