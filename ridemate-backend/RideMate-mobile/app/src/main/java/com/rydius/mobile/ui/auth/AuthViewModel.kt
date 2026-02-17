package com.rydius.mobile.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rydius.mobile.RideMateApp
import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.repository.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {

    private val repo = AuthRepository()
    private val session = RideMateApp.instance.sessionManager

    // ── State ───────────────────────────────────────────────
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var successMessage by mutableStateOf<String?>(null)
        private set

    // Signup flow
    var signupStep by mutableStateOf(SignupStep.FORM)
        private set
    var otpEmail by mutableStateOf("")
        private set

    enum class SignupStep { FORM, OTP }

    // ── Login ───────────────────────────────────────────────
    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repo.login(email.trim(), password).fold(
                onSuccess = { response ->
                    if (response.success && response.user != null) {
                        session.saveUser(
                            id = response.user.id,
                            name = response.user.name,
                            email = response.user.email
                        )
                        onSuccess()
                    } else {
                        errorMessage = response.message ?: "Login failed"
                    }
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Connection error"
                }
            )
            isLoading = false
        }
    }

    // ── Signup ───────────────────────────────────────────────
    fun signup(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repo.signup(name.trim(), email.trim(), password).fold(
                onSuccess = { response ->
                    if (response.success == true) {
                        otpEmail = email.trim()
                        signupStep = SignupStep.OTP
                        successMessage = "OTP sent to $email"
                    } else {
                        errorMessage = response.message ?: response.error ?: "Signup failed"
                    }
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Connection error"
                }
            )
            isLoading = false
        }
    }

    // ── OTP Verification ────────────────────────────────────
    fun verifyOtp(otp: String, onSuccess: () -> Unit) {
        if (otp.length != 6) {
            errorMessage = "Enter the 6-digit OTP"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repo.verifyOtp(otpEmail, otp.trim()).fold(
                onSuccess = { response ->
                    if (response.success == true) {
                        successMessage = "Account verified! Please log in."
                        onSuccess()
                    } else {
                        errorMessage = response.message ?: response.error ?: "Verification failed"
                    }
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Connection error"
                }
            )
            isLoading = false
        }
    }

    // ── Logout ──────────────────────────────────────────────
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            session.clear()
            ApiClient.clearCookies()
            onDone()
        }
    }

    fun clearError() { errorMessage = null }

    fun resetSignup() {
        signupStep = SignupStep.FORM
        otpEmail = ""
        errorMessage = null
        successMessage = null
    }

    fun clearError() { errorMessage = null }
    fun clearSuccess() { successMessage = null }
    fun resetSignup() { signupStep = SignupStep.FORM; otpEmail = "" }
}
