package com.example.chatbot.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.chatbot.models.CoachingPayload
import com.example.chatbot.models.CoachingRecommendation
import com.example.chatbot.models.SectorMetrics
import com.example.chatbot.models.TelemetryPacket
import com.example.chatbot.models.TrackSectorPoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MemoryBankManager(private val context: Context) {

    private val gson = Gson()
    private var trackSectors: List<TrackSectorPoint> = emptyList()
    private var rules: Map<Int, List<CoachingRecommendation>> = emptyMap()

    private val _coachingFlow = MutableSharedFlow<CoachingPayload>(replay = 1)
    val coachingFlow: SharedFlow<CoachingPayload> = _coachingFlow.asSharedFlow()

    private var currentSegmentId: Int? = null
    private var currentMetrics: SectorMetrics = SectorMetrics()

    init {
        loadTrackSectors()
    }

    private fun loadTrackSectors() {
        try {
            val jsonString = context.assets.open("track_sectors.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<TrackSectorPoint>>() {}.type
            trackSectors = gson.fromJson(jsonString, listType)
            Log.d("MemoryBankManager", "Loaded ${trackSectors.size} track sectors")
        } catch (e: Exception) {
            Log.e("MemoryBankManager", "Failed to load track sectors", e)
        }
    }

    fun loadCsvRules(uri: Uri) {
        try {
            val recommendations = mutableListOf<CoachingRecommendation>()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var isFirstLine = true
                    reader.forEachLine { line ->
                        if (isFirstLine) {
                            isFirstLine = false
                            return@forEachLine
                        }
                        val tokens = line.split(",")
                        if (tokens.size >= 12) {
                            val rec = CoachingRecommendation(
                                type = tokens[0].trim(),
                                sector_id = tokens[1].trim().toIntOrNull() ?: 0,
                                start_lat = tokens[2].trim().toDoubleOrNull() ?: 0.0,
                                start_long = tokens[3].trim().toDoubleOrNull() ?: 0.0,
                                end_lat = tokens[4].trim().toDoubleOrNull() ?: 0.0,
                                end_long = tokens[5].trim().toDoubleOrNull() ?: 0.0,
                                tag = tokens[6].trim(),
                                title = tokens[7].trim(),
                                description = tokens[8].trim(),
                                metric = tokens[9].trim(),
                                threshold = tokens[10].trim().toDoubleOrNull() ?: 0.0,
                                optimal_value = tokens[11].trim().toDoubleOrNull() ?: 0.0
                            )
                            recommendations.add(rec)
                        }
                    }
                }
            }
            rules = recommendations.groupBy { it.sector_id }
            Log.d("MemoryBankManager", "Loaded ${recommendations.size} rules from CSV")
        } catch (e: Exception) {
            Log.e("MemoryBankManager", "Failed to load CSV rules", e)
        }
    }

    fun processTelemetry(packet: TelemetryPacket) {
        val lat = packet.latitude ?: return
        val lon = packet.longitude ?: return
        
        val nearestSector = findNearestSector(lat, lon) ?: return
        val newSegmentId = nearestSector.segment_id

        if (currentSegmentId != newSegmentId) {
            if (currentSegmentId != null) {
                evaluateSector(currentSegmentId!!)
            }
            // Start new sector
            currentSegmentId = newSegmentId
            val sectorRules = rules[newSegmentId]
            val totalDistance = sectorRules?.firstOrNull()?.let {
                haversineDistance(it.start_lat, it.start_long, it.end_lat, it.end_long)
            }
            
            currentMetrics = SectorMetrics(
                minSpeed = packet.speed ?: Double.MAX_VALUE,
                coastingTime = 0.0,
                throttleStartPercent = null,
                startTime = packet.timestamp,
                lastTimestamp = packet.timestamp,
                distanceTraveled = 0.0,
                lastLat = lat,
                lastLong = lon,
                totalSectorDistance = totalDistance
            )
        } else {
            // Update metrics for current sector
            updateMetrics(packet, lat, lon)
        }
    }

    private fun updateMetrics(packet: TelemetryPacket, lat: Double, lon: Double) {
        val dt = packet.timestamp - currentMetrics.lastTimestamp
        
        // Update distance
        if (currentMetrics.lastLat != null && currentMetrics.lastLong != null) {
            currentMetrics.distanceTraveled += haversineDistance(
                currentMetrics.lastLat!!, currentMetrics.lastLong!!, lat, lon
            )
        }
        
        // Update Min Speed
        val speed = packet.speed ?: Double.MAX_VALUE
        if (speed < currentMetrics.minSpeed) {
            currentMetrics.minSpeed = speed
        }

        // Update Coasting Time
        val throttle = packet.throttle ?: 0.0
        val brake = packet.brake ?: 0.0
        if (throttle == 0.0 && brake == 0.0 && dt > 0) {
            currentMetrics.coastingTime += dt
        }

        // Update Throttle Start Percent
        if (currentMetrics.throttleStartPercent == null && throttle > 0.0) {
            val totalDist = currentMetrics.totalSectorDistance
            if (totalDist != null && totalDist > 0.0) {
                currentMetrics.throttleStartPercent = currentMetrics.distanceTraveled / totalDist
            } else {
                currentMetrics.throttleStartPercent = 0.0
            }
        }

        currentMetrics.lastTimestamp = packet.timestamp
        currentMetrics.lastLat = lat
        currentMetrics.lastLong = lon
    }

    private fun evaluateSector(segmentId: Int) {
        val sectorRules = rules[segmentId] ?: return

        for (rule in sectorRules) {
            var triggered = false
            when (rule.metric) {
                "min_speed" -> {
                    if (currentMetrics.minSpeed < rule.threshold) triggered = true
                }
                "coasting_time" -> {
                    if (currentMetrics.coastingTime > rule.threshold) triggered = true
                }
                "throttle_start_percent" -> {
                    if ((currentMetrics.throttleStartPercent ?: 1.0) > rule.threshold) triggered = true
                }
            }

            if (triggered) {
                val payload = CoachingPayload(
                    instruction = rule.description,
                    urgency = if (rule.type == "Physics") "HIGH" else "NORMAL",
                    targetCorner = "Sector $segmentId - ${rule.title}",
                    latencyMs = 0
                )
                _coachingFlow.tryEmit(payload)
                break // Only emit one advice per sector
            }
        }
    }

    fun getCurrentSegmentId(): Int? = currentSegmentId

    // Helper functions
    private fun findNearestSector(lat: Double, lon: Double): TrackSectorPoint? {
        if (trackSectors.isEmpty()) return null
        
        var minDistance = Double.MAX_VALUE
        var nearest: TrackSectorPoint? = null
        
        for (point in trackSectors) {
            // Using fast squared euclidean distance on coordinates since sectors are close
            // This avoids doing haversine thousands of times per packet
            val dLat = lat - point.lat
            val dLon = lon - point.long
            val distSq = dLat * dLat + dLon * dLon
            if (distSq < minDistance) {
                minDistance = distSq
                nearest = point
            }
        }
        return nearest
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
