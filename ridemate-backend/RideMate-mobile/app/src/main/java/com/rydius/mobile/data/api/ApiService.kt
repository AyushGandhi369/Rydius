package com.rydius.mobile.data.api

import com.rydius.mobile.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Auth ────────────────────────────────────────────────────
    @POST("api/signup")
    suspend fun signup(@Body body: SignupRequest): Response<ApiResponse>

    @POST("api/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): Response<ApiResponse>

    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("api/logout")
    suspend fun logout(): Response<ApiResponse>

    @GET("api/auth/status")
    suspend fun authStatus(): Response<AuthStatusResponse>

    @GET("api/config")
    suspend fun getConfig(): Response<ConfigResponse>

    // ── Maps (backend proxy) ────────────────────────────────────
    @GET("api/maps/autocomplete")
    suspend fun autocomplete(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5
    ): Response<AutocompleteResponse>

    @GET("api/maps/geocode")
    suspend fun geocode(@Query("query") query: String): Response<GeocodeResponse>

    @GET("api/maps/reverse-geocode")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): Response<ReverseGeocodeResponse>

    @GET("api/maps/directions")
    suspend fun directions(
        @Query("origin") origin: String,
        @Query("destination") destination: String
    ): Response<DirectionsResponse>

    // ── Trips (driver) ──────────────────────────────────────────
    @POST("api/trips")
    suspend fun createTrip(@Body body: CreateTripRequest): Response<CreateTripResponse>

    @GET("api/trips/active")
    suspend fun getActiveTrip(): Response<ActiveTripResponse>

    @GET("api/trips/{id}")
    suspend fun getTrip(@Path("id") id: Int): Response<TripResponse>

    @GET("api/trips/{id}/requests")
    suspend fun getTripRequests(@Path("id") id: Int): Response<TripRequestsResponse>

    @GET("api/trips/{id}/route-segment")
    suspend fun getTripRouteSegment(
        @Path("id") id: Int,
        @Query("pickup_lat") pickupLat: Double,
        @Query("pickup_lng") pickupLng: Double,
        @Query("dropoff_lat") dropoffLat: Double,
        @Query("dropoff_lng") dropoffLng: Double
    ): Response<RouteSegmentResponse>

    @PUT("api/trips/{id}/cancel")
    suspend fun cancelTrip(@Path("id") id: Int): Response<ApiResponse>

    // ── Ride requests (passenger) ───────────────────────────────
    @POST("api/ride-requests")
    suspend fun createRideRequest(@Body body: CreateRideRequestRequest): Response<CreateRideRequestResponse>

    @GET("api/available-drivers")
    suspend fun getAvailableDrivers(
        @Query("pickup_lat") pickupLat: Double,
        @Query("pickup_lng") pickupLng: Double,
        @Query("dropoff_lat") dropoffLat: Double,
        @Query("dropoff_lng") dropoffLng: Double,
        @Query("distance_km") distanceKm: Double
    ): Response<AvailableDriversResponse>

    // ── Matches ─────────────────────────────────────────────────
    @POST("api/matches")
    suspend fun createMatch(@Body body: CreateMatchRequest): Response<CreateMatchResponse>

    @PUT("api/matches/{id}/status")
    suspend fun updateMatchStatus(
        @Path("id") id: Int,
        @Body body: UpdateMatchStatusRequest
    ): Response<ApiResponse>

    @GET("api/matches/active")
    suspend fun getActiveMatches(): Response<ActiveMatchesResponse>

    // ── Cost sharing ────────────────────────────────────────────
    @POST("api/cost-sharing/calculate")
    suspend fun calculateCost(@Body body: CalculateCostRequest): Response<CostSharingResponse>

    @GET("api/cost-sharing/fuel-prices")
    suspend fun getFuelPrices(): Response<FuelPricesResponse>
}
