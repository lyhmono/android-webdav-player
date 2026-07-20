package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.PlayerRepository
import javax.inject.Inject

/**
 * 播放某一项用例（§6 T08/T09 编排）。
 *
 * 流程：解析为 [com.example.webdavplayer.domain.model.PlayableMedia]
 * → 发现同级字幕（P2，失败容错）→ 标记当前项 → PlayerRepository.prepare → play。
 * 内核与进度由 PlayerRepository / PlaylistController 持有，UI 不直接碰内核。
 */
class PlayMediaUseCase @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistController: PlaylistController,
    private val mediaResolver: MediaResolver,
) {
    suspend operator fun invoke(item: PlaylistItem): Result<PlayableMedia> = Result.runCatching {
        val base = mediaResolver.resolve(item)
        // 字幕发现失败（离线 / 无权限）时静默退化为无字幕，不影响主媒体播放。
        val subtitles = runCatching { mediaResolver.discoverSubtitles(item) }.getOrDefault(emptyList())
        val media = base.copy(subtitles = subtitles)
        playlistController.setCurrent(item)
        playerRepository.prepare(media)
        playerRepository.play()
        media
    }
}
