package com.rydius.mobile.data.repository

import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.model.*

class TripRepository {

    private val api = ApiClient.api

    suspend fun createTrip(request: CreateTripRequest): Result<CreateTripResponse> =
        safeCall { api.createTrip(request) }

    suspend fun getActiveTrip(): Result<ActiveTripResponse> =
        safeCall { api.getActiveTrip() }

    suspend fun getTrip(id: Int): Result<TripData> =
        safeCall { api.getTrip(id) }

    suspend fun getTripRequests(tripId: Int): Result<List<MatchData>> =
        safeCall { api.getTripRequests(tripId) }

    suspend fun cancelTrip(id: Int): Result<ApiResponse> =
        safeCall { api.cancelTrip(id) }

    suspend fun createRideRequest(request: CreateRideRequestRequest): Result<CreateRideRequestResponse> =
        safeCall { api.createRideRequest(request) }

    suspend fun getAvailableDrivers(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double,
        distanceKm: Double
    ): Result<List<AvailableDriver>> =
        safeCall {
            api.getAvailableDrivers(pickupLat, pickupLng, dropoffLat, dropoffLng, distanceKm)
        }

    suspend fun createMatch(request: CreateMatchRequest): Result<CreateMatchResponse> =
        safeCall { api.createMatch(request) }

    suspend fun updateMatchStatus(matchId: Int, status: String): Result<ApiResponse> =
        safeCall { api.updateMatchStatus(matchId, UpdateMatchStatusRequest(status)) }

    suspend fun getActiveMatches(): Result<List<MatchData>> =
        safeCall { api.getActiveMatches() }

    suspend fun calculateCost(request: CalculateCostRequest): Result<CostSharingResponse> =
        safeCall { api.calculateCost(request) }

    suspend fun getFuelPrices(): Result<FuelPricesResponse> =
        safeCall { api.getFuelPrices() }

    suspend fun getMyRides(): Result<MyRidesResponse> =
        safeCall { api.getMyRides() }
}

private suspend fun <T> safeCall(block: suspend () -> retrofit2.Response<T>): Result<T> =
    try {
        val response = block()
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            Result.failure(Exception(response.errorBody()?.string() ?: "Unknown error"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
