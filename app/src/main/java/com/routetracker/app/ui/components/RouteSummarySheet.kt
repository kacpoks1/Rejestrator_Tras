package com.routetracker.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.routetracker.app.RouteSummary
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet showing route summary after recording stops.
 * Displays distance, duration, average speed, max speed.
 * Allows entering route name and saving.
 */
@Composable
fun RouteSummarySheet(
    summary: RouteSummary,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    val defaultName = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    var routeName by remember { mutableStateOf(defaultName) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Route Summary",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = "Distance",
                    value = "%.2f km".format(summary.distanceKm)
                )
                Spacer(modifier = Modifier.weight(1f))
                StatItem(
                    label = "Duration",
                    value = formatDuration(summary.durationSeconds)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = "Avg speed",
                    value = "%.1f km/h".format(summary.averageSpeed)
                )
                Spacer(modifier = Modifier.weight(1f))
                StatItem(
                    label = "Max speed",
                    value = "%.1f km/h".format(summary.maxSpeed)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ElevationProfile(
                trackPoints = summary.trackPoints,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = routeName,
                onValueChange = { routeName = it },
                label = { Text("Route name") },
                placeholder = { Text("Enter route name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.padding(8.dp))

                Button(
                    onClick = { if (routeName.isNotBlank()) onSave(routeName) },
                    modifier = Modifier.weight(1f),
                    enabled = routeName.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "%dh %dm %ds".format(hours, minutes, secs)
        minutes > 0 -> "%dm %ds".format(minutes, secs)
        else -> "%ds".format(secs)
    }
}
