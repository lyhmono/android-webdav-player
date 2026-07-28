package com.example.webdavplayer.data.repository

import com.example.webdavplayer.domain.model.PlaybackProgress
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PlaybackProgressSaver] 单元测试（C3 节流保存 + 自然结束清除）。
 *
 * 纯 JVM：用假 [PlaybackProgressRepository] 记录调用；Saver 内部 `scope.launch`
 * 跑在 Dispatchers.IO，断言前 `delay(200)` 等待其落库完成。
 */
class PlaybackProgressSaverTest {

    private class FakeRepo : PlaybackProgressRepository {
        val store = mutableMapOf<Pair<String, String>, PlaybackProgress>()
        var savedCount = 0
        var clearedCount = 0
        override suspend fun save(p: PlaybackProgress) {
            store[Pair(p.serverId, p.path)] = p
            savedCount++
        }
        override suspend fun get(s: String, p: String): PlaybackProgress? = store[Pair(s, p)]
        override suspend fun clear(s: String, p: String) {
            store.remove(Pair(s, p))
            clearedCount++
        }
        override suspend fun clearAll() {
            store.clear()
        }
        override suspend fun clearServer(serverId: String) {
            store.keys.removeAll { it.first == serverId }
        }
    }

    @Test
    fun onProgress_firstCallSaves_throttlesSecondCallWithinInterval() = runBlocking {
        val repo = FakeRepo()
        val saver = PlaybackProgressSaver(repo)
        saver.onProgress("s1", "/a.mp4", 1000L)
        saver.onProgress("s1", "/a.mp4", 2000L) // 5s 内，应被节流
        delay(200)
        assertEquals(1, repo.savedCount)
        assertEquals(1000L, repo.store[Pair("s1", "/a.mp4")]?.positionMs)
    }

    @Test
    fun flush_alwaysSavesRegardlessOfThrottle() = runBlocking {
        val repo = FakeRepo()
        val saver = PlaybackProgressSaver(repo)
        saver.onProgress("s1", "/a.mp4", 1000L)
        delay(50)
        saver.flush("s1", "/a.mp4", 5000L) // 绕过节流
        assertEquals(2, repo.savedCount)
        assertEquals(5000L, repo.store[Pair("s1", "/a.mp4")]?.positionMs)
    }

    @Test
    fun onEnded_clearsProgress() = runBlocking {
        val repo = FakeRepo()
        val saver = PlaybackProgressSaver(repo)
        saver.onProgress("s1", "/a.mp4", 1000L)
        delay(50)
        saver.onEnded("s1", "/a.mp4")
        delay(200)
        assertEquals(1, repo.clearedCount)
        assertNull(repo.store[Pair("s1", "/a.mp4")])
    }

    @Test
    fun clear_delegatesToRepositoryClear() = runBlocking {
        val repo = FakeRepo()
        val saver = PlaybackProgressSaver(repo)
        saver.onProgress("s1", "/a.mp4", 1000L)
        delay(50)
        saver.clear("s1", "/a.mp4")
        delay(200)
        assertEquals(1, repo.clearedCount)
        assertNull(repo.store[Pair("s1", "/a.mp4")])
    }
}
