# PlayerScreen 重写 + 全屏手势 + VLC Surface 时序修复

**日期**: 2026-07-23 04:38  
**Commit**: `3203ff2`  
**CI**: ✅ 通过 (lite 21MB + full 199MB)

## 修复内容

### 1. 删除视频重复控制按钮
- **问题**: PlayerView 自带控制器（进度条/播放暂停/快进快退）已可用，但下面还有一行 Compose 的播放/暂停/切歌按钮
- **修复**: 视频模式下不再显示额外的控制按钮，只用 PlayerView 自带的控制器
- **音频模式**: 保留 Compose 控制按钮 + Slider（音频没有 PlayerView）

### 2. 真正的全屏模式
- 强制横屏 (`SCREEN_ORIENTATION_LANDSCAPE`)
- 沉浸式 SystemUI（状态栏 + 导航栏全隐藏，`IMMERSIVE_STICKY`）
- `FLAG_KEEP_SCREEN_ON` 保持屏幕常亮
- `PlayerView` `RESIZE_MODE_FIT`（适应屏幕，不拉伸变形）
- 点击屏幕切换控制层显示/隐藏

### 3. 全屏滑动手势 seek
- `detectHorizontalDragGestures` 检测水平滑动
- 滑动整个屏幕宽度 = 跳整条视频时长
- 滑动中实时显示目标时间预览（中心气泡）
- 松手后执行 seek
- `onDragStart` 记录起始位置，`onDragEnd` 执行 seek

### 4. 全屏底部控制栏
- Slider 进度条（白色 thumb + 主题色 track）
- 快退 15s / 播放暂停 / 快进 15s
- 左侧当前时间 / 右侧总时长

### 5. VLC Surface 绑定时序修复
- **根因**: SurfaceView 的 `surfaceCreated` 回调在 `mediaPlayer` 创建之前就触发了，导致 `vlcVout.setVideoView()` 没被调用
- **修复**:
  - 新增 `surfaceReady` 标志位
  - `attachVlcVout()` 幂等函数：surface 和 mediaPlayer 都就绪时才绑定
  - `prepare()` 和 `play()` 中都调用 `attachVlcVout()` 确保绑定
  - `release()` 中 `detachViews` 加 try-catch

## APK 路径
- Lite: `downloaded-apks/app-lite-debug/app-lite-debug.apk` (21 MB)
- Full: `downloaded-apks/app-full-debug/app-full-debug.apk` (199 MB)
