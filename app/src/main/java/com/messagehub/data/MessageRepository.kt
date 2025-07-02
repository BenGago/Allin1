package com.messagehub.data

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import com.messagehub.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("message_hub", Context.MODE_PRIVATE)
    private val smsManager = SmsManager.getDefault()
    
    companion object {
        private const val TAG = "MessageRepository"
        private const val DEVICE_ID_KEY = "device_id"
    }
    
    fun getDeviceId(): String {
        return prefs.getString(DEVICE_ID_KEY, "") ?: ""
    }
    
    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
    }
    
    suspend fun getAllMessages(): List<Message> {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = getDeviceId()
                if (deviceId.isNotEmpty()) {
                    apiService.getMessages(deviceId)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get messages", e)
                emptyList()
            }
        }
    }
    
    suspend fun saveMessages(messages: List<Message>) {
        withContext(Dispatchers.IO) {
            try {
                // In a real implementation, you would save to local database
                Log.d(TAG, "Saved ${messages.size} messages")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save messages", e)
            }
        }
    }
    
    suspend fun sendSms(recipient: String, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                smsManager.sendTextMessage(recipient, null, message, null, null)
                Log.d(TAG, "SMS sent to $recipient")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS", e)
                false
            }
        }
    }
    
    suspend fun saveMessage(message: Message) {
        withContext(Dispatchers.IO) {
            try {
                // Save to local database
                Log.d(TAG, "Message saved: ${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message", e)
            }
        }
    }
}
