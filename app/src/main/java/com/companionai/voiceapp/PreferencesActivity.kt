package com.companionai.voiceapp

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.companionai.voiceapp.data.UserPreferences
import com.companionai.voiceapp.databinding.ActivityPreferencesBinding
import com.companionai.voiceapp.engine.CompanionEngineHost
import com.google.android.material.snackbar.Snackbar

/** Layar "MY PREFERENCES" (spek bagian 12). Data user, bukan data karakter. */
class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private val languageOptions = listOf("id", "en")
    private val responseLengthOptions = listOf("short", "medium", "long")
    private val emojiOptions = listOf("none", "low", "high")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "My Preferences"

        binding.spinnerLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageOptions)
        binding.spinnerResponseLength.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, responseLengthOptions)
        binding.spinnerEmojiLevel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, emojiOptions)

        CompanionEngineHost.ensureRepositoriesOnly(applicationContext)
        val repo = CompanionEngineHost.userPreferencesRepository
        val p = repo.get()

        binding.inputPreferredName.setText(p.preferredName)
        binding.spinnerLanguage.setSelection(languageOptions.indexOf(p.language).coerceAtLeast(0))
        binding.spinnerResponseLength.setSelection(responseLengthOptions.indexOf(p.responseLength).coerceAtLeast(0))
        binding.inputPreferredTone.setText(p.preferredTone)
        binding.spinnerEmojiLevel.setSelection(emojiOptions.indexOf(p.emojiLevel).coerceAtLeast(0))
        binding.switchVoiceEnabled.isChecked = p.voiceEnabled
        binding.switchAutoSpeak.isChecked = p.autoSpeak
        binding.switchMemoryEnabled.isChecked = p.memoryEnabled

        binding.savePreferencesButton.setOnClickListener {
            repo.save(
                UserPreferences(
                    preferredName = binding.inputPreferredName.text.toString(),
                    language = languageOptions[binding.spinnerLanguage.selectedItemPosition],
                    responseLength = responseLengthOptions[binding.spinnerResponseLength.selectedItemPosition],
                    preferredTone = binding.inputPreferredTone.text.toString(),
                    emojiLevel = emojiOptions[binding.spinnerEmojiLevel.selectedItemPosition],
                    voiceEnabled = binding.switchVoiceEnabled.isChecked,
                    autoSpeak = binding.switchAutoSpeak.isChecked,
                    memoryEnabled = binding.switchMemoryEnabled.isChecked
                )
            )
            Snackbar.make(binding.root, "Preferences disimpan", Snackbar.LENGTH_SHORT).show()
        }

        binding.deleteMyDataButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus data preferences?")
                .setMessage("Preferred name, tone, dan pengaturan lain di halaman ini akan dihapus.")
                .setPositiveButton("Hapus") { _, _ ->
                    repo.clearAll()
                    recreate()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }
}
