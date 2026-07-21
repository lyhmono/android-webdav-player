package com.example.webdavplayer.data.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    }

    private fun ensurePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply { addListener(playerListener) }
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
            // 本地文件（离线缓存）：直接设置 URI；字幕以外部文本轨附带，不走流式数据源。
            val item = MediaItem.Builder()
                .setUri(media.uri)
                .setSubtitleConfigurations(
                    media.subtitles.map { sub ->
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                            .setMimeType(sub.mimeType)
                            .setLanguage(sub.language)
                            .setLabel(sub.label)
                            .build()
                    },
                )
                .build()
            player!!.setMediaItem(item)
        }
        player!!.prepare()
        // 字幕默认关闭：避免主媒体带字幕时自动显示，由用户经「字幕」菜单显式开启。
        player!!.setTrackSelectionParameters(
            player!!.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build(),
        )
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

    override fun selectSubtitle(language: String?) {
        val p = player ?: return
        val params = p.trackSelectionParameters.buildUpon()
        if (language == null) {
            // 关闭字幕：禁用文本轨。
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            // 按语言选择文本轨；若无可匹配语言则保持禁用状态由播放器择一。
            params.setPreferredTextLanguage(language)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        }
        p.setTrackSelectionParameters(params.build())
    }

    override fun enableSubtitles() {
        val p = player ?: return
        // 不指定语言：仅解除文本轨禁用，由播放器自动选第一条可用文本轨。
        p.setTrackSelectionParameters(
            p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build(),
        )
    }

    override fun setListener(listener: EngineListener?) {
        this.listener = listener
    }

    override fun getState(): PlaybackState = state

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
