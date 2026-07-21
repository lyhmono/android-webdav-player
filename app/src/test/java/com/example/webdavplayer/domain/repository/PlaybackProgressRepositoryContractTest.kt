package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.PlaybackProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PlaybackProgressRepository] 复合主键契约测试（C3 / §5.1）。
 *
 * 模拟 Room `@Entity(primaryKeys = ["serverId", "path"])` + `@Upsert` 的语义：
 * 以 (serverId, path) 唯一标识一条断点；覆盖 save/query/clear/clearAll 与
 * 按 serverId / path 维度区分。真实 [com.example.webdavplayer.data.repository.PlaybackProgressRepositoryImpl]
 * 经 Room DAO 实现相同契约，此处验证契约本身（避免 Room 依赖）。
 */
class PlaybackProgressRepositoryContractTest {

    /** 模拟复合主键语义的假仓库。 */
    private class FakeRepo : PlaybackProgressRepository {
        private val store = mutableMapOf<Pair<String, String>, PlaybackProgress>()
        override suspend fun save(p: PlaybackProgress) {
            store[Pair(p.serverId, p.path)] = p
        }
        override suspend fun get(s: String, p: String): PlaybackProgress? = store[Pair(s, p)]
        override suspend fun clear(s: String, p: String) {
            store.remove(Pair(s, p))
        }
        override suspend fun clearServer(serverId: String) {
            store.keys.removeAll { it.first == serverId }
        }
        override suspend fun clearAll() {
            store.clear()
        }
    }

    @Test
    fun compositeKey_upsertOverwritesSameServerPath() = runBlocking {
        val repo = FakeRepo()
        repo.save(PlaybackProgress("s1", "/a.mp4", 100L))
        repo.save(PlaybackProgress("s1", "/a.mp4", 200L)) // 同键 → 覆盖
        assertEquals(200L, repo.get("s1", "/a.mp4")?.positionMs)
    }

    @Test
    fun compositeKey_distinguishesByServerId() = runBlocking {
        val repo = FakeRepo()
        repo.save(PlaybackProgress("s1", "/a.mp4", 100L))
        repo.save(PlaybackProgress("s2", "/a.mp4", 300L)) // 不同 server → 不同键
        assertEquals(100L, repo.get("s1", "/a.mp4")?.positionMs)
        assertEquals(300L, repo.get("s2", "/a.mp4")?.positionMs)
    }

    @Test
    fun compositeKey_distinguishesByPath() = runBlocking {
        val repo = FakeRepo()
        repo.save(PlaybackProgress("s1", "/a.mp4", 100L))
        repo.save(PlaybackProgress("s1", "/b.mp4", 400L))
        assertEquals(100L, repo.get("s1", "/a.mp4")?.positionMs)
        assertEquals(400L, repo.get("s1", "/b.mp4")?.positionMs)
    }

    @Test
    fun clear_removesOnlyMatchingKey() = runBlocking {
        val repo = FakeRepo()
        repo.save(PlaybackProgress("s1", "/a.mp4", 100L))
        repo.save(PlaybackProgress("s2", "/a.mp4", 300L))
        repo.clear("s1", "/a.mp4")
        assertNull(repo.get("s1", "/a.mp4"))
        assertEquals(300L, repo.get("s2", "/a.mp4")?.positionMs)
    }

    @Test
    fun clearAll_removesEverything() = runBlocking {
        val repo = FakeRepo()
        repo.save(PlaybackProgress("s1", "/a.mp4", 100L))
        repo.save(PlaybackProgress("s2", "/b.mp4", 200L))
        repo.clearAll()
        assertNull(repo.get("s1", "/a.mp4"))
        assertNull(repo.get("s2", "/b.mp4"))
    }

    @Test
    fun clearServer_removesOnlyThatServer() = runBlocking {
        val repo = FakeRepo()
        repo.save(PlaybackProgress("s1", "/a.mp4", 100L))
        repo.save(PlaybackProgress("s1", "/b.mp4", 150L))
        repo.save(PlaybackProgress("s2", "/a.mp4", 300L)) // 不同服务器，应保留
        repo.clearServer("s1")
        assertNull(repo.get("s1", "/a.mp4"))
        assertNull(repo.get("s1", "/b.mp4"))
        assertEquals(300L, repo.get("s2", "/a.mp4")?.positionMs)
    }
}
