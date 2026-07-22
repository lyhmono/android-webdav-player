package com.example.webdavplayer.data.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
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
 * 使用 TextureView 渲染视频（不需要 Activity context，ApplicationContext 即可）。
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

    /** TextureView 供 UI 挂载到布局中。 */
    val textureView: TextureView by lazy {
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    attachVlcVout(Surface(surface))
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    mediaPlayer?.vlcVout?.setWindowSize(width, height)
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    try { mediaPlayer?.vlcVout?.detachViews() } catch (_: Exception) {}
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    /** 将 VLC vout 绑定到 Surface。 */
    private fun attachVlcVout(surface: Surface) {
        val mp = mediaPlayer ?: return
        try {
            mp.vlcVout.setVideoSurface(surface, null)
            mp.vlcVout.attachViews()
        } catch (_: Exception) {
            // 可能已经 attached，忽略
        }
    }

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

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun getState(): PlaybackState = state

    override fun release() {
        stopProgress()
        try { mediaPlayer?.vlcVout?.detachViews() } catch (_: Exception) {}
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
