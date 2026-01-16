package com.podcast.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_progress",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["episode_id"], unique = true),
        Index(value = ["is_completed"]),
        Index(value = ["last_played_at"])
    ]
)
data class PlaybackProgress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "episode_id")
    val episodeId: Long,

    @ColumnInfo(name = "position_seconds")
    val positionSeconds: Int = 0,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null,

    @ColumnInfo(name = "playback_speed")
    val playbackSpeed: Float = 1.0f
)
