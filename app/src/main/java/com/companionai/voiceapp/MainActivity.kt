package com.companionai.voiceapp

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.companionai.voiceapp.databinding.ActivityMainBinding
import com.companionai.voiceapp.service.CompanionForegroundService
import com.companionai.voiceapp.ui.ChatMessageUi
import com.companionai.voiceapp.ui.ChatViewModel
import com.companionai.voiceapp.ui.CompanionStatus
import com.companionai.voiceapp.ui.MessageAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: ChatViewModel
    private val adapter = MessageAdapter()
    private var pulseAnimator: ValueAnimator? = null
    private var suppressSwitchCallback = false

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Snackbar.make(binding.root, "Izin microphone dibutuhkan untuk voice chat", Snackbar.LENGTH_LONG).show()
        }
    }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* foreground service tetap jalan walau ditolak, cuma gak ada notif */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, ChatViewModel.Factory(application))[ChatViewModel::class.java]

        binding.transcriptRecycler.layoutManager = LinearLayoutManager(this)
        binding.transcriptRecycler.adapter = adapter

        binding.micButton.setOnClickListener {
            ensurePermissionsThenRun { viewModel.onMicButtonPressed() }
        }

        binding.stopButton.setOnClickListener { viewModel.onStopButtonPressed() }

        binding.sendButton.setOnClickListener {
            val text = binding.textInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.onTextMessageSubmitted(text)
                binding.textInput.setText("")
            }
        }

        binding.aiPowerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressSwitchCallback) return@setOnCheckedChangeListener
            viewModel.setAiOn(isChecked)
            if (isChecked) {
                if (hasRecordAudioPermission()) CompanionForegroundService.start(this)
            } else {
                CompanionForegroundService.stop(this)
            }
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        observeViewModel()
        askInitialPermissions()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
        syncFloatingBubbleIfEnabled()
    }

    /** Kalau user pernah nyalain floating bubble & overlay permission masih ada, pastikan
     *  service-nya jalan lagi (misalnya proses app sempat mati lalu dibuka ulang). */
    private fun syncFloatingBubbleIfEnabled() {
        val floatingPrefs = getSharedPreferences("floating_prefs", MODE_PRIVATE)
        val wantsBubble = floatingPrefs.getBoolean("bubble_on", false)
        val aiOn = viewModel.aiOn.value
        if (wantsBubble && aiOn && android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this, com.companionai.voiceapp.service.FloatingBubbleService::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.messages.collect { list: List<ChatMessageUi> ->
                adapter.submitList(list)
                if (list.isNotEmpty()) binding.transcriptRecycler.scrollToPosition(list.size - 1)
            }
        }
        lifecycleScope.launch {
            viewModel.status.collect { status -> renderStatus(status) }
        }
        lifecycleScope.launch {
            viewModel.errorEvent.collect { message ->
                if (message != null) {
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    viewModel.consumeError()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.aiOn.collect { on ->
                suppressSwitchCallback = true
                binding.aiPowerSwitch.isChecked = on
                suppressSwitchCallback = false
                binding.micButton.isEnabled = on
                binding.sendButton.isEnabled = on
                binding.micButton.alpha = if (on) 1f else 0.4f
            }
        }
        lifecycleScope.launch {
            viewModel.activeCharacterName.collect { name ->
                binding.aiNameText.text = name
            }
        }
    }

    private fun renderStatus(status: CompanionStatus) {
        val (colorRes, label) = when (status) {
            CompanionStatus.IDLE -> R.color.status_idle to getString(R.string.status_idle)
            CompanionStatus.LISTENING -> R.color.status_listening to getString(R.string.status_listening)
            CompanionStatus.PROCESSING -> R.color.status_thinking to getString(R.string.status_thinking)
            CompanionStatus.SPEAKING -> R.color.status_speaking to getString(R.string.status_speaking)
            CompanionStatus.OFFLINE -> R.color.status_idle to getString(R.string.status_offline)
            CompanionStatus.ERROR -> R.color.danger to getString(R.string.status_error)
        }
        binding.statusDot.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        binding.statusText.text = label

        pulseAnimator?.cancel()
        if (status == CompanionStatus.LISTENING || status == CompanionStatus.SPEAKING) {
            pulseAnimator = ObjectAnimator.ofFloat(binding.avatarView, "scaleX", 1f, 1.08f, 1f).apply {
                duration = 900
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
            binding.avatarView.scaleY = binding.avatarView.scaleX
        } else {
            binding.avatarView.scaleX = 1f
            binding.avatarView.scaleY = 1f
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askInitialPermissions() {
        if (!hasRecordAudioPermission()) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun ensurePermissionsThenRun(action: () -> Unit) {
        if (hasRecordAudioPermission()) {
            action()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
