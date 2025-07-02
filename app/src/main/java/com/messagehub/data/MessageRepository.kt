package com.messagehub.data

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.messagehub.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {
    
    companion object {
        private const val TAG = "MessageRepository"
        private const val PREFS_NAME = "messaging_hub_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, "")
        if (deviceId.isNullOrEmpty()) {
            deviceId = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }
    
    private fun generateDeviceId(): String {
        return "device_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
    
    suspend fun registerDevice(deviceName: String = "Android Device"): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId()
                val fcmToken = getFCMToken()
                
                apiService.registerDevice(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    fcmToken = fcmToken
                )
                
                Log.d(TAG, "Device registered successfully: $deviceId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register device", e)
                false
            }
        }
    }
    
    suspend fun getFCMToken(): String {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
            Log.d(TAG, "FCM token obtained: ${token.take(20)}...")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token", e)
            prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        }
    }
    
    suspend fun updateFCMToken(token: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
                
                apiService.updateFCMToken(
                    deviceId = getDeviceId(),
                    fcmToken = token
                )
                
                Log.d(TAG, "FCM token updated successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
                false
            }
        }
    }
    
    suspend fun sendIncomingSms(sender: String, message: String, timestamp: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                apiService.sendIncomingSms(
                    sender = sender,
                    message = message,
                    timestamp = timestamp,
                    deviceId = getDeviceId()
                )
                
                Log.d(TAG, "Incoming SMS sent to server: $sender")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send incoming SMS to server", e)
                false
            }
        }
    }
    
    suspend fun saveIncomingMessage(
        platform: String,
        sender: String,
        message: String,
        timestamp: Long,
        messageType: String = "text",
        filePath: String? = null,
        metadata: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                apiService.sendIncomingMessage(
                    platform = platform,
                    sender = sender,
                    message = message,
                    timestamp = timestamp,
                    deviceId = getDeviceId(),
                    messageType = messageType,
                    filePath = filePath,
                    metadata = metadata
                )
                
                Log.d(TAG, "Incoming message saved: $platform - $sender")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save incoming message", e)
                false
            }
        }
    }
    
    suspend fun getMessages(platform: String? = null, limit: Int = 50, offset: Int = 0): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val messages = apiService.getMessages(
                    deviceId = getDeviceId(),
                    platform = platform,
                    limit = limit,
                    offset = offset
                )
                
                Log.d(TAG, "Retrieved ${messages.size} messages")
                messages
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get messages", e)
                emptyList()
            }
        }
    }
    
    suspend fun sendReply(
        platform: String,
        recipientId: String,
        message: String,
        messageType: String = "text"
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                apiService.sendReply(
                    platform = platform,
                    recipientId = recipientId,
                    message = message,
                    deviceId = getDeviceId(),
                    messageType = messageType
                )
                
                Log.d(TAG, "Reply sent via $platform to $recipientId")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply", e)
                false
            }
        }
    }
    
    suspend fun sendSms(recipient: String, message: String): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                val smsManager = SmsManager.getDefault()
                
                // Split long messages
                val parts = smsManager.divideMessage(message)
                
                if (parts.size == 1) {
                    smsManager.sendTextMessage(recipient, null, message, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
                }
                
                Log.d(TAG, "SMS sent to $recipient")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS", e)
                false
            }
        }
    }
    
    suspend fun getOutgoingSms(): List<OutgoingSms> {
        return withContext(Dispatchers.IO) {
            try {
                val smsQueue = apiService.getOutgoingSms(getDeviceId())
                Log.d(TAG, "Retrieved ${smsQueue.size} outgoing SMS")
                smsQueue
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get outgoing SMS", e)
                emptyList()
            }
        }
    }
    
    suspend fun markSmsAsSent(id: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                apiService.markSmsAsSent(id)
                Log.d(TAG, "SMS marked as sent: $id")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark SMS as sent", e)
                false
            }
        }
    }
    
    suspend fun getAIVoiceReply(
        message: String,
        context: String? = null,
        mood: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getAIVoiceReply(
                    message = message,
                    deviceId = getDeviceId(),
                    context = context,
                    mood = mood
                )
                
                Log.d(TAG, "AI voice reply generated")
                response.reply
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get AI voice reply", e)
                null
            }
        }
    }
    
    suspend fun detectMood(message: String): MoodResult? {
        return withContext(Dispatchers.IO) {
            try {
                val result = apiService.detectMood(
                    message = message,
                    deviceId = getDeviceId()
                )
                
                Log.d(TAG, "Mood detected: ${result.mood} (${result.confidence})")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to detect mood", e)
                null
            }
        }
    }
    
    suspend fun sendTestNotification(title: String, body: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                apiService.sendTestNotification(
                    deviceId = getDeviceId(),
                    title = title,
                    body = body
                )
                
                Log.d(TAG, "Test notification sent")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send test notification", e)
                false
            }
        }
    }
}

// Data classes for API responses
data class Message(
    val id: Int,
    val deviceId: String,
    val platform: String,
    val sender: String,
    val recipient: String?,
    val message: String,
    val timestamp: Long,
    val messageType: String = "text",
    val filePath: String?,
    val metadata: String?,
    val mood: String?,
    val aiResponse: String?,
    val createdAt: String
)

data class OutgoingSms(
    val id: String,
    val deviceId: String,
    val recipient: String,
    val message: String,
    val status: String,
    val createdAt: String
)

data class AIVoiceReplyResponse(
    val success: Boolean,
    val reply: String,
    val model: String,
    val timestamp: Long
)

data class MoodResult(
    val success: Boolean,
    val mood: String,
    val confidence: Float,
    val timestamp: Long
)

data class NotificationResponse(
    val success: Boolean,
    val messageId: String?
)
