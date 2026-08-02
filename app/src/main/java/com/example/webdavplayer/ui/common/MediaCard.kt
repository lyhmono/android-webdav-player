@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.webdavplayer.ui.theme.Spacing

/**
 * 设计系统组件库（C 方案：深色磨砂原生风）。
 * - [MediaCard]：统一圆角卡片容器（12dp 圆角 + surface 表面）。
 * - [MediaTopBar]：磨砂风格顶栏（背景渐变 + 半透明表面，模拟 iOS 毛玻璃）。
 * - [SectionCard]：设置/分组用的外框卡片。
 */

/** 统一卡片容器：12dp 圆角 + 2dp 高度感，替代裸 ListItem。 */
@Composable
fun MediaCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(enabled = onClick != null) { onClick?.invoke() }
    }
    Card(
        modifier = modifier.then(clickModifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

/** 设置分组外框卡片：标题 + 圆角表面容器。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

/** 磨砂风格顶栏容器：深色渐变背景（延伸到状态栏后）+ 内容自动避让状态栏。 */
@Composable
fun MediaTopBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to bg.copy(alpha = 0.98f),
                    1f to bg.copy(alpha = 0.85f),
                )
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
