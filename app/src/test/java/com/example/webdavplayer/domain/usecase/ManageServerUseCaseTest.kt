package com.example.webdavplayer.domain.usecase

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.domain.repository.ServerRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import com.example.webdavplayer.domain.repository.TrustedCertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ManageServerUseCase] 单元测试（C5 多账户：记住“当前服务器”）。
 *
 * 验证 [ManageServerUseCase.selectServer] 经 [SettingsRepository.setCurrentServerId]
 * 写入 currentServerId；导航（navController.navigate("browse/{id}")）由 UI 层
 * [com.example.webdavplayer.ui.servers.ServerListScreen] 负责，已静态核验。
 */
class ManageServerUseCaseTest {

    private class FakeSettings : SettingsRepository {
        private val flow = MutableStateFlow<String?>(null)
        override fun observeCurrentServerId() = flow
        override fun getCurrentServerId() = flow.value
        override suspend fun setCurrentServerId(id: String?) {
            flow.value = id
        }
        override fun observeEngineType() = kotlinx.coroutines.flow.flowOf(EngineType.MEDIA3)
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

    @Test
    fun selectServer_writesCurrentServerId() = runBlocking {
        val settings = FakeSettings()
        val useCase = ManageServerUseCase(FakeServers(), FakeCerts(), settings)
        useCase.selectServer("srv2")
        assertEquals("srv2", settings.getCurrentServerId())
        assertEquals("srv2", useCase.observeCurrentServerId().first())
    }
}
