@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.ui.common.EmptyView
import com.example.webdavplayer.ui.common.MediaCard
import com.example.webdavplayer.ui.common.MediaTopBar
import com.example.webdavplayer.ui.player.PlayerViewModel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ServerListScreen(
    navController: NavHostController,
    playerVm: PlayerViewModel,
    playlistVm: PlaylistViewModel,
    viewModel: ServerListViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val currentId by viewModel.currentServerId.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            MediaTopBar {
                Text(
                    "服务器",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = Spacing.sm),
                )
                IconButton(onClick = { navController.navigate("log") }) {
                    Icon(Icons.Filled.Info, contentDescription = "日志")
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("server_config") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加服务器") },
            )
        },
    ) { padding ->
        if (servers.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { EmptyView("还没有服务器，点击右下角添加", icon = Icons.Filled.CloudOff) }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(servers, key = { it.id }) { server ->
                    val isCurrent = server.id == currentId
                    MediaCard(
                        selected = isCurrent,
                        onClick = {
                            // C5：记住当前服务器，导航按 serverId 隔离（§7）
                            viewModel.selectServer(server.id)
                            navController.navigate("browse/${server.id}")
                        },
                        modifier = Modifier.animateItemPlacement(),
                    ) {
                        // 当前服务器左侧橙色细条
                        if (isCurrent) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        // 圆形首字母头像
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = server.name.ifEmpty { "W" }.first().uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = server.name.ifEmpty { server.baseUrl },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = server.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "当前",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = {
                            navController.navigate("server_config?serverId=${server.id}")
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { toDelete = server.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }

    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除服务器") },
            text = { Text("确定删除该服务器配置吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeServer(toDelete!!)
                        toDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("取消") }
            },
        )
    }
}
