package org.fossify.gallery.activities

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.fossify.commons.extensions.updateMarginWithBase
import org.fossify.commons.extensions.updatePaddingWithBase
import org.fossify.gallery.extensions.config

abstract class BaseViewerActivity : SimpleActivity() {
    override val padCutout: Boolean = false
    abstract val contentHolder: View
    abstract val appBarLayout: AppBarLayout
    abstract val viewerToolbar: MaterialToolbar

    private var viewerTitle: CharSequence = ""
    private var landscapeTitleView: TextView? = null
    private var statusBarHeight = 0
    private var currentOrientation = Configuration.ORIENTATION_UNDEFINED
    private var isViewerChromeVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contentRoot = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { _, insets ->
            setupEdgeToEdge(insets)
            insets
        }
        registerShowNotchCollector(contentRoot)
    }

    private fun registerShowNotchCollector(view: View) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                config.showNotchFlow.collect {
                    view.requestApplyInsets()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentOrientation = newConfig.orientation
        applyViewerTitle()
    }

    /**
     * In portrait the title is shown in the toolbar as usual. In landscape it is moved out of the toolbar
     * and shown centered at the very top edge of the screen, over the status bar.
     */
    fun setViewerTitle(title: CharSequence) {
        viewerTitle = title
        applyViewerTitle()
    }

    private fun isLandscape(): Boolean {
        if (currentOrientation == Configuration.ORIENTATION_UNDEFINED) {
            currentOrientation = resources.configuration.orientation
        }
        return currentOrientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun applyViewerTitle() {
        val landscape = isLandscape()
        viewerToolbar.title = if (landscape) "" else viewerTitle
        if (landscape) {
            getOrCreateLandscapeTitleView().text = viewerTitle
        }
        syncLandscapeTitleVisibility()
    }

    private fun getOrCreateLandscapeTitleView(): TextView {
        landscapeTitleView?.let { return it }
        // Added to the window content root (not the activity layout), so it is not affected by the
        // app bar / cutout paddings and is centered relative to the whole screen width.
        val parent = findViewById<FrameLayout>(android.R.id.content)
        val maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 360f, resources.displayMetrics).toInt()
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MIDDLE
            gravity = Gravity.CENTER
            maxWidth = maxWidthPx
            minimumHeight = statusBarHeight
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )

        parent.addView(view, params)
        landscapeTitleView = view
        return view
    }

    /**
     * Fades the separate landscape title together with the top bar. Called by the viewers right where
     * they animate their app bar / toolbar on fullscreen toggling (no per-frame syncing needed).
     */
    fun animateViewerTitle(visible: Boolean) {
        isViewerChromeVisible = visible
        val view = landscapeTitleView ?: return
        if (!isLandscape()) {
            view.visibility = View.GONE
            return
        }

        val newAlpha = if (visible) 1f else 0f
        view.animate().cancel()
        view.animate().alpha(newAlpha).withStartAction {
            view.visibility = View.VISIBLE
        }.withEndAction {
            view.visibility = if (visible) View.VISIBLE else View.GONE
        }.start()
    }

    private fun syncLandscapeTitleVisibility() {
        val view = landscapeTitleView ?: return
        view.animate().cancel()
        val shown = isLandscape() && isViewerChromeVisible
        view.visibility = if (shown) View.VISIBLE else View.GONE
        view.alpha = if (shown) 1f else 0f
    }

    private fun setupEdgeToEdge(insets: WindowInsetsCompat) {
        statusBarHeight = insets.getInsetsIgnoringVisibility(Type.statusBars()).top
        landscapeTitleView?.minimumHeight = statusBarHeight

        if (config.showNotch) {
            val systemAndCutout =
                insets.getInsetsIgnoringVisibility(Type.systemBars() or Type.displayCutout())
            appBarLayout.updatePaddingWithBase(
                top = systemAndCutout.top,
                left = systemAndCutout.left,
                right = systemAndCutout.right
            )

            contentHolder.updatePaddingWithBase(left = 0, top = 0, right = 0, bottom = 0)
        } else {
            val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
            val cutout = insets.getInsetsIgnoringVisibility(Type.displayCutout())
            appBarLayout.updatePaddingWithBase(
                top = if (cutout.top > 0) 0 else system.top,
                left = if (cutout.left > 0) 0 else system.left,
                right = if (cutout.right > 0) 0 else system.right
            )

            contentHolder.updatePaddingWithBase(
                left = cutout.left,
                top = cutout.top,
                right = cutout.right,
                bottom = cutout.bottom
            )
        }
    }

    fun applyProperHorizontalInsets(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            if (config.showNotch) {
                val systemAndCutout =
                    insets.getInsetsIgnoringVisibility(Type.systemBars() or Type.displayCutout())
                view.updateMarginWithBase(
                    left = systemAndCutout.left,
                    right = systemAndCutout.right
                )
            } else {
                val system = insets.getInsetsIgnoringVisibility(Type.systemBars())
                val cutout = insets.getInsetsIgnoringVisibility(Type.displayCutout())
                view.updateMarginWithBase(
                    left = if (cutout.left > 0) 0 else system.left,
                    right = if (cutout.right > 0) 0 else system.right
                )
            }
            insets
        }
    }
}
