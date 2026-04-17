package com.routetracker.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routetracker.app.data.GpxManager
import com.routetracker.app.data.RouteRepository
import com.routetracker.app.data.AppDatabase
import com.routetracker.app.models.Route
import com.routetracker.app.models.TrackPoint
import com.routetracker.app.service.TrackingService
import com.routetracker.app.bluetooth.BluetoothConnectionState
import com.routetracker.app.bluetooth.BluetoothDeviceInfo
import com.routetracker.app.bluetooth.BluetoothManager
import com.routetracker.app.bluetooth.BluetoothPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that manages route recording, storage, and retrieval.
 * Binds to TrackingService for live location updates.
 */
class RouteViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val gpxManager = GpxManager(application)
    private val repository = RouteRepository(database.routeDao(), gpxManager)

    private val bluetoothPreferences = BluetoothPreferences(application)
    private val bluetoothManager = BluetoothManager(application)

    val isBtAutoStartEnabled = bluetoothPreferences.btEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    
    val btPreferredDevice = bluetoothPreferences.btDevice.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    val bluetoothConnectionState = bluetoothManager.connectionState

    // StateFlow for saved routes
    private val _savedRoutes = MutableStateFlow<List<Route>>(emptyList())
    val savedRoutes: StateFlow<List<Route>> = _savedRoutes.asStateFlow()

    // StateFlow for currently recording track points (from service)
    private val _currentTrackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val currentTrackPoints: StateFlow<List<TrackPoint>> = _currentTrackPoints.asStateFlow()

    // StateFlow for recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // StateFlow for route summary after stopping
    private val _routeSummary = MutableStateFlow<RouteSummary?>(null)
    val routeSummary: StateFlow<RouteSummary?> = _routeSummary.asStateFlow()

    // StateFlow for selected route ID (for showing on map)
    private val _selectedRouteId = MutableStateFlow<Long?>(null)
    val selectedRouteId: StateFlow<Long?> = _selectedRouteId.asStateFlow()

    // StateFlow for route to display on map
    private val _routeToShow = MutableStateFlow<List<TrackPoint>>(emptyList())
    val routeToShow: StateFlow<List<TrackPoint>> = _routeToShow.asStateFlow()

    // Settings
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _isAutoStartEnabled = MutableStateFlow(prefs.getBoolean("auto_start", false))
    val isAutoStartEnabled: StateFlow<Boolean> = _isAutoStartEnabled.asStateFlow()

    private val _isPowerSaveMode = MutableStateFlow(prefs.getBoolean("power_save", false))
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    // Theme state: null = auto, true = dark, false = light
    private val _isDarkModeEnabled = MutableStateFlow<Boolean?>(null)
    val isDarkModeEnabled: StateFlow<Boolean?> = _isDarkModeEnabled.asStateFlow()

    private var autoStartAttempted = false

    // Tracking service reference
    private var trackingService: TrackingService? = null
    private var serviceBound = false

    // Service connection for binding to TrackingService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TrackingService.LocalBinder
            trackingService = binder.getService()
            serviceBound = true

            // Observe service state
            viewModelScope.launch {
                trackingService?.trackPoints?.collect { points ->
                    _currentTrackPoints.value = points
                }
            }

            viewModelScope.launch {
                trackingService?.isRecording?.collect { recording ->
                    _isRecording.value = recording
                }
            }

            // Try auto-start if enabled and not already recording
            if (_isAutoStartEnabled.value && !autoStartAttempted && _isRecording.value == false) {
                autoStartAttempted = true
                trackingService?.reset()
                val intent = Intent(application, TrackingService::class.java)
                application.startForegroundService(intent)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            serviceBound = false
        }
    }

    init {
        loadSavedRoutes()
        // Initialize theme from preferences
        val savedTheme = prefs.getString("map_theme", "auto")
        _isDarkModeEnabled.value = when(savedTheme) {
            "dark" -> true
            "light" -> false
            else -> null
        }

        // Bluetooth auto-start logic
        viewModelScope.launch {
            isBtAutoStartEnabled.collectLatest { enabled ->
                val device = btPreferredDevice.value
                if (enabled && device != null) {
                    bluetoothManager.startWatching(device.address)
                } else {
                    bluetoothManager.stopWatching()
                }
            }
        }

        viewModelScope.launch {
            btPreferredDevice.collectLatest { device ->
                if (isBtAutoStartEnabled.value && device != null) {
                    bluetoothManager.startWatching(device.address)
                }
            }
        }

        viewModelScope.launch {
            bluetoothManager.connectionState.collectLatest { state ->
                when (state) {
                    is BluetoothConnectionState.Connected -> {
                        if (!_isRecording.value) {
                            startRecording(application)
                        }
                    }
                    is BluetoothConnectionState.Disconnected -> {
                        if (_isRecording.value) {
                            stopRecording(application)
                            _routeSummary.value?.let {
                                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date())
                                saveRoute(
                                    name = "BT Auto: $dateStr",
                                    mergeByDate = true
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Bind to the tracking service.
     * Must be called from Activity/Fragment context.
     */
    fun bindTrackingService(context: Context) {
        if (!serviceBound) {
            val intent = Intent(context, TrackingService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * Unbind from the tracking service.
     */
    fun unbindTrackingService(context: Context) {
        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /**
     * Start recording a new route.
     */
    fun startRecording(context: Context) {
        trackingService?.reset()
        val intent = Intent(context, TrackingService::class.java)
        context.startForegroundService(intent)
    }

    /**
     * Stop recording and calculate route summary.
     */
    fun stopRecording(context: Context) {
        val trackPoints = trackingService?.stopAndGetTrackPoints() ?: emptyList()

        if (trackPoints.isNotEmpty()) {
            // Calculate statistics
            val distanceKm = gpxManager.calculateTotalDistance(trackPoints)
            val durationSeconds = gpxManager.calculateDuration(trackPoints)
            val avgSpeed = gpxManager.calculateAverageSpeed(trackPoints)
            val maxSpeed = gpxManager.calculateMaxSpeed(trackPoints)

            _routeSummary.value = RouteSummary(
                trackPoints = trackPoints,
                distanceKm = distanceKm,
                durationSeconds = durationSeconds,
                averageSpeed = avgSpeed,
                maxSpeed = maxSpeed
            )
        }
    }

    /**
     * Import a GPX file.
     */
    fun importGpx(uri: Uri) {
        viewModelScope.launch {
            repository.importGpx(uri)
            loadSavedRoutes()
        }
    }

    /**
     * Save the recorded route to the database.
     */
    fun saveRoute(name: String, mergeByDate: Boolean = false) {
        val summary = _routeSummary.value ?: return

        viewModelScope.launch {
            repository.saveRoute(
                name = name,
                date = System.currentTimeMillis(),
                trackPoints = summary.trackPoints,
                distanceKm = summary.distanceKm,
                durationSeconds = summary.durationSeconds,
                averageSpeed = summary.averageSpeed,
                maxSpeed = summary.maxSpeed,
                mergeByDate = mergeByDate
            )
            loadSavedRoutes()
            _routeSummary.value = null
        }
    }

    /**
     * Cancel saving the route (discard the summary).
     */
    fun cancelSaveRoute() {
        _routeSummary.value = null
    }

    /**
     * Load all saved routes from the database.
     */
    private fun loadSavedRoutes() {
        viewModelScope.launch {
            _savedRoutes.value = repository.getAllRoutes()
        }
    }

    /**
     * Delete a route by ID.
     */
    fun deleteRoute(routeId: Long) {
        viewModelScope.launch {
            repository.deleteRoute(routeId)
            loadSavedRoutes()
        }
    }

    /**
     * Get track points for a specific route.
     */
    suspend fun getTrackPointsForRoute(routeId: Long): List<TrackPoint> {
        return repository.getTrackPointsForRoute(routeId)
    }

    /**
     * Export a single route's GPX file for sharing.
     */
    suspend fun exportRouteGpx(context: Context, routeId: Long): android.net.Uri? {
        return repository.exportGpx(context, routeId)
    }

    /**
     * Export all routes as a ZIP archive.
     */
    suspend fun exportAllRoutes(context: Context): android.net.Uri? {
        return repository.exportAllGpx(context)
    }

    /**
     * Get a route by ID.
     */
    suspend fun getRouteById(routeId: Long): Route? {
        return repository.getRouteById(routeId)
    }

    /**
     * Set whether to automatically start recording on app launch.
     */
    fun setAutoStartEnabled(enabled: Boolean) {
        _isAutoStartEnabled.value = enabled
        prefs.edit().putBoolean("auto_start", enabled).apply()
    }

    fun setPowerSaveMode(enabled: Boolean) {
        _isPowerSaveMode.value = enabled
        prefs.edit().putBoolean("power_save", enabled).apply()
    }

    /**
     * Set map theme: "auto", "light", "dark"
     */
    fun setMapTheme(theme: String) {
        prefs.edit().putString("map_theme", theme).apply()
        _isDarkModeEnabled.value = when(theme) {
            "dark" -> true
            "light" -> false
            else -> null
        }
    }

    fun setBtAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            bluetoothPreferences.setBtEnabled(enabled)
        }
    }

    fun saveBtDevice(address: String, name: String) {
        viewModelScope.launch {
            bluetoothPreferences.setBtDevice(address, name)
        }
    }

    fun getPairedBluetoothDevices(): List<BluetoothDeviceInfo> {
        return bluetoothManager.getPairedDevices()
    }

    /**
     * Check and trigger auto-start if conditions are met.
     */
    fun checkAutoStart(context: Context) {
        if (_isAutoStartEnabled.value && !autoStartAttempted && !_isRecording.value && serviceBound) {
            autoStartAttempted = true
            startRecording(context)
        }
    }

    /**
     * Select a route to show on map.
     */
    fun selectRoute(routeId: Long) {
        _selectedRouteId.value = routeId
        viewModelScope.launch {
            _routeToShow.value = getTrackPointsForRoute(routeId)
        }
    }

    /**
     * Clear the selected route.
     */
    fun clearSelectedRoute() {
        _selectedRouteId.value = null
        _routeToShow.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.release()
        // Service will be unbound by Activity
    }
}

/**
 * Data class holding route summary information after recording stops.
 */
data class RouteSummary(
    val trackPoints: List<TrackPoint>,
    val distanceKm: Double,
    val durationSeconds: Long,
    val averageSpeed: Double,
    val maxSpeed: Double
)
