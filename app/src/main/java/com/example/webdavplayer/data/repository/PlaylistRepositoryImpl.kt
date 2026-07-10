package com.example.webdavplayer.data.repository

import com.example.webdavplayer.data.local.dao.PlaylistDao
import com.example.webdavplayer.data.local.dao.PlaylistMetaDao
import com.example.webdavplayer.data.local.entity.PlaylistMetaEntity
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.repository.PlaylistRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放列表仓库实现（C2 持久化到 Room / §4）。
 *
 * 串联 P0 已存在的 [PlaylistDao] + [PlaylistItemEntity] 桩：
 * - 内存缓存（@Singleton 跨会话存活），启动即从 Room 载入全部条目与播放模式；
 * - 写操作（增删改/模式/重排）双写（内存 + Room），进程被杀不丢（C2 AC2）；
 * - `observeItems()` 按 `currentServerId` 过滤归属（C5 §7），默认呈现当前服务器列表；
 * - 顺序沿用 `addedAt`（拖拽重编号实现排序，零契约变更）。
 */
@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val dao: PlaylistDao,
    private val metaDao: PlaylistMetaDao,
    private val settingsRepository: SettingsRepository,
) : PlaylistRepository {

    private val cache = MutableStateFlow<List<PlaylistItem>>(emptyList())
    private val modeCache = MutableStateFlow(PlayMode.SEQUENTIAL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 启动即从 Room 载入（P0 无旧数据，首次为空列表；fallbackToDestructiveMigration 已处理升级）
        scope.launch {
            runCatching { dao.getAll() }.getOrDefault(emptyList())
                .let { cache.value = it.map { e -> e.toDomain() } }
            runCatching { metaDao.get() }.getOrNull()
                ?.let { modeCache.value = it.mode }
        }
    }

    override fun observeItems(): Flow<List<PlaylistItem>> =
        combine(settingsRepository.observeCurrentServerId(), cache) { sid, items ->
            if (sid == null) emptyList() else items.filter { it.serverId == sid }
        }

    override suspend fun addItems(items: List<PlaylistItem>, replace: Boolean) {
        val next = if (replace) items else cache.value + items
        cache.value = next
        dao.upsertAll(next.map { it.toEntity() }) // 双写 Room（全量，过滤在读取时按 serverId）
    }

    override suspend fun removeItem(id: String) {
        cache.value = cache.value.filterNot { it.id == id }
        dao.deleteById(id)
    }

    override suspend fun clear() {
        cache.value = emptyList()
        dao.clear()
    }

    override fun observeMode(): Flow<PlayMode> = modeCache.asStateFlow()

    override suspend fun setMode(mode: PlayMode) {
        modeCache.value = mode
        metaDao.set(PlaylistMetaEntity(mode = mode))
    }

    override suspend fun reorder(fromIndex: Int, toIndex: Int) {
        val sid = settingsRepository.getCurrentServerId() ?: return
        val all = cache.value
        val serverItems = all.filter { it.serverId == sid }.toMutableList()
        if (fromIndex !in serverItems.indices || toIndex !in serverItems.indices) return
        val moved = serverItems.removeAt(fromIndex)
        serverItems.add(toIndex, moved)
        // 重编号 addedAt（相对顺序保留），零契约变更（§4.1）
        val base = System.currentTimeMillis()
        val renumbered = serverItems.mapIndexed { i, it -> it.copy(addedAt = base + i) }
        val byId = renumbered.associateBy { it.id }
        cache.value = all.map { old -> byId[old.id] ?: old }
        dao.upsertAll(cache.value.map { it.toEntity() })
    }
}
