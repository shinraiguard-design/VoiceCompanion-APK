package com.companionai.voiceapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.companionai.voiceapp.R

private const val TYPE_USER = 0
private const val TYPE_AI = 1

class MessageAdapter : ListAdapter<ChatMessageUi, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).role == "user") TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutRes = if (viewType == TYPE_USER) R.layout.item_message_user else R.layout.item_message_ai
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        holder.itemView.findViewById<TextView>(R.id.messageText).text = item.text
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessageUi>() {
            override fun areItemsTheSame(oldItem: ChatMessageUi, newItem: ChatMessageUi) =
                oldItem === newItem
            override fun areContentsTheSame(oldItem: ChatMessageUi, newItem: ChatMessageUi) =
                oldItem == newItem
        }
    }
}
