package com.companionai.voiceapp

import android.app.Application
import com.companionai.voiceapp.data.AppDatabase

/**
 * Application class. Inisialisasi singleton yang dipakai di seluruh app
 * (database Room). Tidak ada koneksi network apapun di sini — semua lokal.
 */
class VoiceCompanionApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // Tidak ada init cloud/analytics. Semua fitur berjalan lokal by default.
    }
}
