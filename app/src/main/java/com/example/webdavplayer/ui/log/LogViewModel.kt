package com.example.webdavplayer.ui.log

import androidx.lifecycle.ViewModel
import com.example.webdavplayer.data.log.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 日志查看页 ViewModel（任务 B）。
 *
 * - [logs] 由 [AppLogger.snapshot] 构建，init 时拉取一次，clear 时刷新；
 * - [exportText] 将全部日志拼为纯文本，供“复制”使用。
 */
@HiltViewModel
class LogViewModel @Inject constructor() : ViewModel() {

    private val _logs: MutableStateFlow<List<AppLogger.LogEntry>> =
        MutableStateFlow(AppLogger.snapshot())

    /** 日志列表（最新在前）。 */
    val logs: StateFlow<List<AppLogger.LogEntry>> = _logs.asStateFlow()

    init {
        refresh()
    }

    /** 从 [AppLogger] 重新拉取快照，刷新 [logs]。 */
    fun refresh() {
        try {
            _logs.value = AppLogger.snapshot()
        } catch (_: Throwable) {
            // 不应发生，静默失败
        }
    }

    /** 清空日志并刷新列表。 */
    fun clear() {
        AppLogger.clear()
        refresh()
    }

    /** 导出全部日志为纯文本（每行：`时间 级别 tag: message`）。 */
    fun exportText(): String {
        return try {
            AppLogger.snapshot().joinToString("\n") { entry ->
                val t = AppLogger.formatTime(entry.time)
                "$t ${entry.level} ${entry.tag}: ${entry.message}"
            }
        } catch (_: Throwable) {
            ""
        }
    }
}
