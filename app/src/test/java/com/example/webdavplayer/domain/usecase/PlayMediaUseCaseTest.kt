package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.repository.PlaylistControllerImpl
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackProgress
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.SubtitleTrack
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import com.example.webdavplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayMediaUseCase] 断点续播单元测试（修复 G：进度被保存却从未读取/seek，续播实际失效）。
 *
 * 验证：prepare 之后、play 之前，若存在正进度则 seek 到断点；无进度 / 进度为 0 则不跳转（从头播放）。
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

        override suspend fun discoverSubtitles(item: PlaylistItem): List<SubtitleTrack> = emptyList()
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
        override fun release() {}
    }

    private class FakeProgressRepository(var saved: PlaybackProgress? = null) :
        PlaybackProgressRepository {
        override suspend fun save(progress: PlaybackProgress) {}
        override suspend fun get(serverId: String, path: String): PlaybackProgress? = saved
        override suspend fun clear(serverId: String, path: String) {}
        override suspend fun clearAll() {}
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
    fun play_resumesFromSavedProgress_whenPositionPositive() = runBlocking {
        val player = FakePlayerRepository()
        val useCase = PlayMediaUseCase(
            player,
            PlaylistControllerImpl(),
            FakeMediaResolver(),
            FakeProgressRepository(PlaybackProgress("s1", "/a.mp4", 12_000L, 0L)),
        )

        val r = useCase(item("/a.mp4"))

        assertTrue("应返回成功", r is Result.Success)
        // 顺序：prepare → seek(断点) → play
        assertEquals(listOf("prepare", "seek:12000", "play"), player.events)
    }

    @Test
    fun play_doesNotSeek_whenNoSavedProgress() = runBlocking {
        val player = FakePlayerRepository()
        val useCase = PlayMediaUseCase(
            player,
            PlaylistControllerImpl(),
            FakeMediaResolver(),
            FakeProgressRepository(null),
        )

        useCase(item("/b.mp4"))

        assertEquals(listOf("prepare", "play"), player.events)
        assertNull(player.lastSeekTo)
    }

    @Test
    fun play_doesNotSeek_whenSavedProgressIsZero() = runBlocking {
        val player = FakePlayerRepository()
        val useCase = PlayMediaUseCase(
            player,
            PlaylistControllerImpl(),
            FakeMediaResolver(),
            FakeProgressRepository(PlaybackProgress("s1", "/c.mp4", 0L, 0L)),
        )

        useCase(item("/c.mp4"))

        assertEquals(listOf("prepare", "play"), player.events)
        assertNull(player.lastSeekTo)
    }
}
