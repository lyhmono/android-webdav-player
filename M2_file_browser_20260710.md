# M2 文件浏览器阶段 - 任务完成报告

## 目标
在 WebDavPlayer 项目中新增 M2 阶段（文件浏览器）的全部代码，基于 M1 已有基础架构。

## 完成内容

### 新增文件（共 16 个）

#### 核心缓存层
1. **`core/cache/DirectoryCacheManager.kt`** — 目录缓存管理器
   - 使用 DirectoryCacheDao 缓存目录列表 JSON
   - 先返回缓存 → 后台刷新策略
   - 缓存过期判断（默认 5 分钟）
   - 提供 getCached/refresh/getCachedEvenIfExpired/clear 等方法

2. **`core/cache/ThumbnailCacheManager.kt`** — 缩略图缓存管理器
   - 视频缩略图：通过 WebDavClient 获取文件头部 2MB，用 MediaMetadataRetriever 提取帧
   - 音频封面：预留接口（M2 先返回 null）
   - LRU 内存缓存（64条）+ 磁盘缓存（100MB 上限，自动清理最旧文件）

3. **`core/cache/WebDavEntrySerializer.kt`** — WebDavEntry 的 JSON 序列化包装类
   - 领域模型 WebDavEntry 未标注 @Serializable，使用包装类进行序列化

#### 数据层
4. **`data/repository/ProgressRequestBody.kt`** — 带进度跟踪的 OkHttp RequestBody
   - 包装原始 RequestBody，在写入时回调进度

#### ViewModel
5. **`feature/browser/BrowserViewModel.kt`** — 文件浏览器 ViewModel
   - 管理当前路径、文件列表、加载状态
   - 排序：名称/大小/修改时间/类型（升降序可切换）+ 目录优先选项
   - 搜索：当前目录内实时过滤
   - 缓存优先：先显示缓存数据，后台刷新
   - 多选模式：长按进入，支持批量删除、重命名
   - 文件操作：删除、重命名
   - 上传：文件选择 + 进度跟踪 + 取消

#### UI 组件
6. **`feature/browser/BrowserScreen.kt`** — 主界面
   - 面包屑导航 + 搜索 + 排序栏 + 文件列表 + FAB
   - 下拉刷新、加载中、空目录状态
   - 多选模式 + 底部操作栏

7. **`feature/browser/components/FileListItem.kt`** — 文件列表项
   - 文件类型图标 + 名称 + 大小 + 相对时间
   - combinedClickable 支持点击和长按
   - 选中状态视觉反馈

8. **`feature/browser/components/BreadcrumbNav.kt`** — 面包屑导航
   - 显示路径层级，支持水平滚动和点击跳转

9. **`feature/browser/components/SortBar.kt`** — 排序栏
   - 排序字段选择 + 升降序切换 + 目录优先

10. **`feature/browser/components/SelectionActionBar.kt`** — 多选操作栏
    - 底部浮动栏，显示选中数量和操作按钮

11. **`feature/browser/components/UploadDialog.kt`** — 上传对话框
    - 系统文件选择器 + 上传进度显示 + 取消

12. **`feature/browser/components/RenameDialog.kt`** — 重命名对话框

13. **`feature/browser/components/SearchBar.kt`** — 搜索栏
    - 展开/收起式，实时过滤

#### 工具类
14. **`feature/browser/util/FileTypeIcon.kt`** — 文件类型图标映射
    - 视频/音频/图片/PDF/文档/压缩包/其他

15. **`feature/browser/util/Formatters.kt`** — 格式化工具
    - formatFileSize / formatDate / formatDuration / formatRelativeTime

#### 导航
16. **`ui/navigation/AppNavigation.kt`** — NavHost 配置
    - 路由：server_config → browser/{serverId}/{encodedPath}

### 修改的文件（共 6 个）

1. **`MainActivity.kt`** — setContent 替换为 AppNavigation()
2. **`ui/ServerConfigScreen.kt`** — 添加 onServerClick 参数，服务器卡片可点击导航
3. **`ui/ServerConfigViewModel.kt`** — 添加 getServerById 方法
4. **`data/repository/WebDavRepository.kt`** — 新增 uploadFile/deleteFile/renameFile/createDirectory/getFileStream 方法
5. **`core/webdav/WebDavClient.kt`** — 新增 uploadFile(path, requestBody) 方法
6. **`gradle/libs.versions.toml`** + **`app/build.gradle.kts`** — 添加 Coil 2.7.0 依赖

## 关键设计决策
- 缓存优先策略：先显示缓存（即使过期），后台异步刷新
- 缩略图提取：下载文件头部 2MB → MediaMetadataRetriever 提取帧 → JPEG 压缩存盘
- 排序默认：目录优先 + 名称升序
- 上传进度：通过 ProgressRequestBody 包装实现
- 所有 UI 文本使用中文
