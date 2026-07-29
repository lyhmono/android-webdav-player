@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.player

import android.content.pm.ActivityInfo
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaybackState
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.findActivity
import com.example.webdavplayer.ui.common.formatDuration
import com.example.webdavplayer.ui.common.stateLabel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.theme.Spacing

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
    val mediaController by playerVm.controller.collectAsStateWithLifecycle()
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

    ImmersiveModeEffect(enabled = isFullScreen)

    // ===== 竖屏：PlayerView + 下半部信息 =====
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 视频区 — PlayerView 自动处理比例 + 控制条
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            if (isVideo && mediaController != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = mediaController
                            useController = true
                            controllerShowTimeoutMs = 3000
                            controllerAutoShow = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowSubtitleButton(false)
                            setControllerOnFullScreenModeChangedListener {
                                isFullScreen = it
                            }
                        }
                    },
                )
                // 手势层
                VideoGestureLayer(
                    modifier = Modifier.fillMaxSize(),
                    isVideo = true,
                    durationMs = duration,
                    onSeekBy = { delta ->
                        playerVm.seekTo((position + delta).coerceIn(0, duration.coerceAtLeast(1)))
                    },
                )
            } else if (!isVideo) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AudioFile, "音频", modifier = Modifier.size(100.dp), tint = Color.White.copy(alpha = 0.25f))
                    IconButton(
                        onClick = { playerVm.togglePlay() },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, "播放", modifier = Modifier.size(48.dp), tint = Color.White)
                    }
                }
            }
        }

        // 下半部
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
                                fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else null)
                        },
                        leadingContent = {
                            Icon(if (item.mediaType == MediaType.VIDEO) Icons.Filled.VideoLibrary else Icons.Filled.AudioFile,
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
        SnackbarHost(snackbarHostState)
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

fun modeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "顺序"
    PlayMode.LOOP -> "循环"
    PlayMode.SHUFFLE -> "随机"
}

fun engineLabel(type: EngineType): String = when (type) {
    EngineType.MEDIA3 -> "Media3"
    EngineType.VLC -> "libVLC"
}