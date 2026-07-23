# M3 播放器集成 — 任务完成报告

## 目标
在 WebDavPlayer 项目中新增 M3 阶段（播放器集成）的全部代码，包括 ExoPlayer 引擎实现、播放服务、播放器 UI、播放列表管理等功能。

## 新增文件清单

### 核心引擎层
1. **`core/player/exoplayer/ExoPlayerEngine.kt`** — ExoPlayer 播放引擎实现
   - 实现 `PlayerEngine` 接口所有方法
   - 使用 Media3 ExoPlayer + WebDavDataSourceFactory 读取 `webdav://` URI
   - 状态/事件映射: ExoPlayer → PlaybackState / PlaybackEvent
   - 支持播放速度、音量控制
   - 进度回调 Flow (500ms 间隔)
   - 自动下一首/上一首
   - 4 种播放模式: 顺序/单曲循环/列表循环/随机

2. **`core/player/PlaybackService.kt`** — Media3 MediaSessionService
   - 管理前台播放、MediaSession
   - 通知栏控制 (由 Media3 自动生成)
   - 锁屏 WAKE_LOCK 支持
   - 蓝牙耳机按键 (Media3 内置处理)
   - onTaskRemoved 自动停止逻辑

### 依赖注入
3. **`di/PlayerModule.kt`** — Hilt DI 模块
   - `@Binds` ExoPlayerEngine → PlayerEngine
   - `@Provides` PlayerEngineFactory

### ViewModel
4. **`feature/player/PlayerViewModel.kt`** — 播放器 ViewModel
   - 连接 ExoPlayerEngine (直接持有，Singleton)
   - 播放进度 Flow
   - 播放模式切换
   - 播放速度切换 (0.5x~2.0x)
   - 队列管理 (添加/移除/清空/跳转)
   - WebDavEntry → MediaSource 转换
   - 从播放列表数据库加载到队列
   - 保存当前队列为播放列表

5. **`feature/playlist/PlaylistViewModel.kt`** — 播放列表管理 ViewModel
   - 播放列表 CRUD
   - 播放列表项管理
   - 从队列保存为播放列表

### UI 组件
6. **`feature/player/PlayerScreen.kt`** — 全屏播放页面
   - 视频: AndroidView + PlayerView (Media3)
   - 音频: 占位图标 + 标题
   - 控制层 3 秒自动隐藏
   - 手势: 左右 seek, 左侧亮度, 右侧音量
   - 视频横屏全屏

7. **`feature/player/components/PlayerControls.kt`** — 控制层
   - 顶部: 返回 + 标题
   - 底部: 播放模式/上一首/播放暂停/下一首/播放列表
   - 进度条 Slider + 时间显示
   - 自动隐藏动画

8. **`feature/player/components/PlaylistBottomSheet.kt`** — 播放队列弹窗
   - ModalBottomSheet 展示队列
   - 当前播放项高亮
   - 点击切换播放
   - 删除按钮
   - 清空按钮

9. **`feature/player/components/SpeedSelectorDialog.kt`** — 速度选择
   - 6 档速度 (0.5x/0.75x/1.0x/1.25x/1.5x/2.0x)
   - RadioButton 单选

10. **`feature/player/components/PlayModeButton.kt`** — 播放模式按钮
    - 图标: PlayArrow/RepeatOne/Repeat/Shuffle
    - 点击循环切换: 顺序→单曲循环→列表循环→随机

11. **`feature/player/MiniPlayerBar.kt`** — 迷你播放器
    - 底部浮动条
    - 歌曲名 + 播放/暂停 + 下一首 + 进度条
    - 点击展开全屏
    - 滑入/滑出动画

12. **`feature/player/util/GestureHelper.kt`** — 手势辅助
    - 左右滑动 seek
    - 左侧上下滑动亮度
    - 右侧上下滑动音量
    - 窗口亮度管理

13. **`feature/playlist/PlaylistScreen.kt`** — 播放列表管理页面
    - 列表展示所有播放列表
    - 新建按钮 (对话框输入名称)
    - 点击进入详情
    - 长按删除 (确认对话框)

14. **`feature/playlist/PlaylistDetailScreen.kt`** — 播放列表详情
    - 展示所有项目
    - 播放全部按钮
    - 点击单项播放 (从该项开始)
    - 左滑/点击删除项目

## 修改的文件

15. **`ui/navigation/AppNavigation.kt`** — 新增路由
    - `player` — 全屏播放器
    - `playlists` — 播放列表管理
    - `playlist_detail/{playlistId}` — 播放列表详情
    - BrowserScreen 传递 playerViewModel 和播放回调
    - PlaylistScreen 底部显示 MiniPlayerBar

16. **`AndroidManifest.xml`** — 更新 Service 注册
    - 添加 MediaSessionService intent-filter
    - 移除 MissingClass 忽略
    - 权限已存在 (WAKE_LOCK, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS)

17. **`feature/browser/BrowserScreen.kt`** — 集成播放器
    - 新增参数: onPlayFile, onPlayAll, onNavigateToPlayer, onNavigateToPlaylists, playerViewModel
    - 文件点击: 可播放文件 → onPlayFile + 导航到播放器
    - 底部添加 MiniPlayerBar

18. **`feature/browser/BrowserViewModel.kt`** — 新增方法
    - `getCurrentServerId()` — 获取当前服务器 ID
    - `getPlayableEntries()` — 获取当前目录可播放文件

## 依赖确认
- `build.gradle.kts` — Media3 依赖已完整 (exoplayer/ui/session/datasource-okhttp)
- `libs.versions.toml` — media3 1.5.1 已配置

## 关键设计决策
1. **PlayerViewModel 直接持有 ExoPlayerEngine** (Singleton)，而非通过 MediaController 连接 Service — 简化架构，ExoPlayer 实例全局共享
2. **PlaybackService 使用 ExoPlayerEngine 的 ExoPlayer 实例** — Service 不创建新 Player，复用 Singleton
3. **PlayerEngine 释放由 ExoPlayerEngine.release() 管理** — Service.onDestroy 不释放 player
4. **PlayMode 使用 data.model.PlayMode** — 与 M1/M2 数据模型一致
5. **手势控制分离为 GestureHelper** — 可复用，PlayerScreen 负责调度
