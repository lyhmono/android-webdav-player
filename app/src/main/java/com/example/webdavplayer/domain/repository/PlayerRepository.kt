package com.example.webdavplayer.domain.repository

import androidx.media3.exoplayer.ExoPlayer
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
    fun setListener(listener: EngineListener?)
    fun getState(): PlaybackState
    fun release()

    /**
     * 获取底层 ExoPlayer 实例（供 UI 绑定 Surface 渲染视频画面）。
     * 若当前内核非 Media3/ExoPlayer 或引擎尚未创建，返回 null。
     */
    fun getExoPlayer(): ExoPlayer?

    /**
     * UI 层创建 Surface 后通过此方法传给 VLC 内核。
     * 若当前内核非 VLC，此方法无效果。
     */
    fun setVlcSurface(surface: android.view.Surface?)

    /**
     * 当前引擎是否为 VLC。
     */
    fun isVlcEngine(): Boolean
}
