package eu.kanade.tachiyomi.ui.base

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller
import com.google.android.material.appbar.AppBarLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.view.backgroundColor
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class BigAppBarLayout@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppBarLayout(context, attrs) {

    var cardToolbar: FloatingToolbar? = null
    var cardFrame: FrameLayout? = null
    var mainToolbar: CenteredToolbar? = null
    var bigTitleView: TextView? = null
    val preferences: PreferencesHelper by injectLazy()
    var bigView: View? = null
    var imageView: ImageView? = null
    private var tabsFrameLayout: FrameLayout? = null
    var mainActivity: MainActivity? = null
    var toolbarMode = ToolbarState.BIG
        set(value) {
            field = value
            if (value == ToolbarState.SEARCH) {
                mainToolbar?.isGone = true
            } else if (value == ToolbarState.MAIN) {
                mainToolbar?.alpha = 1f
                mainToolbar?.isVisible = true
            }
            if (value != ToolbarState.BIG) {
                mainToolbar?.translationY = 0f
                y = 0f
            }
        }
    var useTabsInPreLayout = false
    var yAnimator: ViewPropertyAnimator? = null

    enum class ToolbarState {
        BIG,
        MAIN,
        SEARCH,
    }

    fun setToolbarModeBy(controller: Controller, useSmall: Boolean? = null) {
        toolbarMode = if (useSmall ?: !preferences.useLargeToolbar()) {
            when (controller) {
                is FloatingSearchInterface -> ToolbarState.SEARCH
                else -> ToolbarState.MAIN
            }
        } else {
            when (controller) {
                is SmallToolbarInterface -> {
                    if (controller is FloatingSearchInterface) {
                        ToolbarState.SEARCH
                    } else {
                        ToolbarState.MAIN
                    }
                }
                else -> ToolbarState.BIG
            }
        }
    }

    fun hideBigView(useSmall: Boolean, force: Boolean? = null, setTitleAlpha: Boolean = true) {
        val useSmallAnyway = force ?: (useSmall || !preferences.useLargeToolbar())
        bigView?.isGone = useSmallAnyway
        if (useSmallAnyway) {
            mainToolbar?.backgroundColor = null
            val toolbarTextView = mainToolbar?.toolbarTitle ?: return
            if (!setTitleAlpha) return
            toolbarTextView.setTextColor(
                ColorUtils.setAlphaComponent(
                    toolbarTextView.currentTextColor,
                    255
                )
            )
        }
    }

    private val minTabletHeight: Int
        get() {
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            return if (context.isTablet()) {
                (mainToolbar?.height ?: 0) + paddingTop + tabHeight
            } else {
                0
            }
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        bigTitleView = findViewById(R.id.big_title)
        cardToolbar = findViewById(R.id.card_toolbar)
        mainToolbar = findViewById(R.id.toolbar)
        bigView = findViewById(R.id.big_toolbar)
        cardFrame = findViewById(R.id.card_frame)
        tabsFrameLayout = findViewById(R.id.tabs_frame_layout)
        imageView = findViewById(R.id.big_icon)
        updateBigView(resources.configuration)
    }

    fun setTitle(title: CharSequence?) {
        bigTitleView?.text = title
        mainToolbar?.title = title
    }

    override fun setTranslationY(translationY: Float) {
        val realHeight = (preLayoutHeight + paddingTop).toFloat()
        val newY = MathUtils.clamp(translationY, -realHeight + minTabletHeight, 0f)
        super.setTranslationY(newY)
    }

    private val toolbarHeight: Float
        get() {
            val appBarHeight = if (mainToolbar?.height ?: 0 > 0) {
                mainToolbar?.height ?: 0
            } else {
                val attrsArray = intArrayOf(R.attr.mainActionBarSize)
                val array = context.obtainStyledAttributes(attrsArray)
                val height = array.getDimensionPixelSize(0, 0)
                array.recycle()
                height
            }
            return (appBarHeight + paddingTop).toFloat()
        }

    val toolbarDistance: Int
        get() {
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            return paddingTop - (mainToolbar?.height ?: 0) - tabHeight
        }

    val recyclerOffset: Int
        get() {
            val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
            return -preLayoutHeight + (mainToolbar?.height ?: 0) + tabHeight
        }

    val preLayoutHeight: Int
        get() = getEstimatedLayout(
            cardFrame?.isVisible == true && toolbarMode == ToolbarState.BIG,
            useTabsInPreLayout,
            toolbarMode == ToolbarState.BIG
        )

    fun getEstimatedLayout(includeSearchToolbar: Boolean, includeTabs: Boolean, includeLargeToolbar: Boolean): Int {
        val hasLargeToolbar = includeLargeToolbar && preferences.useLargeToolbar()
        val attrsArray = intArrayOf(R.attr.mainActionBarSize)
        val array = context.obtainStyledAttributes(attrsArray)
        val appBarHeight = (
            array.getDimensionPixelSize(0, 0) *
                (if (includeSearchToolbar && hasLargeToolbar) 2 else 1)
            )
        val widthMeasureSpec = MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST)
        val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        bigTitleView?.measure(widthMeasureSpec, heightMeasureSpec)
        val textHeight = max(bigTitleView?.height ?: 0, bigTitleView?.measuredHeight ?: 0) +
            (bigTitleView?.marginTop?.plus(bigView?.paddingBottom ?: 0) ?: 0)
        array.recycle()
        return appBarHeight + (if (hasLargeToolbar) textHeight else 0) +
            if (includeTabs) 48.dpToPx else 0
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateBigView(newConfig)
    }

    private fun updateBigView(config: Configuration?) {
        config ?: return
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            config.screenWidthDp >= 720
        ) {
            val bigTitleView = bigTitleView ?: return
            val isTablet = config.smallestScreenWidthDp >= 600
            val attrs = intArrayOf(
                if (isTablet) R.attr.textAppearanceHeadlineLarge
                else R.attr.textAppearanceHeadlineMedium
            )
            val ta = context.obtainStyledAttributes(attrs)
            val resId = ta.getResourceId(0, 0)
            ta.recycle()
            TextViewCompat.setTextAppearance(bigTitleView, resId)
            bigTitleView.setTextColor(context.getResourceColor(R.attr.actionBarTintColor))
            bigTitleView.updateLayoutParams<MarginLayoutParams> {
                topMargin = if (isTablet) 52.dpToPx else 12.dpToPx
            }
            imageView?.updateLayoutParams<MarginLayoutParams> {
                height = if (isTablet) 52.dpToPx else 48.dpToPx
                width = if (isTablet) 52.dpToPx else 48.dpToPx
            }
        }
    }
    fun updateViewsAfterY(recyclerView: RecyclerView, cancelAnim: Boolean = true) {
        if (cancelAnim) {
            yAnimator?.cancel()
        }
        val offset = recyclerView.computeVerticalScrollOffset()
        val bigHeight = bigView?.height ?: 0
        val realHeight = preLayoutHeight + paddingTop
        val tabHeight = if (tabsFrameLayout?.isVisible == true) 48.dpToPx else 0
        val smallHeight = -realHeight + toolbarHeight + tabHeight
        val newY = if (offset < realHeight - toolbarHeight - tabHeight) {
            -offset.toFloat()
        } else {
            MathUtils.clamp(
                translationY,
                -realHeight.toFloat() + top + minTabletHeight,
                max(
                    smallHeight,
                    if (offset > realHeight - toolbarHeight - tabHeight) smallHeight else min(-offset.toFloat(), 0f)
                ) + top.toFloat()
            )
        }

        translationY = newY
        mainToolbar?.let { mainToolbar ->
            mainToolbar.translationY = when {
                toolbarMode != ToolbarState.BIG -> 0f
                -newY <= bigHeight -> max(-newY, 0f)
                else -> bigHeight.toFloat()
            }
        }
        if (toolbarMode != ToolbarState.BIG) {
            setToolbar(offset > realHeight - toolbarHeight - tabHeight)
            return
        }
        val alpha = (bigHeight + newY * 2) / (bigHeight) + 0.45f // (realHeight.toFloat() + newY * 5) / realHeight.toFloat() + .33f
        bigView?.alpha = MathUtils.clamp(if (alpha.isNaN()) 1f else alpha, 0f, 1f)
        val toolbarTextView = mainToolbar?.toolbarTitle ?: return
        toolbarTextView.setTextColor(
            ColorUtils.setAlphaComponent(
                toolbarTextView.currentTextColor,
                (MathUtils.clamp((1 - ((if (alpha.isNaN()) 1f else alpha) + 0.95f)) * 2, 0f, 1f) * 255)
                    .roundToInt()
            )
        )
        mainToolbar?.alpha = MathUtils.clamp(
            (y + (mainToolbar?.y ?: 0f)) / paddingTop,
            0f,
            1f
        )
        setToolbar(mainToolbar?.alpha ?: 0f <= 0f)
    }

    fun snapAppBarY(recyclerView: RecyclerView, callback: (() -> Unit)?): Float {
        yAnimator?.cancel()
        val halfWay = toolbarHeight / 2
        val shortAnimationDuration = resources?.getInteger(
            android.R.integer.config_longAnimTime
        ) ?: 0
        val realHeight = preLayoutHeight + paddingTop
        val closerToTop = abs(y) > realHeight - halfWay
        val atTop = !recyclerView.canScrollVertically(-1)
        val lastY = if (closerToTop && !atTop) {
            -height.toFloat()
        } else {
            toolbarHeight
        }

        val onFirstItem = recyclerView.computeVerticalScrollOffset() < realHeight - toolbarHeight

        return if (!onFirstItem) {
            yAnimator = animate().y(lastY)
                .setDuration(shortAnimationDuration.toLong())
            yAnimator?.setUpdateListener {
                updateViewsAfterY(recyclerView, false)
                callback?.invoke()
            }
            yAnimator?.start()
            setToolbar(true)
            lastY
        } else {
            setToolbar(mainToolbar?.alpha ?: 0f <= 0f)
            y
        }
    }

    fun setToolbar(showCardTB: Boolean) {
        val mainActivity = mainActivity ?: return
        if ((showCardTB || toolbarMode == ToolbarState.SEARCH) && cardFrame?.isVisible == true) {
            if (mainActivity.currentToolbar != cardToolbar) {
                mainActivity.setFloatingToolbar(true, showSearchAnyway = true)
            }
            if (mainActivity.currentToolbar == cardToolbar) {
                if (toolbarMode == ToolbarState.BIG) {
                    mainToolbar?.isInvisible = true
                }
                mainToolbar?.backgroundColor = null
                cardFrame?.backgroundColor = null
            }
        } else {
            if (mainActivity.currentToolbar != mainToolbar) {
                mainActivity.setFloatingToolbar(false, showSearchAnyway = true)
            }
            if (toolbarMode == ToolbarState.BIG) {
                mainToolbar?.isInvisible = false
            }
            if (tabsFrameLayout?.isVisible == false) {
                cardFrame?.backgroundColor = mainActivity.getResourceColor(R.attr.colorSurface)
            } else {
                cardFrame?.backgroundColor = null
            }
        }
    }
}

interface SmallToolbarInterface
