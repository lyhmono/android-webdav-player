# WebDAV Player M5 高级功能 — 完成记录

## 日期
2026-07-10

## 目标
完成 M5 阶段（LibVLC 引擎集成 + 音轨/字幕切换 + 引擎动态切换）全部代码

## 完成内容

### Git 提交
- commit: `feat: M5 高级功能` (3886e62)
- 18 files changed, +1714 / -138 lines
- 已推送到 https://github.com/lyhmono/WebDavPlayer main 分支

### M5 新增文件 (7 个)

| 文件 | 行数 | 说明 |
|------|------|------|
| core/player/TrackInfo.kt | 41 | 音轨/字幕数据模型 + TrackType 枚举 |
| core/player/libvlc/LibVlcPlayerEngine.kt | 572 | LibVLC 引擎完整实现 PlayerEngine 接口 |
| core/player/libvlc/LibVlcMediaFactory.kt | 102 | MediaSource → LibVLC Media 转换 + 认证 URL |
| core/player/EngineManager.kt | 150 | 引擎管理器（动态切换 + 状态保存/恢复） |
| feature/player/components/AudioTrackDialog.kt | 117 | 音轨选择对话框 |
| feature/player/components/SubtitleTrackDialog.kt | 156 | 字幕选择对话框（含外部字幕预留） |
| feature/player/components/LibVlcVideoView.kt | 37 | LibVLC SurfaceView 的 Compose 封装 |

### M5 修改文件 (11 个)
1. PlayerEngine.kt — 接口扩展 5 方法 + 2 StateFlow
2. ExoPlayerEngine.kt — DefaultTrackSelector 音轨/字幕实现 (+168 行)
3. PlayerModule.kt — 双引擎 DI 绑定
4. PlayerViewModel.kt — EngineManager 注入 + 音轨/字幕/引擎切换
5. PlayerScreen.kt — 根据引擎类型选择渲染视图 + 对话框
6. PlayerControls.kt — 三段式布局（左:模式+音轨+字幕 | 中:播放控制 | 右:速度+列表）
7. SettingsScreen.kt — ExoPlayer 和 LibVLC 均可切换
8. SettingsViewModel.kt — switchEngine 方法
9. libs.versions.toml — LibVLC 3.6.0
10. build.gradle.kts — vlc-android 依赖
11. AndroidManifest.xml — configChanges 防重建

### 功能清单
- ✅ LibVLC 引擎完整实现（WebDAV HTTP 流式播放 + Basic Auth）
- ✅ ExoPlayer 音轨切换（DefaultTrackSelector + TrackSelectionOverride）
- ✅ ExoPlayer 字幕切换（内嵌字幕 + 外部字幕接口预留）
- ✅ LibVLC 音轨/字幕切换（MediaPlayer.getTrackDescription）
- ✅ 引擎动态切换（保存队列/位置/模式/速度 → 切换 → 恢复）
- ✅ 音轨选择对话框（语言/标题 + 当前高亮 + 默认选项）
- ✅ 字幕选择对话框（关闭字幕 + 外部字幕预留）
- ✅ PlayerControls 三段式布局
- ✅ PlayerScreen 双引擎视图渲染（PlayerView / LibVlcVideoView）
- ✅ 设置页面引擎选择可用

### 项目总计
- **89 个源文件**，**80 个 Kotlin 文件**，**9410 行 Kotlin 代码**
- 5 个 Git 提交（M1→M2→M3→M4→M5）

### Git 提交历史
1. M1: 基础架构 - WebDAV客户端 + 播放内核抽象 + Room + Hilt DI
2. M2: 文件浏览器 - 目录浏览/排序/搜索/多选/上传/重命名/删除
3. M3: 播放器集成 - ExoPlayer引擎/Media3 Service/全屏播放器/手势控制/播放列表
4. M4: 设置页面 - DataStore偏好存储/主题切换/排序偏好持久化/缓存管理
5. M5: 高级功能 - LibVLC引擎/音轨字幕切换/引擎动态切换

## 项目完成度
WebDAV Player 核心功能全部完成，包括:
- WebDAV 服务器管理（增删改查 + 连接测试）
- 文件浏览器（目录浏览/排序/搜索/多选/上传/下载/重命名/删除）
- 双播放内核（ExoPlayer + LibVLC，可动态切换）
- 全屏播放器（手势控制/进度条/播放模式/速度/音轨/字幕）
- Media3 前台播放服务（通知栏/锁屏控制）
- 播放列表管理（创建/删除/添加/移除/播放）
- 迷你播放器条
- 设置页面（主题/排序/缓存/引擎/高级）
- 自签证书支持
