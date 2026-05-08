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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import com.example.chatbot.models.CoachingRulesResponse
import com.example.chatbot.models.GcsObjectsResponse
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MemoryBankManager(private val context: Context) {

    private val gson = Gson()
    private val client = OkHttpClient()
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

    fun fetchAvailableFiles(onComplete: (List<String>) -> Unit) {
        val url = "https://storage.googleapis.com/storage/v1/b/public-race-coaching/o"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MemoryBankManager", "Failed to fetch files list", e)
                onComplete(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("MemoryBankManager", "Unexpected code $response")
                    onComplete(emptyList())
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val gcsResponse = gson.fromJson(responseBody, GcsObjectsResponse::class.java)
                        val files = gcsResponse.items?.map { it.name }?.filter { it.endsWith(".json") } ?: emptyList()
                        Log.d("MemoryBankManager", "Loaded ${files.size} available files")
                        onComplete(files)
                    } else {
                        onComplete(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e("MemoryBankManager", "Failed to parse files list", e)
                    onComplete(emptyList())
                }
            }
        })
    }

    fun fetchRules(filePath: String, onComplete: (Boolean) -> Unit) {
        val url = "https://storage.googleapis.com/public-race-coaching/$filePath"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MemoryBankManager", "Failed to fetch rules", e)
                onComplete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("MemoryBankManager", "Unexpected code $response")
                    onComplete(false)
                    return
                }

                try {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val rulesResponse = gson.fromJson(responseBody, CoachingRulesResponse::class.java)
                        rules = rulesResponse.coachingRules.groupBy { it.sector_id }
                        Log.d("MemoryBankManager", "Loaded ${rulesResponse.coachingRules.size} rules from GCS")
                        onComplete(true)
                    } else {
                        onComplete(false)
                    }
                } catch (e: Exception) {
                    Log.e("MemoryBankManager", "Failed to parse JSON", e)
                    onComplete(false)
                }
            }
        })
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
            val totalDistance = calculateSectorDistance(newSegmentId)
            
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
                    if (rule.operator == "<" && currentMetrics.minSpeed < rule.threshold) triggered = true
                    else if (rule.operator == ">" && currentMetrics.minSpeed > rule.threshold) triggered = true
                }
                "coasting_time" -> {
                    if (rule.operator == "<" && currentMetrics.coastingTime < rule.threshold) triggered = true
                    else if (rule.operator == ">" && currentMetrics.coastingTime > rule.threshold) triggered = true
                }
                "throttle_start_percent" -> {
                    val value = currentMetrics.throttleStartPercent ?: 1.0
                    if (rule.operator == "<" && value < rule.threshold) triggered = true
                    else if (rule.operator == ">" && value > rule.threshold) triggered = true
                }
            }

            if (triggered) {
                val payload = CoachingPayload(
                    instruction = rule.description,
                    urgency = "HIGH", // JSON rules don't specify type, assume HIGH for all Memory Bank rules to trigger
                    targetCorner = "Sector $segmentId - ${rule.title}",
                    latencyMs = 0,
                    audioFile = rule.audio_file
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

    private fun calculateSectorDistance(segmentId: Int): Double {
        val points = trackSectors.filter { it.segment_id == segmentId }
        if (points.size < 2) return 0.0
        var dist = 0.0
        for (i in 0 until points.size - 1) {
            dist += haversineDistance(points[i].lat, points[i].long, points[i+1].lat, points[i+1].long)
        }
        return dist
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
