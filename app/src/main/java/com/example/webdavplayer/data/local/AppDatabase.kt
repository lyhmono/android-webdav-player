package com.example.webdavplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.webdavplayer.data.local.dao.PlaylistDao
import com.example.webdavplayer.data.local.dao.PlaylistMetaDao
import com.example.webdavplayer.data.local.dao.PlaybackProgressDao
import com.example.webdavplayer.data.local.dao.RemoteFileDao
import com.example.webdavplayer.data.local.dao.TrustedCertDao
import com.example.webdavplayer.data.local.entity.PlaylistItemEntity
import com.example.webdavplayer.data.local.entity.PlaylistMetaEntity
import com.example.webdavplayer.data.local.entity.PlaybackProgressEntity
import com.example.webdavplayer.data.local.entity.RemoteFileEntity
import com.example.webdavplayer.data.local.entity.TrustedCertEntity

/** 应用数据库：远程文件缓存 + 播放列表 + 播放模式 + 播放进度 + 信任证书。 */
@Database(
    entities = [
        RemoteFileEntity::class,
        PlaylistItemEntity::class,
        TrustedCertEntity::class,
        PlaylistMetaEntity::class,   // C2：播放模式单例行
        PlaybackProgressEntity::class, // C3：进度表
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun remoteFileDao(): RemoteFileDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistMetaDao(): PlaylistMetaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun trustedCertDao(): TrustedCertDao
}
