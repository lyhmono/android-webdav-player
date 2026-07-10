package com.example.webdavplayer.domain.player

import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem

/**
 * 播放列表导航控制（§4.2）。
 *
 * 与内核解耦：持有播放列表快照与当前索引，按 [PlayMode] 计算
 * 上一条 / 下一条 / 自然结束后下一首。进度与顺序的真相源在上层。
 */
interface PlaylistController {
    /** 同步最新播放列表快照（由观察 PlaylistRepository 的流驱动）。 */
    fun sync(items: List<PlaylistItem>)

    /** 设置播放模式。 */
    fun setMode(mode: PlayMode)

    /** 读取当前播放模式（C3-AC4：用于判断自然结束后是否清除断点）。 */
    fun getMode(): PlayMode

    /** 当前项（可能为 null）。 */
    fun current(): PlaylistItem?

    /** 将某一项标记为“当前”（点击列表项时调用）。 */
    fun setCurrent(item: PlaylistItem)

    /** 当前列表快照（供媒体会话构建播放列表/计算索引，C1 使用）。 */
    fun snapshot(): List<PlaylistItem>

    /** 下一首（按模式计算）。SEQUENTIAL 到末尾返回 null。 */
    fun next(): PlaylistItem?

    /** 上一首（按模式计算）。 */
    fun previous(): PlaylistItem?

    /** 单条媒体自然结束 → 返回应播放的下一首（由播放器调用）。 */
    fun onItemEnded(): PlaylistItem?
}
