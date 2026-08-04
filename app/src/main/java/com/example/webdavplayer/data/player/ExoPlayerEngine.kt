package com.example.webdavplayer.data.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
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
import okhttp3.OkHttpClient

/**
 * Media3 / ExoPlayer 内核实现（§1.2 默认内核）。
 * 仅负责「当前这一条媒体的解码渲染」，进度/列表由上层持有。
 *
 * 视频渲染由 media3-ui-compose PlayerSurface 直接绑定 [getPlayer] 返回的 ExoPlayer（方案 C），
 * 本内核不再管理 Surface 生命周期。
 */
@UnstableApi
class ExoPlayerEngine(
    private val context: Context,
    private val streamingSource: WebDavStreamingSource,
) : PlayerEngine {

    private var player: ExoPlayer? = null
    private var listener: EngineListener? = null
    private var okHttpClient: OkHttpClient? = null
    private var state: PlaybackState = PlaybackState.IDLE
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private companion object {
        /** 进度回调间隔（毫秒）：200ms 保证进度条流畅。 */
        const val PROGRESS_INTERVAL_MS = 200L
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> updateState(PlaybackState.IDLE)
                Player.STATE_BUFFERING -> updateState(PlaybackState.PREPARING)
                Player.STATE_READY -> updateState(
                    if (player?.isPlaying == true) PlaybackState.PLAYING else PlaybackState.PAUSED,
                )
                Player.STATE_ENDED -> {
                    updateState(PlaybackState.ENDED)
                    listener?.onEnded()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateState(if (isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED)
            if (isPlaying) startProgress() else stopProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            updateState(PlaybackState.ERROR)
            listener?.onError(error)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            listener?.onVideoSizeChanged(videoSize.width, videoSize.height)
        }
    }

    private fun ensurePlayer() {
        if (player == null) {
            // LoadControl：WebDAV 流式播放优化
            // minBuffer 30s / maxBuffer 300s / startBuffer 10s / rebuffer 30s
            // startBuffer 10s → 首帧快；maxBuffer 300s → 网络好时大量囤积；
            // rebuffer 30s → 中断后缓冲充足才恢复，避免 1-2 秒自动暂停
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 300_000, 10_000, 30_000)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()
            player = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build().apply {
                    addListener(playerListener)
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                }
        }
    }

    /** 注入共享 OkHttp（含自签信任 + 鉴权），供流式数据源使用。 */
    fun setOkHttpClient(client: OkHttpClient) {
        okHttpClient = client
    }

    override fun prepare(media: PlayableMedia) {
        ensurePlayer()
        if (media.uri.startsWith("http", ignoreCase = true)) {
            val client = okHttpClient
                ?: throw IllegalStateException("OkHttpClient 未注入，无法流式播放")
            val source = streamingSource.createExoMediaSource(client, media)
            player!!.setMediaSource(source)
        } else {
            // 本地文件（离线缓存）：直接设置 URI。
            player!!.setMediaItem(MediaItem.fromUri(media.uri))
        }
        player!!.prepare()
        updateState(PlaybackState.PREPARING)
    }

    override fun play() {
        player?.playWhenReady = true
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        // ExoPlayer 通过 playbackParameters 表达倍速（pitch 保持默认 1.0）。
        player?.setPlaybackSpeed(speed)
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun getState(): PlaybackState = state

    override fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    override fun getDurationMs(): Long = player?.duration?.takeIf { it > 0 } ?: 0L

    /** 暴露底层 ExoPlayer 实例（PlayerSurface 直接绑定渲染，方案 C）。 */
    override fun getPlayer(): Player? = player

    override fun release() {
        stopProgress()
        player?.release()
        player = null
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
                val position = player?.currentPosition ?: 0L
                val duration = player?.duration ?: 0L
                listener?.onProgress(position, duration)
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }
}
