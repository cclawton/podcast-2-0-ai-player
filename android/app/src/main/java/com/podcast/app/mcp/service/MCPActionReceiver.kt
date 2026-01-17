package com.podcast.app.mcp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.podcast.app.mcp.bridge.InputValidator
import com.podcast.app.mcp.bridge.MCPActions
import com.podcast.app.playback.IPlaybackController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broadcast receiver for MCP actions.
 *
 * This receiver is protected by a signature-level permission
 * to ensure only trusted apps can send commands.
 *
 * Registration in AndroidManifest.xml:
 * <receiver
 *     android:name=".mcp.service.MCPActionReceiver"
 *     android:exported="true"
 *     android:permission="com.podcast.app.permission.MCP_CONTROL">
 *     <intent-filter>
 *         <action android:name="com.podcast.app.action.MCP_COMMAND" />
 *     </intent-filter>
 * </receiver>
 */
@AndroidEntryPoint
class MCPActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var playbackController: IPlaybackController

    @Inject
    lateinit var inputValidator: InputValidator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MCP_COMMAND) return

        val action = intent.getStringExtra(EXTRA_ACTION) ?: return

        scope.launch {
            when (action) {
                MCPActions.PLAY_EPISODE -> handlePlayEpisode(intent)
                MCPActions.PAUSE -> playbackController.pause()
                MCPActions.RESUME -> playbackController.resume()
                MCPActions.SKIP_FORWARD -> handleSkipForward(intent)
                MCPActions.SKIP_BACKWARD -> handleSkipBackward(intent)
                MCPActions.SET_SPEED -> handleSetSpeed(intent)
                MCPActions.SEEK_TO -> handleSeekTo(intent)
            }
        }
    }

    private suspend fun handlePlayEpisode(intent: Intent) {
        val podcastIdStr = intent.getStringExtra(EXTRA_PODCAST_ID) ?: return
        val episodeIdStr = intent.getStringExtra(EXTRA_EPISODE_ID) ?: return
        val startPosition = intent.getIntExtra(EXTRA_START_POSITION, 0)

        // Validate inputs
        if (!inputValidator.validateId(podcastIdStr).isValid()) return
        if (!inputValidator.validateId(episodeIdStr).isValid()) return
        if (!inputValidator.validatePosition(startPosition).isValid()) return

        val episodeId = episodeIdStr.toLongOrNull() ?: return

        playbackController.playEpisode(episodeId, startPosition)
    }

    private fun handleSkipForward(intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 15)
        if (!inputValidator.validateSkipSeconds(seconds).isValid()) return

        playbackController.skipForward(seconds)
    }

    private fun handleSkipBackward(intent: Intent) {
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 15)
        if (!inputValidator.validateSkipSeconds(seconds).isValid()) return

        playbackController.skipBackward(seconds)
    }

    private fun handleSetSpeed(intent: Intent) {
        val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
        if (!inputValidator.validateSpeed(speed).isValid()) return

        playbackController.setPlaybackSpeed(speed)
    }

    private fun handleSeekTo(intent: Intent) {
        val position = intent.getIntExtra(EXTRA_POSITION, 0)
        if (!inputValidator.validatePosition(position).isValid()) return

        playbackController.seekTo(position * 1000L) // Convert to milliseconds
    }

    companion object {
        const val ACTION_MCP_COMMAND = "com.podcast.app.action.MCP_COMMAND"

        const val EXTRA_ACTION = "action"
        const val EXTRA_PODCAST_ID = "podcastId"
        const val EXTRA_EPISODE_ID = "episodeId"
        const val EXTRA_START_POSITION = "startPosition"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_POSITION = "position"
    }
}
