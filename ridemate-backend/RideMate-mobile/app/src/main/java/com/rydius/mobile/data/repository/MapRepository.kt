package com.rydius.mobile.data.repository

import com.rydius.mobile.data.api.ApiClient
import com.rydius.mobile.data.model.*

class MapRepository {

    private val api = ApiClient.api

    suspend fun autocomplete(query: String, limit: Int = 5): Result<List<Prediction>> =
        try {
            val response = api.autocomplete(query, limit)
            if (response.isSuccessful) {
                Result.success(response.body()?.predictions ?: emptyList())
            } else {
                Result.failure(Exception("Autocomplete failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun geocode(query: String): Result<GeoLocation?> =
        try {
            val response = api.geocode(query)
            if (response.isSuccessful) {
                Result.success(response.body()?.location)
            } else {
                Result.failure(Exception("Geocode failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun reverseGeocode(lat: Double, lng: Double): Result<ReverseGeocodeResponse> =
        try {
            val response = api.reverseGeocode(lat, lng)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Reverse geocode failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun getDirections(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ): Result<DirectionsResponse> =
        try {
            val origin = "$originLat,$originLng"
            val destination = "$destLat,$destLng"
            val response = api.directions(origin, destination)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Directions failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Hardcoded local locations as fallback when the API is unreachable.
     * Mirrors the web app's location-autocomplete.js.
     */
    fun getLocalSuggestions(query: String, limit: Int = 5): List<Prediction> {
        val q = query.lowercase()
        return LOCAL_LOCATIONS
            .filter { it.first.lowercase().contains(q) }
            .take(limit)
            .map { (name, lat, lng) ->
                Prediction(
                    description = name,
                    placeId = null,
                    lat = lat,
                    lng = lng,
                    types = listOf("local")
                )
            }
    }

    companion object {
        private val LOCAL_LOCATIONS = listOf(
            Triple("Satellite, Ahmedabad", 23.0225, 72.5714),
            Triple("Vastrapur, Ahmedabad", 23.0369, 72.5294),
            Triple("Navrangpura, Ahmedabad", 23.0388, 72.5616),
            Triple("Paldi, Ahmedabad", 23.0103, 72.5654),
            Triple("Maninagar, Ahmedabad", 23.0006, 72.6020),
            Triple("Bopal, Ahmedabad", 23.0348, 72.4637),
            Triple("SG Highway, Ahmedabad", 23.0469, 72.5117),
            Triple("Prahlad Nagar, Ahmedabad", 23.0146, 72.5108),
            Triple("C.G. Road, Ahmedabad", 23.0258, 72.5628),
            Triple("Ellis Bridge, Ahmedabad", 23.0284, 72.5633),
            Triple("Ashram Road, Ahmedabad", 23.0300, 72.5700),
            Triple("Law Garden, Ahmedabad", 23.0311, 72.5575),
            Triple("Thaltej, Ahmedabad", 23.0540, 72.4990),
            Triple("Gota, Ahmedabad", 23.1069, 72.5449),
            Triple("Chandkheda, Ahmedabad", 23.1130, 72.5860),
            Triple("Gandhinagar", 23.2156, 72.6369),
            Triple("Surat", 21.1702, 72.8311),
            Triple("Vadodara", 22.3072, 73.1812),
            Triple("Rajkot", 22.3039, 70.8022),
            Triple("Mumbai", 19.0760, 72.8777),
            Triple("Delhi", 28.6139, 77.2090),
            Triple("Bangalore", 12.9716, 77.5946),
            Triple("Pune", 18.5204, 73.8567),
            Triple("Jaipur", 26.9124, 75.7873),
            Triple("Hyderabad", 17.3850, 78.4867),
        )
    }
}
