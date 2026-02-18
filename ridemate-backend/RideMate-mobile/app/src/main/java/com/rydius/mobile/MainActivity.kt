package com.rydius.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.repository.AuthRepository
import com.rydius.mobile.navigation.AppNavGraph
import com.rydius.mobile.navigation.Routes
import com.rydius.mobile.ui.theme.RideMateTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var appReady by mutableStateOf(false)
    private var isLoggedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val session = (application as RideMateApp).sessionManager
        isLoggedIn = session.isLoggedIn
        splashScreen.setKeepOnScreenCondition { !appReady }

        lifecycleScope.launch {
            if (session.isLoggedIn) {
                val authRepo = AuthRepository()
                authRepo.checkAuthStatus().fold(
                    onSuccess = { status ->
                        if (status.isAuthenticated) {
                            isLoggedIn = true
                        } else {
                            session.clear()
                            ApiClient.clearCookies()
                            isLoggedIn = false
                        }
                    },
                    onFailure = {
                        // Keep local session on transient network failure.
                        isLoggedIn = session.isLoggedIn
                    }
                )
            } else {
                isLoggedIn = false
            }
            appReady = true
        }

        setContent {
            RideMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!appReady) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val navController = rememberNavController()

                        // Listen for session-expired broadcasts from the 401 interceptor
                        DisposableEffect(Unit) {
                            val receiver = object : BroadcastReceiver() {
                                override fun onReceive(ctx: Context?, intent: Intent?) {
                                    isLoggedIn = false
                                    navController.navigate(Routes.LOGIN) {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                            ContextCompat.registerReceiver(
                                this@MainActivity,
                                receiver,
                                IntentFilter(ApiClient.ACTION_SESSION_EXPIRED),
                                ContextCompat.RECEIVER_NOT_EXPORTED
                            )
                            onDispose { unregisterReceiver(receiver) }
                        }

                        AppNavGraph(
                            navController = navController,
                            isLoggedIn = isLoggedIn
                        )
                    }
                }
            }
        }
    }
}
