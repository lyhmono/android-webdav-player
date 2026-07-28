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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.formatDuration
import com.example.webdavplayer.ui.common.stateLabel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.theme.Spacing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
    val showGesture = isVideo

    var menuExpanded by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

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
        // 竖屏：视频优先布局——上半屏视频区 + 半透明控制叠加，下半部信息+播放列表。
        val scrollState = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // ── 视频主区域（上半屏约 45%）──
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .background(Color.Black)
                        .clickable { controlsVisible = !controlsVisible },
                ) {
                    if (isVideo) {
                        VideoArea(Modifier.fillMaxSize())
                    } else {
                        // 音频占位
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.AudioFile,
                                contentDescription = "音频",
                                modifier = Modifier.size(80.dp),
                                tint = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }

                    // 半透明控制叠加层（点击视频区切换显示/隐藏）
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !controlsVisible || !isPlaying,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(300)),
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            // 顶部渐变导航栏
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.6f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    )
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                                }
                                Text(
                                    title.ifEmpty { "未选择媒体" },
                                    modifier = Modifier.weight(1f),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                IconButton(onClick = { isFullScreen = !isFullScreen }) {
                                    Icon(
                                        if (isFullScreen) Icons.Filled.FullscreenExit
                                        else Icons.Filled.Fullscreen,
                                        "全屏",
                                        tint = Color.White,
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Filled.MoreVert, "更多", tint = Color.White)
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
                                }
                            }

                            // 居中大播放按钮（暂停时）
                            if (!isPlaying && state != PlaybackState.PREPARING) {
                                IconButton(
                                    onClick = { playerVm.togglePlay() },
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(72.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }

                            // 底部渐变 + 控制条
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.7f),
                                            ),
                                        ),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Slider(
                                    value = (if (isScrubbing) scrubValue else position.toFloat())
                                        .coerceIn(0f, maxValue),
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
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                    ),
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        formatDuration(position),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        formatDuration(duration),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { playerVm.previous() }) {
                                        Icon(Icons.Filled.SkipPrevious, "上一首", tint = Color.White)
                                    }
                                    IconButton(onClick = { playerVm.togglePlay() }) {
                                        Icon(
                                            if (isPlaying) Icons.Filled.Pause
                                            else Icons.Filled.PlayArrow,
                                            if (isPlaying) "暂停" else "播放",
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp),
                                        )
                                    }
                                    IconButton(onClick = { playerVm.next() }) {
                                        Icon(Icons.Filled.SkipNext, "下一首", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 下半部：信息 + 播放列表（可滚动）──
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column {
                        Text(
                            title.ifEmpty { "未选择媒体" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stateLabel(state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    SectionHeader("播放倍速")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        playbackSpeeds.forEach { s ->
                            FilterChip(
                                selected = speed == s,
                                onClick = { playerVm.setSpeed(s) },
                                label = {
                                    Text("${if (s % 1f == 0f) s.toInt() else s}x")
                                },
                            )
                        }
                    }

                    SectionHeader("播放模式")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PlayMode.values().forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { playerVm.setMode(m) },
                                label = { Text(modeLabel(m)) },
                            )
                        }
                    }

                    SectionHeader("播放内核")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        EngineType.values().forEach { t ->
                            FilterChip(
                                selected = engineType == t,
                                onClick = { playerVm.switchEngine(t) },
                                label = { Text(engineLabel(t)) },
                                enabled = if (t == EngineType.VLC) isVlcAvailable else true,
                            )
                        }
                    }

                    SectionHeader("播放列表")
                    items.forEach { item ->
                        val isCurrent = item.id == currentItemId
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.name,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) {
                                        androidx.compose.ui.text.font.FontWeight.Bold
                                    } else null,
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
                                .combinedClickable(
                                    onClick = { playerVm.playItem(item) },
                                    onLongClick = { playlistVm.removeItem(item.id) },
                                ),
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
