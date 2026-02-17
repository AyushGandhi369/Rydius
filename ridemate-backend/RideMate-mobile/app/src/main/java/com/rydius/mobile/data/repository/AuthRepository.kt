package com.rydius.mobile.data.repository

import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.model.*

class AuthRepository {

    private val api = ApiClient.api

    suspend fun signup(name: String, email: String, password: String): Result<ApiResponse> =
        safeCall { api.signup(SignupRequest(name, email, password)) }

    suspend fun verifyOtp(email: String, otp: String): Result<ApiResponse> =
        safeCall { api.verifyOtp(VerifyOtpRequest(email, otp)) }

    suspend fun login(email: String, password: String): Result<LoginResponse> =
        safeCall { api.login(LoginRequest(email, password)) }

    suspend fun logout(): Result<ApiResponse> =
        safeCall { api.logout() }

    suspend fun checkAuthStatus(): Result<AuthStatusResponse> =
        safeCall { api.authStatus() }

    suspend fun getConfig(): Result<ConfigResponse> =
        safeCall { api.getConfig() }
}

private suspend fun <T> safeCall(block: suspend () -> retrofit2.Response<T>): Result<T> =
    try {
        val response = block()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception(response.errorBody()?.string() ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
