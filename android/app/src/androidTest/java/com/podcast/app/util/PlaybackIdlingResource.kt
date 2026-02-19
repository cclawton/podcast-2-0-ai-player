package com.podcast.app.util

import androidx.test.espresso.IdlingResource
import com.podcast.app.playback.IPlaybackController
import com.podcast.app.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Espresso IdlingResource for playback operations.
 *
 * This idling resource can be used to wait for playback state transitions:
 * - Wait for playback to start (isPlaying = true)
 * - Wait for playback to stop (isPlaying = false)
 * - Wait for player to be ready (playerState = READY)
 * - Wait for buffering to complete
 *
 * Usage:
 * ```kotlin
 * val idlingResource = PlaybackIdlingResource(playbackController)
 * IdlingRegistry.getInstance().register(idlingResource)
 *
 * // Wait for playback to start
 * idlingResource.waitForPlaying()
 *
 * // ... perform test actions ...
 *
 * IdlingRegistry.getInstance().unregister(idlingResource)
 * ```
 *
 * This is particularly useful for background playback tests where
 * state transitions may be asynchronous.
 */
class PlaybackIdlingResource(
    private val playbackController: IPlaybackController,
    private val name: String = "PlaybackIdlingResource"
) : IdlingResource {

    private var resourceCallback: IdlingResource.ResourceCallback? = null
    private val isIdle = AtomicBoolean(true)

    // Condition to check for idleness
    private var idleCondition: ((PlaybackIdleState) -> Boolean)? = null

    private var collectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    /**
     * Data class representing the current playback state for idle checking.
     */
    data class PlaybackIdleState(
        val isPlaying: Boolean,
        val playerState: PlayerState,
        val positionMs: Long,
        val durationMs: Long
    )

    override fun getName(): String = name

    override fun isIdleNow(): Boolean = isIdle.get()

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        resourceCallback = callback
    }

    /**
     * Start monitoring playback state and become idle when playing.
     */
    fun waitForPlaying() {
        setIdleCondition { state -> state.isPlaying }
    }

    /**
     * Start monitoring playback state and become idle when paused/stopped.
     */
    fun waitForPaused() {
        setIdleCondition { state -> !state.isPlaying }
    }

    /**
     * Start monitoring playback state and become idle when player is ready.
     */
    fun waitForReady() {
        setIdleCondition { state -> state.playerState == PlayerState.READY }
    }

    /**
     * Start monitoring playback state and become idle when buffering completes.
     */
    fun waitForBufferingComplete() {
        setIdleCondition { state -> state.playerState != PlayerState.BUFFERING }
    }

    /**
     * Wait for position to reach a specific time (within tolerance).
     */
    fun waitForPosition(targetMs: Long, toleranceMs: Long = 1000L) {
        setIdleCondition { state ->
            kotlin.math.abs(state.positionMs - targetMs) <= toleranceMs
        }
    }

    /**
     * Wait for a custom condition to be met.
     */
    fun waitFor(condition: (PlaybackIdleState) -> Boolean) {
        setIdleCondition(condition)
    }

    /**
     * Reset the idling resource to idle state.
     * Call this before setting a new condition.
     */
    fun reset() {
        collectionJob?.cancel()
        collectionJob = null
        idleCondition = null
        setIdle(true)
    }

    private fun setIdleCondition(condition: (PlaybackIdleState) -> Boolean) {
        reset()
        idleCondition = condition
        setIdle(false)
        startMonitoring()
    }

    private fun startMonitoring() {
        collectionJob = scope.launch {
            playbackController.playbackState.collectLatest { state ->
                val currentState = PlaybackIdleState(
                    isPlaying = state.isPlaying,
                    playerState = state.playerState,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs
                )

                idleCondition?.let { condition ->
                    if (condition(currentState)) {
                        setIdle(true)
                    }
                }
            }
        }
    }

    private fun setIdle(idle: Boolean) {
        val wasIdle = isIdle.getAndSet(idle)
        if (!wasIdle && idle) {
            resourceCallback?.onTransitionToIdle()
        }
    }

    /**
     * Clean up resources. Call when done using the idling resource.
     */
    fun cleanup() {
        reset()
    }
}

/**
 * Simple countdown idling resource for timed waits.
 *
 * Usage:
 * ```kotlin
 * val countdown = CountdownIdlingResource("wait5s", 5000L)
 * IdlingRegistry.getInstance().register(countdown)
 * countdown.start()
 * // ... test will wait until countdown completes ...
 * IdlingRegistry.getInstance().unregister(countdown)
 * ```
 */
class CountdownIdlingResource(
    private val name: String,
    private val durationMs: Long
) : IdlingResource {

    private var resourceCallback: IdlingResource.ResourceCallback? = null
    private val isIdle = AtomicBoolean(true)
    private var startTime: Long = 0

    override fun getName(): String = name

    override fun isIdleNow(): Boolean {
        if (startTime == 0L) return true

        val elapsed = System.currentTimeMillis() - startTime
        val idle = elapsed >= durationMs

        if (idle && !isIdle.getAndSet(true)) {
            resourceCallback?.onTransitionToIdle()
        }

        return idle
    }

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        resourceCallback = callback
    }

    /**
     * Start the countdown. The resource becomes idle after durationMs.
     */
    fun start() {
        isIdle.set(false)
        startTime = System.currentTimeMillis()
    }

    /**
     * Reset the countdown.
     */
    fun reset() {
        startTime = 0
        isIdle.set(true)
    }
}

/**
 * IdlingResource that waits for a specific number of state transitions.
 *
 * Useful for testing scenarios like:
 * - Wait for 3 seek operations to complete
 * - Wait for play/pause to toggle N times
 */
class TransitionCountIdlingResource(
    private val name: String,
    private val targetCount: Int
) : IdlingResource {

    private var resourceCallback: IdlingResource.ResourceCallback? = null
    private val isIdle = AtomicBoolean(true)
    @Volatile
    private var count: Int = 0

    override fun getName(): String = name

    override fun isIdleNow(): Boolean = isIdle.get()

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        resourceCallback = callback
    }

    /**
     * Start counting transitions.
     */
    fun startCounting() {
        count = 0
        isIdle.set(false)
    }

    /**
     * Record a transition. When count reaches target, becomes idle.
     */
    fun recordTransition() {
        count++
        if (count >= targetCount) {
            if (!isIdle.getAndSet(true)) {
                resourceCallback?.onTransitionToIdle()
            }
        }
    }

    /**
     * Reset the counter.
     */
    fun reset() {
        count = 0
        isIdle.set(true)
    }
}
