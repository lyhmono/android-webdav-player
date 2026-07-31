@file:OptIn(UnstableApi::class)

package com.example.webdavplayer.ui.player

import android.content.pm.ActivityInfo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    val position by playerVm.position.collectAsStateWithLifecycle()
    val duration by playerVm.duration.collectAsStateWithLifecycle()
    val items by playerVm.items.collectAsStateWithLifecycle()
    val mode by playerVm.mode.collectAsStateWithLifecycle()
    val mediaType by playerVm.currentMediaType.collectAsStateWithLifecycle()
    val isOnline by playerVm.isOnline.collectAsStateWithLifecycle()
    val resumedPosition by playerVm.resumedPosition.collectAsStateWithLifecycle()
    val currentItemId by playerVm.currentItemId.collectAsStateWithLifecycle()
    val speed by playerVm.speed.collectAsStateWithLifecycle()
    val subtitles by playerVm.subtitles.collectAsStateWithLifecycle()
    val player by playerVm.player.collectAsStateWithLifecycle()
    val isPlaying = state == PlaybackState.PLAYING

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullScreen by remember { mutableStateOf(false) }
    val fullscreen = isFullScreen || isLandscape
    val isVideo = mediaType == MediaType.VIDEO

    val context = LocalContext.current
    val activity = context.findActivity()
    DisposableEffect(fullscreen) {
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 控制栏显隐（默认隐藏：点击视频框只切换控制栏，不误触播放/暂停按钮）
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    // 进度条拖动的临时位置（A3 防抖）
    var seekPosition by remember { mutableStateOf<Long?>(null) }
    // U2：自动隐藏 token——每次用户交互（点视频区/点控制栏按钮/拖进度条）都自增，重置 3 秒计时
    var controlsHideToken by remember { mutableStateOf(0) }

    LaunchedEffect(isOnline) {
        if (!isOnline) snackbarHostState.showSnackbar("网络已断开")
    }
    LaunchedEffect(resumedPosition) {
        resumedPosition?.let { pos ->
            if (pos > 0) {
                snackbarHostState.showSnackbar("已从 ${formatDuration(pos)} 续播")
                playerVm.consumeResumedPosition()
            }
        }
    }
    LaunchedEffect(items) {
        if (items.isNotEmpty() && currentItemId == null) {
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

                // 手势层（亮度/音量/快进退 + 点击切换控制栏）— 仅横屏/全屏叠加
                if (fullscreen) {
                    VideoGestureLayer(
                        modifier = Modifier.fillMaxSize(),
                        isVideo = true,
                        durationMs = duration,
                        onSeekBy = { delta ->
                            playerVm.seekTo((position + delta).coerceIn(0, duration.coerceAtLeast(1)))
                        },
                        onToggleControls = {
                            controlsVisible = !controlsVisible
                            if (controlsVisible) controlsHideToken++
                        },
                    )
                } else {
                    // 竖屏：仅点击切换控制栏（无亮度/音量/快进手势）
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                            ) { controlsVisible = !controlsVisible; if (controlsVisible) controlsHideToken++ },
                    )
                }

                // 控制层（U1：去掉 clickable Box，改由 VGL 的 onToggleControls 驱动）
                androidx.compose.animation.AnimatedVisibility(
                    visible = controlsVisible,
                    enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
                ) {
                    PlayerControls(
                        title = title,
                        isPlaying = isPlaying,
                        positionMs = seekPosition ?: position,
                        durationMs = duration,
                        onBack = { navController.popBackStack() },
                        onTogglePlay = { playerVm.togglePlay(); controlsHideToken++ },
                        onSeeking = { seekPosition = it; controlsHideToken++ },
                        onSeekFinished = { seekPosition = null; playerVm.seekTo(it); controlsHideToken++ },
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
                // 引擎尚未创建（prepare 前）显示 loading
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (mediaType == MediaType.IMAGE) {
                // 图片查看：直接展示解码后的 Bitmap
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
                                ) { navController.popBackStack() },
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else if (!isVideo) {
                // A5：音频模式也显示标题 + 播放按钮
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        // 音频进度条
                        if (duration > 0) {
                            Slider(
                                value = if (duration > 0) (seekPosition ?: position).toFloat() / duration else 0f,
                                onValueChange = { ratio -> seekPosition = (ratio * duration).toLong() },
                                onValueChangeFinished = { val finalPos = seekPosition ?: position; seekPosition = null; playerVm.seekTo(finalPos) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                            )
                            Text(
                                "${formatDuration(seekPosition ?: position)} / ${formatDuration(duration)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
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
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("字幕") }, onClick = { menuExpanded = false; showSubtitleDialog = true })
                        DropdownMenuItem(text = { Text("清除进度") }, onClick = { menuExpanded = false; playerVm.clearProgressAndRestart() })
                    }
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
