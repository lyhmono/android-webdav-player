package com.example.webdavplayer.ui.log

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.webdavplayer.data.log.AppLogger
import com.example.webdavplayer.ui.theme.Spacing

/**
 * 应用内日志查看页（任务 B）。
 *
 * 顶部操作：清除（清空缓冲区）、复制（导出文本到剪贴板）。
 * 主体 LazyColumn 按级别着色展示每条日志：E 红 / W 橙 / I 默认 / D 灰 / V 更灰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    navController: NavHostController,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun copyLogs() {
        try {
            val text = viewModel.exportText()
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("webdav-logs", text))
            Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "清除")
                    }
                    IconButton(onClick = { copyLogs() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
        ) {
            items(logs) { entry ->
                val color = when (entry.level) {
                    "E" -> MaterialTheme.colorScheme.error
                    "W" -> Color(0xFFFF9800) // 橙
                    "D" -> MaterialTheme.colorScheme.onSurfaceVariant
                    "V" -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface // I 默认
                }
                Text(
                    text = "${AppLogger.formatTime(entry.time)} ${entry.level} ${entry.tag}: ${entry.message}",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
            }
            if (logs.isEmpty()) {
                item {
                    Text(
                        "暂无日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
        }
    }
}
