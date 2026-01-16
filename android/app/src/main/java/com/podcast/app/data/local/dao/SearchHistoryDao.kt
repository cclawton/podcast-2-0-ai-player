package com.podcast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.podcast.app.data.local.entities.SearchHistory
import com.podcast.app.data.local.entities.SearchType
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Insert
    suspend fun insert(searchHistory: SearchHistory): Long

    @Query("""
        SELECT * FROM search_history
        WHERE search_type = :type
        ORDER BY searched_at DESC
        LIMIT :limit
    """)
    fun getRecentSearches(type: SearchType = SearchType.PODCAST, limit: Int = 10): Flow<List<SearchHistory>>

    @Query("""
        SELECT DISTINCT query FROM search_history
        WHERE search_type = :type
        ORDER BY searched_at DESC
        LIMIT :limit
    """)
    fun getRecentSearchQueries(type: SearchType = SearchType.PODCAST, limit: Int = 10): Flow<List<String>>

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM search_history WHERE search_type = :type")
    suspend fun clearHistory(type: SearchType)

    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM search_history WHERE searched_at < :beforeTimestamp")
    suspend fun deleteOldSearches(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM search_history")
    fun getSearchCount(): Flow<Int>
}
