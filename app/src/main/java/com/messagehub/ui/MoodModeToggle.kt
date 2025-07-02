package com.messagehub.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagehub.data.MessageMood
import com.messagehub.features.FlirtIntensity
import com.messagehub.viewmodels.MoodViewModel

@Composable
fun MoodModeToggle(
    modifier: Modifier = Modifier,
    viewModel: MoodViewModel = viewModel()
) {
    val currentMood by viewModel.currentMood.collectAsState()
    val sextModeEnabled by viewModel.sextModeEnabled.collectAsState()
    val flirtScore by viewModel.flirtScore.collectAsState()
    val crushProbability by viewModel.crushProbability.collectAsState()
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (currentMood) {
                MessageMood.SPICY -> Color(0xFFFF6B6B)
                MessageMood.ROMANTIC -> Color(0xFFFF8A95)
                MessageMood.PLAYFUL -> Color(0xFF4ECDC4)
                MessageMood.SAD -> Color(0xFF95A5A6)
                MessageMood.NEUTRAL -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mood Indicator
            MoodIndicator(currentMood, flirtScore)
            
            // Sext Mode Toggle
            SextModeToggle(
                enabled = sextModeEnabled,
                onToggle = { viewModel.setSextModeEnabled(it) }
            )
            
            // Flirt Meter
            FlirtMeter(flirtScore, crushProbability)
            
            // AI Clone Status
            AICloneStatus(viewModel)
        }
    }
}

@Composable
fun MoodIndicator(mood: MessageMood, flirtScore: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Mood Icon
        val (icon, color, text) = when (mood) {
            MessageMood.SPICY -> Triple(Icons.Default.LocalFireDepartment, Color(0xFFFF4757), "Spicy Mode 🔥")
            MessageMood.ROMANTIC -> Triple(Icons.Default.Favorite, Color(0xFFFF6B9D), "Romantic Mode 💕")
            MessageMood.PLAYFUL -> Triple(Icons.Default.EmojiEmotions, Color(0xFF26D0CE), "Playful Mode 😄")
            MessageMood.SAD -> Triple(Icons.Default.SentimentDissatisfied, Color(0xFF747D8C), "Comfort Mode 🤗")
            MessageMood.NEUTRAL -> Triple(Icons.Default.Chat, Color.Gray, "Normal Mode 💬")
        }
        
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = color
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Flirt Score Badge
        if (flirtScore > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFF6B6B).copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${flirtScore.toInt()}% 🔥",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF4757)
                )
            }
        }
    }
}

@Composable
fun SextModeToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = if (enabled) Color(0xFFFF4757) else Color.Gray
            )
            
            Column {
                Text(
                    text = "Sext Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (enabled) "Spicy responses enabled 😈" else "Normal responses",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF4757),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray
            )
        )
    }
}

@Composable
fun FlirtMeter(flirtScore: Float, crushProbability: Float) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Flirt Analysis 📊",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        
        // Flirt Score Bar
        FlirtProgressBar(
            label = "Flirt Level",
            value = flirtScore,
            maxValue = 100f,
            color = Color(0xFFFF6B6B),
            emoji = "🔥"
        )
        
        // Crush Probability Bar
        FlirtProgressBar(
            label = "Crush Probability",
            value = crushProbability,
            maxValue = 100f,
            color = Color(0xFFFF8A95),
            emoji = "💕"
        )
    }
}

@Composable
fun FlirtProgressBar(
    label: String,
    value: Float,
    maxValue: Float,
    color: Color,
    emoji: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label $emoji",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${value.toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(value / maxValue)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.7f),
                                color
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun AICloneStatus(viewModel: MoodViewModel) {
    val cloneEnabled by viewModel.cloneEnabled.collectAsState()
    val trainingProgress by viewModel.trainingProgress.collectAsState()
    val personalityProfile by viewModel.personalityProfile.collectAsState()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                tint = if (cloneEnabled) Color(0xFF6C5CE7) else Color.Gray
            )
            
            Column {
                Text(
                    text = "AI Clone",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (cloneEnabled) {
                        "Mimicking your ${personalityProfile.communicationStyle} style"
                    } else {
                        "Learning your texting style..."
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        
        Switch(
            checked = cloneEnabled,
            onCheckedChange = { viewModel.setCloneEnabled(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6C5CE7),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray
            )
        )
    }
    
    // Training Progress
    if (trainingProgress > 0f && trainingProgress < 1f) {
        LinearProgressIndicator(
            progress = trainingProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = Color(0xFF6C5CE7)
        )
    }
}
