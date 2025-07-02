package com.messagehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.messagehub.data.Attachment

@Composable
fun EnhancedMessageInput(
    onSendMessage: (String, List<Attachment>) -> Unit,
    onTyping: (Boolean) -> Unit,
    onEmojiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    
    // Handle typing indicator
    LaunchedEffect(messageText) {
        val newIsTyping = messageText.isNotEmpty()
        if (newIsTyping != isTyping) {
            isTyping = newIsTyping
            onTyping(newIsTyping)
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Emoji Button
            IconButton(onClick = onEmojiClick) {
                Icon(Icons.Default.EmojiEmotions, "Emoji")
            }
            
            // Message Input Field
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            
            // Voice Reply Button
            VoiceReplyButton(
                onVoiceResult = { aiResponse ->
                    messageText = aiResponse
                },
                modifier = Modifier.size(48.dp)
            )
            
            // Send Button
            FloatingActionButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText.trim(), emptyList())
                        messageText = ""
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = if (messageText.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
