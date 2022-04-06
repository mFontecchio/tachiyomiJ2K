package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.pxToDp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class AutofitRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    androidx.recyclerview.widget.RecyclerView(context, attrs) {

    val manager = GridLayoutManagerAccurateOffset(context, 1)

    var lastMeasuredWidth = 0
    var columnWidth = -1f
        set(value) {
            field = value
            if (measuredWidth > 0) {
                setSpan(true)
            }
        }

    var spanCount = 0
        set(value) {
            field = value
            if (value > 0) {
                manager.spanCount = value
            }
        }

    val itemWidth: Int
        get() {
            return if (spanCount == 0) measuredWidth / getTempSpan()
            else measuredWidth / manager.spanCount
        }

    init {
        layoutManager = manager
    }

    private fun getTempSpan(): Int {
        if (spanCount == 0 && columnWidth > 0) {
            val dpWidth = (measuredWidth.pxToDp / 100f).roundToInt()
            return max(1, (dpWidth / columnWidth).roundToInt())
        }
        return 3
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        setSpan()
        lastMeasuredWidth = measuredWidth
    }

    fun setGridSize(preferences: PreferencesHelper) {
        // Migrate to float for grid size
        if (preferences.gridSize().isNotSet()) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val oldGridSize = prefs.getInt("grid_size", -1)
            if (oldGridSize != -1) {
                preferences.gridSize().set(
                    when (oldGridSize) {
                        4 -> 3f
                        3 -> 1.5f
                        2 -> 1f
                        1 -> 0f
                        0 -> -.5f
                        else -> .5f
                    }
                )
                prefs.edit {
                    remove("grid_size")
                }
            }
        }

        val size = 1.5f.pow(preferences.gridSize().get())
        val trueSize = MULTIPLE * ((size * 100 / MULTIPLE).roundToInt()) / 100f
        columnWidth = trueSize
    }

    private fun setSpan(force: Boolean = false) {
        if ((spanCount == 0 || force || measuredHeight != lastMeasuredWidth) && columnWidth > 0) {
            val dpWidth = (measuredWidth.pxToDp / 100f).roundToInt()
            val count = max(1, (dpWidth / columnWidth).roundToInt())
            spanCount = count
        }
    }

    companion object {
        private const val MULTIPLE_PERCENT = 0.25f
        const val MULTIPLE = MULTIPLE_PERCENT * 100
    }
}

class GridLayoutManagerAccurateOffset(context: Context?, spanCount: Int) : GridLayoutManager(context, spanCount) {

    // map of child adapter position to its height.
    private val childSizesMap = mutableMapOf<Int, Int>()
    private val childSpanMap = mutableMapOf<Int, Int>()
    private val childTypeMap = mutableMapOf<Int, MutableMap<Int, Int>>()
    var rView: RecyclerView? = null

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: return
            val position = getPosition(child)
            childSizesMap[position] = child.height
            childSpanMap[position] = spanSizeLookup.getSpanSize(getPosition(child))
            childTypeMap[getItemViewType(child)] = (
                childTypeMap[getItemViewType(child)]?.also {
                    it[position] = child.height
                } ?: mutableMapOf(position to child.height)
                )
        }
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        rView = view
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val childAvgHeightMap = mutableMapOf<Int, Int>()
        var scrolledY = 0
        var spanC = 0
        var maxHeight = 0
        for (i in 0 until itemCount) {
            val height: Int = if (childSizesMap[i] != null) {
                childSizesMap[i] ?: 0
            } else {
                val type = rView?.adapter?.getItemViewType(i) ?: 0
                if (childAvgHeightMap[type] == null) {
                    childAvgHeightMap[type] = childTypeMap[type]?.values?.average()?.roundToInt() ?: 0
                }
                childAvgHeightMap[type] ?: 0
            }
            val spanCurrentSize = childSpanMap[i] ?: spanSizeLookup.getSpanSize(i)
            if (spanCount <= spanCurrentSize) {
                scrolledY += height
                scrolledY += maxHeight
                maxHeight = 0
                spanC = 0
            } else if (spanCurrentSize == 1) {
                maxHeight = max(maxHeight, height)
                spanC++
                if (spanC <= spanCount) {
                    scrolledY += maxHeight
                    maxHeight = 0
                    spanC = 0
                }
            }
        }
        return scrolledY
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val childAvgHeightMap = mutableMapOf<Int, Int>()
        val firstChild = getChildAt(0) ?: return 0
        val firstChildPosition = (0 to childCount).toList()
            .mapNotNull { getChildAt(it) }
            .mapNotNull { pos -> getPosition(pos).takeIf { it != RecyclerView.NO_POSITION } }
            .minOrNull() ?: 0
        var scrolledY: Int = -firstChild.y.toInt()
        var spanC = 0
        var maxHeight = 0
        for (i in 0 until firstChildPosition) {
            val height: Int = if (childSizesMap[i] != null) {
                childSizesMap[i] ?: 0
            } else {
                val type = rView?.adapter?.getItemViewType(i) ?: 0
                if (childAvgHeightMap[type] == null) {
                    childAvgHeightMap[type] = childTypeMap[type]?.values?.average()?.roundToInt() ?: 0
                }
                childAvgHeightMap[type] ?: 0
            }
            val spanCurrentSize = childSpanMap[i] ?: spanSizeLookup.getSpanSize(i)
            if (spanCount <= spanCurrentSize) {
                scrolledY += height
                scrolledY += maxHeight
                maxHeight = 0
                spanC = 0
            } else if (spanCurrentSize == 1) {
                maxHeight = max(maxHeight, height)
                spanC++
                if (spanC <= spanCount) {
                    scrolledY += maxHeight
                    maxHeight = 0
                    spanC = 0
                }
            }
        }
        scrolledY += maxHeight
        return scrolledY + paddingTop
    }
}
