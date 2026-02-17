package com.rydius.mobile.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rydius.mobile.data.model.Prediction
import com.rydius.mobile.data.repository.MapRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val mapRepo = MapRepository()

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
        if (text.length >= 2) {
            debounceAutocomplete(text, isPickup = true)
        } else {
            pickupSuggestions = emptyList()
            showPickupSuggestions = false
        }
    }

    fun onDropoffTextChange(text: String) {
        dropoffText = text
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
            delay(250)
            mapRepo.autocomplete(query, 5).fold(
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
                    // Fallback to local suggestions
                    val local = mapRepo.getLocalSuggestions(query, 5)
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

    fun setSeats(n: Int) { seats = n.coerceIn(1, 4) }
    fun setDepartureTime(t: String) { departureTime = t }

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
            else -> null
        }
        return errorMessage == null
    }

    fun clearError() { errorMessage = null }
}
