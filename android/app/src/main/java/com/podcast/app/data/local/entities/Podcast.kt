package com.podcast.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "podcasts",
    indices = [
        Index(value = ["podcast_index_id"], unique = true),
        Index(value = ["feed_url"], unique = true),
        Index(value = ["is_subscribed"]),
        Index(value = ["added_at"])
    ]
)
data class Podcast(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "podcast_index_id")
    val podcastIndexId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "feed_url")
    val feedUrl: String,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "language")
    val language: String = "en",

    @ColumnInfo(name = "explicit")
    val explicit: Boolean = false,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "website_url")
    val websiteUrl: String? = null,

    @ColumnInfo(name = "podcast_guid")
    val podcastGuid: String? = null,

    @ColumnInfo(name = "author")
    val author: String? = null,

    @ColumnInfo(name = "episode_count")
    val episodeCount: Int = 0,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,

    @ColumnInfo(name = "is_subscribed")
    val isSubscribed: Boolean = true,

    @ColumnInfo(name = "custom_name")
    val customName: String? = null,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
