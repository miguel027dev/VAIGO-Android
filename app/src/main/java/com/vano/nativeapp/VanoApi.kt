package com.vano.nativeapp

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VanoApi(private val baseUrl: String = BuildConfig.VANO_BASE_URL.trimEnd('/')) {
    suspend fun bootstrap(): JSONObject = getJson("/api/mobile/native-map/config")

    suspend fun search(query: String, proximity: LatLngPoint?): List<SearchPlace> {
        if (query.trim().length < 3) return emptyList()
        val b = Uri.parse("$baseUrl/api/geocode").buildUpon().appendQueryParameter("q", query.trim())
        proximity?.let {
            b.appendQueryParameter("proximity_lat", it.lat.toString())
            b.appendQueryParameter("proximity_lon", it.lon.toString())
        }
        val root = getJsonAbsolute(b.build().toString())
        val arr = root.optJSONArray("results") ?: JSONArray()
        return buildList {
            for (i in 0 until minOf(arr.length(), 10)) {
                val x = arr.optJSONObject(i) ?: continue
                val lat = x.optDouble("lat", Double.NaN)
                val lon = x.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) continue
                add(SearchPlace(
                    name = x.optString("name", x.optString("label", "Local")),
                    label = x.optString("label", ""),
                    category = x.optString("category", "Local"),
                    lat = lat, lon = lon,
                    distanceM = if (x.has("distance_m")) x.optInt("distance_m") else null,
                ))
            }
        }
    }

    suspend fun route(
        start: LatLngPoint,
        end: LatLngPoint,
        vehicle: VehicleProfile,
        mode: RouteMode,
        bearing: Double? = null,
        speedMps: Double? = null,
    ): List<RouteOption> {
        val profile = if (vehicle == VehicleProfile.MOTORCYCLE) "motorcycle" else "driving"
        val modeName = when (mode) { RouteMode.SMART -> "smart"; RouteMode.FASTEST -> "fastest"; RouteMode.SAFEST -> "safest" }
        val b = Uri.parse("$baseUrl/api/route").buildUpon()
            .appendQueryParameter("start_lat", start.lat.toString())
            .appendQueryParameter("start_lon", start.lon.toString())
            .appendQueryParameter("end_lat", end.lat.toString())
            .appendQueryParameter("end_lon", end.lon.toString())
            .appendQueryParameter("profile", profile)
            .appendQueryParameter("mode", modeName)
            .appendQueryParameter("adaptive", "1")
            .appendQueryParameter("mobile_compact", "1")
            .appendQueryParameter("variant_budget", "4")
        bearing?.takeIf { it.isFinite() }?.let { b.appendQueryParameter("start_bearing", it.toString()) }
        speedMps?.takeIf { it.isFinite() }?.let { b.appendQueryParameter("start_speed", it.toString()) }
        val root = getJsonAbsolute(b.build().toString(), mapOf("X-VANO-Mobile-Compact" to "1"))
        val arr = root.optJSONArray("routes") ?: JSONArray()
        val selected = root.optString("selected_id", "")
        return buildList {
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                val coords = readCoordinates(x.opt("geometry"))
                if (coords.size < 2) continue
                val id = x.opt("id")?.toString() ?: i.toString()
                add(RouteOption(
                    id = id,
                    coordinates = coords,
                    durationSec = x.optDouble("duration", x.optDouble("duration_seconds", 0.0)),
                    distanceM = x.optDouble("distance", x.optDouble("distance_m", 0.0)),
                    safetyScore = x.optNullableDouble("safety_conservative_score") ?: x.optNullableDouble("safety_score"),
                    trafficScore = x.optNullableDouble("traffic_score"),
                    label = if (id == selected) "Recomendada" else "Alternativa ${i + 1}",
                ))
            }
        }
    }

    private fun readCoordinates(raw: Any?): List<LatLngPoint> {
        val arr = when (raw) {
            is JSONObject -> raw.optJSONArray("coordinates")
            is String -> runCatching { JSONObject(raw).optJSONArray("coordinates") }.getOrNull()
            else -> null
        } ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val pair = arr.optJSONArray(i) ?: continue
                if (pair.length() < 2) continue
                val lon = pair.optDouble(0, Double.NaN); val lat = pair.optDouble(1, Double.NaN)
                if (lat.isFinite() && lon.isFinite()) add(LatLngPoint(lat, lon))
            }
        }
    }

    private suspend fun getJson(path: String): JSONObject = getJsonAbsolute("$baseUrl$path")

    private suspend fun getJsonAbsolute(url: String, headers: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 7000; c.readTimeout = 16000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "VANO-Native-Test/0.1 Android")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(JSONObject(text.ifBlank { "{}" }).optString("error", "HTTP $code"))
            JSONObject(text)
        } finally { c.disconnect() }
    }
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key, Double.NaN).takeIf { it.isFinite() }
