package org.fossify.gallery.helpers

import android.app.Activity
import android.content.Context
import android.graphics.Matrix
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RelativeLayout
import androidx.core.net.toUri
import org.fossify.commons.extensions.getAndroidSAFUri
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot

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

// DisplayListener.onDisplayChanged also fires for refresh-rate switches (60/90/120 Hz, frame-rate
// matching during video), HDR/brightness-related changes and other displays. Re-gluing on every such
// event would force relayouts/redraws for nothing, so only report actual rotation changes of the
// display this activity is on.
// Shared by the display listener and onConfigurationChanged: whichever reports a new rotation first
// handles it, the other one sees no change and skips the duplicate re-glue.
class DisplayRotationFilter {
    private var lastRotation = -1

    @Suppress("DEPRECATION")
    fun isRotationChange(activity: Activity, displayId: Int = activity.windowManager.defaultDisplay.displayId): Boolean {
        val display = activity.windowManager.defaultDisplay
        if (displayId != display.displayId) {
            return false
        }
        val rotation = display.rotation
        if (rotation == lastRotation) {
            return false
        }
        lastRotation = rotation
        return true
    }

    // Forget the last rotation, e.g. on resume: the device may have turned while the listener was off.
    fun reset() {
        lastRotation = -1
    }
}

/**
 * Real display size of a video (rotation metadata applied), read with a single metadata retriever.
 * A portrait phone video is usually stored as landscape + 90° rotation, so the coded size alone
 * would be mistaken for a landscape video. Handles plain paths, content:// URIs and SAF-only roots.
 */
fun Context.getVideoDisplaySize(path: String): Point? {
    val retriever = MediaMetadataRetriever()
    return try {
        when {
            path.startsWith("content://", true) -> retriever.setDataSource(this, path.toUri())
            isRestrictedSAFOnlyRoot(path) -> retriever.setDataSource(this, getAndroidSAFUri(path))
            else -> retriever.setDataSource(path)
        }
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        when {
            width <= 0 || height <= 0 -> null
            rotation % 180 != 0 -> Point(height, width)
            else -> Point(width, height)
        }
    } catch (ignored: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (ignored: Exception) {
        }
    }
}

/**
 * Glues a video drawn into [texture] inside [frame]: the texture fills the frame and the content is
 * un-stretched, counter-rotated by the display rotation and scaled to fit, centered.
 * Returns the applied rotation (0/90/180/270), or null when the frame is not laid out yet (it then
 * retries by itself after the next layout and calls [onApplied]) or the video size is unknown.
 */
fun Activity.applyTextureGlue(
    frame: View,
    texture: TextureView,
    videoWidth: Int,
    videoHeight: Int,
    onApplied: ((rotation: Int, width: Int, height: Int) -> Unit)? = null
): Int? {
    if (frame.width == 0 || frame.height == 0) {
        frame.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                frame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (!isDestroyed && frame.width > 0 && frame.height > 0) {
                    applyTextureGlue(frame, texture, videoWidth, videoHeight, onApplied)
                }
            }
        })
        return null
    }

    // Right after a turn the frame still reports its old size until the next layout. Use the size
    // matching the current display orientation, so the very first frame is already glued.
    val size = currentOrientedSize(frame.width, frame.height)
    val w = size.x
    val h = size.y

    val params = texture.layoutParams
    if (params.width != w || params.height != h) {
        params.width = w
        params.height = h
        texture.layoutParams = params
    }

    if (videoWidth <= 1 || videoHeight <= 1) {
        return null
    }

    val vw = videoWidth.toFloat()
    val vh = videoHeight.toFloat()
    val fillLandscape = when {
        vw > vh -> true
        vw < vh -> false
        else -> null
    }
    // Same formula as photos: counter-rotate by the display rotation, so the video keeps one
    // orientation relative to the physical screen and never flips 180°.
    val rotation = getGlueRotation(fillLandscape)
    val swapped = rotation % 180 != 0
    val scale = if (!swapped) minOf(w / vw, h / vh) else minOf(w / vh, h / vw)
    val matrix = Matrix().apply {
        postTranslate(-w / 2f, -h / 2f)
        postScale(vw * scale / w, vh * scale / h)
        postRotate(rotation.toFloat())
        postTranslate(w / 2f, h / 2f)
    }
    texture.setTransform(matrix)
    onApplied?.invoke(rotation, w, h)
    return rotation
}
