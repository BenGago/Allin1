package com.messagehub.data

import kotlinx.serialization.Serializable

// Remove the userId field from Message since each installation is single-user
@Serializable
data class Message(
    val id: String,
    val platform: String,
    val sender: String,
    val content: String,
    val timestamp: String,
    val recipientId: String? = null,
    val messageType: String = "text",
    val attachments: List<String> = emptyList(),
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val replyToId: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val metadata: MutableMap<String, String>? = null
)

@Serializable
data class Reaction(
    val emoji: String,
    val userId: String,
    val timestamp: Long
)

// Remove the userId field from Chat since each installation is single-user
@Serializable
data class Chat(
    val id: String,
    val platform: String,
    val participants: List<String>,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// Update User to represent the single account owner
@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String,
    val profilePicture: String? = null,
    val encryptionKey: String? = null,
    val platformCredentials: Map<String, PlatformCredential> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val isRegistered: Boolean = false
)

@Serializable
data class PlatformCredential(
    val platform: String,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val isEnabled: Boolean = true,
    val lastSyncTime: Long = 0
)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val type: String,
    val userId: String,
    val isActive: Boolean = true,
    val lastSyncAt: String? = null,
    val pushToken: String? = null
)

@Serializable
data class PlatformConfig(
    val platform: String,
    val isEnabled: Boolean,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val accessToken: String? = null,
    val webhookUrl: String? = null,
    val lastSyncTime: Long = 0
)

@Serializable
data class MessageStats(
    val platform: String,
    val userId: String, // Added for user separation
    val totalSent: Long = 0,
    val totalReceived: Long = 0,
    val totalFailed: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

// Remove userId from UiState since it's single-user
@Serializable
data class UiState(
    val deviceId: String = "",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val typingUsers: Map<String, List<String>> = emptyMap(),
    val onlineUsers: List<User> = emptyList(),
    val queueStats: Map<String, Map<String, Long>> = emptyMap(),
    val error: String? = null,
    val isUserRegistered: Boolean = false
)
