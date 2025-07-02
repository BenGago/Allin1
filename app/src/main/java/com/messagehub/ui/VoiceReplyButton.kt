package com.messagehub.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.messagehub.features.VoiceState
import com.messagehub.viewmodels.VoiceReplyViewModel

@Composable
fun VoiceReplyButton(
    onVoiceResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceReplyViewModel = viewModel()
) {
    val voiceState by viewModel.voiceState.collectAsState()
    val transcribedText by viewModel.transcribedText.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    
    var showVoiceDialog by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        // Main Voice Button
        FloatingActionButton(
            onClick = {
                when (voiceState) {
                    VoiceState.IDLE -> {
                        showVoiceDialog = true
                        viewModel.startVoiceRecording { response ->
                            onVoiceResult(response)
                            showVoiceDialog = false
                        }
                    }
                    VoiceState.RECORDING, VoiceState.LISTENING -> {
                        viewModel.stopVoiceRecording()
                        showVoiceDialog = false
                    }
                    else -> { /* Do nothing during processing */ }
                }
            },
            containerColor = when (voiceState) {
                VoiceState.IDLE -> MaterialTheme.colorScheme.primary
                VoiceState.LISTENING, VoiceState.RECORDING -> Color.Red
                VoiceState.PROCESSING, VoiceState.GENERATING_AI_RESPONSE -> MaterialTheme.colorScheme.secondary
                VoiceState.COMPLETED -> Color.Green
                VoiceState.ERROR -> Color.Red
            }
        ) {
            AnimatedContent(
                targetState = voiceState,
                transitionSpec = {
                    fadeIn() + scaleIn() with fadeOut() + scaleOut()
                }
            ) { state ->
                when (state) {
                    VoiceState.IDLE -> Icon(Icons.Default.Mic, "Start Voice Recording")
                    VoiceState.LISTENING -> Icon(Icons.Default.MicNone, "Listening...")
                    VoiceState.RECORDING -> Icon(Icons.Default.Stop, "Stop Recording")
                    VoiceState.PROCESSING -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                    VoiceState.GENERATING_AI_RESPONSE -> Icon(Icons.Default.Psychology, "AI Thinking...")
                    VoiceState.COMPLETED -> Icon(Icons.Default.Check, "Completed")
                    VoiceState.ERROR -> Icon(Icons.Default.Error, "Error")
                }
            }
        }
        
        // Voice Recording Dialog
        if (showVoiceDialog) {
            VoiceRecordingDialog(
                voiceState = voiceState,
                transcribedText = transcribedText,
                aiResponse = aiResponse,
                onDismiss = {
                    viewModel.stopVoiceRecording()
                    showVoiceDialog = false
                },
                onSendResponse = { response ->
                    onVoiceResult(response)
                    showVoiceDialog = false
                }
            )
        }
    }
}

@Composable
fun VoiceRecordingDialog(
    voiceState: VoiceState,
    transcribedText: String,
    aiResponse: String,
    onDismiss: () -> Unit,
    onSendResponse: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Voice Assistant")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Voice State Indicator
                VoiceStateIndicator(voiceState)
                
                // Transcribed Text
                if (transcribedText.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "You said:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = transcribedText,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                // AI Response
                if (aiResponse.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "AI Response:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = aiResponse,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (voiceState == VoiceState.COMPLETED && aiResponse.isNotEmpty()) {
                Button(
                    onClick = { onSendResponse(aiResponse) }
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VoiceStateIndicator(voiceState: VoiceState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Animated indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    when (voiceState) {
                        VoiceState.LISTENING, VoiceState.RECORDING -> Color.Red
                        VoiceState.PROCESSING, VoiceState.GENERATING_AI_RESPONSE -> Color.Orange
                        VoiceState.COMPLETED -> Color.Green
                        VoiceState.ERROR -> Color.Red
                        else -> Color.Gray
                    }
                )
        )
        
        // State text
        Text(
            text = when (voiceState) {
                VoiceState.IDLE -> "Ready to listen"
                VoiceState.LISTENING -> "Listening..."
                VoiceState.RECORDING -> "Recording..."
                VoiceState.PROCESSING -> "Processing speech..."
                VoiceState.GENERATING_AI_RESPONSE -> "AI is thinking..."
                VoiceState.COMPLETED -> "Response ready!"
                VoiceState.ERROR -> "Something went wrong"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
