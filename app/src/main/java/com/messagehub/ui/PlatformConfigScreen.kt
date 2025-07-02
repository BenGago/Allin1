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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.messagehub.data.PlatformCredential
import com.messagehub.viewmodels.PlatformConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformConfigScreen(
    viewModel: PlatformConfigViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddPlatformDialog by remember { mutableStateOf(false) }
    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Platform Configuration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { showAddPlatformDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Platform")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Platform List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.platforms) { (platform, credential) ->
                PlatformCard(
                    platform = platform,
                    credential = credential,
                    onEdit = { selectedPlatform = platform },
                    onToggle = { enabled ->
                        viewModel.togglePlatform(platform, enabled)
                    },
                    onDelete = {
                        viewModel.deletePlatform(platform)
                    }
                )
            }
        }
        
        if (uiState.platforms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No platforms configured",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Add a platform to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
    
    // Add Platform Dialog
    if (showAddPlatformDialog) {
        AddPlatformDialog(
            onDismiss = { showAddPlatformDialog = false },
            onAddPlatform = { platform ->
                selectedPlatform = platform
                showAddPlatformDialog = false
            }
        )
    }
    
    // Edit Platform Dialog
    selectedPlatform?.let { platform ->
        EditPlatformDialog(
            platform = platform,
            credential = uiState.platforms[platform],
            onDismiss = { selectedPlatform = null },
            onSave = { credential ->
                viewModel.savePlatformCredentials(platform, credential)
                selectedPlatform = null
            }
        )
    }
}

@Composable
fun PlatformCard(
    platform: String,
    credential: PlatformCredential,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val platformIcon = when (platform) {
        "telegram" -> "✈️"
        "messenger" -> "💬"
        "twitter" -> "🐦"
        "sms" -> "📱"
        else -> "📧"
    }
    
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = platformIcon,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = platform.capitalize(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (credential.isEnabled) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (credential.isEnabled) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = credential.isEnabled,
                        onCheckedChange = onToggle
                    )
                    
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            
            if (credential.apiKey?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "API Key: ${credential.apiKey.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddPlatformDialog(
    onDismiss: () -> Unit,
    onAddPlatform: (String) -> Unit
) {
    val platforms = listOf("telegram", "messenger", "twitter", "sms")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Platform") },
        text = {
            LazyColumn {
                items(platforms) { platform ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onAddPlatform(platform) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (platform) {
                                "telegram" -> "✈️"
                                "messenger" -> "💬"
                                "twitter" -> "🐦"
                                "sms" -> "📱"
                                else -> "📧"
                            }
                            
                            Text(text = icon, style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = platform.capitalize(),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditPlatformDialog(
    platform: String,
    credential: PlatformCredential?,
    onDismiss: () -> Unit,
    onSave: (PlatformCredential) -> Unit
) {
    var apiKey by remember { mutableStateOf(credential?.apiKey ?: "") }
    var apiSecret by remember { mutableStateOf(credential?.apiSecret ?: "") }
    var accessToken by remember { mutableStateOf(credential?.accessToken ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${platform.capitalize()}") },
        text = {
            Column {
                when (platform) {
                    "telegram" -> {
                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("Bot Token") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11") }
                        )
                    }
                    "messenger" -> {
                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("Page Access Token") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiSecret,
                            onValueChange = { apiSecret = it },
                            label = { Text("Verify Token") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "twitter" -> {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiSecret,
                            onValueChange = { apiSecret = it },
                            label = { Text("API Secret") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("Bearer Token") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "sms" -> {
                        Text(
                            text = "SMS uses device capabilities. No additional configuration required.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newCredential = PlatformCredential(
                        platform = platform,
                        apiKey = apiKey.takeIf { it.isNotEmpty() },
                        apiSecret = apiSecret.takeIf { it.isNotEmpty() },
                        accessToken = accessToken.takeIf { it.isNotEmpty() },
                        isEnabled = true
                    )
                    onSave(newCredential)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
