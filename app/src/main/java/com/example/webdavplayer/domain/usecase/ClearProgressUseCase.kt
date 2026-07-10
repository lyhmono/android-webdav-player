package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import javax.inject.Inject

/**
 * 清除某一媒体的播放进度断点（C3）。
 * UI“清除进度/从头播放”菜单项调用本用例后，再由上层 [com.example.webdavplayer.domain.repository.PlayerRepository.seekTo]
 * 归零并重新播放。
 */
class ClearProgressUseCase @Inject constructor(
    private val repository: PlaybackProgressRepository,
) {
    suspend operator fun invoke(serverId: String, path: String) {
        repository.clear(serverId, path)
    }
}
