package com.companionai.voiceapp.engine

/** Abstraksi TTS sesuai spek: speak/stop/pause/resume. */
interface TTSEngine {
    interface Listener {
        fun onSpeakingStarted()
        fun onSpeakingFinished()
        fun onSpeakingError(message: String)
    }

    fun speak(text: String)
    fun stop()
    fun pause()   // Android TTS native gak punya pause asli -> diimplementasi sbg stop + resume dari awal
    fun resume()
    fun setSpeechRate(rate: Float)   // 0.5 = pelan, 1.0 = normal, 2.0 = cepat
    fun setPitch(pitch: Float)
    fun getAvailableVoiceNames(): List<String>
    fun setVoiceByName(name: String): Boolean
    fun setListener(listener: Listener)
    fun isSpeaking(): Boolean
    fun shutdown()
}
