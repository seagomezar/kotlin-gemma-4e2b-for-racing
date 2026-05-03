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
    val throttle: Double? = null,
    val brake: Double? = null,
    val steering: Double? = null,
    val gear: Int? = null,
    val lap: Int? = null,
    // Pro Sensor Channels required by the review
    val shockPots: List<Double>? = null,
    val tireSlipVectors: List<Double>? = null,
    val wheelSpeedDeltas: List<Double>? = null
)

data class CoachingPayload(
    val instruction: String,
    val urgency: String = "NORMAL",
    val targetCorner: String = "Unknown",
    val latencyMs: Long = 0
)

data class TrackSectorPoint(
    val lat: Double,
    val long: Double,
    val segment_id: Int
)

data class CoachingRecommendation(
    val type: String,
    val sector_id: Int,
    val start_lat: Double,
    val start_long: Double,
    val end_lat: Double,
    val end_long: Double,
    val tag: String,
    val title: String,
    val description: String,
    val metric: String,
    val threshold: Double,
    val optimal_value: Double
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
