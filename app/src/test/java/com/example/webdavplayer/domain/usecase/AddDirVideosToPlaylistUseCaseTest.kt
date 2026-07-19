package com.example.webdavplayer.domain.usecase

import androidx.paging.PagingData
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.repository.BrowseRepository
import com.example.webdavplayer.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okio.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AddDirVideosToPlaylistUseCase] 单元测试（§1.5 / §6 T08）。
 * 用假 Repository 直接实例化（构造注入），验证“仅视频入列 + 路径拼接 + replace 开关”。
 */
class AddDirVideosToPlaylistUseCaseTest {

    private fun remote(
        name: String,
        mediaType: MediaType,
        serverId: String = "srv1",
        parentPath: String = "/movies",
    ): RemoteFile = RemoteFile(
        id = "$serverId:$parentPath/$name",
        serverId = serverId,
        parentPath = parentPath,
        name = name,
        isDirectory = false,
        size = 0L,
        contentType = "",
        lastModified = 0L,
        eTag = "",
        mediaType = mediaType,
    )

    private class FakeBrowseRepository(private val files: List<RemoteFile>) : BrowseRepository {
        override fun getDirectory(serverId: String, path: String): Flow<PagingData<RemoteFile>> = emptyFlow()
        override suspend fun refreshDirectory(serverId: String, path: String) = Unit
        override suspend fun refreshIfStale(serverId: String, path: String, maxAgeMs: Long) = Unit
        override suspend fun isCacheFresh(serverId: String, path: String, maxAgeMs: Long): Boolean = false
        override suspend fun getLastRefreshedAt(serverId: String, path: String): Long? = null
        override suspend fun listDirectory(serverId: String, path: String): List<RemoteFile> = files
        override suspend fun rename(serverId: String, fromPath: String, toName: String) = Unit
        override suspend fun move(serverId: String, fromPath: String, toPath: String) = Unit
        override suspend fun delete(serverId: String, path: String) = Unit
        override suspend fun upload(serverId: String, path: String, source: Source, size: Long?) = Unit
    }

    private class FakeBrowseRepositoryThatThrows(private val error: Throwable) : BrowseRepository {
        override fun getDirectory(serverId: String, path: String): Flow<PagingData<RemoteFile>> = emptyFlow()
        override suspend fun refreshDirectory(serverId: String, path: String) = Unit
        override suspend fun refreshIfStale(serverId: String, path: String, maxAgeMs: Long) = Unit
        override suspend fun isCacheFresh(serverId: String, path: String, maxAgeMs: Long): Boolean = false
        override suspend fun getLastRefreshedAt(serverId: String, path: String): Long? = null
        override suspend fun listDirectory(serverId: String, path: String): List<RemoteFile> = throw error
        override suspend fun rename(serverId: String, fromPath: String, toName: String) = Unit
        override suspend fun move(serverId: String, fromPath: String, toPath: String) = Unit
        override suspend fun delete(serverId: String, path: String) = Unit
        override suspend fun upload(serverId: String, path: String, source: Source, size: Long?) = Unit
    }

    private class FakePlaylistRepository : PlaylistRepository {
        var addedItems: List<PlaylistItem>? = null
        var addedReplace: Boolean? = null

        override fun observeItems(): Flow<List<PlaylistItem>> = emptyFlow()
        override suspend fun addItems(items: List<PlaylistItem>, replace: Boolean) {
            addedItems = items
            addedReplace = replace
        }
        override suspend fun removeItem(id: String) = Unit
        override suspend fun clear() = Unit
        override suspend fun reorder(fromIndex: Int, toIndex: Int) = Unit
        override fun observeMode(): Flow<PlayMode> = emptyFlow()
        override suspend fun setMode(mode: PlayMode) = Unit
    }

    @Test
    fun invoke_filtersOnlyVideosAndBuildsItems() = runBlocking {
        val files = listOf(
            remote("a.mp4", MediaType.VIDEO),
            remote("b.mp3", MediaType.AUDIO),
            remote("c.txt", MediaType.OTHER),
            remote("d.mkv", MediaType.VIDEO),
        )
        val browse = FakeBrowseRepository(files)
        val playlist = FakePlaylistRepository()
        val useCase = AddDirVideosToPlaylistUseCase(browse, playlist)

        val result = useCase("srv1", "/movies")

        assertTrue(result.isSuccess)
        val items = result.getOrNull()!!
        assertEquals(2, items.size)
        assertEquals("a.mp4", items[0].name)
        assertEquals("d.mkv", items[1].name)
        items.forEach { assertEquals(MediaType.VIDEO, it.mediaType) }
        // 路径与 id 拼接正确（joinPath 行为）
        assertEquals("/movies/a.mp4", items[0].path)
        assertEquals("srv1:/movies/a.mp4", items[0].id)
        // addItems 被调用且默认追加（replace=false）
        assertEquals(2, playlist.addedItems?.size)
        assertEquals(false, playlist.addedReplace)
    }

    @Test
    fun invoke_withReplace_true_passesReplaceFlag() = runBlocking {
        val files = listOf(remote("a.mp4", MediaType.VIDEO))
        val browse = FakeBrowseRepository(files)
        val playlist = FakePlaylistRepository()
        val useCase = AddDirVideosToPlaylistUseCase(browse, playlist)

        useCase("srv1", "/movies", replace = true)

        assertEquals(true, playlist.addedReplace)
        assertEquals(1, playlist.addedItems?.size)
    }

    @Test
    fun invoke_emptyDirectory_returnsEmptyAndAddsNothing() = runBlocking {
        val browse = FakeBrowseRepository(emptyList())
        val playlist = FakePlaylistRepository()
        val useCase = AddDirVideosToPlaylistUseCase(browse, playlist)

        val result = useCase("srv1", "/")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
        assertEquals(emptyList<PlaylistItem>(), playlist.addedItems)
    }

    @Test
    fun invoke_rootDirPath_joinKeepsLeadingSlash() = runBlocking {
        // dirPath="/" 应以 "/" 直接拼接文件名（joinPath 中 parent.endsWith("/") 分支）
        val files = listOf(remote("root.mp4", MediaType.VIDEO, parentPath = "/"))
        val browse = FakeBrowseRepository(files)
        val playlist = FakePlaylistRepository()
        val useCase = AddDirVideosToPlaylistUseCase(browse, playlist)

        val items = useCase("srv1", "/").getOrNull()!!

        assertEquals("/root.mp4", items[0].path)
        assertEquals("srv1:/root.mp4", items[0].id)
    }

    @Test
    fun invoke_propagatesErrorWhenBrowseFails() = runBlocking {
        val browse = FakeBrowseRepositoryThatThrows(RuntimeException("network down"))
        val playlist = FakePlaylistRepository()
        val useCase = AddDirVideosToPlaylistUseCase(browse, playlist)

        val result = useCase("srv1", "/movies")

        assertTrue(result.isError)
    }
}
