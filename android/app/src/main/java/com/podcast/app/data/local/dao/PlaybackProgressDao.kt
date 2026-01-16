package com.podcast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.podcast.app.data.local.entities.PlaybackProgress
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: PlaybackProgress): Long

    @Update
    suspend fun update(progress: PlaybackProgress)

    @Query("SELECT * FROM playback_progress WHERE episode_id = :episodeId")
    suspend fun getProgress(episodeId: Long): PlaybackProgress?

    @Query("SELECT * FROM playback_progress WHERE episode_id = :episodeId")
    fun observeProgress(episodeId: Long): Flow<PlaybackProgress?>

    @Query("""
        SELECT * FROM playback_progress
        WHERE is_completed = 0
        ORDER BY last_played_at DESC
        LIMIT 1
    """)
    suspend fun getLastPlayedUncompletedEpisode(): PlaybackProgress?

    @Query("""
        SELECT * FROM playback_progress
        WHERE is_completed = 0
        ORDER BY last_played_at DESC
        LIMIT :limit
    """)
    fun getInProgressEpisodes(limit: Int = 20): Flow<List<PlaybackProgress>>

    @Query("""
        SELECT * FROM playback_progress
        WHERE is_completed = 1
        ORDER BY completed_at DESC
        LIMIT :limit
    """)
    fun getCompletedEpisodes(limit: Int = 50): Flow<List<PlaybackProgress>>

    @Query("""
        UPDATE playback_progress
        SET position_seconds = :position,
            last_played_at = :timestamp,
            playback_speed = :speed
        WHERE episode_id = :episodeId
    """)
    suspend fun updatePosition(
        episodeId: Long,
        position: Int,
        timestamp: Long = System.currentTimeMillis(),
        speed: Float = 1.0f
    )

    @Query("""
        UPDATE playback_progress
        SET is_completed = 1,
            completed_at = :timestamp
        WHERE episode_id = :episodeId
    """)
    suspend fun markAsCompleted(episodeId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE playback_progress
        SET is_completed = 0,
            completed_at = NULL,
            position_seconds = 0
        WHERE episode_id = :episodeId
    """)
    suspend fun markAsUnplayed(episodeId: Long)

    @Query("DELETE FROM playback_progress WHERE episode_id = :episodeId")
    suspend fun deleteProgress(episodeId: Long)

    @Query("SELECT COUNT(*) FROM playback_progress WHERE is_completed = 1")
    fun getCompletedCount(): Flow<Int>
}
