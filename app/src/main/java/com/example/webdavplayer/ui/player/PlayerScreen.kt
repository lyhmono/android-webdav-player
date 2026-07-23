@file:OptIn(ExperimentalFoundationApi::class, UnstableApi::class)

package com.example.webdavplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.example.webdavplayer.BuildConfig
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.engineLabel
import com.example.webdavplayer.ui.common.formatDuration
import com.example.webdavplayer.ui.common.modeLabel
import com.example.webdavplayer.ui.common.stateLabel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.theme.Spacing

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
    val exoPlayer by playerVm.exoPlayer.collectAsStateWithLifecycle()
    val isVlcEngine by playerVm.isVlcEngine.collectAsStateWithLifecycle()

    val isVlcAvailable = BuildConfig.FLAVOR == "full"
    val isPlaying = state == PlaybackState.PLAYING
    val isVideo = mediaType == MediaType.VIDEO

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isFullScreen by remember { mutableStateOf(false) }

    // 退出播放界面时停止播放
    // 关键：放在最外层，全屏/非全屏切换不会触发 onDispose
    DisposableEffect(Unit) {
        onDispose {
            playerVm.stop()
        }
    }

    // 全屏：隐藏系统 UI + 横屏（仅在 isFullScreen=true 时生效）
    DisposableEffect(isFullScreen) {
        val act = activity
        if (isFullScreen && act != null) {
            val originalOrientation = act.requestedOrientation
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            act.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            act.window?.decorView?.let { decorView ->
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
            onDispose {
                act.requestedOrientation = originalOrientation
                act.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                act.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        } else {
            onDispose {}
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isOnline) {
        if (!isOnline) snackbarHostState.showSnackbar("网络已断开，播放可能受到影响")
    }
    LaunchedEffect(resumedPosition) {
        resumedPosition?.let { p ->
            if (p > 0) {
                snackbarHostState.showSnackbar("已从 ${formatDuration(p)} 续播")
                playerVm.consumeResumedPosition()
            }
        }
    }

    // 关键修复：全屏和非全屏使用同一个 Scaffold，通过内容切换避免 DisposableEffect 重建
    Scaffold(
        topBar = {
            // 全屏时隐藏 TopAppBar
            if (!isFullScreen || !isVideo) {
                TopAppBar(
                    title = { Text(title.ifEmpty { "播放" }) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    actions = {
                        if (isVideo) {
                            IconButton(onClick = { isFullScreen = true }) {
                                Icon(Icons.Filled.Fullscreen, "全屏")
                            }
                        }
                        IconButton(onClick = { navController.navigate("playlist") }) {
                            Icon(Icons.Filled.QueueMusic, "播放列表")
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, "更多")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isFullScreen && isVideo) {
            // ===== 全屏视频播放器 =====
            FullScreenVideoContent(
                exoPlayer = exoPlayer,
                isVlcEngine = isVlcEngine,
                position = position,
                duration = duration,
                isPlaying = isPlaying,
                title = title,
                onSeekTo = { playerVm.seekTo(it) },
                onTogglePlay = { playerVm.togglePlay() },
                onExitFullScreen = { isFullScreen = false },
                onAttachVlcSurfaceView = { sv -> playerVm.attachVlcSurfaceView(sv) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // ===== 普通模式 =====
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // ===== 视频画面 =====
                if (isVideo) {
                    VideoSurface(
                        exoPlayer = exoPlayer,
                        isVlcEngine = isVlcEngine,
                        onAttachVlcSurfaceView = { sv -> playerVm.attachVlcSurfaceView(sv) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }

                // ===== 标题 + 状态 =====
                Text(title.ifEmpty { "未选择媒体" }, style = MaterialTheme.typography.titleLarge)
                Text(stateLabel(state), style = MaterialTheme.typography.bodyMedium)

                // ===== 音频模式才有播放控制按钮（视频用 PlayerView 自带的） =====
                if (!isVideo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = { playerVm.previous() }) {
                            Icon(Icons.Filled.SkipPrevious, "上一首")
                        }
                        Spacer(Modifier.width(24.dp))
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            IconButton(onClick = { playerVm.togglePlay() }, modifier = Modifier.padding(4.dp)) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    "播放/暂停",
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(24.dp))
                        IconButton(onClick = { playerVm.next() }) {
                            Icon(Icons.Filled.SkipNext, "下一首")
                        }
                    }

                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                        onValueChange = { playerVm.seekTo(it.toLong()) },
                        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatDuration(position), style = MaterialTheme.typography.labelSmall)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                    }
                }

                // ===== 播放模式 =====
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

                // ===== 播放内核 =====
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

                // ===== 播放列表 =====
                SectionHeader("播放列表")
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                                    fontWeight = if (isCurrent) FontWeight.Bold else null,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (item.mediaType == MediaType.VIDEO) Icons.Filled.VideoLibrary
                                    else Icons.Filled.AudioFile,
                                    contentDescription = null,
                                    tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                if (isCurrent && isPlaying) {
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
}

// ============================================================
// 视频渲染组件（非全屏 + 全屏共用）
// ============================================================
@Composable
private fun VideoSurface(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    isVlcEngine: Boolean,
    onAttachVlcSurfaceView: (SurfaceView?) -> Unit,
    modifier: Modifier = Modifier,
    resizeModeFill: Boolean = false,
) {
    if (isVlcEngine) {
        // VLC: UI 层创建 SurfaceView，通过 setVideoView + attachViews 标准方式绑定
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    // 立即传给 VlcEngine，VlcEngine 会在 play() 时 attachViews
                    onAttachVlcSurfaceView(sv)
                }
            },
            update = { sv ->
                // 确保 VlcEngine 持有最新的 SurfaceView 引用
                onAttachVlcSurfaceView(sv)
            },
            modifier = modifier,
        )
    } else {
        // Media3: PlayerView 自带控制器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    player = exoPlayer
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    controllerAutoShow = true
                    resizeMode = if (resizeModeFill) {
                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) view.player = exoPlayer
            },
            modifier = modifier,
        )
    }
}

// ============================================================
// 全屏视频播放内容（在 Scaffold 内部渲染，不会触发外层 onDispose）
// ============================================================
@Composable
private fun FullScreenVideoContent(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    isVlcEngine: Boolean,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    title: String,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onExitFullScreen: () -> Unit,
    onAttachVlcSurfaceView: (SurfaceView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showControls by remember { mutableStateOf(true) }
    var seekPreview by remember { mutableLongStateOf(-1L) }
    val effectivePosition = if (seekPreview >= 0) seekPreview else position
    val durSafe = duration.coerceAtLeast(1L)

    Box(
        modifier = modifier.background(Color.Black),
    ) {
        // --- 视频画面（全屏铺满） ---
        VideoSurface(
            exoPlayer = exoPlayer,
            isVlcEngine = isVlcEngine,
            onAttachVlcSurfaceView = onAttachVlcSurfaceView,
            modifier = Modifier.fillMaxSize(),
            resizeModeFill = true,
        )

        // --- 手势层（点击切换控制 + 横向滑动 seek） ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = { showControls = !showControls })
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            seekPreview = position
                        },
                        onDragEnd = {
                            if (seekPreview >= 0) onSeekTo(seekPreview)
                            seekPreview = -1L
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val widthPx = this.size.width.toFloat().coerceAtLeast(1f)
                            val deltaMs = (dragAmount / widthPx * durSafe).toLong()
                            seekPreview = (seekPreview + deltaMs).coerceIn(0L, duration)
                        },
                    )
                },
        )

        // --- 顶部栏 ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExitFullScreen) {
                    Icon(Icons.Filled.FullscreenExit, "退出全屏", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // --- 底部控制栏 ---
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Slider(
                    value = effectivePosition.toFloat().coerceIn(0f, durSafe.toFloat()),
                    onValueChange = { seekPreview = it.toLong() },
                    onValueChangeFinished = {
                        if (seekPreview >= 0) onSeekTo(seekPreview)
                        seekPreview = -1L
                    },
                    valueRange = 0f..durSafe.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatDuration(effectivePosition),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            onSeekTo((effectivePosition - 15_000).coerceAtLeast(0))
                        }) {
                            Icon(Icons.Filled.FastRewind, "后退15秒", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = Color.White.copy(alpha = 0.2f),
                        ) {
                            IconButton(onClick = onTogglePlay, modifier = Modifier.padding(4.dp)) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    "播放/暂停",
                                    tint = Color.White,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = {
                            onSeekTo((effectivePosition + 15_000).coerceAtMost(duration))
                        }) {
                            Icon(Icons.Filled.FastForward, "快进15秒", tint = Color.White)
                        }
                    }
                    Text(
                        formatDuration(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // --- 滑动 seek 提示气泡 ---
        if (seekPreview >= 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(
                    "${formatDuration(seekPreview)} / ${formatDuration(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 从 Context 向上查找 Activity。 */
private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
