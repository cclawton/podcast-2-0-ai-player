package com.podcast.app

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.PodcastFeed
import com.podcast.app.data.remote.models.SearchResponse
import com.podcast.app.data.repository.PodcastRepository
import com.podcast.app.data.rss.RssFeedParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PodcastRepositoryTest {

    private lateinit var repository: PodcastRepository
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var api: PodcastIndexApi
    private lateinit var rssFeedParser: RssFeedParser
    private lateinit var rssHttpClient: OkHttpClient

    @Before
    fun setup() {
        podcastDao = mockk(relaxed = true)
        episodeDao = mockk(relaxed = true)
        api = mockk(relaxed = true)
        rssFeedParser = mockk(relaxed = true)
        rssHttpClient = mockk(relaxed = true)
        repository = PodcastRepository(podcastDao, episodeDao, api, rssFeedParser, rssHttpClient)
    }

    @Test
    fun `getSubscribedPodcasts returns flow from dao`() = runTest {
        val podcasts = listOf(
            createTestPodcast(1, "Podcast 1"),
            createTestPodcast(2, "Podcast 2")
        )
        coEvery { podcastDao.getSubscribedPodcasts() } returns flowOf(podcasts)

        val result = repository.getSubscribedPodcasts()

        coVerify { podcastDao.getSubscribedPodcasts() }
    }

    @Test
    fun `searchPodcasts caches results in database`() = runTest {
        val feed = PodcastFeed(
            id = 123,
            title = "Test Podcast",
            url = "https://example.com/feed.xml"
        )
        val response = SearchResponse(
            status = "true",
            feeds = listOf(feed),
            count = 1
        )
        coEvery { api.searchByTerm(any(), any(), any()) } returns response

        val result = repository.searchPodcasts("test")

        assertTrue(result.isSuccess)
        coVerify { podcastDao.insertPodcasts(any()) }
    }

    @Test
    fun `searchPodcasts returns failure on API error`() = runTest {
        coEvery { api.searchByTerm(any(), any(), any()) } throws Exception("Network error")

        val result = repository.searchPodcasts("test")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getPodcast returns local data first`() = runTest {
        val podcast = createTestPodcast(123, "Local Podcast")
        coEvery { podcastDao.getPodcastByIndexId(123) } returns podcast

        val result = repository.getPodcast(123)

        assertEquals(podcast, result)
        coVerify(exactly = 0) { api.getPodcastById(any()) }
    }

    @Test
    fun `getPodcast fetches from API if not in database`() = runTest {
        coEvery { podcastDao.getPodcastByIndexId(123) } returns null

        repository.getPodcast(123)

        coVerify { api.getPodcastById(123) }
    }

    @Test
    fun `subscribeToPodcast sets isSubscribed to true`() = runTest {
        val podcast = createTestPodcast(123, "Test Podcast", isSubscribed = false)
        coEvery { podcastDao.getPodcastByIndexId(123) } returns podcast

        repository.subscribeToPodcast(123)

        coVerify {
            podcastDao.insertPodcast(match { it.isSubscribed })
        }
    }

    @Test
    fun `unsubscribeFromPodcast updates subscription status`() = runTest {
        val idSlot = slot<Long>()
        val subscribedSlot = slot<Boolean>()
        coEvery { podcastDao.updateSubscription(capture(idSlot), capture(subscribedSlot), any()) } returns Unit

        repository.unsubscribeFromPodcast(123)

        assertEquals(123L, idSlot.captured)
        assertEquals(false, subscribedSlot.captured)
    }

    private fun createTestPodcast(
        id: Long,
        title: String,
        isSubscribed: Boolean = true
    ) = Podcast(
        id = id,
        podcastIndexId = id,
        title = title,
        feedUrl = "https://example.com/feed$id.xml",
        isSubscribed = isSubscribed
    )
}
