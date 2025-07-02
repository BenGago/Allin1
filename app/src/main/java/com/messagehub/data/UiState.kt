package com.messagehub.data

data class UiState(
    val deviceId: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val typingUsers: Map<String, List<String>> = emptyMap(),
    val onlineUsers: List<User> = emptyList(),
    val queueStats: Map<String, Map<String, Long>> = emptyMap(),
    val error: String? = null
)
