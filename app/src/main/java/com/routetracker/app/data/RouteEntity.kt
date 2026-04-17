package com.routetracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a recorded route in the database.
 * Maps to the 'routes' table.
 */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val date: Long,
    val distanceKm: Double,
    val durationSeconds: Long,
    val gpxFilePath: String,
    val averageSpeed: Double = 0.0,
    val maxSpeed: Double = 0.0
)
