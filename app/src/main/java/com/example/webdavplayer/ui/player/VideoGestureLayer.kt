package com.example.webdavplayer.ui.player

import android.media.AudioManager
import android.view.Window
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import com.example.webdavplayer.ui.common.findActivity
import com.example.webdavplayer.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 视频手势层（C4 / §7）。
 *
 * 视频播放时叠加（横竖屏统一启用，门控在 [com.example.webdavplayer.ui.player.PlayerScreen]）。
 * 三层手势分区：
 * - 左半屏上下拖拽 → 调节亮度（[Window.LayoutParams.screenBrightness]）；
 * - 右半屏上下拖拽 → 调节音量（[AudioManager.STREAM_MUSIC]）；
 * - 底部区域左右拖拽 → 快进/快退（[onSeekBy] 增量，最终落到 [com.example.webdavplayer.domain.repository.PlayerRepository.seekTo]）。
 *
 * 使用 `pointerInput` + `detectDragGestures` 捕获手势，避免与 Compose 滚动/系统手势冲突。
 * 本层在手势进行中叠加一个居中的「亮度 / 音量 / 快退」提示 HUD（见下方 [AnimatedVisibility]），
 * 提供即时视觉反馈，松手后自动淡出，不影响手势捕获。
 *
 * 注意：本层**不再**自带 `.fillMaxSize()` 全覆盖；[modifier] 由调用方传入（即视频区 Modifier），
 * 从而只覆盖视频区、不遮挡控制条。
 *
 * @param modifier 视频区 Modifier（由 PlayerScreen 传入，限定本层覆盖区域）。
 * @param isVideo 当前是否为视频（非视频不消费手势，直接返回）。
 * @param onSeekBy 快进/快退增量（毫秒，正数前进/负数后退）。
 * @param onToggleControls 单击视频区时切换控制栏显隐（方案 B：U1 修复）。
 * @param gesturesEnabled 是否启用拖拽手势（亮度/音量/快进退）。控制栏可见时为 false——
 *                        避免与进度条 Slider 争抢拖拽事件；点击切换控制栏始终保留。
 */
@Composable
fun VideoGestureLayer(
    modifier: Modifier = Modifier,
    isVideo: Boolean,
    onSeekBy: (deltaMs: Long) -> Unit,
    onToggleControls: (() -> Unit)? = null,
    gesturesEnabled: Boolean = true,
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

    // 使用 rememberUpdatedState，确保手势闭包始终读取最新的回调（pointerInput(Unit) 不会重订阅）。
    val onSeekByState = rememberUpdatedState(onSeekBy)
    val onToggleControlsState = rememberUpdatedState(onToggleControls)

    // 单次拖拽的分区（在 onDragStart 中决定，整段拖拽保持不变）。
    var dragZone by remember { mutableIntStateOf(ZONE_NONE) }

    // ===== 手势提示 HUD 状态 =====
    var hudVisible by remember { mutableStateOf(false) }
    var hudIcon by remember { mutableStateOf<ImageVector>(Icons.Filled.BrightnessMedium) }
    var hudText by remember { mutableStateOf("") }
    var hudProgress by remember { mutableFloatStateOf(-1f) }
    // #16：用「最近手势时间戳」替代 token++——常驻轮询避免每帧重启 LaunchedEffect
    var hudLastAt by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        // 常驻守护协程，240ms 一次检查：超过 HUD_AUTO_HIDE_MS 无手势就淡出
        while (true) {
            delay(HUD_CHECK_INTERVAL_MS)
            if (hudVisible && System.currentTimeMillis() - hudLastAt > HUD_AUTO_HIDE_MS) {
                hudVisible = false
            }
        }
    }

    Box(
        modifier = modifier.then(
            // 控制栏可见时整个手势层不拦截任何事件——让触摸直接冒泡到 PlayerControls 的 Slider/按钮
            if (!gesturesEnabled) {
                Modifier
            } else {
                Modifier
                    .pointerInput(onToggleControls) {
                        if (onToggleControlsState.value == null) return@pointerInput
                        detectTapGestures(
                            onTap = { onToggleControlsState.value?.invoke() },
                        )
                    }
                    .pointerInput(gesturesEnabled) {
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
                                        hudLastAt = System.currentTimeMillis()
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
                                            hudLastAt = System.currentTimeMillis()
                                        }
                                    }
                                    ZONE_SEEK -> {
                                        // #7：满屏拖动 = 90 秒增量，不再随媒体时长线性放大（B 站/YouTube 同款）
                                        val msPerPx = SEEK_FULL_SCREEN_MS / size.width.toFloat()
                                        val deltaMs = (dragAmount.x * msPerPx).toLong()
                                        onSeekByState.value(deltaMs)
                                        hudIcon = if (dragAmount.x >= 0f) Icons.Filled.FastForward else Icons.Filled.Replay
                                        hudText = formatHudTime(deltaMs)
                                        hudProgress = -1f
                                        hudVisible = true
                                        hudLastAt = System.currentTimeMillis()
                                    }
                                    else -> Unit
                                }
                            },
                        )
                    }
            },
        ),
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

private const val ZONE_NONE = -1
private const val ZONE_BRIGHTNESS = 0
private const val ZONE_VOLUME = 1
private const val ZONE_SEEK = 2

/** 一次满屏高度拖拽对应的最大亮度变化量。 */
private const val BRIGHTNESS_STEP = 0.5f

/** #7：底部横向拖拽满屏对应的 seek 增量（毫秒）= 90 秒（B 站/YouTube 同款手感）。 */
private const val SEEK_FULL_SCREEN_MS = 90_000L

/** HUD 松手后自动隐藏的延迟（毫秒）。 */
private const val HUD_AUTO_HIDE_MS = 800L

/** HUD 守护协程检查间隔：240ms，足够及时隐藏又不必高频轮询。 */
private const val HUD_CHECK_INTERVAL_MS = 240L

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
