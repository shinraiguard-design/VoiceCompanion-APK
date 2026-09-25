package com.companionai.voiceapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.companionai.voiceapp.engine.CompanionEngineHost
import com.companionai.voiceapp.engine.LLMEngine
import com.companionai.voiceapp.engine.SpeechEngine
import com.companionai.voiceapp.engine.TTSEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    // PENTING: init block ini harus jadi yang PERTAMA di class body -- di Kotlin,
    // property initializer dan init block dieksekusi berurutan sesuai urutan
    // penulisannya. Kalau `aiPower`/`userPrefs` di bawah dideklarasikan SEBELUM
    // init block ini, keduanya akan mencoba baca CompanionEngineHost.aiPowerRepository
    // (lateinit) SEBELUM ensureRepositoriesOnly() sempat mengisinya -> crash
    // UninitializedPropertyAccessException. Ini bug nyata yang sudah diperbaiki di sini.
    init {
        CompanionEngineHost.ensureRepositoriesOnly(application)
    }

    private val appCtx get() = getApplication<Application>()
    private val aiPower = CompanionEngineHost.aiPowerRepository
    private val userPrefs = CompanionEngineHost.userPreferencesRepository

    private val _status = MutableStateFlow(CompanionStatus.IDLE)
    val status: StateFlow<CompanionStatus> = _status.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    private val _aiOn = MutableStateFlow(true)
    val aiOn: StateFlow<Boolean> = _aiOn.asStateFlow()

    private val _activeCharacterName = MutableStateFlow("")
    val activeCharacterName: StateFlow<String> = _activeCharacterName.asStateFlow()

    // Bug nyata yang diperbaiki: observeMessages() dipanggil ulang setiap kali AI
    // di-ON-kan (init, onScreenResumed, setAiOn). Tanpa tracking job ini, tiap
    // panggilan bikin collector Flow BARU tanpa membatalkan yang lama -> menumpuk
    // terus kalau user toggle ON/OFF berkali-kali dalam satu sesi Activity.
    private var messagesJob: Job? = null

    init {
        CompanionEngineHost.ensureRepositoriesOnly(application)
        _aiOn.value = aiPower.isOn()
        if (_aiOn.value) {
            CompanionEngineHost.ensureInitialized(application)
            attachEngineListeners()
            observeMessages()
        } else {
            _status.value = CompanionStatus.OFFLINE
        }
        refreshActiveCharacterName()
    }

    private fun observeMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            val memoryOn = userPrefs.get().memoryEnabled
            val flow = if (memoryOn) CompanionEngineHost.chatRepository.observeMessages()
                       else CompanionEngineHost.chatRepository.observeTransientMessages()
            flow.collect { entities ->
                _messages.value = entities.map { ChatMessageUi(it.role, it.text) }
            }
        }
    }

    private fun refreshActiveCharacterName() {
        viewModelScope.launch {
            val character = CompanionEngineHost.characterRepository.getActiveCharacter()
            _activeCharacterName.value = character?.name ?: "Belum ada karakter"
        }
    }

    /** Dipanggil dari MainActivity.onResume() untuk sinkron kalau AI ON/OFF atau karakter aktif diganti di Settings/bubble. */
    fun onScreenResumed() {
        refreshActiveCharacterName()
        val onNow = aiPower.isOn()
        if (onNow != _aiOn.value) {
            _aiOn.value = onNow
            if (onNow) {
                CompanionEngineHost.ensureInitialized(appCtx)
                attachEngineListeners()
                observeMessages()
                _status.value = CompanionStatus.IDLE
            } else {
                _status.value = CompanionStatus.OFFLINE
            }
        }
    }

    private fun attachEngineListeners() {
        if (!CompanionEngineHost.areEnginesReady()) return

        CompanionEngineHost.speechEngine.setListener(object : SpeechEngine.Listener {
            override fun onListeningStarted() { _status.value = CompanionStatus.LISTENING }
            override fun onPartialResult(text: String) {}
            override fun onFinalResult(text: String) { handleUserMessage(text) }
            override fun onError(message: String) {
                _status.value = if (_aiOn.value) CompanionStatus.IDLE else CompanionStatus.OFFLINE
                _errorEvent.value = message
            }
            override fun onListeningStopped() {
                if (_status.value == CompanionStatus.LISTENING) {
                    _status.value = if (_aiOn.value) CompanionStatus.IDLE else CompanionStatus.OFFLINE
                }
            }
        })

        CompanionEngineHost.ttsEngine.setListener(object : TTSEngine.Listener {
            override fun onSpeakingStarted() { _status.value = CompanionStatus.SPEAKING }
            override fun onSpeakingFinished() {
                _status.value = if (_aiOn.value) CompanionStatus.IDLE else CompanionStatus.OFFLINE
            }
            override fun onSpeakingError(message: String) {
                _status.value = if (_aiOn.value) CompanionStatus.IDLE else CompanionStatus.OFFLINE
                _errorEvent.value = message
            }
        })
    }

    // ---------- Master switch AI ON/OFF ----------

    fun setAiOn(on: Boolean) {
        aiPower.setOn(on)
        _aiOn.value = on
        if (on) {
            CompanionEngineHost.restartEngines(appCtx)
            attachEngineListeners()
            observeMessages()
            _status.value = CompanionStatus.IDLE
        } else {
            _status.value = CompanionStatus.OFFLINE
            viewModelScope.launch { CompanionEngineHost.shutdownEngines() }
        }
    }

    // ---------- Voice ----------

    fun onMicButtonPressed() {
        if (!_aiOn.value) {
            _errorEvent.value = "AI sedang OFF. Nyalakan dulu lewat tombol ON/OFF."
            return
        }
        if (!CompanionEngineHost.modelManager.hasActiveModel()) {
            _errorEvent.value = "Pilih model dulu di Settings sebelum ngobrol"
            return
        }
        if (CompanionEngineHost.speechEngine.isListening()) {
            CompanionEngineHost.speechEngine.stopListening()
        } else {
            CompanionEngineHost.ttsEngine.stop()
            CompanionEngineHost.speechEngine.startListening()
        }
    }

    /** Tombol [Stop] eksplisit di UI: hentikan listening, TTS, dan tandai generation dihentikan. */
    fun onStopButtonPressed() {
        if (CompanionEngineHost.areEnginesReady()) {
            CompanionEngineHost.speechEngine.cancel()
            CompanionEngineHost.ttsEngine.stop()
            CompanionEngineHost.llmEngine.stopGeneration()
        }
        _status.value = if (_aiOn.value) CompanionStatus.IDLE else CompanionStatus.OFFLINE
    }

    /** Dipanggil dari text input di UI. */
    fun onTextMessageSubmitted(text: String) {
        if (text.isBlank()) return
        if (!_aiOn.value) {
            _errorEvent.value = "AI sedang OFF. Nyalakan dulu lewat tombol ON/OFF."
            return
        }
        handleUserMessage(text)
    }

    private fun handleUserMessage(text: String) {
        _status.value = CompanionStatus.PROCESSING
        viewModelScope.launch {
            val result = CompanionEngineHost.chatRepository.sendUserMessage(text)
            result.onSuccess { aiText ->
                if (userPrefs.get().autoSpeak) {
                    CompanionEngineHost.ttsEngine.speak(aiText)
                } else {
                    _status.value = CompanionStatus.IDLE
                }
            }.onFailure { e ->
                _status.value = if (_aiOn.value) CompanionStatus.IDLE else CompanionStatus.OFFLINE
                _errorEvent.value = (e as? LLMEngine.LlmError)?.userMessage ?: e.message ?: "Gagal generate respons"
            }
        }
    }

    fun consumeError() { _errorEvent.value = null }

    override fun onCleared() {
        super.onCleared()
        if (CompanionEngineHost.areEnginesReady() && CompanionEngineHost.speechEngine.isListening()) {
            CompanionEngineHost.speechEngine.stopListening()
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(application) as T
        }
    }
}
