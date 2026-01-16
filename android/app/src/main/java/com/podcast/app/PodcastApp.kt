package com.podcast.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PodcastApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization will be handled by Hilt
    }
}
