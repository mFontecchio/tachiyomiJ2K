package eu.kanade.tachiyomi.widget

import android.content.Context
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginTop
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        val inset = rView?.rootWindowInsetsCompat?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
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
