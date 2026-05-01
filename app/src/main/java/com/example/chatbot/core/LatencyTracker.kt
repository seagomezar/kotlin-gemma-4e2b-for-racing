package com.example.chatbot.core

import android.util.Log

object LatencyTracker {
    private var telemetryIngestionTime: Long = 0

    fun markTelemetryIngestion() {
        telemetryIngestionTime = System.currentTimeMillis()
    }

    fun markAudioPlaybackStarted(): Long {
        if (telemetryIngestionTime == 0L) return 0L
        
        val playbackTime = System.currentTimeMillis()
        val latency = playbackTime - telemetryIngestionTime
        
        Log.i("LatencyTracker", "End-to-End Latency: ${latency}ms")
        
        // Reset for next cycle
        telemetryIngestionTime = 0L
        return latency
    }
}
