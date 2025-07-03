package com.messagehub.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.messagehub.data.EnhancedMessage
import com.messagehub.data.MessageType
import com.messagehub.data.Reaction
import com.messagehub.features.SmartReplyManager
import com.messagehub.features.TranslationResult
import com.messagehub.viewmodels.EnhancedChatViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedChatScreen(
    viewModel: EnhancedChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedMessage by remember { mutableStateOf<EnhancedMessage?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var replyToMessage by remember { mutableStateOf<EnhancedMessage?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Chat Header with platform indicator
        ChatHeader(
            platform = uiState.currentPlatform,
            isTyping = uiState.isTyping,
            onBackClick = { /* Handle back */ }
        )

        // Messages List
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

        // Reply Preview
        replyToMessage?.let { message ->
            ReplyPreview(
                message = message,
                onDismiss = { replyToMessage = null }
            )
        }

        // Smart Reply Suggestions
        if (uiState.smartReplies.isNotEmpty()) {
            SmartReplyRow(
                suggestions = uiState.smartReplies,
                onSuggestionClick = { suggestion ->
                    viewModel.sendReply(suggestion, replyToMessage?.id)
                    replyToMessage = null
                }
            )
        }

        // Message Input
        EnhancedMessageInput(
            onSendMessage = { content, attachments ->
                viewModel.sendMessage(content, attachments, replyToMessage?.id)
                replyToMessage = null
            },
            onTyping = { viewModel.setTyping(it) },
            onEmojiClick = { showEmojiPicker = true }
        )
    }

    // Message Actions Bottom Sheet
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

    // Emoji Picker
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
                    text = platform.replaceFirstChar { it.uppercase() },
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
    val isOutgoing = message.sender == "You" // Determine based on your logic

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
                    onClick = { },
                    onLongClick = onLongClick
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
                // Reply indicator
                message.replyTo?.let {
                    // Assuming you have a ReplyIndicator composable
                    // ReplyIndicator(replyToId = it)
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
                            if (message.content.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = message.content,
                                    color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    MessageType.AUDIO -> {
                        // Assuming you have an AudioMessageBubble composable
                        // AudioMessageBubble(message.attachments.first())
                    }
                    MessageType.DOCUMENT -> {
                        // Assuming you have a DocumentMessageBubble composable
                        //
