package org.jellyfin.androidtv.ui.home

import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.androidtv.R
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

		/** How long a row takes to fade out above the selected row, or back in. */
		const val FADE_MS = 160L
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
	private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fadeNowAndAfterAnimations() }
	private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
		override fun onChildViewAttachedToWindow(view: View) = fadeNowAndAfterAnimations()
		override fun onChildViewDetachedFromWindow(view: View) = Unit
	}

	/** Fade now, and again once the grid's own item animations (rows added or refreshed) have finished. */
	private fun fadeNowAndAfterAnimations() {
		fade()
		grid.post { grid.itemAnimator?.isRunning { fade() } ?: fade() }
	}

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
		grid.addOnChildAttachStateChangeListener(attachListener)
		fade()
	}

	fun detach() {
		grid.removeOnScrollListener(scrollListener)
		grid.removeOnLayoutChangeListener(layoutListener)
		grid.removeOnChildAttachStateChangeListener(attachListener)
		for (i in 0 until grid.childCount) grid.getChildAt(i).apply {
			animate().cancel()
			setTag(R.id.spotlight_alpha_target, null)
			setTag(R.id.spotlight_alpha_animating, null)
			alpha = 1f
		}
		grid.windowAlignment = savedWindowAlignment
		grid.windowAlignmentOffsetPercent = savedWindowAlignmentOffsetPercent
		grid.windowAlignmentOffset = savedWindowAlignmentOffset
		grid.itemAlignmentOffset = savedItemAlignmentOffset
		grid.itemAlignmentOffsetPercent = savedItemAlignmentOffsetPercent
		grid.isItemAlignmentOffsetWithPadding = savedItemAlignmentOffsetWithPadding
		grid.setPadding(grid.paddingLeft, grid.paddingTop, grid.paddingRight, savedPaddingBottom)
	}

	/**
	 * Rows above the selected one are hidden, so nothing shows behind the backdrop and the title text. The
	 * rows are lined up by the bottom of their cards, not the titles under them, so position is a steadier
	 * test than distance from the keyline. A row fades out (or back in) briefly instead of popping.
	 */
	private fun fade() {
		val selected = grid.selectedPosition
		for (i in 0 until grid.childCount) {
			val child = grid.getChildAt(i)
			val position = grid.getChildAdapterPosition(child)
			val target = if (position != RecyclerView.NO_POSITION && position < selected) 0f else 1f
			// Skip only a row already fading to the target. The grid's own add and change animations set a
			// row's alpha back to 1 when they finish, so the row's actual alpha is checked, not just the target.
			val fading = child.getTag(R.id.spotlight_alpha_target) == target && child.getTag(R.id.spotlight_alpha_animating) == true
			if (fading || (child.alpha == target && child.getTag(R.id.spotlight_alpha_target) == target)) continue
			child.setTag(R.id.spotlight_alpha_target, target)
			child.setTag(R.id.spotlight_alpha_animating, true)
			child.animate().cancel()
			child.animate().alpha(target).setDuration(FADE_MS)
				.withEndAction { child.setTag(R.id.spotlight_alpha_animating, false) }
				.start()
		}
	}
}
