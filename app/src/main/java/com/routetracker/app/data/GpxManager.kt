package com.routetracker.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.routetracker.app.models.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * Manages GPX file operations: generation, saving, sharing, and deletion.
 * Also provides distance and speed calculations using the Haversine formula.
 */
class GpxManager(private val context: Context) {

    private val gpxDirectory: File
        get() = File(context.getExternalFilesDir("GPX"), "routes").apply { mkdirs() }

    /**
     * Generate a valid GPX 1.1 XML string from a list of TrackPoints.
     * GPX 1.1 format: https://www.topografix.com/GPX/1/1/
     */
    fun generateGpx(trackPoints: List<TrackPoint>, routeName: String): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"Route Tracker\" ")
        builder.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        builder.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        builder.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        builder.append("  <trk>\n")
        builder.append("    <name>").append(escapeXml(routeName)).append("</name>\n")
        builder.append("    <trkseg>\n")

        for (point in trackPoints) {
            builder.append("      <trkpt lat=\"").append(point.latitude).append("\" lon=\"").append(point.longitude).append("\">\n")
            point.elevation?.let { ele ->
                builder.append("        <ele>").append(ele).append("</ele>\n")
            }
            builder.append("        <time>").append(formatIso8601(point.timestamp)).append("</time>\n")
            point.speed?.let { speed ->
                builder.append("        <extensions><speed>").append(speed).append("</speed></extensions>\n")
            }
            builder.append("      </trkpt>\n")
        }

        builder.append("    </trkseg>\n")
        builder.append("  </trk>\n")
        builder.append("</gpx>")

        return builder.toString()
    }

    /**
     * Import a GPX file and return a list of TrackPoints.
     */
    suspend fun importGpx(uri: Uri): Pair<String, List<TrackPoint>>? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            doc.documentElement.normalize()

            val trackPoints = mutableListOf<TrackPoint>()
            var routeName = "Imported Route"

            // Get route name
            val nameNodes = doc.getElementsByTagName("name")
            if (nameNodes.length > 0) {
                routeName = nameNodes.item(0).textContent
            }

            // Get track points
            val trkptNodes: NodeList = doc.getElementsByTagName("trkpt")
            val isoSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            for (i in 0 until trkptNodes.length) {
                val node = trkptNodes.item(i) as Element
                val lat = node.getAttribute("lat").toDouble()
                val lon = node.getAttribute("lon").toDouble()
                
                var ele: Double? = null
                val eleNodes = node.getElementsByTagName("ele")
                if (eleNodes.length > 0) {
                    ele = eleNodes.item(0).textContent.toDoubleOrNull()
                }

                var timestamp = System.currentTimeMillis()
                val timeNodes = node.getElementsByTagName("time")
                if (timeNodes.length > 0) {
                    val timeStr = timeNodes.item(0).textContent
                    try {
                        timestamp = isoSdf.parse(timeStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        // Fallback to current time or increment from previous
                    }
                }

                var speed: Float? = null
                val speedNodes = node.getElementsByTagName("speed")
                if (speedNodes.length > 0) {
                    speed = speedNodes.item(0).textContent.toFloatOrNull()
                }

                trackPoints.add(TrackPoint(lat, lon, ele, timestamp, speed))
            }

            inputStream.close()
            Pair(routeName, trackPoints)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Save GPX file to external storage and return the absolute path.
     */
    suspend fun saveGpxFile(trackPoints: List<TrackPoint>, fileName: String): String =
        withContext(Dispatchers.IO) {
            val gpxContent = generateGpx(trackPoints, "Route_$fileName")
            val gpxFile = File(gpxDirectory, "${fileName}.gpx")
            FileWriter(gpxFile).use { writer ->
                writer.write(gpxContent)
            }
            gpxFile.absolutePath
        }

    /**
     * Get a FileProvider Uri for sharing a GPX file.
     */
    fun getGpxUri(context: Context, gpxFilePath: String): Uri {
        val file = File(gpxFilePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Delete a GPX file from disk.
     */
    fun deleteGpxFile(filePath: String) {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Export all GPX files as a ZIP archive for bulk sharing.
     */
    suspend fun exportAllAsZip(context: Context): Uri? = withContext(Dispatchers.IO) {
        val gpxFiles = gpxDirectory.listFiles { file -> file.extension == "gpx" }
            ?: return@withContext null

        if (gpxFiles.isEmpty()) return@withContext null

        val zipFile = File(context.cacheDir, "all_routes_${System.currentTimeMillis()}.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            for (gpxFile in gpxFiles) {
                val entry = ZipEntry(gpxFile.name)
                zos.putNextEntry(entry)
                gpxFile.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }

        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
    }

    /**
     * Calculate total distance using the Haversine formula.
     * Haversine formula calculates the great-circle distance between two points on a sphere.
     * Formula: a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
     *          c = 2 * atan2(√a, √(1−a))
     *          d = R * c (where R = Earth's radius = 6,371 km)
     */
    fun calculateTotalDistance(trackPoints: List<TrackPoint>): Double {
        if (trackPoints.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 1 until trackPoints.size) {
            totalDistance += haversineDistance(
                trackPoints[i - 1].latitude,
                trackPoints[i - 1].longitude,
                trackPoints[i].latitude,
                trackPoints[i].longitude
            )
        }
        return totalDistance
    }

    /**
     * Haversine formula to calculate distance between two GPS coordinates.
     * @return Distance in kilometers
     */
    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0

        // Convert degrees to radians
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        // Haversine formula
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadiusKm * c
    }

    /**
     * Calculate route duration in seconds.
     */
    fun calculateDuration(trackPoints: List<TrackPoint>): Long {
        if (trackPoints.size < 2) return 0L
        return (trackPoints.last().timestamp - trackPoints.first().timestamp) / 1000
    }

    /**
     * Calculate average speed in km/h.
     */
    fun calculateAverageSpeed(trackPoints: List<TrackPoint>): Double {
        val distanceKm = calculateTotalDistance(trackPoints)
        val durationHours = calculateDuration(trackPoints) / 3600.0
        return if (durationHours > 0) distanceKm / durationHours else 0.0
    }

    /**
     * Calculate maximum speed from recorded speeds or derived from segments.
     * Returns speed in km/h.
     */
    fun calculateMaxSpeed(trackPoints: List<TrackPoint>): Double {
        if (trackPoints.isEmpty()) return 0.0

        // First try to use recorded GPS speeds (already in m/s from Location)
        val recordedSpeeds = trackPoints.mapNotNull { it.speed }
        if (recordedSpeeds.isNotEmpty()) {
            return recordedSpeeds.max() * 3.6 // Convert m/s to km/h
        }

        // Fall back to calculating from segments
        var maxSpeedMps = 0.0
        for (i in 1 until trackPoints.size) {
            val distanceM = haversineDistance(
                trackPoints[i - 1].latitude,
                trackPoints[i - 1].longitude,
                trackPoints[i].latitude,
                trackPoints[i].longitude
            ) * 1000 // km to m

            val timeS = (trackPoints[i].timestamp - trackPoints[i - 1].timestamp) / 1000.0
            if (timeS > 0) {
                val speedMps = distanceM / timeS
                if (speedMps > maxSpeedMps) {
                    maxSpeedMps = speedMps
                }
            }
        }
        return maxSpeedMps * 3.6 // Convert m/s to km/h
    }

    // XML escape special characters
    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    // Format timestamp as ISO 8601 for GPX
    private fun formatIso8601(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }
}
