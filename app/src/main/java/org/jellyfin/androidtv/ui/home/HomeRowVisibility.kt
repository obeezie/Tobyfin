package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row

/**
 * Keeps the displayed Home rows in source order as their asynchronous contents change.
 *
 * Rows are revealed top-down: a row is shown only once every row above it has its first results,
 * so a slow upper row never inserts itself above rows the viewer is already looking at.
 */
class HomeRowVisibility(
    private val source: ObjectAdapter,
    private val isPending: (ObjectAdapter) -> Boolean = { false },
    private val publish: (List<Row>) -> Unit,
) : AutoCloseable {
    private val children = mutableMapOf<ObjectAdapter, ObjectAdapter.DataObserver>()
    private var closed = false
    private var waiting = true
    private fun observer() = object : ObjectAdapter.DataObserver() {
        override fun onChanged() = update()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = update()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = update()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = update()
        override fun onItemMoved(fromPosition: Int, toPosition: Int) = update()
    }
    private val sourceObserver = observer()
    init {
        source.registerObserver(sourceObserver)
        update()
    }

    /** Re-evaluates rows, for example after a row finished loading without any results. */
    fun refresh() = update()

    /** Stops holding rows back behind ones that are still loading. */
    fun stopWaiting() {
        waiting = false
        update()
    }

    private fun update() {
        if (closed) return
        val rows = (0 until source.size()).mapNotNull { source[it] as? Row }
        val adapters = rows.filterIsInstance<ListRow>().map { it.adapter }.toSet()
        (children.keys - adapters).forEach { adapter -> adapter.unregisterObserver(children.remove(adapter)!!) }
        (adapters - children.keys).forEach { adapter ->
            val observer = observer()
            children[adapter] = observer
            adapter.registerObserver(observer)
        }
        val visible = mutableListOf<Row>()
        for (row in rows) {
            if (row !is ListRow || row.adapter.size() > 0) visible.add(row)
            else if (waiting && isPending(row.adapter)) break
        }
        // Once every row has answered, later reloads no longer hold rows back.
        if (rows.isNotEmpty() && rows.none { it is ListRow && isPending(it.adapter) }) waiting = false
        publish(visible)
    }
    override fun close() {
        closed = true
        source.unregisterObserver(sourceObserver)
        children.forEach { (adapter, observer) -> adapter.unregisterObserver(observer) }
        children.clear()
    }
}
