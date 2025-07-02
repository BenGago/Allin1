package com.messagehub.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.ImageAnalysisResult
import com.messagehub.data.MessageType
import com.messagehub.features.ImageAnalyzer
import com.messagehub.viewmodels.ImageAnalysisViewModel
import com.messagehub.viewmodels.ReactionViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedMessageBubbleWithFeatures(
    message: EnhancedMessage,
    onReply: (EnhancedMessage) -> Unit,
    onReactionClick: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    imageAnalysisViewModel: ImageAnalysisViewModel = viewModel(),
    reactionViewModel: ReactionViewModel = viewModel()
) {
    val isOutgoing = message.sender == "You"
    var showReactionPicker by remember { mutableStateOf(false) }
    var showQuickReactions by remember { mutableStateOf(false) }
    val imageAnalysis by imageAnalysisViewModel.getAnalysisForMessage(message.id).collectAsState()
    
    // Analyze image if message contains image
    LaunchedEffect(message) {
        if (message.messageType == MessageType.IMAGE && message.attachments.isNotEmpty()) {
            imageAnalysisViewModel.analyzeImage(message.id, message.attachments.first())
        }
    }
    
    SwipeToReplyMessageBubble(
        message = message,
        onReply = onReply,
        onLongPress = onLongPress,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = { 
                            showQuickReactions = !showQuickReactions 
                        },
                        onLongClick = { 
                            showReactionPicker = true 
                        }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOutgoing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isOutgoing) 16.dp else 4.dp,
                    bottomEnd = if (isOutgoing) 4.dp else 16.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Reply indicator if this is a reply
                    message.replyTo?.let { replyToId ->
                        // You would fetch the original message here
                        // QuotedMessageBubble(originalMessage = originalMessage)
                        // Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Message content based on type
                    when (message.messageType) {
                        MessageType.TEXT -> {
                            Text(
                                text = message.content,
                                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        MessageType.IMAGE -> {
                            message.attachments.firstOrNull()?.let { attachment ->
                                AsyncImage(
                                    model = attachment.url,
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                
                                // Show image analysis result
                                imageAnalysis?.let { analysis ->
                                    if (analysis.suggestedResponse.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        ImageAnalysisCard(analysis = analysis)
                                    }
                                }
                                
                                if (message.content.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = message.content,
                                        color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        else -> {
                            Text(
                                text = message.content,
                                color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = formatTime(message.timestamp),
                            fontSize = 10.sp,
                            color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Reactions
            if (message.reactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                ReactionRow(
                    reactions = message.reactions,
                    onReactionClick = onReactionClick
                )
            }
            
            // Quick reactions (show on tap)
            AnimatedVisibility(visible = showQuickReactions) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    QuickReactionBar(
                        message = message,
                        onReactionClick = { emoji ->
                            onReactionClick(emoji)
                            showQuickReactions = false
                        }
                    )
                }
            }
        }
    }
    
    // Reaction picker dialog
    if (showReactionPicker) {
        ReactionPickerDialog(
            message = message,
            onReactionSelected = { emoji ->
                onReactionClick(emoji)
                showReactionPicker = false
            },
            onDismiss = { showReactionPicker = false }
        )
    }
}

@Composable
fun ImageAnalysisCard(
    analysis: ImageAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🤖 AI Analysis",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (analysis.confidence > 0) {
                    Text(
                        text = "${(analysis.confidence * 100).toInt()}%",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = analysis.suggestedResponse,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // Show detected features
            if (analysis.faceCount > 0 || analysis.detectedText.isNotEmpty() || analysis.labels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                
                val features = mutableListOf<String>()
                if (analysis.faceCount > 0) {
                    features.add("${analysis.faceCount} face${if (analysis.faceCount > 1) "s" else ""}")
                }
                if (analysis.isMeme) {
                    features.add("meme")
                }
                if (analysis.labels.isNotEmpty()) {
                    features.addAll(analysis.labels.take(2))
                }
                
                if (features.isNotEmpty()) {
                    Text(
                        text = "Detected: ${features.joinToString(", ")}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// Helper function to format timestamp
private fun formatTime(timestamp: String): String {
    return try {
        val time = timestamp.toLong()
        val date = java.util.Date(time)
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
    } catch (e: Exception) {
        timestamp
    }
}
