@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.BuildConfig
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.formatDuration
import com.example.webdavplayer.ui.common.stateLabel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.theme.Spacing

/** 播放倍速档位（1.0 = 正常速度）。 */
private val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    navController: NavHostController,
    playerVm: PlayerViewModel,
    playlistVm: PlaylistViewModel,
) {
    val title by playerVm.title.collectAsStateWithLifecycle()
    val state by playerVm.state.collectAsStateWithLifecycle()
    val position by playerVm.position.collectAsStateWithLifecycle()
    val duration by playerVm.duration.collectAsStateWithLifecycle()
    val engineType by playerVm.engineType.collectAsStateWithLifecycle()
    val items by playerVm.items.collectAsStateWithLifecycle()
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val mediaType by playerVm.currentMediaType.collectAsStateWithLifecycle()
    val isOnline by playerVm.isOnline.collectAsStateWithLifecycle()
    val resumedPosition by playerVm.resumedPosition.collectAsStateWithLifecycle()
    val currentItemId by playerVm.currentItemId.collectAsStateWithLifecycle()
    val speed by playerVm.speed.collectAsStateWithLifecycle()
    val subtitles by playerVm.subtitles.collectAsStateWithLifecycle()
    val isVlcAvailable = BuildConfig.FLAVOR == "full"
    val isPlaying = state == PlaybackState.PLAYING

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullScreen by remember { mutableStateOf(false) }

    val isVideo = mediaType == MediaType.VIDEO
    // 视频手势层仅在视频 + 横屏/全屏时启用（C4）；且限定在视频区内。
    val showGesture = isVideo && (isFullScreen || isLandscape)

    var menuExpanded by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // 网络断开提示
    LaunchedEffect(isOnline) {
        if (!isOnline) {
            snackbarHostState.showSnackbar("网络已断开，播放可能受到影响")
        }
    }

    // 播放进度恢复提示
    LaunchedEffect(resumedPosition) {
        resumedPosition?.let { pos ->
            if (pos > 0) {
                snackbarHostState.showSnackbar("已从 ${formatDuration(pos)} 续播")
                playerVm.consumeResumedPosition()
            }
        }
    }

    // scrubbing 本地状态：拖动期间不 seek，松手才提交到引擎。
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(position.toFloat()) }
    val maxValue = duration.coerceAtLeast(1).toFloat()

    // 全屏沉浸（仅全屏态启用，隐藏状态栏/导航栏）。
    ImmersiveModeEffect(enabled = isFullScreen)

    // 横向手势快进/快退（落到 PlayerRepository.seekTo）。
    val onSeekBy: (Long) -> Unit = { delta ->
        playerVm.seekTo((position + delta).coerceIn(0, duration.coerceAtLeast(1)))
    }

    /** 视频区：按内核切换渲染方式（手势层限定在本视频区内，不遮挡控制条）。 */
    @Composable
    fun VideoArea(videoModifier: Modifier) {
        if (isVideo) {
            Box(videoModifier) {
                // 双内核统一走 VideoSurfaceHost 穿透路径：TextureView → playerVm.attachVideoSurface
                // → PlayerRepository.setVideoSurface → 当前引擎（ExoPlayer / VLC）。不再依赖
                // PlayerView / MediaController 的视频表面钩子（Media3 的 SimpleBasePlayer 该钩子
                // 在本编译环境下不可 override，而 VLC 早已证明此直连路径可行）。
                VideoSurfaceHost(
                    modifier = Modifier.fillMaxSize(),
                    onSurfaceReady = { playerVm.attachVideoSurface(it) },
                    onSurfaceDestroyed = { playerVm.detachVideoSurface() },
                )
                if (showGesture) {
                    VideoGestureLayer(
                        modifier = Modifier.fillMaxSize(),
                        isVideo = true,
                        durationMs = duration,
                        onSeekBy = onSeekBy,
                    )
                }
            }
        }
    }

    if (isFullScreen || isLandscape) {
        // 横屏 / 全屏：无 TopAppBar，视频铺满，控制条叠加底部。
        Box(Modifier.fillMaxSize()) {
            VideoArea(Modifier.fillMaxSize())
            PlayerControlBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                isPlaying = isPlaying,
                mode = mode,
                engineType = engineType,
                isVlcAvailable = isVlcAvailable,
                showMoreMenu = true,
                onTogglePlay = { playerVm.togglePlay() },
                onPrevious = { playerVm.previous() },
                onNext = { playerVm.next() },
                onSetMode = { playerVm.setMode(it) },
                onSwitchEngine = { playerVm.switchEngine(it) },
                onOpenPlaylist = { navController.navigate("playlist") },
                onClearProgress = { playerVm.clearProgressAndRestart() },
                onBack = { navController.popBackStack() },
                onToggleFullScreen = { isFullScreen = !isFullScreen },
                isFullScreen = isFullScreen,
                onShowSubtitles = { showSubtitleDialog = true },
            )
            // 网络断开 / 进度恢复提示（横屏态也展示）
            SnackbarHost(snackbarHostState)
        }
    } else {
        // 竖屏：Scaffold + TopAppBar（视频框在上、控制区在下）。
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("播放") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isFullScreen = !isFullScreen }) {
                            Icon(
                                if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                "全屏",
                            )
                        }
                        IconButton(onClick = { navController.navigate("playlist") }) {
                            Icon(Icons.Filled.QueueMusic, "播放列表")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("字幕") },
                                onClick = {
                                    menuExpanded = false
                                    showSubtitleDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("清除进度/从头播放") },
                                onClick = {
                                    menuExpanded = false
                                    playerVm.clearProgressAndRestart()
                                },
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(title.ifEmpty { "未选择媒体" }, style = MaterialTheme.typography.titleLarge)
                Text(stateLabel(state), style = MaterialTheme.typography.bodyMedium)

                // 视频区（竖屏为固定比例框；音频不显示）。
                if (isVideo) {
                    VideoArea(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                }

                Slider(
                    value = (if (isScrubbing) scrubValue else position.toFloat()).coerceIn(0f, maxValue),
                    onValueChange = {
                        isScrubbing = true
                        scrubValue = it
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        playerVm.seekTo(scrubValue.toLong())
                    },
                    valueRange = 0f..maxValue,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(position),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatDuration(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                PlayerControlBar(
                    isPlaying = isPlaying,
                    mode = mode,
                    engineType = engineType,
                    isVlcAvailable = isVlcAvailable,
                    showMoreMenu = false,
                    onTogglePlay = { playerVm.togglePlay() },
                    onPrevious = { playerVm.previous() },
                    onNext = { playerVm.next() },
                    onSetMode = { playerVm.setMode(it) },
                    onSwitchEngine = { playerVm.switchEngine(it) },
                    onOpenPlaylist = { navController.navigate("playlist") },
                    onClearProgress = { playerVm.clearProgressAndRestart() },
                )

                Spacer(Modifier.height(Spacing.sm))
                SectionHeader("播放倍速")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    playbackSpeeds.forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = { playerVm.setSpeed(s) },
                            label = { Text("${if (s % 1f == 0f) s.toInt() else s}x") },
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
                SectionHeader("播放列表")
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(items, key = { it.id }) { item ->
                        val isCurrent = item.id == currentItemId
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.name,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold
                                        else null,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (item.mediaType == MediaType.VIDEO) {
                                        Icons.Filled.VideoLibrary
                                    } else {
                                        Icons.Filled.AudioFile
                                    },
                                    null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                if (isCurrent && state == PlaybackState.PLAYING) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "正在播放",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement()
                                .combinedClickable(
                                    onClick = { playerVm.playItem(item) },
                                    onLongClick = { playlistVm.removeItem(item.id) },
                                ),
                        )
                    }
                }
            }
        }
    }

    // 字幕选择对话框（P2）：列出当前媒体的可选字幕轨，或关闭。
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("字幕") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SubtitleChoiceRow(
                        label = "关闭",
                        onClick = {
                            playerVm.selectSubtitle(null)
                            showSubtitleDialog = false
                        },
                    )
                    if (subtitles.isEmpty()) {
                        Text(
                            "当前目录下未找到字幕文件（.srt / .vtt / .ass）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        subtitles.forEach { sub ->
                            SubtitleChoiceRow(
                                label = buildString {
                                    append(sub.label)
                                    sub.language?.let { append(" · $it") }
                                },
                                onClick = {
                                    // 无语言后缀的字幕（language 为 null）无法按语言选，
                                    // 用 enableSubtitles 直接开启文本轨；否则按语言选。
                                    if (sub.language != null) {
                                        playerVm.selectSubtitle(sub.language)
                                    } else {
                                        playerVm.enableSubtitles()
                                    }
                                    showSubtitleDialog = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleDialog = false }) { Text("完成") }
            },
        )
    }
}

/** 字幕选择项（点击即应用）。 */
@Composable
private fun SubtitleChoiceRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
