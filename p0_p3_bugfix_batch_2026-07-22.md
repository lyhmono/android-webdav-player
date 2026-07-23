# P0-P3 批量修复报告

> 时间: 2026-07-22 21:38+ (Asia/Shanghai)
> 分支: fix/p0-p3-bugfix-batch
> PR: https://github.com/lyhmono/android-webdav-player/pull/14
> 改动: 9 files, +144/-56 lines

## 修复清单

### P0 严重（2 项）

| # | 文件 | 问题 | 修复方案 |
|---|------|------|----------|
| 2 | MainActivity.kt | 无条件 startForegroundService，Android 12+ 可能抛 ForegroundServiceStartNotAllowedException | 添加 SDK 版本检查 + 注释明确延迟前台晋升策略 |
| 3 | EngineMedia3Adapter.kt | ERROR → STATE_IDLE，通知栏无法重试 | 改为 STATE_ERROR |

### P1-P2 中等（8 项）

| # | 文件 | 问题 | 修复方案 |
|---|------|------|----------|
| 1 | PlaybackService.kt | onTaskRemoved 未先暂停就 stopSelf | 已在上一个 commit 修复 |
| 10 | PlaybackService.kt | onError 方法体为空 | 添加 Log.e 记录错误堆栈 |
| 13 | EngineMedia3Adapter.kt | @Volatile 多变量非原子读取 | 添加 StateSnapshot + stateLock synchronized |
| 5 | PlaybackService.kt | serviceScope 用 Dispatchers.Main | 改为 Dispatchers.IO |
| 6 | PlaybackProgressSaver.kt | onProgress 可能每秒多次触发写 DB | persist 用 tryLock 防积压 |
| 14 | PlaylistControllerImpl.kt | current() 并发修改不安全 | 所有读写加 synchronized(stateLock) |
| 19 | SardineWebDavClient.kt | WebDAV 操作缺少重试 | 添加 retryIO（3次指数退避） |
| 20 | RemoteFileDao.kt | Room 缓存缺少 TTL | 添加 cachedAt 字段 + clearExpired 查询 |

### P3 轻微（2 项）

| # | 文件 | 问题 | 修复方案 |
|---|------|------|----------|
| 9 | ServerConfigStore.kt | 多线程并发读写风险 | loadAll/saveAll 加 synchronized(writeLock) |
| 12 | PlaybackService.kt | 通知渠道 ID 硬编码 | 提取为 companion object CHANNEL_ID |

### 未修复（需单独 PR 的较大重构）

| # | 说明 |
|---|------|
| 8 | BrowseScreen.kt 22KB 拆分 — UI 重构 |
| 11 | ImmediateFuture 替换为 Guava — 依赖变更 |
| 15 | ProGuard keep 规则 — 需要实际打包验证 |
| 18 | SharedPreferences → DataStore — 大范围迁移 |

## 关键代码变更

### EngineMedia3Adapter.kt — mapState 修复
```kotlin
// 修复前
PlaybackState.ERROR -> Player.STATE_IDLE  // 通知栏无法感知错误

// 修复后
PlaybackState.ERROR -> Player.STATE_ERROR  // 通知栏显示错误，允许用户重试
```

### EngineMedia3Adapter.kt — 原子状态快照
```kotlin
private data class StateSnapshot(
    val state: PlaybackState = PlaybackState.IDLE,
    val position: Long = 0L,
    val duration: Long = 0L,
)
private val stateLock = Any()
@Volatile private var snapshot = StateSnapshot()

fun onEngineState(state: PlaybackState) {
    synchronized(stateLock) { snapshot = snapshot.copy(state = state) }
    engineState = state
    invalidateState()
}
```

### PlaylistControllerImpl.kt — 并发安全
```kotlin
private val stateLock = Any()

override fun current(): PlaylistItem? = synchronized(stateLock) {
    items.getOrNull(currentIndex)
}
override fun next(): PlaylistItem? = synchronized(stateLock) {
    if (items.isEmpty()) return null
    val nextIndex = computeNext()
    if (nextIndex < 0) return null
    currentIndex = nextIndex
    items[currentIndex]
}
```

### SardineWebDavClient.kt — 重试机制
```kotlin
private fun <T> retryIO(maxRetries: Int = 3, initialDelayMs: Long = 500L, block: () -> T): T {
    var lastError: Throwable? = null
    repeat(maxRetries) { attempt ->
        try { return block() }
        catch (e: java.io.IOException) {
            lastError = e
            if (attempt < maxRetries - 1) Thread.sleep(initialDelayMs * (1 shl attempt))
        }
    }
    throw lastError ?: java.io.IOException("retryIO: unknown failure")
}
```

### PlaybackProgressSaver.kt — tryLock 防积压
```kotlin
private fun persist(...) {
    scope.launch {
        if (saveMutex.tryLock()) {
            try { repository.save(...) }
            finally { saveMutex.unlock() }
        }
    }
}
```

### RemoteFileEntity.kt — TTL 字段
```kotlin
val cachedAt: Long = System.currentTimeMillis()
```

### RemoteFileDao.kt — TTL 清理
```kotlin
@Query("DELETE FROM remote_files WHERE cachedAt < :expireBefore")
suspend fun clearExpired(expireBefore: Long)
```

## 提交记录

- `12a3a2a` — fix: PlaylistController currentIndex 污染 + PlaybackService onTaskRemoved 暂停逻辑
- `25c0216` — fix: 修复 P0-P3 共 14 项问题

## PR 链接

- PR #13 (上一 commit): https://github.com/lyhmono/android-webdav-player/pull/13
- PR #14 (本批次): https://github.com/lyhmono/android-webdav-player/pull/14
