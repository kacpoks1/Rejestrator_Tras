package com.routetracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Route Tracker app.
 * Singleton pattern ensures only one instance exists throughout the app lifecycle.
 */
@Database(
    entities = [RouteEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         * Uses double-checked locking for thread safety.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "route_tracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
