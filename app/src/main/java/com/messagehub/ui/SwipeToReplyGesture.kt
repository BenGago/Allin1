package com.messagehub.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagehub.data.EnhancedMessage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun SwipeToReplyMessageBubble(
    message: EnhancedMessage,
    onReply: (EnhancedMessage) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var offsetX by remember { mutableStateOf(0f) }
    var isReplying by remember { mutableStateOf(false) }
    
    val maxSwipeDistance = with(density) { 80.dp.toPx() }
    val triggerDistance = with(density) { 60.dp.toPx() }
    
    val draggableState = rememberDraggableState { delta ->
        val newOffset = offsetX + delta
        offsetX = when {
            newOffset > 0 -> min(newOffset, maxSwipeDistance)
            newOffset < 0 -> max(newOffset, -maxSwipeDistance)
            else -> newOffset
        }
        
        // Trigger reply when swiped far enough
        if (abs(offsetX) >= triggerDistance && !isReplying) {
            isReplying = true
        }
    }
    
    LaunchedEffect(isReplying) {
        if (isReplying) {
            onReply(message)
            // Reset state
            offsetX = 0f
            isReplying = false
        }
    }
    
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // Reply indicator background
        if (abs(offsetX) > 10f) {
            ReplyIndicatorBackground(
                offsetX = offsetX,
                maxSwipeDistance = maxSwipeDistance,
                triggerDistance = triggerDistance
            )
        }
        
        // Message content with swipe gesture
        Box(
            modifier = Modifier
                .offset(x = with(density) { offsetX.toDp() })
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        // Snap back if not triggered
                        if (!isReplying) {
                            offsetX = 0f
                        }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
fun ReplyIndicatorBackground(
    offsetX: Float,
    maxSwipeDistance: Float,
    triggerDistance: Float
) {
    val progress = abs(offsetX) / maxSwipeDistance
    val isTriggered = abs(offsetX) >= triggerDistance
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (offsetX > 0) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .alpha(progress)
                .clip(CircleShape)
                .background(
                    if (isTriggered) Color(0xFF4CAF50) else Color(0xFF2196F3)
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isTriggered,
                transitionSpec = {
                    scaleIn() + fadeIn() with scaleOut() + fadeOut()
                }
            ) { triggered ->
                Icon(
                    imageVector = if (triggered) Icons.Default.Check else Icons.Default.Reply,
                    contentDescription = if (triggered) "Reply triggered" else "Swipe to reply",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ReplyPreview(
    message: EnhancedMessage,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reply indicator line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Reply content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Replying to ${message.sender}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun QuotedMessageBubble(
    originalMessage: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quote indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(1.5.dp)
                    )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column {
                Text(
                    text = originalMessage.sender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = originalMessage.content,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
