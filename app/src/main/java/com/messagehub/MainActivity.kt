package com.messagehub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.messagehub.data.Message
import com.messagehub.data.MessageRepository
import com.messagehub.services.MessageSyncService
import com.messagehub.ui.theme.MessageHubTheme
import com.messagehub.viewmodels.MainViewModel
import com.messagehub.workers.MessageSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var messageRepository: MessageRepository
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.INTERNET,
        Manifest.permission.FOREGROUND_SERVICE
    )
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBackgroundSync()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
        
        setContent {
            MessageHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            startBackgroundSync()
        }
    }
    
    private fun startBackgroundSync() {
        // Start foreground service
        val serviceIntent = Intent(this, MessageSyncService::class.java)
        startForegroundService(serviceIntent)
        
        // Start platform integrations service
        val platformServiceIntent = Intent(this, PlatformIntegrationService::class.java)
        startForegroundService(platformServiceIntent)
        
        // Schedule periodic work
        val workRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "MessageSync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Device ID Setup
        if (uiState.deviceId.isEmpty()) {
            DeviceIdSetup(
                onDeviceIdSaved = { deviceId ->
                    viewModel.saveDeviceId(deviceId)
                }
            )
        } else {
            // Main Dashboard
            MainDashboard(
                messages = uiState.messages,
                onRefresh = { viewModel.refreshMessages() },
                onReply = { message, reply ->
                    viewModel.sendReply(message, reply)
                }
            )
        }
    }
}

@Composable
fun DeviceIdSetup(onDeviceIdSaved: (String) -> Unit) {
    var deviceId by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Setup Device ID",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                label = { Text("Device ID / API Token") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { onDeviceIdSaved(deviceId) },
                enabled = deviceId.isNotBlank()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun MainDashboard(
    messages: List<Message>,
    onRefresh: () -> Unit,
    onReply: (Message, String) -> Unit
) {
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Button(onClick = onRefresh) {
                Text("Refresh")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(messages) { message ->
                MessageItem(
                    message = message,
                    onClick = { selectedMessage = message }
                )
            }
        }
    }
    
    // Reply Dialog
    selectedMessage?.let { message ->
        ReplyDialog(
            message = message,
            onDismiss = { selectedMessage = null },
            onSend = { reply ->
                onReply(message, reply)
                selectedMessage = null
            }
        )
    }
}

@Composable
fun MessageItem(
    message: Message,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[${message.platform.uppercase()}] ${message.sender}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ReplyDialog(
    message: Message,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reply to ${message.sender}")
        },
        text = {
            Column {
                Text(
                    text = "Original: ${message.content}",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    label = { Text("Your reply") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(replyText) },
                enabled = replyText.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
