package com.example.chatbot.core

import com.example.chatbot.models.TelemetryPacket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GatedInferenceEngine {
    
    private val steeringWindow = mutableListOf<Double>()
    private val WINDOW_SIZE = 10 // Rolling window size to evaluate variance
    
    // We emit an event when inference should be triggered
    private val _inferenceTriggerFlow = MutableSharedFlow<TelemetryPacket>(replay = 1)
    val inferenceTriggerFlow: SharedFlow<TelemetryPacket> = _inferenceTriggerFlow.asSharedFlow()

    private var isCoolingDown = false
    private var lastTriggerTime = 0L
    private val COOLDOWN_MS = 5000L // 5 seconds cooldown between triggers to save thermal load

    fun processTelemetry(packet: TelemetryPacket) {
        val steering = packet.steering ?: return
        
        steeringWindow.add(steering)
        if (steeringWindow.size > WINDOW_SIZE) {
            steeringWindow.removeAt(0)
        }

        if (steeringWindow.size == WINDOW_SIZE) {
            evaluateStraightaway(packet)
        }
    }

    private fun evaluateStraightaway(packet: TelemetryPacket) {
        if (isCoolingDown && System.currentTimeMillis() - lastTriggerTime < COOLDOWN_MS) {
            return
        }
        
        isCoolingDown = false

        // Calculate variance or absolute sum of steering inputs
        val avgSteering = steeringWindow.average()
        val variance = steeringWindow.map { Math.pow(it - avgSteering, 2.0) }.average()

        // If variance is very low, we assume we are on a straightaway
        val threshold = 0.5 // Adjust based on actual simulator telemetry ranges
        
        if (variance < threshold) {
            // Straightaway detected! Safe for LLM inference (Thermal load optimized)
            triggerInference(packet)
        }
    }

    private fun triggerInference(packet: TelemetryPacket) {
        lastTriggerTime = System.currentTimeMillis()
        isCoolingDown = true
        _inferenceTriggerFlow.tryEmit(packet)
    }
}
