package com.example.webdavplayer.data.repository

import androidx.paging.LoadParams
import androidx.paging.LoadResult
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.local.dao.DirectoryMetaDao
import com.example.webdavplayer.data.local.dao.RemoteFileDao
import com.example.webdavplayer.data.local.entity.DirectoryMetaEntity
import com.example.webdavplayer.data.local.entity.RemoteFileEntity
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.repository.BrowseRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BrowseRepositoryImpl] 缓存优先 / TTL 条件刷新 单元测试（§1.3 优化）。
 *
 * 用假 DAO / 假 WebDavClient 直接构造仓库，验证：
 * - 缓存新鲜且非空 → [BrowseRepository.refreshIfStale] 不打 PROPFIND；
 * - 无元数据 / 超龄 / 缓存为空 → 触发刷新（PROPFIND）并记录时间戳；
 * - [BrowseRepository.refreshDirectory] 成功后写入目录刷新时间戳且缓存非空。
 */
class BrowseRepositoryStaleRefreshTest {

    private fun sampleFile(name: String, parentPath: String = "/"): RemoteFile = RemoteFile(
        id = "s1:$parentPath/$name",
        serverId = "s1",
        parentPath = parentPath,
        name = name,
        isDirectory = false,
        size = 0L,
        contentType = "video/mp4",
        lastModified = 0L,
        eTag = "",
        mediaType = MediaType.VIDEO,
    )

    private class FakeRemoteFileDao : RemoteFileDao {
        private val counts = mutableMapOf<Pair<String, String>, Int>()

        fun setCount(serverId: String, parentPath: String, n: Int) {
            counts[serverId to parentPath] = n
        }

        override suspend fun upsertAll(list: List<RemoteFileEntity>) {
            if (list.isNotEmpty()) {
                val e = list.first()
                counts[e.serverId to e.parentPath] = list.size
            }
        }

        override fun pagingSource(
            serverId: String,
            parentPath: String,
        ): PagingSource<Int, RemoteFileEntity> = object : PagingSource<Int, RemoteFileEntity>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RemoteFileEntity> =
                LoadResult.Page(emptyList(), null, null)

            override fun getRefreshKey(state: PagingState<Int, RemoteFileEntity>): Int? = null
        }

        override suspend fun clearDirectory(serverId: String, parentPath: String) {
            counts[serverId to parentPath] = 0
        }

        override suspend fun clearServer(serverId: String) {
            counts.keys.removeAll { it.first == serverId }
        }

        override suspend fun countDirectory(serverId: String, parentPath: String): Int =
            counts[serverId to parentPath] ?: 0
    }

    private class FakeDirectoryMetaDao : DirectoryMetaDao {
        private val store = mutableMapOf<String, DirectoryMetaEntity>()

        override suspend fun upsert(meta: DirectoryMetaEntity) {
            store[meta.id] = meta
        }

        override suspend fun get(serverId: String, parentPath: String): DirectoryMetaEntity? =
            store["$serverId::$parentPath"]

        override suspend fun clearDirectory(serverId: String, parentPath: String) {
            store.remove("$serverId::$parentPath")
        }

        override suspend fun clearServer(serverId: String) {
            store.keys.removeAll { it.startsWith("$serverId::") }
        }
    }

    private class FakeWebDavClient : WebDavClient {
        var listDirectoryCallCount = 0
        var listDirectoryResult: List<RemoteFile> = emptyList()

        override suspend fun connect(config: ServerConfig) = Unit
        override suspend fun listDirectory(path: String, depth: Int): List<RemoteFile> {
            listDirectoryCallCount++
            return listDirectoryResult
        }

        override suspend fun openStream(path: String): Source =
            throw UnsupportedOperationException("not used in browse test")

        override suspend fun upload(path: String, source: Source, size: Long?) = Unit
        override suspend fun rename(from: String, to: String) = Unit
        override suspend fun move(from: String, to: String) = Unit
        override suspend fun delete(path: String) = Unit
        override fun getOkHttpClient(): OkHttpClient = OkHttpClient()
    }

    private class FakeServerRepository : ServerRepository {
        override fun observeAll() = emptyFlow<List<ServerConfig>>()
        override suspend fun getAll(): List<ServerConfig> = emptyList()
        override suspend fun getById(id: String): ServerConfig =
            ServerConfig(id, "n", "https://x", "u", "p", AuthType.BASIC, false, 0)

        override suspend fun save(config: ServerConfig) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun connect(config: ServerConfig): Result<Unit> = Result.Success(Unit)
    }

    @Test
    fun refreshIfStale_whenFreshAndNonEmpty_doesNotHitNetwork() = runBlocking {
        val client = FakeWebDavClient()
        val files = FakeRemoteFileDao().apply { setCount("s1", "/", 3) }
        val meta = FakeDirectoryMetaDao().apply {
            upsert(DirectoryMetaEntity("s1::/", "s1", "/", System.currentTimeMillis()))
        }
        val repo = BrowseRepositoryImpl(client, files, meta, FakeServerRepository())

        repo.refreshIfStale("s1", "/")

        assertEquals(0, client.listDirectoryCallCount)
        assertTrue("缓存应判定为新鲜", repo.isCacheFresh("s1", "/"))
    }

    @Test
    fun refreshIfStale_whenNoMeta_triggersRefreshAndRecordsTimestamp() = runBlocking {
        val client = FakeWebDavClient().apply { listDirectoryResult = listOf(sampleFile("a.mp4")) }
        val files = FakeRemoteFileDao()
        val meta = FakeDirectoryMetaDao()
        val repo = BrowseRepositoryImpl(client, files, meta, FakeServerRepository())

        repo.refreshIfStale("s1", "/")

        assertEquals(1, client.listDirectoryCallCount)
        assertNotNull("应写入目录刷新时间戳", meta.get("s1", "/"))
    }

    @Test
    fun refreshIfStale_whenStaleMeta_triggersRefresh() = runBlocking {
        val client = FakeWebDavClient().apply { listDirectoryResult = listOf(sampleFile("a.mp4")) }
        val files = FakeRemoteFileDao().apply { setCount("s1", "/", 3) }
        val meta = FakeDirectoryMetaDao().apply {
            // 10 分钟前刷新，超过默认 5 分钟 TTL
            upsert(DirectoryMetaEntity("s1::/", "s1", "/", System.currentTimeMillis() - 10 * 60 * 1000))
        }
        val repo = BrowseRepositoryImpl(client, files, meta, FakeServerRepository())

        repo.refreshIfStale("s1", "/", maxAgeMs = 5 * 60 * 1000L)

        assertEquals("超龄应触发刷新", 1, client.listDirectoryCallCount)
    }

    @Test
    fun refreshIfStale_whenEmptyCache_triggersRefresh() = runBlocking {
        val client = FakeWebDavClient().apply { listDirectoryResult = listOf(sampleFile("a.mp4")) }
        val files = FakeRemoteFileDao().apply { setCount("s1", "/", 0) }
        val meta = FakeDirectoryMetaDao().apply {
            upsert(DirectoryMetaEntity("s1::/", "s1", "/", System.currentTimeMillis()))
        }
        val repo = BrowseRepositoryImpl(client, files, meta, FakeServerRepository())

        repo.refreshIfStale("s1", "/")

        assertEquals("空缓存应触发刷新", 1, client.listDirectoryCallCount)
    }

    @Test
    fun refreshDirectory_recordsMetaAndNonEmptyCache() = runBlocking {
        val client = FakeWebDavClient().apply {
            listDirectoryResult = listOf(sampleFile("a.mp4"), sampleFile("b.mp4"))
        }
        val files = FakeRemoteFileDao()
        val meta = FakeDirectoryMetaDao()
        val repo = BrowseRepositoryImpl(client, files, meta, FakeServerRepository())

        repo.refreshDirectory("s1", "/")

        val m = meta.get("s1", "/")
        assertNotNull(m)
        assertTrue("刷新时间戳应接近现在", System.currentTimeMillis() - (m!!.lastRefreshedAt) < 5000)
        assertEquals(2, files.countDirectory("s1", "/"))
    }
}
