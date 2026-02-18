package com.rydius.mobile.ui.driver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rydius.mobile.RideMateApp
import com.rydius.mobile.data.model.*
import com.rydius.mobile.data.repository.MapRepository
import com.rydius.mobile.data.repository.TripRepository
import com.rydius.mobile.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DriverViewModel : ViewModel() {

    private val tripRepo = TripRepository()
    private val mapRepo = MapRepository()
    private val socketManager = RideMateApp.instance.socketManager

    // ── State ───────────────────────────────────────────────
    var isLoading by mutableStateOf(true)
        private set
    var tripId by mutableStateOf<Int?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Trip details (kept in the VM so "resume" always renders the real active trip)
    var tripStartLocation by mutableStateOf("")
        private set
    var tripEndLocation by mutableStateOf("")
        private set
    var tripStartLat by mutableStateOf(0.0)
        private set
    var tripStartLng by mutableStateOf(0.0)
        private set
    var tripEndLat by mutableStateOf(0.0)
        private set
    var tripEndLng by mutableStateOf(0.0)
        private set
    var tripAvailableSeats by mutableIntStateOf(1)
        private set
    var tripDepartureTime by mutableStateOf("Now")
        private set

    // Route info
    var distanceKm by mutableStateOf(0.0)
        private set
    var durationMinutes by mutableStateOf(0)
        private set
    var routePolyline by mutableStateOf<String?>(null)
        private set

    // Cost sharing
    var costInfo by mutableStateOf<CostSharingResponse?>(null)
        private set

    // Passenger requests
    var pendingRequests by mutableStateOf<List<MatchData>>(emptyList())
        private set
    var searchingPassengers by mutableStateOf(false)
        private set
    var searchStatusText by mutableStateOf("Setting up your trip...")
        private set

    var tripCancelled by mutableStateOf(false)
        private set
    var tripCompleted by mutableStateOf(false)
        private set

    private var initialized = false

    // ── Initialize ──────────────────────────────────────────
    fun initialize(
        startLocation: String, endLocation: String,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        seats: Int, departureTime: String
    ) {
        if (initialized) return
        initialized = true

        // Render something immediately; if we find an existing active trip we will overwrite these.
        tripStartLocation = startLocation
        tripEndLocation = endLocation
        tripStartLat = startLat
        tripStartLng = startLng
        tripEndLat = endLat
        tripEndLng = endLng
        tripAvailableSeats = seats
        tripDepartureTime = departureTime

        viewModelScope.launch {
            isLoading = true
            resetStateForInit()
            try {
                // If the driver already has an active trip, resume it instead of creating a new one.
                // Backend rejects duplicates with 409, so this prevents a broken "Resume Trip" flow.
                val existing = tripRepo.getActiveTrip().getOrNull()?.trip
                if (existing != null) {
                    applyActiveTrip(existing)
                    isLoading = false
                    return@launch
                }

                // 1. Get directions
                val dirResult = mapRepo.getDirections(tripStartLat, tripStartLng, tripEndLat, tripEndLng)
                dirResult.onSuccess { dirs ->
                    val route = dirs.routes?.firstOrNull()
                    val leg = route?.legs?.firstOrNull()
                    distanceKm = (leg?.distance?.value ?: 0.0) / 1000.0
                    durationMinutes = ((leg?.duration?.value ?: 0.0) / 60.0).toInt()
                    routePolyline = route?.overviewPolyline
                }

                // If directions failed, estimate from coordinates
                if (distanceKm == 0.0) {
                    distanceKm = com.rydius.mobile.util.LocationHelper.distanceKm(
                        tripStartLat, tripStartLng, tripEndLat, tripEndLng
                    )
                    durationMinutes = (distanceKm * 2.5).toInt() // rough estimate
                }

                // 2. Create trip
                val tripResult = tripRepo.createTrip(
                    CreateTripRequest(
                        startLocation = tripStartLocation,
                        startLocationLat = tripStartLat,
                        startLocationLng = tripStartLng,
                        endLocation = tripEndLocation,
                        endLocationLat = tripEndLat,
                        endLocationLng = tripEndLng,
                        routePolyline = routePolyline,
                        distanceKm = distanceKm,
                        durationMinutes = durationMinutes,
                        departureTime = tripDepartureTime,
                        availableSeats = tripAvailableSeats
                    )
                )

                tripResult.fold(
                    onSuccess = { response ->
                        if (response.tripId != null) {
                            tripId = response.tripId
                            searchStatusText = "Searching for passengers..."
                            searchingPassengers = true
                            startPassengerSearch(response.tripId)
                            setupSocketListening(response.tripId)
                        } else {
                            errorMessage = response.message ?: "Failed to create trip"
                        }
                    },
                    onFailure = { e ->
                        errorMessage = e.message ?: "Connection error"
                    }
                )

                // 3. Calculate cost sharing
                if (distanceKm > 0.0) {
                    tripRepo.calculateCost(CalculateCostRequest(distanceInKm = distanceKm))
                        .onSuccess { cost -> costInfo = cost }
                }

            } catch (e: Exception) {
                errorMessage = e.message
            }
            isLoading = false
        }
    }

    // Internal helpers
    private fun resetStateForInit() {
        tripId = null
        errorMessage = null
        distanceKm = 0.0
        durationMinutes = 0
        routePolyline = null
        costInfo = null
        pendingRequests = emptyList()
        searchingPassengers = false
        searchStatusText = "Setting up your trip..."
        tripCancelled = false
        tripCompleted = false
    }

    private fun applyActiveTrip(trip: TripData) {
        tripId = trip.id

        tripStartLocation = trip.startLocation
        tripEndLocation = trip.endLocation
        tripStartLat = trip.startLocationLat
        tripStartLng = trip.startLocationLng
        tripEndLat = trip.endLocationLat
        tripEndLng = trip.endLocationLng
        tripAvailableSeats = trip.availableSeats
        tripDepartureTime = trip.departureTime ?: "Now"

        routePolyline = trip.routePolyline
        distanceKm = trip.distanceKm
        durationMinutes = trip.durationMinutes

        searchStatusText = "Searching for passengers..."
        searchingPassengers = true
        startPassengerSearch(trip.id)
        setupSocketListening(trip.id)

        // Best-effort: show already pending requests + cost card without blocking UI.
        viewModelScope.launch { fetchPendingRequests(trip.id) }
        viewModelScope.launch {
            if (distanceKm > 0.0) {
                tripRepo.calculateCost(CalculateCostRequest(distanceInKm = distanceKm))
                    .onSuccess { cost -> costInfo = cost }
            }
        }
    }

    private fun startPassengerSearch(tripId: Int) {
        viewModelScope.launch {
            while (searchingPassengers && !tripCancelled) {
                fetchPendingRequests(tripId)
                delay(30_000) // Poll every 30 seconds
            }
        }
    }

    private suspend fun fetchPendingRequests(tripId: Int) {
        tripRepo.getTripRequests(tripId).onSuccess { requests ->
            pendingRequests = requests.filter { it.status == Constants.STATUS_PENDING }
            if (pendingRequests.isNotEmpty()) {
                searchStatusText = "${pendingRequests.size} passenger request(s)!"
            }
        }
    }

    // ── Socket.IO real-time ─────────────────────────────────
    private fun setupSocketListening(tripId: Int) {
        socketManager.connect()
        val userId = RideMateApp.instance.sessionManager.userId
        socketManager.joinTrip(tripId, userId)
        socketManager.off("passenger-request")
        socketManager.onNewPassengerRequest { _ ->
            viewModelScope.launch {
                fetchPendingRequests(tripId)
            }
        }
    }

    // ── Accept / Decline ────────────────────────────────────
    fun acceptMatch(matchId: Int) {
        viewModelScope.launch {
            tripRepo.updateMatchStatus(matchId, Constants.STATUS_ACCEPTED).fold(
                onSuccess = {
                    pendingRequests = pendingRequests.filter { it.id != matchId }
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    fun declineMatch(matchId: Int) {
        viewModelScope.launch {
            tripRepo.updateMatchStatus(matchId, Constants.STATUS_REJECTED).fold(
                onSuccess = {
                    pendingRequests = pendingRequests.filter { it.id != matchId }
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    // ── Cancel trip ─────────────────────────────────────────
    fun cancelTrip(onCancelled: () -> Unit) {
        val id = tripId ?: return
        viewModelScope.launch {
            tripRepo.cancelTrip(id).fold(
                onSuccess = {
                    tripCancelled = true
                    searchingPassengers = false
                    socketManager.off("passenger-request")
                    socketManager.leaveTrip()
                    onCancelled()
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    // ── Complete trip ───────────────────────────────────────
    fun completeTrip(onCompleted: () -> Unit) {
        val id = tripId ?: return
        viewModelScope.launch {
            tripRepo.completeTrip(id).fold(
                onSuccess = {
                    tripCompleted = true
                    searchingPassengers = false
                    socketManager.off("passenger-request")
                    socketManager.leaveTrip()
                    onCompleted()
                },
                onFailure = { errorMessage = it.message }
            )
        }
    }

    fun retry(
        startLocation: String, endLocation: String,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        seats: Int, departureTime: String
    ) {
        initialized = false
        searchingPassengers = false
        socketManager.off("passenger-request")
        socketManager.leaveTrip()
        resetStateForInit()
        initialize(startLocation, endLocation, startLat, startLng, endLat, endLng, seats, departureTime)
    }

    override fun onCleared() {
        searchingPassengers = false
        socketManager.off("passenger-request")
        socketManager.leaveTrip()
        super.onCleared()
    }
}
