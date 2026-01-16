package com.podcast.app.voice

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy string matching utility for podcast and episode names.
 *
 * Provides fuzzy matching capabilities to handle voice recognition
 * inaccuracies, misspellings, and partial matches when users
 * request podcasts or episodes by name.
 *
 * Uses multiple algorithms:
 * - Levenshtein distance for typo tolerance
 * - Jaro-Winkler for prefix matching
 * - Token-based matching for word order independence
 * - Phonetic similarity (simple soundex-like)
 */
@Singleton
class FuzzyMatcher @Inject constructor() {

    companion object {
        // Match quality thresholds
        private const val EXACT_MATCH_SCORE = 1.0f
        private const val HIGH_MATCH_THRESHOLD = 0.85f
        private const val MEDIUM_MATCH_THRESHOLD = 0.70f
        private const val LOW_MATCH_THRESHOLD = 0.55f

        // Common words to ignore in fuzzy matching
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "need",
            "podcast", "episode", "show", "series"
        )
    }

    /**
     * Result of a fuzzy match operation.
     */
    data class MatchResult<T>(
        val item: T,
        val score: Float,
        val matchType: MatchType
    ) {
        fun isHighQuality(): Boolean = score >= HIGH_MATCH_THRESHOLD
        fun isMediumQuality(): Boolean = score >= MEDIUM_MATCH_THRESHOLD
        fun isAcceptable(): Boolean = score >= LOW_MATCH_THRESHOLD
    }

    enum class MatchType {
        EXACT,           // Exact string match
        NORMALIZED,      // Exact after normalization
        FUZZY_HIGH,      // High confidence fuzzy match
        FUZZY_MEDIUM,    // Medium confidence fuzzy match
        FUZZY_LOW,       // Low confidence fuzzy match
        TOKEN,           // Token/word-based match
        NO_MATCH         // No acceptable match found
    }

    /**
     * Find the best matching item from a list.
     *
     * @param query The search query (e.g., voice-recognized podcast name)
     * @param items The items to search through
     * @param selector Function to extract the searchable string from each item
     * @return The best matching result, or null if no acceptable match found
     */
    fun <T> findBestMatch(
        query: String,
        items: List<T>,
        selector: (T) -> String
    ): MatchResult<T>? {
        if (query.isBlank() || items.isEmpty()) return null

        val normalizedQuery = normalize(query)
        var bestMatch: MatchResult<T>? = null

        for (item in items) {
            val itemName = selector(item)
            val result = calculateMatchScore(normalizedQuery, itemName, item)

            if (bestMatch == null || result.score > bestMatch.score) {
                bestMatch = result
            }
        }

        return bestMatch?.takeIf { it.isAcceptable() }
    }

    /**
     * Find all items matching a query above a threshold.
     *
     * @param query The search query
     * @param items The items to search through
     * @param selector Function to extract the searchable string from each item
     * @param minScore Minimum match score to include
     * @param limit Maximum number of results to return
     * @return List of matches sorted by score descending
     */
    fun <T> findMatches(
        query: String,
        items: List<T>,
        selector: (T) -> String,
        minScore: Float = LOW_MATCH_THRESHOLD,
        limit: Int = 10
    ): List<MatchResult<T>> {
        if (query.isBlank() || items.isEmpty()) return emptyList()

        val normalizedQuery = normalize(query)

        return items
            .map { item -> calculateMatchScore(normalizedQuery, selector(item), item) }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Calculate the match score between a query and a candidate string.
     */
    private fun <T> calculateMatchScore(
        normalizedQuery: String,
        candidateName: String,
        item: T
    ): MatchResult<T> {
        val normalizedCandidate = normalize(candidateName)

        // Check for exact match
        if (normalizedQuery == normalizedCandidate) {
            return MatchResult(item, EXACT_MATCH_SCORE, MatchType.EXACT)
        }

        // Check for substring match
        if (normalizedCandidate.contains(normalizedQuery)) {
            val lengthRatio = normalizedQuery.length.toFloat() / normalizedCandidate.length
            val score = 0.85f + (lengthRatio * 0.15f)
            return MatchResult(item, score, MatchType.NORMALIZED)
        }

        // Calculate various similarity scores
        val levenshteinScore = calculateLevenshteinSimilarity(normalizedQuery, normalizedCandidate)
        val jaroWinklerScore = calculateJaroWinklerSimilarity(normalizedQuery, normalizedCandidate)
        val tokenScore = calculateTokenSimilarity(normalizedQuery, normalizedCandidate)

        // Weighted combination of scores
        val combinedScore = (levenshteinScore * 0.3f) +
                (jaroWinklerScore * 0.4f) +
                (tokenScore * 0.3f)

        val matchType = when {
            combinedScore >= HIGH_MATCH_THRESHOLD -> MatchType.FUZZY_HIGH
            combinedScore >= MEDIUM_MATCH_THRESHOLD -> MatchType.FUZZY_MEDIUM
            combinedScore >= LOW_MATCH_THRESHOLD -> MatchType.FUZZY_LOW
            else -> MatchType.NO_MATCH
        }

        return MatchResult(item, combinedScore, matchType)
    }

    /**
     * Normalize a string for matching.
     */
    private fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Calculate Levenshtein distance based similarity (0.0 to 1.0).
     */
    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) {
            return if (s1 == s2) 1.0f else 0.0f
        }

        val distance = levenshteinDistance(s1, s2)
        val maxLength = max(s1.length, s2.length)

        return (1.0f - (distance.toFloat() / maxLength)).coerceIn(0.0f, 1.0f)
    }

    /**
     * Calculate Levenshtein edit distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Use single array optimization for space efficiency
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // Deletion
                    curr[j - 1] + 1,  // Insertion
                    prev[j - 1] + cost // Substitution
                )
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }

        return curr[n]
    }

    /**
     * Calculate Jaro-Winkler similarity (0.0 to 1.0).
     * Favors strings that match from the beginning.
     */
    private fun calculateJaroWinklerSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val jaro = calculateJaroSimilarity(s1, s2)

        // Calculate common prefix length (max 4 characters)
        var prefixLength = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        while (prefixLength < maxPrefix && s1[prefixLength] == s2[prefixLength]) {
            prefixLength++
        }

        // Jaro-Winkler uses scaling factor p = 0.1
        return jaro + (prefixLength * 0.1f * (1 - jaro))
    }

    /**
     * Calculate Jaro similarity (0.0 to 1.0).
     */
    private fun calculateJaroSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f

        val len1 = s1.length
        val len2 = s2.length

        val matchWindow = (max(len1, len2) / 2) - 1
        if (matchWindow < 0) return 0.0f

        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        var transpositions = 0

        // Find matching characters
        for (i in 0 until len1) {
            val start = max(0, i - matchWindow)
            val end = min(i + matchWindow + 1, len2)

            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0f

        // Count transpositions
        var j = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[j]) j++
            if (s1[i] != s2[j]) transpositions++
            j++
        }

        val m = matches.toFloat()
        return ((m / len1) + (m / len2) + ((m - transpositions / 2f) / m)) / 3f
    }

    /**
     * Calculate token-based similarity using word overlap.
     */
    private fun calculateTokenSimilarity(s1: String, s2: String): Float {
        val tokens1 = s1.split(" ")
            .filter { it.isNotBlank() && it !in STOP_WORDS }
            .toSet()

        val tokens2 = s2.split(" ")
            .filter { it.isNotBlank() && it !in STOP_WORDS }
            .toSet()

        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0f
        }

        // Calculate Jaccard similarity with fuzzy token matching
        var matchCount = 0f
        for (t1 in tokens1) {
            val bestTokenMatch = tokens2.maxOfOrNull { t2 ->
                calculateLevenshteinSimilarity(t1, t2)
            } ?: 0f
            if (bestTokenMatch >= 0.8f) {
                matchCount += bestTokenMatch
            }
        }

        val unionSize = tokens1.size + tokens2.size - matchCount
        return if (unionSize > 0) matchCount / unionSize else 0f
    }

    /**
     * Check if two strings are phonetically similar (simplified).
     * Useful for voice recognition errors.
     */
    fun arePhoneticallySimlar(s1: String, s2: String): Boolean {
        val p1 = toPhoneticCode(s1)
        val p2 = toPhoneticCode(s2)
        return p1 == p2 || calculateLevenshteinSimilarity(p1, p2) > 0.8f
    }

    /**
     * Generate a simple phonetic code for a string.
     * Based on simplified Soundex principles.
     */
    private fun toPhoneticCode(text: String): String {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return ""

        return buildString {
            append(normalized[0]) // Keep first letter

            var lastCode = getPhoneticCode(normalized[0])

            for (i in 1 until normalized.length) {
                val c = normalized[i]
                if (c == ' ') {
                    lastCode = '0'
                    continue
                }

                val code = getPhoneticCode(c)
                if (code != '0' && code != lastCode) {
                    append(code)
                    lastCode = code
                }
            }
        }.take(6)
    }

    /**
     * Get phonetic code for a character.
     */
    private fun getPhoneticCode(c: Char): Char = when (c) {
        'b', 'f', 'p', 'v' -> '1'
        'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'
        'd', 't' -> '3'
        'l' -> '4'
        'm', 'n' -> '5'
        'r' -> '6'
        else -> '0'
    }
}
