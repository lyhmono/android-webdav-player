package com.example.webdavplayer.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.example.webdavplayer.domain.repository.PlaylistRepository
import com.example.webdavplayer.domain.usecase.ClearProgressUseCase
import com.example.webdavplayer.domain.usecase.PlayMediaUseCase
import com.example.webdavplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 播放页 ViewModel（§6 T09 / C1 改造）。
 *
 * P1 改造：降级为 Media3 [MediaController] 客户端。
 * - 引擎与引擎监听的“唯一拥有者”已移交给 [PlaybackService]（后台播放所需）；
 *   本 VM 不再持有 [com.example.webdavplayer.domain.model.EngineListener]，
 *   也不再在 [onCleared] 中 [PlayerRepository.release]（否则后台播放会被打断）。
 * - 播放状态/进度来自 [MediaController]（会话背后是 [PlaybackService] 的引擎）；
 * - 播放控制命令转发给 [MediaController]（失败时回退到 [PlayerRepository] 直连同一单例引擎）。
 *
 * 进度与顺序真相源仍在 [PlaylistController]（由观察 PlaylistRepository 驱动）。
 */
@HiltViewModel
@UnstableApi
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistRepository: PlaylistRepository,
    private val playlistController: PlaylistController,
    private val playMedia: PlayMediaUseCase,
    private val clearProgress: ClearProgressUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.IDLE)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _engineType = MutableStateFlow<EngineType>(EngineType.MEDIA3)
    val engineType: StateFlow<EngineType> = _engineType.asStateFlow()

    /** 当前媒体类型（用于视频手势层门控，C4）。 */
    private val _currentMediaType = MutableStateFlow(MediaType.OTHER)
    val currentMediaType: StateFlow<MediaType> = _currentMediaType.asStateFlow()

    val items: StateFlow<List<PlaylistItem>> = playlistRepository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mode: StateFlow<PlayMode> = playlistRepository.observeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayMode.SEQUENTIAL)

    /** 连接到后台 [PlaybackService] 的 MediaSession 的 MediaController。 */
    private var mediaController: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    init {
        _engineType.value = playerRepository.getEngineType()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            { onControllerConnected() },
            ContextCompat.getMainExecutor(context),
        )

        viewModelScope.launch {
            playlistRepository.observeItems().collect { playlistController.sync(it) }
        }
        viewModelScope.launch {
            playlistRepository.observeMode().collect { playlistController.setMode(it) }
        }
    }

    /** MediaController 已连接：注册监听并同步首帧状态。 */
    @UnstableApi
    private fun onControllerConnected() {
        val controller = try {
            controllerFuture.get()
        } catch (_: Exception) {
            return
        }
        mediaController = controller
        controller.addListener(playerListener)
        syncFromController(controller)
    }

    /** 将 MediaController 的播放状态/进度映射到本 VM 的 StateFlow。 */
    private fun syncFromController(controller: MediaController) {
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val dur = if (controller.duration != C.TIME_UNSET) {
            controller.duration.coerceAtLeast(0L)
        } else {
            0L
        }
        _position.value = pos
        _duration.value = dur
        _state.value = mapControllerState(controller)
        _currentMediaType.value = playlistController.current()?.mediaType ?: MediaType.OTHER
    }

    /** Media3 Player 状态 → 领域 [PlaybackState]。 */
    private fun mapControllerState(c: MediaController): PlaybackState = when (c.playbackState) {
        Player.STATE_IDLE -> PlaybackState.IDLE
        Player.STATE_BUFFERING -> PlaybackState.PREPARING
        Player.STATE_READY -> if (c.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
        Player.STATE_ENDED -> PlaybackState.ENDED
        else -> PlaybackState.IDLE
    }

    /** MediaController 事件监听：驱动 UI 状态刷新与标题同步。 */
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromController(player as MediaController)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            mediaMetadata.title?.let { _title.value = it.toString() }
        }
    }

    /** 播放某一列表项（直连共享单例引擎；服务侧监听会回灌状态到 MediaController）。 */
    fun playItem(item: PlaylistItem) {
        _title.value = item.name
        viewModelScope.launch {
            when (playMedia(item)) {
                is Result.Success -> { /* 状态由 MediaController 监听驱动 */ }
                is Result.Error -> _state.value = PlaybackState.ERROR
            }
        }
    }

    fun play() = mediaController?.play() ?: playerRepository.play()

    fun pause() = mediaController?.pause() ?: playerRepository.pause()

    fun seekTo(ms: Long) = mediaController?.seekTo(ms) ?: playerRepository.seekTo(ms)

    fun togglePlay() {
        if (_state.value == PlaybackState.PLAYING) pause() else play()
    }

    fun next() {
        val controller = mediaController
        if (controller != null) {
            controller.seekToNextMediaItem()
        } else {
            viewModelScope.launch { playNext() }
        }
    }

    fun previous() {
        val controller = mediaController
        if (controller != null) {
            controller.seekToPreviousMediaItem()
        } else {
            viewModelScope.launch {
                val prev = playlistController.previous()
                if (prev != null) playItem(prev)
            }
        }
    }

    private suspend fun playNext() {
        val next = playlistController.next()
        if (next != null) {
            playItem(next)
        } else {
            _state.value = PlaybackState.ENDED
        }
    }

    fun setMode(mode: PlayMode) {
        viewModelScope.launch { playlistRepository.setMode(mode) }
    }

    /** 应用内切换内核（§1.2）。 */
    fun switchEngine(type: EngineType) {
        viewModelScope.launch {
            playerRepository.setEngineType(type)
            _engineType.value = type
        }
    }

    /**
     * 清除当前项的播放进度断点并“从头播放”（C3 UI 菜单项）。
     * 先清库，再归零进度并播放。
     */
    fun clearProgressAndRestart() {
        val item = playlistController.current() ?: return
        viewModelScope.launch {
            clearProgress(item.serverId, item.path)
            seekTo(0)
            play()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 注意：不再释放 playerRepository 引擎（后台播放依赖其存活）。
        mediaController?.removeListener(playerListener)
        mediaController?.release()
    }
}
