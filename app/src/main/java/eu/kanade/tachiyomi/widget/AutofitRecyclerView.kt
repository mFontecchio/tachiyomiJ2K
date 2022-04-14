package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.marginTop
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.pxToDp
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class AutofitRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    androidx.recyclerview.widget.RecyclerView(context, attrs) {

    var manager: LayoutManager = GridLayoutManagerAccurateOffset(context, 1)

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
                managerSpanCount = value
            }
        }

    val itemWidth: Int
        get() {
            return if (spanCount == 0) measuredWidth / getTempSpan()
            else measuredWidth / managerSpanCount
        }

    init {
        layoutManager = manager
    }

    var managerSpanCount: Int
        get() {
            return (manager as? GridLayoutManager)?.spanCount ?: (manager as StaggeredGridLayoutManager).spanCount
        }
        set(value) {
            (manager as? GridLayoutManager)?.spanCount = value
            (manager as? StaggeredGridLayoutManager)?.spanCount = value
        }

    private fun getTempSpan(): Int {
        if (spanCount == 0 && columnWidth > 0) {
            val dpWidth = (width.pxToDp / 100f).roundToInt()
            return max(1, (dpWidth / columnWidth).roundToInt())
        }
        return 3
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        setSpan()
        lastMeasuredWidth = width
    }

    fun useStaggered(preferences: PreferencesHelper) {
        useStaggered(
            preferences.useStaggeredGrid().get() &&
                !preferences.uniformGrid().get() &&
                preferences.libraryLayout().get() != 1
        )
    }

    private fun useStaggered(use: Boolean) {
        if (use && manager !is StaggeredGridLayoutManagerAccurateOffset) {
            manager = StaggeredGridLayoutManagerAccurateOffset(
                context,
                null,
                1,
                StaggeredGridLayoutManager.VERTICAL
            )
            layoutManager = manager
        } else if (!use && manager !is GridLayoutManagerAccurateOffset) {
            manager = GridLayoutManagerAccurateOffset(context, 1)
            layoutManager = manager
        }
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
        if ((spanCount == 0 || force || (width != lastMeasuredWidth && lastMeasuredWidth == 0)) && columnWidth > 0) {
            val dpWidth = (width.pxToDp / 100f).roundToInt()
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
    private val childTypeHeightMap = mutableMapOf<Int, MutableMap<Int, Int>>()
    private val childTypeMap = mutableMapOf<Int, Int>()
    private val childTypeEstimateMap = mutableMapOf<Int, Int>()
    val childAvgHeightMap = mutableMapOf<Int, Int>()
    var rView: RecyclerView? = null

    private val toolbarHeight by lazy {
        val attrsArray = intArrayOf(R.attr.mainActionBarSize)
        val array = (context ?: rView?.context)?.obtainStyledAttributes(attrsArray)
        val height = array?.getDimensionPixelSize(0, 0) ?: 0
        array?.recycle()
        height
    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: return
            val position = getPosition(child)
            childSizesMap[position] = child.height
            childSpanMap[position] = spanSizeLookup.getSpanSize(getPosition(child))
            val type = getItemViewType(child)
            childTypeMap[position] = type
            if (childSizesMap[type] != null) {
                childTypeHeightMap[type]!![position] = child.height
            } else {
                childTypeHeightMap[type] = mutableMapOf(position to child.height)
            }
            childTypeHeightMap[type] = (
                childTypeHeightMap[type]?.also {
                    it[position] = child.height
                } ?: mutableMapOf(position to child.height)
                )
        }
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        rView = view
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        rView = null
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        rView ?: return super.computeVerticalScrollRange(state)
        var scrolledY = 0
        var spanC = 0
        var maxHeight = 0
        childAvgHeightMap.clear()
        for (i in 0 until itemCount) {
            val height: Int = getItemHeight(i)
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
        rView ?: return super.computeVerticalScrollOffset(state)
        val childAvgHeightMap = mutableMapOf<Int, Int>()
        val firstChild = getChildAt(0) ?: return 0
        val firstChildPosition = (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .mapNotNull { pos -> getPosition(pos).takeIf { it != RecyclerView.NO_POSITION } }
            .minOrNull() ?: 0
        var scrolledY: Int = -firstChild.y.toInt()
        var spanC = 0
        var maxHeight = 0
        childAvgHeightMap.clear()
        for (i in 0 until firstChildPosition) {
            val height: Int = getItemHeight(i)
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

    private fun getItemHeight(pos: Int): Int {
        return if (childSizesMap[pos] != null) {
            childSizesMap[pos] ?: 0
        } else {
            val type = if (childTypeMap[pos] == null) {
                val t = rView?.adapter?.getItemViewType(pos) ?: 0
                childTypeMap[pos] = t
                t
            } else {
                childTypeMap[pos] ?: 0
            }
            when {
                childTypeEstimateMap[type] != null -> childTypeEstimateMap[type] ?: 0
                childAvgHeightMap[type] == null -> {
                    val array = (childTypeHeightMap[type]?.values ?: mutableListOf(0)).toIntArray()
                    childAvgHeightMap[type] = array
                        .copyOfRange(0, min(array.size, 10))
                        .average()
                        .roundToInt()
                    if (array.size >= 10) {
                        childTypeEstimateMap[type] = childAvgHeightMap[type]!!
                    }
                    childAvgHeightMap[type] ?: 0
                }
                else -> childAvgHeightMap[type] ?: 0
            }
        }
    }

    override fun findFirstVisibleItemPosition(): Int {
        return getFirstPos()
    }

    override fun findFirstCompletelyVisibleItemPosition(): Int {
        return getFirstPos()
    }

    private fun getFirstPos(): Int {
        val inset = rView?.rootWindowInsetsCompat?.getInsets(systemBars())?.top ?: 0
        return (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .filter {
                val isLibraryHeader = getItemViewType(it) == R.layout.library_category_header_item
                val marginTop = if (isLibraryHeader) it.findViewById<TextView>(R.id.category_title)?.marginTop ?: 0 else 0
                it.y >= inset + toolbarHeight - marginTop
            }
            .mapNotNull { pos -> getPosition(pos).takeIf { it != RecyclerView.NO_POSITION } }
            .minOrNull() ?: RecyclerView.NO_POSITION
    }
}

class StaggeredGridLayoutManagerAccurateOffset(context: Context?, attr: AttributeSet?, spanCount: Int, orientation: Int) :
    StaggeredGridLayoutManager(context, attr, spanCount, orientation) {

    var rView: RecyclerView? = null

    private val toolbarHeight by lazy {
        val attrsArray = intArrayOf(R.attr.mainActionBarSize)
        val array = (context ?: rView?.context)?.obtainStyledAttributes(attrsArray)
        val height = array?.getDimensionPixelSize(0, 0) ?: 0
        array?.recycle()
        height
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
        rView = view
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        rView = null
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
//        return super.computeVerticalScrollOffset(state)

        rView ?: return super.computeVerticalScrollOffset(state)
        val firstChild = (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .mapNotNull { pos -> (pos to getPosition(pos)).takeIf { it.second != RecyclerView.NO_POSITION } }
            .minByOrNull { it.second } ?: return 0
        val scrolledY: Int = -firstChild.first.y.toInt()
        return if (firstChild.second == 0) {
            scrolledY + paddingTop
        } else {
            super.computeVerticalScrollOffset(state)
        }
    }

    fun findFirstVisibleItemPosition(): Int {
        return getFirstPos()
    }

    fun findFirstCompletelyVisibleItemPosition(): Int {
        return getFirstPos()
    }

    private fun getFirstPos(): Int {
        val inset = rView?.rootWindowInsetsCompat?.getInsets(systemBars())?.top ?: 0
        return (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .filter {
                val isLibraryHeader = getItemViewType(it) == R.layout.library_category_header_item
                val marginTop = if (isLibraryHeader) it.findViewById<TextView>(R.id.category_title)?.marginTop ?: 0 else 0
                it.y >= inset + toolbarHeight - marginTop
            }
            .mapNotNull { pos -> getPosition(pos).takeIf { it != RecyclerView.NO_POSITION } }
            .minOrNull() ?: RecyclerView.NO_POSITION
    }
}
