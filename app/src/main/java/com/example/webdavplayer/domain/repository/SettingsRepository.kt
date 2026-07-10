package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.EngineType
import kotlinx.coroutines.flow.Flow

/**
 * 轻量偏好仓库（§8 / T11）。
 * 用 DataStore 持久化当前播放内核选择，供 PlayerRepository 读取。
 *
 * C5（P1）：扩展 `currentServerId` —— 记住“当前服务器”，重启后仍指向最后使用的服务器，
 * 并在切换时驱动播放列表按归属过滤（§7 / T12）。接口向后兼容，既有方法不变。
 */
interface SettingsRepository {
    fun observeEngineType(): Flow<EngineType>
    fun getEngineType(): EngineType
    suspend fun setEngineType(type: EngineType)

    /** 观察当前服务器 id（可能为 null：尚未选择）。 */
    fun observeCurrentServerId(): Flow<String?>

    /** 读取当前服务器 id（同步，用于非挂起上下文）。 */
    fun getCurrentServerId(): String?

    /** 写入当前服务器 id（null 表示清除）。 */
    suspend fun setCurrentServerId(id: String?)
}
