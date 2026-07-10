package com.example.webdavplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络模块（§3 di/NetworkModule）。
 *
 * 提供基础 [OkHttpClient]（超时等通用配置）；各服务器的自签信任 + Basic/Digest 鉴权
 * 在 [com.example.webdavplayer.data.remote.SardineWebDavClient] 中按 [com.example.webdavplayer.domain.model.ServerConfig] 在其基础上叠加。
 * 这样「共享 OkHttp」被复用（流式播放亦复用同一实例，§8）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideBaseOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
