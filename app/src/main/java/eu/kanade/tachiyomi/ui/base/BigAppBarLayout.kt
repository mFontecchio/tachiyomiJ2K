package eu.kanade.tachiyomi.ui.base

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.backgroundColor
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
    var bigView: View? = null
    var tabsFrameLayout: FrameLayout? = null
    var smallToolbarMode = false
    var useTabsInPreLayout = false
    var yAnimator: ViewPropertyAnimator? = null

    fun hideBigView(useSmall: Boolean) {
        bigView?.isGone = useSmall
        if (useSmall) {
            mainToolbar?.backgroundColor = null
            val toolbarTextView = mainToolbar?.toolbarTitle ?: return
            toolbarTextView.setTextColor(
                ColorUtils.setAlphaComponent(
                    toolbarTextView.currentTextColor,
                    255
                )
            )
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
    }

    fun setTitle(title: CharSequence?) {
        bigTitleView?.text = title
        mainToolbar?.title = title
    }

    override fun setTranslationY(translationY: Float) {
        val realHeight = (preLayoutHeight + paddingTop).toFloat()
        val newY = MathUtils.clamp(translationY, -realHeight, 0f)
        super.setTranslationY(newY)
    }

    private val toolbarHeight: Float
        get() {
            val attrsArray = intArrayOf(R.attr.mainActionBarSize)
            val array = context.obtainStyledAttributes(attrsArray)
            val appBarHeight = (
                array.getDimensionPixelSize(0, 0)
                )
            array.recycle()
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
        get() {
            val attrsArray = intArrayOf(R.attr.mainActionBarSize)
            val array = context.obtainStyledAttributes(attrsArray)
            val appBarHeight = (
                array.getDimensionPixelSize(0, 0) *
                    (if (cardFrame?.isVisible == true && !smallToolbarMode) 2 else 1)
                )
            val widthMeasureSpec = MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST)
            val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            bigTitleView?.measure(widthMeasureSpec, heightMeasureSpec)
            val textHeight = (bigTitleView?.measuredHeight ?: 0) + 64.dpToPx
            array.recycle()
            return appBarHeight + if (smallToolbarMode) 0 else textHeight +
                if (useTabsInPreLayout) 48.dpToPx else 0
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
                -realHeight.toFloat() + top,
                max(
                    smallHeight,
                    if (offset > realHeight - toolbarHeight - tabHeight) smallHeight else min(-offset.toFloat(), 0f)
                ) + top.toFloat()
            )
        }
        setToolbar(offset > height - toolbarHeight - tabHeight)

        translationY = newY
        mainToolbar?.let { mainToolbar ->
            mainToolbar.translationY = when {
                smallToolbarMode -> 0f
                -newY <= bigHeight -> max(-newY, 0f)
                else -> bigHeight.toFloat()
            }
        }
        if (smallToolbarMode) return
        val alpha = MathUtils.clamp((realHeight.toFloat() + newY * 5) / realHeight.toFloat() + .33f, 0.0f, 1.0f)
        bigView?.alpha = if (alpha.isNaN()) 1f else alpha
        val alpha2 = MathUtils.clamp(-newY * 3 / realHeight.toFloat() - 0.33f, 0.0f, 1.0f)
        val toolbarTextView = mainToolbar?.toolbarTitle ?: return
        toolbarTextView.setTextColor(
            ColorUtils.setAlphaComponent(
                toolbarTextView.currentTextColor,
                ((if (alpha2.isNaN()) 0f else alpha2) * 255).roundToInt()
            )
        )
    }

    fun snapY(recyclerView: RecyclerView): Float {
        yAnimator?.cancel()
        val halfWay = toolbarHeight / 2
        val shortAnimationDuration = resources?.getInteger(
            android.R.integer.config_longAnimTime
        ) ?: 0
        val closerToTop = abs(y) > height - halfWay
        val atTop = !recyclerView.canScrollVertically(-1)
        val lastY = if (closerToTop && !atTop) {
            -height.toFloat()
        } else {
            toolbarHeight
        }

        val onFirstItem = recyclerView.computeVerticalScrollOffset() < height - toolbarHeight

        return if (!onFirstItem) {
            yAnimator = animate().y(lastY)
                .setDuration(shortAnimationDuration.toLong())
            yAnimator?.setUpdateListener {
                updateViewsAfterY(recyclerView, false)
            }
            yAnimator?.start()
            setToolbar(true)
            lastY
        } else {
            setToolbar(false)
            y
        }
    }

    fun setToolbar(showCardTB: Boolean) {
        val mainActivity = (context as? MainActivity) ?: return
        if (showCardTB) {
            if (mainActivity.currentToolbar != cardToolbar) {
                mainActivity.setFloatingToolbar(true, showSearchAnyway = true)
            }
            if (mainActivity.currentToolbar == cardToolbar) {
                mainToolbar?.isInvisible = true
                mainToolbar?.backgroundColor = null
                cardFrame?.backgroundColor = null
            }
        } else {
            if (mainActivity.currentToolbar != mainToolbar) {
                mainActivity.setFloatingToolbar(false, showSearchAnyway = true)
            }
            mainToolbar?.isInvisible = false

            if (tabsFrameLayout?.isVisible == false) {
                cardFrame?.backgroundColor = mainActivity.getResourceColor(R.attr.colorSurface)
            } else {
                cardFrame?.backgroundColor = null
            }
        }
    }
}
