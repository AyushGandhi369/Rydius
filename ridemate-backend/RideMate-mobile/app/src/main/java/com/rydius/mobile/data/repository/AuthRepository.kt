package com.rydius.mobile.data.repository

import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.model.*

class AuthRepository {

    private val api = ApiClient.api

    suspend fun signup(name: String, email: String, password: String): Result<ApiResponse> =
        safeApiCall { api.signup(SignupRequest(name, email, password)) }

    suspend fun verifyOtp(email: String, otp: String): Result<ApiResponse> =
        safeApiCall { api.verifyOtp(VerifyOtpRequest(email, otp)) }

    suspend fun login(email: String, password: String): Result<LoginResponse> =
        safeApiCall { api.login(LoginRequest(email, password)) }

    suspend fun logout(): Result<ApiResponse> =
        safeApiCall { api.logout() }

    suspend fun checkAuthStatus(): Result<AuthStatusResponse> =
        safeApiCall { api.authStatus() }

    suspend fun getConfig(): Result<ConfigResponse> =
        safeApiCall { api.getConfig() }

    // ── Profile ─────────────────────────────────────────────────
    suspend fun getProfile(): Result<ProfileResponse> =
        safeApiCall { api.getProfile() }

    suspend fun updateProfile(request: UpdateProfileRequest): Result<ApiResponse> =
        safeApiCall { api.updateProfile(request) }

    suspend fun uploadProfilePhoto(base64Photo: String): Result<UploadPhotoResponse> =
        safeApiCall { api.uploadProfilePhoto(UploadPhotoRequest(base64Photo)) }

    suspend fun deleteProfilePhoto(): Result<ApiResponse> =
        safeApiCall { api.deleteProfilePhoto() }

    suspend fun sendPhoneOtp(phone: String): Result<PhoneOtpResponse> =
        safeApiCall { api.sendPhoneOtp(SendPhoneOtpRequest(phone)) }

    suspend fun verifyPhoneOtp(otp: String): Result<ApiResponse> =
        safeApiCall { api.verifyPhoneOtp(VerifyPhoneOtpRequest(otp)) }

    suspend fun getProfileCompletion(): Result<ProfileCompletionResponse> =
        safeApiCall { api.getProfileCompletion() }
}
