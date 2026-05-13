package com.dermatrack.ai

import android.app.Application
import com.dermatrack.ai.data.AppContainer

class DermaTrackApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
