@file:OptIn(ExperimentalFoundationApi::class)

package com.example.webdavplayer.ui.browse

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.paging.compose.itemKey
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.webdavplayer.data.remote.WebDavPath
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.ui.common.EmptyView
import com.example.webdavplayer.ui.common.LoadingView
import com.example.webdavplayer.ui.player.PlayerViewModel
import com.example.webdavplayer.ui.theme.Spacing
import com.example.webdavplayer.ui.playlist.PlaylistViewModel
import kotlinx.coroutines.flow.first
import okio.source
import java.net.URLDecoder
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    navController: NavHostController,
    playerVm: PlayerViewModel,
    playlistVm: PlaylistViewModel,
    backStackEntry: NavBackStackEntry,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val path by viewModel.path.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val videosAdded by viewModel.videosAdded.collectAsStateWithLifecycle()

    val lazyItems = viewModel.directoryFlow.collectAsLazyPagingItems()

    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver

    var fileAction by remember { mutableStateOf<RemoteFile?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var moveText by remember { mutableStateOf("") }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "upload.bin"
            val size = contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) c.getLong(idx) else null
            }
            val input = contentResolver.openInputStream(uri)
            if (input != null) {
                val source = input.source()
                viewModel.upload(path, fileName, source, size)
            }
        }
    }

    val rawPath = backStackEntry.arguments?.getString("path")
    val initialPath = rawPath?.let { URLDecoder.decode(it, "UTF-8") } ?: "/"

    LaunchedEffect(Unit) {
        viewModel.loadDirectory(initialPath)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar("错误：$it")
            viewModel.consumeError()
        }
    }
    LaunchedEffect(videosAdded) {
        videosAdded?.let { count ->
            if (count > 0) {
                snackbarHostState.showSnackbar("已将 $count 个视频加入播放列表")
                navController.navigate("playlist")
            } else {
                snackbarHostState.showSnackbar("该目录未找到视频文件")
            }
            viewModel.consumeVideosAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (path == "/") {
                            navController.navigate("servers") { popUpTo("servers") { inclusive = true } }
                        } else {
                            val parent = WebDavPath.parentOf(path)
                            navController.navigate(
                                "browse/${viewModel.serverId}?path=" +
                                    URLEncoder.encode(parent, "UTF-8"),
                            ) {
                                popUpTo("browse/${viewModel.serverId}") { inclusive = true }
                            }
                        }
                    }) { Icon(Icons.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { uploadLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.Upload, "上传")
                    }
                    IconButton(onClick = { navController.navigate("playlist") }) {
                        Icon(Icons.Filled.QueueMusic, "播放列表")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { uploadLauncher.launch("*/*") }) {
                Icon(Icons.Filled.Upload, "上传")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                isLoading && lazyItems.itemCount == 0 -> LoadingView()
                !isLoading && lazyItems.itemCount == 0 -> EmptyView("此目录为空")
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        items(
                            count = lazyItems.itemCount,
                            key = lazyItems.itemKey { it.id },
                        ) { index ->
                            val file = lazyItems[index]
                            if (file != null) {
                                FileRow(
                                    file = file,
                                    modifier = Modifier.animateItemPlacement(),
                                    onClick = {
                                        if (file.isDirectory) {
                                            val child = viewModel.fullPath(file.name)
                                            navController.navigate(
                                                "browse/${viewModel.serverId}?path=" +
                                                    URLEncoder.encode(child, "UTF-8"),
                                            )
                                        } else {
                                            viewModel.playFile(file)
                                            navController.navigate("player")
                                        }
                                    },
                                    onLongClick = {
                                        if (file.isDirectory) {
                                            viewModel.onDirLongClick(file)
                                        } else {
                                            fileAction = file
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 文件操作对话框（重命名 / 移动 / 删除）—— 单一 fileAction?.let 块，避免双重渲染
    fileAction?.let { file ->
        when {
            showRename -> {
                AlertDialog(
                    onDismissRequest = { showRename = false; fileAction = null },
                    title = { Text("重命名") },
                    text = {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("新名称") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.rename(viewModel.fullPath(file.name), renameText)
                            showRename = false
                            fileAction = null
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRename = false; fileAction = null }) { Text("取消") }
                    },
                )
            }
            showMove -> {
                AlertDialog(
                    onDismissRequest = { showMove = false; fileAction = null },
                    title = { Text("移动到") },
                    text = {
                        OutlinedTextField(
                            value = moveText,
                            onValueChange = { moveText = it },
                            label = { Text("目标目录路径") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.move(viewModel.fullPath(file.name), moveText)
                            showMove = false
                            fileAction = null
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMove = false; fileAction = null }) { Text("取消") }
                    },
                )
            }
            else -> {
                AlertDialog(
                    onDismissRequest = { fileAction = null },
                    title = { Text(file.name) },
                    text = { Text("选择操作") },
                    confirmButton = {
                        TextButton(onClick = {
                            renameText = file.name
                            showRename = true
                        }) { Text("重命名") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                moveText = file.parentPath
                                showMove = true
                            }) { Text("移动") }
                            TextButton(onClick = {
                                viewModel.delete(viewModel.fullPath(file.name))
                                fileAction = null
                            }) { Text("删除") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: RemoteFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = {
            val sub = if (file.isDirectory) {
                "目录"
            } else {
                "${file.mediaType} · ${file.size} 字节"
            }
            Text(sub)
        },
        leadingContent = {
            Icon(
                if (file.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                contentDescription = null,
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}
