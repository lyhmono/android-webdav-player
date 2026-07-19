package com.example.webdavplayer.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.webdavplayer.data.local.AppDatabase
import com.example.webdavplayer.data.local.dao.DirectoryMetaDao
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
            // ⚠️ fallbackToDestructiveMigration：数据库版本升级时直接删库重建。
            // 所有数据（播放列表、断点进度、信任证书、目录缓存）会丢失。
            // P0 阶段无不可丢失的持久数据，可接受；P1 后需改为 Migration 路径。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRemoteFileDao(db: AppDatabase): RemoteFileDao = db.remoteFileDao()

    @Provides
    fun provideDirectoryMetaDao(db: AppDatabase): DirectoryMetaDao = db.directoryMetaDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlaylistMetaDao(db: AppDatabase): PlaylistMetaDao = db.playlistMetaDao()

    @Provides
    fun providePlaybackProgressDao(db: AppDatabase): PlaybackProgressDao = db.playbackProgressDao()

    @Provides
    fun provideTrustedCertDao(db: AppDatabase): TrustedCertDao = db.trustedCertDao()
}
