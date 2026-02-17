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

    // Password reset flow
    var resetStep by mutableStateOf(ResetStep.EMAIL)
        private set
    var resetEmail by mutableStateOf("")
        private set
    var isDeleting by mutableStateOf(false)
        private set

    enum class ResetStep { EMAIL, OTP_AND_PASSWORD }

    // ── Login ───────────────────────────────────────────────
    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Please fill in all fields"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage = "Please enter a valid email address"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repo.login(email.trim(), password).fold(
                onSuccess = { response ->
                    if (response.user != null) {
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
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage = "Please enter a valid email address"
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
                    otpEmail = email.trim()
                    signupStep = SignupStep.OTP
                    successMessage = response.message ?: "OTP sent to $email"
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
                    successMessage = response.message ?: "Account verified! Please log in."
                    onSuccess()
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

    // ── Forgot Password ─────────────────────────────────────
    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            errorMessage = "Please enter your email"
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            errorMessage = "Please enter a valid email address"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repo.forgotPassword(email.trim()).fold(
                onSuccess = { response ->
                    resetEmail = email.trim()
                    resetStep = ResetStep.OTP_AND_PASSWORD
                    successMessage = response.message ?: "If this email exists, a reset OTP was sent."
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Connection error"
                }
            )
            isLoading = false
        }
    }

    fun resetPassword(otp: String, newPassword: String, onSuccess: () -> Unit) {
        if (otp.length != 6) {
            errorMessage = "Enter the 6-digit OTP"
            return
        }
        if (newPassword.length < 6) {
            errorMessage = "Password must be at least 6 characters"
            return
        }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            repo.resetPassword(resetEmail, otp.trim(), newPassword).fold(
                onSuccess = { response ->
                    successMessage = response.message ?: "Password reset successful!"
                    onSuccess()
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Connection error"
                }
            )
            isLoading = false
        }
    }

    fun resetForgotPasswordFlow() {
        resetStep = ResetStep.EMAIL
        resetEmail = ""
        errorMessage = null
        successMessage = null
    }

    // ── Delete Account ──────────────────────────────────────
    fun deleteAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            isDeleting = true
            errorMessage = null
            repo.deleteAccount().fold(
                onSuccess = {
                    session.clear()
                    ApiClient.clearCookies()
                    isDeleting = false
                    onDone()
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Failed to delete account"
                    isDeleting = false
                }
            )
        }
    }

    fun clearError() { errorMessage = null }
    fun setError(msg: String) { errorMessage = msg }

    fun resetSignup() {
        signupStep = SignupStep.FORM
        otpEmail = ""
        errorMessage = null
        successMessage = null
    }

    fun clearSuccess() { successMessage = null }
}
