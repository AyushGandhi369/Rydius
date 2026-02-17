package com.rydius.mobile.data.socket

import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.util.Constants
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URI

/**
 * Manages the Socket.IO connection for real-time driver ↔ passenger notifications.
 */
class SocketManager {

    private var socket: Socket? = null
    private var currentTripId: Int? = null

    val isConnected: Boolean get() = socket?.connected() == true

    fun connect() {
        if (socket?.connected() == true) return
        try {
            val baseUrl = Constants.BASE_URL.trimEnd('/')
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                timeout = 20000
                transports = arrayOf("websocket", "polling")
            }
            ApiClient.getSessionCookieHeader(baseUrl)?.let { cookie ->
                if (cookie.isNotBlank()) {
                    opts.extraHeaders = mapOf("Cookie" to listOf(cookie))
                }
            }
            socket = IO.socket(URI.create(baseUrl), opts)
            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        leaveTrip()
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    // ── Driver: join a trip room to receive passenger requests ───
    fun joinTrip(tripId: Int, userId: Int) {
        currentTripId = tripId
        val payload = JSONObject().apply {
            put("tripId", tripId)
            put("userId", userId)
        }
        socket?.emit("driver-join-trip", payload)
    }

    fun leaveTrip() {
        currentTripId?.let { id ->
            val payload = JSONObject().apply { put("tripId", id) }
            socket?.emit("driver-leave-trip", payload)
        }
        currentTripId = null
    }

    // ── Passenger: notify driver that we selected them ──────────
    fun notifyDriverSelected(tripId: Int, fareAmount: Double? = null, passengerData: JSONObject? = null) {
        val payload = JSONObject().apply {
            put("tripId", tripId)
            if (fareAmount != null) put("fareAmount", fareAmount)
            if (passengerData != null) put("passengerData", passengerData)
        }
        socket?.emit("passenger-selected-driver", payload)
    }

    // ── Listen for new passenger request (driver side) ──────────
    fun onNewPassengerRequest(listener: (JSONObject) -> Unit) {
        socket?.off("passenger-request")
        socket?.on("passenger-request") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject ?: JSONObject(args[0].toString())
                    listener(data)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ── Generic event listeners ─────────────────────────────────
    fun onMatchStatusUpdate(listener: (JSONObject) -> Unit) {
        socket?.off("match-status-update")
        socket?.on("match-status-update") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject ?: JSONObject(args[0].toString())
                    listener(data)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun on(event: String, listener: Emitter.Listener) {
        socket?.on(event, listener)
    }

    fun off(event: String) {
        socket?.off(event)
    }

    fun onConnect(listener: () -> Unit) {
        socket?.on(Socket.EVENT_CONNECT) { listener() }
    }

    fun onDisconnect(listener: () -> Unit) {
        socket?.on(Socket.EVENT_DISCONNECT) { listener() }
    }

    fun onError(listener: (String) -> Unit) {
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val msg = if (args.isNotEmpty()) args[0].toString() else "Connection error"
            listener(msg)
        }
    }
}
