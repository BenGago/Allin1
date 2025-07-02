package com.messagehub.cache

import android.util.Log
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
    private val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"
    
    companion object {
        private const val TAG = "RedisManager"
        private const val MESSAGE_PREFIX = "msg:"
        private const val QUEUE_PREFIX = "queue:"
        private const val TYPING_PREFIX = "typing:"
        private const val USER_PREFIX = "user:"
        private const val STATS_PREFIX = "stats:"
    }
    
    suspend fun cacheMessage(messageId: String, messageData: String, ttlSeconds: Int = 3600): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$MESSAGE_PREFIX$messageId"
                setWithExpiry(key, messageData, ttlSeconds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache message: $messageId", e)
                false
            }
        }
    }
    
    suspend fun getCachedMessage(messageId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$MESSAGE_PREFIX$messageId"
                get(key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get cached message: $messageId", e)
                null
            }
        }
    }
    
    suspend fun queueMessage(platform: String, messageData: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val queueKey = "$QUEUE_PREFIX$platform"
                lpush(queueKey, messageData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to queue message for platform: $platform", e)
                false
            }
        }
    }
    
    suspend fun dequeueMessage(platform: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val queueKey = "$QUEUE_PREFIX$platform"
                rpop(queueKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dequeue message for platform: $platform", e)
                null
            }
        }
    }
    
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
    
    suspend fun setUserSession(userId: String, sessionData: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$USER_PREFIX$userId"
                set(key, sessionData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set user session", e)
                false
            }
        }
    }
    
    suspend fun getUserSession(userId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$USER_PREFIX$userId"
                get(key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get user session", e)
                null
            }
        }
    }
    
    suspend fun incrementMessageStats(platform: String, type: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val key = "$STATS_PREFIX$platform:$type"
                incr(key) > 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to increment stats", e)
                false
            }
        }
    }
    
    suspend fun getMessageStats(platform: String): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            try {
                val pattern = "$STATS_PREFIX$platform:*"
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
    
    private suspend fun incr(key: String): Long {
        return executeRedisCommand("INCR", listOf(key))?.toLongOrNull() ?: 0L
    }
    
    private suspend fun keys(pattern: String): List<String> {
        val result = executeRedisCommand("KEYS", listOf(pattern))
        return result?.split(",") ?: emptyList()
    }
    
    private suspend fun publish(channel: String, message: String): Boolean {
        return executeRedisCommand("PUBLISH", listOf(channel, message)) != null
    }
    
    private suspend fun executeRedisCommand(command: String, args: List<String>): String? {
        return try {
            val requestBody = json.encodeToString(mapOf(
                "command" to command,
                "args" to args
            )).toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$redisUrl/redis")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.e(TAG, "Redis command failed: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Redis HTTP request failed", e)
            null
        }
    }
}
