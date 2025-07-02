package com.messagehub.features

import android.content.Context
import android.net.Uri
import android.util.Log
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository
) {
    
    companion object {
        private const val TAG = "ChatExportManager"
    }
    
    suspend fun exportChatHistory(
        platform: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        format: ExportFormat = ExportFormat.JSON
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val messages = messageRepository.getMessagesForExport(platform, startDate, endDate)
            
            val fileName = generateFileName(platform, format)
            val file = File(context.getExternalFilesDir(null), fileName)
            
            when (format) {
                ExportFormat.JSON -> exportAsJson(messages, file)
                ExportFormat.CSV -> exportAsCsv(messages, file)
                ExportFormat.HTML -> exportAsHtml(messages, file)
            }
            
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export chat history", e)
            null
        }
    }
    
    private fun exportAsJson(messages: List<EnhancedMessage>, file: File) {
        val json = Json { prettyPrint = true }
        val jsonString = json.encodeToString(messages)
        file.writeText(jsonString)
    }
    
    private fun exportAsCsv(messages: List<EnhancedMessage>, file: File) {
        FileWriter(file).use { writer ->
            // CSV Header
            writer.append("ID,Platform,Sender,Content,Timestamp,Message Type,Attachments Count,Reactions Count\n")
            
            messages.forEach { message ->
                writer.append("${message.id},")
                writer.append("${message.platform},")
                writer.append("\"${message.sender.replace("\"", "\"\"")}\",")
                writer.append("\"${message.content.replace("\"", "\"\"")}\",")
                writer.append("${message.timestamp},")
                writer.append("${message.messageType},")
                writer.append("${message.attachments.size},")
                writer.append("${message.reactions.size}\n")
            }
        }
    }
    
    private fun exportAsHtml(messages: List<EnhancedMessage>, file: File) {
        val html = buildString {
            append("<!DOCTYPE html>\n")
            append("<html><head><title>Chat Export</title>")
            append("<style>")
            append("body { font-family: Arial, sans-serif; margin: 20px; }")
            append(".message { margin: 10px 0; padding: 10px; border-left: 3px solid #ccc; }")
            append(".telegram { border-left-color: #0088cc; }")
            append(".messenger { border-left-color: #0084ff; }")
            append(".twitter { border-left-color: #1da1f2; }")
            append(".sms { border-left-color: #34c759; }")
            append(".sender { font-weight: bold; color: #333; }")
            append(".timestamp { font-size: 0.8em; color: #666; }")
            append(".content { margin: 5px 0; }")
            append("</style></head><body>")
            append("<h1>Chat Export</h1>")
            
            messages.forEach { message ->
                append("<div class=\"message ${message.platform}\">")
                append("<div class=\"sender\">[${message.platform.uppercase()}] ${message.sender}</div>")
                append("<div class=\"content\">${message.content}</div>")
                append("<div class=\"timestamp\">${formatTimestamp(message.timestamp)}</div>")
                append("</div>")
            }
            
            append("</body></html>")
        }
        
        file.writeText(html)
    }
    
    private fun generateFileName(platform: String?, format: ExportFormat): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val platformSuffix = platform?.let { "_$it" } ?: "_all"
        return "chat_export$platformSuffix$timestamp.${format.extension}"
    }
    
    private fun formatTimestamp(timestamp: String): String {
        return try {
            val date = Date(timestamp.toLong())
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            timestamp
        }
    }
}

enum class ExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv"),
    HTML("html")
}
