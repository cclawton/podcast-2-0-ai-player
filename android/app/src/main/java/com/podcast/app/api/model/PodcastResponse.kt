package com.podcast.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response models for Podcast Index podcast metadata endpoints.
 * GET /podcasts/byfeedid, /podcasts/byfeedurl, etc.
 */

@Serializable
data class PodcastResponse(
    @SerialName("status")
    val status: String,

    @SerialName("feed")
    val feed: PodcastDetail? = null,

    @SerialName("description")
    val description: String? = null,

    @SerialName("query")
    val query: QueryInfo? = null
)

@Serializable
data class QueryInfo(
    @SerialName("id")
    val id: String? = null,

    @SerialName("url")
    val url: String? = null
)

@Serializable
data class PodcastDetail(
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

    @SerialName("inPollingQueue")
    val inPollingQueue: Int? = null,

    @SerialName("priority")
    val priority: Int? = null,

    @SerialName("lastGoodHttpStatusTime")
    val lastGoodHttpStatusTime: Long? = null,

    @SerialName("lastHttpStatus")
    val lastHttpStatus: Int? = null,

    @SerialName("contentType")
    val contentType: String? = null,

    @SerialName("itunesId")
    val itunesId: Long? = null,

    @SerialName("itunesType")
    val itunesType: String? = null,

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
    val dead: Int = 0,

    @SerialName("chash")
    val chash: String? = null,

    @SerialName("episodeCount")
    val episodeCount: Int = 0,

    @SerialName("crawlErrors")
    val crawlErrors: Int = 0,

    @SerialName("parseErrors")
    val parseErrors: Int = 0,

    @SerialName("locked")
    val locked: Int = 0,

    @SerialName("imageUrlHash")
    val imageUrlHash: Long? = null,

    @SerialName("newestItemPubdate")
    val newestItemPubdate: Long? = null,

    @SerialName("newestEnclosureUrl")
    val newestEnclosureUrl: String? = null,

    @SerialName("newestEnclosureDuration")
    val newestEnclosureDuration: Int? = null,

    // Podcast 2.0 fields
    @SerialName("categories")
    val categories: Map<String, String>? = null,

    @SerialName("funding")
    val funding: FundingInfo? = null,

    @SerialName("value")
    val value: ValueInfo? = null,

    @SerialName("persons")
    val persons: List<Person>? = null,

    @SerialName("location")
    val location: LocationInfo? = null,

    @SerialName("license")
    val license: LicenseInfo? = null,

    @SerialName("podcastPerson")
    val podcastPerson: String? = null,

    @SerialName("podcastSocial")
    val podcastSocial: List<SocialInfo>? = null,

    @SerialName("podcastTxt")
    val podcastTxt: List<TxtRecord>? = null
)

@Serializable
data class Person(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("name")
    val name: String,

    @SerialName("role")
    val role: String? = null,

    @SerialName("group")
    val group: String? = null,

    @SerialName("img")
    val img: String? = null,

    @SerialName("href")
    val href: String? = null
)

@Serializable
data class LocationInfo(
    @SerialName("name")
    val name: String? = null,

    @SerialName("geo")
    val geo: String? = null,

    @SerialName("osm")
    val osm: String? = null
)

@Serializable
data class LicenseInfo(
    @SerialName("url")
    val url: String? = null,

    @SerialName("type")
    val type: String? = null
)

@Serializable
data class SocialInfo(
    @SerialName("platform")
    val platform: String? = null,

    @SerialName("url")
    val url: String? = null,

    @SerialName("id")
    val id: String? = null,

    @SerialName("priority")
    val priority: Int? = null,

    @SerialName("signUp")
    val signUp: Boolean? = null
)

@Serializable
data class TxtRecord(
    @SerialName("purpose")
    val purpose: String? = null,

    @SerialName("txt")
    val txt: String? = null
)
