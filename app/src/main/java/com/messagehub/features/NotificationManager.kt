package com.messagehub.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.messagehub.MainActivity
import com.messagehub.R
import com.messagehub.data.EnhancedMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val CHANNEL_ID = "message_notifications"
        private const val NOTIFICATION_ID = 1001
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannel()
    }
    
    fun showMessageNotification(message: EnhancedMessage) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("message_id", message.id)
            putExtra("platform", message.platform)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val platformEmoji = when (message.platform) {
            "telegram" -> "✈️"
            "messenger" -> "💬"
            "twitter" -> "🐦"
            "sms" -> "📱"
            else -> "💌"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$platformEmoji ${message.platform.capitalize()} - ${message.sender}")
            .setContentText(message.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_reply,
                "Quick Reply",
                createQuickReplyIntent(message)
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_ID + message.id.hashCode(), notification)
    }
    
    private fun createQuickReplyIntent(message: EnhancedMessage): PendingIntent {
        val intent = Intent(context, QuickReplyReceiver::class.java).apply {
            putExtra("message_id", message.id)
            putExtra("platform", message.platform)
            putExtra("recipient_id", message.recipientId)
        }
        
        return PendingIntent.getBroadcast(
            context,
            message.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Message Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming messages"
            enableVibration(true)
            setShowBadge(true)
        }
        
        notificationManager.createNotificationChannel(channel)
    }
}

@AndroidEntryPoint
class MessageFirebaseService : FirebaseMessagingService() {
    
    @Inject
    lateinit var notificationManager: MessageNotificationManager
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Handle FCM message and show notification
        val title = remoteMessage.notification?.title ?: "New Message"
        val body = remoteMessage.notification?.body ?: ""
        
        // You can parse the message data and create EnhancedMessage
        // then call notificationManager.showMessageNotification(message)
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to your backend
    }
}
