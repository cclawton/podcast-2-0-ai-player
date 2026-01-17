package com.podcast.app.util

/**
 * Utility functions for text processing.
 */
object TextUtils {

    /**
     * Strips HTML tags from a string and cleans up the result.
     *
     * @param html The HTML string to clean
     * @return Plain text without HTML tags
     */
    fun stripHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null

        return html
            // Remove HTML tags
            .replace(Regex("<[^>]*>"), " ")
            // Decode common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            // Decode numeric HTML entities
            .replace(Regex("&#(\\d+);")) { matchResult ->
                val code = matchResult.groupValues[1].toIntOrNull()
                code?.let { Char(it).toString() } ?: matchResult.value
            }
            // Clean up whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
