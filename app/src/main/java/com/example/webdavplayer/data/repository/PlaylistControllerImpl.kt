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
        val oldIndex = currentIndex
        val currentId = this.items.getOrNull(oldIndex)?.id
        this.items = items
        currentIndex = when {
            currentId != null -> {
                val idx = items.indexOfFirst { it.id == currentId }
                if (idx >= 0) {
                    idx
                } else {
                    // 当前项已被删除：兜底到原槽位（被删项之后的项会前移补齐该槽位），
                    // 使控制器仍指向一个有效项而不至于失步为 -1（§一致性）。
                    fallbackIndex(oldIndex, items)
                }
            }
            else -> -1
        }
    }

    /** 当前项被删除时的兜底索引：保持原槽位；列表为空返回 -1。 */
    private fun fallbackIndex(oldIndex: Int, items: List<PlaylistItem>): Int =
        if (items.isEmpty()) -1 else oldIndex.coerceIn(0, items.lastIndex)

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
        currentIndex = computeNext()
        return items.getOrNull(currentIndex)
    }

    override fun previous(): PlaylistItem? {
        if (items.isEmpty()) return null
        currentIndex = computePrev()
        return items.getOrNull(currentIndex)
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
