package com.podcast.app.data.local.database

import androidx.room.TypeConverter
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.SearchType

class Converters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)

    @TypeConverter
    fun fromSearchType(type: SearchType): String = type.name

    @TypeConverter
    fun toSearchType(value: String): SearchType = SearchType.valueOf(value)
}
