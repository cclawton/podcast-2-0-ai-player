package com.podcast.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.podcast.app.data.local.entities.Download
import com.podcast.app.data.local.entities.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: Download): Long

    @Update
    suspend fun update(download: Download)

    @Delete
    suspend fun delete(download: Download)

    @Query("SELECT * FROM downloads WHERE episode_id = :episodeId")
    suspend fun getDownload(episodeId: Long): Download?

    @Query("SELECT * FROM downloads WHERE episode_id = :episodeId")
    fun observeDownload(episodeId: Long): Flow<Download?>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY created_at DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloaded_at DESC")
    fun getCompletedDownloads(): Flow<List<Download>>

    @Query("SELECT * FROM downloads WHERE status = 'PENDING' OR status = 'IN_PROGRESS' ORDER BY created_at ASC")
    fun getPendingDownloads(): Flow<List<Download>>

    @Query("""
        UPDATE downloads
        SET status = :status,
            downloaded_bytes = :downloadedBytes,
            downloaded_at = CASE WHEN :status = 'COMPLETED' THEN :timestamp ELSE downloaded_at END
        WHERE episode_id = :episodeId
    """)
    suspend fun updateDownloadProgress(
        episodeId: Long,
        status: DownloadStatus,
        downloadedBytes: Long,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE downloads SET status = :status, error_message = :errorMessage WHERE episode_id = :episodeId")
    suspend fun updateDownloadStatus(episodeId: Long, status: DownloadStatus, errorMessage: String? = null)

    @Query("DELETE FROM downloads WHERE episode_id = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: Long)

    @Query("SELECT SUM(file_size) FROM downloads WHERE status = 'COMPLETED'")
    fun getTotalDownloadedSize(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    fun getDownloadedEpisodeCount(): Flow<Int>
}
