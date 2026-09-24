package com.companionai.voiceapp.engine

/**
 * Abstraksi LLM engine. Diperluas dengan error model yang lebih spesifik
 * (missing / incompatible / RAM kurang) sesuai spek Model Manager.
 */
interface LLMEngine {

    enum class Status { UNLOADED, LOADING, READY, GENERATING, ERROR }

    sealed class LlmError(val userMessage: String) : Exception(userMessage) {
        data class ModelMissing(val path: String) :
            LlmError("File model tidak ditemukan di: $path. Import model .task dulu lewat Settings.")
        data class InsufficientRam(val requiredMb: Long, val availableMb: Long) :
            LlmError("RAM kemungkinan tidak cukup untuk model ini (butuh ~${requiredMb}MB, tersedia ~${availableMb}MB). Coba model yang lebih kecil.")
        data class ModelIncompatible(val reason: String) :
            LlmError("Model tidak kompatibel: $reason")
        data class LoadFailed(val reason: String) :
            LlmError("Gagal memuat model: $reason")
        object NotLoaded :
            LlmError("Model belum dimuat. Pilih & load model dulu di Settings.")
        data class GenerationFailed(val reason: String) :
            LlmError("Gagal menghasilkan respons: $reason")
    }

    data class GenerationConfig(
        val maxTokens: Int = 512,
        val topK: Int = 40,
        val temperature: Float = 0.8f
    )

    suspend fun loadModel(modelPath: String, config: GenerationConfig = GenerationConfig()): Result<Unit>

    suspend fun unloadModel()

    suspend fun reloadModel(): Result<Unit>

    suspend fun generate(systemPrompt: String, conversation: List<Pair<String, String>>, userInput: String): Result<String>

    fun stopGeneration()

    fun getStatus(): Status

    fun getLoadedModelPath(): String?
}
