package com.rydius.mobile.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonParser
import com.rydius.mobile.BuildConfig
import com.rydius.mobile.util.Constants
import com.rydius.mobile.util.LocationHelper
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val TAG = "RideMateMap"

/** Ensures the OkHttp interceptor for Ola Maps API key injection is set up exactly once. */
private var olaHttpClientConfigured = false
private var olaOkHttpClient: OkHttpClient? = null
private var cachedOlaStyleJson: String? = null

private fun buildOlaOkHttpClient(apiKey: String): OkHttpClient {
    val dispatcher = Dispatcher().apply {
        // Match MapLibre's internal defaults (higher parallelism improves tile loading).
        maxRequests = 64
        maxRequestsPerHost = 20
    }

    return OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .addInterceptor { chain ->
            val original = chain.request()
            val url = original.url.toString()
            if (url.contains("olamaps.io") && !url.contains("api_key=")) {
                val separator = if (url.contains("?")) "&" else "?"
                val newUrl = "${url}${separator}api_key=$apiKey"
                chain.proceed(original.newBuilder().url(newUrl).build())
            } else {
                chain.proceed(original)
            }
        }
        .build()
}

private fun configureOlaHttpClient(apiKey: String) {
    if (olaHttpClientConfigured) return
    olaHttpClientConfigured = true
    olaOkHttpClient = buildOlaOkHttpClient(apiKey)
    HttpRequestUtil.setOkHttpClient(olaOkHttpClient)
}

private fun patchOlaStyleJson(raw: String): String {
    // Patch known issues in Ola's hosted style JSON that trip up MapLibre:
    // 1. Sprite URL has a querystring (e.g. ".../sprite?key=0.4.0"). MapLibre appends
    //    ".json"/".png" after the full URL, producing invalid paths. Safe to strip
    //    because we inject auth with `api_key` via OkHttp interceptor.
    // 2. Some layers define `line-dasharray` with < 2 elements. MapLibre requires at
    //    least [dash, gap]; fewer causes "line dasharray requires at least two elements"
    //    and the layer silently fails to render.
    return try {
        val obj = JsonParser.parseString(raw).asJsonObject

        // Fix sprite querystring
        val sprite = obj.get("sprite")?.asString
        if (!sprite.isNullOrBlank()) {
            val cleaned = sprite.substringBefore('?')
            if (cleaned.isNotBlank()) obj.addProperty("sprite", cleaned)
        }

        // Fix malformed line-dasharray entries
        val layers = obj.getAsJsonArray("layers")
        if (layers != null) {
            for (element in layers) {
                if (!element.isJsonObject) continue
                val paint = element.asJsonObject.getAsJsonObject("paint") ?: continue
                val dash = paint.get("line-dasharray")
                if (dash != null && dash.isJsonArray && dash.asJsonArray.size() < 2) {
                    paint.remove("line-dasharray")
                }
            }
        }

        obj.toString()
    } catch (_: Exception) {
        raw
    }
}

private suspend fun fetchStyleJson(client: OkHttpClient, url: String): String =
    withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty response body")
        }
    }

private sealed interface MapStyle {
    object Fallback : MapStyle
    object LoadingOla : MapStyle
    data class OlaJson(val json: String) : MapStyle
}

private sealed interface OlaStyleState {
    object Disabled : OlaStyleState
    object Loading : OlaStyleState
    data class Loaded(val json: String) : OlaStyleState
    data class Error(val message: String) : OlaStyleState
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

    // MapLibre must be initialized before constructing a MapView.
    remember(context) { MapLibre.getInstance(context) }

    val olaMapsApiKey = Constants.OLA_API_KEY
    val hasValidOlaKey = remember(olaMapsApiKey) {
        val k = olaMapsApiKey.trim()
        k.isNotBlank() &&
            !k.contains("your_key_here", ignoreCase = true) &&
            !k.contains("your_ola_maps_api_key_here", ignoreCase = true)
    }

    // Configure OkHttp interceptor so ALL MapLibre requests to olamaps.io include the api_key.
    if (hasValidOlaKey) {
        configureOlaHttpClient(olaMapsApiKey)
    }

    var olaRetryToken by remember { mutableStateOf(0) }

    // Fetch + patch the Ola style JSON. We keep the fetch URL without `api_key` so the key isn't
    // baked into style strings; the OkHttp interceptor injects it for requests.
    val olaStyleState by produceState<OlaStyleState>(
        initialValue = when {
            !hasValidOlaKey -> OlaStyleState.Disabled
            cachedOlaStyleJson != null -> OlaStyleState.Loaded(cachedOlaStyleJson!!)
            else -> OlaStyleState.Loading
        },
        key1 = hasValidOlaKey,
        key2 = olaMapsApiKey,
        key3 = olaRetryToken,
    ) {
        if (!hasValidOlaKey) {
            value = OlaStyleState.Disabled
            return@produceState
        }
        cachedOlaStyleJson?.let {
            value = OlaStyleState.Loaded(it)
            return@produceState
        }
        value = OlaStyleState.Loading
        val client = olaOkHttpClient ?: return@produceState
        value = try {
            val patched = patchOlaStyleJson(fetchStyleJson(client, Constants.OLA_STYLE_URL))
            cachedOlaStyleJson = patched
            if (BuildConfig.DEBUG) Log.d(TAG, "Ola style JSON loaded (len=${patched.length})")
            OlaStyleState.Loaded(patched)
        } catch (e: CancellationException) {
            // Don't treat recomposition cancellation as an error state.
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Ola style JSON load failed: ${e.javaClass.simpleName}: ${e.message}")
            OlaStyleState.Error(e.message ?: "Failed to load Ola style")
        }
    }

    // Decode the polyline once and remember the result
    val decodedPoints = remember(routePolyline) {
        routePolyline?.let { LocationHelper.decodePolyline(it) } ?: emptyList()
    }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    var lastRenderSignature by remember { mutableStateOf("") }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    var mapLoadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(mapView) {
        val failListener = MapView.OnDidFailLoadingMapListener { msg ->
            mapLoadError = msg
            if (BuildConfig.DEBUG) Log.w(TAG, "Map load failed: $msg")
        }
        mapView.addOnDidFailLoadingMapListener(failListener)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.removeOnDidFailLoadingMapListener(failListener)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    val mapStyle: MapStyle = remember(hasValidOlaKey, olaStyleState, mapLoadError) {
        when {
            !hasValidOlaKey -> MapStyle.Fallback
            olaStyleState is OlaStyleState.Error -> MapStyle.Fallback
            mapLoadError != null -> MapStyle.Fallback
            olaStyleState is OlaStyleState.Loaded -> MapStyle.OlaJson((olaStyleState as OlaStyleState.Loaded).json)
            else -> MapStyle.LoadingOla
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val renderSignature = listOf(
                    startLat, startLng, endLat, endLng, routePolyline,
                    when (mapStyle) {
                        is MapStyle.Fallback -> "fallback"
                        is MapStyle.LoadingOla -> "ola_loading"
                        is MapStyle.OlaJson -> "ola:${mapStyle.json.hashCode()}"
                    }
                ).joinToString("|")
                if (renderSignature == lastRenderSignature) return@AndroidView
                lastRenderSignature = renderSignature

                view.getMapAsync { map ->
                    mapInstance = map
                    if (mapStyle is MapStyle.LoadingOla) return@getMapAsync

                    fun renderStyle(style: Style) {
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

                    // Load style; for Ola we supply patched JSON to avoid sprite querystring issues.
                    when (mapStyle) {
                        is MapStyle.Fallback -> {
                            map.setStyle(Constants.FALLBACK_STYLE_URL) { s -> renderStyle(s) }
                        }
                        is MapStyle.OlaJson -> {
                            mapLoadError = null
                            map.setStyle(Style.Builder().fromJson(mapStyle.json)) { s ->
                                if (BuildConfig.DEBUG) {
                                    val fullyLoaded = try { s.isFullyLoaded } catch (_: Exception) { false }
                                    Log.d(TAG, "Ola style applied (fullyLoaded=$fullyLoaded)")
                                }
                                renderStyle(s)
                            }
                        }
                        is MapStyle.LoadingOla -> Unit
                    }
                }
            }
        )

        if (mapStyle is MapStyle.LoadingOla) {
            // Prefer Ola maps: show a loading overlay instead of switching to fallback tiles.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0x22FFFFFF)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        if (hasValidOlaKey && (olaStyleState is OlaStyleState.Error || mapLoadError != null)) {
            val msg = when {
                mapLoadError != null -> mapLoadError
                else -> (olaStyleState as? OlaStyleState.Error)?.message
            } ?: "Ola Maps failed to load"

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            text = "Ola Maps error",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                mapLoadError = null
                                cachedOlaStyleJson = null
                                olaRetryToken += 1
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Retry Ola")
                        }
                    }
                }
            }
        }
    }
}

private fun addCircleMarker(
    style: Style,
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
