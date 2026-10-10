package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row
import java.util.WeakHashMap

/**
 * Remembers which card was selected in each Home row. Leanback rebuilds row views when Home
 * returns from a details page (or a row scrolls back on screen), which would otherwise put every
 * row back at its first card.
 */
class HomeRowPositions {
    private val positions = WeakHashMap<ObjectAdapter, Int>()

    fun record(row: Row?, position: Int) {
        if (row is ListRow && position >= 0) positions[row.adapter] = position
    }

    /** The remembered card for [row], or null when it was never browsed or no longer exists. */
    fun positionFor(row: Any?): Int? {
        val adapter = (row as? ListRow)?.adapter ?: return null
        val position = positions[adapter] ?: return null
        return position.takeIf { it < adapter.size() }
    }
}
