package com.routetracker.app.data

import com.routetracker.app.models.Route
import com.routetracker.app.models.TrackPoint
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository that wraps Room DAO operations and GPX file management.
 * Provides a clean API for the ViewModel to interact with data layer.
 */
class RouteRepository(
    private val routeDao: RouteDao,
    private val gpxManager: GpxManager
) {

    /**
     * Get all routes from the database.
     */
    suspend fun getAllRoutes(): List<Route> {
        return routeDao.getAllRoutes().map { it.toRoute() }
    }

    /**
     * Get a specific route by ID.
     */
    suspend fun getRouteById(routeId: Long): Route? {
        return routeDao.getRouteById(routeId)?.toRoute()
    }

    /**
     * Get track points for a specific route.
     */
    suspend fun getTrackPointsForRoute(routeId: Long): List<TrackPoint> {
        return routeDao.getTrackPointsForRoute(routeId).map { it.toTrackPoint() }
    }

    /**
     * Import a GPX file from a URI.
     */
    suspend fun importGpx(uri: Uri): Long? {
        val result = gpxManager.importGpx(uri) ?: return null
        val (name, trackPoints) = result
        
        if (trackPoints.isEmpty()) return null

        // Calculate stats
        val distanceKm = gpxManager.calculateTotalDistance(trackPoints)
        val durationSeconds = gpxManager.calculateDuration(trackPoints)
        val avgSpeed = gpxManager.calculateAverageSpeed(trackPoints)
        val maxSpeed = gpxManager.calculateMaxSpeed(trackPoints)

        return saveRoute(
            name = name,
            date = trackPoints.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
            trackPoints = trackPoints,
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            averageSpeed = avgSpeed,
            maxSpeed = maxSpeed
        )
    }

    /**
     * Save a complete route with its track points to the database and GPX file.
     * @return The ID of the newly inserted route
     */
    suspend fun saveRoute(
        name: String,
        date: Long,
        trackPoints: List<TrackPoint>,
        distanceKm: Double,
        durationSeconds: Long,
        averageSpeed: Double,
        maxSpeed: Double
    ): Long {
        // Check if a route with this name already exists
        val existingRoute = routeDao.getRouteByName(name)

        return if (existingRoute != null) {
            val routeId = existingRoute.id
            val existingPoints = getTrackPointsForRoute(routeId)
            val allPoints = existingPoints + trackPoints

            // Recalculate stats for the entire merged route
            val totalDistance = gpxManager.calculateTotalDistance(allPoints)
            val totalDuration = gpxManager.calculateDuration(allPoints)
            val totalAvgSpeed = gpxManager.calculateAverageSpeed(allPoints)
            val totalMaxSpeed = maxOf(existingRoute.maxSpeed, maxSpeed)

            // Update GPX file (overwrite with all points)
            val gpxFile = File(existingRoute.gpxFilePath)
            withContext(Dispatchers.IO) {
                val gpxContent = gpxManager.generateGpx(allPoints, name)
                gpxFile.writeText(gpxContent)
            }

            // Update existing route entity
            val updatedRoute = existingRoute.copy(
                distanceKm = totalDistance,
                durationSeconds = totalDuration,
                averageSpeed = totalAvgSpeed,
                maxSpeed = totalMaxSpeed
            )
            routeDao.updateRoute(updatedRoute)

            // Insert ONLY the new track points
            val trackPointEntities = trackPoints.map { point ->
                TrackPointEntity(
                    routeId = routeId,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    elevation = point.elevation,
                    timestamp = point.timestamp,
                    speed = point.speed
                )
            }
            routeDao.insertTrackPoints(trackPointEntities)

            routeId
        } else {
            // Create GPX file and get its path (use name as filename if possible)
            // Sanitize name for filename
            val sanitizedName = name.replace(Regex("[^a-zA-Z0-9-_]"), "_")
            val gpxFilePath = gpxManager.saveGpxFile(trackPoints, sanitizedName)

            // Create and insert route entity
            val routeEntity = RouteEntity(
                name = name,
                date = date,
                distanceKm = distanceKm,
                durationSeconds = durationSeconds,
                gpxFilePath = gpxFilePath,
                averageSpeed = averageSpeed,
                maxSpeed = maxSpeed
            )

            val routeId = routeDao.insertRoute(routeEntity)

            // Insert track points with the generated route ID
            val trackPointEntities = trackPoints.map { point ->
                TrackPointEntity(
                    routeId = routeId,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    elevation = point.elevation,
                    timestamp = point.timestamp,
                    speed = point.speed
                )
            }
            routeDao.insertTrackPoints(trackPointEntities)

            routeId
        }
    }

    /**
     * Delete a route by ID, including its GPX file.
     */
    suspend fun deleteRoute(routeId: Long) {
        // Get the route to find the GPX file path
        val route = routeDao.getRouteById(routeId) ?: return

        // Delete the GPX file
        gpxManager.deleteGpxFile(route.gpxFilePath)

        // Delete from database (track points are deleted via CASCADE)
        routeDao.deleteRouteById(routeId)
    }

    /**
     * Delete all routes and their GPX files.
     */
    suspend fun deleteAllRoutes() {
        val routes = routeDao.getAllRoutes()
        routes.forEach { route ->
            gpxManager.deleteGpxFile(route.gpxFilePath)
        }
        routeDao.deleteAllRoutes()
    }

    /**
     * Export a route's GPX file for sharing via FileProvider.
     * @return Android Uri for the GPX file
     */
    suspend fun exportGpx(context: android.content.Context, routeId: Long): android.net.Uri? {
        val route = routeDao.getRouteById(routeId) ?: return null
        return gpxManager.getGpxUri(context, route.gpxFilePath)
    }

    /**
     * Export all GPX files as a ZIP archive.
     * @return Android Uri for the ZIP file
     */
    suspend fun exportAllGpx(context: android.content.Context): android.net.Uri? {
        return gpxManager.exportAllAsZip(context)
    }
}

// Extension functions to convert between Entity and model classes
fun RouteEntity.toRoute(): Route = Route(
    id = id,
    name = name,
    date = date,
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    gpxFilePath = gpxFilePath,
    averageSpeed = averageSpeed,
    maxSpeed = maxSpeed
)

fun TrackPointEntity.toTrackPoint(): TrackPoint = TrackPoint(
    latitude = latitude,
    longitude = longitude,
    elevation = elevation,
    timestamp = timestamp,
    speed = speed
)
