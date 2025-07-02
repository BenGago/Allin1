package com.messagehub.data

import kotlinx.serialization.Serializable

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
    val reactions: List<Reaction> = emptyList()
)

@Serializable
data class Reaction(
    val emoji: String,
    val userId: String,
    val timestamp: Long
)

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

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val platform: String,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val profilePicture: String? = null
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
    val totalSent: Long = 0,
    val totalReceived: Long = 0,
    val totalFailed: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
