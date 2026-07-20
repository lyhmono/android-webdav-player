package com.example.webdavplayer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    /**
     * v1 → v2 迁移：新增 playback_progress 表。
     *
     * v2 在 v1 基础上增加了 PlaybackProgressEntity（播放断点），
     * 原有表结构不变，只需 CREATE TABLE。
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playback_progress (
                    serverId TEXT NOT NULL,
                    path TEXT NOT NULL,
                    positionMs INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(serverId, path)
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "webdav_player.db")
            // 已知版本迁移走正式 Migration 路径，保留数据
            .addMigrations(MIGRATION_1_2)
            // 未知版本升级兜底（开发阶段新加字段忘记写 Migration 时）
            .fallbackToDestructiveMigration()
            // 降级时直接重建（调试回退版本场景）
            .fallbackToDestructiveMigrationOnDowngrade()
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
