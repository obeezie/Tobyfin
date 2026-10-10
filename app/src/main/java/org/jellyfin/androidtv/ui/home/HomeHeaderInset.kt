package org.jellyfin.androidtv.ui.home

import androidx.leanback.widget.VerticalGridView
import kotlin.math.roundToInt

/** Reserves first-row space without clipping rows as they scroll behind the toolbar. */
internal class HomeHeaderInset(private val grid: VerticalGridView) {
	private val originalTopPadding = grid.paddingTop
	private val originalAlignmentOffset = grid.windowAlignmentOffset
	private val alignmentFraction = grid.windowAlignmentOffsetPercent.takeIf { it >= 0f }?.div(100f) ?: 0f
	private var appliedHeight: Int? = null

	fun apply(headerHeight: Int) {
		val height = headerHeight.coerceAtLeast(0)
		if (appliedHeight == height) return
		appliedHeight = height
		grid.clipToPadding = false
		grid.setPadding(grid.paddingLeft, originalTopPadding + height, grid.paddingRight, grid.paddingBottom)
		// Keep the focus keyline where it was in the shorter, below-toolbar viewport.
		val originShift = if (originalAlignmentOffset >= 0) height else 0
		grid.windowAlignmentOffset = originalAlignmentOffset + originShift - (height * alignmentFraction).roundToInt()
	}
}
