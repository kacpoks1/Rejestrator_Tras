package com.routetracker.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stats bar displayed during route recording.
 * Shows elapsed time, distance, current speed, and point count.
 */
@Composable
fun RecordingStats(
    distanceKm: Double,
    durationSeconds: Long,
    speedMps: Float?,
    pointCount: Int
) {
    Row {
        StatItem(
            label = "Distance",
            value = "%.2f km".format(distanceKm)
        )

        Spacer(modifier = Modifier.width(16.dp))

        StatItem(
            label = "Time",
            value = formatDurationShort(durationSeconds)
        )

        Spacer(modifier = Modifier.width(16.dp))

        StatItem(
            label = "Speed",
            value = speedMps?.let { "%.1f km/h".format(it * 3.6f) } ?: "0.0 km/h"
        )

        Spacer(modifier = Modifier.width(16.dp))

        StatItem(
            label = "Points",
            value = pointCount.toString()
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun formatDurationShort(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, secs)
        else -> "%02d:%02d".format(minutes, secs)
    }
}
