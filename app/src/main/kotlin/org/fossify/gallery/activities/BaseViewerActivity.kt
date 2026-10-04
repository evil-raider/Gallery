package org.fossify.gallery.activities

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.RelativeLayout
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
    private val landscapeTitleSyncListener = ViewTreeObserver.OnPreDrawListener {
        syncLandscapeTitleVisibility()
        true
    }

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
        applyViewerTitle()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (landscapeTitleView != null) {
            contentHolder.viewTreeObserver.removeOnPreDrawListener(landscapeTitleSyncListener)
        }
    }

    /**
     * In portrait the title is shown in the toolbar as usual. In landscape it is moved out of the toolbar
     * and shown centered at the very top edge of the screen, over the status bar.
     */
    fun setViewerTitle(title: CharSequence) {
        viewerTitle = title
        applyViewerTitle()
    }

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
        val parent = contentHolder as ViewGroup
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
        }

        val params = if (parent is RelativeLayout) {
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
                addRule(RelativeLayout.CENTER_HORIZONTAL)
            }
        } else {
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        parent.addView(view, params)
        parent.viewTreeObserver.addOnPreDrawListener(landscapeTitleSyncListener)
        landscapeTitleView = view
        return view
    }

    // keep the separate title in sync with the toolbar fading in/out on fullscreen toggling
    private fun syncLandscapeTitleVisibility() {
        val view = landscapeTitleView ?: return
        val shown = isLandscape() && appBarLayout.visibility == View.VISIBLE && viewerToolbar.visibility == View.VISIBLE
        val newVisibility = if (shown) View.VISIBLE else View.GONE
        if (view.visibility != newVisibility) {
            view.visibility = newVisibility
        }

        val newAlpha = appBarLayout.alpha * viewerToolbar.alpha
        if (view.alpha != newAlpha) {
            view.alpha = newAlpha
        }
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
