package com.example.webdavplayer.data.repository

import com.example.webdavplayer.data.local.EnginePreference
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** 轻量偏好仓库实现：DataStore 持久化内核选择 + 当前服务器（§8 / T11 + C5）。 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val enginePreference: EnginePreference,
) : SettingsRepository {

    override fun observeEngineType(): Flow<EngineType> = enginePreference.engineTypeFlow

    override fun getEngineType(): EngineType = runBlocking { enginePreference.get() }

    override suspend fun setEngineType(type: EngineType) = enginePreference.set(type)

    override fun observeCurrentServerId(): Flow<String?> = enginePreference.currentServerIdFlow

    override fun getCurrentServerId(): String? = runBlocking { enginePreference.getCurrentServerId() }

    override suspend fun setCurrentServerId(id: String?) = enginePreference.setCurrentServerId(id)
}
