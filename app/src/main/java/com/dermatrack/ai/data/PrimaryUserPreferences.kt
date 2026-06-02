package com.dermatrack.ai.data

import android.content.Context

class PrimaryUserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun primaryUserEmail(): String? = prefs.getString(KEY_PRIMARY_EMAIL, null)

    fun primaryPersonaId(): Long? {
        val value = prefs.getLong(KEY_PRIMARY_PERSONA_ID, -1L)
        return if (value > 0L) value else null
    }

    fun setPrimaryUser(email: String, primaryPersonaId: Long) {
        prefs.edit()
            .putString(KEY_PRIMARY_EMAIL, email)
            .putLong(KEY_PRIMARY_PERSONA_ID, primaryPersonaId)
            .apply()
    }

    fun clearPrimaryUser() {
        prefs.edit()
            .remove(KEY_PRIMARY_EMAIL)
            .remove(KEY_PRIMARY_PERSONA_ID)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "dermatrack_primary_user"
        const val KEY_PRIMARY_EMAIL = "primary_user_email"
        const val KEY_PRIMARY_PERSONA_ID = "primary_persona_id"
    }
}
