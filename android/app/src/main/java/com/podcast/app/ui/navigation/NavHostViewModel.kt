package com.podcast.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.podcast.app.privacy.PrivacyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * ViewModel for navigation state.
 *
 * Provides access to app-level state needed for navigation decisions,
 * such as whether onboarding has been completed.
 */
@HiltViewModel
class NavHostViewModel @Inject constructor(
    private val privacyRepository: PrivacyRepository
) : ViewModel() {

    /**
     * Flow indicating whether onboarding has been completed.
     */
    val isOnboardingCompleted: Flow<Boolean> = privacyRepository.isOnboardingCompleted
}
