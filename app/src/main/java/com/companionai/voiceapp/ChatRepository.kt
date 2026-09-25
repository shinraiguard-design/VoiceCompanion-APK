package com.companionai.voiceapp.data

import com.companionai.voiceapp.engine.LLMEngine
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Titik temu antara UI, Room (memory), CharacterRepository, UserPreferences,
 * dan LLMEngine. Menghormati toggle "Memory ON/OFF": kalau OFF, percakapan
 * TIDAK ditulis ke Room (tidak persist), tapi tetap dipakai sebagai konteks
 * sementara untuk sesi berjalan ini saja (in-memory, hilang saat app ditutup).
 */
class ChatRepository(
    private val dao: MessageDao,
    private val llmEngine: LLMEngine,
    private val characterRepository: CharacterRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Dipakai HANYA saat memory OFF, supaya percakapan sesi ini tetap nyambung
    // tanpa ditulis ke database.
    private val transientHistory = mutableListOf<Pair<String, String>>()

    private val _transientMessages = MutableStateFlow<List<MessageEntity>>(emptyList())

    fun observeMessages(): Flow<List<MessageEntity>> = dao.observeAll()

    /** Dipakai UI kalau memory OFF, karena observeMessages() (Room) tidak akan terisi. */
    fun observeTransientMessages(): Flow<List<MessageEntity>> = _transientMessages.asStateFlow()

    suspend fun sendUserMessage(text: String): Result<String> {
        val memoryOn = userPreferencesRepository.get().memoryEnabled
        val character = characterRepository.getActiveCharacter()
            ?: return Result.failure(IllegalStateException("Belum ada karakter aktif. Buat karakter dulu di menu Characters."))

        val historyPairs: List<Pair<String, String>>

        if (memoryOn) {
            dao.insert(MessageEntity(role = "user", text = text))
            val recent = dao.getRecent(limit = 12).reversed()
            historyPairs = recent.filter { it.text != text }.map { it.role to it.text }
        } else {
            transientHistory.add("user" to text)
            historyPairs = transientHistory.toList()
            _transientMessages.value = transientHistory.map { (role, t) -> MessageEntity(role = role, text = t) }
        }

        val prefs = userPreferencesRepository.get()
        val systemPrompt = PromptBuilder.buildFullSystemPrompt(character, prefs)

        val result = llmEngine.generate(
            systemPrompt = systemPrompt,
            conversation = historyPairs,
            userInput = text
        )

        result.onSuccess { aiText ->
            if (memoryOn) {
                dao.insert(MessageEntity(role = "ai", text = aiText))
            } else {
                transientHistory.add("ai" to aiText)
                _transientMessages.value = transientHistory.map { (role, t) -> MessageEntity(role = role, text = t) }
            }
        }
        return result
    }

    fun clearTransientSession() {
        transientHistory.clear()
        _transientMessages.value = emptyList()
    }

    suspend fun clearConversation() {
        dao.clearAll()
        clearTransientSession()
    }

    suspend fun exportMemoryAsJson(): String {
        val all = dao.getAllForExport()
        return Gson().toJson(all)
    }
}
