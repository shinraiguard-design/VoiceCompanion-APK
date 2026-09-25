package com.companionai.voiceapp.data

import android.content.Context

class UserPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    fun get(): UserPreferences = UserPreferences(
        preferredName = prefs.getString("preferred_name", "") ?: "",
        language = prefs.getString("language", "id") ?: "id",
        responseLength = prefs.getString("response_length", "short") ?: "short",
        preferredTone = prefs.getString("preferred_tone", "") ?: "",
        emojiLevel = prefs.getString("emoji_level", "low") ?: "low",
        voiceEnabled = prefs.getBoolean("voice_enabled", true),
        autoSpeak = prefs.getBoolean("auto_speak", true),
        memoryEnabled = prefs.getBoolean("memory_enabled", true)
    )

    fun save(p: UserPreferences) {
        prefs.edit()
            .putString("preferred_name", p.preferredName)
            .putString("language", p.language)
            .putString("response_length", p.responseLength)
            .putString("preferred_tone", p.preferredTone)
            .putString("emoji_level", p.emojiLevel)
            .putBoolean("voice_enabled", p.voiceEnabled)
            .putBoolean("auto_speak", p.autoSpeak)
            .putBoolean("memory_enabled", p.memoryEnabled)
            .apply()
    }

    /** "Jangan hard-code data pribadi user. User dapat menghapus data kapan saja." */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
