package eu.kanade.tachiyomi.widget

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min
import kotlin.math.roundToInt

class LinearLayoutManagerAccurateOffset(context: Context?) : LinearLayoutManager(context) {

    // map of child adapter position to its height.
    private val childSizesMap = mutableMapOf<Int, Int>()
    private val childTypeMap = mutableMapOf<Int, MutableMap<Int, Int>>()
    var rView: RecyclerView? = null

    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: return
            val position = getPosition(child)
            childSizesMap[position] = child.height
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

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: RecyclerView.Recycler?) {
        super.onDetachedFromWindow(view, recycler)
        rView = null
    }

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val childAvgHeightMap = mutableMapOf<Int, Int>()
        var scrolledY = 0
        for (i in 0 until itemCount) {
            val height: Int = if (childSizesMap[i] != null) {
                childSizesMap[i] ?: 0
            } else {
                val type = rView?.adapter?.getItemViewType(i) ?: 0
                if (childAvgHeightMap[type] == null) {
                    val array = (childTypeMap[type]?.values ?: mutableListOf(0)).toIntArray()
                    childAvgHeightMap[type] = array
                        .copyOfRange(0, min(array.size, 50))
                        .average()
                        .roundToInt()
                }
                childAvgHeightMap[type] ?: 0
            }
            scrolledY += height
        }
        return scrolledY
    }

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }
        val firstChild = getChildAt(0) ?: return 0
        val firstChildPosition = (0 to childCount).toList()
            .mapNotNull { getChildAt(it) }
            .mapNotNull { pos -> getPosition(pos).takeIf { it != RecyclerView.NO_POSITION } }
            .minOrNull() ?: 0
        val childAvgHeightMap = mutableMapOf<Int, Int>()
        var scrolledY: Int = -firstChild.y.toInt()
        for (i in 0 until firstChildPosition) {
            val height: Int = if (childSizesMap[i] != null) {
                childSizesMap[i] ?: 0
            } else {
                val type = rView?.adapter?.getItemViewType(i) ?: 0
                if (childAvgHeightMap[type] == null) {
                    val array = (childTypeMap[type]?.values ?: mutableListOf(0)).toIntArray()
                    childAvgHeightMap[type] = array
                        .copyOfRange(0, min(array.size, 50))
                        .average()
                        .roundToInt()
                }
                childAvgHeightMap[type] ?: 0
            }
            scrolledY += height
        }
        return scrolledY + paddingTop
    }
}
