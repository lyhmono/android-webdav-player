package com.example.webdavplayer.domain.model

/** 播放内核事件回调（§4.2）。上层通过它感知状态/进度/结束，无需直接引用内核对象。 */
interface EngineListener {
    fun onStateChange(state: PlaybackState)
    fun onProgress(positionMs: Long, durationMs: Long)
    fun onEnded()
    fun onError(throwable: Throwable)

    /** 视频尺寸变化（宽/高，用于 UI 层计算 aspectRatio 保持原始比例）。 */
    fun onVideoSizeChanged(width: Int, height: Int) {}
}
