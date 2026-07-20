package com.example.webdavplayer.data.repository

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.CachedMedia
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.model.SubtitleTrack
import com.example.webdavplayer.domain.repository.CacheRepository
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.ServerRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MediaResolverImpl.discoverSubtitles] 单元测试（P2，纯 JVM）。
 *
 * 验证：在父目录中按「同名前缀」筛选同级字幕、剔除非字幕/异名文件，
 * 并基于服务器 baseUrl 拼出带鉴权的绝对字幕 URI；服务器缺失时返回空列表。
 */
class MediaResolverSubtitleDiscoveryTest {

    private fun server(id: String = "s1", baseUrl: String = "https://dav.example.com") = ServerConfig(
        id = id,
        name = "主服",
        baseUrl = baseUrl,
        username = "u",
        encryptedPassword = "p",
        authType = AuthType.NONE,
        trustSelfSigned = false,
        createdAt = 0,
    )

    private fun file(name: String, parent: String = "/movies") = RemoteFile(
        id = "s1:$parent/$name",
        serverId = "s1",
        parentPath = parent,
        name = name,
        isDirectory = false,
        size = 10,
        contentType = "",
        lastModified = 0,
        eTag = "",
        mediaType = MediaType.OTHER,
    )

    @Test
    fun discoverSubtitles_filters_siblings_and_builds_uri() = runBlocking {
        val dir = listOf(
            file("foo.mp4"),
            file("foo.zh.srt"),
            file("foo.en.srt"),
            file("bar.srt"),
            file("notes.txt"),
        )
        val repo = MediaResolverImpl(
            FakeServerRepository(mapOf("s1" to server())),
            FakeCacheRepository(),
            FakeWebDavClient(mapOf("/movies" to dir)),
        )
        val item = PlaylistItem("s1:/movies/foo.mp4", "s1", "/movies/foo.mp4", "foo.mp4", MediaType.VIDEO, 0, 0)

        val subs = repo.discoverSubtitles(item)

        assertEquals(2, subs.size) // foo.zh.srt + foo.en.srt；bar.srt 异名、notes.txt 非字幕被剔除
        assertEquals("https://dav.example.com/movies/foo.zh.srt", subs[0].uri)
        assertEquals("zh", subs[0].language)
        assertEquals("application/x-subrip", subs[0].mimeType)
        assertEquals("en", subs[1].language)
    }

    @Test
    fun discoverSubtitles_returns_empty_when_server_missing() = runBlocking {
        val repo = MediaResolverImpl(
            FakeServerRepository(emptyMap()),
            FakeCacheRepository(),
            FakeWebDavClient(emptyMap()),
        )
        val item = PlaylistItem("sX:/a.mp4", "sX", "/a.mp4", "a.mp4", MediaType.VIDEO, 0, 0)
        assertEquals(emptyList<SubtitleTrack>(), repo.discoverSubtitles(item))
    }

    @Test
    fun discoverSubtitles_returns_empty_when_no_subtitles() = runBlocking {
        val dir = listOf(file("foo.mp4"), file("bar.mkv"))
        val repo = MediaResolverImpl(
            FakeServerRepository(mapOf("s1" to server())),
            FakeCacheRepository(),
            FakeWebDavClient(mapOf("/movies" to dir)),
        )
        val item = PlaylistItem("s1:/movies/foo.mp4", "s1", "/movies/foo.mp4", "foo.mp4", MediaType.VIDEO, 0, 0)
        assertEquals(emptyList<SubtitleTrack>(), repo.discoverSubtitles(item))
    }

    private class FakeServerRepository(
        private val servers: Map<String, ServerConfig>,
    ) : ServerRepository {
        override fun observeAll() = flowOf(servers.values.toList())
        override suspend fun getAll(): List<ServerConfig> = servers.values.toList()
        override suspend fun getById(id: String): ServerConfig? = servers[id]
        override suspend fun save(config: ServerConfig) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun connect(config: ServerConfig): Result<Unit> = Result.success(Unit)
    }

    private class FakeCacheRepository : CacheRepository {
        override suspend fun download(serverId: String, path: String): Result<CachedMedia> =
            Result.error(UnsupportedOperationException())

        override suspend fun getLocalFilePath(serverId: String, path: String): String? = null
        override fun observeAll() = emptyFlow<List<CachedMedia>>()
        override suspend fun delete(id: String) = Unit
    }

    private class FakeWebDavClient(
        private val dirs: Map<String, List<RemoteFile>>,
    ) : WebDavClient {
        override suspend fun connect(config: ServerConfig) = Unit
        override suspend fun listDirectory(path: String, depth: Int): List<RemoteFile> = dirs[path] ?: emptyList()
        override suspend fun openStream(path: String): Source = throw UnsupportedOperationException()
        override suspend fun upload(path: String, source: Source, size: Long?) = Unit
        override suspend fun rename(from: String, to: String) = Unit
        override suspend fun move(from: String, to: String) = Unit
        override suspend fun delete(path: String) = Unit
        override fun getOkHttpClient(): OkHttpClient = throw UnsupportedOperationException()
    }
}
