package com.messagehub.cache

import android.util.Log
import com.messagehub.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedisManager @Inject constructor() {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "RedisManager"
        private const val MESSAGE_PREFIX = "msg:"
        private const val QUEUE_PREFIX = "queue:"
        private const val TYPING_PREFIX = "typing:"
        private const val USER_PREFIX = "user:"
        private const val STATS_PREFIX = "stats:"
        private const val SESSION_PREFIX = "session:"
    }
    
    private fun getRedisUrl(): String {
        return System.getenv("REDIS_URL") ?: ""
    }
    
    private fun buildRedisRequest(command: String, vararg args: String): Request {
        val redisUrl = getRedisUrl()
        if (redisUrl.isEmpty()) {
            throw IllegalStateException("REDIS_URL environment variable not set")
        }
        
        val body = buildRedisCommand(command, *args)
        
        return Request.Builder()
            .url("$redisUrl/command")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()
    }
    
    private fun buildRedisCommand(command: String, vararg args: String): String {
        val commandArray = listOf(command) + args.toList()
        return json.encodeToString(commandArray)
    }
    
    // User-specific message caching
    suspend fun cacheMessage(message: Message, ttlSeconds: Int = 3600): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$MESSAGE_PREFIX${message.userId}:${message.id}"
                val messageJson = json.encodeToString(message)
                setWithExpiry(key, messageJson, ttlSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache message: ${message.id}", e)
                false
            }
        }
    }
    
    suspend fun getCachedMessage(userId: String, messageId: String): Message? {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$MESSAGE_PREFIX$userId:$messageId"
                val messageJson = get(key)
                messageJson?.let { json.decodeFromString<Message>(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cached message: $messageId", e)
                null
            }
        }
    }
    
    suspend fun getCachedMessagesForUser(userId: String): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val pattern = "$MESSAGE_PREFIX$userId:*"
                val keys = keys(pattern)
                val messages = mutableListOf<Message>()
                
                keys.forEach { key ->
                    get(key)?.let { messageJson ->
                        try {
                            val message = json.decodeFromString<Message>(messageJson)
                            messages.add(message)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse cached message", e)
                        }
                    }
                }
                
                messages.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cached messages for user: $userId", e)
                emptyList()
            }
        }
    }
    
    // User-specific message queuing
    suspend fun queueMessage(message: Message): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queueKey = "$QUEUE_PREFIX${message.userId}:${message.platform}"
                val messageJson = json.encodeToString(message)
                lpush(queueKey, messageJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue message for user: ${message.userId}", e)
                false
            }
        }
    }
    
    suspend fun dequeueMessage(userId: String, platform: String): Message? {
        return withContext(Dispatchers.IO) {
            try {
                val queueKey = "$QUEUE_PREFIX$userId:$platform"
                val messageJson = rpop(queueKey)
                messageJson?.let { json.decodeFromString<Message>(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dequeue message for user: $userId", e)
                null
            }
        }
    }
    
    suspend fun getQueueSize(userId: String, platform: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val queueKey = "$QUEUE_PREFIX$userId:$platform"
                llen(queueKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get queue size", e)
                0L
            }
        }
    }
    
    // User-specific typing indicators
    suspend fun setTypingIndicator(userId: String, chatId: String, isTyping: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$TYPING_PREFIX$chatId:$userId"
                if (isTyping) {
                    setWithExpiry(key, "typing", 10) // 10 seconds TTL
                } else {
                    delete(key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set typing indicator", e)
                false
            }
        }
    }
    
    suspend fun getTypingUsers(chatId: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val pattern = "$TYPING_PREFIX$chatId:*"
                val keys = keys(pattern)
                keys.mapNotNull { key ->
                    key.substringAfterLast(":")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get typing users", e)
                emptyList()
            }
        }
    }
    
    // User session management
    suspend fun setUserSession(userId: String, sessionData: String, ttlSeconds: Int = 86400): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$SESSION_PREFIX$userId"
                setWithExpiry(key, sessionData, ttlSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set user session", e)
                false
            }
        }
    }
    
    suspend fun getUserSession(userId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$SESSION_PREFIX$userId"
                get(key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get user session", e)
                null
            }
        }
    }
    
    suspend fun removeUserSession(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$SESSION_PREFIX$userId"
                delete(key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove user session", e)
                false
            }
        }
    }
    
    // User-specific statistics
    suspend fun incrementMessageStats(userId: String, platform: String, type: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$STATS_PREFIX$userId:$platform:$type"
                incr(key) > 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to increment stats", e)
                false
            }
        }
    }
    
    suspend fun getMessageStats(userId: String, platform: String): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            try {
                val pattern = "$STATS_PREFIX$userId:$platform:*"
                val keys = keys(pattern)
                val stats = mutableMapOf<String, Long>()
                
                keys.forEach { key ->
                    val type = key.substringAfterLast(":")
                    val count = get(key)?.toLongOrNull() ?: 0L
                    stats[type] = count
                }
                
                stats
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get message stats", e)
                emptyMap()
            }
        }
    }
    
    suspend fun getAllUserStats(userId: String): Map<String, Map<String, Long>> {
        return withContext(Dispatchers.IO) {
            try {
                val pattern = "$STATS_PREFIX$userId:*"
                val keys = keys(pattern)
                val allStats = mutableMapOf<String, MutableMap<String, Long>>()
                
                keys.forEach { key ->
                    val parts = key.split(":")
                    if (parts.size >= 4) {
                        val platform = parts[2]
                        val type = parts[3]
                        val count = get(key)?.toLongOrNull() ?: 0L
                        
                        allStats.getOrPut(platform) { mutableMapOf() }[type] = count
                    }
                }
                
                allStats
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get all user stats", e)
                emptyMap()
            }
        }
    }
    
    // Pub/Sub for real-time updates
    suspend fun publishMessage(channel: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                publish(channel, message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish message", e)
                false
            }
        }
    }
    
    suspend fun publishUserMessage(userId: String, message: Message): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val channel = "user:$userId:messages"
                val messageJson = json.encodeToString(message)
                publish(channel, messageJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish user message", e)
                false
            }
        }
    }
    
    // Cache cleanup
    suspend fun clearUserCache(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val patterns = listOf(
                    "$MESSAGE_PREFIX$userId:*",
                    "$QUEUE_PREFIX$userId:*",
                    "$SESSION_PREFIX$userId",
                    "$STATS_PREFIX$userId:*"
                )
                
                var success = true
                patterns.forEach { pattern ->
                    val keys = keys(pattern)
                    if (keys.isNotEmpty()) {
                        keys.forEach { key ->
                            if (!delete(key)) {
                                success = false
                            }
                        }
                    }
                }
                
                success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear user cache", e)
                false
            }
        }
    }
    
    // Redis HTTP API calls
    private suspend fun set(key: String, value: String): Boolean {
        return executeRedisCommand("SET", listOf(key, value)) != null
    }
    
    private suspend fun setWithExpiry(key: String, value: String, ttlSeconds: Int): Boolean {
        return executeRedisCommand("SETEX", listOf(key, ttlSeconds.toString(), value)) != null
    }
    
    private suspend fun get(key: String): String? {
        return executeRedisCommand("GET", listOf(key))
    }
    
    private suspend fun delete(key: String): Boolean {
        return executeRedisCommand("DEL", listOf(key)) != null
    }
    
    private suspend fun lpush(key: String, value: String): Boolean {
        return executeRedisCommand("LPUSH", listOf(key, value)) != null
    }
    
    private suspend fun rpop(key: String): String? {
        return executeRedisCommand("RPOP", listOf(key))
    }
    
    private suspend fun llen(key: String): Long {
        return executeRedisCommand("LLEN", listOf(key))?.toLongOrNull() ?: 0L
    }
    
    private suspend fun incr(key: String): Long {
        return executeRedisCommand("INCR", listOf(key))?.toLongOrNull() ?: 0L
    }
    
    private suspend fun keys(pattern: String): List<String> {
        val result = executeRedisCommand("KEYS", listOf(pattern))
        return if (result.isNullOrEmpty() || result == "null") {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<String>>(result)
            } catch (e: Exception) {
                result.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }
    
    private suspend fun publish(channel: String, message: String): Boolean {
        return executeRedisCommand("PUBLISH", listOf(channel, message)) != null
    }
    
    private suspend fun executeRedisCommand(command: String, args: List<String>): String? {
        return try {
            val request = buildRedisRequest(command, *args.toTypedArray())
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody == "null" || responseBody.isNullOrEmpty()) {
                    null
                } else {
                    responseBody
                }
            } else {
                Log.e(TAG, "Redis command failed: ${response.code} - ${response.message}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Redis HTTP request failed for command: $command", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error executing Redis command: $command", e)
            null
        }
    }
}
