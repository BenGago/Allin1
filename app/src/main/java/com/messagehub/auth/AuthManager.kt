package com.messagehub.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.messagehub.data.User
import com.messagehub.network.ApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService
) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val TAG = "AuthManager"
        private const val USER_KEY = "current_user"
        private const val AUTH_TOKEN_KEY = "auth_token"
        private const val DEVICE_ID_KEY = "device_id"
        private const val IS_REGISTERED_KEY = "is_registered"
    }
    
    suspend fun registerUser(
        username: String,
        displayName: String,
        email: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val deviceId = getOrCreateDeviceId()
            
            val registrationRequest = RegistrationRequest(
                username = username,
                displayName = displayName,
                email = email,
                password = password,
                deviceId = deviceId
            )
            
            val response = apiService.registerUser(registrationRequest)
            
            if (response.success) {
                val user = User(
                    id = response.userId,
                    username = username,
                    displayName = displayName,
                    email = email,
                    isRegistered = true
                )
                
                saveUser(user)
                saveAuthToken(response.authToken)
                setRegistered(true)
                
                Log.d(TAG, "User registered successfully: $username")
                AuthResult.Success(user)
            } else {
                Log.e(TAG, "Registration failed: ${response.message}")
                AuthResult.Error(response.message ?: "Registration failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            AuthResult.Error("Network error: ${e.message}")
        }
    }
    
    suspend fun loginUser(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val deviceId = getOrCreateDeviceId()
            
            val loginRequest = LoginRequest(
                email = email,
                password = password,
                deviceId = deviceId
            )
            
            val response = apiService.loginUser(loginRequest)
            
            if (response.success) {
                val user = User(
                    id = response.userId,
                    username = response.username,
                    displayName = response.displayName,
                    email = email,
                    profilePicture = response.profilePicture,
                    isRegistered = true
                )
                
                saveUser(user)
                saveAuthToken(response.authToken)
                setRegistered(true)
                
                Log.d(TAG, "User logged in successfully: $email")
                AuthResult.Success(user)
            } else {
                Log.e(TAG, "Login failed: ${response.message}")
                AuthResult.Error(response.message ?: "Login failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            AuthResult.Error("Network error: ${e.message}")
        }
    }
    
    fun getCurrentUser(): User? {
        return try {
            val userJson = encryptedPrefs.getString(USER_KEY, null)
            userJson?.let { json.decodeFromString<User>(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            null
        }
    }
    
    fun isUserRegistered(): Boolean {
        return encryptedPrefs.getBoolean(IS_REGISTERED_KEY, false)
    }
    
    fun getAuthToken(): String? {
        return encryptedPrefs.getString(AUTH_TOKEN_KEY, null)
    }
    
    fun getDeviceId(): String {
        return encryptedPrefs.getString(DEVICE_ID_KEY, "") ?: ""
    }
    
    private fun getOrCreateDeviceId(): String {
        var deviceId = getDeviceId()
        if (deviceId.isEmpty()) {
            deviceId = "device_${UUID.randomUUID()}"
            encryptedPrefs.edit().putString(DEVICE_ID_KEY, deviceId).apply()
        }
        return deviceId
    }
    
    private fun saveUser(user: User) {
        val userJson = json.encodeToString(user)
        encryptedPrefs.edit().putString(USER_KEY, userJson).apply()
    }
    
    private fun saveAuthToken(token: String) {
        encryptedPrefs.edit().putString(AUTH_TOKEN_KEY, token).apply()
    }
    
    private fun setRegistered(isRegistered: Boolean) {
        encryptedPrefs.edit().putBoolean(IS_REGISTERED_KEY, isRegistered).apply()
    }
    
    suspend fun updateUserProfile(
        displayName: String,
        profilePicture: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val currentUser = getCurrentUser() ?: return@withContext AuthResult.Error("No user logged in")
            val authToken = getAuthToken() ?: return@withContext AuthResult.Error("No auth token")
            
            val updateRequest = UpdateProfileRequest(
                displayName = displayName,
                profilePicture = profilePicture
            )
            
            val response = apiService.updateProfile(authToken, updateRequest)
            
            if (response.success) {
                val updatedUser = currentUser.copy(
                    displayName = displayName,
                    profilePicture = profilePicture
                )
                saveUser(updatedUser)
                
                Log.d(TAG, "Profile updated successfully")
                AuthResult.Success(updatedUser)
            } else {
                AuthResult.Error(response.message ?: "Update failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Profile update error", e)
            AuthResult.Error("Network error: ${e.message}")
        }
    }
    
    fun logout() {
        encryptedPrefs.edit().clear().apply()
        Log.d(TAG, "User logged out")
    }
    
    suspend fun deleteAccount(): Boolean = withContext(Dispatchers.IO) {
        try {
            val authToken = getAuthToken() ?: return@withContext false
            val response = apiService.deleteAccount(authToken)
            
            if (response.success) {
                logout()
                Log.d(TAG, "Account deleted successfully")
                true
            } else {
                Log.e(TAG, "Account deletion failed: ${response.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account deletion error", e)
            false
        }
    }
}

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

data class RegistrationRequest(
    val username: String,
    val displayName: String,
    val email: String,
    val password: String,
    val deviceId: String
)

data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String
)

data class UpdateProfileRequest(
    val displayName: String,
    val profilePicture: String?
)

data class AuthResponse(
    val success: Boolean,
    val message: String?,
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val profilePicture: String? = null,
    val authToken: String = ""
)
