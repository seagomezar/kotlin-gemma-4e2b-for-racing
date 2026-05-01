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
