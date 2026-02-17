package com.rydius.mobile.util

object Constants {
    val BASE_URL: String get() = com.rydius.mobile.BuildConfig.BASE_URL.trimEnd('/') + "/"
    val OLA_API_KEY: String get() = com.rydius.mobile.BuildConfig.OLA_MAPS_API_KEY

    const val OLA_STYLE_URL = "https://api.olamaps.io/tiles/vector/v1/styles/default-light-standard/style.json"

    // Default map center — Ahmedabad
    const val DEFAULT_LAT = 23.0225
    const val DEFAULT_LNG = 72.5714
    const val DEFAULT_ZOOM = 12.0

    // Roles
    const val ROLE_RIDER = "rider"
    const val ROLE_DRIVER = "driver"

    // Match status
    const val STATUS_PENDING = "pending"
    const val STATUS_ACCEPTED = "accepted"
    const val STATUS_REJECTED = "rejected"

    // Trip status
    const val TRIP_ACTIVE = "active"
    const val TRIP_COMPLETED = "completed"
    const val TRIP_CANCELLED = "cancelled"
}
