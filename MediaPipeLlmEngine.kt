package com.companionai.voiceapp.engine

import android.app.ActivityManager
import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementasi LLMEngine pakai Google AI Edge / MediaPipe GenAI (tasks-genai).
 *
 * KENAPA INI, BUKAN llama.cpp MENTAH:
 * llama.cpp asli butuh dikompilasi lewat NDK/CMake (native C++), sangat sulit
 * dilakukan hanya dari HP tanpa toolchain native lengkap. tasks-genai adalah
 * dependency Gradle biasa (AAR) yang isinya SUDAH berisi binary native hasil
 * kompilasi Google -- tinggal `implementation` di build.gradle, TIDAK perlu
 * compile native apapun sendiri.
 *
 * Model TIDAK ikut di dalam APK. User import file .task lewat Settings.
 */
class MediaPipeLlmEngine(private val context: Context) : LLMEngine {

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var status: LLMEngine.Status = LLMEngine.Status.UNLOADED
    @Volatile private var cancelled = false
    @Volatile private var loadedModelPath: String? = null
    @Volatile private var lastConfig: LLMEngine.GenerationConfig = LLMEngine.GenerationConfig()

    override suspend fun loadModel(
        modelPath: String,
        config: LLMEngine.GenerationConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            status = LLMEngine.Status.LOADING

            val file = File(modelPath)
            if (!file.exists()) {
                status = LLMEngine.Status.ERROR
                return@withContext Result.failure(LLMEngine.LlmError.ModelMissing(modelPath))
            }

            // Cek RAM kasar sebelum load: model file size dipakai sebagai proxy kebutuhan
            // RAM minimum (praktiknya butuh ~1.2-1.5x ukuran file karena overhead runtime).
            val memInfo = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
            val availableMb = memInfo.availMem / (1024 * 1024)
            val requiredMb = (file.length() / (1024 * 1024)) * 3 / 2
            if (memInfo.lowMemory || availableMb < requiredMb) {
                status = LLMEngine.Status.ERROR
                return@withContext Result.failure(
                    LLMEngine.LlmError.InsufficientRam(requiredMb, availableMb)
                )
            }

            // Unload model lama dulu (kalau ada) sebelum load yang baru.
            llmInference?.close()
            llmInference = null

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(config.maxTokens)
                .setTopK(config.topK)
                .setTemperature(config.temperature)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
            lastConfig = config
            status = LLMEngine.Status.READY
            Result.success(Unit)
        } catch (e: Exception) {
            status = LLMEngine.Status.ERROR
            // MediaPipe biasanya melempar RuntimeException generik kalau file bukan
            // model .task yang valid/kompatibel -- kita bungkus jadi ModelIncompatible
            // supaya pesan ke user lebih jelas daripada stack trace mentah.
            Result.failure(LLMEngine.LlmError.ModelIncompatible(e.message ?: "format model tidak dikenali"))
        }
    }

    override suspend fun unloadModel() = withContext(Dispatchers.IO) {
        try {
            llmInference?.close()
        } finally {
            llmInference = null
            loadedModelPath = null
            status = LLMEngine.Status.UNLOADED
        }
    }

    override suspend fun reloadModel(): Result<Unit> {
        val path = loadedModelPath
            ?: return Result.failure(LLMEngine.LlmError.NotLoaded)
        unloadModel()
        return loadModel(path, lastConfig)
    }

    override suspend fun generate(
        systemPrompt: String,
        conversation: List<Pair<String, String>>,
        userInput: String
    ): Result<String> = withContext(Dispatchers.Default) {
        val engine = llmInference
            ?: return@withContext Result.failure(LLMEngine.LlmError.NotLoaded)

        cancelled = false
        status = LLMEngine.Status.GENERATING
        try {
            val prompt = buildGemmaPrompt(systemPrompt, conversation, userInput)
            val rawResult = engine.generateResponse(prompt)
            status = LLMEngine.Status.READY

            if (cancelled) {
                Result.failure(LLMEngine.LlmError.GenerationFailed("Dihentikan oleh user"))
            } else {
                Result.success(rawResult.trim())
            }
        } catch (e: Exception) {
            status = LLMEngine.Status.ERROR
            Result.failure(LLMEngine.LlmError.GenerationFailed(e.message ?: "unknown error"))
        }
    }

    override fun stopGeneration() {
        // generateResponse() di versi ini blocking/non-streaming, tidak ada cancel token
        // native. Flag ini menahan hasil di level UI. Untuk stop instan mid-generation,
        // migrasi ke generateResponseAsync() (streaming, punya callback per-token).
        cancelled = true
    }

    override fun getStatus(): LLMEngine.Status = status

    override fun getLoadedModelPath(): String? = loadedModelPath

    private fun buildGemmaPrompt(
        systemPrompt: String,
        conversation: List<Pair<String, String>>,
        userInput: String
    ): String {
        val trimmedHistory = conversation.takeLast(6)
        return buildString {
            append("<start_of_turn>user\n")
            append(systemPrompt)
            append("\n\n")
            for ((role, text) in trimmedHistory) {
                val label = if (role == "user") "User" else "AI"
                append("$label: $text\n")
            }
            append("User: $userInput")
            append("<end_of_turn>\n<start_of_turn>model\n")
        }
    }
}
