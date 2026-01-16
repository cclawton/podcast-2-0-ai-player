package com.podcast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.podcast.app.data.local.entities.Episode
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: Episode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<Episode>)

    @Update
    suspend fun updateEpisode(episode: Episode)

    @Delete
    suspend fun deleteEpisode(episode: Episode)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: Long): Episode?

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observeEpisodeById(id: Long): Flow<Episode?>

    @Query("SELECT * FROM episodes WHERE episode_index_id = :indexId")
    suspend fun getEpisodeByIndexId(indexId: Long): Episode?

    @Query("SELECT * FROM episodes WHERE episode_guid = :guid")
    suspend fun getEpisodeByGuid(guid: String): Episode?

    @Query("""
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId
        ORDER BY published_at DESC
        LIMIT :limit
    """)
    fun getEpisodesByPodcast(podcastId: Long, limit: Int = 100): Flow<List<Episode>>

    @Query("""
        SELECT * FROM episodes
        WHERE podcast_id = :podcastId
        ORDER BY published_at DESC
    """)
    suspend fun getEpisodesByPodcastOnce(podcastId: Long): List<Episode>

    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN podcasts p ON e.podcast_id = p.id
        WHERE p.is_subscribed = 1
        ORDER BY e.published_at DESC
        LIMIT :limit
    """)
    fun getRecentEpisodesFromSubscriptions(limit: Int = 50): Flow<List<Episode>>

    @Query("""
        SELECT * FROM episodes
        WHERE title LIKE '%' || :query || '%'
        OR description LIKE '%' || :query || '%'
        ORDER BY published_at DESC
        LIMIT :limit
    """)
    fun searchEpisodes(query: String, limit: Int = 50): Flow<List<Episode>>

    @Query("SELECT COUNT(*) FROM episodes WHERE podcast_id = :podcastId")
    fun getEpisodeCountForPodcast(podcastId: Long): Flow<Int>

    @Query("""
        SELECT e.* FROM episodes e
        LEFT JOIN playback_progress pp ON e.id = pp.episode_id
        WHERE e.podcast_id = :podcastId
        AND (pp.is_completed IS NULL OR pp.is_completed = 0)
        ORDER BY e.published_at DESC
        LIMIT 1
    """)
    suspend fun getNextUnplayedEpisode(podcastId: Long): Episode?

    @Query("UPDATE episodes SET transcript_cached = :transcript WHERE id = :episodeId")
    suspend fun updateCachedTranscript(episodeId: Long, transcript: String)

    @Query("DELETE FROM episodes WHERE podcast_id = :podcastId")
    suspend fun deleteEpisodesForPodcast(podcastId: Long)
}
