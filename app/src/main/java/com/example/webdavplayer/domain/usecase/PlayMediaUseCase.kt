package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
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
 * → 标记当前项 → PlayerRepository.prepare → play。
 *
 * 进度恢复（C3 / 中优项）：prepare 后检查是否有断点记录，
 * 如果有且超过 5 秒则 seekTo 断点位置。
 * 内核与进度由 PlayerRepository / PlaylistController 持有，UI 不直接碰内核。
 */
class PlayMediaUseCase @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistController: PlaylistController,
    private val mediaResolver: MediaResolver,
    private val progressRepository: PlaybackProgressRepository,
) {
    /**
     * @return [Result] 包含恢复的断点位置（毫秒），null 表示无断点或未恢复。
     */
    suspend operator fun invoke(item: PlaylistItem): Result<Long?> = Result.runCatching {
        val media = mediaResolver.resolve(item)
        playlistController.setCurrent(item)
        playerRepository.prepare(media)

        // 检查是否有播放断点，超过 5s 的才恢复
        val progress = progressRepository.get(item.serverId, item.path)
        if (progress != null && progress.positionMs > RESUME_THRESHOLD_MS) {
            playerRepository.seekTo(progress.positionMs)
        }

        playerRepository.play()
        progress?.positionMs
    }

    companion object {
        /** 断点恢复阈值（ms）：小于此值视为从头播放，避免短暂播放也弹恢复提示。 */
        private const val RESUME_THRESHOLD_MS = 5_000L
    }
}
