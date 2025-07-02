package com.messagehub.data

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("message_hub", Context.MODE_PRIVATE)
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString("device_id", deviceId).apply()
    }
    
    fun getDeviceId(): String {
        return prefs.getString("device_id", "") ?: ""
    }
    
    fun saveMessages(messages: List<Message>) {
        _messages.value = messages.sortedByDescending { it.timestamp }
    }
    
    fun sendSms(recipient: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(recipient, null, message, null, null)
            Log.d("MessageRepository", "SMS sent to $recipient")
        } catch (e: Exception) {
            Log.e("MessageRepository", "Failed to send SMS", e)
        }
    }
}
