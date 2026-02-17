package com.rydius.mobile.data.repository

import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.model.*

class TripRepository {

    private val api = ApiClient.api

    suspend fun createTrip(request: CreateTripRequest): Result<CreateTripResponse> =
        safeApiCall { api.createTrip(request) }

    suspend fun getActiveTrip(): Result<ActiveTripResponse> =
        safeApiCall { api.getActiveTrip() }

    suspend fun getTrip(id: Int): Result<TripData> =
        safeApiCall { api.getTrip(id) }

    suspend fun getTripRequests(tripId: Int): Result<List<MatchData>> =
        safeApiCall { api.getTripRequests(tripId) }

    suspend fun cancelTrip(id: Int): Result<ApiResponse> =
        safeApiCall { api.cancelTrip(id) }

    suspend fun completeTrip(id: Int): Result<ApiResponse> =
        safeApiCall { api.completeTrip(id) }

    suspend fun createRideRequest(request: CreateRideRequestRequest): Result<CreateRideRequestResponse> =
        safeApiCall { api.createRideRequest(request) }

    suspend fun getAvailableDrivers(
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double,
        distanceKm: Double
    ): Result<List<AvailableDriver>> =
        safeApiCall {
            api.getAvailableDrivers(pickupLat, pickupLng, dropoffLat, dropoffLng, distanceKm)
        }

    suspend fun createMatch(request: CreateMatchRequest): Result<CreateMatchResponse> =
        safeApiCall { api.createMatch(request) }

    suspend fun updateMatchStatus(matchId: Int, status: String): Result<ApiResponse> =
        safeApiCall { api.updateMatchStatus(matchId, UpdateMatchStatusRequest(status)) }

    suspend fun getActiveMatches(): Result<List<MatchData>> =
        safeApiCall { api.getActiveMatches() }

    suspend fun calculateCost(request: CalculateCostRequest): Result<CostSharingResponse> =
        safeApiCall { api.calculateCost(request) }

    suspend fun getFuelPrices(): Result<FuelPricesResponse> =
        safeApiCall { api.getFuelPrices() }

    suspend fun getMyRides(): Result<MyRidesResponse> =
        safeApiCall { api.getMyRides() }
}
