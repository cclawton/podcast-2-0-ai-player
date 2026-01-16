package com.podcast.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response models for Podcast Index search endpoints.
 * Includes full Podcast 2.0 tag support.
 */

@Serializable
data class SearchResponse(
    @SerialName("status")
    val status: String,

    @SerialName("feeds")
    val feeds: List<PodcastFeed> = emptyList(),

    @SerialName("count")
    val count: Int = 0,

    @SerialName("query")
    val query: String? = null,

    @SerialName("description")
    val description: String? = null
)

@Serializable
data class PodcastFeed(
    @SerialName("id")
    val id: Long,

    @SerialName("podcastGuid")
    val podcastGuid: String? = null,

    @SerialName("title")
    val title: String,

    @SerialName("url")
    val url: String,

    @SerialName("originalUrl")
    val originalUrl: String? = null,

    @SerialName("link")
    val link: String? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("author")
    val author: String? = null,

    @SerialName("ownerName")
    val ownerName: String? = null,

    @SerialName("image")
    val image: String? = null,

    @SerialName("artwork")
    val artwork: String? = null,

    @SerialName("lastUpdateTime")
    val lastUpdateTime: Long? = null,

    @SerialName("lastCrawlTime")
    val lastCrawlTime: Long? = null,

    @SerialName("lastParseTime")
    val lastParseTime: Long? = null,

    @SerialName("lastGoodHttpStatusTime")
    val lastGoodHttpStatusTime: Long? = null,

    @SerialName("contentType")
    val contentType: String? = null,

    @SerialName("itunesId")
    val itunesId: Long? = null,

    @SerialName("generator")
    val generator: String? = null,

    @SerialName("language")
    val language: String? = null,

    @SerialName("explicit")
    val explicit: Boolean = false,

    @SerialName("type")
    val type: Int? = null,

    @SerialName("medium")
    val medium: String? = null,

    @SerialName("dead")
    val dead: Int? = null,

    @SerialName("episodeCount")
    val episodeCount: Int = 0,

    @SerialName("crawlErrors")
    val crawlErrors: Int? = null,

    @SerialName("parseErrors")
    val parseErrors: Int? = null,

    @SerialName("locked")
    val locked: Int? = null,

    @SerialName("imageUrlHash")
    val imageUrlHash: Long? = null,

    @SerialName("newestItemPubdate")
    val newestItemPubdate: Long? = null,

    // Podcast 2.0 fields
    @SerialName("categories")
    val categories: Map<String, String>? = null,

    @SerialName("funding")
    val funding: FundingInfo? = null,

    @SerialName("value")
    val value: ValueInfo? = null
)

@Serializable
data class FundingInfo(
    @SerialName("url")
    val url: String? = null,

    @SerialName("message")
    val message: String? = null
)

@Serializable
data class ValueInfo(
    @SerialName("model")
    val model: ValueModel? = null,

    @SerialName("destinations")
    val destinations: List<ValueDestination>? = null
)

@Serializable
data class ValueModel(
    @SerialName("type")
    val type: String? = null,

    @SerialName("method")
    val method: String? = null,

    @SerialName("suggested")
    val suggested: String? = null
)

@Serializable
data class ValueDestination(
    @SerialName("name")
    val name: String? = null,

    @SerialName("address")
    val address: String? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("split")
    val split: Int? = null,

    @SerialName("fee")
    val fee: Boolean? = null,

    @SerialName("customKey")
    val customKey: String? = null,

    @SerialName("customValue")
    val customValue: String? = null
)
