package com.example.webdavplayer.domain.model

/** 播放状态（PlayerEngine 状态机）。 */
enum class PlaybackState {
    IDLE,       // 空闲
    PREPARING,  // 准备中
    READY,      // 已就绪（可播放）
    PLAYING,    // 播放中
    PAUSED,     // 暂停
    ENDED,      // 播放结束
    ERROR,      // 出错
}
