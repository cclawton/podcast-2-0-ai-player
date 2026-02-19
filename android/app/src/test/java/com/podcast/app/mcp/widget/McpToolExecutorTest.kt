package com.podcast.app.mcp.widget

import com.podcast.app.data.local.dao.EpisodeDao
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.remote.api.PodcastIndexApi
import com.podcast.app.data.remote.models.EpisodesResponse
import com.podcast.app.data.remote.models.EpisodeItem
import com.podcast.app.data.remote.models.PodcastFeed
import com.podcast.app.data.remote.models.PodcastResponse
import com.podcast.app.data.remote.models.SearchResponse
import com.podcast.app.mcp.bridge.InputValidator
import com.podcast.app.playback.IPlaybackController
import com.podcast.app.playback.PlaybackState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * Unit tests for McpToolExecutor.
 *
 * Tests the routing of MCP tool calls to appropriate services
 * and validation of inputs.
 */
class McpToolExecutorTest {

    private lateinit var podcastIndexApi: PodcastIndexApi
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var playbackController: IPlaybackController
    private lateinit var inputValidator: InputValidator
    private lateinit var executor: McpToolExecutor

    @Before
    fun setup() {
        podcastIndexApi = mockk()
        podcastDao = mockk()
        episodeDao = mockk()
        playbackController = mockk()
        inputValidator = InputValidator()

        // Setup default playback controller behavior
        every { playbackController.playbackState } returns MutableStateFlow(PlaybackState())
        every { playbackController.currentEpisode } returns MutableStateFlow(null)
        every { playbackController.queue } returns MutableStateFlow(emptyList())
        every { playbackController.pause() } just Runs
        every { playbackController.resume() } just Runs

        executor = McpToolExecutor(
            podcastIndexApi = podcastIndexApi,
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            playbackController = playbackController,
            inputValidator = inputValidator
        )
    }

    // ========== Search Tests ==========

    @Test
    fun `search_byterm returns podcasts on success`() = runTest {
        // Given
        val searchResponse = SearchResponse(
            status = "true",
            feeds = listOf(
                PodcastFeed(
                    id = 123L,
                    title = "Test Podcast",
                    url = "https://example.com/feed.xml",
                    author = "Test Author",
                    episodeCount = 10,
                    language = "en"
                )
            ),
            count = 1
        )
        coEvery { podcastIndexApi.searchByTerm(any(), any(), any()) } returns searchResponse

        // When
        val result = executor.execute("search_byterm", """{"q": "test"}""")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Podcasts::class.java)
        val podcasts = (result as McpToolExecutor.ToolResult.Podcasts).items
        assertThat(podcasts).hasSize(1)
        assertThat(podcasts[0].id).isEqualTo(123L)
        assertThat(podcasts[0].title).isEqualTo("Test Podcast")
    }

    @Test
    fun `search_byterm returns error when query is missing`() = runTest {
        // When
        val result = executor.execute("search_byterm", "{}")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Error::class.java)
        assertThat((result as McpToolExecutor.ToolResult.Error).message)
            .contains("Missing required parameter")
    }

    @Test
    fun `search_byterm returns error when query is empty`() = runTest {
        // When
        val result = executor.execute("search_byterm", """{"q": ""}""")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Error::class.java)
    }

    // ========== Episodes Tests ==========

    @Test
    fun `episodes_byfeedid returns local episodes when available`() = runTest {
        // Given
        val podcast = Podcast(
            id = 1L,
            podcastIndexId = 123L,
            title = "Test Podcast",
            feedUrl = "https://example.com/feed.xml"
        )
        val episodes = listOf(
            Episode(
                id = 1L,
                episodeIndexId = 1001L,
                podcastId = 1L,
                title = "Episode 1",
                audioUrl = "https://example.com/ep1.mp3"
            )
        )

        coEvery { podcastDao.getPodcastByIndexId(123L) } returns podcast
        coEvery { episodeDao.getEpisodesByPodcast(1L, any()) } returns flowOf(episodes)

        // When
        val result = executor.execute("episodes_byfeedid", """{"id": 123}""")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Episodes::class.java)
        val items = (result as McpToolExecutor.ToolResult.Episodes).items
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Episode 1")
    }

    @Test
    fun `episodes_byfeedid falls back to API when local unavailable`() = runTest {
        // Given
        coEvery { podcastDao.getPodcastByIndexId(123L) } returns null

        val apiResponse = EpisodesResponse(
            status = "true",
            items = listOf(
                EpisodeItem(
                    id = 456L,
                    title = "API Episode",
                    enclosureUrl = "https://example.com/ep.mp3",
                    datePublished = 1234567890L,
                    duration = 3600
                )
            ),
            count = 1
        )
        coEvery { podcastIndexApi.getEpisodesByFeedId(123L, any()) } returns apiResponse

        // When
        val result = executor.execute("episodes_byfeedid", """{"id": 123}""")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Episodes::class.java)
        val items = (result as McpToolExecutor.ToolResult.Episodes).items
        assertThat(items).hasSize(1)
        assertThat(items[0].id).isEqualTo(456L)
        assertThat(items[0].title).isEqualTo("API Episode")
    }

    @Test
    fun `episodes_byfeedid returns error when id is missing`() = runTest {
        // When
        val result = executor.execute("episodes_byfeedid", "{}")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Error::class.java)
        assertThat((result as McpToolExecutor.ToolResult.Error).message)
            .contains("Missing required parameter")
    }

    @Test
    fun `episodes_byfeedid returns error for invalid id`() = runTest {
        // When
        val result = executor.execute("episodes_byfeedid", """{"id": -1}""")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Error::class.java)
    }

    // ========== Playback Tests ==========

    @Test
    fun `play_episode starts playback`() = runTest {
        // Given
        coEvery { playbackController.playEpisode(123L, 0) } just Runs

        // When
        val result = executor.execute("play_episode", """{"id": 123}""")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Success::class.java)
        coVerify { playbackController.playEpisode(123L, 0) }
    }

    @Test
    fun `pause_playback pauses playback`() = runTest {
        // When
        val result = executor.execute("pause_playback", "")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Success::class.java)
        coVerify { playbackController.pause() }
    }

    @Test
    fun `resume_playback resumes playback`() = runTest {
        // When
        val result = executor.execute("resume_playback", "")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Success::class.java)
        coVerify { playbackController.resume() }
    }

    // ========== Subscribe Tests ==========

    @Test
    fun `subscribeToPodcast subscribes to new podcast`() = runTest {
        // Given
        coEvery { podcastDao.getPodcastByIndexId(123L) } returns null

        val podcastResponse = PodcastResponse(
            status = "true",
            feed = PodcastFeed(
                id = 123L,
                title = "New Podcast",
                url = "https://example.com/feed.xml",
                author = "Author",
                episodeCount = 5
            )
        )
        coEvery { podcastIndexApi.getPodcastById(123L) } returns podcastResponse
        coEvery { podcastDao.insertPodcast(any()) } returns 1L

        // When
        val result = executor.subscribeToPodcast(123L)

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Success::class.java)
        assertThat((result as McpToolExecutor.ToolResult.Success).message)
            .contains("Subscribed to New Podcast")
        coVerify { podcastDao.insertPodcast(any()) }
    }

    @Test
    fun `subscribeToPodcast returns success when already subscribed`() = runTest {
        // Given
        val existingPodcast = Podcast(
            id = 1L,
            podcastIndexId = 123L,
            title = "Existing Podcast",
            feedUrl = "https://example.com/feed.xml",
            isSubscribed = true
        )
        coEvery { podcastDao.getPodcastByIndexId(123L) } returns existingPodcast

        // When
        val result = executor.subscribeToPodcast(123L)

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Success::class.java)
        assertThat((result as McpToolExecutor.ToolResult.Success).message)
            .contains("Already subscribed")
    }

    // ========== Get Subscribed Tests ==========

    @Test
    fun `get_subscribed returns subscribed podcasts`() = runTest {
        // Given
        val podcasts = listOf(
            Podcast(
                id = 1L,
                podcastIndexId = 123L,
                title = "Subscribed Podcast",
                feedUrl = "https://example.com/feed.xml",
                isSubscribed = true
            )
        )
        coEvery { podcastDao.getSubscribedPodcasts() } returns flowOf(podcasts)

        // When
        val result = executor.execute("get_subscribed", "")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Podcasts::class.java)
        val items = (result as McpToolExecutor.ToolResult.Podcasts).items
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("Subscribed Podcast")
    }

    // ========== Unknown Tool Tests ==========

    @Test
    fun `unknown tool returns error`() = runTest {
        // When
        val result = executor.execute("unknown_tool", "{}")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Error::class.java)
        assertThat((result as McpToolExecutor.ToolResult.Error).message)
            .contains("Unknown tool")
    }

    // ========== JSON Parsing Tests ==========

    @Test
    fun `handles empty args json`() = runTest {
        // Given
        coEvery { podcastDao.getSubscribedPodcasts() } returns flowOf(emptyList())

        // When
        val result = executor.execute("get_subscribed", "")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Podcasts::class.java)
    }

    @Test
    fun `handles malformed json gracefully`() = runTest {
        // When
        val result = executor.execute("search_byterm", "not valid json")

        // Then
        assertThat(result).isInstanceOf(McpToolExecutor.ToolResult.Error::class.java)
    }
}
