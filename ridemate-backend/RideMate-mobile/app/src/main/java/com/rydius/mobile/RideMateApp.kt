package com.rydius.mobile

import android.app.Application
import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.socket.SocketManager
import com.rydius.mobile.util.SessionManager

class RideMateApp : Application() {

    lateinit var sessionManager: SessionManager
        private set

    lateinit var socketManager: SocketManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionManager = SessionManager(this)
        socketManager = SocketManager()
        ApiClient.init(this)
    }

    override fun onTerminate() {
        socketManager.disconnect()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: RideMateApp
            private set
    }
}
