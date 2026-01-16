package com.podcast.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcast_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episode_index_id"], unique = true),
        Index(value = ["podcast_id"]),
        Index(value = ["published_at"]),
        Index(value = ["episode_guid"], unique = true)
    ]
)
data class Episode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "episode_index_id")
    val episodeIndexId: Long,

    @ColumnInfo(name = "podcast_id")
    val podcastId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "audio_url")
    val audioUrl: String,

    @ColumnInfo(name = "audio_duration")
    val audioDuration: Int? = null,

    @ColumnInfo(name = "audio_size")
    val audioSize: Long? = null,

    @ColumnInfo(name = "audio_type")
    val audioType: String = "audio/mpeg",

    @ColumnInfo(name = "published_at")
    val publishedAt: Long? = null,

    @ColumnInfo(name = "episode_guid")
    val episodeGuid: String? = null,

    @ColumnInfo(name = "explicit")
    val explicit: Boolean = false,

    @ColumnInfo(name = "link")
    val link: String? = null,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "transcript_url")
    val transcriptUrl: String? = null,

    @ColumnInfo(name = "transcript_type")
    val transcriptType: String? = null,

    @ColumnInfo(name = "transcript_cached")
    val transcriptCached: String? = null,

    @ColumnInfo(name = "chapters_json")
    val chaptersJson: String? = null,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int? = null,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
