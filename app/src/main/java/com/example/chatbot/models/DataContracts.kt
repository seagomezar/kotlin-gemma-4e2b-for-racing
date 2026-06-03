package com.example.chatbot.models

data class TelemetryPacket(
    val sequence: Long = 0,
    val timestamp: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speed: Double? = null,
    val heading: Double? = null,
    val altitude: Double? = null,
    val satellites: Int? = null,
    val rpm: Double? = null,
    val waterTempF: Double? = null,
    val throttle: Double? = null,
    val brake: Double? = null,
    val brakePressurePsi: Double? = null,
    val brakeSwitchApplied: Boolean? = null,
    val steering: Double? = null,
    val gear: Int? = null,
    val lap: Int? = null,
    val ecuDbwApp1Percent: Double? = null,
    val pedalPositionPercent: Double? = null,
    val inlineAccelG: Double? = null,
    val lateralAccelG: Double? = null,
    val verticalAccelG: Double? = null,
    val rollRateDps: Double? = null,
    val pitchRateDps: Double? = null,
    val yawRateDps: Double? = null,
    val engineOilTempF: Double? = null,
    val analogOilTempF: Double? = null,
    val oilFilterTempF: Double? = null,
    val oilPressurePsi: Double? = null,
    val fuelPressurePsi: Double? = null,
    val fuelLevelGallons: Double? = null,
    val ecuMilOut: Int? = null,
    val batteryVoltage: Double? = null,
    val wheelSpeedFlMph: Double? = null,
    val wheelSpeedFrMph: Double? = null,
    val wheelSpeedRlMph: Double? = null,
    val wheelSpeedRrMph: Double? = null,
    // Pro Sensor Channels required by the review
    val shockPots: List<Double>? = null,
    val tireSlipVectors: List<Double>? = null,
    val wheelSpeedDeltas: List<Double>? = null
)

data class CoachingPayload(
    val instruction: String,
    val urgency: String = "NORMAL",
    val targetCorner: String = "Unknown",
    val latencyMs: Long = 0,
    val audioFile: String? = null
)

data class TrackSectorPoint(
    val lat: Double,
    val long: Double,
    val segment_id: Int
)

data class CoachingRulesResponse(
    val coachingRules: List<CoachingRecommendation>
)

data class CoachingRecommendation(
    val id: String,
    val sector_id: Int,
    val tag: String,
    val title: String,
    val description: String,
    val metric: String,
    val operator: String,
    val threshold: Double,
    val optimal_value: Double,
    val average_value: Double,
    val frequency: Double,
    val audio_file: String,
    val priority: Int = 3,
    val command: String = ""
)

data class SectorMetrics(
    var minSpeed: Double = Double.MAX_VALUE,
    var coastingTime: Double = 0.0,
    var throttleStartPercent: Double? = null,
    var startTime: Double = 0.0,
    var lastTimestamp: Double = 0.0,
    var distanceTraveled: Double = 0.0,
    var lastLat: Double? = null,
    var lastLong: Double? = null,
    var totalSectorDistance: Double? = null // Pre-calculated from CSV start to end
)

data class GcsObjectsResponse(
    val items: List<GcsObjectItem>?
)

data class GcsObjectItem(
    val name: String,
    val updated: String? = null
)
