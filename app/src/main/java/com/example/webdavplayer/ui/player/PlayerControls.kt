package com.example.webdavplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.webdavplayer.ui.common.formatDuration

/**
 * 自定义 Compose 播放控制层（方案 B：media3-ui-compose PlayerSurface 配套）。
 *
 * 视觉风格：顶部/底部半透明渐变遮罩 + 居中大圆播放按钮 + 细进度条。
 *
 * #9/#10：进度位置 [positionState] 与拖动状态 [seekPosition] 由本组件**内部持有**——
 * 避免 200ms progress 回调与 60fps onValueChange 触发父级 [PlayerScreen] 整体重组。
 */
@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionState: androidx.compose.runtime.State<Long>,
    durationState: androidx.compose.runtime.State<Long>,
    currentSpeed: Float = 1.0f,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRequested: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onSpeedSelected: (Float) -> Unit = {},
    speedOptions: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    onToggleFullscreen: () -> Unit = {},
    isFullscreen: Boolean = false,
    moreMenuExpanded: Boolean = false,
    onMoreMenuDismiss: () -> Unit = {},
    moreMenuContent: @Composable () -> Unit = {},
    modeMenuExpanded: Boolean = false,
    onModeMenuDismiss: () -> Unit = {},
    modeMenuContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var seekPosition by remember { mutableStateOf<Long?>(null) }
    val position = positionState.value
    val duration = durationState.value
    val displayPos = seekPosition ?: position
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        // 顶部渐变遮罩 + 标题栏
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.7f),
                        1f to Color.Transparent,
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                    Text(
                        text = title.ifEmpty { "未选择媒体" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = onMore) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = Color.White)
                    }
                    // 更多菜单锚定在按钮处（右上角弹出），替代根级无锚菜单
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = onMoreMenuDismiss,
                    ) {
                        moreMenuContent()
                    }
                    // 二级菜单（播放模式）：从"模式"展开，同样锚定在按钮处
                    DropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = onModeMenuDismiss,
                    ) {
                        modeMenuContent()
                    }
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        if (isFullscreen) "退出全屏" else "进入全屏",
                        tint = Color.White,
                    )
                }
            }
        }

        // 中间播放按钮组（半透明白色圆背景）
        Row(
            modifier = Modifier
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipPrevious, "上一条", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.size(20.dp))
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(64.dp).clip(CircleShape),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.25f),
                ),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "播/暂停",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.size(20.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.SkipNext, "下一条", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(32.dp))
            }
        }

        // 底部渐变遮罩 + 进度条
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f),
                    ),
                )
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 12.dp),
        ) {
            Slider(
                value = if (duration > 0) (displayPos.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { ratio ->
                    if (duration > 0) seekPosition = (ratio * duration).toLong()
                },
                onValueChangeFinished = {
                    if (duration > 0) {
                        val final = seekPosition ?: position
                        seekPosition = null
                        onSeekRequested(final)
                    }
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.9f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDuration(displayPos), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall)
                // 倍速按钮：显示当前倍速，点击弹出选择菜单
                Box {
                    Text(
                        text = "${if (currentSpeed % 1f == 0f) currentSpeed.toInt() else currentSpeed}x",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clickable { speedMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                        speedOptions.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${if (s % 1f == 0f) s.toInt() else s}x${if (s == currentSpeed) "  ✓" else ""}") },
                                onClick = { onSpeedSelected(s); speedMenuExpanded = false },
                            )
                        }
                    }
                }
                Text(formatDuration(duration), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
