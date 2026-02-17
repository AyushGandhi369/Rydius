package com.rydius.mobile.data.repository

import com.google.gson.JsonParser
import retrofit2.Response

/**
 * Wraps a Retrofit call in a try-catch, returning [Result.success] for 2xx
 * responses with a non-null body, or [Result.failure] otherwise.
 */
suspend fun <T> safeApiCall(block: suspend () -> Response<T>): Result<T> =
    try {
        val response = block()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            val errorMsg = try {
                val raw = response.errorBody()?.string()
                if (raw != null) {
                    try {
                        val json = JsonParser.parseString(raw).asJsonObject
                        json.get("message")?.asString
                            ?: json.get("error")?.asString
                            ?: raw
                    } catch (_: Exception) { raw }
                } else null
            } catch (_: Exception) { null }
            Result.failure(Exception(errorMsg ?: "Request failed (${response.code()})"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
