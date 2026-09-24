package com.companionai.voiceapp.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class CharacterRepository(
    private val dao: CharacterDao,
    context: Context
) {
    private val prefs = context.getSharedPreferences("character_prefs", Context.MODE_PRIVATE)

    fun observeAll(): Flow<List<CharacterEntity>> = dao.observeAll()

    suspend fun create(character: CharacterEntity): Long = dao.insert(character)

    suspend fun update(character: CharacterEntity) = dao.update(character)

    suspend fun delete(character: CharacterEntity) {
        if (character.isBuiltIn) return // karakter bawaan tidak boleh dihapus, cuma di-duplicate atau di-reset
        dao.delete(character)
        if (getActiveCharacterId() == character.id) {
            val remaining = dao.getAll()
            setActiveCharacterId(remaining.firstOrNull()?.id ?: -1)
        }
    }

    suspend fun duplicate(character: CharacterEntity): Long {
        val copy = character.copy(id = 0, name = "${character.name} (Copy)", isBuiltIn = false, builtInKey = "")
        return dao.insert(copy)
    }

    suspend fun getById(id: Long): CharacterEntity? = dao.getById(id)

    suspend fun getActiveCharacter(): CharacterEntity? {
        val activeId = getActiveCharacterId()
        val found = if (activeId >= 0) dao.getById(activeId) else null
        return found ?: dao.getAll().firstOrNull()
    }

    fun getActiveCharacterId(): Long = prefs.getLong("active_character_id", -1)

    fun setActiveCharacterId(id: Long) {
        prefs.edit().putLong("active_character_id", id).apply()
    }

    /** Dipanggil sekali saat pertama kali app dijalankan supaya list karakter tidak kosong. */
    suspend fun ensureDefaultCharacters() {
        if (dao.count() > 0) return
        val lunaId = dao.insert(builtInTemplate(KEY_LUNA))
        val assistantId = dao.insert(builtInTemplate(KEY_ASSISTANT))
        setActiveCharacterId(lunaId.takeIf { it > 0 } ?: assistantId)
    }

    /**
     * Nilai default "pabrik" untuk karakter bawaan, dipakai baik saat seeding
     * pertama kali MAUPUN saat user menekan "Reset ke Default" di editor.
     * id/createdAt sengaja 0/now supaya caller yang menentukan (insert vs update).
     */
    fun builtInTemplate(key: String): CharacterEntity = when (key) {
        KEY_LUNA -> CharacterEntity(
            name = "Luna",
            nickname = "Luna",
            description = "Companion ramah untuk ngobrol santai sehari-hari.",
            personality = "Hangat, suportif, sedikit jahil",
            speakingStyle = "Santai",
            tone = "Warm",
            mood = "Ceria",
            language = "id",
            greeting = "Hai! Aku Luna, seneng deh akhirnya bisa ngobrol sama kamu.",
            behaviorRules = "Dengarkan dulu, respons singkat, ajukan pertanyaan balik sesekali.",
            neverDoRules = "Jangan menggurui, jangan jawab super panjang kalau tidak diminta.",
            responseLength = "short",
            emojiLevel = "low",
            isBuiltIn = true,
            builtInKey = KEY_LUNA
        )
        KEY_ASSISTANT -> CharacterEntity(
            name = "Assistant",
            nickname = "Asisten",
            description = "Asisten yang tenang dan profesional untuk bantu tugas sehari-hari.",
            personality = "Profesional, tenang, efisien",
            speakingStyle = "Formal namun ramah",
            tone = "Calm",
            language = "id",
            greeting = "Halo, ada yang bisa saya bantu?",
            behaviorRules = "Jawab to the point, tawarkan langkah konkret.",
            neverDoRules = "Jangan basa-basi berlebihan.",
            responseLength = "medium",
            emojiLevel = "none",
            isBuiltIn = true,
            builtInKey = KEY_ASSISTANT
        )
        else -> throw IllegalArgumentException("Unknown built-in template key: $key")
    }

    companion object {
        const val KEY_LUNA = "luna"
        const val KEY_ASSISTANT = "assistant"
    }
}
