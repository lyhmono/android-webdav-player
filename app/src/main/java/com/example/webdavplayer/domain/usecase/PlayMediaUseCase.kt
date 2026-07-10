package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.PlayerRepository
import javax.inject.Inject

/**
 * 播放某一项用例（§6 T08/T09 编排）。
 *
 * 流程：解析为 [com.example.webdavplayer.domain.model.PlayableMedia]
 * → 标记当前项 → PlayerRepository.prepare → play。
 * 内核与进度由 PlayerRepository / PlaylistController 持有，UI 不直接碰内核。
 */
class PlayMediaUseCase @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistController: PlaylistController,
    private val mediaResolver: MediaResolver,
) {
    suspend operator fun invoke(item: PlaylistItem): Result<Unit> = Result.runCatching {
        val media = mediaResolver.resolve(item)
        playlistController.setCurrent(item)
        playerRepository.prepare(media)
        playerRepository.play()
    }
}
