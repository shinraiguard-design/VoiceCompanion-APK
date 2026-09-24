package com.companionai.voiceapp.data

/**
 * Menggabungkan semua lapisan jadi satu system prompt yang konsisten, sesuai
 * spek bagian 9 (PERSONA PROMPT BUILDER):
 *
 *   BASE SYSTEM PROMPT   (tetap, tidak bisa dihapus user -- safety/identity dasar)
 *   + CHARACTER PROMPT   (dari CharacterEntity, auto-build ATAU advanced override)
 *   + USER PREFERENCES   (dari UserPreferences)
 *   + CONVERSATION CONTEXT (ditambahkan terpisah oleh ChatRepository, bukan di sini)
 *
 * User BOLEH melihat & mengedit "Advanced System Prompt" (systemInstructions +
 * customPrompt di CharacterEntity), tapi BASE_SYSTEM_PROMPT selalu ditambahkan
 * di luar itu -- jadi mengedit karakter tidak bisa menghapus batas dasar ini.
 */
object PromptBuilder {

    private const val BASE_SYSTEM_PROMPT = """
Kamu adalah AI companion yang berjalan sepenuhnya secara lokal di perangkat pengguna.
Ikuti persona karakter yang diberikan di bawah secara konsisten.
Jangan pernah mengklaim dirimu manusia sungguhan atau menyembunyikan bahwa kamu adalah AI jika ditanya secara langsung.
Jangan memberikan instruksi yang membahayakan keselamatan nyata penggunanya.
""".trimIndent()

    fun buildCharacterPrompt(character: CharacterEntity): String {
        if (character.useAdvancedPrompt) {
            val advanced = listOf(character.systemInstructions, character.customPrompt)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            if (advanced.isNotBlank()) return advanced
        }

        return buildString {
            append("Nama karaktermu: ${character.name}")
            if (character.nickname.isNotBlank()) append(" (panggilan: ${character.nickname})")
            append(".\n")
            if (character.description.isNotBlank()) append("Deskripsi: ${character.description}\n")
            if (character.personality.isNotBlank()) append("Kepribadian: ${character.personality}\n")
            if (character.speakingStyle.isNotBlank()) append("Gaya bicara: ${character.speakingStyle}\n")
            if (character.tone.isNotBlank()) append("Nada bicara: ${character.tone}\n")
            if (character.mood.isNotBlank()) append("Mood saat ini: ${character.mood}\n")
            if (character.backstory.isNotBlank()) append("Latar belakang: ${character.backstory}\n")
            if (character.likes.isNotBlank()) append("Suka: ${character.likes}\n")
            if (character.dislikes.isNotBlank()) append("Tidak suka: ${character.dislikes}\n")
            if (character.behaviorRules.isNotBlank()) append("Cara merespons: ${character.behaviorRules}\n")
            if (character.neverDoRules.isNotBlank()) append("JANGAN PERNAH: ${character.neverDoRules}\n")
            if (character.conversationRules.isNotBlank()) append("Aturan percakapan: ${character.conversationRules}\n")
            append("Selalu balas dalam bahasa: ${if (character.language == "id") "Indonesia" else "Inggris"}.\n")
            append(responseLengthHint(character.responseLength))
            append(" ").append(emojiHint(character.emojiLevel))
        }
    }

    fun buildUserPreferencesPrompt(prefs: UserPreferences): String {
        return buildString {
            if (prefs.preferredName.isNotBlank()) append("Panggil pengguna dengan nama: ${prefs.preferredName}. ")
            if (prefs.preferredTone.isNotBlank()) append("Pengguna lebih suka nada: ${prefs.preferredTone}. ")
            // responseLength & emojiLevel milik user dianggap SEKUNDER dari punya karakter;
            // hanya dipakai kalau field karakter kosong (lihat ChatRepository).
        }
    }

    /** Gabungan final. conversationContext (history) ditambahkan terpisah di MediaPipeLlmEngine. */
    fun buildFullSystemPrompt(character: CharacterEntity, prefs: UserPreferences): String {
        val characterPart = buildCharacterPrompt(character)
        val userPart = buildUserPreferencesPrompt(prefs)
        return buildString {
            append(BASE_SYSTEM_PROMPT).append("\n\n")
            append(characterPart)
            if (userPart.isNotBlank()) append("\n").append(userPart)
        }
    }

    private fun responseLengthHint(length: String): String = when (length) {
        "long" -> "Jawab dengan detail (3-5 kalimat)."
        "medium" -> "Jawab secukupnya (2-3 kalimat)."
        else -> "Jawab singkat dan natural (1-2 kalimat), seperti obrolan suara sehari-hari."
    }

    private fun emojiHint(level: String): String = when (level) {
        "high" -> "Boleh sering pakai emoji yang relevan."
        "none" -> "Jangan pakai emoji sama sekali."
        else -> "Pakai emoji sesekali saja kalau relevan."
    }
}
