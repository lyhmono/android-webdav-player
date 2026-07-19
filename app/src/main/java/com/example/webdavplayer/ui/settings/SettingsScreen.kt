@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.BuildConfig
import com.example.webdavplayer.domain.model.EngineType
import com.example.webdavplayer.domain.model.TrustedCert
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
    val isVlcAvailable = BuildConfig.FLAVOR == "full"

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
            SectionHeader("播放内核")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                EngineType.values().forEach { t ->
                    FilterChip(
                        selected = engineType == t,
                        onClick = { playerVm.switchEngine(t) },
                        label = { Text(engineLabel(t)) },
                        enabled = if (t == EngineType.VLC) isVlcAvailable else true,
                    )
                }
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
    ListItem(
        headlineContent = { Text(cert.serverId) },
        supportingContent = { Text("颁发者：${cert.issuer}") },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, "移除")
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

// engineLabel 已抽取到 ui.common.Labels
