package com.companionai.voiceapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.companionai.voiceapp.MainActivity
import com.companionai.voiceapp.R
import com.companionai.voiceapp.engine.CompanionEngineHost
import com.companionai.voiceapp.engine.SpeechEngine
import com.companionai.voiceapp.engine.TTSEngine
import com.companionai.voiceapp.ui.ChatMessageUi
import com.companionai.voiceapp.ui.MessageAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Floating bubble + mini chat window (spek bagian 6 & 14).
 *
 * Izin resmi Android (Settings.canDrawOverlays + WindowManager API publik) --
 * TIDAK ada bypass/hidden method. Posisi bubble disimpan (SharedPreferences)
 * supaya konsisten tiap kali bubble dibuka lagi.
 *
 * Bubble TETAP bisa muncul walau AI dalam kondisi OFF (statusnya jadi
 * "AI nonaktif" / offline) -- ini supaya user tetap punya cara menyalakan AI
 * lagi lewat tombol power di mini chat, sesuai spek "melihat AI status" dan
 * "ON/OFF AI" dari floating bubble.
 */
class FloatingBubbleService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var miniChatView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isMiniChatOpen = false

    // Bug nyata yang diperbaiki: openMiniChat() dipanggil ulang setiap kali mini
    // chat dibuka lagi (termasuk saat toggle power di bubble, yang close+reopen
    // mini chat) tapi collector Flow lama tidak pernah dibatalkan -> menumpuk.
    private var miniChatMessagesJob: Job? = null

    private val adapter = MessageAdapter()
    private val positionPrefs by lazy {
        getSharedPreferences("bubble_position_prefs", MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // SENGAJA tidak pakai ensureInitialized() penuh di sini -- bubble harus bisa
        // tampil (dan menunjukkan status "AI nonaktif") walau AI OFF, tanpa diam-diam
        // menyalakan model/microphone hanya karena bubble-nya dibuka.
        CompanionEngineHost.ensureRepositoriesOnly(applicationContext)
        if (CompanionEngineHost.aiPowerRepository.isOn()) {
            CompanionEngineHost.ensureInitialized(applicationContext)
        }
        startForegroundWithNotification()
        addBubbleView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Floating companion", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating companion aktif")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else 0
        )
    }

    // ---------- Bubble ----------

    private fun addBubbleView() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        bubbleView = LayoutInflater.from(this).inflate(R.layout.view_floating_bubble, null)
        applyBubbleSizePreference()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val savedX = positionPrefs.getInt("bubble_x", 0)
        val savedY = positionPrefs.getInt("bubble_y", 300)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        bubbleParams = params

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX)
                    val dy = (event.rawY - initialTouchY)
                    if (abs(dx) > 8 || abs(dy) > 8) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(bubbleView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    } else {
                        // Simpan posisi baru cuma saat selesai di-drag, bukan tiap pixel gerak.
                        positionPrefs.edit()
                            .putInt("bubble_x", params.x)
                            .putInt("bubble_y", params.y)
                            .apply()
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView?.setOnClickListener { toggleMiniChat() }

        windowManager.addView(bubbleView, params)
        updateBubbleStatusDot()
    }

    private fun updateBubbleStatusDot() {
        val dot = bubbleView?.findViewById<View>(R.id.bubbleStatusDot) ?: return
        val colorRes = if (CompanionEngineHost.aiPowerRepository.isOn()) R.color.status_listening else R.color.status_idle
        dot.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    /** Ukuran bubble diatur dari Settings > Floating > Bubble size (Small/Medium/Large). */
    private fun applyBubbleSizePreference() {
        val floatingPrefs = getSharedPreferences("floating_prefs", MODE_PRIVATE)
        val sizeIndex = floatingPrefs.getInt("bubble_size_index", 1) // 0=Small,1=Medium,2=Large
        val sizeDp = when (sizeIndex) {
            0 -> 44
            2 -> 72
            else -> 56
        }
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        bubbleView?.layoutParams = android.view.ViewGroup.LayoutParams(sizePx, sizePx)
    }

    // ---------- Mini chat window ----------

    private fun toggleMiniChat() {
        if (isMiniChatOpen) closeMiniChat() else openMiniChat()
    }

    private fun openMiniChat() {
        if (isMiniChatOpen) return
        isMiniChatOpen = true

        miniChatView = LayoutInflater.from(this).inflate(R.layout.view_mini_chat, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams?.x ?: 0
            y = (bubbleParams?.y ?: 300) + 140
        }

        val recycler = miniChatView?.findViewById<RecyclerView>(R.id.miniTranscriptRecycler)
        recycler?.layoutManager = LinearLayoutManager(this)
        recycler?.adapter = adapter

        val statusText = miniChatView?.findViewById<TextView>(R.id.miniStatusText)
        val aiIsOn = CompanionEngineHost.aiPowerRepository.isOn()
        statusText?.text = if (aiIsOn) getString(R.string.status_idle) else getString(R.string.status_offline)

        if (aiIsOn && CompanionEngineHost.areEnginesReady()) {
            miniChatMessagesJob?.cancel()
            miniChatMessagesJob = lifecycleScope.launch {
                val memoryOn = CompanionEngineHost.userPreferencesRepository.get().memoryEnabled
                val flow = if (memoryOn) CompanionEngineHost.chatRepository.observeMessages()
                           else CompanionEngineHost.chatRepository.observeTransientMessages()
                flow.collect { entities ->
                    adapter.submitList(entities.map { ChatMessageUi(it.role, it.text) })
                }
            }

            CompanionEngineHost.speechEngine.setListener(object : SpeechEngine.Listener {
                override fun onListeningStarted() { statusText?.setText(R.string.status_listening) }
                override fun onPartialResult(text: String) {}
                override fun onFinalResult(text: String) {
                    statusText?.setText(R.string.status_thinking)
                    lifecycleScope.launch {
                        val result = CompanionEngineHost.chatRepository.sendUserMessage(text)
                        result.onSuccess {
                            if (CompanionEngineHost.userPreferencesRepository.get().autoSpeak) {
                                CompanionEngineHost.ttsEngine.speak(it)
                            } else {
                                statusText?.setText(R.string.status_idle)
                            }
                        }.onFailure { e ->
                            statusText?.text = e.message ?: getString(R.string.status_error)
                        }
                    }
                }
                override fun onError(message: String) { statusText?.text = message }
                override fun onListeningStopped() { statusText?.setText(R.string.status_idle) }
            })

            CompanionEngineHost.ttsEngine.setListener(object : TTSEngine.Listener {
                override fun onSpeakingStarted() { statusText?.setText(R.string.status_speaking) }
                override fun onSpeakingFinished() { statusText?.setText(R.string.status_idle) }
                override fun onSpeakingError(message: String) { statusText?.text = message }
            })

            miniChatView?.findViewById<View>(R.id.miniMicButton)?.setOnClickListener {
                if (!CompanionEngineHost.modelManager.hasActiveModel()) {
                    statusText?.text = getString(R.string.model_not_loaded)
                    return@setOnClickListener
                }
                if (CompanionEngineHost.speechEngine.isListening()) {
                    CompanionEngineHost.speechEngine.stopListening()
                } else {
                    CompanionEngineHost.ttsEngine.stop()
                    CompanionEngineHost.speechEngine.startListening()
                }
            }

            miniChatView?.findViewById<View>(R.id.miniStopButton)?.setOnClickListener {
                CompanionEngineHost.speechEngine.cancel()
                CompanionEngineHost.ttsEngine.stop()
                CompanionEngineHost.llmEngine.stopGeneration()
                statusText?.setText(R.string.status_idle)
            }
        } else {
            miniChatView?.findViewById<View>(R.id.miniMicButton)?.setOnClickListener {
                statusText?.text = "Nyalakan AI dulu (tombol power di atas)"
            }
            miniChatView?.findViewById<View>(R.id.miniStopButton)?.setOnClickListener { /* tidak ada yang perlu dihentikan saat AI OFF */ }
        }

        // Tombol power: toggle AI ON/OFF langsung dari bubble (spek bagian 6).
        miniChatView?.findViewById<View>(R.id.miniPowerButton)?.setOnClickListener {
            val turningOn = !CompanionEngineHost.aiPowerRepository.isOn()
            CompanionEngineHost.aiPowerRepository.setOn(turningOn)
            if (turningOn) {
                // Panggil langsung (bukan cuma lewat intent ke service) supaya engine
                // sudah READY saat mini chat di-render ulang beberapa baris di bawah --
                // start service tetap dipanggil untuk notifikasi persistent-nya.
                CompanionEngineHost.ensureInitialized(applicationContext)
                CompanionForegroundService.start(this)
            } else {
                lifecycleScope.launch { CompanionEngineHost.shutdownEngines() }
                CompanionForegroundService.stop(this)
            }
            updateBubbleStatusDot()
            closeMiniChat()
            openMiniChat() // re-open supaya listener ke-rebind sesuai state AI yang baru
        }

        miniChatView?.findViewById<View>(R.id.miniOpenAppButton)?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        miniChatView?.findViewById<View>(R.id.miniCloseButton)?.setOnClickListener { closeMiniChat() }

        windowManager.addView(miniChatView, params)
    }

    private fun closeMiniChat() {
        if (!isMiniChatOpen) return
        isMiniChatOpen = false
        miniChatMessagesJob?.cancel()
        miniChatMessagesJob = null
        miniChatView?.let { runCatching { windowManager.removeView(it) } }
        miniChatView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeMiniChat()
        bubbleView?.let {
            bubbleParams?.let { p ->
                positionPrefs.edit().putInt("bubble_x", p.x).putInt("bubble_y", p.y).apply()
            }
            runCatching { windowManager.removeView(it) }
        }
        bubbleView = null
    }

    companion object {
        private const val CHANNEL_ID = "floating_bubble_channel"
        private const val NOTIF_ID = 2002
    }
}
