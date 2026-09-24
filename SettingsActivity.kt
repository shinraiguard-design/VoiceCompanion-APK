package com.companionai.voiceapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.companionai.voiceapp.data.UserPreferences
import com.companionai.voiceapp.databinding.ActivitySettingsBinding
import com.companionai.voiceapp.engine.CompanionEngineHost
import com.companionai.voiceapp.engine.LLMEngine
import com.companionai.voiceapp.service.CompanionForegroundService
import com.companionai.voiceapp.service.FloatingBubbleService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val bubbleSizeOptions = listOf("Small", "Medium", "Large")
    private var suppressSeek = true

    private val pickModelLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) importModel(uri)
    }

    private val createExportFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) writeExportTo(uri)
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingBubbleService::class.java))
        } else {
            binding.switchFloatingBubble.isChecked = false
            Snackbar.make(binding.root, getString(R.string.overlay_permission_needed), Snackbar.LENGTH_LONG).show()
        }
        refreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Settings"

        CompanionEngineHost.ensureRepositoriesOnly(applicationContext)
        if (CompanionEngineHost.aiPowerRepository.isOn()) {
            CompanionEngineHost.ensureInitialized(applicationContext)
        }

        setupSpinners()
        loadAiSection()
        loadVoiceSection()
        loadCharacterSection()
        loadMemorySection()
        loadFloatingSection()
        refreshServiceStatus()
        refreshPermissionStatus()

        bindListeners()
        suppressSeek = false
    }

    override fun onResume() {
        super.onResume()
        loadCharacterSection()
        refreshServiceStatus()
        refreshPermissionStatus()
    }

    // ---------- Setup ----------

    private fun setupSpinners() {
        binding.spinnerBubbleSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bubbleSizeOptions)
        val voiceNames = if (CompanionEngineHost.areEnginesReady()) {
            CompanionEngineHost.ttsEngine.getAvailableVoiceNames().ifEmpty { listOf("(default sistem)") }
        } else {
            listOf("(nyalakan AI dulu untuk lihat daftar voice)")
        }
        binding.spinnerVoice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames)
    }

    /** Guard dipakai di semua handler yang butuh llmEngine/speechEngine/ttsEngine. */
    private fun requireEnginesReady(): Boolean {
        if (!CompanionEngineHost.areEnginesReady()) {
            Snackbar.make(binding.root, "Nyalakan AI dulu (switch AI ON/OFF di atas)", Snackbar.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // ---------- AI section ----------

    private fun loadAiSection() {
        binding.switchAiOn.isChecked = CompanionEngineHost.aiPowerRepository.isOn()
        refreshModelStatus()
        val config = CompanionEngineHost.modelManager.getGenerationConfig()
        binding.seekTemperature.progress = (config.temperature * 10).toInt().coerceIn(0, 20)
        binding.seekMaxTokens.progress = config.maxTokens.coerceIn(0, 1024)
        updateAiConfigNote(config.temperature, config.maxTokens)
    }

    private fun refreshModelStatus() {
        val mm = CompanionEngineHost.modelManager
        binding.modelStatusText.text = if (mm.hasActiveModel()) {
            "Model aktif: ${File(mm.getActiveModelPath()!!).name} · status: ${engineStatusLabel()}"
        } else {
            getString(R.string.model_not_loaded)
        }
    }

    private fun engineStatusLabel(): String {
        if (!CompanionEngineHost.areEnginesReady()) return "AI OFF"
        return when (CompanionEngineHost.llmEngine.getStatus()) {
            LLMEngine.Status.READY -> "siap"
            LLMEngine.Status.LOADING -> "sedang memuat…"
            LLMEngine.Status.GENERATING -> "sedang generate…"
            LLMEngine.Status.ERROR -> "error"
            LLMEngine.Status.UNLOADED -> "belum dimuat"
        }
    }

    private fun updateAiConfigNote(temperature: Float, maxTokens: Int) {
        binding.aiConfigNote.text = "Temperature: ${"%.1f".format(temperature)} · Max tokens: $maxTokens · Perubahan berlaku setelah Reload"
    }

    // ---------- Voice section ----------

    private fun loadVoiceSection() {
        val prefs = CompanionEngineHost.userPreferencesRepository.get()
        binding.switchAutoSpeak.isChecked = prefs.autoSpeak
        binding.seekSpeechRate.progress = 10 // default 1.0x -> tengah slider (0.0-2.0 range, step 0.1)
        binding.seekPitch.progress = 10
    }

    // ---------- Character section ----------

    private fun loadCharacterSection() {
        lifecycleScope.launch {
            val c = CompanionEngineHost.characterRepository.getActiveCharacter()
            binding.activeCharacterText.text = "Karakter aktif: ${c?.name ?: "belum ada, buat dulu"}"
        }
    }

    // ---------- Memory section ----------

    private fun loadMemorySection() {
        binding.switchMemoryEnabled.isChecked = CompanionEngineHost.userPreferencesRepository.get().memoryEnabled
    }

    // ---------- Floating section ----------

    private fun loadFloatingSection() {
        val prefs = getSharedPreferences("floating_prefs", MODE_PRIVATE)
        binding.switchFloatingBubble.isChecked = prefs.getBoolean("bubble_on", false)
        binding.spinnerBubbleSize.setSelection(prefs.getInt("bubble_size_index", 1))
    }

    // ---------- Status ----------

    private fun refreshServiceStatus() {
        binding.serviceStatusText.text =
            "Foreground service: ${if (CompanionEngineHost.aiPowerRepository.isOn()) "aktif (AI ON)" else "berhenti (AI OFF)"}"
    }

    private fun refreshPermissionStatus() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val overlay = Settings.canDrawOverlays(this)
        binding.permissionsStatusText.text =
            "Mic: ${if (mic) "OK" else "belum"} · Notifikasi: ${if (notif) "OK" else "belum"} · Overlay: ${if (overlay) "OK" else "belum"}"
    }

    // ---------- Listeners ----------

    private fun bindListeners() {
        binding.switchAiOn.setOnCheckedChangeListener { _, checked ->
            CompanionEngineHost.aiPowerRepository.setOn(checked)
            if (checked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    CompanionForegroundService.start(this)
                    CompanionEngineHost.ensureInitialized(this) // biar kontrol di Settings ini langsung bisa dipakai
                    setupSpinners()
                } else {
                    Snackbar.make(binding.root, "Izinkan microphone dulu di layar utama", Snackbar.LENGTH_LONG).show()
                }
            } else {
                CompanionForegroundService.stop(this)
                lifecycleScope.launch { CompanionEngineHost.shutdownEngines() }
                setupSpinners()
            }
            refreshServiceStatus()
            refreshModelStatus()
        }

        binding.pickModelButton.setOnClickListener { pickModelLauncher.launch(arrayOf("*/*")) }

        binding.reloadModelButton.setOnClickListener {
            if (!requireEnginesReady()) return@setOnClickListener
            val path = CompanionEngineHost.modelManager.getActiveModelPath()
            if (path == null) {
                Snackbar.make(binding.root, "Belum ada model yang dipilih", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val config = LLMEngine.GenerationConfig(
                    maxTokens = binding.seekMaxTokens.progress.coerceAtLeast(32),
                    topK = 40,
                    temperature = binding.seekTemperature.progress / 10f
                )
                CompanionEngineHost.modelManager.saveGenerationConfig(config)
                val result = CompanionEngineHost.llmEngine.loadModel(path, config)
                result.onSuccess { Snackbar.make(binding.root, "Model berhasil di-reload", Snackbar.LENGTH_SHORT).show() }
                result.onFailure { e -> Snackbar.make(binding.root, describeError(e), Snackbar.LENGTH_LONG).show() }
                refreshModelStatus()
            }
        }

        binding.unloadModelButton.setOnClickListener {
            if (!requireEnginesReady()) return@setOnClickListener
            lifecycleScope.launch {
                CompanionEngineHost.llmEngine.unloadModel()
                refreshModelStatus()
                Snackbar.make(binding.root, "Model di-unload", Snackbar.LENGTH_SHORT).show()
            }
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!suppressSeek) updateAiConfigNote(binding.seekTemperature.progress / 10f, binding.seekMaxTokens.progress.coerceAtLeast(32))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.seekTemperature.setOnSeekBarChangeListener(seekListener)
        binding.seekMaxTokens.setOnSeekBarChangeListener(seekListener)

        binding.spinnerVoice.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (suppressSeek || !CompanionEngineHost.areEnginesReady()) return
                val name = parent?.getItemAtPosition(position) as? String ?: return
                CompanionEngineHost.ttsEngine.setVoiceByName(name)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })

        binding.seekSpeechRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && CompanionEngineHost.areEnginesReady()) {
                    CompanionEngineHost.ttsEngine.setSpeechRate((progress / 10f).coerceAtLeast(0.1f))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && CompanionEngineHost.areEnginesReady()) {
                    CompanionEngineHost.ttsEngine.setPitch((progress / 10f).coerceAtLeast(0.1f))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.switchAutoSpeak.setOnCheckedChangeListener { _, checked -> saveUserPrefsPatch { it.copy(autoSpeak = checked) } }

        binding.manageCharactersButton.setOnClickListener { startActivity(Intent(this, CharactersActivity::class.java)) }
        binding.myPreferencesButton.setOnClickListener { startActivity(Intent(this, PreferencesActivity::class.java)) }

        binding.switchMemoryEnabled.setOnCheckedChangeListener { _, checked -> saveUserPrefsPatch { it.copy(memoryEnabled = checked) } }

        binding.exportMemoryButton.setOnClickListener {
            createExportFileLauncher.launch("voice_companion_memory_${System.currentTimeMillis()}.json")
        }
        binding.clearConversationButton.setOnClickListener {
            lifecycleScope.launch {
                (application as VoiceCompanionApp).database.messageDao().clearAll()
                if (CompanionEngineHost.areEnginesReady()) CompanionEngineHost.chatRepository.clearTransientSession()
                Snackbar.make(binding.root, "Conversation dihapus", Snackbar.LENGTH_SHORT).show()
            }
        }
        binding.clearAllDataButton.setOnClickListener { confirmClearAllData() }

        binding.switchFloatingBubble.setOnCheckedChangeListener { _, checked ->
            getSharedPreferences("floating_prefs", MODE_PRIVATE).edit().putBoolean("bubble_on", checked).apply()
            if (checked) {
                if (Settings.canDrawOverlays(this)) {
                    startService(Intent(this, FloatingBubbleService::class.java))
                } else {
                    overlayPermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
            } else {
                stopService(Intent(this, FloatingBubbleService::class.java))
            }
        }
        binding.spinnerBubbleSize.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                getSharedPreferences("floating_prefs", MODE_PRIVATE).edit().putInt("bubble_size_index", position).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
        binding.resetBubblePositionButton.setOnClickListener {
            getSharedPreferences("bubble_position_prefs", MODE_PRIVATE).edit().clear().apply()
            Snackbar.make(binding.root, "Posisi bubble di-reset (berlaku saat bubble dibuka ulang)", Snackbar.LENGTH_SHORT).show()
        }

        binding.batteryGuidanceButton.setOnClickListener { openBatteryGuidance() }
    }

    private fun saveUserPrefsPatch(patch: (UserPreferences) -> UserPreferences) {
        val repo = CompanionEngineHost.userPreferencesRepository
        repo.save(patch(repo.get()))
    }

    private fun importModel(uri: Uri) {
        Snackbar.make(binding.root, "Mengimport model, tunggu sebentar (bisa 1-2 menit untuk file besar)…", Snackbar.LENGTH_LONG).show()
        lifecycleScope.launch {
            val result = CompanionEngineHost.modelManager.importModel(uri, "active_model.task")
            result.onSuccess { path ->
                if (CompanionEngineHost.areEnginesReady()) {
                    val config = CompanionEngineHost.modelManager.getGenerationConfig()
                    val loadResult = CompanionEngineHost.llmEngine.loadModel(path, config)
                    loadResult.onSuccess {
                        Snackbar.make(binding.root, "Model berhasil dimuat", Snackbar.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Snackbar.make(binding.root, describeError(e), Snackbar.LENGTH_LONG).show()
                    }
                } else {
                    Snackbar.make(binding.root, "Model tersimpan. Nyalakan AI untuk memuatnya.", Snackbar.LENGTH_LONG).show()
                }
                refreshModelStatus()
            }.onFailure { e ->
                Snackbar.make(binding.root, "Gagal import: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun describeError(e: Throwable): String = (e as? LLMEngine.LlmError)?.userMessage ?: (e.message ?: "Unknown error")

    private fun writeExportTo(uri: Uri) {
        lifecycleScope.launch {
            val all = (application as VoiceCompanionApp).database.messageDao().getAllForExport()
            val json = com.google.gson.Gson().toJson(all)
            contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { it.write(json.toByteArray()) }
            }
            Snackbar.make(binding.root, "Export selesai", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearAllData() {
        AlertDialog.Builder(this)
            .setTitle("Hapus SEMUA data lokal AI?")
            .setMessage("Ini akan menghapus seluruh conversation history, karakter buatanmu (kecuali karakter bawaan), dan preferences. Model .task yang sudah diimport TIDAK dihapus (file terpisah).")
            .setPositiveButton("Hapus Semua") { _, _ ->
                lifecycleScope.launch {
                    (application as VoiceCompanionApp).database.messageDao().clearAll()
                    if (CompanionEngineHost.areEnginesReady()) CompanionEngineHost.chatRepository.clearTransientSession()
                    CompanionEngineHost.userPreferencesRepository.clearAll()
                    val repo = CompanionEngineHost.characterRepository
                    val currentCharacters = repo.observeAll().first()
                    currentCharacters.filter { !it.isBuiltIn }.forEach { repo.delete(it) }
                    Snackbar.make(binding.root, "Semua data lokal AI dihapus", Snackbar.LENGTH_LONG).show()
                    recreate()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun openBatteryGuidance() {
        AlertDialog.Builder(this)
            .setTitle("Battery optimization")
            .setMessage(
                "Supaya AI lebih jarang dimatikan Android/OEM saat di background:\n\n" +
                "1. Buka pengaturan baterai app ini (tombol di bawah).\n" +
                "2. Pilih \"Unrestricted\"/\"Tidak dibatasi\" kalau tersedia.\n" +
                "3. Untuk HP Xiaomi/Oppo/Vivo/Samsung, cek juga menu tambahan \"Autostart\" atau " +
                "\"App battery management\" di Settings sistem (nama beda-beda per OEM).\n\n" +
                "Ini TIDAK menjamin app 100% tidak pernah dimatikan -- itu keputusan akhir Android/OEM."
            )
            .setPositiveButton("Buka Pengaturan Baterai") { _, _ -> launchBatterySettings() }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun launchBatterySettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            } else {
                Intent(Settings.ACTION_SETTINGS)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Tidak bisa membuka pengaturan baterai di device ini", Snackbar.LENGTH_LONG).show()
        }
    }
}
