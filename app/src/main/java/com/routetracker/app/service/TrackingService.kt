package com.routetracker.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.routetracker.app.MainActivity
import com.routetracker.app.R
import com.routetracker.app.models.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Foreground service that tracks location updates and exposes route data via StateFlow.
 * Binds to ViewModel and runs as a foreground service with persistent notification.
 */
class TrackingService : Service() {

    companion object {
        private const val TAG = "TrackingService"
        private const val CHANNEL_ID = "tracking_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_INTERVAL_MS = 3000L
        private const val MIN_UPDATE_DISTANCE_M = 5f
        private const val ACCURACY_THRESHOLD_M = 30f

        const val EXTRA_POWER_SAVE_MODE = "extra_power_save_mode"

        // Action for stopping the service from notification
        const val ACTION_STOP_TRACKING = "com.routetracker.app.ACTION_STOP_TRACKING"
        const val ACTION_OPTIMIZE_BATTERY = "com.routetracker.app.ACTION_OPTIMIZE_BATTERY"
    }

    // Binder for client binding
    private val binder = LocalBinder()

    // StateFlow exposing current track points
    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()

    // StateFlow indicating if recording is active
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // FusedLocationProviderClient for location updates
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Wake lock to prevent CPU sleep during recording
    private var wakeLock: PowerManager.WakeLock? = null

    // Distance tracking
    private var lastLocation: Location? = null

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Client bound to service")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        val powerSaveMode = intent?.getBooleanExtra(EXTRA_POWER_SAVE_MODE, false) ?: false

        // Handle notification actions
        when (intent?.action) {
            ACTION_STOP_TRACKING -> {
                stopRecording()
                stopSelf()
            }
            ACTION_OPTIMIZE_BATTERY -> {
                openBatteryOptimizationSettings()
            }
        }

        // Acquire wake lock to prevent CPU sleep
        acquireWakeLock()

        // Start foreground immediately (must be within 5 seconds)
        startForeground(NOTIFICATION_ID, createNotification(0.0, 0L))

        _isRecording.value = true
        startLocationUpdates(powerSaveMode)

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(powerSaveMode: Boolean) {
        val interval = if (powerSaveMode) 10000L else LOCATION_INTERVAL_MS
        val minDistance = if (powerSaveMode) 15f else MIN_UPDATE_DISTANCE_M

        // Build location request
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            interval
        )
            .setMinUpdateIntervalMillis(interval)
            .setMinUpdateDistanceMeters(minDistance)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // Filter out low-accuracy points (accuracy > 30m)
                if (location.accuracy > ACCURACY_THRESHOLD_M) {
                    Log.d(TAG, "Skipping low-accuracy point: accuracy=${location.accuracy}")
                    return
                }

                val trackPoint = TrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevation = location.altitude.takeIf { it != 0.0 },
                    timestamp = location.time,
                    speed = location.speed.takeIf { it > 0 }
                )

                _trackPoints.value = _trackPoints.value + trackPoint
                lastLocation = location

                // Update notification with current stats
                val distanceKm = calculateDistance()
                val startTime = _trackPoints.value.firstOrNull()?.timestamp ?: System.currentTimeMillis()
                val durationMin = (System.currentTimeMillis() - startTime) / 60000
                updateNotification(distanceKm, durationMin)
            }
        }
    }

    /**
     * Calculate total distance from recorded track points using Haversine formula.
     */
    private fun calculateDistance(): Double {
        val points = _trackPoints.value
        if (points.size < 2) return 0.0

        var totalDistance = 0.0
        val earthRadiusKm = 6371.0

        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]

            val dLat = Math.toRadians(p2.latitude - p1.latitude)
            val dLon = Math.toRadians(p2.longitude - p1.longitude)
            val lat1Rad = Math.toRadians(p1.latitude)
            val lat2Rad = Math.toRadians(p2.latitude)

            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)

            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            totalDistance += earthRadiusKm * c
        }

        return totalDistance
    }

    private fun createNotification(distanceKm: Double = 0.0, durationMin: Long = 0): Notification {
        // Intent to open the app
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Stop action
        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }.let {
            PendingIntent.getService(
                this,
                1,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Battery optimization action
        val batteryIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_OPTIMIZE_BATTERY
        }.let {
            PendingIntent.getService(
                this,
                2,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val contentText = buildString {
            append("Recording route...")
            if (distanceKm > 0) {
                append(" %.2f km".format(distanceKm))
            }
            if (durationMin > 0) {
                append(" | %d min".format(durationMin))
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_route))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.stop), stopIntent)
            .addAction(android.R.drawable.ic_dialog_info, getString(R.string.optimize_battery), batteryIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(distanceKm: Double, durationMin: Long) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(distanceKm, durationMin))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.recording_route),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tracking service notification"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RouteTracker::TrackingService")
        wakeLock?.acquire()
        Log.d(TAG, "Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun stopRecording() {
        _isRecording.value = false
        stopLocationUpdates()
        releaseWakeLock()
    }

    @SuppressLint("MissingPermission")
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d(TAG, "Location updates stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        releaseWakeLock()
        Log.d(TAG, "Service destroyed")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Client unbound from service")
        return super.onUnbind(intent)
    }

    /**
     * Stop recording and return the collected track points.
     */
    fun stopAndGetTrackPoints(): List<TrackPoint> {
        stopRecording()
        return _trackPoints.value
    }

    /**
     * Reset the service for a new recording session.
     */
    fun reset() {
        _trackPoints.value = emptyList()
        lastLocation = null
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }
}
