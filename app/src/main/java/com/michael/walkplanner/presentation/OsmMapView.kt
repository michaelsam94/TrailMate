package com.michael.walkplanner.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

@Composable
fun OsmMapView(
    center: GeoPoint,
    routePoints: List<GeoPoint> = emptyList(),
    zoomLevel: Double = 16.0,
    enableOffset: Boolean = false,
    zoomToBoundingBox: Boolean = enableOffset,
    plannedRoutePoints: List<GeoPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(zoomLevel)
                controller.setCenter(center)
            }
        },
        update = { map ->
            map.overlays.clear()

            // 1. Draw planned route as underlay guide line
            if (plannedRoutePoints.isNotEmpty()) {
                val plannedPolyline = Polyline(map).apply {
                    setPoints(plannedRoutePoints)
                    outlinePaint.color = android.graphics.Color.parseColor("#401B6B45") // Light semi-transparent Forest Green underlay
                    outlinePaint.strokeWidth = 12f
                }
                map.overlays.add(plannedPolyline)
            }

            // 2. Draw live/active route points
            if (routePoints.isNotEmpty()) {
                val isBacktrack = enableOffset && isBacktrackingRoute(routePoints)
                val displayPoints = getOffsetPoints(routePoints, isBacktrack, offsetMeters = 18.0)
                val midIndex = routePoints.size / 2

                if (isBacktrack && displayPoints.size > midIndex) {
                    // Split backtracking route into outgoing (solid) and return (dashed) polylines
                    val outgoingPoints = displayPoints.subList(0, midIndex + 1)
                    val returnPoints = displayPoints.subList(midIndex, displayPoints.size)

                    val outgoingPolyline = Polyline(map).apply {
                        setPoints(outgoingPoints)
                        outlinePaint.color = android.graphics.Color.parseColor("#1B6B45") // Forest Green
                        outlinePaint.strokeWidth = 8f
                    }
                    map.overlays.add(outgoingPolyline)

                    val returnPolyline = Polyline(map).apply {
                        setPoints(returnPoints)
                        outlinePaint.color = android.graphics.Color.parseColor("#1B6B45") // Forest Green
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 15f), 0f) // Dashed line
                    }
                    map.overlays.add(returnPolyline)
                } else {
                    // Standard single polyline for non-backtracking loops or live tracking paths
                    val polyline = Polyline(map).apply {
                        setPoints(displayPoints)
                        outlinePaint.color = android.graphics.Color.parseColor("#1B6B45")
                        outlinePaint.strokeWidth = 8f
                    }
                    map.overlays.add(polyline)
                }

                val startMarker = Marker(map).apply {
                    position = routePoints.first()
                    title = if (isBacktrack) "Start & End Point" else "Start Point"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(startMarker)

                if (isBacktrack) {
                    val turnaroundMarker = Marker(map).apply {
                        position = routePoints[midIndex]
                        title = "Turnaround Point"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(turnaroundMarker)
                }

                if (!zoomToBoundingBox) {
                    val currentMarker = Marker(map).apply {
                        position = center
                        title = "Current Location"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(currentMarker)
                }

                // Fit the bounding box of route points or center on location
                if (zoomToBoundingBox) {
                    val minLat = routePoints.minOf { it.latitude }
                    val maxLat = routePoints.maxOf { it.latitude }
                    val minLng = routePoints.minOf { it.longitude }
                    val maxLng = routePoints.maxOf { it.longitude }
                    val boundingBox = BoundingBox(maxLat, maxLng, minLat, minLng)

                    val currentRouteKey = routePoints.hashCode()
                    val lastRouteKey = map.tag as? Int
                    if (lastRouteKey != currentRouteKey) {
                        map.tag = currentRouteKey
                        map.post {
                            map.zoomToBoundingBox(boundingBox, false, 120)
                        }
                    }
                } else {
                    map.controller.setCenter(center)
                }
            } else {
                // If there are no live tracking points yet, show a start point marker
                val startMarkerPosition = if (plannedRoutePoints.isNotEmpty()) {
                    plannedRoutePoints.first()
                } else {
                    center
                }
                val centerMarker = Marker(map).apply {
                    position = startMarkerPosition
                    title = "Start Point"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(centerMarker)

                // If zoomToBoundingBox is true (e.g. previewing route) and we have a planned route, fit to bounds
                if (zoomToBoundingBox && plannedRoutePoints.isNotEmpty()) {
                    val minLat = plannedRoutePoints.minOf { it.latitude }
                    val maxLat = plannedRoutePoints.maxOf { it.latitude }
                    val minLng = plannedRoutePoints.minOf { it.longitude }
                    val maxLng = plannedRoutePoints.maxOf { it.longitude }
                    val boundingBox = BoundingBox(maxLat, maxLng, minLat, minLng)

                    val currentRouteKey = plannedRoutePoints.hashCode()
                    val lastRouteKey = map.tag as? Int
                    if (lastRouteKey != currentRouteKey) {
                        map.tag = currentRouteKey
                        map.post {
                            map.zoomToBoundingBox(boundingBox, false, 120)
                        }
                    }
                } else {
                    map.controller.setZoom(zoomLevel)
                    map.controller.setCenter(center)
                }
            }
            map.invalidate()
        },
        modifier = modifier
    )
}

private fun isBacktrackingRoute(points: List<GeoPoint>): Boolean {
    if (points.size < 6) return false
    val n = points.size
    val mid = n / 2
    var overlapCount = 0
    var compareCount = 0
    for (i in 1 until mid - 1) {
        val forwardPt = points[i]
        val backwardPt = points[n - 1 - i]
        val dist = calculateDistanceMeters(forwardPt, backwardPt)
        compareCount++
        if (dist < 30.0) { // 30 meters
            overlapCount++
        }
    }
    return compareCount > 0 && (overlapCount.toDouble() / compareCount) > 0.7
}

private fun calculateDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
    val r = 6371000.0 // Earth's radius in meters
    val dLat = Math.toRadians(p2.latitude - p1.latitude)
    val dLon = Math.toRadians(p2.longitude - p1.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun getOffsetPoints(points: List<GeoPoint>, isBacktrack: Boolean, offsetMeters: Double = 18.0): List<GeoPoint> {
    if (points.size < 4 || !isBacktrack) return points
    val n = points.size
    val mid = n / 2
    val result = ArrayList<GeoPoint>(n)
    
    for (i in 0 until n) {
        val p = points[i]
        
        // Calculate envelope scale
        val scale = if (i < mid) {
            val t = i.toDouble() / mid
            sin(t * Math.PI)
        } else {
            val t = (i - mid).toDouble() / (n - 1 - mid)
            sin(t * Math.PI)
        }
        
        if (scale < 1e-5 || i == 0 || i == n - 1) {
            result.add(p)
            continue
        }
        
        // Calculate direction vector using adjacent points
        val prev = points[i - 1]
        val next = points[i + 1]
        
        val dy = next.latitude - prev.latitude
        val dx = next.longitude - prev.longitude
        val len = sqrt(dy * dy + dx * dx)
        
        if (len < 1e-9) {
            result.add(p)
            continue
        }
        
        // Perpendicular vector (shifted to the right)
        val perLat = -dx / len
        val perLng = dy / len
        
        val latRad = Math.toRadians(p.latitude)
        val latOffset = perLat * (offsetMeters / 111000.0) * scale
        val lngOffset = perLng * (offsetMeters / (111000.0 * cos(latRad))) * scale
        
        result.add(GeoPoint(p.latitude + latOffset, p.longitude + lngOffset))
    }
    return result
}
