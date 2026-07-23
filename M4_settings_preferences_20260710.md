# M4 阶段实现：设置页面 + 偏好存储

## 目标
为 WebDavPlayer 项目新增 M4 阶段全部代码，包括设置页面 UI、偏好存储（DataStore Preferences）、主题动态切换、排序偏好持久化、自签证书信任等功能。

## 新增文件

### 1. `app/src/main/java/com/webdav/player/ui/theme/ThemeMode.kt`
- 定义 `ThemeMode` 枚举：SYSTEM / LIGHT / DARK，各带 displayName

### 2. `app/src/main/java/com/webdav/player/data/preferences/AppPreferences.kt`
- 使用 DataStore Preferences 存储 12 项设置
- 每项设置提供 Flow 读取 + suspend 修改方法
- 设置项：播放引擎、自动播放下一首、默认播放速度、主题模式、动态色彩、默认排序字段/方向、目录优先、缓存过期时间、缩略图缓存开关、手势控制、自签证书信任开关

### 3. `app/src/main/java/com/webdav/player/feature/settings/SettingsViewModel.kt`
- 注入 AppPreferences + ThumbnailCacheManager
- 所有设置项暴露为 StateFlow（SharingStarted.Eagerly，主题切换实时生效）
- 提供清除缓存方法、关于信息（版本号从 BuildConfig 获取）

### 4. `app/src/main/java/com/webdav/player/feature/settings/SettingsScreen.kt`
- Material3 设置页面，6 个分组卡片：播放设置 / 外观设置 / 浏览设置 / 缓存管理 / 高级 / 关于
- 主题模式用 SegmentedButton，播放引擎用 RadioButton 列表，开关用 Switch，排序用 DropdownMenu
- 清除缓存显示确认 AlertDialog，展示缓存大小

## 修改文件

### 5. `ui/theme/Theme.kt`
- WebDavPlayerTheme 参数从 `darkTheme: Boolean` 改为 `themeMode: ThemeMode` + `dynamicColor: Boolean`
- 根据 themeMode 决定深浅色

### 6. `MainActivity.kt`
- 注入 AppPreferences，combine themeMode + dynamicColor 为 StateFlow
- setContent 中 collectAsState 后传给 WebDavPlayerTheme，主题切换实时生效

### 7. `ui/navigation/AppNavigation.kt`
- 新增 `SETTINGS = "settings"` 路由
- ServerConfigScreen 传入 onNavigateToSettings 回调
- 新增 SettingsScreen composable 路由

### 8. `ui/ServerConfigScreen.kt`
- 新增 `onNavigateToSettings` 参数
- TopAppBar actions 添加 Settings 齿轮图标 IconButton

### 9. `feature/browser/BrowserViewModel.kt`
- 构造函数注入 AppPreferences
- init 块从 AppPreferences 读取默认排序设置（sortField / sortAscending / directoriesFirst）
- setSortField / toggleSortOrder / toggleDirectoriesFirst 变更时同步持久化到 AppPreferences

### 10. `di/NetworkModule.kt`
- provideOkHttpClient 接收 DataStore + Context 参数
- 读取 trustSelfSignedCert 偏好，若开启则配置信任所有证书的 TrustManager + HostnameVerifier

### 11. `di/AppModule.kt`
- 新增 provideAppPreferences 提供 AppPreferences 单例

### 12. `app/build.gradle.kts`
- buildFeatures 中新增 `buildConfig = true`，支持 BuildConfig.VERSION_NAME 访问

### 13. `core/cache/ThumbnailCacheManager.kt`
- 新增 `getDiskCacheSize()` 方法返回磁盘缓存字节数

## 关键设计决策
- 主题切换实时生效：MainActivity 用 combine + stateIn(Eagerly) + collectAsState，主题/动态色彩变更无需重启
- 排序偏好双向同步：BrowserViewModel init 从偏好读取 → 用户排序操作同步回偏好
- 自签证书：NetworkModule 在 OkHttpClient 构建时读取偏好（runBlocking 一次性读取，因为 DI 时无 Compose 作用域）
- AppPreferences 通过 Hilt @Singleton 提供，同时被 SettingsViewModel / BrowserViewModel / MainActivity / NetworkModule 使用
