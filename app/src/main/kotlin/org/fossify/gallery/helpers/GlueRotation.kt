package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Point
import android.view.Surface

/**
 * Clockwise rotation (0/90/180/270) to apply to media content, relative to the window, so it stays
 * glued to the physical screen in the "aspect ratio + device rotation" mode while the window itself
 * rotates with the device.
 *
 * Display.rotation R means the window is drawn rotated R degrees clockwise relative to the panel,
 * so the content must be counter-rotated by -R. On top of that, a base 90° is added when the media's
 * fill orientation differs from the device's natural orientation. Result: base - R.
 *
 * A single formula for every rotation keeps photos, video previews and playing videos consistent and
 * never flips anything 180°: the media keeps the same orientation relative to the physical screen.
 */
fun computeGlueRotation(fillLandscape: Boolean?, displayLandscape: Boolean, displayRotation: Int): Int {
    val displayDegrees = when (displayRotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    val naturalLandscape = if (displayDegrees % 180 == 0) displayLandscape else !displayLandscape
    val base = if (fillLandscape == null || fillLandscape == naturalLandscape) 0 else 90
    return ((base - displayDegrees) % 360 + 360) % 360
}

// Reads the size and rotation from the same Display, so both always describe the same moment
// (no mismatch between a stale Configuration and a fresh display rotation during a turn).
@Suppress("DEPRECATION")
fun Activity.getGlueRotation(fillLandscape: Boolean?): Int {
    val display = windowManager.defaultDisplay
    val size = Point()
    display.getRealSize(size)
    return computeGlueRotation(fillLandscape, size.x > size.y, display.rotation)
}
