package com.podcast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.podcast.app.data.local.entities.Podcast
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: Podcast): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcasts(podcasts: List<Podcast>)

    @Update
    suspend fun updatePodcast(podcast: Podcast)

    @Delete
    suspend fun deletePodcast(podcast: Podcast)

    @Query("SELECT * FROM podcasts WHERE is_subscribed = 1 ORDER BY added_at DESC")
    fun getSubscribedPodcasts(): Flow<List<Podcast>>

    @Query("SELECT * FROM podcasts WHERE id = :id")
    suspend fun getPodcastById(id: Long): Podcast?

    @Query("SELECT * FROM podcasts WHERE id = :id")
    fun observePodcastById(id: Long): Flow<Podcast?>

    @Query("SELECT * FROM podcasts WHERE podcast_index_id = :indexId")
    suspend fun getPodcastByIndexId(indexId: Long): Podcast?

    @Query("SELECT * FROM podcasts WHERE feed_url = :feedUrl")
    suspend fun getPodcastByFeedUrl(feedUrl: String): Podcast?

    @Query("SELECT COUNT(*) FROM podcasts WHERE is_subscribed = 1")
    fun getSubscriptionCount(): Flow<Int>

    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcasts(): Flow<List<Podcast>>

    @Query("""
        SELECT * FROM podcasts
        WHERE title LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchPodcasts(query: String): Flow<List<Podcast>>

    @Query("UPDATE podcasts SET is_subscribed = :subscribed, updated_at = :timestamp WHERE id = :podcastId")
    suspend fun updateSubscription(podcastId: Long, subscribed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE podcasts SET last_synced_at = :timestamp WHERE id = :podcastId")
    suspend fun updateLastSynced(podcastId: Long, timestamp: Long)

    @Query("DELETE FROM podcasts WHERE is_subscribed = 0 AND updated_at < :beforeTimestamp")
    suspend fun deleteOldUnsubscribedPodcasts(beforeTimestamp: Long)
}
