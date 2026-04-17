package com.routetracker.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for route operations.
 * Provides methods for CRUD operations on routes and track points.
 */
@Dao
interface RouteDao {

    // Route operations
    @Insert
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY date DESC")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getRouteById(routeId: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE name = :name LIMIT 1")
    suspend fun getRouteByName(name: String): RouteEntity?

    @Query("SELECT * FROM routes WHERE date >= :startOfDay AND date <= :endOfDay AND name LIKE :namePattern LIMIT 1")
    suspend fun getRouteByDateAndType(startOfDay: Long, endOfDay: Long, namePattern: String): RouteEntity?

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: Long)

    @Query("DELETE FROM routes")
    suspend fun deleteAllRoutes()

    // Track point operations
    @Insert
    suspend fun insertTrackPoint(trackPoint: TrackPointEntity): Long

    @Insert
    suspend fun insertTrackPoints(trackPoints: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    suspend fun getTrackPointsForRoute(routeId: Long): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE routeId = :routeId")
    suspend fun deleteTrackPointsForRoute(routeId: Long)
}
