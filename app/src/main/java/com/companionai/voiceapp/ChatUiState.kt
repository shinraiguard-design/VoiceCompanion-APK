package com.companionai.voiceapp.ui

/** Sesuai spek bagian 4 & 14: IDLE, LISTENING, PROCESSING, SPEAKING, + OFFLINE (AI mati) & ERROR. */
enum class CompanionStatus { IDLE, LISTENING, PROCESSING, SPEAKING, OFFLINE, ERROR }

data class ChatMessageUi(val role: String, val text: String)
