package com.messagehub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.messagehub.data.MessageRepository
import com.messagehub.ui.theme.MessageHubTheme
import com.messagehub.ui.EnhancedChatScreen
import com.messagehub.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    @Inject
    lateinit var messageRepository: MessageRepository
    
    private val mainViewModel: MainViewModel by viewModels()
    
    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ).let { permissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions + Manifest.permission.POST_NOTIFICATIONS
            } else {
                permissions
            }
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            initializeApp()
        } else {
            Log.w(TAG, "Some permissions denied: $permissions")
            // Handle denied permissions - show explanation or disable features
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "MainActivity created")
        
        // Check and request permissions
        checkAndRequestPermissions()
        
        setContent {
            MessageHubTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EnhancedChatScreen()
                }
            }
        }
        
        // Handle notification intent extras
        handleNotificationIntent()
    }
    
    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            initializeApp()
        } else {
            Log.d(TAG, "Requesting permissions: $missingPermissions")
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    
    private fun initializeApp() {
        Log.d(TAG, "Initializing app...")
        
        // Initialize Firebase Messaging
        initializeFirebaseMessaging()
        
        // Register device with backend
        lifecycleScope.launch {
            try {
                val success = messageRepository.registerDevice("Android Messaging Hub")
                if (success) {
                    Log.d(TAG, "Device registered successfully")
                } else {
                    Log.w(TAG, "Failed to register device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering device", e)
            }
        }
        
        // Start background services
        startBackgroundServices()
    }
    
    private fun initializeFirebaseMessaging() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            
            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Token: ${token.take(20)}...")
            
            // Update token on server
            lifecycleScope.launch {
                try {
                    messageRepository.updateFCMToken(token)
                    Log.d(TAG, "FCM token updated on server")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update FCM token on server", e)
                }
            }
        }
        
        // Subscribe to general topic for broadcast messages
        FirebaseMessaging.getInstance().subscribeToTopic("general")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to general topic"
                if (!task.isSuccessful) {
                    msg = "Failed to subscribe to general topic"
                }
                Log.d(TAG, msg)
            }
    }
    
    private fun startBackgroundServices() {
        // Background services are started automatically via WorkManager
        // See MessageSyncWorker and other background components
        Log.d(TAG, "Background services initialized")
    }
    
    private fun handleNotificationIntent() {
        val action = intent.getStringExtra("action")
        when (action) {
            "quick_reply" -> {
                val sender = intent.getStringExtra("sender")
                val platform = intent.getStringExtra("platform")
                Log.d(TAG, "Quick reply action: $sender on $platform")
                // Handle quick reply - could open specific chat or reply dialog
            }
            "play_voice" -> {
                val sender = intent.getStringExtra("sender")
                Log.d(TAG, "Play voice action: $sender")
                // Handle voice message playback
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent()
    }
}
