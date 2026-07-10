package com.example.webdavplayer.data.local

import androidx.room.TypeConverter
import com.example.webdavplayer.domain.model.MediaType
import com.example.webdavplayer.domain.model.PlayMode

/** Room 类型转换器：枚举 <-> 字符串。 */
class Converters {
    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun playModeToString(value: PlayMode): String = value.name

    @TypeConverter
    fun stringToPlayMode(value: String): PlayMode = PlayMode.valueOf(value)
}
