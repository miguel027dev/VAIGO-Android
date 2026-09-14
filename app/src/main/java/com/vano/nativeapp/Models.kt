package com.vano.nativeapp

data class LatLngPoint(val lat: Double, val lon: Double)
data class SearchPlace(
    val name: String,
    val label: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val distanceM: Int? = null,
)
data class RouteOption(
    val id: String,
    val coordinates: List<LatLngPoint>,
    val durationSec: Double,
    val distanceM: Double,
    val safetyScore: Double?,
    val trafficScore: Double?,
    val label: String,
)
enum class VehicleProfile { CAR, MOTORCYCLE }
enum class RouteMode { SMART, FASTEST, SAFEST }
