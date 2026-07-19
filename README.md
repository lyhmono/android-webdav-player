# 安卓 WebDAV 音视频播放器

通过 WebDAV 协议连接网盘 / NAS，在安卓上**直接流式播放**音视频（不下载），并提供轻量文件管理与播放列表组织。

## 功能

**连接与浏览**
- WebDAV 连接与目录浏览（HTTP/HTTPS，Basic / Digest 认证）
- 自签名证书信任：首次连接展示 SHA-256 指纹，用户确认后永久信任（设置页可查看 / 移除）
- 大目录性能：服务端 `PROPFIND(Depth:1)` 单层拉取 + Room 缓存 + 虚拟列表，上千文件滚动流畅
- 缓存优先浏览：进目录秒显 Room 缓存，仅当缓存为空或超龄（默认 5 分钟）才打 PROPFIND，并显示“更新于 Xs 前”

**播放**
- 音频：mp3 / flac / m4a / aac / wav / ogg
- 视频：mp4 / mkv / avi / mov / ts / m2ts / flv / webm / wmv / rmvb（靠双内核广覆盖）
- 可切换播放内核：**Media3(ExoPlayer) 默认 + libVLC 备选**（设置页即时切换；可选 `lite` 包仅含 Media3）
- 点击文件直接播放；长按目录自动识别其中视频加入播放列表
- 播放列表模式：顺序 / 循环 / 随机
- 播放倍速：0.5x / 0.75x / 1x / 1.25x / 1.5x / 2x（Media3 + libVLC 双内核一致，跨曲目持续生效）
- 音频后台播放 + 锁屏 / 通知栏控制（Media3 MediaSession）
- 断点续播（按 服务器+路径 记录进度，误差 ≤2s）；视频手势（亮度 / 音量 / 进度拖拽）

**文件管理（连接 WebDAV 后的目录内）**
- 上传 / 重命名 / 移动 / 删除
- 多 WebDAV 账户管理（添加 / 删除 / 切换，凭据加密存储）

**其他**
- 中文界面
- 播放列表持久化（跨会话保存）
- 应用锁：**不做**

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · DataStore · Security-Crypto · AndroidX Media3 · libVLC-android · OkHttp · sardine-android · Paging3

## 构建与运行

前置：Android SDK 34 + JDK 17 + Gradle 8.6。

用 Android Studio 打开本项目，执行：

```bash
# 运行单元测试（lite 风味，纯 JVM，无需设备）
./gradlew testLiteDebugUnitTest

# 构建
./gradlew assembleLiteDebug     # 仅 Media3 的小包
./gradlew assembleFullDebug     # 双内核（含 libVLC）的 full 包
```

## 测试

14 个测试文件、93 个单元测试（P0 基线 60 + P1 新增 24 + 倍速契约 4 + 缓存优先刷新 5），覆盖媒体类型识别、路径编码、播放列表顺序/循环/随机、断点续播、多账户切换、长按目录自动识别视频、倍速引擎契约、目录缓存 TTL 条件刷新等。

## 已知限制 / 后续（P2，第一版未做）

- 投屏（Chromecast / DLNA）
- 字幕加载与选择
- 离线缓存
- 文件夹 / 媒体缩略图
