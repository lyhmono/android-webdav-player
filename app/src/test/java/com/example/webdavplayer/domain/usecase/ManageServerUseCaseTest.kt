package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackProgress
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import com.example.webdavplayer.domain.repository.PlaylistRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import com.example.webdavplayer.domain.repository.TrustedCertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ManageServerUseCase] 单元测试（C5 多账户：记住“当前服务器”；H 删除服务器清理归属数据）。
 *
 * 验证 [ManageServerUseCase.selectServer] 经 [SettingsRepository.setCurrentServerId]
 * 写入 currentServerId；导航（navController.navigate("browse/{id}")）由 UI 层
 * [com.example.webdavplayer.ui.servers.ServerListScreen] 负责，已静态核验。
 *
 * 验证 [ManageServerUseCase.removeServer] 在删除服务器时，连同清理该服务器的
 * 播放列表项与播放断点（H：避免孤儿行、避免误删其它服务器数据）。
 */
class ManageServerUseCaseTest {

    private class FakeSettings : SettingsRepository {
        private val flow = MutableStateFlow<String?>(null)
        override fun observeCurrentServerId() = flow
        override fun getCurrentServerId() = flow.value
        override suspend fun setCurrentServerId(id: String?) {
            flow.value = id
        }
        override fun observeEngineType() = flowOf(EngineType.MEDIA3)
        override fun getEngineType() = EngineType.MEDIA3
        override suspend fun setEngineType(type: EngineType) {}
    }

    private class FakeServers : ServerRepository {
        override fun observeAll() = emptyFlow<List<ServerConfig>>()
        override suspend fun getAll() = emptyList<ServerConfig>()
        override suspend fun getById(id: String) = null
        override suspend fun save(config: ServerConfig) {}
        override suspend fun delete(id: String) {}
        override suspend fun connect(config: ServerConfig) = Result.success(Unit)
    }

    private class FakeCerts : TrustedCertRepository {
        override fun observeAll() = emptyFlow<List<TrustedCert>>()
        override suspend fun getByServer(serverId: String) = emptyList<TrustedCert>()
        override suspend fun getFingerprints(serverId: String) = emptyList<String>()
        override suspend fun add(cert: TrustedCert) {}
        override suspend fun remove(id: String) {}
        override suspend fun removeByServer(serverId: String) {}
    }

    private class FakePlaylist : PlaylistRepository {
        val items = MutableStateFlow<List<PlaylistItem>>(emptyList())
        val clearedServers = mutableListOf<String>()
        override fun observeItems() = items
        override suspend fun addItems(newItems: List<PlaylistItem>, replace: Boolean) {
            items.value = if (replace) newItems else items.value + newItems
        }
        override suspend fun removeItem(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
        override suspend fun clear() {
            items.value = emptyList()
        }
        override suspend fun clearServer(serverId: String) {
            clearedServers.add(serverId)
            items.value = items.value.filterNot { it.serverId == serverId }
        }
        override fun observeMode() = flowOf(PlayMode.SEQUENTIAL)
        override suspend fun setMode(mode: PlayMode) {}
        override suspend fun reorder(fromIndex: Int, toIndex: Int) {}
    }

    private class FakeProgress : PlaybackProgressRepository {
        val clearedServers = mutableListOf<String>()
        override suspend fun save(progress: PlaybackProgress) {}
        override suspend fun get(serverId: String, path: String): PlaybackProgress? = null
        override suspend fun clear(serverId: String, path: String) {}
        override suspend fun clearServer(serverId: String) {
            clearedServers.add(serverId)
        }
        override suspend fun clearAll() {}
    }

    @Test
    fun selectServer_writesCurrentServerId() = runBlocking {
        val settings = FakeSettings()
        val useCase = ManageServerUseCase(FakeServers(), FakeCerts(), settings, FakePlaylist(), FakeProgress())
        useCase.selectServer("srv2")
        assertEquals("srv2", settings.getCurrentServerId())
        assertEquals("srv2", useCase.observeCurrentServerId().first())
    }

    @Test
    fun removeServer_cleansPlaylistItemsAndProgress() = runBlocking {
        val playlist = FakePlaylist()
        val progress = FakeProgress()
        playlist.addItems(
            listOf(
                PlaylistItem("a", "srv1", "/a.mp4", "a", MediaType.VIDEO, 0L, 1L),
                PlaylistItem("b", "srv2", "/b.mp4", "b", MediaType.VIDEO, 0L, 2L),
            ),
            replace = true,
        )
        val useCase = ManageServerUseCase(FakeServers(), FakeCerts(), FakeSettings(), playlist, progress)

        useCase.removeServer("srv1")

        // srv1 的列表项被清理，srv2 不受影响（无孤儿行、无跨服务器误删）
        assertEquals(emptyList<String>(), playlist.items.value.filter { it.serverId == "srv1" }.map { it.id })
        assertEquals(listOf("b"), playlist.items.value.filter { it.serverId == "srv2" }.map { it.id })
        // 播放列表与断点均按 serverId 清理
        assertEquals(listOf("srv1"), playlist.clearedServers)
        assertEquals(listOf("srv1"), progress.clearedServers)
    }
}
