package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.repository.PlaylistControllerImpl
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayMediaUseCase] 单元测试。
 *
 * 验证：prepare 之后 play，事件顺序为 prepare → play（观看进度已禁用，不 seek）。
 */
class PlayMediaUseCaseTest {

    private class FakeMediaResolver : MediaResolver {
        override suspend fun resolve(item: PlaylistItem): PlayableMedia = PlayableMedia(
            uri = "https://host${item.path}",
            headers = emptyMap(),
            name = item.name,
            mediaType = item.mediaType,
            serverId = item.serverId,
            trustSelfSigned = false,
        )
    }

    private class FakePlayerRepository : PlayerRepository {
        val events = mutableListOf<String>()
        var lastSeekTo: Long? = null
        override fun getEngineType(): EngineType = EngineType.MEDIA3
        override suspend fun setEngineType(type: EngineType) {}
        override suspend fun prepare(media: PlayableMedia) { events.add("prepare") }
        override fun play() { events.add("play") }
        override fun pause() {}
        override fun seekTo(positionMs: Long) {
            events.add("seek:$positionMs")
            lastSeekTo = positionMs
        }
        override fun setSpeed(speed: Float) {}
        override fun setListener(listener: EngineListener?) {}
        override fun getState(): PlaybackState = PlaybackState.IDLE
        override fun getCurrentPosition(): Long = 0L
        override fun getDurationMs(): Long = 0L
        override fun getPlayer(): androidx.media3.common.Player? = null
        override fun release() {}
    }

    private fun item(path: String): PlaylistItem = PlaylistItem(
        id = "s1:$path",
        serverId = "s1",
        path = path,
        name = path,
        mediaType = MediaType.VIDEO,
        durationMs = 0L,
        addedAt = 0L,
    )

    @Test
    fun play_preparesThenPlays() = runBlocking {
        val player = FakePlayerRepository()
        val useCase = PlayMediaUseCase(
            player,
            PlaylistControllerImpl(),
            FakeMediaResolver(),
        )

        val r = useCase(item("/a.mp4"))

        assertTrue("应返回成功", r is Result.Success)
        // 观看进度已禁用：一律 prepare → play，不 seek
        assertEquals(listOf("prepare", "play"), player.events)
        assertEquals(null, player.lastSeekTo)
    }
}
