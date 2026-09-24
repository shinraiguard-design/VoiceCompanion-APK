package com.companionai.voiceapp.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.companionai.voiceapp.engine.LLMEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Urus file model .task DAN generation config (temperature/topK/maxTokens).
 * Model TIDAK ikut APK -- murni file eksternal yang diimport user sendiri.
 */
class ModelManager(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    private val prefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE)

    suspend fun importModel(uri: Uri, displayName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val targetFile = File(modelsDir, sanitizeFileName(displayName))

            resolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 1 shl 20) // 1MB buffer, model bisa >500MB
                }
            } ?: return@withContext Result.failure(IllegalStateException("Tidak bisa buka file model"))

            prefs.edit().putString("active_model_path", targetFile.absolutePath).apply()
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveModelPath(): String? = prefs.getString("active_model_path", null)

    fun hasActiveModel(): Boolean {
        val path = getActiveModelPath() ?: return false
        return File(path).exists()
    }

    fun getGenerationConfig(): LLMEngine.GenerationConfig = LLMEngine.GenerationConfig(
        maxTokens = prefs.getInt("max_tokens", 512),
        topK = prefs.getInt("top_k", 40),
        temperature = prefs.getFloat("temperature", 0.8f)
    )

    fun saveGenerationConfig(config: LLMEngine.GenerationConfig) {
        prefs.edit()
            .putInt("max_tokens", config.maxTokens)
            .putInt("top_k", config.topK)
            .putFloat("temperature", config.temperature)
            .apply()
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
