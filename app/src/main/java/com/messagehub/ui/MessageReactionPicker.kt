package com.messagehub.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagehub.data.EnhancedMessage
import com.messagehub.features.EmojiReaction
import com.messagehub.viewmodels.ReactionViewModel

@Composable
fun QuickReactionBar(
    message: EnhancedMessage,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReactionViewModel = viewModel()
) {
    val quickReactions = viewModel.getQuickReactions()
    
    LazyRow(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(quickReactions) { reaction ->
            QuickReactionButton(
                reaction = reaction,
                onClick = { onReactionClick(reaction.emoji) }
            )
        }
        
        // More reactions button
        item {
            IconButton(
                onClick = { /* Open full reaction picker */ },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "More reactions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuickReactionButton(
    reaction: EmojiReaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = reaction.emoji,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun ReactionPickerDialog(
    message: EnhancedMessage,
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ReactionViewModel = viewModel()
) {
    val availableEmojis by viewModel.availableEmojis.collectAsState()
    val recentEmojis by viewModel.recentEmojis.collectAsState()
    val stickerPacks by viewModel.stickerPacks.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Recent", "Emojis", "Stickers")
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Reaction",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                // Content
                when (selectedTab) {
                    0 -> RecentEmojisGrid(
                        emojis = recentEmojis,
                        onEmojiClick = { emoji ->
                            onReactionSelected(emoji)
                            onDismiss()
                        }
                    )
                    1 -> EmojiGrid(
                        emojis = availableEmojis,
                        onEmojiClick = { emoji ->
                            onReactionSelected(emoji)
                            onDismiss()
                        }
                    )
                    2 -> StickerGrid(
                        stickerPacks = stickerPacks,
                        onStickerClick = { sticker ->
                            onReactionSelected(sticker)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentEmojisGrid(
    emojis: List<String>,
    onEmojiClick: (String) -> Unit
) {
    if (emojis.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No recent emojis",
                color = Color.Gray
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(emojis) { emoji ->
                EmojiButton(
                    emoji = emoji,
                    onClick = { onEmojiClick(emoji) }
                )
            }
        }
    }
}

@Composable
fun EmojiGrid(
    emojis: List<EmojiReaction>,
    onEmojiClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(emojis) { reaction ->
            EmojiButton(
                emoji = reaction.emoji,
                onClick = { onEmojiClick(reaction.emoji) }
            )
        }
    }
}

@Composable
fun StickerGrid(
    stickerPacks: List<com.messagehub.features.StickerPack>,
    onStickerClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stickerPacks.forEach { pack ->
            items(pack.stickers) { sticker ->
                StickerButton(
                    sticker = sticker,
                    onClick = { onStickerClick(sticker.emoji) }
                )
            }
        }
    }
}

@Composable
fun EmojiButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun StickerButton(
    sticker: com.messagehub.features.Sticker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(60.dp)
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = sticker.emoji,
                fontSize = 20.sp
            )
            Text(
                text = sticker.name,
                fontSize = 8.sp,
                maxLines = 1
            )
        }
    }
}
