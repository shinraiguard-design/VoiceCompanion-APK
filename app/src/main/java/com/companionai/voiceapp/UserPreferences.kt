package com.companionai.voiceapp.data

/** Data class untuk halaman "MY PREFERENCES" -- ini soal USER, bukan soal karakter AI. */
data class UserPreferences(
    val preferredName: String = "",
    val language: String = "id",             // "id" | "en" -- bisa override bahasa karakter
    val responseLength: String = "short",    // "short" | "medium" | "long"
    val preferredTone: String = "",          // free text, misal "santai", "to the point"
    val emojiLevel: String = "low",          // "none" | "low" | "high"
    val voiceEnabled: Boolean = true,
    val autoSpeak: Boolean = true,
    val memoryEnabled: Boolean = true
)
