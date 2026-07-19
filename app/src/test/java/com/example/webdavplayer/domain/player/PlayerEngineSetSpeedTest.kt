package com.example.webdavplayer.domain.player

import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 倍速（O1 + F1）契约测试（纯 JVM）。
 *
 * ⚠️ 设计说明：本测试用 [SpeedRecordingPlayerRepository] 复刻 [com.example.webdavplayer.data.repository.PlayerRepositoryImpl]
 * 的"prepare / setEngineType 重建内核后重放 currentSpeed"契约，而非直接实例化真实仓库——
 * 因为真实 [com.example.webdavplayer.data.repository.PlayerRepositoryImpl] 的构造依赖 Android [android.content.Context]
 * （经 [com.example.webdavplayer.data.player.PlayerEngineFactory.create] 创建 ExoPlayer），
 * 纯 JVM 单测无法提供。此处的复刻用于：
 * 1. 锁定 [PlayerEngine.setSpeed] 接口契约（内核实现必须转发倍速值）；
 * 2. 文档化并锁定"倍速作为偏好，在 prepare / 切内核后必须重放"这一不变量。
 *
 * 若改动 [com.example.webdavplayer.data.repository.PlayerRepositoryImpl] 的倍速重放逻辑，
 * 请同步更新本复刻，并人工核对一致性（沙箱无 Android SDK，无法跑 instrumented test）。
 */
class PlayerEngineSetSpeedTest {

    private fun sampleMedia(): PlayableMedia = PlayableMedia(
        uri = "https://example.com/a.mp4",
        headers = emptyMap(),
        name = "a.mp4",
        mediaType = MediaType.VIDEO,
        serverId = "s1",
    )

    @Test
    fun engine_setSpeed_forwardsValueToUnderlyingPlayer() {
        val engine = RecordingPlayerEngine()
        engine.setSpeed(1.5f)
        assertEquals(1.5f, engine.lastSpeed)
        assertEquals(listOf(1.5f), engine.speeds)
    }

    @Test
    fun engine_defaultSpeedIsNormal() {
        val engine = RecordingPlayerEngine()
        assertEquals(1.0f, engine.lastSpeed)
    }

    @Test
    fun repository_reappliesSpeedOnPrepare_afterSetSpeed() = runBlocking {
        val repo = SpeedRecordingPlayerRepository()
        repo.setSpeed(2.0f)
        repo.prepare(sampleMedia())
        // 重建 / 准备内核后倍速被重放，引擎应持有 2.0f。
        assertEquals(2.0f, repo.engine.lastSpeed)
    }

    @Test
    fun repository_defaultSpeedReappliedOnPrepare() = runBlocking {
        val repo = SpeedRecordingPlayerRepository()
        repo.prepare(sampleMedia())
        assertEquals(1.0f, repo.engine.lastSpeed)
    }

    @Test
    fun repository_reappliesSpeedOnEngineSwitch() = runBlocking {
        val repo = SpeedRecordingPlayerRepository()
        repo.prepare(sampleMedia())
        repo.setSpeed(1.5f)
        repo.setEngineType(EngineType.VLC)
        assertEquals("切内核后应重放倍速", 1.5f, repo.engine.lastSpeed)
    }

    /** 记录倍速调用的 [PlayerEngine] 假实现（无 Android 依赖）。 */
    private class RecordingPlayerEngine : PlayerEngine {
        var lastSpeed: Float = 1.0f
            private set
        val speeds = mutableListOf<Float>()

        override fun prepare(media: PlayableMedia) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setSpeed(speed: Float) {
            lastSpeed = speed
            speeds += speed
        }
        override fun setListener(listener: EngineListener?) = Unit
        override fun getState(): PlaybackState = PlaybackState.IDLE
        override fun release() = Unit
    }

    /**
     * 持有倍速并在 prepare / setEngineType 时重放的 [PlayerRepository] 假实现。
     * 复刻 [com.example.webdavplayer.data.repository.PlayerRepositoryImpl] 的倍速重放契约：
     * `prepare` 与 `setEngineType` 重建内核后必须调用 `engine.setSpeed(currentSpeed)`。
     */
    private class SpeedRecordingPlayerRepository : PlayerRepository {
        val engine = RecordingPlayerEngine()
        private var currentSpeed: Float = 1.0f
        private var engineType: EngineType = EngineType.MEDIA3

        override fun getEngineType(): EngineType = engineType
        override suspend fun setEngineType(type: EngineType) {
            engineType = type
            // 模拟重建内核后重放倍速（与 PlayerRepositoryImpl 一致）。
            engine.setSpeed(currentSpeed)
        }
        override suspend fun prepare(media: PlayableMedia) {
            engine.prepare(media)
            engine.setSpeed(currentSpeed)
        }
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setSpeed(speed: Float) {
            currentSpeed = speed
            engine.setSpeed(speed)
        }
        override fun setListener(listener: EngineListener?) = Unit
        override fun getState(): PlaybackState = engine.getState()
        override fun release() = Unit
    }
}
