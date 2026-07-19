package com.example.webdavplayer.domain.player

import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.repository.PlayerRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 倍速（O1 + F1）契约测试（纯 JVM）。
 *
 * 锁定两项不变量，防止后续误删 / 漏实现：
 * 1. [PlayerEngine.setSpeed] 必须被内核实现并转发倍速值；
 * 2. [PlayerRepository] 作为倍速偏好的持有者，必须在 [PlayerRepository.prepare] 重建内核后
 *    重放倍速，保证跨曲目 / 跨内核持续生效（后台切歌路径依赖此行为）。
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
    fun repository_reappliesSpeedOnPrepare_afterSetSpeed() {
        val repo = SpeedRecordingPlayerRepository()
        repo.setSpeed(2.0f)
        repo.prepare(sampleMedia())
        // 重建 / 准备内核后倍速被重放，引擎应持有 2.0f。
        assertEquals(2.0f, repo.engine.lastSpeed)
    }

    @Test
    fun repository_defaultSpeedReappliedOnPrepare() {
        val repo = SpeedRecordingPlayerRepository()
        repo.prepare(sampleMedia())
        assertEquals(1.0f, repo.engine.lastSpeed)
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

    /** 持有倍速并在 prepare 时重放的 [PlayerRepository] 假实现（复刻 PlayerRepositoryImpl 契约）。 */
    private class SpeedRecordingPlayerRepository : PlayerRepository {
        val engine = RecordingPlayerEngine()
        private var currentSpeed: Float = 1.0f

        override fun getEngineType(): EngineType = EngineType.MEDIA3
        override suspend fun setEngineType(type: EngineType) = Unit
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
