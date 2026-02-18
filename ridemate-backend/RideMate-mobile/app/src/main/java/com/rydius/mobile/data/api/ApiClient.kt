package com.rydius.mobile.data.api

import android.content.Context
import android.content.Intent
import com.rydius.mobile.BuildConfig
import com.rydius.mobile.util.Constants
import okhttp3.Interceptor
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.util.concurrent.TimeUnit

object ApiClient {

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    lateinit var client: OkHttpClient
        private set

    lateinit var api: ApiService
        private set

    /** Broadcast action sent when a 401 is detected (session expired). */
    const val ACTION_SESSION_EXPIRED = "com.rydius.mobile.SESSION_EXPIRED"

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
            // Avoid leaking session cookies into debug logs.
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        client = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(ApiService::class.java)
    }

    /** Clears all cookies (used on logout). */
    fun clearCookies() {
        cookieManager.cookieStore.removeAll()
    }

    /**
     * Returns the `Cookie` header value for the current session (if any),
     * used by Socket.IO to authenticate using the same `connect.sid` cookie as Retrofit.
     */
    fun getSessionCookieHeader(baseUrl: String = Constants.BASE_URL): String? {
        return try {
            val uri = URI(baseUrl.trimEnd('/'))
            val cookies = cookieManager.cookieStore.get(uri)
            val sid = cookies.firstOrNull { it.name == "connect.sid" } ?: return null
            "${sid.name}=${sid.value}"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Intercepts 401 responses and broadcasts a session-expired event
     * so the UI can redirect to the login screen.
     */
    private class AuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.code == 401) {
                appContext?.let { ctx ->
                    val session = com.rydius.mobile.util.SessionManager(ctx)
                    // Clear cookies always; only broadcast if the user was logged in.
                    clearCookies()
                    if (session.isLoggedIn) {
                        session.clear()
                        ctx.sendBroadcast(Intent(ACTION_SESSION_EXPIRED).setPackage(ctx.packageName))
                    }
                }
            }
            return response
        }
    }
}
