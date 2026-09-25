package com.companionai.voiceapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.companionai.voiceapp.data.CharacterEntity
import com.companionai.voiceapp.databinding.ActivityCharactersBinding
import com.companionai.voiceapp.engine.CompanionEngineHost
import com.companionai.voiceapp.ui.CharacterAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Layar "CHARACTERS" (spek bagian 7 & 8): create/edit/duplicate/delete/select. */
class CharactersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCharactersBinding
    private var currentActiveId: Long = -1
    private lateinit var adapter: CharacterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCharactersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Characters"

        CompanionEngineHost.ensureRepositoriesOnly(applicationContext)
        val repo = CompanionEngineHost.characterRepository

        adapter = CharacterAdapter(
            activeId = { currentActiveId },
            onSelect = { character ->
                repo.setActiveCharacterId(character.id)
                currentActiveId = character.id
                adapter.notifyDataSetChanged()
                Snackbar.make(binding.root, "${character.name} sekarang aktif", Snackbar.LENGTH_SHORT).show()
            },
            onEdit = { character ->
                startActivity(Intent(this, CharacterEditorActivity::class.java).putExtra(EXTRA_CHARACTER_ID, character.id))
            },
            onDuplicate = { character ->
                lifecycleScope.launch {
                    repo.duplicate(character)
                    Snackbar.make(binding.root, "Karakter diduplikat", Snackbar.LENGTH_SHORT).show()
                }
            },
            onDelete = { character ->
                AlertDialog.Builder(this)
                    .setTitle("Hapus ${character.name}?")
                    .setMessage("Karakter ini akan dihapus permanen.")
                    .setPositiveButton("Hapus") { _, _ ->
                        lifecycleScope.launch { repo.delete(character) }
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )

        binding.characterRecycler.layoutManager = LinearLayoutManager(this)
        binding.characterRecycler.adapter = adapter

        binding.newCharacterButton.setOnClickListener {
            startActivity(Intent(this, CharacterEditorActivity::class.java))
        }

        lifecycleScope.launch {
            repo.observeAll().collect { list: List<CharacterEntity> ->
                currentActiveId = repo.getActiveCharacterId()
                adapter.submitList(list)
            }
        }
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }
}
