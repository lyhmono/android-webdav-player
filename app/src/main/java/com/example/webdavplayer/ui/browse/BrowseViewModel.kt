package com.example.webdavplayer.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.example.webdavplayer.common.Result
import com.example.webdavplayer.data.remote.WebDavPath
import com.example.webdavplayer.domain.model.PlaylistItem
import com.example.webdavplayer.domain.model.RemoteFile
import com.example.webdavplayer.domain.repository.CacheRepository
import com.example.webdavplayer.domain.usecase.AddDirVideosToPlaylistUseCase
import com.example.webdavplayer.domain.usecase.BrowseDirectoryUseCase
import com.example.webdavplayer.domain.usecase.PlayMediaUseCase
import com.example.webdavplayer.domain.usecase.RenameMoveDeleteUseCase
import com.example.webdavplayer.domain.usecase.UploadFileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import okio.Source
import javax.inject.Inject

/**
 * 浏览页 ViewModel（§6 T06 / T07）。
 * 目录分页流来自 Room；刷新/增删改走对应 UseCase。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val browseUseCase: BrowseDirectoryUseCase,
    private val addVideos: AddDirVideosToPlaylistUseCase,
    private val uploadUseCase: UploadFileUseCase,
    private val fileOps: RenameMoveDeleteUseCase,
    private val playMedia: PlayMediaUseCase,
    private val cacheRepository: CacheRepository,
) : ViewModel() {

    val serverId: String = savedStateHandle.get<String>("serverId") ?: ""

    private val _path = MutableStateFlow("/")
    val path: StateFlow<String> = _path.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _videosAdded = MutableStateFlow<Int?>(null)
    val videosAdded: StateFlow<Int?> = _videosAdded.asStateFlow()

    /** 目录缓存最后刷新时间戳（毫秒），供 UI 显示“更新于 Xs 前”（§1.3 优化）。 */
    private val _lastRefreshedAt = MutableStateFlow<Long?>(null)
    val lastRefreshedAt: StateFlow<Long?> = _lastRefreshedAt.asStateFlow()

    /** 当前目录分页流（来自 Room 缓存，§1.3）。 */
    val directoryFlow: Flow<PagingData<RemoteFile>> =
        _path.flatMapLatest { p -> browseUseCase(serverId, p) }

    fun loadDirectory(p: String) {
        _path.value = WebDavPath.normalize(p)
        refreshStale(_path.value)
    }

    /** 缓存优先刷新（§1.3 优化）：秒显 Room 缓存，仅超龄/为空才打 PROPFIND。 */
    private fun refreshStale(p: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            when (val r = browseUseCase.refreshIfStale(serverId, p)) {
                is Result.Success -> { /* 缓存已更新 */ }
                is Result.Error -> _error.value = r.throwable.message ?: "加载失败"
            }
            // 无论是否触发了网络刷新，都同步一次“最后刷新时间”（秒显缓存时也应有值）。
            _lastRefreshedAt.value = browseUseCase.lastRefreshedAt(serverId, p)
            _isLoading.value = false
        }
    }

    /** 计算某文件相对服务器的完整路径（供播放/操作使用）。 */
    fun fullPath(name: String): String {
        val p = _path.value
        return if (p == "/") "/$name" else "$p/$name"
    }

    /** 长按目录：识别其中视频并加入播放列表（§1.5）。 */
    fun onDirLongClick(dir: RemoteFile) {
        viewModelScope.launch {
            when (val r = addVideos(serverId, dir.parentPath, replace = false)) {
                is Result.Success -> _videosAdded.value = r.data.size
                is Result.Error -> _error.value = r.throwable.message ?: "添加失败"
            }
        }
    }

    fun upload(parentPath: String, fileName: String, source: Source, size: Long?) {
        viewModelScope.launch {
            when (val r = uploadUseCase(serverId, parentPath, fileName, source, size)) {
                is Result.Success -> _message.value = "上传成功"
                is Result.Error -> _error.value = r.throwable.message ?: "上传失败"
            }
        }
    }

    fun rename(fromPath: String, toName: String) {
        viewModelScope.launch {
            when (val r = fileOps.rename(serverId, fromPath, toName)) {
                is Result.Success -> _message.value = "重命名成功"
                is Result.Error -> _error.value = r.throwable.message ?: "重命名失败"
            }
        }
    }

    fun move(fromPath: String, toPath: String) {
        viewModelScope.launch {
            when (val r = fileOps.move(serverId, fromPath, toPath)) {
                is Result.Success -> _message.value = "移动成功"
                is Result.Error -> _error.value = r.throwable.message ?: "移动失败"
            }
        }
    }

    fun delete(path: String) {
        viewModelScope.launch {
            when (val r = fileOps.delete(serverId, path)) {
                is Result.Success -> _message.value = "已删除"
                is Result.Error -> _error.value = r.throwable.message ?: "删除失败"
            }
        }
    }

    /** 直接点击媒体文件：播放。 */
    fun playFile(file: RemoteFile) {
        val item = PlaylistItem(
            id = "${file.serverId}:${fullPath(file.name)}",
            serverId = file.serverId,
            path = fullPath(file.name),
            name = file.name,
            mediaType = file.mediaType,
            durationMs = 0L,
            addedAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val r = playMedia(item)
            when (r) {
                is Result.Success -> { /* 跳转由界面处理 */ }
                is Result.Error -> _error.value = "播放失败：${r.throwable.message}"
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeError() {
        _error.value = null
    }

    fun consumeVideosAdded() {
        _videosAdded.value = null
    }

    /** 下载当前文件到本地离线缓存（P2）。 */
    fun downloadFile(path: String) {
        viewModelScope.launch {
            when (val r = cacheRepository.download(serverId, path)) {
                is Result.Success -> _message.value = "已下载到本地：${r.data.name}"
                is Result.Error -> _error.value = r.throwable.message ?: "下载失败"
            }
        }
    }
}
