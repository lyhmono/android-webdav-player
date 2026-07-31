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
 *
 * 进度恢复（C3 / 中优项）：prepare 后检查是否有断点记录，
 * 如果有且超过阈值则 seekTo 断点位置，并保留恢复提示 UX。
 * 内核与进度由 PlayerRepository / PlaylistController 持有，UI 不直接碰内核。
 */
class PlayMediaUseCase @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistController: PlaylistController,
    private val mediaResolver: MediaResolver,
    private val progressRepository: PlaybackProgressRepository,
) {
    /**
     * @return [Result] 包含可播放媒体（含字幕轨与可选断点续播位置）。
     */
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
        // 观看进度已禁用（云鹤要求）：不读取断点、不 seek、不弹恢复提示，一律从头播放。
        playerRepository.play()
        media.copy(resumedPositionMs = null)
    }
}
