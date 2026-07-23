# 视频画面渲染修复

**日期:** 2026-07-23 03:47
**Commit:** 93019e3
**CI Run:** 29953022580 (通过)

## 问题

用户反馈"还是同样问题"——点击视频后无画面渲染。

## 根因分析

1. **ExoPlayer 实例不是 Compose State** — 原实现 `val exoPlayer: ExoPlayer? get() = playerRepository.getExoPlayer()` 是普通 getter，不触发 recomposition。`AndroidView` 的 `factory` 只在首次组合时执行，此时 `exoPlayer` 为 null，后续即使 ExoPlayer 被创建也不会重新绑定。

2. **AndroidView factory 绑定时机错误** — 在 `factory` 中设置 `player = exoPlayer`，但 factory 只执行一次，如果此时 exoPlayer 为 null，PlayerView 永远不会拿到 player。

## 修复方案

1. **PlayerViewModel**: `exoPlayer` 改为 `MutableStateFlow<ExoPlayer?>`，`playItem` 成功后更新 `_exoPlayer.value = playerRepository.getExoPlayer()`
2. **PlayerScreen**: 用 `collectAsStateWithLifecycle()` 收集 `exoPlayer`，`AndroidView` 添加 `update` 块在每次 recomposition 时绑定最新 player 实例
3. **AndroidView factory**: 不再在 factory 中设置 player，改为在 `update` 块中 `if (view.player !== exoPlayer) view.player = exoPlayer`

## 修改文件

- `PlayerViewModel.kt` — exoPlayer 改为 StateFlow
- `PlayerScreen.kt` — collectAsStateWithLifecycle + AndroidView update 块

## APK 产出

- Lite: `apk-output/latest/app-lite-debug.apk` (21.29MB)
- Full: `apk-output/latest-full/app-full-debug.apk` (199.37MB)
