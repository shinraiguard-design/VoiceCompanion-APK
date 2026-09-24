package com.companionai.voiceapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.companionai.voiceapp.R
import com.companionai.voiceapp.data.CharacterEntity

class CharacterAdapter(
    private val activeId: () -> Long,
    private val onSelect: (CharacterEntity) -> Unit,
    private val onEdit: (CharacterEntity) -> Unit,
    private val onDuplicate: (CharacterEntity) -> Unit,
    private val onDelete: (CharacterEntity) -> Unit
) : ListAdapter<CharacterEntity, CharacterAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.characterName)
        val description: TextView = view.findViewById(R.id.characterDescription)
        val activeBadge: TextView = view.findViewById(R.id.activeBadge)
        val selectBtn: TextView = view.findViewById(R.id.selectButton)
        val editBtn: TextView = view.findViewById(R.id.editButton)
        val duplicateBtn: TextView = view.findViewById(R.id.duplicateButton)
        val deleteBtn: TextView = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_character, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.description.text = item.description.ifBlank { item.personality }
        holder.activeBadge.visibility = if (item.id == activeId()) View.VISIBLE else View.GONE
        holder.selectBtn.setOnClickListener { onSelect(item) }
        holder.editBtn.setOnClickListener { onEdit(item) }
        holder.duplicateBtn.setOnClickListener { onDuplicate(item) }
        holder.deleteBtn.visibility = if (item.isBuiltIn) View.GONE else View.VISIBLE
        holder.deleteBtn.setOnClickListener { onDelete(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CharacterEntity>() {
            override fun areItemsTheSame(oldItem: CharacterEntity, newItem: CharacterEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CharacterEntity, newItem: CharacterEntity) = oldItem == newItem
        }
    }
}
