package org.fossify.gallery.helpers

import android.app.Activity
import android.graphics.Point
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.view.WindowManager

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

// No rotation animation while glued: the system would otherwise spin the old frame by 90° together
// with the window (the media visibly "rotates with the screen" and then snaps back). SEAMLESS keeps
// the content physically still (like a camera viewfinder) and falls back to a cross-fade/jump-cut
// where seamless is not possible (e.g. 180° flips), so the media never visibly turns.
fun Activity.setSeamlessRotation(enabled: Boolean) {
    val wanted = if (enabled) {
        WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
    } else {
        WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE
    }
    val attributes = window.attributes
    if (attributes.rotationAnimation != wanted) {
        attributes.rotationAnimation = wanted
        window.attributes = attributes
    }
}

// Frame size that matches the CURRENT display orientation. Right after a turn the views can still
// report their old size until the next layout; swapping it keeps the first frame already correct.
@Suppress("DEPRECATION")
fun Activity.currentOrientedSize(width: Int, height: Int): Point {
    val real = Point()
    windowManager.defaultDisplay.getRealSize(real)
    if (width == 0 || height == 0) {
        return real
    }
    val displayLandscape = real.x > real.y
    return if ((width > height) == displayLandscape) Point(width, height) else Point(height, width)
}

// Lays out a RelativeLayout child for gluing: in a 90°/270° rotation it gets the swapped size
// (frameHeight x frameWidth), centered, and is rotated around its center so it fills the frame.
// RelativeLayout clamps a child to its own bounds (a 2000px-wide child in a 900px-wide parent
// becomes 900px -> a square -> the media shrank with bars on all 4 sides). Negative margins
// enlarge the available space so the child really gets the swapped size.
// Returns true when the layout size changed.
fun View.applyGlueLayout(rotation: Int, frameWidth: Int, frameHeight: Int): Boolean {
    val params = layoutParams as RelativeLayout.LayoutParams
    val swapped = rotation % 180 != 0 && frameWidth > 0 && frameHeight > 0
    val newWidth = if (swapped) frameHeight else ViewGroup.LayoutParams.MATCH_PARENT
    val newHeight = if (swapped) frameWidth else ViewGroup.LayoutParams.MATCH_PARENT
    val marginX = if (swapped) minOf(0, (frameWidth - frameHeight) / 2) else 0
    val marginY = if (swapped) minOf(0, (frameHeight - frameWidth) / 2) else 0
    val changed = params.width != newWidth || params.height != newHeight ||
        params.leftMargin != marginX || params.topMargin != marginY
    if (changed) {
        params.width = newWidth
        params.height = newHeight
        params.setMargins(marginX, marginY, marginX, marginY)
        params.marginStart = marginX
        params.marginEnd = marginX
        params.addRule(RelativeLayout.CENTER_IN_PARENT)
        layoutParams = params
    }
    this.rotation = rotation.toFloat()
    return changed
}
