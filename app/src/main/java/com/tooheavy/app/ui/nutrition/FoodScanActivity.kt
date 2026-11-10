package com.tooheavy.app.ui.nutrition

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.camera.CameraInstance
import kotlin.math.roundToInt

/**
 * The barcode scanner with tap to focus, like the camera app: tapping the preview focuses (and meters) on that
 * spot right away and keeps it there, and a ring shows where. Otherwise ZXing's own scanner screen, which keeps
 * refocusing every two seconds.
 */
@Suppress("DEPRECATION") // ZXing embedded runs on the original Camera API.
class FoodScanActivity : CaptureActivity() {
    private var ring: View? = null
    private var ringSize = 0

    override fun initializeContent(): DecoratedBarcodeView {
        val scanner = super.initializeContent()
        val root = findViewById<ViewGroup>(android.R.id.content)
        val density = resources.displayMetrics.density
        ringSize = (72 * density).roundToInt()
        ring = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).roundToInt(), Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(ringSize, ringSize)
            alpha = 0f
        }
        root.addView(ring)
        scanner.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_UP && v.width > 0 && v.height > 0) {
                focusAt(scanner, e.x / v.width, e.y / v.height)
                val at = IntArray(2)
                root.getLocationOnScreen(at)
                showRing(e.rawX - at[0], e.rawY - at[1])
                v.performClick()
            }
            true
        }
        return scanner
    }

    /** Focuses and meters on the point at [nx], [ny] (0..1 across the preview). */
    private fun focusAt(scanner: DecoratedBarcodeView, nx: Float, ny: Float) {
        val instance = scanner.barcodeView.cameraInstance ?: return
        val rotation = runCatching { instance.cameraRotation }.getOrDefault(90)
        val area = Camera.Area(focusRect(nx, ny, rotation), 1000)
        scanner.changeCameraParameters { p ->
            if (p.maxNumFocusAreas > 0) p.focusAreas = listOf(area)
            if (p.maxNumMeteringAreas > 0) p.meteringAreas = listOf(area)
            p
        }
        // Then focus now rather than at ZXing's next two-second cycle; this runs on the camera thread after the
        // areas are set, like ZXing does when switching the torch.
        scanner.changeCameraParameters { p ->
            refocusNow(instance)
            p
        }
    }

    /** Restarts ZXing's auto-focus cycle, which focuses at once; skipped quietly if the library differs. */
    private fun refocusNow(instance: CameraInstance) {
        runCatching {
            val getManager = CameraInstance::class.java.getDeclaredMethod("getCameraManager").apply { isAccessible = true }
            val manager = getManager.invoke(instance) ?: return
            val field = manager.javaClass.getDeclaredField("autoFocusManager").apply { isAccessible = true }
            val autoFocus = field.get(manager) ?: return
            autoFocus.javaClass.getMethod("stop").invoke(autoFocus)
            autoFocus.javaClass.getMethod("start").invoke(autoFocus)
        }
    }

    private fun showRing(x: Float, y: Float) {
        val r = ring ?: return
        r.animate().cancel()
        r.translationX = x - ringSize / 2f
        r.translationY = y - ringSize / 2f
        r.scaleX = 1.4f
        r.scaleY = 1.4f
        r.alpha = 1f
        r.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction {
            r.animate().alpha(0f).setStartDelay(800).setDuration(300).start()
        }.start()
    }

    companion object {
        /** Half the side of the focus area, in the camera's -1000..1000 coordinates. */
        private const val HALF = 150

        /**
         * The focus area around a tap at [nx], [ny] (0..1 across the screen), in the camera's coordinates
         * (-1000..1000 along the sensor). The preview is shown turned by [rotation] degrees clockwise, so the
         * tap is turned back first.
         */
        fun focusRect(nx: Float, ny: Float, rotation: Int): Rect {
            val x = nx.coerceIn(0f, 1f)
            val y = ny.coerceIn(0f, 1f)
            val (sx, sy) = when ((rotation % 360 + 360) % 360) {
                90 -> y to 1 - x
                180 -> 1 - x to 1 - y
                270 -> 1 - y to x
                else -> x to y
            }
            val cx = (sx * 2000 - 1000).roundToInt()
            val cy = (sy * 2000 - 1000).roundToInt()
            val left = (cx - HALF).coerceIn(-1000, 1000 - 2 * HALF)
            val top = (cy - HALF).coerceIn(-1000, 1000 - 2 * HALF)
            return Rect(left, top, left + 2 * HALF, top + 2 * HALF)
        }
    }
}
