package com.podcast.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response models for Podcast Index episode endpoints.
 * Includes full Podcast 2.0 tag support for chapters, transcripts, value, etc.
 */

@Serializable
data class EpisodesResponse(
    @SerialName("status")
    val status: String,

    @SerialName("items")
    val items: List<Episode> = emptyList(),

    @SerialName("count")
    val count: Int = 0,

    @SerialName("query")
    val query: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("liveItems")
    val liveItems: List<Episode>? = null
)

@Serializable
data class SingleEpisodeResponse(
    @SerialName("status")
    val status: String,

    @SerialName("episode")
    val episode: Episode? = null,

    @SerialName("id")
    val id: Long? = null,

    @SerialName("description")
    val description: String? = null
)

@Serializable
data class RecentEpisodesResponse(
    @SerialName("status")
    val status: String,

    @SerialName("items")
    val items: List<Episode> = emptyList(),

    @SerialName("count")
    val count: Int = 0,

    @SerialName("max")
    val max: Int? = null,

    @SerialName("description")
    val description: String? = null
)

@Serializable
data class Episode(
    @SerialName("id")
    val id: Long,

    @SerialName("title")
    val title: String,

    @SerialName("link")
    val link: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("guid")
    val guid: String? = null,

    @SerialName("datePublished")
    val datePublished: Long? = null,

    @SerialName("datePublishedPretty")
    val datePublishedPretty: String? = null,

    @SerialName("dateCrawled")
    val dateCrawled: Long? = null,

    @SerialName("enclosureUrl")
    val enclosureUrl: String? = null,

    @SerialName("enclosureType")
    val enclosureType: String? = null,

    @SerialName("enclosureLength")
    val enclosureLength: Long? = null,

    @SerialName("duration")
    val duration: Int? = null,

    @SerialName("explicit")
    val explicit: Int = 0,

    @SerialName("episode")
    val episodeNumber: Int? = null,

    @SerialName("episodeType")
    val episodeType: String? = null,

    @SerialName("season")
    val season: Int? = null,

    @SerialName("image")
    val image: String? = null,

    @SerialName("feedItunesId")
    val feedItunesId: Long? = null,

    @SerialName("feedImage")
    val feedImage: String? = null,

    @SerialName("feedId")
    val feedId: Long? = null,

    @SerialName("feedTitle")
    val feedTitle: String? = null,

    @SerialName("feedLanguage")
    val feedLanguage: String? = null,

    @SerialName("feedUrl")
    val feedUrl: String? = null,

    @SerialName("feedAuthor")
    val feedAuthor: String? = null,

    @SerialName("feedDead")
    val feedDead: Int? = null,

    @SerialName("feedDuplicateOf")
    val feedDuplicateOf: Long? = null,

    // Podcast 2.0 fields
    @SerialName("chaptersUrl")
    val chaptersUrl: String? = null,

    @SerialName("chapters")
    val chapters: ChaptersData? = null,

    @SerialName("transcriptUrl")
    val transcriptUrl: String? = null,

    @SerialName("transcripts")
    val transcripts: List<Transcript>? = null,

    @SerialName("soundbite")
    val soundbite: Soundbite? = null,

    @SerialName("soundbites")
    val soundbites: List<Soundbite>? = null,

    @SerialName("persons")
    val persons: List<Person>? = null,

    @SerialName("socialInteract")
    val socialInteract: List<SocialInteract>? = null,

    @SerialName("value")
    val value: ValueInfo? = null
)

@Serializable
data class ChaptersData(
    @SerialName("version")
    val version: String? = null,

    @SerialName("chapters")
    val chapters: List<Chapter> = emptyList()
)

@Serializable
data class Chapter(
    @SerialName("startTime")
    val startTime: Double = 0.0,

    @SerialName("title")
    val title: String? = null,

    @SerialName("img")
    val img: String? = null,

    @SerialName("url")
    val url: String? = null,

    @SerialName("toc")
    val toc: Boolean? = null,

    @SerialName("endTime")
    val endTime: Double? = null,

    @SerialName("location")
    val location: LocationInfo? = null
)

@Serializable
data class Transcript(
    @SerialName("url")
    val url: String,

    @SerialName("type")
    val type: String? = null,

    @SerialName("language")
    val language: String? = null,

    @SerialName("rel")
    val rel: String? = null
) {
    companion object {
        const val TYPE_SRT = "application/x-subrip"
        const val TYPE_VTT = "text/vtt"
        const val TYPE_JSON = "application/json"
        const val TYPE_HTML = "text/html"
        const val TYPE_PLAIN = "text/plain"

        const val REL_CAPTIONS = "captions"
        const val REL_TRANSCRIPT = "transcript"
    }
}

@Serializable
data class Soundbite(
    @SerialName("startTime")
    val startTime: Double = 0.0,

    @SerialName("duration")
    val duration: Double = 0.0,

    @SerialName("title")
    val title: String? = null
)

@Serializable
data class SocialInteract(
    @SerialName("uri")
    val uri: String? = null,

    @SerialName("protocol")
    val protocol: String? = null,

    @SerialName("accountId")
    val accountId: String? = null,

    @SerialName("accountUrl")
    val accountUrl: String? = null,

    @SerialName("priority")
    val priority: Int? = null
)
