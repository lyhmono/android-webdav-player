package com.example.webdavplayer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.PreferenceDataStoreFactory
import com.example.webdavplayer.domain.model.EngineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放内核选择 + 当前服务器偏好（§8 / T11 + C5）。
 * 用 DataStore 持久化当前内核类型与“当前服务器 id”，复用同一 `settings` DataStore 文件。
 */
@Singleton
class EnginePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("settings")
    }

    private val key = stringPreferencesKey("engine_type")
    private val currentServerKey = stringPreferencesKey("current_server_id")

    val engineTypeFlow: Flow<EngineType> = dataStore.data.map { prefs ->
        prefs[key]
            ?.let { runCatching { EngineType.valueOf(it) }.getOrNull() }
            ?: EngineType.MEDIA3
    }

    suspend fun get(): EngineType =
        dataStore.data.first()[key]
            ?.let { runCatching { EngineType.valueOf(it) }.getOrNull() }
            ?: EngineType.MEDIA3

    suspend fun set(type: EngineType) {
        dataStore.edit { it[key] = type.name }
    }

    /** 观察当前服务器 id（C5）。 */
    val currentServerIdFlow: Flow<String?> = dataStore.data.map { prefs -> prefs[currentServerKey] }

    /** 读取当前服务器 id（C5）。 */
    suspend fun getCurrentServerId(): String? = dataStore.data.first()[currentServerKey]

    /** 写入当前服务器 id（null 表示清除，C5）。 */
    suspend fun setCurrentServerId(id: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(currentServerKey) else prefs[currentServerKey] = id
        }
    }
}
