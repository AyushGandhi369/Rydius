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

    // Rating
    var ratedMatchIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    var isSubmittingRating by mutableStateOf(false)
        private set
    var ratingSuccess by mutableStateOf<String?>(null)
        private set

    fun loadRides() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            tripRepo.getMyRides().fold(
                onSuccess = { response ->
                    rides = response.rides
                    // Check which matches are already rated
                    checkRatedMatches(response.rides)
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Failed to load rides"
                }
            )
            isLoading = false
        }
    }

    private suspend fun checkRatedMatches(rides: List<MyRide>) {
        val completedWithMatch = rides.filter {
            it.status == "completed" && it.matchId != null && it.userRole == "passenger"
        }
        val rated = mutableSetOf<Int>()
        for (ride in completedWithMatch) {
            tripRepo.checkRating(ride.matchId!!).onSuccess { check ->
                if (check.hasRated) rated.add(ride.matchId)
            }
        }
        ratedMatchIds = rated
    }

    fun submitRating(matchId: Int, rating: Int, review: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            isSubmittingRating = true
            tripRepo.submitRating(matchId, rating, review).fold(
                onSuccess = {
                    ratedMatchIds = ratedMatchIds + matchId
                    ratingSuccess = "Rating submitted!"
                    isSubmittingRating = false
                    onDone()
                },
                onFailure = { e ->
                    errorMessage = e.message ?: "Failed to submit rating"
                    isSubmittingRating = false
                }
            )
        }
    }

    fun clearRatingSuccess() { ratingSuccess = null }
    fun clearError() { errorMessage = null }
}
