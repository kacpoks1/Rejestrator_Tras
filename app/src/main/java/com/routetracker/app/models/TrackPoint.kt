package com.routetracker.app.models

/**
 * Represents a single GPS point in a recorded route.
 * @param latitude Latitude in decimal degrees
 * @param longitude Longitude in decimal degrees
 * @param elevation Elevation in meters (nullable)
 * @param timestamp Unix timestamp in milliseconds when the point was recorded
 * @param speed Speed in meters per second (nullable)
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timestamp: Long,
    val speed: Float? = null
)
