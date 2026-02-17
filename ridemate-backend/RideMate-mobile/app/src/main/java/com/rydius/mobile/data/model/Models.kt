package com.rydius.mobile.data.model

import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════════════════════════
//  REQUEST MODELS
// ═══════════════════════════════════════════════════════════════

data class SignupRequest(
    val name: String,
    val email: String,
    val password: String
)

data class VerifyOtpRequest(
    val email: String,
    val otp: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class CreateTripRequest(
    @SerializedName("startLocation")    val startLocation: String,
    @SerializedName("startLocationLat") val startLocationLat: Double,
    @SerializedName("startLocationLng") val startLocationLng: Double,
    @SerializedName("endLocation")      val endLocation: String,
    @SerializedName("endLocationLat")   val endLocationLat: Double,
    @SerializedName("endLocationLng")   val endLocationLng: Double,
    @SerializedName("routePolyline")    val routePolyline: String? = null,
    @SerializedName("distanceKm")       val distanceKm: Double,
    @SerializedName("durationMinutes")  val durationMinutes: Int,
    @SerializedName("departureTime")    val departureTime: String,
    @SerializedName("availableSeats")   val availableSeats: Int = 1
)

data class CreateRideRequestRequest(
    @SerializedName("pickupLocation")  val pickupLocation: String,
    @SerializedName("pickupLat")       val pickupLat: Double,
    @SerializedName("pickupLng")       val pickupLng: Double,
    @SerializedName("dropoffLocation") val dropoffLocation: String,
    @SerializedName("dropoffLat")      val dropoffLat: Double,
    @SerializedName("dropoffLng")      val dropoffLng: Double,
    @SerializedName("requestedTime")   val requestedTime: String,
    @SerializedName("travelMode")      val travelMode: String = "4W"
)

data class CreateMatchRequest(
    @SerializedName("trip_id")          val tripId: Int,
    @SerializedName("ride_request_id")  val rideRequestId: Int,
    @SerializedName("pickup_lat")       val pickupLat: Double,
    @SerializedName("pickup_lng")       val pickupLng: Double,
    @SerializedName("dropoff_lat")      val dropoffLat: Double,
    @SerializedName("dropoff_lng")      val dropoffLng: Double,
    val fare: Double
)

data class UpdateMatchStatusRequest(val status: String)

data class CalculateCostRequest(
    @SerializedName("distanceInKm")       val distanceInKm: Double,
    @SerializedName("vehicleType")        val vehicleType: String = "4W",
    @SerializedName("fuelType")           val fuelType: String = "petrol",
    @SerializedName("numberOfPassengers") val numberOfPassengers: Int = 1
)

// ═══════════════════════════════════════════════════════════════
//  RESPONSE MODELS
// ═══════════════════════════════════════════════════════════════

data class ApiResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null
)

data class LoginResponse(
    val success: Boolean = false,
    val message: String? = null,
    val user: UserInfo? = null
)

data class AuthStatusResponse(
    val loggedIn: Boolean = false,
    val user: UserInfo? = null
)

data class UserInfo(
    val id: Int = -1,
    val name: String = "",
    val email: String = ""
)

data class ConfigResponse(
    @SerializedName("olaMapsApiKey") val olaMapsApiKey: String? = null
)

// ── Maps ────────────────────────────────────────────────────────

data class AutocompleteResponse(
    val predictions: List<Prediction>? = null
)

data class Prediction(
    val description: String = "",
    @SerializedName("place_id") val placeId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val types: List<String>? = null
)

data class GeocodeResponse(
    val location: GeoLocation? = null
)

data class GeoLocation(
    val description: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerializedName("place_id") val placeId: String? = null
)

data class ReverseGeocodeResponse(
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerializedName("place_id") val placeId: String? = null
)

data class DirectionsResponse(
    val routes: List<Route>? = null
)

data class Route(
    val legs: List<Leg>? = null,
    @SerializedName("overview_polyline") val overviewPolyline: String? = null
)

data class Leg(
    val distance: MetricValue? = null,
    val duration: MetricValue? = null
)

data class MetricValue(
    val text: String? = null,
    val value: Double? = null
)

// ── Trips ───────────────────────────────────────────────────────

data class CreateTripResponse(
    val success: Boolean = false,
    @SerializedName("tripId") val tripId: Int? = null,
    val message: String? = null
)

data class ActiveTripResponse(
    val trip: TripData? = null
)

data class TripResponse(
    val trip: TripData? = null
)

data class TripData(
    val id: Int = 0,
    @SerializedName("driver_id")          val driverId: Int = 0,
    @SerializedName("start_location")     val startLocation: String = "",
    @SerializedName("start_location_lat") val startLocationLat: Double = 0.0,
    @SerializedName("start_location_lng") val startLocationLng: Double = 0.0,
    @SerializedName("end_location")       val endLocation: String = "",
    @SerializedName("end_location_lat")   val endLocationLat: Double = 0.0,
    @SerializedName("end_location_lng")   val endLocationLng: Double = 0.0,
    @SerializedName("route_polyline")     val routePolyline: String? = null,
    @SerializedName("distance_km")        val distanceKm: Double = 0.0,
    @SerializedName("duration_minutes")   val durationMinutes: Int = 0,
    @SerializedName("departure_time")     val departureTime: String? = null,
    val status: String = "",
    @SerializedName("driver_name")        val driverName: String? = null,
    @SerializedName("available_seats")    val availableSeats: Int = 1
)

data class TripRequestsResponse(
    val requests: List<MatchData>? = null
)

data class RouteSegmentResponse(
    val segment: List<List<Double>>? = null,
    @SerializedName("pickup_point")  val pickupPoint: LatLng? = null,
    @SerializedName("dropoff_point") val dropoffPoint: LatLng? = null
)

data class LatLng(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)

// ── Ride requests ───────────────────────────────────────────────

data class CreateRideRequestResponse(
    val success: Boolean = false,
    @SerializedName("rideRequestId") val rideRequestId: Int? = null,
    val message: String? = null
)

data class AvailableDriversResponse(
    val drivers: List<AvailableDriver>? = null
)

data class AvailableDriver(
    @SerializedName("trip_id")          val tripId: Int = 0,
    @SerializedName("driver_name")      val driverName: String? = null,
    @SerializedName("start_location")   val startLocation: String = "",
    @SerializedName("end_location")     val endLocation: String = "",
    @SerializedName("departure_time")   val departureTime: String? = null,
    @SerializedName("distance_km")      val distanceKm: Double = 0.0,
    @SerializedName("pickup_distance")  val pickupDistance: Double = 0.0,
    @SerializedName("dropoff_distance") val dropoffDistance: Double = 0.0,
    val fare: Double? = null,
    @SerializedName("vehicle_type")     val vehicleType: String? = null,
    val rating: Double? = null,
    @SerializedName("route_polyline")   val routePolyline: String? = null,
    @SerializedName("start_location_lat") val startLat: Double? = null,
    @SerializedName("start_location_lng") val startLng: Double? = null,
    @SerializedName("end_location_lat")   val endLat: Double? = null,
    @SerializedName("end_location_lng")   val endLng: Double? = null
)

// ── Matches ─────────────────────────────────────────────────────

data class CreateMatchResponse(
    val success: Boolean = false,
    val message: String? = null,
    @SerializedName("matchId") val matchId: Int? = null
)

data class MatchData(
    val id: Int = 0,
    @SerializedName("trip_id")          val tripId: Int = 0,
    @SerializedName("ride_request_id")  val rideRequestId: Int = 0,
    @SerializedName("pickup_point_lat") val pickupLat: Double? = null,
    @SerializedName("pickup_point_lng") val pickupLng: Double? = null,
    @SerializedName("dropoff_point_lat") val dropoffLat: Double? = null,
    @SerializedName("dropoff_point_lng") val dropoffLng: Double? = null,
    @SerializedName("fare_amount")       val fare: Double? = null,
    val status: String = "",
    @SerializedName("passenger_name")   val passengerName: String? = null,
    @SerializedName("driver_name")      val driverName: String? = null,
    @SerializedName("pickup_location")  val pickupLocation: String? = null,
    @SerializedName("dropoff_location") val dropoffLocation: String? = null
)

data class ActiveMatchesResponse(
    val matches: List<MatchData>? = null
)

// ── Cost sharing ────────────────────────────────────────────────

data class CostSharingResponse(
    @SerializedName("baseTripCost")     val baseTripCost: Double? = null,
    @SerializedName("passengerPool")    val passengerPool: Double? = null,
    @SerializedName("costPerPassenger") val costPerPassenger: Double? = null,
    @SerializedName("driverSaved")      val driverSaved: Double? = null,
    @SerializedName("savingsVsTaxi")    val savingsVsTaxi: Double? = null,
    @SerializedName("savingsPercentage") val savingsPercentage: Double? = null,
    @SerializedName("co2SavedKg")       val co2SavedKg: Double? = null,
    val message: String? = null
)

data class FuelPricesResponse(
    val prices: List<FuelPrice>? = null
)

data class FuelPrice(
    @SerializedName("vehicle_type") val vehicleType: String = "",
    @SerializedName("fuel_type")    val fuelType: String = "",
    @SerializedName("cost_per_km")  val costPerKm: Double = 0.0
)
