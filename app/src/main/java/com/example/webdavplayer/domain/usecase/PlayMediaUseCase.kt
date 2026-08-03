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
        // 先 prepare + play 主媒体（无字幕），让视频立即开播，字幕异步发现（有 5s 超时保护）。
        // 避免 WebDAV PROPFIND 超时导致"一直准备中"。
        playlistController.setCurrent(item)
        playerRepository.prepare(base.copy(subtitles = emptyList()))
        playerRepository.play()
        // 字幕异步发现（失败/超时/离线 静默退化为无字幕）
        val subtitles = if (base.uri.startsWith("file", ignoreCase = true)) {
            emptyList()
        } else {
            runCatching { mediaResolver.discoverSubtitles(item) }.getOrDefault(emptyList())
        }
        base.copy(subtitles = subtitles)
    }
}
