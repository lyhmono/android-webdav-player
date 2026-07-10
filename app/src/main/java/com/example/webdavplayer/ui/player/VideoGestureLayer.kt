package com.example.webdavplayer.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

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
 * 本层为透明覆盖，不绘制任何 UI。
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
                                }
                            }
                            ZONE_SEEK -> {
                                val dur = durationMsState.value
                                val msPerPx = if (dur > 0) {
                                    dur.toFloat() / size.width.toFloat()
                                } else {
                                    0f
                                }
                                onSeekByState.value((dragAmount.x * msPerPx).toLong())
                            }
                            else -> Unit
                        }
                    },
                )
            },
    )
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
