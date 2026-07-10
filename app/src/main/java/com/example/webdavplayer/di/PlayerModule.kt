package com.example.webdavplayer.di

import com.example.webdavplayer.data.player.WebDavStreamingSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 播放内核相关提供（§3 di/PlayerModule）。 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideWebDavStreamingSource(): WebDavStreamingSource = WebDavStreamingSource()
}
