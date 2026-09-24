package com.companionai.voiceapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Satu baris chat (dari user ATAU dari AI). Disimpan di Room (SQLite lokal).
 * Ini adalah "memory" percakapan yang diminta di spek: conversation history.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,       // "user" atau "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
