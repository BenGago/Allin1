package com.messagehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.messagehub.data.Message
import com.messagehub.ui.theme.MessageHubTheme
import com.messagehub.viewmodels.EnhancedMainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageHubApp() {
    MessageHubTheme {
        val navController = rememberNavController()
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination
                    
                    val items = listOf(
                        NavigationItem("messages", "Messages", Icons.Default.Message),
                        NavigationItem("users", "Users", Icons.Default.People),
                        NavigationItem("settings", "Settings", Icons.Default.Settings)
                    )
                    
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "messages",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("messages") {
                    MainScreen(navController)
                }
                composable("users") {
                    UserManagementScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen(navController: NavController) {
    val viewModel: EnhancedMainViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Show error if any
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }
        
        // Device ID Setup or Main Dashboard
        if (uiState.deviceId.isEmpty()) {
            DeviceIdSetup(
                onDeviceIdSaved = { deviceId ->
                    viewModel.saveDeviceId(deviceId)
                }
            )
        } else {
            MainDashboard(
                uiState = uiState,
                onRefresh = { viewModel.refreshMessages() },
                onReply = { message, reply ->
                    viewModel.sendReply(message, reply)
                },
                onTypingChanged = { chatId, isTyping ->
                    viewModel.setTypingIndicator(chatId, isTyping)
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
    uiState: EnhancedMainViewModel.EnhancedUiState,
    onRefresh: () -> Unit,
    onReply: (Message, String) -> Unit,
    onTypingChanged: (String, Boolean) -> Unit
) {
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    
    Column {
        // Header with stats
        DashboardHeader(
            uiState = uiState,
            onRefresh = onRefresh
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Messages list
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(uiState.messages) { message ->
                    MessageItem(
                        message = message,
                        typingUsers = uiState.typingUsers[message.id] ?: emptyList(),
                        onClick = { selectedMessage = message }
                    )
                }
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
            },
            onTypingChanged = { isTyping ->
                onTypingChanged(message.id, isTyping)
            }
        )
    }
}

@Composable
fun DashboardHeader(
    uiState: EnhancedMainViewModel.EnhancedUiState,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Queue stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                uiState.queueStats.forEach { (platform, stats) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = platform.uppercase(),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = "${stats.totalProcessed}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (uiState.processingStatus[platform] == true) {
                            Text(
                                text = "Processing...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    typingUsers: List<String>,
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
            
            // Show typing indicator if users are typing
            if (typingUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${typingUsers.joinToString(", ")} ${if (typingUsers.size == 1) "is" else "are"} typing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ReplyDialog(
    message: Message,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    
    LaunchedEffect(replyText) {
        val newIsTyping = replyText.isNotEmpty()
        if (newIsTyping != isTyping) {
            isTyping = newIsTyping
            onTypingChanged(isTyping)
        }
    }
    
    AlertDialog(
        onDismissRequest = {
            onTypingChanged(false)
            onDismiss()
        },
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
                onClick = {
                    onTypingChanged(false)
                    onSend(replyText)
                },
                enabled = replyText.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onTypingChanged(false)
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsScreen() {
    Text("Settings Screen - Platform configurations, etc.")
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
