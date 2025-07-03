package com.messagehub.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.messagehub.data.Attachment
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.MessageType
import com.messagehub.data.Reaction
import com.messagehub.features.TranslationResult
import com.messagehub.viewmodels.EnhancedChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedChatScreen(
    viewModel: EnhancedChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<EnhancedMessage?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ChatHeader(
            platform = uiState.currentPlatform,
            isTyping = uiState.isTyping,
            onBackClick = { /* Handle back */ }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true
        ) {
            items(uiState.messages.reversed()) { message ->
                EnhancedMessageBubble(
                    message = message,
                    onLongClick = { selectedMessage = message },
                    onReactionClick = { emoji ->
                        viewModel.addReaction(message.id, emoji)
                    },
                    onReplyClick = { replyToMessage = message },
                    onTranslateClick = { viewModel.translateMessage(message.id) },
                    translation = uiState.translations[message.id]
                )
            }
        }

        replyToMessage?.let { message ->
            ReplyPreview(
                message = message,
                onDismiss = { replyToMessage = null }
            )
        }

        if (uiState.smartReplies.isNotEmpty()) {
            SmartReplyRow(
                suggestions = uiState.smartReplies,
                onSuggestionClick = { suggestion ->
                    viewModel.sendReply(suggestion, replyToMessage?.id)
                    replyToMessage = null
                }
            )
        }

        EnhancedMessageInput(
            onSendMessage = { content, attachments ->
                viewModel.sendMessage(content, attachments, replyToMessage?.id)
                replyToMessage = null
            },
            onTyping = { viewModel.setTyping(it) },
            onEmojiClick = { showEmojiPicker = true }
        )
    }

    selectedMessage?.let { message ->
        MessageActionsBottomSheet(
            message = message,
            onDismiss = { selectedMessage = null },
            onEdit = { newContent ->
                viewModel.editMessage(message.id, newContent)
                selectedMessage = null
            },
            onDelete = {
                viewModel.deleteMessage(message.id)
                selectedMessage = null
            },
            onForward = { platform ->
                viewModel.forwardMessage(message, platform)
                selectedMessage = null
            }
        )
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onEmojiSelected = { emoji ->
                selectedMessage?.let { message ->
                    viewModel.addReaction(message.id, emoji)
                }
                showEmojiPicker = false
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}

@Composable
fun ChatHeader(
    platform: String,
    isTyping: Boolean,
    onBackClick: () -> Unit
) {
    val platformColor = when (platform) {
        "telegram" -> Color(0xFF0088CC)
        "messenger" -> Color(0xFF0084FF)
        "twitter" -> Color(0xFF1DA1F2)
        "sms" -> Color(0xFF34C759)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        color = platformColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                AnimatedVisibility(visible = isTyping) {
                    Text(
                        text = "typing...",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }

            IconButton(onClick = { /* More options */ }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedMessageBubble(
    message: EnhancedMessage,
    onLongClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onReplyClick: () -> Unit,
    onTranslateClick: () -> Unit,
    translation: TranslationResult?
) {
    val isOutgoing = message.sender == "You"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = { /* Handle regular click if needed */ },
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (isOutgoing) 4.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.messageType) {
                    MessageType.IMAGE -> {
                        message.attachments.firstOrNull()?.let { attachment ->
                            AsyncImage(
                                model = attachment.url,
                                contentDescription = "Image attachment",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            if (message.content.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = message.content,
                                    color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // Add other message types here
                    else -> {
                        Text(
                            text = message.content,
                            color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                translation?.translatedText?.let { translatedText ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = translatedText,
                        fontSize = 12.sp,
                        color = if (isOutgoing) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontStyle = FontStyle.Italic
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        fontSize = 10.sp,
                        color = if (isOutgoing) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    if (message.isEdited) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "edited",
                            fontSize = 8.sp,
                            color = if (isOutgoing) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            ReactionRow(
                reactions = message.reactions,
                onReactionClick = onReactionClick
            )
        }
    }
}

@Composable
fun SmartReplyRow(suggestions: List<String>, onSuggestionClick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) }
            )
        }
    }
}

@Composable
fun ReactionRow(reactions: List<Reaction>, onReactionClick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(reactions.groupBy { it.emoji }.entries.toList()) { (emoji, reactionList) ->
            Surface(
                onClick = { onReactionClick(emoji) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 12.sp)
                    if (reactionList.size > 1) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = reactionList.size.toString(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: String): String {
    return try {
        val time = timestamp.toLong()
        val date = Date(time)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        timestamp
    }
}

// Dummy placeholders for missing composables
@Composable
fun ReplyPreview(message: EnhancedMessage, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.LightGray)) {
        Text("Replying to: ${message.content.take(20)}...")
    }
}

@Composable
fun EnhancedMessageInput(onSendMessage: (String, List<Any>) -> Unit, onTyping: (Boolean) -> Unit, onEmojiClick: () -> Unit) {
     Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Message Input Area...")
    }
}

@Composable
fun MessageActionsBottomSheet(message: EnhancedMessage, onDismiss: () -> Unit, onEdit: (String) -> Unit, onDelete: () -> Unit, onForward: (String) -> Unit) {
     Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Message Actions...")
    }
}

@Composable
fun EmojiPickerDialog(onEmojiSelected: (String) -> Unit, onDismiss: () -> Unit) {
     Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text("Emoji Picker...")
    }
}
