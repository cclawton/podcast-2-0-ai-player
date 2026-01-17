package com.podcast.app.util

import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.local.entities.PlaybackProgress

/**
 * Test data factory providing consistent mock data for UI tests.
 */
object TestData {

    // ================================
    // Podcasts
    // ================================

    fun createPodcast(
        id: Long = 1L,
        podcastIndexId: Long = 1001L,
        title: String = "Test Podcast",
        feedUrl: String = "https://example.com/feed.xml",
        imageUrl: String? = "https://example.com/image.jpg",
        description: String? = "A test podcast description",
        author: String? = "Test Author",
        isSubscribed: Boolean = true,
        episodeCount: Int = 10
    ) = Podcast(
        id = id,
        podcastIndexId = podcastIndexId,
        title = title,
        feedUrl = feedUrl,
        imageUrl = imageUrl,
        description = description,
        author = author,
        isSubscribed = isSubscribed,
        episodeCount = episodeCount
    )

    fun createPodcastList(count: Int = 5): List<Podcast> = (1..count).map { index ->
        createPodcast(
            id = index.toLong(),
            podcastIndexId = (1000 + index).toLong(),
            title = "Podcast $index",
            feedUrl = "https://example.com/feed$index.xml",
            author = "Author $index"
        )
    }

    val samplePodcast1 = createPodcast(
        id = 1L,
        podcastIndexId = 1001L,
        title = "Tech Talk Daily",
        author = "Sarah Tech",
        description = "Daily tech news and analysis"
    )

    val samplePodcast2 = createPodcast(
        id = 2L,
        podcastIndexId = 1002L,
        title = "History Uncovered",
        author = "Prof. James History",
        description = "Deep dives into historical events"
    )

    val samplePodcast3 = createPodcast(
        id = 3L,
        podcastIndexId = 1003L,
        title = "Science Weekly",
        author = "Dr. Emily Science",
        description = "Weekly science discoveries and news"
    )

    // ================================
    // Episodes
    // ================================

    fun createEpisode(
        id: Long = 1L,
        episodeIndexId: Long = 2001L,
        podcastId: Long = 1L,
        title: String = "Test Episode",
        description: String? = "A test episode description",
        audioUrl: String = "https://example.com/audio.mp3",
        audioDuration: Int? = 3600,
        publishedAt: Long? = System.currentTimeMillis()
    ) = Episode(
        id = id,
        episodeIndexId = episodeIndexId,
        podcastId = podcastId,
        title = title,
        description = description,
        audioUrl = audioUrl,
        audioDuration = audioDuration,
        publishedAt = publishedAt
    )

    fun createEpisodeList(podcastId: Long = 1L, count: Int = 5, startId: Int = 1): List<Episode> =
        (0 until count).map { index ->
            val id = startId + index
            createEpisode(
                id = id.toLong(),
                episodeIndexId = (2000 + id).toLong(),
                podcastId = podcastId,
                title = "Episode $id",
                audioDuration = 1800 + (id * 300),
                publishedAt = System.currentTimeMillis() - (id * 86400000L)
            )
        }

    val sampleEpisode1 = createEpisode(
        id = 1L,
        episodeIndexId = 2001L,
        podcastId = 1L,
        title = "AI Revolution in 2024",
        description = "Exploring the latest AI developments",
        audioDuration = 2400
    )

    val sampleEpisode2 = createEpisode(
        id = 2L,
        episodeIndexId = 2002L,
        podcastId = 1L,
        title = "Privacy in the Digital Age",
        description = "How to protect your digital privacy",
        audioDuration = 1800
    )

    val sampleEpisode3 = createEpisode(
        id = 3L,
        episodeIndexId = 2003L,
        podcastId = 2L,
        title = "The Fall of Rome",
        description = "Understanding the collapse of the Roman Empire",
        audioDuration = 3600
    )

    // ================================
    // Playback Progress
    // ================================

    fun createPlaybackProgress(
        episodeId: Long = 1L,
        positionSeconds: Int = 300,
        durationSeconds: Int = 3600,
        playbackSpeed: Float = 1.0f
    ) = PlaybackProgress(
        episodeId = episodeId,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        lastPlayedAt = System.currentTimeMillis(),
        playbackSpeed = playbackSpeed
    )

    // ================================
    // Search Results
    // ================================

    val searchResultPodcasts = listOf(
        createPodcast(
            id = 10L,
            title = "Technology Today",
            author = "Tech Media",
            isSubscribed = false
        ),
        createPodcast(
            id = 11L,
            title = "Tech Insights",
            author = "Digital World",
            isSubscribed = false
        ),
        createPodcast(
            id = 12L,
            title = "Future Tech",
            author = "Innovation Labs",
            isSubscribed = false
        )
    )

    // ================================
    // Playback Speeds
    // ================================

    val validPlaybackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
}
