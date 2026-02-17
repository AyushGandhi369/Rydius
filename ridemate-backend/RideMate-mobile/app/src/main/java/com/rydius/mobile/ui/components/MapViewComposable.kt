package com.rydius.mobile.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rydius.mobile.BuildConfig
import com.rydius.mobile.util.Constants
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.utils.BitmapUtils

@Composable
fun MapViewComposable(
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double,
    routePolyline: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val styleUrl = "${Constants.OLA_STYLE_URL}?api_key=${BuildConfig.OLA_MAPS_API_KEY}"

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }

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
        modifier = modifier.clip(RoundedCornerShape(topStart = androidx.compose.ui.unit.Dp(0f), topEnd = androidx.compose.ui.unit.Dp(0f))),
        update = { view ->
            view.getMapAsync { map ->
                map.setStyle(styleUrl) { style ->
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false

                    // Draw route polyline
                    if (routePolyline.isNotEmpty()) {
                        try {
                            val lineManager = LineManager(view, map, style)
                            val routePoints = routePolyline.map { (lat, lng) ->
                                LatLng(lat, lng)
                            }
                            lineManager.create(
                                LineOptions()
                                    .withLatLngs(routePoints)
                                    .withLineColor("#00B4D8")
                                    .withLineWidth(4.0f)
                                    .withLineOpacity(0.8f)
                            )
                        } catch (_: Exception) {
                            // LineManager not available, skip polyline
                        }
                    }

                    // Fit camera to start + end bounds
                    val boundsBuilder = LatLngBounds.Builder()
                    boundsBuilder.include(LatLng(startLat, startLng))
                    boundsBuilder.include(LatLng(endLat, endLng))

                    if (routePolyline.isNotEmpty()) {
                        routePolyline.forEach { (lat, lng) ->
                            boundsBuilder.include(LatLng(lat, lng))
                        }
                    }

                    try {
                        val bounds = boundsBuilder.build()
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngBounds(bounds, 60)
                        )
                    } catch (_: Exception) {
                        // If bounds fail, center between start and end
                        val centerLat = (startLat + endLat) / 2
                        val centerLng = (startLng + endLng) / 2
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(centerLat, centerLng), 11.0
                            )
                        )
                    }

                    // Add start marker (green circle)
                    addCircleMarker(map, style, "start", startLat, startLng, "#22C55E")
                    // Add end marker (red circle)
                    addCircleMarker(map, style, "end", endLat, endLng, "#EF4444")
                }
            }
        }
    )
}

private fun addCircleMarker(
    map: MapLibreMap,
    style: org.maplibre.android.maps.Style,
    id: String,
    lat: Double,
    lng: Double,
    color: String
) {
    try {
        // Use a GeoJSON source + circle layer for markers
        val sourceId = "marker-source-$id"
        val layerId = "marker-layer-$id"

        val geoJson = """
            {
                "type": "FeatureCollection",
                "features": [{
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [$lng, $lat]
                    }
                }]
            }
        """.trimIndent()

        val source = org.maplibre.android.style.sources.GeoJsonSource(sourceId, geoJson)
        style.addSource(source)

        val layer = org.maplibre.android.style.layers.CircleLayer(layerId, sourceId)
        layer.setProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(color),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor("#FFFFFF")
        )
        style.addLayer(layer)
    } catch (_: Exception) {
        // Marker already added or style issue
    }
}
