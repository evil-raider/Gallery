package org.fossify.gallery.helpers

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener

/**
 * Drives the "Aspect ratio and device rotation" screen-rotation mode.
 *
 * When a medium is shown, the activity is first forced into the orientation that
 * lets the media fill the screen (landscape for wide media, portrait for tall
 * media). As soon as the user physically rotates the device away from the
 * orientation it had when the media was shown, control is handed over to the
 * full device sensor, so the UI can then rotate freely in any direction,
 * independent of the media that originally filled the screen.
 */
class AspectRatioSensorOrientationController(
    private val activity: Activity,
    private val isOrientationLocked: () -> Boolean,
) {
    private var orientationListener: OrientationEventListener? = null
    private var armed = false
    private var baselineBucket = BUCKET_UNKNOWN

    /**
     * Force [fillOrientation] (one of [ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE]
     * or [ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]) so the media fills the
     * screen, then start following the device sensor.
     */
    fun fillThenFollowSensor(fillOrientation: Int) {
        if (isOrientationLocked()) {
            return
        }

        activity.requestedOrientation = fillOrientation
        armed = true
        baselineBucket = BUCKET_UNKNOWN
        ensureListener()
        enableListener()
    }

    fun onResume() {
        if (armed) {
            enableListener()
        }
    }

    fun onPause() {
        orientationListener?.disable()
    }

    fun destroy() {
        orientationListener?.disable()
        orientationListener = null
        armed = false
    }

    /** Stop handing control to the sensor, e.g. when the user manually locks the orientation. */
    fun cancel() {
        armed = false
        orientationListener?.disable()
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
                if (!armed || angle == ORIENTATION_UNKNOWN || isOrientationLocked()) {
                    return
                }

                val bucket = angleToBucket(angle)
                if (baselineBucket == BUCKET_UNKNOWN) {
                    // Remember how the device was held when the media filled the screen,
                    // so forcing the fill orientation does not immediately count as a rotation.
                    baselineBucket = bucket
                    return
                }

                if (bucket != baselineBucket) {
                    armed = false
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
            }
        }
    }

    private fun angleToBucket(angle: Int): Int = when {
        angle >= 315 || angle < 45 -> 0
        angle < 135 -> 1
        angle < 225 -> 2
        else -> 3
    }

    companion object {
        private const val BUCKET_UNKNOWN = -1
    }
}
