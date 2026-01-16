package com.podcast.app.mcp.bridge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Input validation for MCP commands.
 *
 * All inputs from MCP must be validated before use to prevent
 * injection attacks and ensure data integrity.
 */
@Singleton
class InputValidator @Inject constructor() {

    companion object {
        // ID validation: positive integers only
        private val ID_PATTERN = Regex("^[1-9][0-9]{0,18}$")

        // Search query: alphanumeric, spaces, common punctuation
        private val SEARCH_PATTERN = Regex("^[\\p{L}\\p{N}\\s\\-_.,!?'\"()]{1,200}$")

        // Speed: valid playback speeds
        private val VALID_SPEEDS = setOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

        // Time offsets: reasonable bounds
        private const val MAX_SKIP_SECONDS = 3600
        private const val MAX_POSITION_SECONDS = 86400 // 24 hours
    }

    /**
     * Validates an ID string (podcast_id, episode_id).
     */
    fun validateId(id: String?): ValidationResult {
        if (id.isNullOrBlank()) {
            return ValidationResult.Invalid("ID cannot be empty")
        }
        if (!ID_PATTERN.matches(id)) {
            return ValidationResult.Invalid("Invalid ID format")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates a search query string.
     */
    fun validateSearchQuery(query: String?): ValidationResult {
        if (query.isNullOrBlank()) {
            return ValidationResult.Invalid("Search query cannot be empty")
        }
        if (query.length > 200) {
            return ValidationResult.Invalid("Search query too long")
        }
        if (!SEARCH_PATTERN.matches(query)) {
            return ValidationResult.Invalid("Invalid characters in search query")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates playback speed.
     */
    fun validateSpeed(speed: Float?): ValidationResult {
        if (speed == null) {
            return ValidationResult.Invalid("Speed cannot be null")
        }
        if (speed !in VALID_SPEEDS) {
            return ValidationResult.Invalid("Invalid playback speed: $speed. Valid speeds: ${VALID_SPEEDS.joinToString()}")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates skip seconds (forward/backward).
     */
    fun validateSkipSeconds(seconds: Int?): ValidationResult {
        if (seconds == null) {
            return ValidationResult.Invalid("Seconds cannot be null")
        }
        if (seconds <= 0) {
            return ValidationResult.Invalid("Seconds must be positive")
        }
        if (seconds > MAX_SKIP_SECONDS) {
            return ValidationResult.Invalid("Skip seconds exceeds maximum ($MAX_SKIP_SECONDS)")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates position in seconds.
     */
    fun validatePosition(position: Int?): ValidationResult {
        if (position == null) {
            return ValidationResult.Invalid("Position cannot be null")
        }
        if (position < 0) {
            return ValidationResult.Invalid("Position cannot be negative")
        }
        if (position > MAX_POSITION_SECONDS) {
            return ValidationResult.Invalid("Position exceeds maximum ($MAX_POSITION_SECONDS)")
        }
        return ValidationResult.Valid
    }

    /**
     * Validates limit for list queries.
     */
    fun validateLimit(limit: Int?, maxAllowed: Int = 100): ValidationResult {
        if (limit == null) {
            return ValidationResult.Valid // Use default
        }
        if (limit <= 0) {
            return ValidationResult.Invalid("Limit must be positive")
        }
        if (limit > maxAllowed) {
            return ValidationResult.Invalid("Limit exceeds maximum ($maxAllowed)")
        }
        return ValidationResult.Valid
    }

    /**
     * Sanitizes a string for safe use (removes potentially dangerous characters).
     */
    fun sanitize(input: String): String {
        return input
            .replace(Regex("[<>\"']"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()

    fun isValid(): Boolean = this is Valid

    fun getErrorOrNull(): String? = (this as? Invalid)?.message
}
