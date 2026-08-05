package com.example.webdavplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.webdavplayer.ui.common.formatDuration

/**
 * 自定义 Compose 播放控制层（方案 B：media3-ui-compose PlayerSurface 配套）。
 *
 * 视觉风格：深色渐变遮罩 + 半透明圆角控件 + 居中大播放按钮 + 细进度条。
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
    val progress = if (duration > 0) (displayPos.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(modifier.fillMaxSize()) {
        // ===== 顶部渐变遮罩 + 标题栏 =====
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.85f),
                        0.7f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                    ),
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = title.ifEmpty { "未选择媒体" },
                        color = Color.White.copy(alpha = 0.95f),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = onMore, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.MoreVert, "更多", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = onMoreMenuDismiss,
                        ) {
                            moreMenuContent()
                        }
                        DropdownMenu(
                            expanded = modeMenuExpanded,
                            onDismissRequest = onModeMenuDismiss,
                        ) {
                            modeMenuContent()
                        }
                    }
                    IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(44.dp)) {
                        Icon(
                            if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            if (isFullscreen) "退出全屏" else "进入全屏",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }

        // ===== 中间播放按钮组 =====
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipPrevious, "上一条", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            // 大播放/暂停按钮：半透明深色底 + 圆角 + 边框光 + 图标 Crossfade 切换
            Surface(
                onClick = onTogglePlay,
                modifier = Modifier.size(68.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.animation.Crossfade(
                        targetState = isPlaying,
                        animationSpec = tween(180),
                        label = "playPause",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "播/暂停",
                            tint = Color.White.copy(alpha = 0.98f),
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.SkipNext, "下一条", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
            }
        }

        // ===== 底部渐变遮罩 + 进度控制 =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                )
                .padding(horizontal = 16.dp)
                .padding(top = 28.dp, bottom = 14.dp),
        ) {
            // 自定义细进度条
            SeekBar(
                progress = progress,
                onSeek = { ratio ->
                    if (duration > 0) seekPosition = (ratio * duration).toLong()
                },
                onSeekFinished = {
                    if (duration > 0) {
                        val final = seekPosition ?: position
                        seekPosition = null
                        onSeekRequested(final)
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左：当前时间
                Text(
                    formatDuration(displayPos),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                )
                // 中：倍速按钮（带圆角底）
                Box {
                Surface(
                    onClick = { speedMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "${if (currentSpeed % 1f == 0f) currentSpeed.toInt() else currentSpeed}x",
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                    DropdownMenu(expanded = speedMenuExpanded, onDismissRequest = { speedMenuExpanded = false }) {
                        speedOptions.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${if (s % 1f == 0f) s.toInt() else s}x${if (s == currentSpeed) "  ✓" else ""}") },
                                onClick = { onSpeedSelected(s); speedMenuExpanded = false },
                            )
                        }
                    }
                }
                // 右：总时长
                Text(
                    formatDuration(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                )
            }
        }
    }
}

/**
 * 自定义细进度条 Composable。
 *
 * 视觉：4dp 高圆角轨道，已播部分白色，未播部分半透明白；
 * thumb 为 14dp 白色圆点，拖动时显现，松手后隐藏。
 */
@Composable
private fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val trackWidthPx = remember { mutableStateOf(0) }

    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { trackWidthPx.value = it.width }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; onSeekFinished() },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val w = size.width.toFloat()
                    if (w > 0) {
                        val ratio = (change.position.x / w).coerceIn(0f, 1f)
                        onSeek(ratio)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道背景
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.22f)),
        )
        // 已播部分
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White),
        )
        // thumb（拖动时显示）——用 dp 像素偏移定位
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val thumbOffsetDp = with(density) {
                (trackWidthPx.value * progress).toDp() - 7.dp
            }
            Box(
                Modifier
                    .offset(x = thumbOffsetDp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
