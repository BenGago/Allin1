package com.messagehub.data

data class Message(
    val id: String,
    val platform: String, // "sms", "telegram", "messenger", "twitter"
    val sender: String,
    val content: String,
    val timestamp: String,
    val recipientId: String? = null
)

data class OutgoingSms(
    val id: String,
    val recipient: String,
    val message: String
)

data class UiState(
    val deviceId: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false
)
