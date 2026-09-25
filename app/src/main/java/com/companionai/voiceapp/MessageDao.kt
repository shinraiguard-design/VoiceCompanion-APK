package com.companionai.voiceapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<MessageEntity>>

    // Dipakai untuk membangun context window ke LLM tanpa mengirim SELURUH history
    // (biar hemat RAM & token, sesuai batasan resource di spek).
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MessageEntity>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<MessageEntity>
}
