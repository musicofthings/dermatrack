package com.dermatrack.ai.data

import android.content.Context

class CloudAnalysisPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutodermEnabled(): Boolean = prefs.getBoolean(KEY_AUTODERM_ENABLED, false)

    fun setAutodermEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTODERM_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "dermatrack_cloud_analysis"
        const val KEY_AUTODERM_ENABLED = "autoderm_enabled"
    }
}
