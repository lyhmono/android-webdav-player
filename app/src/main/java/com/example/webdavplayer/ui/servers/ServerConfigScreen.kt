package com.example.webdavplayer.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.domain.model.AuthType
import com.example.webdavplayer.domain.model.ServerConfig
import com.example.webdavplayer.ui.theme.Spacing
import java.util.UUID

private fun AuthType.label(): String = when (this) {
    AuthType.NONE -> "无"
    AuthType.BASIC -> "Basic"
    AuthType.DIGEST -> "Digest"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigScreen(
    navController: NavHostController,
    serverId: String?,
    viewModel: ServerConfigViewModel = hiltViewModel(),
) {
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val certDialog by viewModel.certDialog.collectAsStateWithLifecycle()
    val savedId by viewModel.savedServerId.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(AuthType.BASIC) }
    var trustSelf by remember { mutableStateOf(false) }

    // 编辑模式：加载已有配置填充表单
    LaunchedEffect(serverId) {
        if (serverId != null) {
            viewModel.loadServerForEdit(serverId)?.let { config ->
                name = config.name
                url = config.baseUrl
                username = config.username
                password = config.encryptedPassword
                authType = config.authType
                trustSelf = config.trustSelfSigned
            }
        }
    }

    LaunchedEffect(savedId) {
        if (savedId != null) {
            val id = viewModel.consumeSaved()
            if (id != null) {
                navController.navigate("browse/$id") {
                    popUpTo("servers") { inclusive = false }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (serverId == null) "添加服务器" else "编辑服务器") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称（如：我的 NAS）") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("服务器地址") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(autoCorrect = false),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
            )

            Text("鉴权方式")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AuthType.values().forEach { type ->
                    FilterChip(
                        selected = authType == type,
                        onClick = { authType = type },
                        label = { Text(type.label()) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("信任自签名证书")
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Switch(checked = trustSelf, onCheckedChange = { trustSelf = it })
                }
            }

            if (lastError != null) {
                Text(
                    "连接失败：$lastError",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    val config = ServerConfig(
                        id = serverId ?: UUID.randomUUID().toString(),
                        name = name.ifEmpty { url },
                        baseUrl = url,
                        username = username,
                        encryptedPassword = password,
                        authType = authType,
                        trustSelfSigned = trustSelf,
                        createdAt = System.currentTimeMillis(),
                    )
                    viewModel.attemptConnect(config)
                },
                enabled = !connecting && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (connecting) "连接中…" else "连接并保存")
            }
        }
    }

    certDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCertDialog() },
            title = { Text("无法验证服务器证书") },
            text = {
                Column {
                    Text("该服务器使用了未被信任的证书。请核对以下指纹，确认无误后点击“信任并继续”。")
                    Spacer(Modifier.height(Spacing.sm))
                    Text("SHA-256 指纹", style = MaterialTheme.typography.labelMedium)
                    Text(dialog.fingerprint, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text("颁发者", style = MaterialTheme.typography.labelMedium)
                    Text(dialog.issuer, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.trustAndContinue() }) {
                    Text("信任并继续")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCertDialog() }) {
                    Text("拒绝")
                }
            },
        )
    }
}
