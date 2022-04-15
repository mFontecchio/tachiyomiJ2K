package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.marginTop
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat

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
