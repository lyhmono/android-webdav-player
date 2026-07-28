package com.example.webdavplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.ui.theme.Spacing

/**
 * 播放控制条（C3 / §7）。
 *
 * 包含：播放/暂停、上一首、下一首、播放模式 chips（顺序/循环/随机）、
 * 播放内核 chips（Media3/libVLC，VLC 仅 full 风味可用）、播放列表入口、更多菜单（清除进度/从头播放）。
 *
 * 在竖屏由 [PlayerScreen] 置于控制区；在横屏/全屏叠加于视频底部。
 * 当 [showMoreMenu] 为 false（竖屏、TopAppBar 已含更多菜单）时隐藏本组件的更多菜单，避免重复。
 * [onBack] / [onToggleFullScreen] 仅在非 null 时显示（横屏/全屏态由控制条承担这些导航动作）。
 */
@Composable
fun PlayerControlBar(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    mode: PlayMode,
    engineType: EngineType,
    isVlcAvailable: Boolean,
    showMoreMenu: Boolean = true,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetMode: (PlayMode) -> Unit,
    onSwitchEngine: (EngineType) -> Unit,
    onOpenPlaylist: () -> Unit,
    onClearProgress: () -> Unit,
    onShowSubtitles: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onToggleFullScreen: (() -> Unit)? = null,
    isFullScreen: Boolean = false,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // 播放控制行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "返回")
                }
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, "上一首")
            }
            Spacer(Modifier.width(Spacing.lg))
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "播放/暂停",
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, "下一首")
            }
            if (onToggleFullScreen != null) {
                IconButton(onClick = onToggleFullScreen) {
                    Icon(
                        if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        "全屏",
                    )
                }
            }
            IconButton(onClick = onOpenPlaylist) {
                Icon(Icons.Filled.QueueMusic, "播放列表")
            }
            if (showMoreMenu) {
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
                            onClearProgress()
                        },
                    )
                    if (onShowSubtitles != null) {
                        DropdownMenuItem(
                            text = { Text("字幕") },
                            onClick = {
                                menuExpanded = false
                                onShowSubtitles()
                            },
                        )
                    }
                }
            }
        }

        // 播放模式 chips
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayMode.values().forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onSetMode(m) },
                    label = { Text(modeLabel(m)) },
                )
            }
        }

        // 播放内核 chips（VLC 仅 full 风味可用）
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EngineType.values().forEach { t ->
                FilterChip(
                    selected = engineType == t,
                    onClick = { onSwitchEngine(t) },
                    label = { Text(engineLabel(t)) },
                    enabled = if (t == EngineType.VLC) isVlcAvailable else true,
                )
            }
        }
    }
}

/** 播放模式 → 中文标签。 */
fun modeLabel(mode: PlayMode): String = when (mode) {
    PlayMode.SEQUENTIAL -> "顺序"
    PlayMode.LOOP -> "循环"
    PlayMode.SHUFFLE -> "随机"
}

/** 播放内核 → 中文标签。 */
fun engineLabel(type: EngineType): String = when (type) {
    EngineType.MEDIA3 -> "Media3 / ExoPlayer"
    EngineType.VLC -> "libVLC"
}
