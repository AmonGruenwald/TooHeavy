package com.tooheavy.app.ui.running

import android.content.Context
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tooheavy.app.BuildConfig
import com.tooheavy.app.data.GpsPoint
import com.tooheavy.app.ui.theme.Accent
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

/** osmdroid settings: identify the app to the OpenStreetMap tile servers and keep tiles in the app's cache. */
private object OsmSetup {
    private var done = false

    fun init(context: Context) {
        if (done) return
        done = true
        val c = Configuration.getInstance()
        c.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        // OpenStreetMap's tile policy asks every app to send its own user agent.
        c.userAgentValue = "${BuildConfig.APPLICATION_ID}/${BuildConfig.VERSION_NAME}"
        val base = File(context.cacheDir, "osmdroid")
        c.osmdroidBasePath = base
        c.osmdroidTileCache = File(base, "tiles")
    }
}

/** Highest zoom used when fitting a route, so a very short one doesn't zoom in to street-sign level. */
private const val MAX_FIT_ZOOM = 18.0

/**
 * A recorded route on an OpenStreetMap map. With [follow] the map stays on the latest point (while recording);
 * otherwise it zooms to show the whole route. The map can be panned and pinched; tiles need a connection, and
 * without one the route is drawn on an empty background.
 */
@Composable
fun RouteMap(points: List<GpsPoint>, modifier: Modifier = Modifier, follow: Boolean = false) {
    val context = LocalContext.current
    val map = remember {
        OsmSetup.init(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(17.0)
            overlays.add(CopyrightOverlay(context))
        }
    }
    val line = remember {
        Polyline(map).apply {
            outlinePaint.color = Accent.toArgb()
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            map.overlays.add(this)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { mv ->
            val geo = points.map { GeoPoint(it.lat, it.lon) }
            line.setPoints(geo)
            when {
                geo.isEmpty() -> Unit
                follow || geo.size < 2 -> mv.controller.setCenter(geo.last())
                else -> fitRoute(mv, geo)
            }
            mv.invalidate()
        },
    )
}

private fun fitRoute(mv: MapView, geo: List<GeoPoint>) {
    val box = BoundingBox.fromGeoPoints(geo)
    val fit = {
        mv.zoomToBoundingBox(box, false, 64)
        if (mv.zoomLevelDouble > MAX_FIT_ZOOM) mv.controller.setZoom(MAX_FIT_ZOOM)
    }
    if (mv.width > 0 && mv.height > 0) fit() else mv.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
}
