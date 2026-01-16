package com.podcast.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SearchType {
    PODCAST,
    EPISODE
}

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["searched_at"]),
        Index(value = ["query"])
    ]
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "query")
    val query: String,

    @ColumnInfo(name = "search_type")
    val searchType: SearchType = SearchType.PODCAST,

    @ColumnInfo(name = "searched_at")
    val searchedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "result_count")
    val resultCount: Int = 0
)
