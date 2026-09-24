package com.companionai.voiceapp.engine

/**
 * Abstraksi STT sesuai spek: startListening/stopListening/transcribe.
 * Implementasi real = AndroidSpeechEngine (SpeechRecognizer bawaan Android).
 */
interface SpeechEngine {

    interface Listener {
        fun onListeningStarted()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(message: String)
        fun onListeningStopped()
    }

    fun startListening()
    fun stopListening()
    fun cancel()
    fun isListening(): Boolean
    fun setListener(listener: Listener)
    fun destroy()
}
