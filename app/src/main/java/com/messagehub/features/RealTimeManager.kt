package com.messagehub.features

import android.util.Log
import com.messagehub.cache.RedisManager
import com.messagehub.data.Message
import com.messagehub.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TypingEvent(
    val userId: String,
    val chatId: String,
    val isTyping: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PresenceEvent(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
data class MessageEvent(
    val message: Message,
    val eventType: String = "new_message",
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class RealTimeManager @Inject constructor(
    private val redisManager: RedisManager
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _incomingMessages = MutableStateFlow<List<Message>>(emptyList())
    val incomingMessages: StateFlow<List<Message>> = _incomingMessages.asStateFlow()
    
    private val _typingUsers = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, List<String>>> = _typingUsers.asStateFlow()
    
    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers.asStateFlow()
    
    private val _userPresence = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val userPresence: StateFlow<Map<String, Boolean>> = _userPresence.asStateFlow()
    
    companion object {
        private const val TAG = "RealTimeManager"
        private const val MESSAGE_CHANNEL = "messages"
        private const val TYPING_CHANNEL = "typing"
        private const val PRESENCE_CHANNEL = "presence"
        private const val USER_CHANNEL_PREFIX = "user:"
    }
    
    private var isListening = false
    
    fun startRealTimeUpdates(currentUserId: String) {
        if (isListening) {
            Log.d(TAG, "Real-time updates already started")
            return
        }
        
        isListening = true
        scope.launch {
            try {
                // Start all listeners
                launch { startMessageListener(currentUserId) }
                launch { startTypingListener() }
                launch { startPresenceListener() }
                launch { startUserSpecificListener(currentUserId) }
                
                Log.d(TAG, "Real-time listeners started for user: $currentUserId")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting real-time listeners", e)
                isListening = false
            }
        }
    }
    
    private suspend fun startMessageListener(currentUserId: String) {
        while (isListening && scope.isActive) {
            try {
                // Check for new messages in user's queues
                val platforms = listOf("telegram", "messenger", "twitter", "sms")
                val newMessages = mutableListOf<Message>()
                
                platforms.forEach { platform ->
                    redisManager.dequeueMessage(currentUserId, platform)?.let { message ->
                        newMessages.add(message)
                    }
                }
                
                if (newMessages.isNotEmpty()) {
                    val currentMessages = _incomingMessages.value.toMutableList()
                    currentMessages.addAll(0, newMessages)
                    _incomingMessages.value = currentMessages
                    
                    Log.d(TAG, "Received ${newMessages.size} new messages for user: $currentUserId")
                }
                
                delay(1000) // Check every second
            } catch (e: Exception) {
                Log.e(TAG, "Error in message listener", e)
                delay(5000) // Wait longer on error
            }
        }
    }
    
    private suspend fun startTypingListener() {
        while (isListening && scope.isActive) {
            try {
                // Get typing indicators for all active chats
                val activeChats = getActiveChats()
                val typingMap = mutableMapOf<String, List<String>>()
                
                activeChats.forEach { chatId ->
                    val typingUsers = redisManager.getTypingUsers(chatId)
                    if (typingUsers.isNotEmpty()) {
                        typingMap[chatId] = typingUsers
                    }
                }
                
                _typingUsers.value = typingMap
                delay(2000) // Check every 2 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error in typing listener", e)
                delay(5000)
            }
        }
    }
    
    private suspend fun startPresenceListener() {
        while (isListening && scope.isActive) {
            try {
                // This would typically listen to Redis pub/sub for presence updates
                // For now, we'll simulate by checking user sessions
                val onlineUserIds = getOnlineUserIds()
                val presenceMap = mutableMapOf<String, Boolean>()
                
                onlineUserIds.forEach { userId ->
                    presenceMap[userId] = true
                }
                
                _userPresence.value = presenceMap
                delay(10000) // Check every 10 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error in presence listener", e)
                delay(15000)
            }
        }
    }
    
    private suspend fun startUserSpecificListener(userId: String) {
        while (isListening && scope.isActive) {
            try {
                // Listen for user-specific events
                // This would typically use Redis pub/sub
                delay(5000) // Placeholder polling
            } catch (e: Exception) {
                Log.e(TAG, "Error in user-specific listener", e)
                delay(10000)
            }
        }
    }
    
    suspend fun broadcastMessage(message: Message): Boolean {
        return try {
            // Cache the message
            redisManager.cacheMessage(message)
            
            // Publish to general message channel
            val messageEvent = MessageEvent(message)
            val eventJson = json.encodeToString(messageEvent)
            redisManager.publishMessage(MESSAGE_CHANNEL, eventJson)
            
            // Publish to user-specific channel
            redisManager.publishUserMessage(message.userId, message)
            
            Log.d(TAG, "Message broadcasted: ${message.id} for user: ${message.userId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast message", e)
            false
        }
    }
    
    suspend fun setTypingIndicator(userId: String, chatId: String, isTyping: Boolean): Boolean {
        return try {
            // Set typing indicator in Redis
            redisManager.setTypingIndicator(userId, chatId, isTyping)
            
            // Broadcast typing event
            val typingEvent = TypingEvent(userId, chatId, isTyping)
            val eventJson = json.encodeToString(typingEvent)
            redisManager.publishMessage(TYPING_CHANNEL, eventJson)
            
            Log.d(TAG, "Typing indicator set: $userId in $chatId = $isTyping")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set typing indicator", e)
            false
        }
    }
    
    suspend fun updateUserPresence(user: User, isOnline: Boolean): Boolean {
        return try {
            // Update user session
            val sessionData = json.encodeToString(mapOf(
                "userId" to user.id,
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis(),
                "displayName" to user.displayName
            ))
            
            if (isOnline) {
                redisManager.setUserSession(user.id, sessionData, 300) // 5 minutes TTL
            } else {
                redisManager.removeUserSession(user.id)
            }
            
            // Broadcast presence event
            val presenceEvent = PresenceEvent(user.id, isOnline)
            val eventJson = json.encodeToString(presenceEvent)
            redisManager.publishMessage(PRESENCE_CHANNEL, eventJson)
            
            // Update local state
            val currentPresence = _userPresence.value.toMutableMap()
            currentPresence[user.id] = isOnline
            _userPresence.value = currentPresence
            
            Log.d(TAG, "User presence updated: ${user.id} = $isOnline")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user presence", e)
            false
        }
    }
    
    suspend fun cacheMessage(message: Message): Boolean {
        return try {
            redisManager.cacheMessage(message, 3600) // Cache for 1 hour
            Log.d(TAG, "Message cached: ${message.id}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache message", e)
            false
        }
    }
    
    suspend fun getCachedMessage(userId: String, messageId: String): Message? {
        return try {
            redisManager.getCachedMessage(userId, messageId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cached message", e)
            null
        }
    }
    
    suspend fun getCachedMessagesForUser(userId: String): List<Message> {
        return try {
            redisManager.getCachedMessagesForUser(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cached messages for user", e)
            emptyList()
        }
    }
    
    suspend fun queueMessage(message: Message): Boolean {
        return try {
            redisManager.queueMessage(message)
            Log.d(TAG, "Message queued: ${message.id} for user: ${message.userId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue message", e)
            false
        }
    }
    
    fun getTypingUsersForChat(chatId: String): List<String> {
        return _typingUsers.value[chatId] ?: emptyList()
    }
    
    fun isUserOnline(userId: String): Boolean {
        return _userPresence.value[userId] ?: false
    }
    
    private suspend fun getActiveChats(): List<String> {
        // In a real implementation, this would get active chats from database
        // For now, return some default chat IDs
        return listOf("general", "support", "announcements")
    }
    
    private suspend fun getOnlineUserIds(): List<String> {
        // In a real implementation, this would query Redis for active sessions
        return try {
            // This is a placeholder - would need to implement Redis SCAN for session keys
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get online user IDs", e)
            emptyList()
        }
    }
    
    fun stopRealTimeUpdates() {
        isListening = false
        scope.coroutineContext.cancel()
        Log.d(TAG, "Real-time updates stopped")
    }
    
    fun clearMessages() {
        _incomingMessages.value = emptyList()
    }
    
    suspend fun getUserStats(userId: String): Map<String, Map<String, Long>> {
        return try {
            redisManager.getAllUserStats(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user stats", e)
            emptyMap()
        }
    }
}
