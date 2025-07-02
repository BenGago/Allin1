package com.messagehub.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM", "New token: $token")
        sendTokenToBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "MessageHub"
        val body = remoteMessage.notification?.body ?: "You have a new message"
        showNotification(title, body)
    }

    private fun sendTokenToBackend(token: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("fcmToken", token)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://allin1-ehd2.onrender.com/api/device/update-token") // ✅ Your backend
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Log.d("FCM", "Token sent to backend")
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e("FCM", "Failed to send token: ${e.message}")
            }
        })
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "push_channel"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FCM Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your own icon
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)

        notificationManager.notify(123, builder.build())
    }
}
