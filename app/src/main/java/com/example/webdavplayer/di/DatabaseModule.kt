package com.example.webdavplayer.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.webdavplayer.data.local.AppDatabase
import com.example.webdavplayer.data.local.dao.PlaybackProgressDao
import com.example.webdavplayer.data.local.dao.PlaylistDao
import com.example.webdavplayer.data.local.dao.PlaylistMetaDao
import com.example.webdavplayer.data.local.dao.RemoteFileDao
import com.example.webdavplayer.data.local.dao.TrustedCertDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 数据库与 DAO 提供（§3 di/DatabaseModule）。 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "webdav_player.db")
            .fallbackToDestructiveMigration() // P0 无持久列表/进度数据，首次升级无需迁移（C2 AC5）
            .build()

    @Provides
    fun provideRemoteFileDao(db: AppDatabase): RemoteFileDao = db.remoteFileDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlaylistMetaDao(db: AppDatabase): PlaylistMetaDao = db.playlistMetaDao()

    @Provides
    fun providePlaybackProgressDao(db: AppDatabase): PlaybackProgressDao = db.playbackProgressDao()

    @Provides
    fun provideTrustedCertDao(db: AppDatabase): TrustedCertDao = db.trustedCertDao()
}
