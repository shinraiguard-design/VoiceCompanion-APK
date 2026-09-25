package com.companionai.voiceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Satu karakter/persona AI, SEPENUHNYA bisa dibuat & diedit user dari dalam
 * aplikasi (Characters screen). Tidak ada personality yang di-hardcode di
 * source code -- semua field ini yang membentuk kepribadian AI.
 */
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // --- Identitas ---
    val name: String,
    val nickname: String = "",
    val description: String = "",

    // --- Kepribadian ---
    val personality: String = "",
    val speakingStyle: String = "",
    val tone: String = "",
    val mood: String = "",
    val language: String = "id", // "id" atau "en", dipakai juga untuk hint TTS/STT

    // --- Latar belakang ---
    val backstory: String = "",
    val likes: String = "",
    val dislikes: String = "",

    // --- Aturan perilaku ---
    val behaviorRules: String = "",       // "How should this character respond?"
    val neverDoRules: String = "",        // "What should this character never do?"
    val conversationRules: String = "",

    // --- Percakapan ---
    val greeting: String = "",

    // --- Advanced ---
    // systemInstructions/customPrompt: kalau useAdvancedPrompt true, GABUNGAN keduanya
    // dipakai APA ADANYA sebagai lapisan "CHARACTER PROMPT" (lihat PromptBuilder),
    // menggantikan hasil auto-build dari field di atas. BASE_SYSTEM_PROMPT (safety)
    // dari PromptBuilder TETAP selalu ditambahkan, tidak bisa dihapus lewat sini.
    val systemInstructions: String = "",
    val customPrompt: String = "",
    val useAdvancedPrompt: Boolean = false,

    // --- Voice & response tuning per-karakter ---
    val responseLength: String = "short", // "short" | "medium" | "long"
    val emojiLevel: String = "low",       // "none" | "low" | "high"

    val isBuiltIn: Boolean = false,       // true untuk karakter default bawaan (tidak bisa dihapus, cuma bisa di-duplicate)
    val builtInKey: String = "",          // penanda stabil ("luna"/"assistant") dipakai fitur Reset -- tidak berubah walau nama diedit user
    val createdAt: Long = System.currentTimeMillis()
)
