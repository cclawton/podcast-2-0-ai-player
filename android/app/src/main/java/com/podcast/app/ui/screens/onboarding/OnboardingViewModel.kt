package com.podcast.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcast.app.privacy.PrivacyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for onboarding flow.
 *
 * Manages the state of the onboarding process, including
 * permission requests and completion tracking.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val privacyRepository: PrivacyRepository
) : ViewModel() {

    private val _micPermissionGranted = MutableStateFlow(false)
    val micPermissionGranted: StateFlow<Boolean> = _micPermissionGranted.asStateFlow()

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    /**
     * Called when microphone permission result is received.
     */
    fun onMicPermissionResult(granted: Boolean) {
        _micPermissionGranted.value = granted
    }

    /**
     * Complete onboarding and navigate to main app.
     * Can be called whether permission is granted or not.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            privacyRepository.completeOnboarding()
            _onboardingComplete.value = true
        }
    }
}
