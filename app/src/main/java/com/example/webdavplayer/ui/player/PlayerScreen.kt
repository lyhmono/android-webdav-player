@file:OptIn(UnstableApi::class)

package com.example.webdavplayer.ui.player

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.navigation.NavHostController
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.findActivity
import com.example.webdavplayer.ui.common.formatDuration
import com.example.webdavplayer.ui.common.modeLabel
import com.example.webdavplayer.ui.common.stateLabel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.theme.Spacing
import kotlinx.coroutines.delay

private val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 控制栏自动隐藏时长 */
private const val CONTROLS_AUTO_HIDE_MS = 3000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    navController: NavHostController,
    playerVm: PlayerViewModel,
    playlistVm: PlaylistViewModel,
) {
    val title by playerVm.title.collectAsStateWithLifecycle()
    val state by playerVm.state.collectAsStateWithLifecycle()
    // #9: 不在父级订阅 position/duration——只在 PlayerControls / AudioProgressBar 内部订阅
    //   原代码 200ms 进度回调让整个 PlayerScreen 重组（含 PlayerSurface）
    val items by playerVm.items.collectAsStateWithLifecycle()
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val mediaType by playerVm.currentMediaType.collectAsStateWithLifecycle()
    val isOnline by playerVm.isOnline.collectAsStateWithLifecycle()
    val currentItemId by playerVm.currentItemId.collectAsStateWithLifecycle()
    val speed by playerVm.speed.collectAsStateWithLifecycle()
    val subtitles by playerVm.subtitles.collectAsStateWithLifecycle()
    val player by playerVm.player.collectAsStateWithLifecycle()
    val isPlaying = state == PlaybackState.PLAYING

    var isFullScreen by remember { mutableStateOf(false) }
    val isVideo = mediaType == MediaType.VIDEO
    // #1/#19：fullscreen 仅由用户主动切换的 isFullScreen 决定
    // 原逻辑用 isLandscape 参与 fullscreen 判定，导致自然横屏时用户点"退出全屏"仍锁 LANDSCAPE
    val fullscreen = isVideo && isFullScreen

    val context = LocalContext.current
    val activity = context.findActivity()
    DisposableEffect(isFullScreen, isVideo) {
        activity?.requestedOrientation = when {
            !isVideo -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            isFullScreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            // 视频非全屏：不锁方向，允许用户自由旋转设备（点全屏按钮才主动进入横屏）
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 控制栏显隐（默认隐藏：点击视频框只切换控制栏，不误触播放/暂停按钮）
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    // U2：自动隐藏 token——每次用户交互（点视频区/点控制栏按钮）都自增，重置 3 秒计时
    var controlsHideToken by remember { mutableStateOf(0) }

    // #4：网络状态提示（断网/恢复各提示一次；首次进入不弹）
    var wasOnline by remember { mutableStateOf(isOnline) }
    LaunchedEffect(isOnline) {
        if (isOnline != wasOnline) {
            snackbarHostState.showSnackbar(if (isOnline) "网络已恢复" else "网络已断开")
            wasOnline = isOnline
        }
    }
    LaunchedEffect(items) {
        // #3：currentItemId 不在新列表（被 replace 清掉/不在目标服务器）或首次进入都自动播第一项
        if (items.isNotEmpty() && items.none { it.id == currentItemId }) {
            playerVm.playItem(items.first())
        }
    }

    ImmersiveModeEffect(enabled = fullscreen)

    // ===== 视频区：PlayerSurface + 自定义控制层 + 手势层 =====
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            if (isVideo && player != null) {
                // 方案 C：PlayerSurface 直接绑定 ExoPlayer 实例（UI 直连引擎，不经 MediaSession）
                PlayerSurface(
                    player = player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize(),
                )

                // 手势层（亮度/音量/快进退 + 点击切换控制栏）— 横竖屏统一启用（#18）
                VideoGestureLayer(
                    modifier = Modifier.fillMaxSize(),
                    isVideo = true,
                    // #17：控制栏可见时禁用拖拽（避免与进度条 Slider 争手），点击切换保留
                    gesturesEnabled = !controlsVisible,
                    // #9: 不再依赖外层 position 变量——直接同步读 StateFlow 当前值
                    onSeekBy = { delta ->
                        val cur = playerVm.position.value
                        val dur = playerVm.duration.value
                        playerVm.seekTo((cur + delta).coerceIn(0, dur.coerceAtLeast(1)))
                    },
                    onToggleControls = {
                        controlsVisible = !controlsVisible
                        if (controlsVisible) controlsHideToken++
                    },
                )

                // 控制层（U1：去掉 clickable Box，改由 VGL 的 onToggleControls 驱动）
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
                ) {
                    PlayerControls(
                        title = title,
                        isPlaying = isPlaying,
                        // #9：传入 State 而非值——position 变化只重组 PlayerControls 内部，不波及父级
                        positionState = playerVm.position.collectAsStateWithLifecycle(),
                        durationState = playerVm.duration.collectAsStateWithLifecycle(),
                        onBack = { navController.popBackStack() },
                        onTogglePlay = { playerVm.togglePlay(); controlsHideToken++ },
                        // #10：拖动状态在 PlayerControls 内部——onSeekFinished 才回调真正 seek
                        onSeekRequested = { playerVm.seekTo(it); controlsHideToken++ },
                        onPrev = { playerVm.previous(); controlsHideToken++ },
                        onNext = { playerVm.next(); controlsHideToken++ },
                        onMore = { menuExpanded = true; controlsHideToken++ },
                        onToggleFullscreen = { isFullScreen = !isFullScreen; controlsHideToken++ },
                        isFullscreen = fullscreen,
                    )
                }

                // U2：控制栏自动隐藏计时器——任何用户交互（token 变化）都重置 3 秒倒计时
                LaunchedEffect(controlsVisible, controlsHideToken) {
                    if (controlsVisible) {
                        delay(CONTROLS_AUTO_HIDE_MS)
                        controlsVisible = false
                    }
                }
            } else if (isVideo && player == null) {
                // #24：区分 loading 与 error——prepare 失败时显示错误提示而非无限 loading
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state == PlaybackState.ERROR) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Error,
                                "播放失败",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White.copy(alpha = 0.5f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("播放失败", color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "请检查网络或文件是否存在",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else if (mediaType == MediaType.IMAGE) {
                // 图片查看：直接展示解码后的 Bitmap；点击切换顶部信息栏（#16），不直接退出
                val imageBitmap by playerVm.imageBitmap.collectAsStateWithLifecycle()
                val bmp = imageBitmap
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    controlsVisible = !controlsVisible
                                    if (controlsVisible) controlsHideToken++
                                },
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                    // 图片顶部信息栏（返回 + 标题），复用控制栏显隐状态
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                        exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                            }
                            Text(
                                text = title.ifEmpty { "未选择媒体" },
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // #21：图片顶栏也暴露更多按钮（复用根级 DropdownMenu）
                            IconButton(
                                onClick = {
                                    menuExpanded = true
                                    controlsHideToken++
                                },
                            ) {
                                Icon(Icons.Filled.MoreVert, "更多", tint = Color.White)
                            }
                        }
                    }
                }
            } else if (!isVideo && currentItemId != null) {
                // A5：音频模式也显示标题 + 播放按钮；左右滑动切上一首/下一首（#23）
                val density = LocalDensity.current
                var dragAccum by remember { mutableStateOf(0f) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragAccum = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragAccum += dragAmount
                                    val threshold = with(density) { 80.dp.toPx() }
                                    if (dragAccum > threshold) {
                                        playerVm.next()
                                        dragAccum = 0f
                                    } else if (dragAccum < -threshold) {
                                        playerVm.previous()
                                        dragAccum = 0f
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AudioFile, "音频", modifier = Modifier.size(100.dp), tint = Color.White.copy(alpha = 0.25f))
                        Spacer(Modifier.height(16.dp))
                        Text(title.ifEmpty { "未选择媒体" }, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        IconButton(
                            onClick = { playerVm.togglePlay() },
                            modifier = Modifier.size(72.dp),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "播放",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White,
                            )
                        }
                        // 音频进度条（#9/#10：独立组件，内部订阅 position/duration，避免父级重组）
                        AudioProgressBar(
                            positionState = playerVm.position.collectAsStateWithLifecycle(),
                            durationState = playerVm.duration.collectAsStateWithLifecycle(),
                            onSeekRequested = { playerVm.seekTo(it) },
                        )
                    }
                }
            } else {
                // #3：尚未确定媒体类型（初始帧）/ 引擎未就绪——显示 loading，避免闪现音频占位图标
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // A4：横屏时隐藏下半部信息区
        if (!fullscreen) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回") }
                    Text(title.ifEmpty { "未选择媒体" }, style = MaterialTheme.typography.titleLarge)
                    // #20：菜单统一由根级 DropdownMenu 承接（横屏全屏时也可用）
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                }
                Text(stateLabel(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                SectionHeader("倍速")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    playbackSpeeds.forEach { s ->
                        FilterChip(selected = speed == s, onClick = { playerVm.setSpeed(s) }, label = { Text("${if (s % 1f == 0f) s.toInt() else s}x") })
                    }
                }

                SectionHeader("模式")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PlayMode.values().forEach { m ->
                        FilterChip(selected = mode == m, onClick = { playerVm.setMode(m) }, label = { Text(modeLabel(m)) })
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
                                Text(item.name, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCurrent) FontWeight.Bold else null)
                            },
                            leadingContent = {
                                Icon(
                                    when (item.mediaType) {
                                        MediaType.VIDEO -> Icons.Filled.VideoLibrary
                                        MediaType.AUDIO -> Icons.Filled.AudioFile
                                        MediaType.IMAGE -> Icons.Filled.Photo
                                        MediaType.OTHER -> Icons.Filled.VideoLibrary
                                    },
                                    null, tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingContent = {
                                if (isCurrent && isPlaying) Icon(Icons.Filled.PlayArrow, "正在播放", tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { playerVm.playItem(item) },
                                onLongClick = { playlistVm.removeItem(item.id) },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
        // P6-2：SnackbarHost 仅在非全屏时显示，避免全屏沉浸式时 snackbar 破坏体验
        if (!fullscreen) {
            SnackbarHost(snackbarHostState)
        }
    }

    // #20/#22：根级菜单（横屏全屏与竖屏共用）——一级：字幕/清除进度/倍速/模式；倍速与模式为二级子菜单
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(text = { Text("字幕") }, onClick = { menuExpanded = false; showSubtitleDialog = true })
        DropdownMenuItem(text = { Text("清除进度") }, onClick = { menuExpanded = false; playerVm.clearProgressAndRestart() })
        DropdownMenuItem(text = { Text("倍速") }, onClick = { menuExpanded = false; speedMenuExpanded = true })
        DropdownMenuItem(text = { Text("模式") }, onClick = { menuExpanded = false; modeMenuExpanded = true })
    }
    DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
        playbackSpeeds.forEach { s ->
            DropdownMenuItem(
                text = { Text("${if (s % 1f == 0f) s.toInt() else s}x${if (speed == s) "  ✓" else ""}") },
                onClick = { playerVm.setSpeed(s); speedMenuExpanded = false },
            )
        }
    }
    DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
        PlayMode.values().forEach { m ->
            DropdownMenuItem(
                text = { Text("${modeLabel(m)}${if (mode == m) "  ✓" else ""}") },
                onClick = { playerVm.setMode(m); modeMenuExpanded = false },
            )
        }
    }

    // 字幕对话框
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("字幕") },
            text = {
                Column {
                    SubtitleChoiceRow("关闭字幕") { playerVm.selectSubtitle(null); showSubtitleDialog = false }
                    subtitles.forEach { sub ->
                        SubtitleChoiceRow(
                            buildString {
                                append("${sub.label}${sub.mimeType?.let { "（${it}）" }.orEmpty()}")
                                sub.language?.let { append(" · ${it}") }
                            },
                            onClick = {
                                if (sub.language != null) playerVm.selectSubtitle(sub.language) else playerVm.enableSubtitles()
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

@Composable
private fun SubtitleChoiceRow(label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick))
}

/**
 * #9/#10：音频模式竖屏进度条——独立 Composable，内部订阅 position/duration + seekPosition。
 * 父级 PlayerScreen 不再订阅 position，避免 200ms 进度回调触发整体重组。
 */
@Composable
private fun AudioProgressBar(
    positionState: androidx.compose.runtime.State<Long>,
    durationState: androidx.compose.runtime.State<Long>,
    onSeekRequested: (Long) -> Unit,
) {
    var seekPosition by remember { mutableStateOf<Long?>(null) }
    val position = positionState.value
    val duration = durationState.value
    val displayPos = seekPosition ?: position

    if (duration > 0) {
        Slider(
            value = (displayPos.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { ratio -> seekPosition = (ratio * duration).toLong() },
            onValueChangeFinished = {
                val final = seekPosition ?: position
                seekPosition = null
                onSeekRequested(final)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Text(
            "${formatDuration(displayPos)} / ${formatDuration(duration)}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
