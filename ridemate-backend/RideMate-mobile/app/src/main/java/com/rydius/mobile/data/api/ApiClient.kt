package com.rydius.mobile.data.api

import android.content.Context
import com.rydius.mobile.BuildConfig
import com.rydius.mobile.util.Constants
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

object ApiClient {

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    lateinit var client: OkHttpClient
        private set

    lateinit var api: ApiService
        private set

    fun init(context: Context) {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        client = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
}
