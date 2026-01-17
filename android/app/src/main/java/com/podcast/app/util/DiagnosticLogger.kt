package com.podcast.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * In-app diagnostic logger for troubleshooting on physical devices.
 *
 * Captures logs in memory that can be viewed and copied from within the app,
 * which is essential for debugging on GrapheneOS devices without ADB access.
 *
 * PRIVACY: Never logs actual API keys, secrets, or sensitive user data.
 * Only logs diagnostic metadata (lengths, presence, error types).
 */
object DiagnosticLogger {

    private const val MAX_ENTRIES = 500
    private const val TAG = "DiagnosticLogger"

    enum class Level(val prefix: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val formattedTime: String = formatTimestamp(timestamp)
    ) {
        companion object {
            private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

            fun formatTimestamp(timestamp: Long): String {
                return dateFormat.format(Date(timestamp))
            }
        }

        override fun toString(): String {
            return "$formattedTime ${level.prefix}/$tag: $message"
        }
    }

    private val entries = ConcurrentLinkedDeque<LogEntry>()
    private val _logFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logFlow: StateFlow<List<LogEntry>> = _logFlow.asStateFlow()

    private fun addEntry(level: Level, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        entries.addFirst(entry)

        // Trim to max size
        while (entries.size > MAX_ENTRIES) {
            entries.removeLast()
        }

        // Update flow
        _logFlow.value = entries.toList()

        // Also log to system log for ADB when available
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }
    }

    fun d(tag: String, message: String) = addEntry(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = addEntry(Level.INFO, tag, message)
    fun w(tag: String, message: String) = addEntry(Level.WARN, tag, message)
    fun e(tag: String, message: String) = addEntry(Level.ERROR, tag, message)

    /**
     * Log an exception with its class name and message.
     * Does NOT log stack traces to avoid exposing internal paths.
     */
    fun e(tag: String, message: String, exception: Throwable) {
        addEntry(Level.ERROR, tag, "$message: ${exception.javaClass.simpleName} - ${exception.message}")
    }

    /**
     * Clear all log entries.
     */
    fun clear() {
        entries.clear()
        _logFlow.value = emptyList()
    }

    /**
     * Get all logs as a single string for copying/sharing.
     */
    fun getLogsAsText(): String {
        val header = buildString {
            appendLine("=== Podcast App Diagnostic Log ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Entries: ${entries.size}")
            appendLine("==================================")
            appendLine()
        }

        return header + entries.reversed().joinToString("\n") { it.toString() }
    }

    /**
     * Get filtered logs by minimum level.
     */
    fun getFilteredLogs(minLevel: Level): List<LogEntry> {
        val minOrdinal = minLevel.ordinal
        return entries.filter { it.level.ordinal >= minOrdinal }.toList()
    }

    /**
     * Get logs for a specific tag.
     */
    fun getLogsByTag(tag: String): List<LogEntry> {
        return entries.filter { it.tag == tag }.toList()
    }

    /**
     * Get error and warning logs only.
     */
    fun getErrorsAndWarnings(): List<LogEntry> {
        return entries.filter { it.level == Level.ERROR || it.level == Level.WARN }.toList()
    }

    /**
     * Get the count of entries by level.
     */
    fun getStats(): Map<Level, Int> {
        return entries.groupingBy { it.level }.eachCount()
    }
}
