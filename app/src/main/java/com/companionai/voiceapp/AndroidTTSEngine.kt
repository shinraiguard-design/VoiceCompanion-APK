package com.companionai.voiceapp.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * TTS pakai android.speech.tts.TextToSpeech (bawaan Android, berjalan offline
 * kalau voice data-nya sudah diinstall device -- default di hampir semua
 * device modern lewat Google TTS / OEM TTS).
 *
 * FALLBACK YANG DIMINTA DI SPEK: TextToSpeech bawaan Android INI SENDIRI sudah
 * jadi fallback paling realistis. TTS offline pihak ketiga (misal Piper) butuh
 * native binary + model suara terpisah + integrasi JNI manual -- jalur upgrade
 * lanjutan, bukan MVP ini.
 */
class AndroidTTSEngine(context: Context) : TTSEngine {

    private var tts: TextToSpeech? = null
    private var listener: TTSEngine.Listener? = null
    private var ready = false
    private var pendingRate = 1.0f
    private var pendingPitch = 1.0f
    private var lastSpokenText: String? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale("id", "ID")
                tts?.setSpeechRate(pendingRate)
                tts?.setPitch(pendingPitch)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        listener?.onSpeakingStarted()
                    }
                    override fun onDone(utteranceId: String?) {
                        listener?.onSpeakingFinished()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        listener?.onSpeakingError("TTS error")
                    }
                })
            } else {
                listener?.onSpeakingError("TTS gagal diinisialisasi (engine tidak tersedia)")
            }
        }
    }

    override fun speak(text: String) {
        if (!ready) {
            listener?.onSpeakingError("TTS belum siap")
            return
        }
        lastSpokenText = text
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun pause() {
        tts?.stop()
    }

    override fun resume() {
        lastSpokenText?.let { speak(it) }
    }

    override fun setSpeechRate(rate: Float) {
        pendingRate = rate
        tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        pendingPitch = pitch
        tts?.setPitch(pitch)
    }

    override fun getAvailableVoiceNames(): List<String> {
        return try {
            tts?.voices?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList() // sebagian device/OEM TTS engine tidak expose voice list dgn benar
        }
    }

    override fun setVoiceByName(name: String): Boolean {
        val voice = tts?.voices?.firstOrNull { it.name == name } ?: return false
        return try {
            tts?.voice = voice
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun setListener(listener: TTSEngine.Listener) {
        this.listener = listener
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking == true

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
