package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import com.example.webdavplayer.domain.repository.PlayerRepository
import javax.inject.Inject

/**
 * 播放某一项用例（§6 T08/T09 编排）。
 *
 * 流程：解析为 [com.example.webdavplayer.domain.model.PlayableMedia]
 * → 发现同级字幕（P2，失败容错）→ 标记当前项 → PlayerRepository.prepare
 * → 断点续播（读取已保存进度并 seek）→ play。
 * 内核与进度由 PlayerRepository / PlaylistController 持有，UI 不直接碰内核。
 */
class PlayMediaUseCase @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistController: PlaylistController,
    private val mediaResolver: MediaResolver,
    private val progressRepository: PlaybackProgressRepository,
) {
    suspend operator fun invoke(item: PlaylistItem): Result<PlayableMedia> = Result.runCatching {
        val base = mediaResolver.resolve(item)
        // 字幕发现失败（离线 / 无权限）时静默退化为无字幕，不影响主媒体播放。
        // 本地缓存文件（file://）无同级服务器字幕可发现，直接跳过网络列举，避免离线回放仍打 PROPFIND。
        val subtitles = if (base.uri.startsWith("file", ignoreCase = true)) {
            emptyList()
        } else {
            runCatching { mediaResolver.discoverSubtitles(item) }.getOrDefault(emptyList())
        }
        val media = base.copy(subtitles = subtitles)
        playlistController.setCurrent(item)
        playerRepository.prepare(media)
        // 断点续播（§一致性/UX）：恢复上次暂停位置。进度为 0 或不存在则不跳转（从头播放）。
        // 注意：ENDED 状态不会持久化末位位置（见 PlaybackService），故此处不会 seek 到末尾。
        val saved = progressRepository.get(item.serverId, item.path)
        if (saved != null && saved.positionMs > 0) {
            playerRepository.seekTo(saved.positionMs)
        }
        playerRepository.play()
        media
    }
}
