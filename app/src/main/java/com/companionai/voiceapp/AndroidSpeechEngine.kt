package com.companionai.voiceapp.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * STT pakai android.speech.SpeechRecognizer.
 *
 * PRIORITAS OFFLINE: coba createOnDeviceSpeechRecognizer() dulu (stabil mulai
 * Android 12/API 31, jalan tanpa internet kalau paket bahasa offline sudah
 * didownload user lewat Settings > System > Languages > On-device recognition).
 * Fallback ke createSpeechRecognizer() biasa kalau on-device tidak tersedia.
 *
 * ALTERNATIF 100% offline terjamin (tidak tergantung OEM): Vosk
 * (org.vosk:vosk-android), model diimport terpisah spt model LLM -- belum
 * diimplementasi di MVP ini supaya scope tetap kecil.
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null
    @Volatile private var listening = false

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null
    private val listeningTimeoutMs = 15_000L // safety-net kalau device tidak trigger onError sendiri

    override fun setListener(listener: SpeechEngine.Listener) {
        this.listener = listener
    }

    override fun isListening(): Boolean = listening

    override fun startListening() {
        if (listening) return

        recognizer?.destroy()
        recognizer = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                listener?.onListeningStarted()
                scheduleTimeout()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onError(error: Int) {
                cancelTimeout()
                listening = false
                listener?.onError(mapErrorCode(error))
                listener?.onListeningStopped()
            }

            override fun onResults(results: Bundle?) {
                cancelTimeout()
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) listener?.onFinalResult(text)
                listener?.onListeningStopped()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) listener?.onPartialResult(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        cancelTimeout()
        recognizer?.stopListening()
        listening = false
    }

    /** Cancel = batalkan TOTAL, buang hasil parsial (beda dari stopListening yang finalize). */
    override fun cancel() {
        cancelTimeout()
        recognizer?.cancel()
        listening = false
        listener?.onListeningStopped()
    }

    override fun destroy() {
        cancelTimeout()
        recognizer?.destroy()
        recognizer = null
    }

    private fun scheduleTimeout() {
        cancelTimeout()
        val runnable = Runnable {
            if (listening) {
                listener?.onError("Timeout, tidak ada suara terdeteksi")
                cancel()
            }
        }
        timeoutRunnable = runnable
        timeoutHandler.postDelayed(runnable, listeningTimeoutMs)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun mapErrorCode(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Gak nangkep ucapannya, coba lagi"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout, gak ada suara terdeteksi"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin microphone belum diberikan"
        SpeechRecognizer.ERROR_NETWORK -> "Butuh koneksi (on-device recognizer belum tersedia di HP ini)"
        SpeechRecognizer.ERROR_AUDIO -> "Error audio recording"
        else -> "STT error (code $code)"
    }
}
