package com.rydius.mobile.ui.rides

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rydius.mobile.data.model.MyRide
import com.rydius.mobile.data.repository.TripRepository
import kotlinx.coroutines.launch

class MyRidesViewModel : ViewModel() {

    private val tripRepo = TripRepository()

    var rides by mutableStateOf<List<MyRide>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun loadRides() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            tripRepo.getMyRides().fold(
                onSuccess = { response ->
                    rides = response.rides
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Failed to load rides"
                }
            )
            isLoading = false
        }
    }
}
