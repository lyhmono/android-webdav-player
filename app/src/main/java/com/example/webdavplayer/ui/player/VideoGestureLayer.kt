package com.example.webdavplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.webdavplayer.ui.theme.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 视频手势层（C4 / §7）。
 *
 * 仅在视频 + 横屏/全屏时叠加（门控在 [com.example.webdavplayer.ui.player.PlayerScreen]）。
 * 三层手势分区：
 * - 左半屏上下拖拽 → 调节亮度（[Window.LayoutParams.screenBrightness]）；
 * - 右半屏上下拖拽 → 调节音量（[AudioManager.STREAM_MUSIC]）；
 * - 底部区域左右拖拽 → 快进/快退（[onSeekBy] 增量，最终落到 [com.example.webdavplayer.domain.repository.PlayerRepository.seekTo]）。
 *
 * 使用 `pointerInput` + `detectDragGestures` 捕获手势，避免与 Compose 滚动/系统手势冲突。
 * 本层在手势进行中叠加一个居中的「亮度 / 音量 / 快退」提示 HUD（见下方 [AnimatedVisibility]），
 * 提供即时视觉反馈，松手后自动淡出，不影响手势捕获。
 *
 * @param isVideo 当前是否为视频（非视频不消费手势，直接返回）。
 * @param durationMs 当前媒体总时长（用于把横向位移换算成毫秒增量）。
 * @param onSeekBy 快进/快退增量（毫秒，正数前进/负数后退）。
 */
@Composable
fun VideoGestureLayer(
    modifier: Modifier = Modifier,
    isVideo: Boolean,
    durationMs: Long,
    onSeekBy: (deltaMs: Long) -> Unit,
) {
    if (!isVideo) return

    val context = LocalContext.current
    val activity = context.findActivity()
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val window: Window? = activity?.window

    // 亮度初始值（系统默认 -1 时视作 0.5）。
    var brightness by remember(window) {
        mutableFloatStateOf(
            window?.attributes?.screenBrightness
                ?.takeIf { it >= 0f } ?: 0.5f,
        )
    }
    // 音量初始值。
    val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
    var volume by remember(audioManager) {
        mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }

    // 使用 rememberUpdatedState，确保手势闭包始终读取最新的时长与回调（pointerInput(Unit) 不会重订阅）。
    val durationMsState = rememberUpdatedState(durationMs)
    val onSeekByState = rememberUpdatedState(onSeekBy)

    // 单次拖拽的分区（在 onDragStart 中决定，整段拖拽保持不变）。
    var dragZone by remember { mutableIntStateOf(ZONE_NONE) }

    // ===== 手势提示 HUD 状态 =====
    var hudVisible by remember { mutableStateOf(false) }
    var hudIcon by remember { mutableStateOf<ImageVector>(Icons.Filled.BrightnessMedium) }
    var hudText by remember { mutableStateOf("") }
    var hudProgress by remember { mutableFloatStateOf(-1f) }
    var hudHideToken by remember { mutableIntStateOf(0) }

    // 每次手势变动都会使 hudHideToken 自增，从而重置「松手后淡出」计时。
    LaunchedEffect(hudHideToken) {
        if (hudHideToken > 0) {
            delay(HUD_AUTO_HIDE_MS)
            hudVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start: Offset ->
                        dragZone = when {
                            start.y > size.height * 0.66f -> ZONE_SEEK
                            start.x < size.width / 2f -> ZONE_BRIGHTNESS
                            else -> ZONE_VOLUME
                        }
                    },
                    onDrag = { _, dragAmount ->
                        when (dragZone) {
                            ZONE_BRIGHTNESS -> {
                                val delta = -dragAmount.y / size.height.toFloat() * BRIGHTNESS_STEP
                                brightness = (brightness + delta).coerceIn(0f, 1f)
                                val attrs = window?.attributes
                                if (attrs != null) {
                                    attrs.screenBrightness = brightness
                                    window.attributes = attrs
                                }
                                hudIcon = Icons.Filled.BrightnessMedium
                                hudText = "${(brightness * 100).toInt()}%"
                                hudProgress = brightness
                                hudVisible = true
                                hudHideToken++
                            }
                            ZONE_VOLUME -> {
                                if (maxVolume > 0) {
                                    val deltaSteps =
                                        (-dragAmount.y / size.height.toFloat() * maxVolume).roundToInt()
                                    volume = (volume + deltaSteps).coerceIn(0, maxVolume)
                                    audioManager?.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        volume,
                                        0,
                                    )
                                    hudIcon = if (volume == 0) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp
                                    hudText = "$volume"
                                    hudProgress = volume.toFloat() / maxVolume
                                    hudVisible = true
                                    hudHideToken++
                                }
                            }
                            ZONE_SEEK -> {
                                val dur = durationMsState.value
                                val msPerPx = if (dur > 0) {
                                    dur.toFloat() / size.width.toFloat()
                                } else {
                                    0f
                                }
                                val deltaMs = (dragAmount.x * msPerPx).toLong()
                                onSeekByState.value(deltaMs)
                                hudIcon = if (dragAmount.x >= 0f) Icons.Filled.FastForward else Icons.Filled.Replay
                                hudText = formatHudTime(deltaMs)
                                hudProgress = -1f
                                hudVisible = true
                                hudHideToken++
                            }
                            else -> Unit
                        }
                    },
                )
            },
    ) {
        // 手势提示 HUD：居中、半透明卡片，亮度/音量附带进度条，快退仅显示时间增量。
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(animationSpec = tween(HUD_FADE_IN_MS)),
            exit = fadeOut(animationSpec = tween(HUD_FADE_OUT_MS)),
            modifier = Modifier
                .align(Alignment.Center)
                .sizeIn(minWidth = 96.dp, minHeight = 96.dp),
        ) {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    Modifier.padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(imageVector = hudIcon, contentDescription = null)
                    Text(hudText, style = MaterialTheme.typography.labelLarge)
                    if (hudProgress >= 0f) {
                        LinearProgressIndicator(
                            progress = hudProgress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** 从 Context 向上查找 ComponentActivity（用于获取 Window 调节亮度）。 */
private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val ZONE_NONE = -1
private const val ZONE_BRIGHTNESS = 0
private const val ZONE_VOLUME = 1
private const val ZONE_SEEK = 2

/** 一次满屏高度拖拽对应的最大亮度变化量。 */
private const val BRIGHTNESS_STEP = 0.5f

/** HUD 松手后自动隐藏的延迟（毫秒）。 */
private const val HUD_AUTO_HIDE_MS = 800L

/** HUD 淡入时长（毫秒）。 */
private const val HUD_FADE_IN_MS = 150

/** HUD 淡出时长（毫秒）。 */
private const val HUD_FADE_OUT_MS = 400

/** 将毫秒增量格式化为带正负号的 mm:ss（用于快进/快退提示）。 */
private fun formatHudTime(ms: Long): String {
    val totalSec = kotlin.math.abs(ms) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val sign = if (ms >= 0) "+" else "-"
    return "$sign%02d:%02d".format(m, s)
}
