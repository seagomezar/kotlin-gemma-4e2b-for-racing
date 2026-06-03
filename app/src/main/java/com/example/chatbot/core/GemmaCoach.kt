package com.example.chatbot.core

import android.content.Context
import android.os.SystemClock
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.CoachingRecommendation
import com.example.chatbot.models.TelemetryPacket
import com.google.ai.edge.litertlm.Backend
import com.google.gson.Gson

class GemmaCoach(
    context: Context,
    private val gemma: Gemma4Lite,
    private val audioDeliveryManager: AudioDeliveryManager
) {
    private val memoryBank = MemoryBank(context)
    private val sectorTelemetryTracker = SectorTelemetryTracker(memoryBank)
    private val gson = Gson()
    private val lastSpokenAtMsByKey = mutableMapOf<String, Long>()
    private var selectedMemoryFile: String? = null

    suspend fun init(selectedMemoryJsonFile: String) {
        val memoryFileChanged = selectedMemoryFile != selectedMemoryJsonFile
        memoryBank.`init`(selectedMemoryJsonFile)
        gemma.initialize(
            backend = Backend.CPU(),
            enableMtp = true
        )

        if (memoryFileChanged) {
            selectedMemoryFile = selectedMemoryJsonFile
            sectorTelemetryTracker.reset()
            lastSpokenAtMsByKey.clear()
        }
    }

    fun onTelemetry(packet: TelemetryPacket): CoachingPayload? {
        val sectorTelemetry = sectorTelemetryTracker.update(packet) ?: return null
        val notes = memoryBank.getRelevantNotes(sectorTelemetry.latitude, sectorTelemetry.longitude)
        if (notes.isEmpty()) return null

        val evaluatedNotes = notes.map { note ->
            val currentValue = packet.metricValue(note.metric, sectorTelemetry)
            EvaluatedMemoryNote(
                note = note,
                currentValue = currentValue,
                conditionMet = currentValue?.let {
                    compareMetric(it, note.operator, note.threshold)
                } ?: false
            )
        }
        val triggeredNotes = evaluatedNotes
            .filter { it.conditionMet }
            .sortedWith(
                compareBy<EvaluatedMemoryNote> { it.note.priority }
                    .thenByDescending { it.note.frequency }
                    .thenBy { it.note.id }
            )
        if (triggeredNotes.isEmpty()) return null

        val response = try {
            gemma.generateJSON(
                prompt = buildPrompt(packet, sectorTelemetry, evaluatedNotes),
                responseClass = GemmaCoachResponse::class.java,
                systemPrompt = SYSTEM_PROMPT
            )
        } catch (_: Exception) {
            return null
        }

        val command = response.command.trim()
        if (!response.shouldSpeak || command.isBlank()) return null

        val bestNote = triggeredNotes.firstOrNull { it.note.command == command }?.note
            ?: triggeredNotes.first().note
        if (!canSpeak(bestNote, command)) return null

        markSpoken(bestNote, command)
        audioDeliveryManager.speak(command)
        return bestNote.toCommandPayload(command)
    }

    private fun buildPrompt(
        packet: TelemetryPacket,
        sectorTelemetry: SectorTelemetrySnapshot,
        evaluatedNotes: List<EvaluatedMemoryNote>
    ): String {
        val telemetry = mapOf(
            "speed" to packet.speed,
            "rpm" to packet.rpm,
            "gear" to packet.gear,
            "ecu_dbw_app1" to packet.ecuDbwApp1Percent,
            "throttle" to (packet.ecuDbwApp1Percent ?: packet.pedalPositionPercent ?: packet.throttle),
            "brake_pressure_psi" to (packet.brakePressurePsi ?: packet.brake),
            "steering" to packet.steering,
            "lateral_accel_g" to packet.lateralAccelG,
            "inline_accel_g" to packet.inlineAccelG,
            "yaw_rate_dps" to packet.yawRateDps,
            "latitude" to packet.latitude,
            "longitude" to packet.longitude,
            "min_speed" to sectorTelemetry.minSpeed,
            "coasting_time" to sectorTelemetry.coastingTimeSeconds,
            "throttle_start_percent" to sectorTelemetry.throttleStartPercent,
            "distance_traveled" to sectorTelemetry.distanceTraveledMeters
        )

        val noteSummaries = evaluatedNotes.map {
            mapOf(
                "id" to it.note.id,
                "sector_id" to it.note.sector_id,
                "priority" to it.note.priority,
                "title" to it.note.title,
                "metric" to it.note.metric,
                "operator" to it.note.operator,
                "threshold" to it.note.threshold,
                "current_value" to it.currentValue,
                "condition_met" to it.conditionMet,
                "optimal_value" to it.note.optimal_value,
                "average_value" to it.note.average_value,
                "command" to it.note.command,
                "description" to it.note.description
            )
        }

        return """
            Current telemetry:
            ${gson.toJson(telemetry)}

            Current GPS location:
            lat=${packet.latitude}, lon=${packet.longitude}

            Closest sector:
            ${sectorTelemetry.sectorId}

            Sector memory annotations, already sorted by priority. Only condition_met=true notes are eligible:
            ${gson.toJson(noteSummaries)}

            If every condition_met value is false, return shouldSpeak=false.
            If one or more condition_met values are true, choose exactly one concise command from
            the highest-priority eligible note unless a shorter equivalent command is safer.
        """.trimIndent()
    }

    private fun canSpeak(note: CoachingRecommendation, command: String): Boolean {
        val nowMs = SystemClock.elapsedRealtime()
        val lastSpokenAtMs = lastSpokenAtMsByKey[note.cooldownKey(command)] ?: return true
        return nowMs - lastSpokenAtMs >= COOLDOWN_MS
    }

    private fun markSpoken(note: CoachingRecommendation, command: String) {
        lastSpokenAtMsByKey[note.cooldownKey(command)] = SystemClock.elapsedRealtime()
    }

    private fun CoachingRecommendation.cooldownKey(command: String): String {
        return "${sector_id}:${command.ifBlank { id }}"
    }

    private data class GemmaCoachResponse(
        val shouldSpeak: Boolean = false,
        val command: String = "",
        val reason: String = ""
    )

    private data class EvaluatedMemoryNote(
        val note: CoachingRecommendation,
        val currentValue: Double?,
        val conditionMet: Boolean
    )

    private companion object {
        private const val COOLDOWN_MS = 12_000L
        private val SYSTEM_PROMPT = """
            You are an expert racing coach for real-time driver development. Judge the driver's
            telemetry like an experienced coach comparing it against sector-specific memory
            notes from faster reference laps. You must decide whether live telemetry violates
            sector memory conditions. Respond only with JSON:
            {
              "shouldSpeak": true,
              "command": "Short racing command.",
              "reason": "Brief telemetry reason."
            }
            Speak only when telemetry indicates coaching is needed. The command must be concise,
            audio-friendly, and exactly one instruction. Do not use markdown.
        """.trimIndent()
    }
}
