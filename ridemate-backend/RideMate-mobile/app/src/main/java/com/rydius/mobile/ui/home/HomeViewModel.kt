package com.rydius.mobile.ui.home

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.rydius.mobile.data.model.Prediction
import com.rydius.mobile.data.repository.MapRepository
import com.rydius.mobile.util.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException

class HomeViewModel : ViewModel() {

    private val mapRepo = MapRepository()
    private val tripRepo = com.rydius.mobile.data.repository.TripRepository()

    // ── Active trip recovery ────────────────────────────────
    var hasActiveTrip by mutableStateOf(false)
        private set
    var activeTripData by mutableStateOf<com.rydius.mobile.data.model.TripData?>(null)
        private set
    var activeTripChecked by mutableStateOf(false)
        private set

    init {
        checkForActiveTrip()
    }

    private fun checkForActiveTrip() {
        viewModelScope.launch {
            tripRepo.getActiveTrip().onSuccess { response ->
                val trip = response.trip
                if (trip != null) {
                    hasActiveTrip = true
                    activeTripData = trip
                }
            }
            activeTripChecked = true
        }
    }

    fun dismissActiveTrip() {
        hasActiveTrip = false
        activeTripData = null
    }

    // ── Role ────────────────────────────────────────────────
    var selectedRole by mutableStateOf("rider") // "rider" or "driver"
        private set

    fun setRole(role: String) { selectedRole = role }

    // ── Location inputs ─────────────────────────────────────
    var pickupText by mutableStateOf("")
    var dropoffText by mutableStateOf("")

    var pickupLat by mutableDoubleStateOf(0.0)
        private set
    var pickupLng by mutableDoubleStateOf(0.0)
        private set
    var dropoffLat by mutableDoubleStateOf(0.0)
        private set
    var dropoffLng by mutableDoubleStateOf(0.0)
        private set

    // Autocomplete
    var pickupSuggestions by mutableStateOf<List<Prediction>>(emptyList())
        private set
    var dropoffSuggestions by mutableStateOf<List<Prediction>>(emptyList())
        private set
    var showPickupSuggestions by mutableStateOf(false)
        private set
    var showDropoffSuggestions by mutableStateOf(false)
        private set

    private var autocompleteJob: Job? = null

    fun onPickupTextChange(text: String) {
        pickupText = text
        // If the user edits the text after selecting a suggestion, coordinates become stale.
        // Force them to re-select a suggestion so we never route using mismatched lat/lng.
        pickupLat = 0.0
        pickupLng = 0.0
        errorMessage = null
        if (text.length >= 2) {
            debounceAutocomplete(text, isPickup = true)
        } else {
            pickupSuggestions = emptyList()
            showPickupSuggestions = false
        }
    }

    fun onDropoffTextChange(text: String) {
        dropoffText = text
        // Same stale-coordinate protection as pickup.
        dropoffLat = 0.0
        dropoffLng = 0.0
        errorMessage = null
        if (text.length >= 2) {
            debounceAutocomplete(text, isPickup = false)
        } else {
            dropoffSuggestions = emptyList()
            showDropoffSuggestions = false
        }
    }

    private fun debounceAutocomplete(query: String, isPickup: Boolean) {
        autocompleteJob?.cancel()
        autocompleteJob = viewModelScope.launch {
            val q = query.trim()
            delay(250)

            // If the user kept typing (or cleared the field) during debounce, ignore stale work.
            if (isPickup) {
                if (pickupText.trim() != q) return@launch
            } else {
                if (dropoffText.trim() != q) return@launch
            }

            val result = mapRepo.autocomplete(q, 5)

            // If the query changed while the request was in-flight, drop the response.
            if (isPickup) {
                if (pickupText.trim() != q) return@launch
            } else {
                if (dropoffText.trim() != q) return@launch
            }

            result.fold(
                onSuccess = { predictions ->
                    if (isPickup) {
                        pickupSuggestions = predictions
                        showPickupSuggestions = predictions.isNotEmpty()
                    } else {
                        dropoffSuggestions = predictions
                        showDropoffSuggestions = predictions.isNotEmpty()
                    }
                },
                onFailure = {
                    // OkHttp reports cancelled requests as IOException("Canceled"). This is expected
                    // when the user types quickly; don't show local fallbacks for cancellations.
                    if (it is IOException && it.message?.contains("Canceled", ignoreCase = true) == true) {
                        return@launch
                    }
                    // Fallback to local suggestions
                    val local = mapRepo.getLocalSuggestions(q, 5)
                    if (isPickup) {
                        pickupSuggestions = local
                        showPickupSuggestions = local.isNotEmpty()
                    } else {
                        dropoffSuggestions = local
                        showDropoffSuggestions = local.isNotEmpty()
                    }
                }
            )
        }
    }

    fun selectPickup(prediction: Prediction) {
        pickupText = prediction.description
        pickupLat = prediction.lat ?: 0.0
        pickupLng = prediction.lng ?: 0.0
        pickupSuggestions = emptyList()
        showPickupSuggestions = false
    }

    fun selectDropoff(prediction: Prediction) {
        dropoffText = prediction.description
        dropoffLat = prediction.lat ?: 0.0
        dropoffLng = prediction.lng ?: 0.0
        dropoffSuggestions = emptyList()
        showDropoffSuggestions = false
    }

    fun dismissSuggestions() {
        showPickupSuggestions = false
        showDropoffSuggestions = false
    }

    // ── Current Location ────────────────────────────────────
    var isFetchingLocation by mutableStateOf(false)
        private set

    @SuppressLint("MissingPermission")
    fun fetchCurrentLocation(context: Context) {
        if (isFetchingLocation) return
        isFetchingLocation = true
        viewModelScope.launch {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val location = fusedClient.lastLocation.await() ?: LocationHelper.getCurrentLocation(context)
                if (location != null) {
                    pickupLat = location.latitude
                    pickupLng = location.longitude
                    // Reverse geocode to get address
                    mapRepo.reverseGeocode(location.latitude, location.longitude).onSuccess { result ->
                        val addr = result.address
                        if (!addr.isNullOrBlank()) pickupText = addr
                        else pickupText = "${location.latitude}, ${location.longitude}"
                    }.onFailure {
                        pickupText = "${location.latitude}, ${location.longitude}"
                    }
                } else {
                    errorMessage = "Could not get current location"
                }
            } catch (e: Exception) {
                errorMessage = "Location error: ${e.message}"
            } finally {
                isFetchingLocation = false
            }
        }
    }

    fun swapLocations() {
        val tmpText = pickupText;      pickupText = dropoffText;     dropoffText = tmpText
        val tmpLat = pickupLat;        pickupLat = dropoffLat;       dropoffLat = tmpLat
        val tmpLng = pickupLng;        pickupLng = dropoffLng;       dropoffLng = tmpLng
    }

    // ── Seats & Time ────────────────────────────────────────
    var seats by mutableIntStateOf(1)
        private set
    var departureTime by mutableStateOf("Now")
        private set
    var departureDisplayText by mutableStateOf("Now")
        private set

    fun updateSeats(n: Int) { seats = n.coerceIn(1, 6) }
    fun updateDepartureTime(t: String) { departureTime = t }
    fun updateDepartureDisplayText(t: String) { departureDisplayText = t }

    // ── Quick tags ──────────────────────────────────────────
    fun applyQuickTag(tag: String) {
        when (tag) {
            "Office" -> { pickupText = "Office, Ahmedabad"; pickupLat = 23.0225; pickupLng = 72.5714 }
            "Home"   -> { pickupText = "Home, Ahmedabad"; pickupLat = 23.0350; pickupLng = 72.5600 }
        }
    }

    // ── Validation ──────────────────────────────────────────
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun validate(): Boolean {
        errorMessage = when {
            pickupText.isBlank() -> "Enter pickup location"
            dropoffText.isBlank() -> "Enter drop-off location"
            pickupLat == 0.0 && pickupLng == 0.0 -> "Select a pickup from suggestions"
            dropoffLat == 0.0 && dropoffLng == 0.0 -> "Select a drop-off from suggestions"
            pickupLat != 0.0 && dropoffLat != 0.0 &&
                LocationHelper.distanceKm(pickupLat, pickupLng, dropoffLat, dropoffLng) < 0.5 ->
                "Pickup and drop-off are too close (min 500m)"
            else -> null
        }
        return errorMessage == null
    }

    fun clearError() { errorMessage = null }
}
