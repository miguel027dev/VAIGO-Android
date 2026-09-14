package com.vano.nativeapp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class CameraTarget(val zoom: Double, val pitch: Double, val lookAheadM: Double, val bearingEase: Double)

object NativeCameraEngine {
    fun target(vehicle: VehicleProfile, speedMps: Double, accuracyM: Double, turnAngleDeg: Double): CameraTarget {
        val kmh = max(0.0, speedMps) * 3.6
        val bike = vehicle == VehicleProfile.MOTORCYCLE
        var zoom = when {
            kmh < 5 -> if (bike) 18.2 else 18.0
            kmh < 35 -> if (bike) 17.5 else 17.2
            kmh < 70 -> if (bike) 16.6 else 16.3
            else -> if (bike) 15.7 else 15.5
        }
        var pitch = when {
            kmh < 8 -> 42.0
            kmh < 45 -> if (bike) 57.0 else 53.0
            else -> if (bike) 62.0 else 58.0
        }
        var look = when {
            kmh < 8 -> 25.0
            kmh < 40 -> if (bike) 85.0 else 65.0
            kmh < 80 -> if (bike) 155.0 else 125.0
            else -> if (bike) 220.0 else 185.0
        }
        if (abs(turnAngleDeg) > 55) { zoom += .45; pitch -= 8; look *= .62 }
        if (accuracyM > 35) { zoom -= .55; pitch -= 6; look *= .75 }
        return CameraTarget(zoom, pitch.coerceIn(35.0, 65.0), look, if (bike) .34 else .22)
    }

    fun smoothBearing(current: Double, desired: Double, alpha: Double): Double {
        var d = ((desired - current + 540.0) % 360.0) - 180.0
        if (abs(d) < 1.0) d = 0.0
        return (current + d * alpha + 360.0) % 360.0
    }
}
