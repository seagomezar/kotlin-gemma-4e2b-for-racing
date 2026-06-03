package com.example.chatbot.core

import android.content.Context
import android.os.SystemClock
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.CoachingRecommendation
import com.example.chatbot.models.TelemetryPacket

class HeuristicBaseAICoach(
    context: Context,
    private val audioDeliveryManager: AudioDeliveryManager
) {
    private val memoryBank = MemoryBank(context)
    private val sectorTelemetryTracker = SectorTelemetryTracker(memoryBank)
    private val lastSpokenAtMsByKey = mutableMapOf<String, Long>()
    private var selectedMemoryFile: String? = null

    fun init(selectedMemoryJsonFile: String) {
        if (selectedMemoryFile == selectedMemoryJsonFile) return
        memoryBank.`init`(selectedMemoryJsonFile)
        selectedMemoryFile = selectedMemoryJsonFile
        lastSpokenAtMsByKey.clear()
        sectorTelemetryTracker.reset()
    }

    fun onTelemetry(packet: TelemetryPacket): CoachingPayload? {
        val sectorTelemetry = sectorTelemetryTracker.update(packet) ?: return null

        val triggeredNote = memoryBank
            .getRelevantNotes(sectorTelemetry.latitude, sectorTelemetry.longitude)
            .filter { note ->
                note.isTriggeredBy(packet, sectorTelemetry) && canSpeak(note)
            }
            .minWithOrNull(
                compareBy<CoachingRecommendation> { it.priority }
                    .thenByDescending { it.frequency }
                    .thenBy { it.id }
            ) ?: return null

        val command = triggeredNote.command.trim().ifBlank { triggeredNote.description.trim() }
        if (command.isBlank()) return null

        markSpoken(triggeredNote)
        audioDeliveryManager.speak(command)
        return triggeredNote.toCommandPayload(command)
    }

    private fun CoachingRecommendation.isTriggeredBy(
        packet: TelemetryPacket,
        sectorTelemetry: SectorTelemetrySnapshot
    ): Boolean {
        val currentValue = packet.metricValue(metric, sectorTelemetry) ?: return false
        return compareMetric(currentValue, operator, threshold)
    }

    private fun canSpeak(note: CoachingRecommendation): Boolean {
        val nowMs = SystemClock.elapsedRealtime()
        val lastSpokenAtMs = lastSpokenAtMsByKey[note.cooldownKey()] ?: return true
        return nowMs - lastSpokenAtMs >= COOLDOWN_MS
    }

    private fun markSpoken(note: CoachingRecommendation) {
        lastSpokenAtMsByKey[note.cooldownKey()] = SystemClock.elapsedRealtime()
    }

    private fun CoachingRecommendation.cooldownKey(): String {
        return "${sector_id}:${command.ifBlank { id }}"
    }

    private companion object {
        private const val COOLDOWN_MS = 12_000L
    }
}
