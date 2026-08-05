@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.TrustedCert
import com.example.webdavplayer.ui.common.MediaCard
import com.example.webdavplayer.ui.common.SectionCard
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.domain.common.FileFormatter
import com.example.webdavplayer.domain.model.CachedMedia
import com.example.webdavplayer.ui.player.PlayerViewModel
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import com.example.webdavplayer.ui.common.SectionHeader
import com.example.webdavplayer.ui.common.engineLabel
import com.example.webdavplayer.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    playerVm: PlayerViewModel,
    playlistVm: PlaylistViewModel,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val engineType by viewModel.engineType.collectAsStateWithLifecycle()
    val certs by viewModel.certs.collectAsStateWithLifecycle()
    val cached by viewModel.cachedMedia.collectAsStateWithLifecycle()
    val downloadDir by viewModel.downloadDir.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard(title = "播放内核") {
                Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    EngineType.values().forEach { t ->
                        FilterChip(
                            selected = engineType == t,
                            onClick = { playerVm.switchEngine(t) },
                            label = { Text(engineLabel(t)) },
                            enabled = t != EngineType.VLC,
                        )
                    }
                }
            }

            SectionHeader("下载")

            // 下载管理入口
            Row(
                Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("下载管理", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { navController.navigate("downloads") }) { Text("查看已下载文件") }
            }

            // 默认下载目录
            var showDirDialog by remember { mutableStateOf(false) }
            val dirLabel = downloadDir ?: "默认（应用私有缓存目录）"
            Row(
                Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("默认下载目录", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        dirLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { showDirDialog = true }) { Text("修改") }
            }

            if (showDirDialog) {
                var dirText by remember { mutableStateOf(downloadDir ?: "") }
                AlertDialog(
                    onDismissRequest = { showDirDialog = false },
                    title = { Text("默认下载目录") },
                    text = {
                        Column {
                            Text(
                                "输入绝对路径。留空恢复默认（应用私有缓存目录）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = dirText,
                                onValueChange = { dirText = it },
                                label = { Text("绝对路径") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setDownloadDir(dirText.ifBlank { null })
                            showDirDialog = false
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDirDialog = false }) { Text("取消") }
                    },
                )
            }

            SectionHeader("已信任的自签证书")
            if (certs.isEmpty()) {
                Text(
                    "暂无",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(certs, key = { it.id }) { cert ->
                        CertRow(
                            cert = cert,
                            onRemove = { viewModel.removeCert(cert.id) },
                            modifier = Modifier.animateItemPlacement(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CertRow(
    cert: TrustedCert,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaCard(
        onClick = null,
        modifier = modifier,
    ) {
        Column(Modifier.weight(1f)) {
            Text(cert.serverId, style = MaterialTheme.typography.titleSmall)
            Text(
                "颁发者：${cert.issuer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, "移除")
        }
    }
}

@Composable
private fun CachedRow(
    cached: CachedMedia,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaCard(
        onClick = null,
        modifier = modifier,
    ) {
        Column(Modifier.weight(1f)) {
            Text(cached.name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                "${FileFormatter.formatSize(cached.size)} · ${cached.path}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, "删除缓存")
        }
    }
}

// engineLabel 已抽取到 ui.common.Labels
