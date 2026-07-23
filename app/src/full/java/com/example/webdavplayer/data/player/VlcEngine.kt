package com.example.webdavplayer.data.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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
 * 仅编译于 `full` 风味（见 src/full）；[PlayerEngineFactory] 通过反射加载。
 *
 * 视频渲染：UI 层创建 [SurfaceView] 并通过 [setSurfaceView] 传入，
 * VlcEngine 使用 VLC 的 setVideoView + attachViews 标准流程绑定。
 */
class VlcEngine @Inject constructor(
    private val context: Context,
) : PlayerEngine {

    private val libVlc: LibVLC = LibVLC(context, arrayListOf(
        "--no-video-title-show",
        "--fullscreen",
    ))
    private var mediaPlayer: MediaPlayer? = null
    private var listener: EngineListener? = null
    private var state: PlaybackState = PlaybackState.IDLE
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    /** UI 层传入的 SurfaceView。 */
    @Volatile
    private var surfaceView: SurfaceView? = null

    /** UI 层调用：绑定 SurfaceView（VLC 标准方式：setVideoView + attachViews）。 */
    fun setSurfaceView(view: SurfaceView?) {
        if (surfaceView === view) return
        // 如果已有 attached 的 views，先 detach 再重新 attach 到新的 SurfaceView
        if (view != null) {
            try { mediaPlayer?.vlcVout?.detachViews() } catch (_: Exception) {}
        }
        surfaceView = view
        if (view != null) {
            attachVlcVout()
        }
    }

    /** 将 VLC vout 绑定到 SurfaceView（幂等）。 */
    private fun attachVlcVout() {
        val mp = mediaPlayer ?: return
        val sv = surfaceView ?: return
        try {
            if (!mp.vlcVout.areViewsAttached()) {
                // VLC 官方推荐方式：setVideoView(SurfaceView) + attachViews()
                mp.vlcVout.setVideoView(sv)
                mp.vlcVout.attachViews()
            }
        } catch (_: Exception) {
            // 可能已经 attached 或 surface 无效，忽略
        }
    }

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                updateState(PlaybackState.PLAYING)
                startProgress()
                // play 时再尝试一次 attach（surface 可能在此刻才 available）
                attachVlcVout()
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

        val m = Media(libVlc, Uri.parse(media.uri))
        // VLC HTTP 认证：使用 :http-user 和 :http-pwd 选项
        val authHeader = media.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            val decoded = String(android.util.Base64.decode(
                authHeader.substring(6), android.util.Base64.DEFAULT
            ))
            val colonIdx = decoded.indexOf(':')
            if (colonIdx > 0) {
                val user = decoded.substring(0, colonIdx)
                val pwd = decoded.substring(colonIdx + 1)
                m.addOption(":http-user=$user")
                m.addOption(":http-pwd=$pwd")
            }
        }
        if (media.trustSelfSigned) m.addOption(":no-tls-check")
        mediaPlayer!!.media = m
        updateState(PlaybackState.PREPARING)
    }

    override fun play() {
        // 确保 vout 已绑定
        attachVlcVout()
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun getState(): PlaybackState = state

    override fun release() {
        stopProgress()
        try { mediaPlayer?.vlcVout?.detachViews() } catch (_: Exception) {}
        mediaPlayer?.release()
        mediaPlayer = null
        surfaceView = null
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
                val mp = mediaPlayer
                if (mp == null) break
                val position = mp.time.coerceAtLeast(0L)
                val duration = mp.length.coerceAtLeast(0L)
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
