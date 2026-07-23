# P4 第三轮修复 — StateSnapshot 原子化 + 节流锁 + retryIO 协程化

> 时间：2026-07-22 22:30  
> 分支：fix/p0-p3-bugfix-batch  
> 提交：6297232  
> CI：Run #75 ✅ success

## 修复清单

### 1. EngineMedia3Adapter StateSnapshot 形同虚设（P1 — 数据竞争）
**问题**：PR #14 添加了 `StateSnapshot` + `stateLock` + `synchronized`，但 `getState()` 和 `currentPositionMs` 仍读取旧的 `@Volatile engineState/positionMs/durationMs`，StateSnapshot 完全没被使用。三个 `@Volatile` 字段各自独立读取，无法保证一致性快照。

**修复**：
- 移除 `engineState`、`positionMs`、`durationMs` 三个 `@Volatile` 字段
- `getState()` 从 `synchronized(stateLock) { snapshot }` 读取所有字段
- `currentPositionMs` 同理
- `onEngineState`/`onEngineProgress` 只更新 `snapshot` 后 `invalidateState()`

### 2. PlaybackProgressSaver.onProgress 非原子读改写（P2 — 竞态条件）
**问题**：`onProgress` 中先读 `lastSavedAt`（@Volatile），判断后写 `lastSavedAt = now`——两个线程可能同时通过 `if (now - lastSavedAt >= saveIntervalMs)` 判断，导致重复写库。

**修复**：用 `saveMutex.tryLock()` 保护 `lastSavedAt` 的读-改-写，释放锁后再调 `persist`。

### 3. SardineWebDavClient.retryIO Thread.sleep 阻塞（P2 — 线程池占用）
**问题**：`retryIO` 是普通函数但在 `withContext(Dispatchers.IO)` 中调用，`Thread.sleep` 会阻塞 IO 线程池中的一个线程，退避期间无法服务其他协程。

**修复**：改为 `suspend fun`，`Thread.sleep` → `kotlinx.coroutines.delay`。

### 4. RemoteFileDao 重复方法（P3 — 代码冗余）
**问题**：`clearExpired` 和 `clearAllExpired` 是完全相同的 SQL 查询。

**修复**：移除 `clearAllExpired`，保留 `clearExpired`。

## 修改文件
| 文件 | 变更 |
|---|---|
| `EngineMedia3Adapter.kt` | 移除 3 个 @Volatile 字段，getState/currentPositionMs 统一读 snapshot |
| `PlaybackProgressSaver.kt` | onProgress 用 tryLock 保护 lastSavedAt 原子性 |
| `SardineWebDavClient.kt` | retryIO 改为 suspend，Thread.sleep → delay |
| `RemoteFileDao.kt` | 移除重复的 clearAllExpired |

## 累计修复进度
- P0-P4 共 20 项修复，CI Run #75 ✅ 全绿
- 剩余非崩溃优化项：#8 BrowseScreen UI、#18 DataStore 替换 SharedPreferences
