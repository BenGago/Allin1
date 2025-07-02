package com.messagehub.data

data class EnhancedMessage(
    val id: String,
    val platform: String,
    val sender: String,
    val content: String,
    val timestamp: String,
    val recipientId: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val attachments: List<Attachment> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val replyTo: String? = null,
    val isEdited: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    STICKER,
    LOCATION,
    CONTACT,
    VOICE_NOTE
}

data class Attachment(
    val id: String,
    val type: AttachmentType,
    val url: String,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null, // for audio/video
    val dimensions: Dimensions? = null // for images/videos
)

enum class AttachmentType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    STICKER,
    VOICE_NOTE
}

data class Dimensions(
    val width: Int,
    val height: Int
)

data class Reaction(
    val emoji: String,
    val userId: String,
    val userName: String,
    val timestamp: String
)

data class MessageReply(
    val platform: String,
    val recipientId: String,
    val content: String,
    val deviceId: String,
    val messageType: MessageType = MessageType.TEXT,
    val attachments: List<Attachment> = emptyList(),
    val replyTo: String? = null,
    val reactions: List<String> = emptyList() // emoji reactions to add
)

data class QuickReply(
    val id: String,
    val text: String,
    val payload: String? = null
)

data class MessageTemplate(
    val id: String,
    val name: String,
    val content: String,
    val platform: String? = null, // null means all platforms
    val quickReplies: List<QuickReply> = emptyList()
)
