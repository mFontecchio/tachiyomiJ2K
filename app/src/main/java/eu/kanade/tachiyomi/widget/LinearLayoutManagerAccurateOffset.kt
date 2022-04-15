package eu.kanade.tachiyomi.widget

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LinearLayoutManagerAccurateOffset(context: Context?) : LinearLayoutManager(context) {

    // map of child adapter position to its height.
    private val childSizesMap = HashMap<Int, Int>()
    private val childTypeMap = HashMap<Int, Int>()
    private val childTypeHeightMap = HashMap<Int, HashMap<Int, Int>>()
    private val childTypeEstimateMap = HashMap<Int, Int>()
    var rView: RecyclerView? = null
    var computedRange: Int? = null

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        computedRange = null
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: return
            val position = getPosition(child)
            childSizesMap[position] = child.height
            val type = getItemViewType(child)
            childTypeMap[position] = type
            if (childSizesMap[type] != null) {
                childTypeHeightMap[type]!![position] = child.height
            } else {
                childTypeHeightMap[type] = hashMapOf(position to child.height)
            }
            childTypeHeightMap[type] = (
                childTypeHeightMap[type]?.also {
                    it[position] = child.height
                } ?: hashMapOf(position to child.height)
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
        if (childCount == 0) return 0
        computedRange?.let { return it }
        val childAvgHeightMap = HashMap<Int, Int>()
        val computedRange = (0 until itemCount).sumOf { getItemHeight(it, childAvgHeightMap) }
        this.computedRange = computedRange
        return computedRange
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) return 0
        val firstChild = getChildAt(0) ?: return 0
        val firstChildPosition = (0 to childCount).toList()
            .mapNotNull { getChildAt(it) }
            .mapNotNull { pos -> getPosition(pos).takeIf { it != RecyclerView.NO_POSITION } }
            .minOrNull() ?: 0
        val childAvgHeightMap = HashMap<Int, Int>()
        val scrolledY: Int = -firstChild.y.toInt() +
            (0 until firstChildPosition).sumOf { getItemHeight(it, childAvgHeightMap) }
        return scrolledY + paddingTop
    }

    private fun getItemHeight(pos: Int, childAvgHeightMap: HashMap<Int, Int>): Int {
        return EstimatedItemHeight.itemOrEstimatedHeight(
            pos,
            rView?.adapter?.getItemViewType(pos),
            childSizesMap,
            childTypeMap,
            childTypeHeightMap,
            childTypeEstimateMap,
            childAvgHeightMap
        )
    }
}
