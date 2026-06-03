package com.example.chatbot.core

import android.os.SystemClock
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.CoachingRecommendation
import com.example.chatbot.models.TelemetryPacket
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal fun TelemetryPacket.metricValue(metric: String): Double? {
    return when (metric.trim().lowercase()) {
        "speed", "speed_mph" -> speed
        "rpm" -> rpm
        "throttle", "pedal", "pedal_position", "pedal_position_percent",
        "ecu_dbw_app1", "ecu_dbw_app1_percent" -> {
            ecuDbwApp1Percent ?: pedalPositionPercent ?: throttle
        }
        "brake", "brake_pressure", "brake_pressure_psi" -> brakePressurePsi ?: brake
        "steering" -> steering
        "gear" -> gear?.toDouble()
        "lateral_accel", "lateral_accel_g" -> lateralAccelG
        "inline_accel", "inline_accel_g", "longitudinal_accel_g" -> inlineAccelG
        "yaw_rate", "yaw_rate_dps" -> yawRateDps
        "oil_pressure", "oil_pressure_psi" -> oilPressurePsi
        "fuel_pressure", "fuel_pressure_psi" -> fuelPressurePsi
        "water_temp", "water_temp_f" -> waterTempF
        "engine_oil_temp", "engine_oil_temp_f" -> engineOilTempF
        "battery_voltage" -> batteryVoltage
        "fuel_level", "fuel_level_gallons" -> fuelLevelGallons
        else -> null
    }
}

internal fun TelemetryPacket.metricValue(
    metric: String,
    sectorTelemetry: SectorTelemetrySnapshot?
): Double? {
    return metricValue(metric) ?: sectorTelemetry?.metricValue(metric)
}

internal fun compareMetric(currentValue: Double, operator: String, threshold: Double): Boolean {
    return when (operator.trim()) {
        ">" -> currentValue > threshold
        ">=" -> currentValue >= threshold
        "<" -> currentValue < threshold
        "<=" -> currentValue <= threshold
        "==" -> kotlin.math.abs(currentValue - threshold) < 0.0001
        "!=" -> kotlin.math.abs(currentValue - threshold) >= 0.0001
        else -> false
    }
}

internal fun CoachingRecommendation.toCommandPayload(commandOverride: String? = null): CoachingPayload {
    val command = commandOverride?.takeIf { it.isNotBlank() } ?: command
    return CoachingPayload(
        instruction = command.ifBlank { description },
        urgency = if (priority <= 1) "HIGH" else "NORMAL",
        targetCorner = "Sector $sector_id - $title",
        latencyMs = 0,
        audioFile = null
    )
}

internal class SectorTelemetryTracker(
    private val memoryBank: MemoryBank
) {
    private var state = SectorTelemetryState()

    fun reset() {
        state = SectorTelemetryState()
    }

    fun update(packet: TelemetryPacket): SectorTelemetrySnapshot? {
        val latitude = packet.latitude ?: return null
        val longitude = packet.longitude ?: return null
        val sectorId = memoryBank.getClosestSectorId(latitude, longitude) ?: return null
        val timestampSeconds = packet.timestamp.takeIf { it > 0.0 }
            ?: (SystemClock.elapsedRealtime() / 1000.0)

        if (state.sectorId != sectorId) {
            state = SectorTelemetryState(
                sectorId = sectorId,
                minSpeed = packet.speed ?: Double.MAX_VALUE,
                totalDistanceMeters = memoryBank.getSectorDistanceMeters(sectorId),
                startTimestampSeconds = timestampSeconds,
                lastTimestampSeconds = timestampSeconds,
                lastLatitude = latitude,
                lastLongitude = longitude
            )
            return state.toSnapshot(latitude, longitude)
        }

        val deltaSeconds = (timestampSeconds - state.lastTimestampSeconds)
            .coerceAtLeast(0.0)
            .coerceAtMost(MAX_TELEMETRY_GAP_SECONDS)
        val nextMinSpeed = packet.speed?.let { minOf(state.minSpeed, it) } ?: state.minSpeed
        val distanceDelta = haversineDistanceMeters(
            state.lastLatitude,
            state.lastLongitude,
            latitude,
            longitude
        )

        val throttle = packet.pedalPositionPercent ?: packet.throttle ?: 0.0
        val brake = packet.brakePressurePsi ?: packet.brake ?: 0.0
        val isCoasting = throttle <= COASTING_THROTTLE_PERCENT && brake <= COASTING_BRAKE_VALUE
        val throttleStartPercent = state.throttleStartPercent
            ?: if (throttle > COASTING_THROTTLE_PERCENT && state.totalDistanceMeters > 0.0) {
                (state.distanceTraveledMeters / state.totalDistanceMeters) * 100.0
            } else {
                null
            }

        state = state.copy(
            minSpeed = nextMinSpeed,
            coastingTimeSeconds = state.coastingTimeSeconds +
                if (isCoasting) deltaSeconds else 0.0,
            throttleStartPercent = throttleStartPercent,
            distanceTraveledMeters = state.distanceTraveledMeters + distanceDelta,
            lastTimestampSeconds = timestampSeconds,
            lastLatitude = latitude,
            lastLongitude = longitude
        )

        return state.toSnapshot(latitude, longitude)
    }

    private fun SectorTelemetryState.toSnapshot(
        latitude: Double,
        longitude: Double
    ): SectorTelemetrySnapshot {
        return SectorTelemetrySnapshot(
            sectorId = checkNotNull(sectorId),
            latitude = latitude,
            longitude = longitude,
            minSpeed = minSpeed.takeIf { it != Double.MAX_VALUE },
            coastingTimeSeconds = coastingTimeSeconds,
            throttleStartPercent = throttleStartPercent,
            distanceTraveledMeters = distanceTraveledMeters,
            totalDistanceMeters = totalDistanceMeters
        )
    }

    private data class SectorTelemetryState(
        val sectorId: Int? = null,
        val minSpeed: Double = Double.MAX_VALUE,
        val coastingTimeSeconds: Double = 0.0,
        val throttleStartPercent: Double? = null,
        val distanceTraveledMeters: Double = 0.0,
        val totalDistanceMeters: Double = 0.0,
        val startTimestampSeconds: Double = 0.0,
        val lastTimestampSeconds: Double = 0.0,
        val lastLatitude: Double = 0.0,
        val lastLongitude: Double = 0.0
    )

    private companion object {
        private const val COASTING_THROTTLE_PERCENT = 1.0
        private const val COASTING_BRAKE_VALUE = 0.0
        private const val MAX_TELEMETRY_GAP_SECONDS = 2.0
    }
}

internal data class SectorTelemetrySnapshot(
    val sectorId: Int,
    val latitude: Double,
    val longitude: Double,
    val minSpeed: Double?,
    val coastingTimeSeconds: Double,
    val throttleStartPercent: Double?,
    val distanceTraveledMeters: Double,
    val totalDistanceMeters: Double
) {
    fun metricValue(metric: String): Double? {
        return when (metric.trim().lowercase()) {
            "min_speed", "minimum_speed" -> minSpeed
            "coasting_time", "coast_time" -> coastingTimeSeconds
            "throttle_start_percent" -> throttleStartPercent
            "distance_traveled" -> distanceTraveledMeters
            "sector_distance" -> totalDistanceMeters
            else -> null
        }
    }
}

private fun haversineDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val radiusMeters = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return radiusMeters * c
}
