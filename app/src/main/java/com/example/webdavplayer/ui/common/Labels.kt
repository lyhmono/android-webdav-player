package com.example.webdavplayer.ui.common

import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState

/** 共享标签函数 —— 避免在各 Screen 中重复定义。 */

fun modeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "顺序"
    PlayMode.LOOP -> "循环"
    PlayMode.SHUFFLE -> "随机"
}

fun engineLabel(type: EngineType): String = when (type) {
    EngineType.MEDIA3 -> "Media3 / ExoPlayer"
    EngineType.VLC -> "libVLC"
}

fun stateLabel(state: PlaybackState): String = when (state) {
    PlaybackState.IDLE -> "空闲"
    PlaybackState.PREPARING -> "准备中"
    PlaybackState.READY -> "就绪"
    PlaybackState.PLAYING -> "播放中"
    PlaybackState.PAUSED -> "已暂停"
    PlaybackState.ENDED -> "播放结束"
    PlaybackState.ERROR -> "出错"
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
