package com.routetracker.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.routetracker.app.R
import com.routetracker.app.RouteViewModel
import com.routetracker.app.models.Route
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying list of all saved routes.
 * Supports swipe-to-delete, show on map, and share GPX actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteListScreen(
    viewModel: RouteViewModel,
    onNavigateBack: () -> Unit,
    onShowRouteOnMap: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val routes by viewModel.savedRoutes.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    var deletedRoute by remember { mutableStateOf<Route?>(null) }
    var showExportAll by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importGpx(it) }
    }

    androidx.compose.runtime.LaunchedEffect(deletedRoute) {
        deletedRoute?.let {
            snackbarHostState.showSnackbar("Route deleted")
            deletedRoute = null
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Saved Routes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch("application/gpx+xml")
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_import),
                            contentDescription = "Import GPX"
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val uri = viewModel.exportAllRoutes(context)
                            if (uri != null) {
                                shareFile(context, uri, "Export all routes")
                            }
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_export),
                            contentDescription = "Export all"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        if (routes.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_map),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No routes yet",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start recording to create your first route",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(routes, key = { it.id }) { route ->
                    var isDismissed by remember { mutableStateOf(false) }

                    if (isDismissed) {
                        deletedRoute = route
                        isDismissed = false
                    }

                    RouteListItem(
                        route = route,
                        onShowOnMap = { onShowRouteOnMap(route.id) },
                        onShareGpx = {
                            scope.launch {
                                val uri = viewModel.exportRouteGpx(context, route.id)
                                if (uri != null) {
                                    shareFile(context, uri, "Share route GPX")
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                deletedRoute = route
                                viewModel.deleteRoute(route.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteListItem(
    route: Route,
    onShowOnMap: () -> Unit,
    onShareGpx: () -> Unit,
    onDelete: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.error,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        },
        content = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRouteStats(route),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        IconButton(onClick = onShowOnMap) {
                            Icon(
                                painter = painterResource(R.drawable.ic_map),
                                contentDescription = "Show on map",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onShareGpx) {
                            Icon(
                                painter = painterResource(R.drawable.ic_share),
                                contentDescription = "Share GPX",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        },
        enableDismissFromStartToEnd = false
    )
}

private fun formatRouteStats(route: Route): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(route.date))
    val distanceStr = "%.2f km".format(route.distanceKm)
    val durationStr = formatDuration(route.durationSeconds)

    return "$dateStr • $distanceStr • $durationStr"
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        hours > 0 -> "%dh %dm".format(hours, minutes)
        minutes > 0 -> "%dm".format(minutes)
        else -> "< 1 min"
    }
}

private fun shareFile(context: Context, uri: Uri, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
