package com.rydius.mobile.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rydius.mobile.util.Constants
import com.rydius.mobile.util.LocationHelper
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

/** Ensures the OkHttp interceptor for Ola Maps API key injection is set up exactly once. */
private var olaHttpClientConfigured = false

private fun configureOlaHttpClient(context: android.content.Context, apiKey: String) {
    if (olaHttpClientConfigured) return
    // MapLibre MUST be initialised before touching HttpRequestUtil
    MapLibre.getInstance(context)
    olaHttpClientConfigured = true
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()
            if (url.contains("api.olamaps.io") && !url.contains("api_key")) {
                val separator = if (url.contains("?")) "&" else "?"
                val newUrl = "${url}${separator}api_key=$apiKey"
                chain.proceed(original.newBuilder().url(newUrl).build())
            } else {
                chain.proceed(original)
            }
        }
        .build()
    HttpRequestUtil.setOkHttpClient(client)
}

/**
 * MapLibre map composable that renders Ola Maps tiles with route polyline and markers.
 *
 * @param routePolyline Encoded polyline string (Google/Ola format) OR null.
 *   The component decodes it internally via [LocationHelper.decodePolyline].
 */
@Composable
fun MapViewComposable(
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double,
    routePolyline: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val olaMapsApiKey = Constants.OLA_API_KEY
    val hasValidOlaKey = remember(olaMapsApiKey) {
        val k = olaMapsApiKey.trim()
        k.isNotBlank() &&
            !k.contains("your_key_here", ignoreCase = true) &&
            !k.contains("your_ola_maps_api_key_here", ignoreCase = true)
    }
    val styleUrl = remember(olaMapsApiKey) {
        if (!hasValidOlaKey) {
            Constants.FALLBACK_STYLE_URL
        } else {
            "${Constants.OLA_STYLE_URL}?api_key=$olaMapsApiKey"
        }
    }

    // Configure OkHttp interceptor so ALL MapLibre requests to api.olamaps.io include the api_key.
    // This also initialises MapLibre internally, so it must run before creating MapView.
    if (hasValidOlaKey) {
        configureOlaHttpClient(context, olaMapsApiKey)
    }

    // Decode the polyline once and remember the result
    val decodedPoints = remember(routePolyline) {
        routePolyline?.let { LocationHelper.decodePolyline(it) } ?: emptyList()
    }

    val mapView = remember {
        if (!hasValidOlaKey) MapLibre.getInstance(context)   // fallback path still needs init
        MapView(context).apply { onCreate(null) }
    }

    // Re-render map only when significant route inputs change.
    var lastRenderSignature by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            val renderSignature = listOf(
                startLat, startLng, endLat, endLng, routePolyline, styleUrl
            ).joinToString("|")
            if (renderSignature == lastRenderSignature) return@AndroidView
            lastRenderSignature = renderSignature

            view.getMapAsync { map ->
                fun renderStyle(style: org.maplibre.android.maps.Style) {
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false

                    // Draw route polyline via GeoJSON LineString
                    if (decodedPoints.isNotEmpty()) {
                        try {
                            // Remove existing sources/layers to prevent duplicates
                            try { style.removeLayer("route-layer") } catch (_: Exception) {}
                            try { style.removeSource("route-source") } catch (_: Exception) {}

                            val coords = decodedPoints.joinToString(",") { (lat, lng) ->
                                "[$lng,$lat]"
                            }
                            val lineGeoJson = """
                                {"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]}}
                            """.trimIndent()
                            style.addSource(GeoJsonSource("route-source", lineGeoJson))
                            val lineLayer = LineLayer("route-layer", "route-source")
                            lineLayer.setProperties(
                                PropertyFactory.lineColor("#00B4D8"),
                                PropertyFactory.lineWidth(5f),
                                PropertyFactory.lineOpacity(0.85f)
                            )
                            style.addLayer(lineLayer)
                        } catch (_: Exception) { }
                    }

                    // Fit camera to bounds
                    val boundsBuilder = LatLngBounds.Builder()
                    boundsBuilder.include(LatLng(startLat, startLng))
                    boundsBuilder.include(LatLng(endLat, endLng))
                    decodedPoints.forEach { (lat, lng) -> boundsBuilder.include(LatLng(lat, lng)) }

                    try {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 60))
                    } catch (_: Exception) {
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng((startLat + endLat) / 2, (startLng + endLng) / 2), 11.0
                            )
                        )
                    }

                    // Start marker (green)
                    addCircleMarker(style, "start", startLat, startLng, "#22C55E")
                    // End marker (red)
                    addCircleMarker(style, "end", endLat, endLng, "#EF4444")
                }

                // Load style — interceptor ensures api_key is on all sub-requests
                var styleLoaded = false
                map.setStyle(styleUrl) { style ->
                    styleLoaded = true
                    renderStyle(style)
                }

                // Safety-net: if the Ola style hasn't loaded after 6s, fall back to free tiles
                if (styleUrl != Constants.FALLBACK_STYLE_URL) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!styleLoaded) {
                            map.setStyle(Constants.FALLBACK_STYLE_URL) { style ->
                                renderStyle(style)
                            }
                        }
                    }, 6000)
                }
            }
        }
    )
}

private fun addCircleMarker(
    style: org.maplibre.android.maps.Style,
    id: String,
    lat: Double,
    lng: Double,
    color: String
) {
    try {
        // Remove existing to prevent duplicates
        try { style.removeLayer("circle-$id") } catch (_: Exception) {}
        try { style.removeSource("marker-$id") } catch (_: Exception) {}

        val geoJson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]}}"""
        style.addSource(GeoJsonSource("marker-$id", geoJson))
        val layer = CircleLayer("circle-$id", "marker-$id")
        layer.setProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(color),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor("#FFFFFF")
        )
        style.addLayer(layer)
    } catch (_: Exception) { }
}
