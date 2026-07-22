package com.example.webdavplayer.data.repository

import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放列表导航控制实现（§4.2 / §6 T08）。
 * 持有播放列表快照与当前索引，按 [PlayMode] 计算上/下一首。
 */
@Singleton
class PlaylistControllerImpl @Inject constructor() : PlaylistController {

    @Volatile
    private var items: List<PlaylistItem> = emptyList()
    @Volatile
    private var currentIndex = -1
    @Volatile
    private var mode: PlayMode = PlayMode.SEQUENTIAL

    override fun sync(items: List<PlaylistItem>) {
        val currentId = this.items.getOrNull(currentIndex)?.id
        this.items = items
        currentIndex = if (currentId != null) {
            items.indexOfFirst { it.id == currentId }
        } else {
            -1
        }
    }

    override fun setMode(mode: PlayMode) {
        this.mode = mode
    }

    override fun getMode(): PlayMode = mode

    override fun current(): PlaylistItem? = items.getOrNull(currentIndex)

    override fun setCurrent(item: PlaylistItem) {
        currentIndex = items.indexOfFirst { it.id == item.id }
    }

    override fun snapshot(): List<PlaylistItem> = items

    override fun next(): PlaylistItem? {
        if (items.isEmpty()) return null
        val nextIndex = computeNext()
        if (nextIndex < 0) return null
        currentIndex = nextIndex
        return items[currentIndex]
    }

    override fun previous(): PlaylistItem? {
        if (items.isEmpty()) return null
        val prevIndex = computePrev()
        if (prevIndex < 0) return null
        currentIndex = prevIndex
        return items[currentIndex]
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
