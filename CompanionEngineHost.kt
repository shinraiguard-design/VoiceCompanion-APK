package com.companionai.voiceapp.engine

import android.content.Context
import com.companionai.voiceapp.VoiceCompanionApp
import com.companionai.voiceapp.data.AiPowerRepository
import com.companionai.voiceapp.data.CharacterRepository
import com.companionai.voiceapp.data.ChatRepository
import com.companionai.voiceapp.data.UserPreferencesRepository
import com.companionai.voiceapp.util.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * SINGLETON per-process yang pegang SATU instance dari semua engine & repository
 * inti. MainActivity (chat biasa) DAN FloatingBubbleService (overlay) sama-sama
 * ambil dari sini, supaya:
 *  - Model LLM cuma di-load SEKALI ke RAM walau ada 2 UI surface aktif.
 *  - Karakter aktif, preferences, dan status AI ON/OFF konsisten di semua tempat.
 *
 * Repository (character/preferences/model path/AI power state) TETAP hidup
 * sepanjang proses app berjalan. Yang benar-benar di-destroy/dibuat ulang saat
 * AI OFF/ON adalah ENGINE (LLM/STT/TTS), karena itu yang makan RAM & resource.
 */
object CompanionEngineHost {

    @Volatile private var repositoriesInitialized = false
    @Volatile private var enginesReady = false

    // Scope berumur selama proses app hidup -- dipakai KHUSUS untuk operasi yang
    // tidak boleh memblokir main thread (misalnya load model, yang bisa makan
    // waktu beberapa detik untuk file berukuran ratusan MB-1GB).
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var llmEngine: LLMEngine
        private set
    lateinit var speechEngine: SpeechEngine
        private set
    lateinit var ttsEngine: TTSEngine
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var characterRepository: CharacterRepository
        private set
    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set
    lateinit var aiPowerRepository: AiPowerRepository
        private set
    lateinit var modelManager: ModelManager
        private set

    /** Selalu aman dipanggil berkali-kali; repository hanya dibuat sekali. */
    fun ensureInitialized(context: Context) {
        ensureRepositories(context)
        if (!enginesReady) createEngines(context)
    }

    /**
     * Versi ringan: pastikan repository (character/preferences/AI-power state) siap
     * TANPA memaksa membuat engine LLM/STT/TTS. Dipakai FloatingBubbleService supaya
     * bubble tetap bisa tampil (baca status AI ON/OFF) walau AI sedang OFF, tanpa
     * diam-diam menyalakan model/microphone.
     */
    fun ensureRepositoriesOnly(context: Context) {
        ensureRepositories(context)
    }

    private fun ensureRepositories(context: Context) {
        if (repositoriesInitialized) return
        synchronized(this) {
            if (repositoriesInitialized) return
            val appContext = context.applicationContext
            val app = appContext as VoiceCompanionApp

            modelManager = ModelManager(appContext)
            userPreferencesRepository = UserPreferencesRepository(appContext)
            aiPowerRepository = AiPowerRepository(appContext)
            characterRepository = CharacterRepository(app.database.characterDao(), appContext)

            runBlocking { characterRepository.ensureDefaultCharacters() }

            repositoriesInitialized = true
        }
    }

    private fun createEngines(context: Context) {
        synchronized(this) {
            if (enginesReady) return
            val appContext = context.applicationContext
            val app = appContext as VoiceCompanionApp

            llmEngine = MediaPipeLlmEngine(appContext)
            speechEngine = AndroidSpeechEngine(appContext)
            ttsEngine = AndroidTTSEngine(appContext)
            chatRepository = ChatRepository(
                app.database.messageDao(), llmEngine, characterRepository, userPreferencesRepository
            )
            enginesReady = true

            // Auto-load model yang sebelumnya diimport user, kalau ada.
            // SENGAJA async (hostScope), bukan runBlocking -- load model bisa makan
            // waktu beberapa detik dan fungsi ini kadang dipanggil dari main thread
            // (Activity/ViewModel init), jadi tidak boleh memblokirnya.
            modelManager.getActiveModelPath()?.let { path ->
                hostScope.launch { llmEngine.loadModel(path, modelManager.getGenerationConfig()) }
            }
        }
    }

    /**
     * Dipanggil saat user menekan [ AI OFF ]. Menghentikan & melepas SEMUA engine
     * (LLM unload, STT destroy, TTS shutdown) supaya RAM/mic/audio benar-benar
     * bebas -- bukan cuma "diam" di background.
     */
    suspend fun shutdownEngines() {
        // Ambil referensi engine SAAT INI lalu langsung flip enginesReady=false,
        // semua di dalam synchronized(this) yang sama dengan createEngines() --
        // supaya createEngines() (dipanggil dari restartEngines()/ensureInitialized(),
        // termasuk dari thread lain) tidak pernah melihat enginesReady yang stale.
        // Proses teardown yang suspend (unloadModel, dll) baru dijalankan SETELAH
        // lock dilepas, pakai referensi lokal -- supaya create engine baru (kalau
        // user langsung tekan ON lagi) tidak perlu menunggu unload model lama selesai.
        val engines = synchronized(this) {
            if (!enginesReady) return
            enginesReady = false
            Triple(speechEngine, ttsEngine, llmEngine)
        }
        val (localSpeech, localTts, localLlm) = engines
        localSpeech.cancel()
        localSpeech.destroy()
        localTts.shutdown()
        localLlm.unloadModel()
    }

    /** Dipanggil saat user menekan [ AI ON ] lagi setelah shutdownEngines(). */
    fun restartEngines(context: Context) {
        if (enginesReady) return
        createEngines(context)
    }

    fun areEnginesReady(): Boolean = enginesReady
}
