package com.messagehub.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagehub.auth.AuthManager
import com.messagehub.auth.AuthResult
import com.messagehub.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    init {
        checkAuthStatus()
    }
    
    private fun checkAuthStatus() {
        val isRegistered = authManager.isUserRegistered()
        val currentUser = authManager.getCurrentUser()
        
        _uiState.value = _uiState.value.copy(
            isAuthenticated = isRegistered && currentUser != null,
            currentUser = currentUser
        )
    }
    
    fun register(username: String, displayName: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = authManager.registerUser(username, displayName, email, password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        currentUser = result.user,
                        error = null
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = authManager.loginUser(email, password)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        currentUser = result.user,
                        error = null
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
    
    fun updateProfile(displayName: String, profilePicture: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            when (val result = authManager.updateUserProfile(displayName, profilePicture)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentUser = result.user,
                        error = null
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }
    
    fun logout() {
        authManager.logout()
        _uiState.value = AuthUiState()
    }
    
    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val success = authManager.deleteAccount()
            if (success) {
                _uiState.value = AuthUiState()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to delete account"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    data class AuthUiState(
        val isLoading: Boolean = false,
        val isAuthenticated: Boolean = false,
        val currentUser: User? = null,
        val error: String? = null
    )
}
