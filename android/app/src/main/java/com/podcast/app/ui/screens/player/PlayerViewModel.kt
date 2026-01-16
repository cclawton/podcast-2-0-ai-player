package com.podcast.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.data.local.dao.PodcastDao
import com.podcast.app.playback.PlaybackController
import com.podcast.app.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val podcastDao: PodcastDao
) : ViewModel() {

    val currentEpisode: StateFlow<Episode?> = playbackController.currentEpisode

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState

    val queue: StateFlow<List<Episode>> = playbackController.queue

    private val _podcast = MutableStateFlow<Podcast?>(null)
    val podcast: StateFlow<Podcast?> = _podcast.asStateFlow()

    private val _showSpeedDialog = MutableStateFlow(false)
    val showSpeedDialog: StateFlow<Boolean> = _showSpeedDialog.asStateFlow()

    init {
        viewModelScope.launch {
            currentEpisode.collect { episode ->
                episode?.let {
                    _podcast.value = podcastDao.getPodcastById(it.podcastId)
                }
            }
        }
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun skipForward(seconds: Int = 15) {
        playbackController.skipForward(seconds)
    }

    fun skipBackward(seconds: Int = 15) {
        playbackController.skipBackward(seconds)
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
        _showSpeedDialog.value = false
    }

    fun showSpeedSelector() {
        _showSpeedDialog.value = true
    }

    fun hideSpeedSelector() {
        _showSpeedDialog.value = false
    }

    fun playNext() {
        viewModelScope.launch {
            playbackController.playNext()
        }
    }

    fun removeFromQueue(episode: Episode) {
        playbackController.removeFromQueue(episode)
    }

    fun clearQueue() {
        playbackController.clearQueue()
    }
}
