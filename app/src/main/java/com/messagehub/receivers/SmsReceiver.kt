package com.messagehub.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.messagehub.data.MessageRepository
import com.messagehub.network.ApiService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var apiService: ApiService
    
    @Inject
    lateinit var messageRepository: MessageRepository
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            messages?.forEach { smsMessage ->
                val sender = smsMessage.originatingAddress ?: "Unknown"
                val content = smsMessage.messageBody ?: ""
                val timestamp = System.currentTimeMillis()
                
                Log.d("SmsReceiver", "Received SMS from $sender: $content")
                
                // Send to backend
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val deviceId = messageRepository.getDeviceId()
                        if (deviceId.isNotEmpty()) {
                            apiService.sendIncomingSms(
                                sender = sender,
                                message = content,
                                timestamp = timestamp,
                                deviceId = deviceId
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Failed to send SMS to backend", e)
                    }
                }
            }
        }
    }
}
