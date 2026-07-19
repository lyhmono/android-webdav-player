package com.example.webdavplayer.service

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PeriodData
import androidx.media3.common.util.UnstableApi
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 立即完成的 [ListenableFuture] 实现。
 *
 * classpath 上仅有 `com.google.common.util.concurrent.ListenableFuture` 接口
 * （Guava listenablefuture 桩），并无 `com.google.common.base.Futures` 工具类。
 * 为避免引入完整 Guava 依赖，这里提供最小实现，供 SimpleBasePlayer 各 handler
 * 返回“已就绪”的结果。
 */
private class ImmediateFuture<V>(private val value: V?) : ListenableFuture<V> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
    override fun isCancelled(): Boolean = false
    override fun isDone(): Boolean = true
    override fun get(): V? = value
    override fun get(timeout: Long, unit: TimeUnit): V? = value
    override fun addListener(listener: Runnable, executor: Executor) {
        executor.execute(listener)
    }
}

/**
 * 将 [PlayerRepository]（双内核抽象）适配为 Media3 [Player]（C1 / §7）。
 *
 * 这是 MediaSession 背后的“真实播放器”代理：MediaSession 与系统（通知/锁屏/
 * 蓝牙）交互，所有命令经本类转译为对 [PlayerRepository] 与 [PlaylistController] 的调用。
 * 内核无状态记忆，进度/顺序的真相源在 [PlaylistController]，本类只负责快照与转发。
 *
 * 设计要点：
 * - 播放状态来自 [onEngineState]（由 [PlaybackService] 的引擎监听驱动）；
 * - 进度来自 [onEngineProgress]（同一监听驱动）；
 * - 上一首/下一首经 [PlaylistController] 计算，并通过 [playItem] 真正触发播放。
 *
 * @param looper 主线程 Looper（SimpleBasePlayer 要求）
 * @param playerRepository 共享的单例播放仓库（与 UI 同一实例）
 * @param playlistController 共享的播放列表导航控制
 * @param playItem 真正“播放某一项”的挂起函数（由服务注入，内部走 PlayMediaUseCase）
 */
@UnstableApi
class EngineMedia3Adapter(
    looper: android.os.Looper,
    private val playerRepository: PlayerRepository,
    private val playlistController: PlaylistController,
    private val playItem: suspend (PlaylistItem) -> Unit,
) : SimpleBasePlayer(looper) {

    /** 工作协程（用于触发 playItem 等挂起调用，主线程即可）。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 当前播放状态（由引擎监听驱动）。 */
    @Volatile
    private var engineState: PlaybackState = PlaybackState.IDLE

    /** 当前进度（毫秒，由引擎监听驱动）。 */
    @Volatile
    private var positionMs: Long = 0L

    /** 当前总时长（毫秒，由引擎监听驱动）。 */
    @Volatile
    private var durationMs: Long = 0L

    /** 当前播放倍速（1.0 = 正常），由 [handleSetPlaybackParameters] 更新并经 State 回报。 */
    @Volatile
    private var currentSpeed: Float = 1.0f

    /** 供外部读取当前播放位置（用于暂停/结束时 flush 进度）。 */
    val currentPositionMs: Long get() = positionMs

    /** 引擎监听驱动：更新状态并推送。 */
    fun onEngineState(state: PlaybackState) {
        engineState = state
        invalidateState()
    }

    /** 引擎监听驱动：更新进度并推送。 */
    fun onEngineProgress(position: Long, duration: Long) {
        positionMs = position
        if (duration > 0) durationMs = duration
        invalidateState()
    }

    @UnstableApi
    override fun getState(): State {
        val items = playlistController.snapshot()
        val commands = buildCommands()

        if (items.isEmpty()) {
            // 列表为空时状态必须为 IDLE / ENDED，索引置为 UNSET。
            return State.Builder()
                .setAvailableCommands(commands)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackState(Player.STATE_IDLE)
                .setCurrentMediaItemIndex(C.INDEX_UNSET)
                .setContentPositionMs(0)
                .setPlaylist(emptyList<MediaItemData>())
                .build()
        }

        val current = playlistController.current()
        val currentIndex = if (current != null) {
            items.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        } else {
            0
        }

        val mediaItemData = items.map { item ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(item.id)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(item.name).build(),
                )
                .build()
            val period = PeriodData.Builder(item.id)
                .setDurationUs(
                    if (item.durationMs > 0) {
                        item.durationMs * 1000
                    } else if (durationMs > 0) {
                        durationMs * 1000
                    } else {
                        C.TIME_UNSET
                    },
                )
                .build()
            MediaItemData.Builder(item.id)
                .setMediaItem(mediaItem)
                .setPeriods(listOf(period))
                .build()
        }

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(
                engineState == PlaybackState.PLAYING,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(mapState(engineState))
            .setCurrentMediaItemIndex(currentIndex)
            .setContentPositionMs(positionMs)
            .setPlaybackParameters(PlaybackParameters(currentSpeed))
            .setPlaylist(mediaItemData)
            .build()
    }

    @UnstableApi
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) playerRepository.play() else playerRepository.pause()
        return ImmediateFuture(playWhenReady)
    }

    @UnstableApi
    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        currentSpeed = playbackParameters.speed
        playerRepository.setSpeed(playbackParameters.speed)
        return ImmediateFuture(Unit)
    }

    @UnstableApi
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val items = playlistController.snapshot()
        val target = items.getOrNull(mediaItemIndex)
        if (target != null && target.id != playlistController.current()?.id) {
            // 切换媒体项（上一首 / 下一首经 Media3 基础实现路由到此处）：播放该媒体。
            scope.launch { playItem(target) }
        } else {
            // 当前项内拖动。
            playerRepository.seekTo(positionMs)
        }
        return ImmediateFuture(Unit)
    }

    @UnstableApi
    override fun handleStop(): ListenableFuture<*> {
        playerRepository.pause()
        return ImmediateFuture(Unit)
    }

    @UnstableApi
    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return ImmediateFuture(Unit)
    }

    /** 声明本代理支持的命令集合。 */
    private fun buildCommands(): Player.Commands = Player.Commands.Builder()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_GET_TIMELINE)
        .add(Player.COMMAND_SET_SPEED_AND_PITCH)
        .build()

    /** 领域 [PlaybackState] → Media3 [Player] 状态。 */
    private fun mapState(state: PlaybackState): Int = when (state) {
        PlaybackState.IDLE -> Player.STATE_IDLE
        PlaybackState.PREPARING -> Player.STATE_BUFFERING
        PlaybackState.READY -> Player.STATE_READY
        PlaybackState.PLAYING -> Player.STATE_READY
        PlaybackState.PAUSED -> Player.STATE_READY
        PlaybackState.ENDED -> Player.STATE_ENDED
        PlaybackState.ERROR -> Player.STATE_IDLE
    }
}
