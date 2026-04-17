package com.routetracker.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single track point in the database.
 * Maps to the 'track_points' table with a foreign key to routes.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = RouteEntity::class,
        parentColumns = ["id"],
        childColumns = ["routeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["routeId"])]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timestamp: Long,
    val speed: Float? = null
)
