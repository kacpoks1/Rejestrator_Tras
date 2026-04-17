package com.routetracker.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.routetracker.app.R
import com.routetracker.app.RouteViewModel
import com.routetracker.app.models.TrackPoint
import com.routetracker.app.ui.components.RecordingStats
import com.routetracker.app.ui.components.RouteSummarySheet
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Main map screen with osmdroid MapView for displaying and recording routes.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: RouteViewModel,
    onNavigateToRouteList: () -> Unit,
    onNavigateToBluetoothSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission states
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val backgroundLocationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    val multiplePermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else ""
        ).filter { it.isNotEmpty() }
    )

    var showPermissionDialog by remember { mutableStateOf(false) }
    var showBackgroundLocationDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    // Collect state from ViewModel
    val isRecording by viewModel.isRecording.collectAsState()
    val currentPoints by viewModel.currentTrackPoints.collectAsState()
    val routeSummary by viewModel.routeSummary.collectAsState()
    val selectedRouteId by viewModel.selectedRouteId.collectAsState()
    val routeToShow by viewModel.routeToShow.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()

    val isSystemDark = isSystemInDarkTheme()
    val isDarkModeEnabled by viewModel.isDarkModeEnabled.collectAsState()
    val isBtEnabled by viewModel.isBtAutoStartEnabled.collectAsState()
    val btDevice by viewModel.btPreferredDevice.collectAsState()

    val finalDarkMode = when(isDarkModeEnabled) {
        true -> true
        false -> false
        null -> isSystemDark
    }

    // State for all saved routes track points
    var allSavedTrackPoints by remember { mutableStateOf<Map<Long, List<GeoPoint>>>(emptyMap()) }

    // Load track points for all saved routes
    LaunchedEffect(savedRoutes) {
        val pointsMap = mutableMapOf<Long, List<GeoPoint>>()
        savedRoutes.forEach { route ->
            val points = viewModel.getTrackPointsForRoute(route.id)
            // Rounding to 4 decimal places (~11m) helps paths on the same street overlap better
            val roundedPoints = points.map { 
                GeoPoint(
                    Math.round(it.latitude * 10000.0) / 10000.0,
                    Math.round(it.longitude * 10000.0) / 10000.0
                )
            }
            // Deduplicate consecutive identical points
            val uniquePoints = mutableListOf<GeoPoint>()
            for (p in roundedPoints) {
                if (uniquePoints.isEmpty() || uniquePoints.last().latitude != p.latitude || uniquePoints.last().longitude != p.longitude) {
                    uniquePoints.add(p)
                }
            }
            pointsMap[route.id] = uniquePoints
        }
        allSavedTrackPoints = pointsMap
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        if (!multiplePermissionsState.allPermissionsGranted) {
            showPermissionDialog = true
        } else {
            // Check for background location on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!backgroundLocationPermissionState.status.isGranted) {
                    showBackgroundLocationDialog = true
                }
            }
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!notificationPermissionState.status.isGranted) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }

    // Bind to tracking service
    DisposableEffect(Unit) {
        viewModel.bindTrackingService(context)
        onDispose {
            viewModel.unbindTrackingService(context)
        }
    }

    // MapView and Location Overlay reference
    val mapView = remember { MapView(context) }
    val locationOverlay = remember { 
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
    }

    // Initialize osmdroid configuration and handle lifecycle
    DisposableEffect(Unit) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            setUserAgentValue(context.packageName)
        }

        onDispose {
            locationOverlay.disableMyLocation()
            mapView.onDetach()
        }
    }

    // Check if all permissions granted
    val hasLocationPermission = multiplePermissionsState.allPermissionsGranted &&
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLocationPermissionState.status.isGranted
            } else {
                true
            }

    // Enable/disable location overlay based on permissions
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            viewModel.checkAutoStart(context)
        } else {
            locationOverlay.disableMyLocation()
            locationOverlay.disableFollowLocation()
        }
    }

    // Permission dialogs
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permission required") },
            text = { Text("Location permission is required for tracking your route") },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    multiplePermissionsState.launchMultiplePermissionRequest()
                }) {
                    Text("Grant permission")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBackgroundLocationDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Background location required") },
            text = { Text("Background location permission allows tracking your route even when the app is not visible. This is required for the tracking service to work properly.") },
            confirmButton = {
                Button(onClick = {
                    showBackgroundLocationDialog = false
                    backgroundLocationPermissionState.launchPermissionRequest()
                }) {
                    Text("Grant permission")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBackgroundLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSettingsDialog) {
        val isAutoStartEnabled by viewModel.isAutoStartEnabled.collectAsState()
        val isPowerSaveMode by viewModel.isPowerSaveMode.collectAsState()
        
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Ustawienia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Automatyczny start nagrywania")
                        Switch(
                            checked = isAutoStartEnabled,
                            onCheckedChange = { viewModel.setAutoStartEnabled(it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Oszczędzanie energii")
                            Text(
                                "Rzadsze odpytywanie GPS (oszczędza baterię)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPowerSaveMode,
                            onCheckedChange = { viewModel.setPowerSaveMode(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Motyw mapy", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf("auto" to "Auto", "light" to "Jasny", "dark" to "Ciemny")
                        themes.forEach { (tag, label) ->
                            val selected = when(tag) {
                                "auto" -> isDarkModeEnabled == null
                                "light" -> isDarkModeEnabled == false
                                "dark" -> isDarkModeEnabled == true
                                else -> false
                            }
                            if (selected) {
                                Button(
                                    onClick = { viewModel.setMapTheme(tag) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.setMapTheme(tag) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Zamknij")
                }
            }
        )
    }

    if (!hasLocationPermission) {
        // Show error screen with settings button
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Location permission required",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) {
                    Text("Open settings")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.app_name)) },
                actions = {
                    Box {
                        BadgedBox(
                            badge = {
                                if (isBtEnabled && btDevice != null) {
                                    Badge(containerColor = Color(0xFF4CAF50))
                                }
                            }
                        ) {
                            IconButton(onClick = { showSettingsMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Ustawienia",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showSettingsMenu,
                            onDismissRequest = { showSettingsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Saved routes") },
                                onClick = {
                                    showSettingsMenu = false
                                    onNavigateToRouteList()
                                },
                                leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Bluetooth auto-start") },
                                onClick = {
                                    showSettingsMenu = false
                                    onNavigateToBluetoothSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("General Settings") },
                                onClick = {
                                    showSettingsMenu = false
                                    showSettingsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { 
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 100.dp)
            ) 
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // MapView
            AndroidView(
                factory = { ctx ->
                    mapView.apply {
                        setMultiTouchControls(true)
                        setTileSource(TileSourceFactory.MAPNIK)
                        controller.setZoom(15.0)
                        setBuiltInZoomControls(true)
                        
                        // Add location overlay if not already present
                        if (!overlays.contains(locationOverlay)) {
                            overlays.add(locationOverlay)
                        }
                    }
                },
                update = { view ->
                    if (finalDarkMode) {
                        val filter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f); postConcat(ColorMatrix(floatArrayOf(
                            -1.0f, 0f, 0f, 0f, 255f,
                            0f, -1.0f, 0f, 0f, 255f,
                            0f, 0f, -1.0f, 0f, 255f,
                            0f, 0f, 0f, 1.0f, 0f
                        ))) })
                        view.overlayManager.tilesOverlay.setColorFilter(filter)
                    } else {
                        view.overlayManager.tilesOverlay.setColorFilter(null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Draw polylines on map
            LaunchedEffect(currentPoints, routeToShow, selectedRouteId, allSavedTrackPoints) {
                // Remove existing polylines but keep location overlay
                mapView.overlays.removeAll { it is Polyline }

                // Draw all saved routes (red/semi-transparent)
                allSavedTrackPoints.forEach { (id, points) ->
                    if (id != selectedRouteId) {
                        val polyline = Polyline().apply {
                            setPoints(points)
                            outlinePaint.apply {
                                color = Color(0xFF3F51B5).copy(alpha = 0.4f).toArgb() // Indigo/Blue matching the icon
                                strokeWidth = 15f
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                        }
                        mapView.overlays.add(polyline)
                    }
                }

                // Draw current recording route (green)
                if (currentPoints.isNotEmpty()) {
                    val polyline = Polyline().apply {
                        setPoints(currentPoints.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.apply {
                            color = Color(0xFF4CAF50).toArgb() // Material Green
                            strokeWidth = 10f
                        }
                    }
                    mapView.overlays.add(polyline)

                    // Center map on current position if recording
                    val lastPoint = currentPoints.last()
                    mapView.controller.setCenter(GeoPoint(lastPoint.latitude, lastPoint.longitude))
                }

                // Draw selected saved route (orange)
                if (routeToShow.isNotEmpty() && selectedRouteId != null) {
                    val polyline = Polyline().apply {
                        setPoints(routeToShow.map { GeoPoint(it.latitude, it.longitude) })
                        outlinePaint.apply {
                            color = Color(0xFFFF9800).toArgb() // Orange
                            strokeWidth = 10f
                        }
                    }
                    mapView.overlays.add(polyline)

                    // Fit camera to show entire route
                    val boundingBox = BoundingBox.fromGeoPoints(
                        routeToShow.map { GeoPoint(it.latitude, it.longitude) }
                    )
                    mapView.zoomToBoundingBox(boundingBox, true)
                }

                mapView.invalidate()
            }

            // Bottom bar with controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Stats bar (visible only while recording)
                AnimatedVisibility(
                    visible = isRecording,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            val distance = calculateDistanceFromPoints(currentPoints)
                            val duration = (System.currentTimeMillis() - (currentPoints.firstOrNull()?.timestamp ?: System.currentTimeMillis())) / 1000
                            val speed = currentPoints.lastOrNull()?.speed

                            RecordingStats(
                                distanceKm = distance,
                                durationSeconds = duration,
                                speedMps = speed,
                                pointCount = currentPoints.size
                            )
                        }
                    }
                }

                // Route summary sheet (shown after stopping)
                AnimatedVisibility(
                    visible = routeSummary != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    routeSummary?.let { summary ->
                        RouteSummarySheet(
                            summary = summary,
                            onSave = { name -> 
                                viewModel.saveRoute(name)
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.save))
                                }
                            },
                            onCancel = { viewModel.cancelSaveRoute() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Menu button
                    FloatingActionButton(
                        onClick = onNavigateToRouteList,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // Start/Stop button
                    if (isRecording) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(72.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(72.dp),
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                            FloatingActionButton(
                                onClick = { viewModel.stopRecording(context) },
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_media_pause),
                                    contentDescription = "Stop",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        FloatingActionButton(
                            onClick = { viewModel.startRecording(context) },
                            containerColor = Color(0xFF4CAF50),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_media_play),
                                contentDescription = "Start",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Locate me button
                    FloatingActionButton(
                        onClick = {
                            val myLoc = locationOverlay.myLocation
                            if (myLoc != null) {
                                mapView.controller.setZoom(16.0)
                                mapView.controller.animateTo(myLoc)
                            } else if (currentPoints.isNotEmpty()) {
                                val lastPoint = currentPoints.last()
                                mapView.controller.setZoom(16.0)
                                mapView.controller.animateTo(
                                    GeoPoint(lastPoint.latitude, lastPoint.longitude)
                                )
                            } else if (routeToShow.isNotEmpty()) {
                                // Center on selected route
                                val boundingBox = BoundingBox.fromGeoPoints(
                                    routeToShow.map { GeoPoint(it.latitude, it.longitude) }
                                )
                                mapView.zoomToBoundingBox(boundingBox, true)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location),
                            contentDescription = "Locate me",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculate distance from track points using Haversine formula.
 * Formula: a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlon/2)
 *          c = 2 × atan2(√a, √(1−a))
 *          d = R × c (where R = Earth radius = 6,371 km)
 */
private fun calculateDistanceFromPoints(points: List<TrackPoint>): Double {
    if (points.size < 2) return 0.0

    var totalDistance = 0.0
    val earthRadiusKm = 6371.0

    for (i in 1 until points.size) {
        val p1 = points[i - 1]
        val p2 = points[i]

        // Convert degrees to radians
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)

        // Haversine formula
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        totalDistance += earthRadiusKm * c
    }

    return totalDistance
}
