package com.companionai.voiceapp.data

import android.content.Context

/**
 * Master switch [ AI ON ] / [ AI OFF ] yang diminta di spek bagian 2.
 * State-nya PERSISTENT (SharedPreferences) sehingga tetap konsisten walau
 * app ditutup lalu dibuka lagi.
 *
 * CATATAN JUJUR SOAL "PERSISTENT SETELAH RESTART":
 * Ini menjamin state ON/OFF konsisten saat APP dibuka lagi oleh user (baca:
 * flag-nya diingat). Ini TIDAK menyalakan service otomatis saat HP baru
 * selesai reboot tanpa user membuka app -- itu butuh BOOT_COMPLETED receiver
 * yang sengaja tidak ditambahkan di MVP ini (Android 12+ juga membatasi
 * start foreground service dari boot receiver). Kalau kamu mau fitur itu,
 * itu langkah lanjutan yang jelas, bukan sesuatu yang diam-diam "dipalsukan".
 */
class AiPowerRepository(context: Context) {

    private val prefs = context.getSharedPreferences("ai_power_prefs", Context.MODE_PRIVATE)

    fun isOn(): Boolean = prefs.getBoolean("ai_on", true) // default ON setelah setup, sesuai spek

    fun setOn(on: Boolean) {
        prefs.edit().putBoolean("ai_on", on).apply()
    }

    fun isSetupDone(): Boolean = prefs.getBoolean("setup_done", false)

    fun markSetupDone() {
        prefs.edit().putBoolean("setup_done", true).apply()
    }
}
