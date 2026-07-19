package com.example.webdavplayer.domain.repository

import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState

/**
 * 播放控制仓库接口（§4.2 / §6 T09）。
 * 持有当前 [com.example.webdavplayer.domain.player.PlayerEngine] 实例，
 * 应用内切换内核 = release 旧 + Factory.create 新 + prepare 当前媒体。
 */
interface PlayerRepository {
    /** 当前内核类型。 */
    fun getEngineType(): EngineType

    /**
     * 设置（切换）内核类型并即时重建引擎。
     * 若已有正在播放的媒体，会在新内核上恢复播放（§1.2）。
     */
    suspend fun setEngineType(type: EngineType)

    /** 准备媒体（连接服务器 → 取共享 OkHttp → 内核 prepare）。 */
    suspend fun prepare(media: PlayableMedia)

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)

    /**
     * 设置播放倍速（1.0 = 正常）。倍速作为播放偏好由本仓库持有，
     * 在 [prepare] / [setEngineType] 重建内核后自动重放，保证跨曲目 / 跨内核持续生效。
     */
    fun setSpeed(speed: Float)

    fun setListener(listener: EngineListener?)
    fun getState(): PlaybackState
    fun release()
}
