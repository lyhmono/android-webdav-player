@file:OptIn(ExperimentalFoundationApi::class, UnstableApi::class)

package com.example.webdavplayer.ui.player

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
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
    val vlcSurfaceView by playerVm.vlcSurfaceView.collectAsStateWithLifecycle()

    val isVlcAvailable = BuildConfig.FLAVOR == "full"
    val isPlaying = state == PlaybackState.PLAYING
    val isVideo = mediaType == MediaType.VIDEO

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullScreen by remember { mutableStateOf(false) }

    // 全屏模式：隐藏系统 UI + 横屏
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(isFullScreen) {
        if (isFullScreen && activity != null) {
            val originalOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // 隐藏状态栏和导航栏（沉浸式）
            activity.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            onDispose {
                activity.requestedOrientation = originalOrientation
                activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.window?.decorView?.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_VISIBLE
                )
            }
        } else {
            onDispose {}
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            snackbarHostState.showSnackbar("网络已断开，播放可能受到影响")
        }
    }

    LaunchedEffect(resumedPosition) {
        resumedPosition?.let { pos ->
            if (pos > 0) {
                snackbarHostState.showSnackbar("已从 ${formatDuration(pos)} 续播")
                playerVm.consumeResumedPosition()
            }
        }
    }

    // ===== 全屏视频模式 =====
    if (isFullScreen && isVideo) {
        val useVlc = engineType == EngineType.VLC && vlcSurfaceView != null
        FullScreenVideoPlayer(
            exoPlayer = exoPlayer,
            vlcSurfaceView = vlcSurfaceView,
            useVlc = useVlc,
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            onSeekTo = { playerVm.seekTo(it) },
            onTogglePlay = { playerVm.togglePlay() },
            onExitFullScreen = { isFullScreen = false },
        )
        return
    }

    // ===== 正常模式 =====
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifEmpty { "播放" }) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
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
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // ===== 视频画面 =====
                if (isVideo) {
                    val useVlc = engineType == EngineType.VLC && vlcSurfaceView != null
                    if (useVlc) {
                        // VLC 内核：挂载 SurfaceView
                        AndroidView(
                            factory = { _ ->
                                vlcSurfaceView!!
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                    } else {
                        // Media3/ExoPlayer 内核：使用 PlayerView（自带控制器）
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    setShowNextButton(false)
                                    setShowPreviousButton(false)
                                    layoutParams = android.widget.FrameLayout.LayoutParams(
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                    )
                                }
                            },
                            update = { view ->
                                if (view.player !== exoPlayer) {
                                    view.player = exoPlayer
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
                Text(title.ifEmpty { "未选择媒体" }, style = MaterialTheme.typography.titleLarge)
                Text(stateLabel(state), style = MaterialTheme.typography.bodyMedium)

                // ===== 播放控制 =====
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playerVm.previous() }) {
                        Icon(Icons.Filled.SkipPrevious, "上一首")
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    IconButton(onClick = { playerVm.togglePlay() }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "播放/暂停",
                        )
                    }
                    Spacer(Modifier.width(Spacing.lg))
                    IconButton(onClick = { playerVm.next() }) {
                        Icon(Icons.Filled.SkipNext, "下一首")
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
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

                Spacer(Modifier.height(Spacing.sm))
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
}

// ===== 全屏视频播放器 =====
@Composable
private fun FullScreenVideoPlayer(
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    vlcSurfaceView: android.view.SurfaceView?,
    useVlc: Boolean,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onExitFullScreen: () -> Unit,
) {
    var showControls by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 视频画面填满全屏
        if (useVlc && vlcSurfaceView != null) {
            AndroidView(
                factory = { _ -> vlcSurfaceView },
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { showControls = !showControls },
                    ),
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // 我们用自己的 Compose 控制层
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = { showControls = !showControls },
                    ),
            )
        }

        // 顶部退出按钮
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExitFullScreen) {
                    Icon(Icons.Filled.FullscreenExit, "退出全屏", tint = Color.White)
                }
            }
        }

        // 底部控制栏
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // 进度条
                val sliderPos = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat())
                androidx.compose.material3.Slider(
                    value = sliderPos,
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatDuration(position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "播放/暂停",
                                tint = Color.White,
                            )
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
