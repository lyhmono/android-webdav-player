@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.WifiOff
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

    val isVlcAvailable = BuildConfig.FLAVOR == "full"
    val isPlaying = state == PlaybackState.PLAYING

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var isFullScreen by remember { mutableStateOf(false) }

    val isVideo = mediaType == MediaType.VIDEO
    // 视频手势层仅在视频 + 横屏/全屏时启用（C4）。
    val showGesture = isVideo && (isFullScreen || isLandscape)

    var menuExpanded by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(Spacing.lg))
                Text(title.ifEmpty { "未选择媒体" }, style = MaterialTheme.typography.titleLarge)
                Text(stateLabel(state), style = MaterialTheme.typography.bodyMedium)

                // 拖拽中保持本地值，释放时才 seek，避免频繁 seekTo 导致卡顿
                var sliderValue by remember { mutableStateOf<Float?>(null) }
                val sliderPos = sliderValue
                    ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat())

                Slider(
                    value = sliderPos,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        sliderValue?.let { playerVm.seekTo(it.toLong()) }
                        sliderValue = null
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${formatDuration(position)} / ${formatDuration(duration)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

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
                        ListItem(
                            headlineContent = { Text(item.name) },
                            leadingContent = {
                                Icon(
                                    if (item.mediaType == MediaType.VIDEO) {
                                        Icons.Filled.VideoLibrary
                                    } else {
                                        Icons.Filled.AudioFile
                                    },
                                    null,
                                )
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

            // 视频手势层（C4）：横屏/全屏 + 视频时叠加，捕获亮度/音量/快退手势。
            if (showGesture) {
                VideoGestureLayer(
                    modifier = Modifier.fillMaxSize(),
                    isVideo = true,
                    durationMs = duration,
                    onSeekBy = { delta ->
                        playerVm.seekTo((position + delta).coerceIn(0, duration.coerceAtLeast(1)))
                    },
                )
            }
        }
    }
}

// modeLabel / engineLabel / stateLabel / formatDuration 已抽取到 ui.common.Labels
