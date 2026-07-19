package com.example.webdavplayer.domain.player

import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState

/**
 * 播放内核抽象（§4.2 / §8）。
 *
 * 上层（UI / 领域）**永远不直接引用 ExoPlayer / LibVLC**，一切经此接口。
 * 具体实现见 [com.example.webdavplayer.data.player.ExoPlayerEngine] 与 `VlcEngine`（full 风味）。
 * 内核只负责“当前这一条媒体的解码渲染”，进度与播放列表由 [com.example.webdavplayer.domain.player.PlaylistController] 持有。
 */
interface PlayerEngine {
    /** 准备媒体（流式，不整文件下载）。 */
    fun prepare(media: PlayableMedia)

    /** 开始播放。 */
    fun play()

    /** 暂停。 */
    fun pause()

    /** 拖动到指定位置（毫秒）。 */
    fun seekTo(positionMs: Long)

    /**
     * 设置播放倍速（1.0 = 正常速度）。
     * 内核负责把倍速作用到当前正在解码的媒体；切换媒体 / 内核时应由上层的
     * [com.example.webdavplayer.domain.repository.PlayerRepository] 重放，保证倍速持续生效。
     */
    fun setSpeed(speed: Float)

    /** 设置事件监听（可传 null 清除监听，用于后台服务销毁时解绑）。 */
    fun setListener(listener: EngineListener?)

    /** 当前状态快照。 */
    fun getState(): PlaybackState

    /** 释放内核资源（切换内核或退出前调用）。 */
    fun release()
}
