package com.vibe.terminal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VibeTerminalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide configurations here
    }
}
