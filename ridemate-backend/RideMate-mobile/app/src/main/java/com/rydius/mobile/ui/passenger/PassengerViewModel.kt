package com.rydius.mobile.ui.passenger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rydius.mobile.RideMateApp
import com.rydius.mobile.data.model.*
import com.rydius.mobile.data.repository.MapRepository
import com.rydius.mobile.data.repository.TripRepository
import com.rydius.mobile.util.LocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class PassengerViewModel : ViewModel() {

    private val tripRepo = TripRepository()
    private val mapRepo = MapRepository()
    private val socketManager = RideMateApp.instance.socketManager

    // ── State ───────────────────────────────────────────────
    var isLoading by mutableStateOf(true)
        private set
    var rideRequestId by mutableStateOf<Int?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Route
    var distanceKm by mutableStateOf(0.0)
        private set
    var durationMinutes by mutableStateOf(0)
        private set
    var routePolyline by mutableStateOf<String?>(null)
        private set

    // Cost
    var costInfo by mutableStateOf<CostSharingResponse?>(null)
        private set

    // Drivers
    var availableDrivers by mutableStateOf<List<AvailableDriver>>(emptyList())
        private set
    var isSearchingDrivers by mutableStateOf(false)
        private set
    var selectedDriverTripId by mutableStateOf<Int?>(null)
        private set
    var matchSent by mutableStateOf(false)
        private set
    var matchAccepted by mutableStateOf(false)
        private set
    var matchRejected by mutableStateOf(false)
        private set
    var acceptedDriverName by mutableStateOf<String?>(null)
        private set
    var acceptedFare by mutableStateOf<Double?>(null)
        private set
    var isCancelling by mutableStateOf(false)
        private set
    var cancelError by mutableStateOf<String?>(null)
        private set

    private var initialized = false
    private var pollingActive = false
    private var sentMatchId by mutableStateOf<Int?>(null)

    // ── Initialize ──────────────────────────────────────────
    fun initialize(
        startLocation: String, endLocation: String,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        seats: Int, departureTime: String
    ) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            isLoading = true
            try {
                // 1. Get directions
                val dirResult = mapRepo.getDirections(startLat, startLng, endLat, endLng)
                dirResult.onSuccess { dirs ->
                    val route = dirs.routes?.firstOrNull()
                    val leg = route?.legs?.firstOrNull()
                    distanceKm = (leg?.distance?.value ?: 0.0) / 1000.0
                    durationMinutes = ((leg?.duration?.value ?: 0.0) / 60.0).toInt()
                    routePolyline = route?.overviewPolyline
                }

                // Fallback estimate
                if (distanceKm == 0.0) {
                    distanceKm = LocationHelper.distanceKm(startLat, startLng, endLat, endLng)
                    durationMinutes = (distanceKm * 2.5).toInt()
                }

                // 2. Create ride request
                val reqResult = tripRepo.createRideRequest(
                    CreateRideRequestRequest(
                        pickupLocation = startLocation,
                        pickupLat = startLat,
                        pickupLng = startLng,
                        dropoffLocation = endLocation,
                        dropoffLat = endLat,
                        dropoffLng = endLng,
                        requestedTime = departureTime
                    )
                )

                val rideRequestCreated = reqResult.fold(
                    onSuccess = { response ->
                        if (response.rideRequestId != null) {
                            rideRequestId = response.rideRequestId
                            true
                        } else {
                            errorMessage = response.message ?: "Failed to create ride request"
                            false
                        }
                    },
                    onFailure = { e ->
                        errorMessage = e.message
                        false
                    }
                )

                if (!rideRequestCreated) {
                    isLoading = false
                    return@launch
                }

                // 3. Calculate cost
                tripRepo.calculateCost(
                    CalculateCostRequest(distanceInKm = distanceKm, numberOfPassengers = seats)
                ).onSuccess { cost -> costInfo = cost }

                // 4. Search for available drivers
                searchDrivers(startLat, startLng, endLat, endLng, distanceKm)

            } catch (e: Exception) {
                errorMessage = e.message
            }
            isLoading = false
        }
    }

    // ── Driver search ───────────────────────────────────────
    private suspend fun searchDrivers(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double,
        distKm: Double
    ) {
        isSearchingDrivers = true
        tripRepo.getAvailableDrivers(pickupLat, pickupLng, dropoffLat, dropoffLng, distKm).fold(
            onSuccess = { drivers -> availableDrivers = drivers },
            onFailure = {
                availableDrivers = emptyList()
            }
        )
        isSearchingDrivers = false
    }

    fun refreshDrivers(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double
    ) {
        viewModelScope.launch {
            searchDrivers(pickupLat, pickupLng, dropoffLat, dropoffLng, distanceKm)
        }
    }

    // ── Select driver ───────────────────────────────────────
    fun selectDriver(
        driver: AvailableDriver,
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double
    ) {
        val reqId = rideRequestId
        if (reqId == null) {
            errorMessage = "Ride request is not ready yet. Please retry."
            return
        }
        selectedDriverTripId = driver.tripId

        viewModelScope.launch {
            tripRepo.createMatch(
                CreateMatchRequest(
                    tripId = driver.tripId,
                    rideRequestId = reqId,
                    pickupLat = pickupLat,
                    pickupLng = pickupLng,
                    dropoffLat = dropoffLat,
                    dropoffLng = dropoffLng,
                    fareAmount = driver.fare ?: (distanceKm * 3.0)
                )
            ).fold(
                onSuccess = { response ->
                    if (response.matchId != null) {
                        matchSent = true
                        sentMatchId = response.matchId
                        setupMatchStatusSocketListener()
                        startMatchStatusPolling()
                    } else {
                        errorMessage = response.message ?: "Failed to send match request"
                        selectedDriverTripId = null
                    }
                },
                onFailure = { e ->
                    errorMessage = e.message
                    selectedDriverTripId = null
                }
            )
        }
    }

    fun clearError() { errorMessage = null }

    // ── Cancel ride request ─────────────────────────────────
    fun cancelRideRequest(onCancelled: () -> Unit) {
        val reqId = rideRequestId ?: return
        viewModelScope.launch {
            isCancelling = true
            cancelError = null
            tripRepo.cancelRideRequest(reqId).fold(
                onSuccess = {
                    pollingActive = false
                    socketManager.off("match-status-update")
                    isCancelling = false
                    onCancelled()
                },
                onFailure = { e ->
                    cancelError = e.message ?: "Failed to cancel request"
                    isCancelling = false
                }
            )
        }
    }

    // ── Match status polling ────────────────────────────────
    private fun setupMatchStatusSocketListener() {
        socketManager.connect()
        socketManager.onMatchStatusUpdate { data: JSONObject ->
            val incomingMatchId = data.optInt("matchId", -1)
            val currentMatchId = sentMatchId ?: return@onMatchStatusUpdate
            if (incomingMatchId <= 0 || incomingMatchId != currentMatchId) return@onMatchStatusUpdate

            val status = data.optString("status", "")
            if (status != "accepted" && status != "rejected") return@onMatchStatusUpdate

            viewModelScope.launch {
                pollingActive = false
                when (status) {
                    "accepted" -> {
                        matchAccepted = true
                        // Best-effort fetch to populate driver name + fare.
                        checkMatchStatus()
                    }
                    "rejected" -> {
                        matchRejected = true
                        matchSent = false
                        selectedDriverTripId = null
                        sentMatchId = null
                    }
                }
            }
        }
    }

    private fun startMatchStatusPolling() {
        if (pollingActive) return
        pollingActive = true
        viewModelScope.launch {
            while (pollingActive && matchSent && !matchAccepted && !matchRejected) {
                delay(5_000) // Poll every 5 seconds
                checkMatchStatus()
            }
        }
    }

    private suspend fun checkMatchStatus() {
        tripRepo.getActiveMatches().onSuccess { matches ->
            val ourMatch = matches.find { it.id == sentMatchId }
            if (ourMatch != null) {
                when (ourMatch.status) {
                    "accepted" -> {
                        matchAccepted = true
                        acceptedDriverName = ourMatch.driverName
                        acceptedFare = ourMatch.fare
                        pollingActive = false
                    }
                    "rejected" -> {
                        matchRejected = true
                        matchSent = false
                        selectedDriverTripId = null
                        sentMatchId = null
                        pollingActive = false
                    }
                }
            } else if (sentMatchId != null) {
                // Match not found in active list — likely rejected.
                // Avoid treating an "accepted" socket event as rejected if the API hasn't caught up yet.
                if (!matchAccepted) {
                    matchRejected = true
                    matchSent = false
                    selectedDriverTripId = null
                    sentMatchId = null
                    pollingActive = false
                }
            }
        }
    }

    fun resetAfterRejection() {
        matchRejected = false
        errorMessage = null
    }

    fun retry(
        startLocation: String, endLocation: String,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        seats: Int, departureTime: String
    ) {
        socketManager.off("match-status-update")
        initialized = false
        errorMessage = null
        matchSent = false
        matchAccepted = false
        matchRejected = false
        sentMatchId = null
        pollingActive = false
        selectedDriverTripId = null
        initialize(startLocation, endLocation, startLat, startLng, endLat, endLng, seats, departureTime)
    }

    override fun onCleared() {
        pollingActive = false
        socketManager.off("match-status-update")
        super.onCleared()
    }
}
