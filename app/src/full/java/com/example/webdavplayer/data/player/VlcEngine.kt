package com.example.webdavplayer.data.player

import android.content.Context
import android.view.TextureView
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.PlayableMedia
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.player.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import javax.inject.Inject

/**
 * libVLC 备选内核（§1.2 / T05）。
 *
 * 仅编译于 `full` 风味（见 src/full）；[PlayerEngineFactory] 通过反射加载，
 * 因此 `lite` 风味（仅 Media3）可正常编译。
 *
 * 流式：使用传入 URI + 鉴权头；自签证书走 `:no-tls-check` 跳过校验
 * （由 [PlayableMedia.trustSelfSigned] 控制）。
 *
 * 视频 Surface 穿透抽象层直达内核：[setVideoSurface] 缓存 [pendingView]（TextureView），
 * 在 [prepare] 创建 MediaPlayer 后（或绑定发生在 prepare 之前时）通过 [applyVideoSurface]
 * 绑定 libVLC 的 `IVLCVout`。
 *
 * libVLC 3.6.0 的 `IVLCVout.setVideoSurface` 只接受 `(Surface, SurfaceHolder)` 或
 * `(SurfaceTexture)`，没有「裸 Surface 单参」重载；统一改用 `setVideoView(TextureView)`
 * （TextureView 同时能满足 Media3 与 libVLC 两种绑定方式）。
 *
 * ⚠️ best-effort：本文件属 `full` 风味，需 full 风味真机核对 API 签名。
 */
class VlcEngine @Inject constructor(
    private val context: Context,
) : PlayerEngine {

    private val libVlc: LibVLC = LibVLC(context, arrayListOf("--no-video-title-show"))
    private var mediaPlayer: MediaPlayer? = null
    private var listener: EngineListener? = null
    private var state: PlaybackState = PlaybackState.IDLE
    private var pendingView: TextureView? = null
    private var attached = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                updateState(PlaybackState.PLAYING)
                startProgress()
            }
            MediaPlayer.Event.Paused -> {
                updateState(PlaybackState.PAUSED)
                stopProgress()
            }
            MediaPlayer.Event.Stopped -> updateState(PlaybackState.IDLE)
            MediaPlayer.Event.EndReached -> {
                updateState(PlaybackState.ENDED)
                listener?.onEnded()
            }
            MediaPlayer.Event.EncounteredError -> {
                updateState(PlaybackState.ERROR)
                listener?.onError(Exception("VLC 播放出错"))
            }
        }
    }

    override fun prepare(media: PlayableMedia) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer(libVlc).apply { setEventListener(eventListener) }
        }
        // 内核创建后重新绑定 prepare 之前已设置的视图。
        pendingView?.let { applyVideoSurface(it) }
        val m = Media(libVlc, Uri.parse(media.uri))
        // libVLC 3.6.0 has no setHttpHeader(); pass custom headers as media options.
        media.headers.forEach { (k, v) -> m.addOption(":http-header=$k: $v") }
        if (media.trustSelfSigned) m.addOption(":no-tls-check")
        mediaPlayer!!.media = m
        updateState(PlaybackState.PREPARING)
    }

    override fun play() {
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    override fun setSpeed(speed: Float) {
        // libVLC 通过 MediaPlayer.setRate 表达倍速（与播放/暂停状态无关，可随时设置）。
        mediaPlayer?.setRate(speed)
    }

    override fun setVideoSurface(view: TextureView?) {
        pendingView = view
        applyVideoSurface(view)
    }

    /** 绑定/解绑 libVLC 的 IVLCVout（按 libVLC 3.6.0 API）。 */
    private fun applyVideoSurface(view: TextureView?) {
        val mp = mediaPlayer ?: return
        val vout = mp.vlcVout
        if (view != null) {
            vout.setVideoView(view)
            if (!attached) {
                vout.attachViews()
                attached = true
            }
        } else {
            vout.detachViews()
            attached = false
        }
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun getState(): PlaybackState = state

    override fun release() {
        stopProgress()
        attached = false
        pendingView = null
        mediaPlayer?.release()
        mediaPlayer = null
        libVlc.release()
        updateState(PlaybackState.IDLE)
    }

    private fun updateState(newState: PlaybackState) {
        state = newState
        listener?.onStateChange(newState)
    }

    private fun startProgress() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                val position = mediaPlayer?.time ?: 0L
                val duration = mediaPlayer?.length ?: 0L
                listener?.onProgress(position, duration)
                delay(500)
            }
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }
}
