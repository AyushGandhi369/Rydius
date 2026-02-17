package com.rydius.mobile.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rydius.mobile.util.Constants
import com.rydius.mobile.util.LocationHelper
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

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
    val styleUrl = remember(olaMapsApiKey) {
        if (olaMapsApiKey.isBlank()) Constants.OLA_STYLE_URL
        else "${Constants.OLA_STYLE_URL}?api_key=$olaMapsApiKey"
    }

    // Decode the polyline once and remember the result
    val decodedPoints = remember(routePolyline) {
        routePolyline?.let { LocationHelper.decodePolyline(it) } ?: emptyList()
    }

    val mapView = remember {
        MapLibre.getInstance(context)
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
                map.setStyle(styleUrl) { style ->
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
