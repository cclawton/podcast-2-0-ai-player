package com.podcast.app.data.remote.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    @SerialName("status") val status: String,
    @SerialName("feeds") val feeds: List<PodcastFeed> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("description") val description: String? = null
)

@Serializable
data class PodcastFeed(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("url") val url: String,
    @SerialName("link") val link: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("artwork") val artwork: String? = null,
    @SerialName("author") val author: String? = null,
    @SerialName("episodeCount") val episodeCount: Int = 0,
    @SerialName("itunesId") val itunesId: Long? = null,
    @SerialName("newestItemPubdate") val newestItemPubdate: Long? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("categories") val categories: Map<String, String>? = null,
    @SerialName("locked") val locked: Int = 0,
    @SerialName("explicit") val explicit: Boolean = false,
    @SerialName("podcastGuid") val podcastGuid: String? = null,
    @SerialName("funding") val funding: FundingInfo? = null,
    @SerialName("value") val value: ValueInfo? = null
)

@Serializable
data class PodcastResponse(
    @SerialName("status") val status: String,
    @SerialName("feed") val feed: PodcastFeed? = null,
    @SerialName("description") val description: String? = null
)

@Serializable
data class EpisodesResponse(
    @SerialName("status") val status: String,
    @SerialName("items") val items: List<EpisodeItem> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("description") val description: String? = null
)

@Serializable
data class EpisodeItem(
    @SerialName("id") val id: Long,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("datePublished") val datePublished: Long? = null,
    @SerialName("enclosureUrl") val enclosureUrl: String,
    @SerialName("enclosureType") val enclosureType: String? = null,
    @SerialName("enclosureLength") val enclosureLength: Long? = null,
    @SerialName("duration") val duration: Int? = null,
    @SerialName("explicit") val explicit: Int = 0,
    @SerialName("guid") val guid: String? = null,
    @SerialName("feedId") val feedId: Long? = null,
    @SerialName("feedImage") val feedImage: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("link") val link: String? = null,
    @SerialName("season") val season: Int? = null,
    @SerialName("episode") val episode: Int? = null,
    @SerialName("transcripts") val transcripts: List<TranscriptInfo>? = null,
    @SerialName("chapters") val chapters: ChaptersInfo? = null
)

@Serializable
data class TranscriptInfo(
    @SerialName("url") val url: String,
    @SerialName("type") val type: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("rel") val rel: String? = null
)

@Serializable
data class ChaptersInfo(
    @SerialName("url") val url: String? = null,
    @SerialName("type") val type: String? = null
)

@Serializable
data class FundingInfo(
    @SerialName("url") val url: String? = null,
    @SerialName("message") val message: String? = null
)

@Serializable
data class ValueInfo(
    @SerialName("type") val type: String? = null,
    @SerialName("method") val method: String? = null,
    @SerialName("suggested") val suggested: String? = null
)

@Serializable
data class RecentEpisodesResponse(
    @SerialName("status") val status: String,
    @SerialName("items") val items: List<EpisodeItem> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("max") val max: Int? = null,
    @SerialName("description") val description: String? = null
)
