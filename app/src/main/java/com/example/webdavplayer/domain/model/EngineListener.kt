package com.example.webdavplayer.domain.model

/** 播放内核事件回调（§4.2）。上层通过它感知状态/进度/结束，无需直接引用内核对象。 */
interface EngineListener {
    /** 状态变化。 */
    fun onStateChange(state: PlaybackState)

    /** 播放进度回调（毫秒）。 */
    fun onProgress(positionMs: Long, durationMs: Long)

    /** 单条媒体自然播放结束。 */
    fun onEnded()

    /** 出错。 */
    fun onError(throwable: Throwable)
}
