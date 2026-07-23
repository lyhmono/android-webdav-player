package com.example.webdavplayer.data.repository

import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放列表导航控制实现（§4.2 / §6 T08）。
 * 持有播放列表快照与当前索引，按 [PlayMode] 计算上/下一首。
 * 所有状态访问通过 [stateLock] 同步，保证并发安全。
 */
@Singleton
class PlaylistControllerImpl @Inject constructor() : PlaylistController {

    private val stateLock = Any()

    @Volatile
    private var items: List<PlaylistItem> = emptyList()
    @Volatile
    private var currentIndex = -1
    @Volatile
    private var mode: PlayMode = PlayMode.SEQUENTIAL

    override fun sync(items: List<PlaylistItem>) = synchronized(stateLock) {
        val currentId = this.items.getOrNull(currentIndex)?.id
        this.items = items
        currentIndex = if (currentId != null) {
            items.indexOfFirst { it.id == currentId }
        } else {
            -1
        }
    }

    override fun setMode(mode: PlayMode) = synchronized(stateLock) {
        this.mode = mode
    }

    override fun getMode(): PlayMode = synchronized(stateLock) { mode }

    override fun current(): PlaylistItem? = synchronized(stateLock) {
        items.getOrNull(currentIndex)
    }

    override fun setCurrent(item: PlaylistItem) = synchronized(stateLock) {
        currentIndex = items.indexOfFirst { it.id == item.id }
    }

    override fun snapshot(): List<PlaylistItem> = synchronized(stateLock) { items }

    override fun next(): PlaylistItem? = synchronized(stateLock) {
        if (items.isEmpty()) return null
        val nextIndex = computeNext()
        if (nextIndex < 0) return null
        currentIndex = nextIndex
        items[currentIndex]
    }

    override fun previous(): PlaylistItem? = synchronized(stateLock) {
        if (items.isEmpty()) return null
        val prevIndex = computePrev()
        if (prevIndex < 0) return null
        currentIndex = prevIndex
        items[currentIndex]
    }

    override fun onItemEnded(): PlaylistItem? = next()

    private fun computeNext(): Int = when (mode) {
        PlayMode.SEQUENTIAL -> if (currentIndex + 1 < items.size) currentIndex + 1 else -1
        PlayMode.LOOP -> (currentIndex + 1).mod(items.size)
        PlayMode.SHUFFLE -> randomIndexExcludingCurrent()
    }

    private fun computePrev(): Int = when (mode) {
        PlayMode.SEQUENTIAL -> (currentIndex - 1).coerceAtLeast(0)
        PlayMode.LOOP -> (currentIndex - 1 + items.size).mod(items.size)
        PlayMode.SHUFFLE -> randomIndexExcludingCurrent()
    }

    /** 随机选取一个不同于当前索引的位置（列表 > 1 时保证不重复）。 */
    private fun randomIndexExcludingCurrent(): Int {
        if (items.size <= 1) return currentIndex.coerceAtLeast(0)
        val candidates = items.indices.toMutableList().apply { remove(currentIndex) }
        return candidates.random()
    }
}
