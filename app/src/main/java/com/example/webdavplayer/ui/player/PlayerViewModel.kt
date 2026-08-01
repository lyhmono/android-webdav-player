package com.example.webdavplayer.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.network.NetworkMonitor
import com.example.webdavplayer.data.remote.WebDavClient
import com.example.webdavplayer.domain.model.EngineListener
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.SubtitleTrack
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.example.webdavplayer.domain.repository.PlaylistRepository
import com.example.webdavplayer.domain.usecase.ClearProgressUseCase
import com.example.webdavplayer.domain.usecase.PlayMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import javax.inject.Inject

/**
 * 播放页 ViewModel（方案 C：视频直连，去掉 MediaSession / MediaController / PlaybackService）。
 *
 * - 引擎（ExoPlayerEngine）由 [PlayerRepository] 单例持有，UI 直接操作；
 * - 视频渲染：PlayerSurface 直接绑定 [PlayerRepository.getPlayer] 返回的 ExoPlayer 实例；
 * - 播放状态/进度：由引擎 [EngineListener] 回调驱动（ExoPlayerEngine 内部 200ms 进度回调）。
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val playlistRepository: PlaylistRepository,
    private val playlistController: PlaylistController,
    private val playMedia: PlayMediaUseCase,
    private val clearProgress: ClearProgressUseCase,
    private val networkMonitor: NetworkMonitor,
    private val webDavClient: WebDavClient,
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

    /** 当前播放倍速（1.0 = 正常）。 */
    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /** 当前媒体类型（用于视频手势层门控 / 图片查看分支）。 */
    private val _currentMediaType = MutableStateFlow(MediaType.OTHER)
    val currentMediaType: StateFlow<MediaType> = _currentMediaType.asStateFlow()

    /** 当前媒体的可选字幕轨列表（来自 [PlayMediaUseCase] 发现结果）。 */
    private val _subtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitles: StateFlow<List<SubtitleTrack>> = _subtitles.asStateFlow()

    /** 底层 ExoPlayer 实例（PlayerSurface 直接绑定渲染；引擎 prepare 后才非空）。 */
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    /** 当前图片的 Bitmap（图片查看分支）。 */
    private val _imageBitmap = MutableStateFlow<Bitmap?>(null)
    val imageBitmap: StateFlow<Bitmap?> = _imageBitmap.asStateFlow()

    /** 视频原始宽高比（width/height，0 表示未知/纯音频），用于 PlayerSurface aspectRatio 适配防拉伸。 */
    private val _videoAspect = MutableStateFlow(0f)
    val videoAspect: StateFlow<Float> = _videoAspect.asStateFlow()

    /** 当前正在播放的列表项 ID（用于 UI 高亮当前播放项）。 */
    private val _currentItemId = MutableStateFlow<String?>(null)
    val currentItemId: StateFlow<String?> = _currentItemId.asStateFlow()

    val items: StateFlow<List<PlaylistItem>> = playlistRepository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mode: StateFlow<PlayMode> = playlistRepository.observeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayMode.SEQUENTIAL)

    /** 网络连接状态（UI 用来显示离线提示）。 */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    /** 当前 playItem 协程（用于防重入取消）。 */
    private var playJob: Job? = null
    /** 图片下载协程（#25：快速切图时取消旧协程，避免旧图晚返回覆盖新图）。 */
    private var imageJob: Job? = null

    /** 引擎事件监听（方案 C：直连引擎后由它驱动状态/进度/自然结束）。 */
    private val engineListener = object : EngineListener {
        override fun onStateChange(state: PlaybackState) {
            _state.value = state
        }

        override fun onProgress(positionMs: Long, durationMs: Long) {
            _position.value = positionMs
            if (durationMs > 0) _duration.value = durationMs
        }

        override fun onEnded() {
            // 自然结束：按播放模式计算下一首并自动播放（原 PlaybackService 职责）
            val next = playlistController.onItemEnded()
            if (next != null) playItem(next) else _state.value = PlaybackState.ENDED
        }

        override fun onError(throwable: Throwable) {
            _state.value = PlaybackState.ERROR
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            _videoAspect.value = if (height > 0) width.toFloat() / height else 0f
        }
    }

    init {
        _engineType.value = playerRepository.getEngineType()
        playerRepository.setListener(engineListener)
        viewModelScope.launch {
            playlistRepository.observeItems().collect { items ->
                playlistController.sync(items)
                // 列表被清空时复位当前项，保证下一次填充列表可自动播放
                if (items.isEmpty()) {
                    _currentItemId.value = null
                    _currentMediaType.value = MediaType.OTHER
                }
            }
        }
        viewModelScope.launch {
            playlistRepository.observeMode().collect { playlistController.setMode(it) }
        }
    }

    /** 播放某一列表项（方案 C：直连引擎，无 MediaController 中转）。 */
    fun playItem(item: PlaylistItem) {
        playJob?.cancel()
        _title.value = item.name
        _currentItemId.value = item.id
        _currentMediaType.value = item.mediaType
        // #26：切换曲目时立即清空旧引擎引用——UI 走 loading 分支，避免"新标题+旧画面"抖动
        _player.value = null
        _position.value = 0L
        _duration.value = 0L
        _videoAspect.value = 0f
        playJob = viewModelScope.launch {
            when (val r = playMedia(item)) {
                is Result.Success -> {
                    _subtitles.value = r.data.subtitles
                    if (r.data.mediaType == MediaType.IMAGE) {
                        // 图片：不走播放引擎，直接下载 Bitmap 展示
                        loadImage(r.data.uri, r.data.headers)
                    } else {
                        _player.value = playerRepository.getPlayer()
                    }
                }
                is Result.Error -> _state.value = PlaybackState.ERROR
            }
        }
    }

    /** 下载 WebDAV 图片并解码为 Bitmap（复用共享 OkHttp：自签信任 + Digest 鉴权）。 */
    private fun loadImage(uri: String, headers: Map<String, String>) {
        imageJob?.cancel()
        _imageBitmap.value = null
        imageJob = viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(uri)
                    headers.forEach { (k, v) -> req.header(k, v) }
                    webDavClient.getOkHttpClient().newCall(req.build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            null
                        } else {
                            resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
            _imageBitmap.value = bmp
        }
    }

    fun play() = playerRepository.play()

    fun pause() = playerRepository.pause()

    fun seekTo(ms: Long) = playerRepository.seekTo(ms)

    fun togglePlay() {
        if (_state.value == PlaybackState.PLAYING) pause() else play()
    }

    /** 设置播放倍速。 */
    fun setSpeed(speed: Float) {
        _speed.value = speed
        playerRepository.setSpeed(speed)
    }

    /**
     * 选择字幕语言（null = 关闭）。直连引擎设置轨道参数。
     */
    fun selectSubtitle(language: String?) {
        playerRepository.selectSubtitle(language)
    }

    /** 启用字幕（不指定语言，由播放器自动选第一条可用文本轨）。 */
    fun enableSubtitles() {
        playerRepository.enableSubtitles()
    }

    fun next() {
        viewModelScope.launch { playNext() }
    }

    fun previous() {
        viewModelScope.launch {
            val prev = playlistController.previous()
            if (prev != null) playItem(prev)
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
        playJob?.cancel()
        imageJob?.cancel()
        // 单例引擎由 PlayerRepository 持有；页面退出不释放（重新进入继续复用）。
    }
}
