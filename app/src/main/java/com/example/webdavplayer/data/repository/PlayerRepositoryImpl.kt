package com.example.webdavplayer.data.repository

import android.content.Context
import android.view.TextureView
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.webdavplayer.data.player.ExoPlayerEngine
import com.example.webdavplayer.data.player.PlayerEngineFactory
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.player.PlayerEngine
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放控制仓库实现（§6 T09）。
 *
 * 持有当前 [PlayerEngine]；应用内切换内核 = `release()` 旧 + `Factory.create()` 新
 * + `prepare()` 当前媒体（§1.2）。内核无状态记忆，进度/列表由上层持有。
 *
 * 视频 Surface 穿透抽象层直达内核：[setVideoSurface] 缓存 [videoSurface] 并转发给当前引擎，
 * 在 [prepare] / [setEngineType] 重建引擎后通过 [reapplyVideoSurface] 重新绑定，避免画面丢失。
 */
@Singleton
class PlayerRepositoryImpl @Inject constructor(
    private val webDavClient: WebDavClient,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val playerEngineFactory: PlayerEngineFactory,
    @ApplicationContext private val context: Context,
) : PlayerRepository {

    @Volatile
    private var engine: PlayerEngine? = null
    @Volatile
    private var currentMedia: PlayableMedia? = null
    @Volatile
    private var listener: EngineListener? = null
    /** 当前倍速（播放偏好，跨曲目 / 跨内核重建后重放）。可能从非主线程设置，需保证可见性。 */
    @Volatile
    private var currentSpeed: Float = 1.0f
    private var videoSurface: TextureView? = null

    override fun getEngineType(): EngineType = settingsRepository.getEngineType()

    override suspend fun setEngineType(type: EngineType) = withContext(Dispatchers.Main) {
        if (engine != null && settingsRepository.getEngineType() == type) return@withContext
        settingsRepository.setEngineType(type)
        val media = currentMedia ?: return@withContext
        val wasPlaying = engine?.getState() == PlaybackState.PLAYING
        engine?.release()
        engine = playerEngineFactory.create(type, context)
        listener?.let { engine!!.setListener(it) }
        reapplyVideoSurface()
        connectFor(media)
        // ExoPlayer 内核需要注入共享 OkHttp（含自签信任 + 鉴权）；
        // VLC 内核使用 libVLC 自建网络栈，不需要也不支持此注入。
        (engine as? ExoPlayerEngine)?.setOkHttpClient(webDavClient.getOkHttpClient())
        engine!!.prepare(media)
        engine!!.setSpeed(currentSpeed)
        if (wasPlaying) engine!!.play()
    }

    override suspend fun prepare(media: PlayableMedia) = withContext(Dispatchers.Main) {
        currentMedia = media
        if (engine == null) {
            engine = playerEngineFactory.create(settingsRepository.getEngineType(), context)
        }
        listener?.let { engine!!.setListener(it) }
        reapplyVideoSurface()
        connectFor(media)
        // ExoPlayer 内核需要注入共享 OkHttp；VLC 自建网络栈无需此注入。
        (engine as? ExoPlayerEngine)?.setOkHttpClient(webDavClient.getOkHttpClient())
        engine!!.prepare(media)
        engine!!.setSpeed(currentSpeed)
    }

    override fun play() {
        engine?.play()
    }

    override fun pause() {
        engine?.pause()
    }

    override fun seekTo(positionMs: Long) {
        engine?.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        engine?.setSpeed(speed)
    }

    override fun selectSubtitle(language: String?) {
        engine?.selectSubtitle(language)
    }

    override fun enableSubtitles() {
        engine?.enableSubtitles()
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
        engine?.setListener(listener)
    }

    override fun getState(): PlaybackState = engine?.getState() ?: PlaybackState.IDLE

    override fun getCurrentPosition(): Long = engine?.getCurrentPosition() ?: 0L

    override fun getDurationMs(): Long = engine?.getDurationMs() ?: 0L

    override fun release() {
        engine?.release()
        engine = null
    }

    override fun setVideoSurface(view: TextureView?) {
        videoSurface = view
        engine?.setVideoSurface(view)
    }

    /** 内核重建后重新绑定已缓存的 Surface（避免切换内核/重连后画面丢失）。 */
    private fun reapplyVideoSurface() {
        videoSurface?.let { engine?.setVideoSurface(it) }
    }

    private suspend fun connectFor(media: PlayableMedia) {
        val cfg = serverRepository.getById(media.serverId)
            ?: throw IllegalStateException("找不到服务器配置：${media.serverId}")
        webDavClient.connect(cfg)
    }
}
