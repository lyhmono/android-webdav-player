package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

/**
 * 播放列表仓库接口（§4.2 / §6 T08）。
 *
 * P0 采用内存态实现（[com.example.webdavplayer.data.repository.PlaylistRepositoryImpl]），
 * P1 持久化到 Room（T12）仅替换实现，接口不变。
 */
interface PlaylistRepository {
    /** 观察当前播放列表项。 */
    fun observeItems(): Flow<List<PlaylistItem>>

    /** 添加项；[replace]=true 时先清空再添加（长按目录默认追加）。 */
    suspend fun addItems(items: List<PlaylistItem>, replace: Boolean)

    /** 移除单项。 */
    suspend fun removeItem(id: String)

    /** 清空。 */
    suspend fun clear()

    /** C2/H：删除某一服务器的全部播放列表项（删除服务器时清理孤儿行）。 */
    suspend fun clearServer(serverId: String)

    /** 观察播放模式。 */
    fun observeMode(): Flow<PlayMode>

    /** 设置播放模式。 */
    suspend fun setMode(mode: PlayMode)

    /** C2：拖拽重排当前服务器列表（重编号 addedAt 落库，零契约变更）。 */
    suspend fun reorder(fromIndex: Int, toIndex: Int)
}
