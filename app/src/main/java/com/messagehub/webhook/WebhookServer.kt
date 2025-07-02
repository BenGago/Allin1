package com.messagehub.webhook

import android.content.Context
import android.util.Log
import com.messagehub.integrations.MessengerIntegration
import com.messagehub.integrations.TelegramIntegration
import com.messagehub.integrations.TwitterIntegration
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramIntegration: TelegramIntegration,
    private val messengerIntegration: MessengerIntegration,
    private val twitterIntegration: TwitterIntegration
) : NanoHTTPD(8080) {
    
    companion object {
        private const val TAG = "WebhookServer"
        private const val PORT = 8080
    }
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        
        Log.d(TAG, "Webhook received: $method $uri")
        
        return when {
            uri.startsWith("/webhook/telegram") -> handleTelegramWebhook(session)
            uri.startsWith("/webhook/messenger") -> handleMessengerWebhook(session)
            uri.startsWith("/webhook/twitter") -> handleTwitterWebhook(session)
            uri == "/health" -> newFixedLengthResponse("OK")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
    
    private fun handleTelegramWebhook(session: IHTTPSession): Response {
        return try {
            val body = getRequestBody(session)
            val json = JSONObject(body)
            
            scope.launch {
                telegramIntegration.handleWebhook(json)
            }
            
            newFixedLengthResponse("OK")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Telegram webhook", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error")
        }
    }
    
    private fun handleMessengerWebhook(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.GET -> {
                    // Webhook verification
                    val params = session.parms
                    val mode = params["hub.mode"]
                    val token = params["hub.verify_token"]
                    val challenge = params["hub.challenge"]
                    
                    if (mode == "subscribe" && token == "your_verify_token") {
                        newFixedLengthResponse(challenge ?: "")
                    } else {
                        newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
                    }
                }
                Method.POST -> {
                    val body = getRequestBody(session)
                    val json = JSONObject(body)
                    
                    scope.launch {
                        messengerIntegration.handleWebhook(json)
                    }
                    
                    newFixedLengthResponse("OK")
                }
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Messenger webhook", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error")
        }
    }
    
    private fun handleTwitterWebhook(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.GET -> {
                    // CRC challenge for Twitter
                    val params = session.parms
                    val crcToken = params["crc_token"]
                    if (crcToken != null) {
                        val response = twitterIntegration.generateCrcResponse(crcToken)
                        newFixedLengthResponse(Response.Status.OK, "application/json", response)
                    } else {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing CRC token")
                    }
                }
                Method.POST -> {
                    val body = getRequestBody(session)
                    val json = JSONObject(body)
                    
                    scope.launch {
                        twitterIntegration.handleWebhook(json)
                    }
                    
                    newFixedLengthResponse("OK")
                }
                else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Twitter webhook", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error")
        }
    }
    
    private fun getRequestBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }
    
    fun startServer() {
        try {
            start()
            Log.d(TAG, "Webhook server started on port $PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start webhook server", e)
        }
    }
    
    fun stopServer() {
        stop()
        Log.d(TAG, "Webhook server stopped")
    }
}
