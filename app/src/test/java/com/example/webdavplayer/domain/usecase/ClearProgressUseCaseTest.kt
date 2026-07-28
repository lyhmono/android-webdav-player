package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.domain.model.PlaybackProgress
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ClearProgressUseCase] 单元测试（C3 UI “清除进度/从头播放”入口）。
 *
 * 验证用例把 (serverId, path) 透传给 [PlaybackProgressRepository.clear]。
 */
class ClearProgressUseCaseTest {

    private class FakeRepo : PlaybackProgressRepository {
        var lastCleared: Pair<String, String>? = null
        override suspend fun save(p: PlaybackProgress) {}
        override suspend fun get(s: String, p: String): PlaybackProgress? = null
        override suspend fun clear(s: String, p: String) {
            lastCleared = Pair(s, p)
        }
        override suspend fun clearServer(serverId: String) {}
        override suspend fun clearAll() {}
    }

    @Test
    fun invoke_clearsByServerIdAndPath() = runBlocking {
        val repo = FakeRepo()
        val useCase = ClearProgressUseCase(repo)
        useCase("s1", "/a.mp4")
        assertEquals(Pair("s1", "/a.mp4"), repo.lastCleared)
    }
}
