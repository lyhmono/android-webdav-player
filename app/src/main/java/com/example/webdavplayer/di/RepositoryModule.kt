package com.example.webdavplayer.di

import com.example.webdavplayer.data.player.PlayerEngineFactory
import com.example.webdavplayer.data.remote.SardineWebDavClient
import com.example.webdavplayer.data.repository.BrowseRepositoryImpl
import com.example.webdavplayer.data.repository.CacheRepositoryImpl
import com.example.webdavplayer.data.repository.MediaResolverImpl
import com.example.webdavplayer.data.repository.PlaylistControllerImpl
import com.example.webdavplayer.data.repository.PlaylistRepositoryImpl
import com.example.webdavplayer.data.repository.PlayerRepositoryImpl
import com.example.webdavplayer.data.repository.PlaybackProgressRepositoryImpl
import com.example.webdavplayer.data.repository.ServerRepositoryImpl
import com.example.webdavplayer.data.repository.SettingsRepositoryImpl
import com.example.webdavplayer.data.repository.TrustedCertRepositoryImpl
import com.example.webdavplayer.domain.player.PlaylistController
import com.example.webdavplayer.domain.repository.BrowseRepository
import com.example.webdavplayer.domain.repository.CacheRepository
import com.example.webdavplayer.domain.repository.MediaResolver
import com.example.webdavplayer.domain.repository.PlaybackProgressRepository
import com.example.webdavplayer.domain.repository.PlayerRepository
import com.example.webdavplayer.domain.repository.PlaylistRepository
import com.example.webdavplayer.domain.repository.ServerRepository
import com.example.webdavplayer.domain.repository.SettingsRepository
import com.example.webdavplayer.domain.repository.TrustedCertRepository
import com.example.webdavplayer.data.remote.WebDavClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 仓库接口 → 实现绑定（§3 di/RepositoryModule）。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWebDavClient(impl: SardineWebDavClient): WebDavClient

    @Binds
    @Singleton
    abstract fun bindCacheRepository(impl: CacheRepositoryImpl): CacheRepository

    @Binds
    @Singleton
    abstract fun bindBrowseRepository(impl: BrowseRepositoryImpl): BrowseRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlayerRepository(impl: PlayerRepositoryImpl): PlayerRepository

    @Binds
    @Singleton
    abstract fun bindTrustedCertRepository(impl: TrustedCertRepositoryImpl): TrustedCertRepository

    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: ServerRepositoryImpl): ServerRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackProgressRepository(impl: PlaybackProgressRepositoryImpl): PlaybackProgressRepository

    @Binds
    @Singleton
    abstract fun bindMediaResolver(impl: MediaResolverImpl): MediaResolver

    @Binds
    @Singleton
    abstract fun bindPlaylistController(impl: PlaylistControllerImpl): PlaylistController
}
