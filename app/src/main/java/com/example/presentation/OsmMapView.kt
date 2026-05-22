package com.example.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmMapView(
    center: GeoPoint,
    routePoints: List<GeoPoint> = emptyList(),
    zoomLevel: Double = 16.0,
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
            map.controller.setCenter(center)
            map.overlays.clear()

            if (routePoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    setPoints(routePoints)
                    outlinePaint.color = android.graphics.Color.parseColor("#1B6B45")
                    outlinePaint.strokeWidth = 10f
                }
                map.overlays.add(polyline)

                val startMarker = Marker(map).apply {
                    position = routePoints.first()
                    title = "Start Point"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(startMarker)
                
                // If there are points, let's zoom to fit the route or center on the route center
                map.controller.setZoom(15.0)
                map.controller.setCenter(routePoints[routePoints.size / 2])
            } else {
                val centerMarker = Marker(map).apply {
                    position = center
                    title = "Your Location"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                map.overlays.add(centerMarker)
                map.controller.setZoom(zoomLevel)
            }
            map.invalidate()
        },
        modifier = modifier
    )
}
