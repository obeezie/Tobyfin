package org.jellyfin.androidtv.ui.home

import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * The Spotlight row style: the selected row sits on the bottom edge of the screen, rows above it fade
 * out as they scroll up, and only the header of the next row peeks in from below.
 *
 * Attach on the Home rows grid; detach restores the standard leanback alignment.
 */
internal class HomeSpotlightRows(private val grid: VerticalGridView) {
	companion object {
		/** Space kept below the selected row's cards: enough for the next row's header text, not its cards. */
		const val PEEK_DP = 52
	}

	private val peek = (PEEK_DP * grid.resources.displayMetrics.density).roundToInt()

	private val savedWindowAlignment = grid.windowAlignment
	private val savedWindowAlignmentOffset = grid.windowAlignmentOffset
	private val savedWindowAlignmentOffsetPercent = grid.windowAlignmentOffsetPercent
	private val savedItemAlignmentOffset = grid.itemAlignmentOffset
	private val savedItemAlignmentOffsetPercent = grid.itemAlignmentOffsetPercent
	private val savedItemAlignmentOffsetWithPadding = grid.isItemAlignmentOffsetWithPadding
	private val savedPaddingBottom = grid.paddingBottom

	private val scrollListener = object : RecyclerView.OnScrollListener() {
		override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = fade()
	}
	private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fade() }

	fun attach() {
		// A negative window offset is measured up from the bottom edge, so the keyline follows the grid height.
		grid.windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
		grid.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
		grid.windowAlignmentOffset = -peek
		// Align the bottom of the row's cards (row_content) to that keyline rather than its top.
		grid.itemAlignmentOffset = 0
		grid.itemAlignmentOffsetPercent = 100f
		grid.isItemAlignmentOffsetWithPadding = true
		grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, 0)
		grid.addOnScrollListener(scrollListener)
		grid.addOnLayoutChangeListener(layoutListener)
		fade()
	}

	fun detach() {
		grid.removeOnScrollListener(scrollListener)
		grid.removeOnLayoutChangeListener(layoutListener)
		for (i in 0 until grid.childCount) grid.getChildAt(i).alpha = 1f
		grid.windowAlignment = savedWindowAlignment
		grid.windowAlignmentOffsetPercent = savedWindowAlignmentOffsetPercent
		grid.windowAlignmentOffset = savedWindowAlignmentOffset
		grid.itemAlignmentOffset = savedItemAlignmentOffset
		grid.itemAlignmentOffsetPercent = savedItemAlignmentOffsetPercent
		grid.isItemAlignmentOffsetWithPadding = savedItemAlignmentOffsetWithPadding
		grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, savedPaddingBottom)
	}

	/**
	 * Rows above the selected one are hidden; the alpha ramps down over the height of the selected row
	 * so the row that was just left fades out while the grid scrolls instead of popping.
	 */
	private fun fade() {
		val keyline = grid.height - peek
		if (keyline <= 0) return
		val selected = grid.findViewHolderForAdapterPosition(grid.selectedPosition)?.itemView
		for (i in 0 until grid.childCount) {
			val child = grid.getChildAt(i)
			val above = keyline - child.bottom
			val ramp = (selected?.height ?: child.height).coerceAtLeast(1)
			child.alpha = if (child === selected || above <= 0) 1f else (1f - above.toFloat() / ramp).coerceIn(0f, 1f)
		}
	}
}
