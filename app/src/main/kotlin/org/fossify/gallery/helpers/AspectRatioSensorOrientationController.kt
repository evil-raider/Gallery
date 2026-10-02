package org.fossify.gallery.helpers

import android.app.Activity
import android.os.Build
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View

/**
 * Drives the "Aspect ratio and device rotation" screen-rotation mode.
 *
 * The media is kept locked in the orientation that lets it fill the screen
 * (landscape for wide media, portrait for tall media), exactly like the plain
 * "Aspect ratio" mode, so the media never rotates and never gets letterboxed.
 *
 * On top of that, the provided overlay control views (toolbar items, bottom
 * action icons, ...) are rotated to follow the device's physical orientation,
 * so the buttons stay upright for the user regardless of how the phone is held,
 * while the media that fills the screen stays put.
 */
class AspectRatioSensorOrientationController(
    private val activity: Activity,
    private val isOrientationLocked: () -> Boolean,
    private val controlViews: () -> List<View>,
) {
    private var orientationListener: OrientationEventListener? = null
    private var active = false
    private var currentControlsRotation = 0

    /**
     * Lock the activity to [fillOrientation] (one of
     * [android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE] or
     * [android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]) so the
     * media fills the screen, then start rotating the overlay controls to match
     * the device's physical orientation.
     */
    fun fillThenRotateControls(fillOrientation: Int) {
        if (isOrientationLocked()) {
            return
        }

        activity.requestedOrientation = fillOrientation
        active = true
        ensureListener()
        enableListener()
    }

    fun onResume() {
        if (active) {
            enableListener()
        }
    }

    fun onPause() {
        orientationListener?.disable()
    }

    fun destroy() {
        orientationListener?.disable()
        orientationListener = null
        active = false
    }

    /** Stop following the sensor and reset the controls, e.g. when the user manually locks the orientation. */
    fun cancel() {
        if (!active) {
            return
        }
        active = false
        orientationListener?.disable()
        resetControls()
    }

    private fun enableListener() {
        orientationListener?.takeIf { it.canDetectOrientation() }?.enable()
    }

    private fun ensureListener() {
        if (orientationListener != null) {
            return
        }

        orientationListener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(angle: Int) {
                if (!active || angle == ORIENTATION_UNKNOWN || isOrientationLocked()) {
                    return
                }

                val snapped = ((angle + 45) / 90 * 90) % 360
                // The activity is locked, so the UI is rendered rotated by this amount
                // relative to the device's natural orientation.
                val displayDegrees = currentDisplayRotationDegrees()
                // Rotation that makes the controls appear upright for the user.
                // CONTROLS_ROTATION_SIGN flips the direction if it ever comes out mirrored on a device.
                var target = (CONTROLS_ROTATION_SIGN * (displayDegrees - snapped)) % 360
                if (target < 0) {
                    target += 360
                }
                if (target > 180) {
                    target -= 360
                }

                if (target != currentControlsRotation) {
                    currentControlsRotation = target
                    applyControlsRotation(target.toFloat())
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotationDegrees(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation
        } else {
            activity.windowManager.defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun applyControlsRotation(degrees: Float) {
        controlViews().forEach { view ->
            view.animate().rotation(degrees).setDuration(ROTATION_ANIM_MS).start()
        }
    }

    private fun resetControls() {
        currentControlsRotation = 0
        controlViews().forEach { view ->
            view.animate().rotation(0f).setDuration(ROTATION_ANIM_MS).start()
        }
    }

    companion object {
        private const val ROTATION_ANIM_MS = 200L

        // Set to -1 if, on a real device, the controls rotate the wrong way.
        private const val CONTROLS_ROTATION_SIGN = 1
    }
}
