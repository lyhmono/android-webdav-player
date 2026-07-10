package com.example.webdavplayer.data.repository

import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.local.ServerConfigStore
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** 服务器配置仓库实现：加密存储 + 连接委托（§1.5 / T11）。 */
@Singleton
class ServerRepositoryImpl @Inject constructor(
    private val store: ServerConfigStore,
    private val webDavClient: WebDavClient,
) : ServerRepository {

    override fun observeAll(): Flow<List<ServerConfig>> = store.observe()

    override suspend fun getAll(): List<ServerConfig> = store.loadAll()

    override suspend fun getById(id: String): ServerConfig? =
        store.loadAll().firstOrNull { it.id == id }

    override suspend fun save(config: ServerConfig) {
        val list = store.loadAll().filterNot { it.id == config.id } + config
        store.saveAll(list)
    }

    override suspend fun delete(id: String) {
        store.saveAll(store.loadAll().filterNot { it.id == id })
    }

    override suspend fun connect(config: ServerConfig): Result<Unit> =
        Result.runCatching { webDavClient.connect(config) }
}
