@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward30
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
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay

/** 播放倍速档位。 */
private val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 控制自动隐藏延迟 */
private const val CONTROLS_AUTO_HIDE_MS = 4000L

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
    val fullscreen = isFullScreen || isLandscape

    val isVideo = mediaType == MediaType.VIDEO
    val showGesture = isVideo

    var menuExpanded by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    // 控制条可见性：播放中 N 秒后自动隐藏，暂停/手势触发时显示
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    // 暂停时强制显示
    if (!isPlaying && !controlsVisible) {
        controlsVisible = true
    }

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

    // 自动播放
    LaunchedEffect(items) {
        if (items.isNotEmpty() && currentItemId == null) {
            playerVm.playItem(items.first())
        }
    }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(position.toFloat()) }
    val maxValue = duration.coerceAtLeast(1).toFloat()

    ImmersiveModeEffect(enabled = isFullScreen)

    val onSeekBy: (Long) -> Unit = { delta ->
        playerVm.seekTo((position + delta).coerceIn(0, duration.coerceAtLeast(1)))
    }

    /** 视频渲染区 — TextureView 直连引擎，保持原始比例。 */
    @Composable
    fun VideoContent(modifier: Modifier) {
        Box(modifier) {
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

    // ===== 音频占位 =====
    @Composable
    fun AudioPlaceholder(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.AudioFile,
                contentDescription = "音频",
                modifier = Modifier.size(100.dp),
                tint = Color.White.copy(alpha = 0.25f),
            )
        }
    }

    if (fullscreen) {
        // ── 横屏/全屏：全屏视频 + 半透明控制条 ──
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoContent(Modifier.fillMaxSize())

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    // 顶部栏
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                                ),
                            )
                            .padding(horizontal = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                            }
                            Text(
                                title.ifEmpty { "未选择媒体" },
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (isVideo) {
                                IconButton(onClick = { isFullScreen = !isFullScreen }) {
                                    Icon(Icons.Filled.FullscreenExit, "退出全屏", tint = Color.White)
                                }
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, "更多", tint = Color.White)
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("字幕") },
                                        onClick = { menuExpanded = false; showSubtitleDialog = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("清除进度") },
                                        onClick = { menuExpanded = false; playerVm.clearProgressAndRestart() },
                                    )
                                }
                            }
                        }
                    }

                    // 居中播放按钮
                    if (!isPlaying) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            "播放",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(80.dp)
                                .clickable { playerVm.togglePlay() },
                        )
                    }

                    // 底部控制条
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Slider(
                            value = (if (isScrubbing) scrubValue else position.toFloat()).coerceIn(0f, maxValue),
                            onValueChange = {
                                isScrubbing = true
                                scrubValue = it
                                controlsVisible = true
                            },
                            onValueChangeFinished = {
                                isScrubbing = false
                                playerVm.seekTo(scrubValue.toLong())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                            ),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(formatDuration(position), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                            Text(formatDuration(duration), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
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
                            IconButton(onClick = { playerVm.seekTo((position - 10_000).coerceAtLeast(0)) }) {
                                Icon(Icons.Filled.Replay10, "后退10s", tint = Color.White)
                            }
                            IconButton(onClick = { playerVm.togglePlay() }, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            IconButton(onClick = { playerVm.seekTo((position + 30_000).coerceAtMost(duration)) }) {
                                Icon(Icons.Filled.Forward30, "前进30s", tint = Color.White)
                            }
                            IconButton(onClick = { playerVm.next() }) {
                                Icon(Icons.Filled.SkipNext, "下一首", tint = Color.White)
                            }
                        }
                    }
                }
            }

            SnackbarHost(snackbarHostState)
        }
    } else {
        // ── 竖屏：视频区上半部 + 控制列表下半部 ──
        val scrollState = rememberScrollState()
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 视频区：宽度撑满，高度计算实际比例（不拉伸变形）
            // 用固定高度 + 内容缩放让引擎自己处理比例
            val videoFraction = if (isVideo) 0.42f else 0.2f
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(videoFraction)
                    .background(Color.Black)
                    .clickable { controlsVisible = !controlsVisible },
            ) {
                if (isVideo) {
                    VideoContent(Modifier.fillMaxSize())
                } else {
                    AudioPlaceholder(Modifier.fillMaxSize())
                }

                // 半透明控制层
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(300)),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // 顶部
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                                    ),
                                )
                                .padding(horizontal = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            ) {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                                }
                                Text(
                                    title.ifEmpty { "未选择媒体" },
                                    modifier = Modifier.weight(1f),
                                    color = Color.White,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (isVideo) {
                                    IconButton(onClick = { isFullScreen = !isFullScreen }) {
                                        Icon(Icons.Filled.Fullscreen, "全屏", tint = Color.White)
                                    }
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
                                            onClick = { menuExpanded = false; showSubtitleDialog = true },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("清除进度") },
                                            onClick = { menuExpanded = false; playerVm.clearProgressAndRestart() },
                                        )
                                    }
                                }
                            }
                        }

                        // 大播放按钮
                        if (!isPlaying) {
                            IconButton(
                                onClick = { playerVm.togglePlay() },
                                modifier = Modifier.align(Alignment.Center).size(72.dp),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    "播放",
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White,
                                )
                            }
                        }

                        // 底部控制
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                    ),
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Slider(
                                value = (if (isScrubbing) scrubValue else position.toFloat()).coerceIn(0f, maxValue),
                                onValueChange = {
                                    isScrubbing = true
                                    scrubValue = it
                                    controlsVisible = true // 拖动时保持可见
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                    playerVm.seekTo(scrubValue.toLong())
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                                ),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(formatDuration(position), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                                Text(formatDuration(duration), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
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
                                IconButton(onClick = { playerVm.seekTo((position - 10_000).coerceAtLeast(0)) }) {
                                    Icon(Icons.Filled.Replay10, "后退10s", tint = Color.White)
                                }
                                IconButton(onClick = { playerVm.togglePlay() }, modifier = Modifier.size(56.dp)) {
                                    Icon(
                                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        if (isPlaying) "暂停" else "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                IconButton(onClick = { playerVm.seekTo((position + 30_000).coerceAtMost(duration)) }) {
                                    Icon(Icons.Filled.Forward30, "跳进30s", tint = Color.White)
                                }
                                IconButton(onClick = { playerVm.next() }) {
                                    Icon(Icons.Filled.SkipNext, "下一首", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // ── 下半部：信息 + 功能 Chips + 列表 ──
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column {
                    Text(title.ifEmpty { "未选择媒体" }, style = MaterialTheme.typography.titleLarge)
                    Text(stateLabel(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SectionHeader("倍速")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    playbackSpeeds.forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = { playerVm.setSpeed(s) },
                            label = { Text("${if (s % 1f == 0f) s.toInt() else s}x") },
                        )
                    }
                }

                SectionHeader("模式")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PlayMode.values().forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { playerVm.setMode(m) },
                            label = { Text(modeLabel(m)) },
                        )
                    }
                }

                SectionHeader("内核")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    EngineType.values().forEach { t ->
                        FilterChip(
                            selected = engineType == t,
                            onClick = { playerVm.switchEngine(t) },
                            label = { Text(engineLabel(t)) },
                            enabled = t != EngineType.VLC || isVlcAvailable,
                        )
                    }
                }

                SectionHeader("播放列表")
                if (items.isEmpty()) {
                    Text("播放列表为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    items.forEach { item ->
                        val isCurrent = item.id == currentItemId
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.name,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else null,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (item.mediaType == MediaType.VIDEO) Icons.Filled.VideoLibrary else Icons.Filled.AudioFile,
                                    null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                if (isCurrent && isPlaying) {
                                    Icon(Icons.Filled.PlayArrow, "正在播放", tint = MaterialTheme.colorScheme.primary)
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
                }

                Spacer(Modifier.height(Spacing.lg))
            }

            SnackbarHost(snackbarHostState)
        }
    }

    // 字幕对话框（不变）
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("字幕") },
            text = {
                Column {
                    SubtitleChoiceRow("关闭字幕") {
                        playerVm.selectSubtitle(null)
                        showSubtitleDialog = false
                    }
                    subtitles.forEach { sub ->
                        SubtitleChoiceRow(
                            buildString {
                                append("${sub.label}${sub.mimeType?.let { "（${it}）" }.orEmpty()}")
                                sub.language?.let { append(" · ${it}") }
                            },
                            onClick = {
                                if (sub.language != null) playerVm.selectSubtitle(sub.language)
                                else playerVm.enableSubtitles()
                                showSubtitleDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSubtitleDialog = false }) { Text("完成") } },
        )
    }
}

// 保持 file-level 辅助函数不变（SubtitleChoiceRow、modeLabel、engineLabel、formatDuration 等）
@Composable
private fun SubtitleChoiceRow(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

// 先从 PlayerControlBar 引用，自身不再重复声明