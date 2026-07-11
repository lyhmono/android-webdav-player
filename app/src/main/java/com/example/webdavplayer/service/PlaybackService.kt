package com.example.webdavplayer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.webdavplayer.R
import com.example.webdavplayer.data.repository.PlaybackProgressSaver
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.example.webdavplayer.domain.usecase.PlayMediaUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 后台播放服务（C1 / §7）。
 *
 * 继承 Media3 [MediaSessionService]：
 * - 持有 MediaSession（背后是 [EngineMedia3Adapter]），系统（通知/锁屏/蓝牙）经会话控制播放；
 * - 是 [PlayerRepository] 引擎监听的唯一拥有者（UI 不再持有监听，改为 MediaController 客户端），
 *   因此活动销毁后引擎仍在后台运行，实现后台播放；
 * - 经 [EngineListener] 把引擎事件转译为会话状态（[EngineMedia3Adapter.invalidateState]），
 *   并在进度回调中驱动 [PlaybackProgressSaver] 落库；
 * - 视频在应用退到后台时自动暂停（[foregroundObserver]）。
 *
 * 注意：MediaSessionService 在媒体播放时会自动以前台服务 + 通知形式运行，
 * 无需手动 startForeground。
 */
@AndroidEntryPoint
@UnstableApi
class PlaybackService : MediaSessionService() {

    /** 通知渠道 id（与 DefaultMediaNotificationProvider 配置对应）。 */
    private val channelId: String = "webdav_playback"

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var playlistController: PlaylistController

    @Inject
    lateinit var playMedia: PlayMediaUseCase

    @Inject
    lateinit var progressSaver: PlaybackProgressSaver

    private lateinit var mediaSession: MediaSession
    private lateinit var adapter: EngineMedia3Adapter

    /** 服务级协程（触发 playItem 等挂起调用）。 */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 统一“播放某一项”入口（供 adapter / callback 复用）。 */
    private fun launchPlayItem(item: PlaylistItem) {
        serviceScope.launch { playMedia(item) }
    }

    /** 引擎事件监听：驱动会话状态 + 进度落库 + 自然结束续播。 */
    private val engineListener = object : EngineListener {
        override fun onStateChange(state: PlaybackState) {
            adapter.onEngineState(state)
        }

        override fun onProgress(positionMs: Long, durationMs: Long) {
            adapter.onEngineProgress(positionMs, durationMs)
            // 节流保存当前项断点（C3）。
            playlistController.current()?.let { item ->
                progressSaver.onProgress(item.serverId, item.path, positionMs)
            }
        }

        override fun onEnded() {
            // 自然结束：仅当当前播放模式“非 LOOP”时才清除刚播完项的续播断点（C3-AC4）。
            // LOOP 模式下保留断点，由 onProgress 每 ~5s 节流续写，保证“下次打开仍从断点续播”。
            if (playlistController.getMode() != PlayMode.LOOP) {
                playlistController.current()?.let { ended ->
                    progressSaver.onEnded(ended.serverId, ended.path)
                }
            }
            val next = playlistController.onItemEnded()
            if (next != null) launchPlayItem(next)
        }

        override fun onError(throwable: Throwable) {
            // 错误状态已由 onEngineState(ERROR) 经 adapter.onEngineState 推送，无需额外刷新。
        }
    }

    /** 应用前后台观测：退到后台且当前是视频则暂停（C1：视频后台暂停）。 */
    private val foregroundObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) {
            val current = playlistController.current()
            if (current?.mediaType == MediaType.VIDEO &&
                playerRepository.getState() == PlaybackState.PLAYING
            ) {
                playerRepository.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(channelId)
            .setChannelName(R.string.playback_channel_name)
            .build()
        // Media3 1.5.1：通知提供方通过 MediaSessionService 的 protected 方法设置
        //（MediaSession.Builder 已无 setMediaNotificationProvider）。
        setMediaNotificationProvider(provider)

        adapter = EngineMedia3Adapter(
            looper = Looper.getMainLooper(),
            playerRepository = playerRepository,
            playlistController = playlistController,
            playItem = { item -> launchPlayItem(item) },
        )

        mediaSession = MediaSession.Builder(this, adapter)
            .setCallback(PlaybackSessionCallback())
            .build()

        // 服务成为引擎监听的唯一拥有者（UI 已降级为 MediaController 客户端）。
        playerRepository.setListener(engineListener)

        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 后台播放：仍在播放则保留服务；已暂停/结束则释放。
        if (playerRepository.getState() != PlaybackState.PLAYING) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(foregroundObserver)
        playerRepository.setListener(null)
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 创建 mediaPlayback 通知渠道（Android 8+ 必需）。 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    getString(R.string.playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "WebDAV 播放器后台播放通知"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
