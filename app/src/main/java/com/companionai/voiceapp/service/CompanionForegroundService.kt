package com.companionai.voiceapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.companionai.voiceapp.MainActivity
import com.companionai.voiceapp.R
import com.companionai.voiceapp.engine.CompanionEngineHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground Service = representasi "AI sedang ON" di level sistem Android,
 * sesuai spek bagian 2 (PERSISTENT AI / ALWAYS ON).
 *
 * Service ini HANYA berjalan selagi master switch = AI ON. Begitu user menekan
 * OFF (dari notification, dari app, atau dari floating bubble), service ini
 * stopSelf() dan SEMUA engine (LLM/STT/TTS) benar-benar dilepas lewat
 * CompanionEngineHost.shutdownEngines() -- bukan cuma "diam".
 *
 * BATASAN YANG TETAP BERLAKU (harus jujur soal ini):
 * Foreground service + notification membuat Android JAUH LEBIH SEGAN mematikan
 * proses, TAPI TIDAK MENJAMIN 24/7. OEM (Xiaomi/Oppo/Vivo/Samsung dkk) tetap
 * bisa membatasi/mematikan proses demi baterai/RAM. Tidak ada root/exploit/
 * accessibility-abuse yang dipakai di sini untuk memaksa Android -- murni API
 * resmi (startForeground + foregroundServiceType).
 */
class CompanionForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TURN_OFF -> {
                turnAiOff()
                return START_NOT_STICKY
            }
            else -> {
                CompanionEngineHost.ensureInitialized(applicationContext)
                CompanionEngineHost.aiPowerRepository.setOn(true)
                createChannelIfNeeded()
                ServiceCompat.startForeground(
                    this,
                    NOTIF_ID,
                    buildNotification(),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    else 0
                )
            }
        }
        return START_STICKY
    }

    private fun turnAiOff() {
        CompanionEngineHost.aiPowerRepository.setOn(false)
        stopService(Intent(this, FloatingBubbleService::class.java))
        serviceScope.launch { CompanionEngineHost.shutdownEngines() }
        stopSelf()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val turnOffAction = NotificationCompat.Action(
            0, getString(R.string.notif_action_turn_off),
            servicePendingIntent(ACTION_TURN_OFF)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText("Siap mendengarkan")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .addAction(turnOffAction)
            .setOngoing(true)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, CompanionForegroundService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "companion_active_channel"
        private const val NOTIF_ID = 1001
        const val ACTION_TURN_OFF = "com.companionai.voiceapp.ACTION_TURN_OFF"

        fun start(context: Context) {
            val intent = Intent(context, CompanionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CompanionForegroundService::class.java).setAction(ACTION_TURN_OFF)
            )
        }
    }
}
