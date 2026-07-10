package com.example.webdavplayer.data.repository

import com.example.webdavplayer.data.local.dao.PlaylistDao
import com.example.webdavplayer.data.local.dao.PlaylistMetaDao
import com.example.webdavplayer.data.local.entity.PlaylistItemEntity
import com.example.webdavplayer.data.local.entity.PlaylistMetaEntity
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PlaylistRepositoryImpl] 单元测试（C2 持久化 + C5 按 serverId 归属过滤）。
 *
 * 纯 JVM：用“回声”假 DAO / 假 Settings 直接实例化真实实现，避免 Room 依赖。
 * 假 DAO 的 `getAll()` 回显最近 `upsertAll` 的内容，使构造函数内的异步 `init`
 * 载入（Dispatchers.IO）无论早晚都会收敛到与同步写操作一致的状态，测试确定性。
 */
class PlaylistRepositoryImplTest {

    private lateinit var fakeDao: FakePlaylistDao
    private lateinit var fakeMetaDao: FakePlaylistMetaDao
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var repo: PlaylistRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakePlaylistDao()
        fakeMetaDao = FakePlaylistMetaDao()
        fakeSettings = FakeSettingsRepository("srvA")
        repo = PlaylistRepositoryImpl(fakeDao, fakeMetaDao, fakeSettings)
        Thread.sleep(100) // 等 init 从（回声）fake DAO 载入完成，确保确定性
    }

    private fun item(id: String, serverId: String = "srvA"): PlaylistItem = PlaylistItem(
        id = id,
        serverId = serverId,
        path = "/$id.mp4",
        name = id,
        mediaType = MediaType.VIDEO,
        durationMs = 0L,
        addedAt = 0L,
    )

    // ===== 模式读写 + 切换（C2 playlist_meta 单例行） =====
    @Test
    fun setMode_updatesModeCacheAndPersistsSingleRow() = runBlocking {
        repo.setMode(PlayMode.LOOP)
        assertEquals(PlayMode.LOOP, repo.observeMode().first())
        assertEquals(PlayMode.LOOP, fakeMetaDao.get()?.mode)
    }

    @Test
    fun modeSwitchesThroughAllThreeValues() = runBlocking {
        repo.setMode(PlayMode.SEQUENTIAL)
        assertEquals(PlayMode.SEQUENTIAL, repo.observeMode().first())
        repo.setMode(PlayMode.LOOP)
        assertEquals(PlayMode.LOOP, repo.observeMode().first())
        repo.setMode(PlayMode.SHUFFLE)
        assertEquals(PlayMode.SHUFFLE, repo.observeMode().first())
        // 单例行应始终只保留最后一值（REPLACE）
        assertEquals(PlayMode.SHUFFLE, fakeMetaDao.get()?.mode)
    }

    // ===== 按 currentServerId 过滤归属（C2 AC4 + C5） =====
    @Test
    fun observeItems_filtersByCurrentServerId() = runBlocking {
        repo.addItems(
            listOf(item("a1", "srvA"), item("a2", "srvA"), item("b1", "srvB")),
            replace = false,
        )
        val list = repo.observeItems().first { it.isNotEmpty() }
        assertEquals(2, list.size)
        assertTrue(list.all { it.serverId == "srvA" })
    }

    @Test
    fun observeItems_emptyWhenNoCurrentServer() = runBlocking {
        fakeSettings.setCurrentServerId(null)
        repo.addItems(listOf(item("a1", "srvA")), replace = false)
        // 无当前服务器 → 始终返回空列表（combine 中 sid==null → emptyList）
        assertEquals(emptyList<PlaylistItem>(), repo.observeItems().first { it.isEmpty() })
    }

    // ===== 双写（内存缓存 + Room）=====
    @Test
    fun addItems_doubleWritesToCacheAndDao() = runBlocking {
        val items = listOf(item("a1"), item("a2"))
        repo.addItems(items, replace = false)
        assertEquals(2, fakeDao.items.size)
        assertEquals(2, repo.observeItems().first { it.isNotEmpty() }.size)
    }

    @Test
    fun removeItem_doubleWrites() = runBlocking {
        repo.addItems(listOf(item("a1"), item("a2")), replace = false)
        repo.removeItem("a1")
        assertFalse(fakeDao.items.any { it.id == "a1" })
        assertEquals(1, repo.observeItems().first { it.isNotEmpty() }.size)
    }

    @Test
    fun clear_doubleWrites() = runBlocking {
        repo.addItems(listOf(item("a1"), item("a2")), replace = false)
        repo.clear()
        assertTrue(fakeDao.items.isEmpty())
        assertEquals(emptyList<PlaylistItem>(), repo.observeItems().first { it.isEmpty() })
    }

    // ===== Fakes =====
    private class FakePlaylistDao : PlaylistDao {
        val items = mutableListOf<PlaylistItemEntity>()
        override suspend fun getAll(): List<PlaylistItemEntity> = items.toList()
        override suspend fun upsertAll(list: List<PlaylistItemEntity>) {
            items.clear()
            items.addAll(list)
        }
        override suspend fun insert(item: PlaylistItemEntity) {
            items.add(item)
        }
        override suspend fun deleteById(id: String) {
            items.removeAll { it.id == id }
        }
        override suspend fun clear() {
            items.clear()
        }
        override fun observe() = kotlinx.coroutines.flow.flowOf(items.toList())
    }

    private class FakePlaylistMetaDao : PlaylistMetaDao {
        private var row: PlaylistMetaEntity? = null
        override suspend fun get(): PlaylistMetaEntity? = row
        override suspend fun set(meta: PlaylistMetaEntity) {
            row = meta
        }
        override fun observe() = kotlinx.coroutines.flow.flowOf(row)
    }

    private class FakeSettingsRepository(initial: String?) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        override fun observeCurrentServerId() = flow
        override fun getCurrentServerId() = flow.value
        override suspend fun setCurrentServerId(id: String?) {
            flow.value = id
        }
        // 其余方法（内核偏好）测试不触及
        override fun observeEngineType() =
            kotlinx.coroutines.flow.flowOf(com.example.webdavplayer.domain.model.EngineType.MEDIA3)
        override fun getEngineType() = com.example.webdavplayer.domain.model.EngineType.MEDIA3
        override suspend fun setEngineType(type: com.example.webdavplayer.domain.model.EngineType) {}
    }
}
