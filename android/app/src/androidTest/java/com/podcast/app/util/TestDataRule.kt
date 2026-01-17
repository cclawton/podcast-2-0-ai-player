package com.podcast.app.util

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.database.PodcastDatabase
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import javax.inject.Inject

/**
 * JUnit rule that populates the test database with sample data before each test.
 *
 * Usage:
 * ```kotlin
 * @get:Rule
 * val testDataRule = TestDataRule()
 * ```
 *
 * This rule requires Hilt injection. Make sure to call `hiltRule.inject()` before
 * this rule runs (use ordering: hiltRule order=0, testDataRule order=1).
 */
class TestDataRule @Inject constructor(
    private val database: PodcastDatabase,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao
) : TestWatcher() {

    override fun starting(description: Description?) {
        super.starting(description)
        populateTestData()
    }

    override fun finished(description: Description?) {
        super.finished(description)
        clearTestData()
    }

    private fun populateTestData() = runBlocking {
        // Insert sample podcasts
        val podcasts = TestData.createPodcastList(5)
        podcasts.forEach { podcast ->
            podcastDao.insertPodcast(podcast)
        }

        // Insert sample episodes for first podcast
        val episodes = TestData.createEpisodeList(podcastId = 1L, count = 5)
        episodes.forEach { episode ->
            episodeDao.insertEpisode(episode)
        }

        // Insert episodes for second podcast
        val moreEpisodes = TestData.createEpisodeList(podcastId = 2L, count = 3)
        moreEpisodes.forEach { episode ->
            episodeDao.insertEpisode(episode)
        }
    }

    private fun clearTestData() = runBlocking {
        database.clearAllTables()
    }
}

/**
 * Simple test data populator that can be used without a rule.
 * Call this in @Before methods after Hilt injection.
 */
object TestDataPopulator {

    suspend fun populate(podcastDao: PodcastDao, episodeDao: EpisodeDao) {
        // Insert sample podcasts
        val podcasts = TestData.createPodcastList(5)
        podcasts.forEach { podcast ->
            podcastDao.insertPodcast(podcast)
        }

        // Insert sample episodes for first podcast
        val episodes = TestData.createEpisodeList(podcastId = 1L, count = 5)
        episodes.forEach { episode ->
            episodeDao.insertEpisode(episode)
        }

        // Insert episodes for second podcast
        val moreEpisodes = TestData.createEpisodeList(podcastId = 2L, count = 3)
        moreEpisodes.forEach { episode ->
            episodeDao.insertEpisode(episode)
        }
    }

    suspend fun clear(database: PodcastDatabase) {
        database.clearAllTables()
    }
}
