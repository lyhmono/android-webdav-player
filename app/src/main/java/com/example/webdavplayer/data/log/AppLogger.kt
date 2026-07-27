package com.example.webdavplayer.data.log

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 应用内日志聚合器（任务 B：应用内日志查看）。
 *
 * - 全局单例 [object AppLogger]，避免引入 Hilt 注入改动，简单可靠；
 * - 线程安全环形缓冲（[synchronized] 保护的不变量替换），最多保留 [MAX_ENTRIES] 条；
 * - 所有写日志方法内部 try/catch，绝不可因记录日志而崩溃；同时镜像到 Logcat 便于开发期排查。
 */
object AppLogger {

    /** 单条日志记录。 */
    data class LogEntry(
        val time: Long,
        val level: String,
        val tag: String,
        val message: String,
    )

    private const val MAX_ENTRIES = 500

    @Volatile private var entries: ArrayDeque<LogEntry> = ArrayDeque(MAX_ENTRIES)

    @Synchronized
    private fun append(level: String, tag: String, message: String) {
        try {
            val entry = LogEntry(
                time = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
            )
            val next = ArrayDeque<LogEntry>(entries.size + 1)
            next.addAll(entries)
            next.addLast(entry)
            while (next.size > MAX_ENTRIES) next.removeFirst()
            entries = next
            // 镜像到 Logcat，方便开发期 adb logcat 同时查看
            when (level) {
                "V" -> Log.v(tag, message)
                "D" -> Log.d(tag, message)
                "I" -> Log.i(tag, message)
                "W" -> Log.w(tag, message)
                else -> Log.e(tag, message)
            }
        } catch (_: Throwable) {
            // 记录日志绝不允许抛出
        }
    }

    /** Verbose 级别。 */
    fun v(tag: String, message: String) = append("V", tag, message)

    /** Debug 级别。 */
    fun d(tag: String, message: String) = append("D", tag, message)

    /** Info 级别。 */
    fun i(tag: String, message: String) = append("I", tag, message)

    /** Warn 级别。 */
    fun w(tag: String, message: String) = append("W", tag, message)

    /** Error 级别。 */
    fun e(tag: String, message: String) = append("E", tag, message)

    /** 记录异常（含堆栈，截断至 2000 字符避免单条过大）。 */
    fun logException(tag: String, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            val stack = sw.toString().lineSequence().take(40).joinToString("\n").take(2000)
            append("E", tag, "${t.message ?: t.javaClass.simpleName}\n$stack")
        } catch (_: Throwable) {
            // 记录日志绝不允许抛出
        }
    }

    /**
     * 返回全部日志的快照。
     * @return 最新一条在最前（倒序）。便于日志界面“最新在上”展示。
     */
    @Synchronized
    fun snapshot(): List<LogEntry> {
        return try {
            entries.toList().reversed()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** 清空缓冲区。 */
    @Synchronized
    fun clear() {
        try {
            entries = ArrayDeque(MAX_ENTRIES)
        } catch (_: Throwable) {
            // 记录日志绝不允许抛出
        }
    }

    /** 便捷：将时间戳格式化为 HH:mm:ss（供 UI/导出复用）。 */
    fun formatTime(time: Long): String =
        try {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(time))
        } catch (_: Throwable) {
            ""
        }
}
