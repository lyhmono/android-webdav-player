# P4 第二轮修复 — DB 迁移 + 引擎空安全 + 状态映射 + 服务启动

> 时间：2026-07-22  
> 分支：fix/p0-p3-bugfix-batch  
> 提交：45257a8  
> CI：Run #74 ✅ success

## 修复清单

### 1. AppDatabase v2→v3 迁移（P0 级 — 数据库崩溃）
**问题**：PR #14 给 `RemoteFileEntity` 加了 `cachedAt` 字段，但 `AppDatabase.version` 仍为 2，且没有 `MIGRATION_2_3`。升级安装时 Room 会抛 `IllegalStateException: A migration from 2 to 3 was required but not found`。

**修复**：
- `AppDatabase.kt`: version 2 → 3
- `DatabaseModule.kt`: 添加 `MIGRATION_2_3`，`ALTER TABLE remote_files ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT <now>`，旧数据回填当前时间戳
- `BrowseRepositoryImpl.kt`: `refreshDirectory` 末尾调用 `remoteFileDao.clearExpired(expireBefore)` 清理 30min 过期缓存

### 2. ExoPlayerEngine 进度轮询空安全（P1 级 — 崩溃）
**问题**：`startProgress()` 中 `player?.currentPosition` 使用安全调用，但如果在 `delay()` 期间引擎被 release，下一轮循环 `player` 变为 null 后仍会继续循环（`while(isActive)` 不检查 player）。

**修复**：每轮循环开始先 `val p = player; if (p == null) break`，同时 `duration` 用 `C.TIME_UNSET` 检查。

### 3. PlayerViewModel ERROR 状态映射（P2 级 — UI 误差）
**问题**：`mapControllerState` 将 `STATE_IDLE` 一律映射为 `PlaybackState.IDLE`，但 Media3 在播放出错时也进入 `STATE_IDLE`（无独立 ERROR 状态）。

**修复**：`STATE_IDLE` 时检查 `c.playerError != null` → 映射为 `PlaybackState.ERROR`。

### 4. MainActivity 移除手动启动 PlaybackService（P0 级 — 崩溃）
**问题**：`onCreate` 中调用 `startForegroundService(Intent(this, PlaybackService::class.java))`，但 `MediaSessionService` 在没有媒体播放时不会调用 `startForeground()`，导致 Android 12+ 抛 `ForegroundServiceDidNotStartInTimeException`。

**修复**：完全移除手动启动逻辑。Media3 通过 `MediaController.Builder` 连接时自动 `bindService`，首次播放时 `MediaSessionService` 自动晋升前台。

## 修改文件
| 文件 | 变更 |
|---|---|
| `AppDatabase.kt` | version 2→3 |
| `DatabaseModule.kt` | +MIGRATION_2_3 |
| `BrowseRepositoryImpl.kt` | +TTL 清理 |
| `ExoPlayerEngine.kt` | 轮询 null 安全 |
| `PlayerViewModel.kt` | ERROR 状态映射 |
| `MainActivity.kt` | 移除手动启动服务 |

## 未修复项（已知，后续处理）
- #8 BrowseScreen UI 优化（文件大小格式化、图标区分）
- #11 ImmediateFuture 错误处理
- #18 DataStore 替换 SharedPreferences
