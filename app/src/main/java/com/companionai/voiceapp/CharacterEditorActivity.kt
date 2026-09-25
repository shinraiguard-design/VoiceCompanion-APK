package com.companionai.voiceapp

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.companionai.voiceapp.data.CharacterEntity
import com.companionai.voiceapp.databinding.ActivityCharacterEditorBinding
import com.companionai.voiceapp.engine.CompanionEngineHost
import kotlinx.coroutines.launch

/**
 * Layar Create/Edit Character (spek bagian 7, 8, 9).
 * Semua field ini yang membentuk system prompt lewat PromptBuilder -- tidak
 * ada personality yang di-hardcode di source code.
 */
class CharacterEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCharacterEditorBinding
    private var editingId: Long = -1
    private var editingBuiltIn = false
    private var editingBuiltInKey = ""

    private val languageOptions = listOf("id", "en")
    private val responseLengthOptions = listOf("short", "medium", "long")
    private val emojiOptions = listOf("none", "low", "high")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCharacterEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spinnerLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageOptions)
        binding.spinnerResponseLength.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, responseLengthOptions)
        binding.spinnerEmojiLevel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, emojiOptions)

        editingId = intent.getLongExtra(CharactersActivity.EXTRA_CHARACTER_ID, -1)
        title = if (editingId >= 0) "Edit Character" else "New Character"

        CompanionEngineHost.ensureRepositoriesOnly(applicationContext)

        if (editingId >= 0) loadExisting(editingId) else binding.resetCharacterButton.visibility = android.view.View.GONE

        binding.saveCharacterButton.setOnClickListener { save() }
        binding.resetCharacterButton.setOnClickListener { confirmResetToDefault() }
    }

    private fun loadExisting(id: Long) {
        lifecycleScope.launch {
            val c = CompanionEngineHost.characterRepository.getById(id) ?: return@launch
            editingBuiltIn = c.isBuiltIn
            editingBuiltInKey = c.builtInKey
            populateForm(c)
            binding.resetCharacterButton.visibility =
                if (c.isBuiltIn) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun save() {
        val name = binding.inputName.text.toString().trim()
        if (name.isBlank()) {
            binding.inputName.error = "Nama wajib diisi"
            return
        }

        val entity = CharacterEntity(
            id = if (editingId >= 0) editingId else 0,
            name = name,
            nickname = binding.inputNickname.text.toString(),
            description = binding.inputDescription.text.toString(),
            personality = binding.inputPersonality.text.toString(),
            speakingStyle = binding.inputSpeakingStyle.text.toString(),
            tone = binding.inputTone.text.toString(),
            mood = binding.inputMood.text.toString(),
            language = languageOptions[binding.spinnerLanguage.selectedItemPosition],
            backstory = binding.inputBackstory.text.toString(),
            likes = binding.inputLikes.text.toString(),
            dislikes = binding.inputDislikes.text.toString(),
            behaviorRules = binding.inputBehaviorRules.text.toString(),
            neverDoRules = binding.inputNeverDoRules.text.toString(),
            conversationRules = binding.inputConversationRules.text.toString(),
            greeting = binding.inputGreeting.text.toString(),
            systemInstructions = binding.inputSystemInstructions.text.toString(),
            customPrompt = binding.inputCustomPrompt.text.toString(),
            useAdvancedPrompt = binding.switchAdvancedPrompt.isChecked,
            responseLength = responseLengthOptions[binding.spinnerResponseLength.selectedItemPosition],
            emojiLevel = emojiOptions[binding.spinnerEmojiLevel.selectedItemPosition],
            isBuiltIn = editingBuiltIn,
            builtInKey = editingBuiltInKey
        )

        lifecycleScope.launch {
            val repo = CompanionEngineHost.characterRepository
            if (editingId >= 0) {
                repo.update(entity)
            } else {
                val newId = repo.create(entity)
                // Kalau ini karakter pertama yang pernah dibuat, langsung jadikan aktif.
                if (repo.getActiveCharacterId() < 0) repo.setActiveCharacterId(newId)
            }
            finish()
        }
    }

    private fun confirmResetToDefault() {
        AlertDialog.Builder(this)
            .setTitle("Reset karakter ini?")
            .setMessage("Semua field akan dikembalikan ke nilai default bawaan (perubahanmu akan hilang setelah disimpan).")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    val current = CompanionEngineHost.characterRepository.getById(editingId) ?: return@launch
                    if (current.builtInKey.isBlank()) return@launch // bukan karakter bawaan, tidak ada template
                    val template = CompanionEngineHost.characterRepository.builtInTemplate(current.builtInKey)
                    populateForm(template)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun populateForm(c: CharacterEntity) {
        with(binding) {
            inputName.setText(c.name)
            inputNickname.setText(c.nickname)
            inputDescription.setText(c.description)
            inputPersonality.setText(c.personality)
            inputSpeakingStyle.setText(c.speakingStyle)
            inputTone.setText(c.tone)
            inputMood.setText(c.mood)
            spinnerLanguage.setSelection(languageOptions.indexOf(c.language).coerceAtLeast(0))
            inputBackstory.setText(c.backstory)
            inputLikes.setText(c.likes)
            inputDislikes.setText(c.dislikes)
            inputBehaviorRules.setText(c.behaviorRules)
            inputNeverDoRules.setText(c.neverDoRules)
            inputConversationRules.setText(c.conversationRules)
            inputGreeting.setText(c.greeting)
            switchAdvancedPrompt.isChecked = c.useAdvancedPrompt
            inputSystemInstructions.setText(c.systemInstructions)
            inputCustomPrompt.setText(c.customPrompt)
            spinnerResponseLength.setSelection(responseLengthOptions.indexOf(c.responseLength).coerceAtLeast(0))
            spinnerEmojiLevel.setSelection(emojiOptions.indexOf(c.emojiLevel).coerceAtLeast(0))
        }
    }
}
