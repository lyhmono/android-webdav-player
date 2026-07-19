# ============================================================
# WebDAV Player — ProGuard / R8 规则
# ============================================================

# ---- 通用 ----
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ---- Kotlin 元数据（反射 / 协程需要） ----
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.collections.** { *; }

# ---- Hilt (Dagger) ----
-keep class dagger.hilt.** { *; }
-keep class *$$hiltInjectionMembers { *; }
-keep class *Hilt_* { *; }
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *
-keep,allowobfuscation @javax.inject.Inject class *
-keep,allowobfuscation @dagger.Module class *
-keep,allowobfuscation @dagger.Provides class *
-keep,allowobfuscation @dagger.Binds class *
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
    @dagger.Provides <methods>;
    @dagger.Binds <methods>;
}

# ---- Room ----
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class *$Dao_Impl { *; }
# Room 生成的实现类
-keep class com.example.webdavplayer.data.local.** { *; }
# TypeConverters
-keep class com.example.webdavplayer.data.local.Converters { *; }
# Entity 类的字段（Room 反射读写）
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}

# ---- Compose / Kotlin 反射 ----
# Compose Compiler 生成的代码需要保留类名和签名
-keep,allowobfuscation class androidx.compose.runtime.** { *; }
-keep class androidx.compose.runtime.internal.** { *; }
# Compose ViewModel
-keep class androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel

# ---- Hilt 注入的 ViewModel ----
-keep class com.example.webdavplayer.ui.**ViewModel { *; }

# ---- Media3 (ExoPlayer) ----
-keep class androidx.media3.** { *; }
# ExoPlayer 依赖反射加载 DataSource / Renderer 工厂
-keep class com.google.android.exo2.** { *; }
-keepclassmembers class * {
    public <init>(android.content.Context);
}

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
# OkHttp 平台检测（Platform.findPlatform 反射）
-keep,allowobfuscation class okhttp3.Platform { *; }
-keep class okhttp3.internal.platform.** { *; }

# ---- Sardine-Android ----
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-dontwarn com.thegrizzlylabs.sardineandroid.**

# ---- libVLC (full flavor only) ----
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# ---- DataStore ----
-keep class androidx.datastore.** { *; }

# ---- Security-Crypto ----
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ---- Paging 3 ----
-keep class androidx.paging.** { *; }

# ---- Navigation Compose ----
-keep class androidx.navigation.** { *; }

# ---- Coroutine ----
-keep class kotlinx.coroutines.android.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- 项目自身代码 ----
# Application 类（Hilt 入口，不可混淆）
-keep class com.example.webdavplayer.WebDavPlayerApp { *; }
-keep class com.example.webdavplayer.MainActivity { *; }
# PlaybackService（MediaSessionService 入口，清单中注册）
-keep class com.example.webdavplayer.service.PlaybackService { *; }
# PlaybackSessionCallback（MediaSession 回调，反射实例化）
-keep class com.example.webdavplayer.service.PlaybackSessionCallback { *; }
-keep class com.example.webdavplayer.service.PlaybackService$PlaybackSessionCallback { *; }
# EngineMedia3Adapter（Media3 Player 实现，反射调用）
-keep class com.example.webdavplayer.service.EngineMedia3Adapter { *; }
# Domain model 枚举（Room TypeConverter 反射valueOf）
-keep enum com.example.webdavplayer.domain.model.** { *; }
# Domain model 数据类（可能序列化）
-keep class com.example.webdavplayer.domain.model.** { *; }
