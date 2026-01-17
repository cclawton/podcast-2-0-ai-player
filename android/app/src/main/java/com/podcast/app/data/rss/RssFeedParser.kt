package com.podcast.app.data.rss

import android.util.Xml
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parser for RSS 2.0 podcast feeds.
 *
 * Supports:
 * - Standard RSS 2.0 elements
 * - iTunes podcast namespace extensions
 * - Podcast 2.0 namespace extensions
 */
@Singleton
class RssFeedParser @Inject constructor() {

    data class ParsedFeed(
        val podcast: Podcast,
        val episodes: List<Episode>
    )

    /**
     * Parse an RSS feed from an input stream.
     *
     * @param inputStream The RSS feed XML as an input stream
     * @param feedUrl The URL of the feed (used for the Podcast entity)
     * @return ParsedFeed containing the podcast and its episodes
     * @throws RssParseException if the feed cannot be parsed
     */
    @Throws(RssParseException::class)
    fun parse(inputStream: InputStream, feedUrl: String): ParsedFeed {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)
            parser.nextTag()

            return readRss(parser, feedUrl)
        } catch (e: XmlPullParserException) {
            throw RssParseException("Invalid RSS feed format: ${e.message}", e)
        } catch (e: IOException) {
            throw RssParseException("Error reading feed: ${e.message}", e)
        }
    }

    private fun readRss(parser: XmlPullParser, feedUrl: String): ParsedFeed {
        parser.require(XmlPullParser.START_TAG, null, "rss")

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            if (parser.name == "channel") {
                return readChannel(parser, feedUrl)
            } else {
                skip(parser)
            }
        }

        throw RssParseException("No channel element found in RSS feed")
    }

    private fun readChannel(parser: XmlPullParser, feedUrl: String): ParsedFeed {
        parser.require(XmlPullParser.START_TAG, null, "channel")

        var title = ""
        var description: String? = null
        var imageUrl: String? = null
        var language = "en"
        var author: String? = null
        var link: String? = null
        var explicit = false
        var category: String? = null
        val episodes = mutableListOf<Episode>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "title" -> title = readText(parser, "title")
                "description" -> description = readText(parser, "description")
                "link" -> link = readText(parser, "link")
                "language" -> language = readText(parser, "language")
                "itunes:author", "author" -> author = readText(parser, parser.name)
                "itunes:explicit" -> explicit = readText(parser, "itunes:explicit").let {
                    it.equals("yes", ignoreCase = true) || it.equals("true", ignoreCase = true)
                }
                "itunes:category" -> {
                    category = parser.getAttributeValue(null, "text")
                    skip(parser)
                }
                "itunes:image" -> {
                    imageUrl = parser.getAttributeValue(null, "href")
                    skip(parser)
                }
                "image" -> {
                    imageUrl = readImageUrl(parser)
                }
                "item" -> {
                    val episode = readItem(parser, 0) // podcastId will be set later
                    episodes.add(episode)
                }
                else -> skip(parser)
            }
        }

        if (title.isBlank()) {
            throw RssParseException("Feed must have a title")
        }

        // Generate a unique ID for manual RSS feeds
        val podcastIndexId = feedUrl.hashCode().toLong().let { if (it < 0) -it else it }

        val podcast = Podcast(
            podcastIndexId = podcastIndexId,
            title = title,
            feedUrl = feedUrl,
            description = description,
            imageUrl = imageUrl,
            language = language,
            author = author,
            websiteUrl = link,
            explicit = explicit,
            category = category,
            episodeCount = episodes.size,
            isSubscribed = true
        )

        return ParsedFeed(podcast, episodes)
    }

    private fun readItem(parser: XmlPullParser, podcastId: Long): Episode {
        parser.require(XmlPullParser.START_TAG, null, "item")

        var title = ""
        var description: String? = null
        var audioUrl: String? = null
        var audioDuration = 0
        var audioSize: Long? = null
        var audioType: String? = null
        var pubDate: Long = System.currentTimeMillis()
        var guid: String? = null
        var link: String? = null
        var imageUrl: String? = null
        var explicit = false
        var seasonNumber: Int? = null
        var episodeNumber: Int? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "title" -> title = readText(parser, "title")
                "description", "itunes:summary" -> {
                    if (description == null) description = readText(parser, parser.name)
                    else skip(parser)
                }
                "enclosure" -> {
                    audioUrl = parser.getAttributeValue(null, "url")
                    audioType = parser.getAttributeValue(null, "type")
                    audioSize = parser.getAttributeValue(null, "length")?.toLongOrNull()
                    skip(parser)
                }
                "pubDate" -> pubDate = parseDate(readText(parser, "pubDate"))
                "guid" -> guid = readText(parser, "guid")
                "link" -> link = readText(parser, "link")
                "itunes:image" -> {
                    imageUrl = parser.getAttributeValue(null, "href")
                    skip(parser)
                }
                "itunes:duration" -> audioDuration = parseDuration(readText(parser, "itunes:duration"))
                "itunes:explicit" -> explicit = readText(parser, "itunes:explicit").let {
                    it.equals("yes", ignoreCase = true) || it.equals("true", ignoreCase = true)
                }
                "itunes:season" -> seasonNumber = readText(parser, "itunes:season").toIntOrNull()
                "itunes:episode" -> episodeNumber = readText(parser, "itunes:episode").toIntOrNull()
                else -> skip(parser)
            }
        }

        // Generate a unique episode ID from guid or URL
        val episodeIndexId = (guid ?: audioUrl ?: title).hashCode().toLong().let {
            if (it < 0) -it else it
        }

        return Episode(
            episodeIndexId = episodeIndexId,
            podcastId = podcastId,
            title = title,
            description = description,
            audioUrl = audioUrl ?: "",
            audioDuration = audioDuration,
            audioSize = audioSize,
            audioType = audioType ?: "audio/mpeg",
            publishedAt = pubDate,
            episodeGuid = guid,
            link = link,
            imageUrl = imageUrl,
            explicit = explicit,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    private fun readImageUrl(parser: XmlPullParser): String? {
        parser.require(XmlPullParser.START_TAG, null, "image")
        var url: String? = null

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            if (parser.name == "url") {
                url = readText(parser, "url")
            } else {
                skip(parser)
            }
        }

        return url
    }

    private fun readText(parser: XmlPullParser, tagName: String): String {
        parser.require(XmlPullParser.START_TAG, null, tagName)
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text.trim()
            parser.nextTag()
        }
        parser.require(XmlPullParser.END_TAG, null, tagName)
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun parseDate(dateString: String): Long {
        val formats = listOf(
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        )

        for (format in formats) {
            try {
                return format.parse(dateString)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                // Try next format
            }
        }

        return System.currentTimeMillis()
    }

    private fun parseDuration(durationString: String): Int {
        // Handle formats: "HH:MM:SS", "MM:SS", or seconds
        return try {
            val parts = durationString.split(":")
            when (parts.size) {
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                1 -> parts[0].toInt()
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}

/**
 * Exception thrown when an RSS feed cannot be parsed.
 */
class RssParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
