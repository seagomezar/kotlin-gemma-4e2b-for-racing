package com.example.chatbot.core

import android.content.Context
import android.util.Log
import com.example.chatbot.models.CoachingRecommendation
import com.example.chatbot.models.CoachingRulesResponse
import com.example.chatbot.models.TrackSectorPoint
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MemoryBank(private val context: Context) {
    private val gson = Gson()
    private var selectedMemoryFilePath: String? = null
    private var notesBySector: Map<Int, List<CoachingRecommendation>> = emptyMap()
    private var trackSectors: List<TrackSectorPoint> = emptyList()
    private var cachedSectorLatitude: Double? = null
    private var cachedSectorLongitude: Double? = null
    private var cachedSectorMatch: SectorMatch? = null

    fun `init`(selectedMemoryJsonFile: String) {
        loadTrackSectorsIfNeeded()

        if (selectedMemoryFilePath == selectedMemoryJsonFile && notesBySector.isNotEmpty()) {
            return
        }

        val memoryFile = File(selectedMemoryJsonFile)
        require(memoryFile.exists() && memoryFile.isFile) {
            "Memory bank file does not exist: $selectedMemoryJsonFile"
        }

        val notes = parseMemoryNotes(memoryFile.readText())
        notesBySector = notes
            .groupBy { it.sector_id }
            .mapValues { (_, sectorNotes) -> sectorNotes.sortedBy { it.priority } }
        selectedMemoryFilePath = selectedMemoryJsonFile

        Log.d(TAG, "Loaded ${notes.size} memory notes from ${memoryFile.name}")
    }

    fun getRelevantNotes(latitude: Double, longitude: Double): List<CoachingRecommendation> {
        if (!isValidGpsCoordinate(latitude, longitude)) return emptyList()
        loadTrackSectorsIfNeeded()
        return getClosestSectorMatch(latitude, longitude)
            ?.takeIf { it.distanceMeters <= MAX_TRACK_MATCH_DISTANCE_METERS }
            ?.let { notesBySector[it.sectorId].orEmpty() }
            .orEmpty()
    }

    fun getRelevantNotes(latitude: Double?, longitude: Double?): List<CoachingRecommendation> {
        if (latitude == null || longitude == null) return emptyList()
        return getRelevantNotes(latitude, longitude)
    }

    fun getCurrentMemoryFilePath(): String? = selectedMemoryFilePath

    fun getClosestSectorId(latitude: Double, longitude: Double): Int? {
        if (!isValidGpsCoordinate(latitude, longitude)) return null
        loadTrackSectorsIfNeeded()
        return getClosestSectorMatch(latitude, longitude)?.sectorId
    }

    fun getSectorDistanceMeters(sectorId: Int): Double {
        loadTrackSectorsIfNeeded()
        val sectorPoints = trackSectors.filter { it.segment_id == sectorId }
        if (sectorPoints.size < 2) return 0.0

        return sectorPoints
            .zipWithNext()
            .sumOf { (start, end) ->
                haversineDistanceMeters(start.lat, start.long, end.lat, end.long)
            }
    }

    fun getClosestSectorDistanceMeters(latitude: Double, longitude: Double): Double? {
        if (!isValidGpsCoordinate(latitude, longitude)) return null
        loadTrackSectorsIfNeeded()
        return getClosestSectorMatch(latitude, longitude)?.distanceMeters
    }

    private fun loadTrackSectorsIfNeeded() {
        if (trackSectors.isNotEmpty()) return

        val json = context.assets.open(TRACK_SECTORS_ASSET).bufferedReader().use { it.readText() }
        val listType = object : TypeToken<List<TrackSectorPoint>>() {}.type
        trackSectors = gson.fromJson(json, listType)

        Log.d(TAG, "Loaded ${trackSectors.size} track sector boundary points")
    }

    private fun parseMemoryNotes(json: String): List<CoachingRecommendation> {
        val root = JsonParser.parseString(json)
        val listType = object : TypeToken<List<CoachingRecommendation>>() {}.type

        return when {
            root is JsonArray -> gson.fromJson(root, listType)
            root is JsonObject && root.has("coachingRules") -> {
                gson.fromJson(root, CoachingRulesResponse::class.java).coachingRules
            }
            root is JsonObject && root.has("notes") -> gson.fromJson(root.get("notes"), listType)
            root is JsonObject && root.has("rules") -> gson.fromJson(root.get("rules"), listType)
            else -> emptyList()
        }
    }

    private fun getClosestSectorMatch(latitude: Double, longitude: Double): SectorMatch? {
        if (cachedSectorLatitude == latitude && cachedSectorLongitude == longitude) {
            return cachedSectorMatch
        }

        val match = trackSectors
            .asSequence()
            .map { point ->
                SectorMatch(
                    sectorId = point.segment_id,
                    distanceMeters = haversineDistanceMeters(latitude, longitude, point.lat, point.long)
                )
            }
            .minByOrNull { it.distanceMeters }

        cachedSectorLatitude = latitude
        cachedSectorLongitude = longitude
        cachedSectorMatch = match
        return match
    }

    private fun isValidGpsCoordinate(latitude: Double, longitude: Double): Boolean {
        if (latitude == 0.0 && longitude == 0.0) return false
        if (kotlin.math.abs(latitude) == 90.0 && longitude == 0.0) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
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

    private companion object {
        private const val TAG = "MemoryBank"
        private const val TRACK_SECTORS_ASSET = "track_sectors.json"
        private const val MAX_TRACK_MATCH_DISTANCE_METERS = 80.0
    }

    private data class SectorMatch(
        val sectorId: Int,
        val distanceMeters: Double
    )
}
