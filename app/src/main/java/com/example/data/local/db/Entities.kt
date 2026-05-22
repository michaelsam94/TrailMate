package com.example.data.local.db

import androidx.room.*
import com.example.domain.model.Waypoint
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val distanceKm: Double,
    val estimatedDurationMinutes: Int,
    val safetyScore: Float,
    val surfaceTypeName: String,
    val elevationGainM: Double,
    val createdAt: Long,
    val waypoints: List<Waypoint>
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val routeId: String,
    val startTime: Long,
    val endTime: Long,
    val currentPaceMinPerKm: Double,
    val distanceCoveredKm: Double,
    val elevationGainM: Double,
    val caloriesBurned: Int,
    val isPaused: Boolean,
    val liveWaypoints: List<Waypoint>
)

@Entity(tableName = "osm_nodes")
data class NodeEntity(
    @PrimaryKey val id: Long,
    val lat: Double,
    val lng: Double,
    val elevationM: Double = 0.0,
    val insertedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "osm_ways")
data class WayEntity(
    @PrimaryKey val id: Long,
    val surfaceType: String,
    val nodeIds: String, // Comma separated list of node ids
    val insertedAt: Long = System.currentTimeMillis()
)

class Converters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val type = Types.newParameterizedType(List::class.java, Waypoint::class.java)
    private val adapter = moshi.adapter<List<Waypoint>>(type)

    @TypeConverter
    fun fromWaypointList(value: List<Waypoint>?): String? {
        return value?.let { adapter.toJson(it) }
    }

    @TypeConverter
    fun toWaypointList(value: String?): List<Waypoint>? {
        return value?.let { adapter.fromJson(it) }
    }
}
