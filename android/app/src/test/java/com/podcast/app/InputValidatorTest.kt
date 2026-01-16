package com.podcast.app

import com.podcast.app.mcp.bridge.InputValidator
import com.podcast.app.mcp.bridge.ValidationResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InputValidatorTest {

    private lateinit var validator: InputValidator

    @Before
    fun setup() {
        validator = InputValidator()
    }

    // ID Validation Tests

    @Test
    fun `validateId accepts valid IDs`() {
        // ID pattern: ^[1-9][0-9]{0,18}$ allows 1-19 digits, first must be non-zero
        val validIds = listOf("1", "123", "999999999", "1234567890123456789")
        validIds.forEach { id ->
            assertTrue("ID '$id' should be valid", validator.validateId(id).isValid())
        }
    }

    @Test
    fun `validateId rejects empty IDs`() {
        assertFalse(validator.validateId("").isValid())
        assertFalse(validator.validateId(null).isValid())
        assertFalse(validator.validateId("   ").isValid())
    }

    @Test
    fun `validateId rejects invalid formats`() {
        val invalidIds = listOf("0", "-1", "abc", "12.34", "12 34", "12-34")
        invalidIds.forEach { id ->
            assertFalse("ID '$id' should be invalid", validator.validateId(id).isValid())
        }
    }

    // Search Query Validation Tests

    @Test
    fun `validateSearchQuery accepts valid queries`() {
        val validQueries = listOf(
            "technology",
            "AI podcast",
            "True Crime Stories",
            "What's new?",
            "Hello, World!"
        )
        validQueries.forEach { query ->
            assertTrue("Query '$query' should be valid", validator.validateSearchQuery(query).isValid())
        }
    }

    @Test
    fun `validateSearchQuery rejects empty queries`() {
        assertFalse(validator.validateSearchQuery("").isValid())
        assertFalse(validator.validateSearchQuery(null).isValid())
    }

    @Test
    fun `validateSearchQuery rejects too long queries`() {
        val longQuery = "a".repeat(201)
        assertFalse(validator.validateSearchQuery(longQuery).isValid())
    }

    // Speed Validation Tests

    @Test
    fun `validateSpeed accepts valid speeds`() {
        val validSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        validSpeeds.forEach { speed ->
            assertTrue("Speed $speed should be valid", validator.validateSpeed(speed).isValid())
        }
    }

    @Test
    fun `validateSpeed rejects invalid speeds`() {
        val invalidSpeeds = listOf(0.0f, 0.3f, 1.3f, 3.5f, 4.0f, -1.0f)
        invalidSpeeds.forEach { speed ->
            assertFalse("Speed $speed should be invalid", validator.validateSpeed(speed).isValid())
        }
    }

    @Test
    fun `validateSpeed rejects null`() {
        assertFalse(validator.validateSpeed(null).isValid())
    }

    // Skip Seconds Validation Tests

    @Test
    fun `validateSkipSeconds accepts valid values`() {
        val validValues = listOf(1, 15, 30, 60, 3600)
        validValues.forEach { seconds ->
            assertTrue("Seconds $seconds should be valid", validator.validateSkipSeconds(seconds).isValid())
        }
    }

    @Test
    fun `validateSkipSeconds rejects invalid values`() {
        val invalidValues = listOf(0, -1, -15, 3601, 7200)
        invalidValues.forEach { seconds ->
            assertFalse("Seconds $seconds should be invalid", validator.validateSkipSeconds(seconds).isValid())
        }
    }

    // Position Validation Tests

    @Test
    fun `validatePosition accepts valid positions`() {
        val validPositions = listOf(0, 1, 3600, 86400)
        validPositions.forEach { position ->
            assertTrue("Position $position should be valid", validator.validatePosition(position).isValid())
        }
    }

    @Test
    fun `validatePosition rejects invalid positions`() {
        assertFalse(validator.validatePosition(-1).isValid())
        assertFalse(validator.validatePosition(86401).isValid())
        assertFalse(validator.validatePosition(null).isValid())
    }

    // Limit Validation Tests

    @Test
    fun `validateLimit accepts valid limits`() {
        assertTrue(validator.validateLimit(1).isValid())
        assertTrue(validator.validateLimit(50).isValid())
        assertTrue(validator.validateLimit(100).isValid())
        assertTrue(validator.validateLimit(null).isValid()) // null uses default
    }

    @Test
    fun `validateLimit rejects invalid limits`() {
        assertFalse(validator.validateLimit(0).isValid())
        assertFalse(validator.validateLimit(-1).isValid())
        assertFalse(validator.validateLimit(101).isValid())
    }

    // Sanitize Tests

    @Test
    fun `sanitize removes dangerous characters`() {
        val input = "<script>alert('xss')</script>"
        val sanitized = validator.sanitize(input)

        assertFalse(sanitized.contains("<"))
        assertFalse(sanitized.contains(">"))
        assertFalse(sanitized.contains("'"))
    }

    @Test
    fun `sanitize trims and limits length`() {
        val input = "  hello world  "
        val sanitized = validator.sanitize(input)

        assertTrue(sanitized == "hello world")
    }

    @Test
    fun `sanitize handles very long input`() {
        val longInput = "a".repeat(1000)
        val sanitized = validator.sanitize(longInput)

        assertTrue(sanitized.length <= 500)
    }

    // Error Messages Tests

    @Test
    fun `validation returns proper error messages`() {
        val result = validator.validateId("")
        assertTrue(result is ValidationResult.Invalid)
        assertNull((result as? ValidationResult.Valid))

        val errorMessage = (result as ValidationResult.Invalid).message
        assertTrue(errorMessage.isNotEmpty())
    }
}
