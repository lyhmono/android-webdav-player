package com.example.webdavplayer.data.repository

import com.example.webdavplayer.domain.model.PlaybackProgress
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放进度节流保存器（C3 / §5.3）。
 *
 * 由 [com.example.webdavplayer.service.PlaybackService] 的引擎监听驱动：
 * - 播放过程中每 ~5s 触发一次落库（[onProgress] 内部节流）；
 * - 暂停 / 离开播放页时调用 [flush] 立即落库当前位置；
 * - 单条媒体“自然结束”调用 [onEnded] 清除断点（非循环场景才应清除）。
 *
 * 设计为 kernel-agnostic：只接收 (serverId, path, positionMs)，不感知具体内核。
 */
@Singleton
class PlaybackProgressSaver @Inject constructor(
    private val repository: PlaybackProgressRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 节流间隔：约 5 秒（C3 AC）。 */
    private val saveIntervalMs = 5_000L

    @Volatile
    private var lastSavedAt = 0L

    /**
     * 周期性进度回调（节流保存）。
     * 内部判断距上次落库是否超过 [saveIntervalMs]，超过才写库。
     */
    fun onProgress(serverId: String, path: String, positionMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSavedAt >= saveIntervalMs) {
            lastSavedAt = now
            persist(serverId, path, positionMs)
        }
    }

    /** 立即落库当前进度（暂停 / onPause 时调用，绕过节流）。 */
    suspend fun flush(serverId: String, path: String, positionMs: Long) {
        lastSavedAt = System.currentTimeMillis()
        repository.save(
            PlaybackProgress(
                serverId = serverId,
                path = path,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * 单条媒体自然结束：清除断点（C3 AC：正常结束清除续播点）。
     * 仅当非循环模式才应调用（调用方负责判断）。
     */
    fun onEnded(serverId: String, path: String) {
        scope.launch { repository.clear(serverId, path) }
    }

    /** 手动清除（如用户点击“清除进度/从头播放”）。 */
    fun clear(serverId: String, path: String) = onEnded(serverId, path)

    private fun persist(serverId: String, path: String, positionMs: Long) {
        scope.launch {
            repository.save(
                PlaybackProgress(
                    serverId = serverId,
                    path = path,
                    positionMs = positionMs,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
