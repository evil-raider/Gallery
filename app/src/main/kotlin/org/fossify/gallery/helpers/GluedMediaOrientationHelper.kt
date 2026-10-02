package org.fossify.gallery.helpers

import android.view.View
import android.view.ViewGroup

/**
 * Helper for the "Aspect ratio and device rotation" screen-rotation mode.
 *
 * In that mode the activity itself rotates freely with the device sensor, so the
 * system bars, the notification shade and the app's own controls all rotate
 * normally. To keep the media (photo/video) "glued" in the orientation that lets
 * it fill the screen, the view that holds the media is rotated back by 90° and
 * resized to the swapped (fill) dimensions whenever the window orientation does
 * not match the media's fill orientation. Because the holder is resized to the
 * swapped dimensions, the media still fills the screen exactly, without cropping.
 */
object GluedMediaOrientationHelper {

    // Flip to -1 if, on a real device, the glued media rotates to the wrong side.
    const val GLUE_ROTATION_SIGN = 1

    /**
     * Glue [mediaView] so its content keeps the [fillLandscape] orientation and
     * fills a [containerWidth] x [containerHeight] window, regardless of the
     * current window orientation.
     */
    fun apply(
        mediaView: View,
        containerWidth: Int,
        containerHeight: Int,
        fillLandscape: Boolean,
    ) {
        if (containerWidth <= 0 || containerHeight <= 0) {
            return
        }

        val windowLandscape = containerWidth > containerHeight
        val layoutParams = mediaView.layoutParams
        if (windowLandscape == fillLandscape) {
            // Window already matches the fill orientation: show the media normally.
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            mediaView.layoutParams = layoutParams
            mediaView.translationX = 0f
            mediaView.translationY = 0f
            mediaView.rotation = 0f
        } else {
            // Lay the media out at the swapped (fill) size and rotate it back by 90°,
            // so it fills the screen in the fill orientation while the window rotates.
            layoutParams.width = containerHeight
            layoutParams.height = containerWidth
            mediaView.layoutParams = layoutParams
            mediaView.translationX = (containerWidth - containerHeight) / 2f
            mediaView.translationY = (containerHeight - containerWidth) / 2f
            mediaView.rotation = 90f * GLUE_ROTATION_SIGN
        }
    }

    /** Remove any glue transform, restoring a normal match-parent media view. */
    fun reset(mediaView: View) {
        val layoutParams = mediaView.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        mediaView.layoutParams = layoutParams
        mediaView.translationX = 0f
        mediaView.translationY = 0f
        mediaView.rotation = 0f
    }
}
