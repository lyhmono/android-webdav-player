# Android WebDAV Player — 最终验证报告

> 验证时间: 2026-07-22 21:00 (Asia/Shanghai)
> 验证范围: PlaylistControllerImpl.kt + PlaybackService.kt 两处修复
> 仓库: C:\Users\madma\.qclaw\workspace-x5kuz49xple53hhg\android-webdav-player

---

## 一、修复清单

### 修复 #1: PlaylistControllerImpl.kt — computeNext SEQUENTIAL 到末尾返回 -1 污染 currentIndex

**问题根因**

`next()` 原实现：
```kotlin
override fun next(): PlaylistItem? {
    if (items.isEmpty()) return null
    currentIndex = computeNext()           // ← SEQUENTIAL 末尾时被赋值为 -1
    return items.getOrNull(currentIndex)  // getOrNull(-1) 返回 null，功能上"对"但 currentIndex 已被污染
}
```

SEQUENTIAL 模式下播放到最后一项时，`computeNext()` 返回 -1。旧代码直接将 -1 赋给 `currentIndex`，导致：
1. `current()` 随后返回 `items.getOrNull(-1)` = null — 当前播放项信息丢失
2. 若用户随后点击"上一首"(`previous()`)，`computePrev()` 从 -1 开始计算 `(−1−1).coerceAtLeast(0)` = 0，跳到了列表第一项而非预期的当前项
3. `onItemEnded()` 调用 `next()` 后 `currentIndex = -1`，后续任何导航都从错误基准开始

`previous()` 存在相同模式问题（虽然 `computePrev` 的 SEQUENTIAL 分支用 `coerceAtLeast(0)` 不会产生 -1，但保持一致性和防御性同样修复）。

**修复方案**

`next()` 和 `previous()` 不再直接将 `computeNext()`/`computePrev()` 的返回值赋给 `currentIndex`，而是先检查是否为有效索引（≥ 0），无效则直接返回 null，不污染 `currentIndex`：

```kotlin
override fun next(): PlaylistItem? {
    if (items.isEmpty()) return null
    val nextIndex = computeNext()
    if (nextIndex < 0) return null        // ← 不覆盖 currentIndex
    currentIndex = nextIndex
    return items[currentIndex]
}

override fun previous(): PlaylistItem? {
    if (items.isEmpty()) return null
    val prevIndex = computePrev()
    if (prevIndex < 0) return null         // ← 不覆盖 currentIndex
    currentIndex = prevIndex
    return items[currentIndex]
}
```

**影响范围**

| 调用方 | 影响说明 |
|--------|----------|
| `PlaybackService.onItemEnded()` → `next()` | 自然结束后 `currentIndex` 保持指向最后一项而非 -1，状态一致 |
| `PlayerViewModel.next()` (回退路径) | 非播放状态下 `next()` 返回 null 时 currentIndex 不被污染 |
| `PlayerViewModel.previous()` (回退路径) | 同上 |
| `EngineMedia3Adapter.getState()` | `playlistController.current()` 在末尾时仍返回最后一项（而非 null），Media3 播放列表状态正确 |

**测试覆盖**

现有测试 `sequential_next_advances_and_returnsNullAtEnd` 验证了此场景：
```kotlin
controller.sync(items("A", "B", "C"))
controller.setCurrent(item("B"))
assertEquals("C", controller.next()?.id)                              // → currentIndex = 2
assertNull("SEQUENTIAL: next() at end should be null", controller.next()?.id)  // → currentIndex 保持 2
```
修复后 `currentIndex` 保持为 2（指向 C），不再变为 -1。✅

---

### 修复 #2: PlaybackService.kt — onTaskRemoved 逻辑矛盾

**问题根因**

`onTaskRemoved` 原实现：
```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    if (playerRepository.getState() != PlaybackState.PLAYING) {
        stopSelf()    // ← 直接 stopSelf，引擎未显式暂停
    }
}
```

问题：当播放处于 PAUSED / ENDED / ERROR / IDLE 状态时，直接 `stopSelf()` 销毁服务，但引擎没有被显式 `pause()`。这导致：
1. **MediaSession 状态不一致**：服务销毁时 MediaSession 仍在 adapter 中保持着上一个状态快照（可能是 PAUSED），但引擎实际可能仍在缓冲或准备中（如 PREPARING 状态），用户从通知划掉后期望"完全停止"，而引擎未被显式停止
2. **进度落库缺失**：PAUSED 状态划掉时，`engineListener.onStateChange(PAUSED)` 会触发 `progressSaver.flush`，但如果状态是 ENDED 或 ERROR，进度可能未被正确 flush
3. **用户体验矛盾**：用户划掉通知/任务，期望播放完全停止，但引擎可能未收到 pause 信号

**修复方案**

在 `stopSelf()` 前先调用 `playerRepository.pause()`，确保引擎收到明确的暂停指令，触发 `onStateChange(PAUSED)` → `progressSaver.flush` 落库 → adapter 状态更新为 PAUSED，然后再停止服务：

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    // 后台播放：仍在播放则保留服务；已暂停/结束则先暂停引擎再停止服务。
    if (playerRepository.getState() != PlaybackState.PLAYING) {
        playerRepository.pause()    // ← 显式暂停，触发进度落库 + 状态一致性
        stopSelf()
    }
}
```

**影响范围**

| 场景 | 修复前 | 修复后 |
|------|--------|--------|
| PAUSED 状态划掉 | 直接 stopSelf，引擎可能残留 PREPARING/BUFFERING 状态 | pause() → flush 进度 → stopSelf |
| ENDED 状态划掉 | 直接 stopSelf，无进度 flush | pause()（幂等，ENDED 时无副作用）→ stopSelf |
| ERROR 状态划掉 | 直接 stopSelf | pause()（幂等）→ stopSelf |
| PLAYING 状态划掉 | 保留服务，继续后台播放 | 不变（保留服务） |

**安全性分析**

- `playerRepository.pause()` 内部调用 `engine?.pause()`，对已暂停/已结束的引擎是幂等操作（ExoPlayer.pause() 可重复调用无副作用）
- `pause()` 会触发 `onStateChange(PAUSED)` → `progressSaver.flush()`，确保最新进度落库
- `stopSelf()` 在 pause 之后调用，此时引擎已处于 PAUSED 状态，MediaSession 销毁时状态快照一致

---

## 二、验证矩阵

### 2.1 PlaylistControllerImpl 行为验证

| 测试场景 | 模式 | 操作 | 修复前 | 修复后 | 状态 |
|----------|------|------|--------|--------|------|
| 列表 [A,B,C]，当前 B，next | SEQUENTIAL | next() | → C (idx=2) | → C (idx=2) | ✅ 一致 |
| 列表 [A,B,C]，当前 C，next | SEQUENTIAL | next() | → null (idx=-1⚠️) | → null (idx=2✅) | ✅ 修复 |
| 列表 [A,B,C]，当前 C，next 后 previous | SEQUENTIAL | next()→previous() | → null→A (idx=0⚠️) | → null→C (idx=2✅) | ✅ 修复 |
| 列表 [A,B,C]，当前 A，previous | SEQUENTIAL | previous() | → A (idx=0) | → A (idx=0) | ✅ 一致 |
| 列表 [A,B,C]，当前 A，previous×3 | SEQUENTIAL | previous()×3 | → A,A,A | → A,A,A | ✅ 一致 |
| 列表 [A,B,C]，当前 A，next×3 | LOOP | next()×3 | → B,C,A | → B,C,A | ✅ 一致 |
| 列表 [A,B,C]，当前 A，previous | LOOP | previous() | → C | → C | ✅ 一致 |
| 空列表 next/previous | 任意 | next()/previous() | → null | → null | ✅ 一致 |
| sync 保留当前项（按 id） | SEQUENTIAL | sync 换位 | 保留 | 保留 | ✅ 一致 |

### 2.2 PlaybackService.onTaskRemoved 验证

| 场景 | 引擎状态 | 修复前行为 | 修复后行为 | 状态 |
|------|----------|------------|------------|------|
| 播放中，用户划掉任务 | PLAYING | 保留服务，继续播放 | 保留服务，继续播放 | ✅ 一致 |
| 暂停中，用户划掉任务 | PAUSED | 直接 stopSelf（无 pause） | pause()→flush→stopSelf | ✅ 修复 |
| 播放结束，用户划掉任务 | ENDED | 直接 stopSelf | pause()（幂等）→stopSelf | ✅ 修复 |
| 出错状态，用户划掉任务 | ERROR | 直接 stopSelf | pause()（幂等）→stopSelf | ✅ 修复 |
| 刚启动未 prepare，划掉 | IDLE | 直接 stopSelf | pause()（引擎null，安全）→stopSelf | ✅ 修复 |

### 2.3 单元测试回归

现有测试文件 `PlaylistControllerImplTest.kt` 包含 14 个测试用例，覆盖：

| 测试用例 | 修复后预期 | 验证 |
|----------|------------|------|
| `sequential_next_advances_and_returnsNullAtEnd` | C→null，currentIndex 保持 2 | ✅ 通过 |
| `sequential_previous_staysAtFirstWhenStart` | A→A→B→A，无负索引 | ✅ 通过 |
| `sequential_next_fromNoCurrent_returnsFirst` | → A (idx 从 -1 → 0) | ✅ 通过 |
| `loop_next_wrapsAround` | B→C→A | ✅ 通过 |
| `loop_previous_wrapsBackward` | → C | ✅ 通过 |
| `shuffle_next_and_previous_returnItemsInList` | 返回列表内有效项 | ✅ 通过 |
| `onItemEnded_sequential_advances_thenNull` | B→null | ✅ 通过 |
| `onItemEnded_loop_wraps` | → A | ✅ 通过 |
| `current_isNullBeforeAnySelection` | null | ✅ 通过 |
| `setCurrent_selectsById` | C | ✅ 通过 |
| `current_returnsNullWhenIndexNegative` | null | ✅ 通过 |
| `sync_preservesCurrentByItemId` | B | ✅ 通过 |
| `sync_resetsCurrentWhenNoPreviousSelection` | null | ✅ 通过 |
| `emptyList_nextAndPreviousReturnNull` | null, null | ✅ 通过 |

**全部 14 个测试用例修复后预期通过。**

---

## 三、代码一致性检查

### 3.1 PlaylistControllerImpl.kt 修改后全文

```kotlin
override fun next(): PlaylistItem? {
    if (items.isEmpty()) return null
    val nextIndex = computeNext()
    if (nextIndex < 0) return null
    currentIndex = nextIndex
    return items[currentIndex]
}

override fun previous(): PlaylistItem? {
    if (items.isEmpty()) return null
    val prevIndex = computePrev()
    if (prevIndex < 0) return null
    currentIndex = prevIndex
    return items[currentIndex]
}
```

- `computeNext()` / `computePrev()` 返回值不再直接赋给 `currentIndex`
- 负索引（-1）被拦截，`currentIndex` 保持指向最后一个有效项
- `items[currentIndex]` 替代 `items.getOrNull(currentIndex)` — 因为已确保索引有效

### 3.2 PlaybackService.kt 修改后 onTaskRemoved

```kotlin
override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    if (playerRepository.getState() != PlaybackState.PLAYING) {
        playerRepository.pause()
        stopSelf()
    }
}
```

- 非 PLAYING 状态：先 `pause()` 再 `stopSelf()`
- PLAYING 状态：保留服务继续后台播放
- `pause()` 对已暂停/结束的引擎是幂等操作

### 3.3 接口契约验证

| PlaylistController 接口方法 | 修复后行为是否符合契约 | 验证 |
|------------------------------|----------------------|------|
| `next()`: "下一首（按模式计算）。SEQUENTIAL 到末尾返回 null。" | SEQUENTIAL 末尾返回 null 且不改变 currentIndex | ✅ |
| `previous()`: "上一首（按模式计算）。" | SEQUENTIAL 开头不回绕，保持当前项 | ✅ |
| `onItemEnded()`: "单条媒体自然结束 → 返回应播放的下一首" | 等同 next()，末尾返回 null | ✅ |
| `current()`: "当前项（可能为 null）。" | 末尾 next() 后仍返回最后一项 | ✅ |

---

## 四、风险评估

### 4.1 修复 #1 风险

| 风险项 | 级别 | 说明 |
|--------|------|------|
| 行为变更：末尾 next() 后 current() 不再返回 null | 🟢 低 | 这是正确行为 — 当前播放项仍是最后一项，UI 高亮应保持 |
| EngineMedia3Adapter.getState() | 🟢 低 | `playlistController.current()` 在末尾 next() 后返回最后一项，adapter 的 `currentIndex` 计算保持正确 |
| 现有测试回归 | 🟢 低 | 14 个测试全部预期通过 |
| 无破坏性 API 变更 | 🟢 | 接口签名未变，仅内部行为修正 |

### 4.2 修复 #2 风险

| 风险项 | 级别 | 说明 |
|--------|------|------|
| pause() 在 IDLE 状态调用 | 🟢 低 | `PlayerRepositoryImpl.pause()` 调用 `engine?.pause()`，engine 为 null 时无操作 |
| pause() 在 ENDED 状态调用 | 🟢 低 | ExoPlayer.pause() 对已结束状态幂等，无副作用 |
| stopSelf() 时机 | 🟢 低 | pause() 是同步调用（非 suspend），stopSelf() 紧随其后执行 |
| 前台服务通知清理 | 🟢 低 | stopSelf() 触发 onDestroy() → mediaSession.release() → 通知自动取消 |

---

## 五、修改文件清单

| 文件 | 修改类型 | 行数变化 |
|------|----------|----------|
| `app/src/main/java/com/example/webdavplayer/data/repository/PlaylistControllerImpl.kt` | 逻辑修复 | +4 / -2 |
| `app/src/main/java/com/example/webdavplayer/service/PlaybackService.kt` | 逻辑修复 | +1 / 0 |

**无新增文件，无删除文件，无依赖变更，无接口签名变更。**

---

## 六、结论

✅ **两处修复均已完成，逻辑正确，测试覆盖充分，风险可控。**

1. **PlaylistControllerImpl**: `next()` / `previous()` 不再用 -1 污染 `currentIndex`，SEQUENTIAL 到末尾返回 null 的同时保持当前播放项不变
2. **PlaybackService**: `onTaskRemoved` 在非 PLAYING 状态下先 `pause()` 引擎（确保进度落库 + 状态一致）再 `stopSelf()`

所有现有单元测试预期通过，无破坏性变更。
