@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.playlist

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.modeLabel
import com.example.webdavplayer.ui.player.PlayerViewModel
import com.example.webdavplayer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    navController: NavHostController,
    playerVm: PlayerViewModel,
    playlistVm: PlaylistViewModel,
) {
    val items by playlistVm.items.collectAsStateWithLifecycle()
    val mode by playlistVm.mode.collectAsStateWithLifecycle()

    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放列表") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (items.isNotEmpty()) showClearConfirm = true },
                        enabled = items.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.ClearAll, "清空")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader("播放模式")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PlayMode.values().forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { playlistVm.setMode(m) },
                        label = { Text(modeLabel(m)) },
                    )
                }
            }

            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("播放列表为空", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "在浏览页长按目录可加入视频，或点击文件播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                var dragFrom by remember { mutableStateOf<Int?>(null) }
                var dragAccumulator by remember { mutableStateOf(0f) }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        PlaylistRow(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement(),
                            onClick = {
                                playlistVm.playItem(item)
                                navController.navigate("player")
                            },
                            onRemove = { playlistVm.removeItem(item.id) },
                            dragModifier = Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragFrom = index
                                        dragAccumulator = 0f
                                    },
                                    onDragEnd = {
                                        val from = dragFrom ?: return@detectDragGestures
                                        val avg = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull()?.size?.toFloat() ?: 80f
                                        val to = (from + (dragAccumulator / avg).roundToInt())
                                            .coerceIn(0, items.lastIndex)
                                        if (to != from) playlistVm.reorder(from, to)
                                        dragFrom = null
                                        dragAccumulator = 0f
                                    },
                                    onDragCancel = {
                                        dragFrom = null
                                        dragAccumulator = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragAccumulator += dragAmount.y
                                    },
                                )
                            },
                        )
                    }
                }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空播放列表") },
            text = { Text("确定要清空当前播放列表吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    playlistVm.clear()
                    showClearConfirm = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PlaylistRow(
    item: PlaylistItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = {
            Text(
                when (item.mediaType) {
                    MediaType.VIDEO -> "视频"
                    MediaType.AUDIO -> "音频"
                    MediaType.OTHER -> "其他"
                },
            )
        },
        leadingContent = {
            Icon(
                if (item.mediaType == MediaType.VIDEO) {
                    Icons.Filled.VideoLibrary
                } else {
                    Icons.Filled.AudioFile
                },
                contentDescription = null,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = "拖拽排序",
                    modifier = dragModifier,
                )
                IconButton(onClick = onClick) {
                    Icon(Icons.Filled.PlayArrow, "播放")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, "移除")
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
    )
}

// modeLabel 已抽取到 ui.common.Labels
