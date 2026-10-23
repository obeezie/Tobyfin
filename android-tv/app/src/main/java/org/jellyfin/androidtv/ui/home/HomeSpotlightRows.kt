package org.jellyfin.androidtv.ui.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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

		// A last check once the grid settles, so no row is left visible after a scroll.
		override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
			if (newState == RecyclerView.SCROLL_STATE_IDLE) fadeNowAndAfterAnimations()
		}
	}
	private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fadeNowAndAfterAnimations() }
	private val attachListener = object : RecyclerView.OnChildAttachStateChangeListener {
		override fun onChildViewAttachedToWindow(view: View) {
			// A row appearing above the selection is hidden before it is ever drawn.
			val position = grid.getChildAdapterPosition(view)
			if (position != RecyclerView.NO_POSITION && position < grid.selectedPosition) {
				(view.getTag(R.id.spotlight_alpha_animator) as? ValueAnimator)?.cancel()
				view.setTag(R.id.spotlight_alpha_animator, null)
				view.setTag(R.id.spotlight_alpha_target, 0f)
				view.alpha = 0f
			}
			fadeNowAndAfterAnimations()
		}
		override fun onChildViewDetachedFromWindow(view: View) = Unit
	}

	private val settleCheck = Runnable { fade() }

	/** Fade now, and again once the grid's own item animations (rows added or refreshed) have finished. */
	private fun fadeNowAndAfterAnimations() {
		fade()
		grid.post { grid.itemAnimator?.isRunning { fade() } ?: fade() }
		grid.postDelayed(settleCheck, FADE_MS + 120)
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
		grid.removeCallbacks(settleCheck)
		for (i in 0 until grid.childCount) grid.getChildAt(i).apply {
			(getTag(R.id.spotlight_alpha_animator) as? ValueAnimator)?.cancel()
			setTag(R.id.spotlight_alpha_animator, null)
			setTag(R.id.spotlight_alpha_target, null)
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
	/** The row selected last time [fade] ran, to tell the row just left (moving down) from any other. */
	private var lastSelected = RecyclerView.NO_POSITION

	/** The row the selection just moved down from; only this one fades out, everything else changes at once. */
	private var leaving = RecyclerView.NO_POSITION

	private fun fade() {
		val selected = grid.selectedPosition
		if (selected != lastSelected) {
			leaving = if (lastSelected != RecyclerView.NO_POSITION && lastSelected < selected) lastSelected else RecyclerView.NO_POSITION
			lastSelected = selected
		}
		for (i in 0 until grid.childCount) {
			val child = grid.getChildAt(i)
			val position = grid.getChildAdapterPosition(child)
			val target = if (position != RecyclerView.NO_POSITION && position < selected) 0f else 1f
			// Trust only what is really happening: a fade that is still running towards the target, or a row
			// already at it. The grid cancels animations and resets alpha when it recycles or animates a row,
			// so a remembered "fading" flag can go stale and leave a row above fully visible.
			val running = child.getTag(R.id.spotlight_alpha_animator) as? ValueAnimator
			if (running?.isRunning == true) {
				if (child.getTag(R.id.spotlight_alpha_target) == target) continue
				running.cancel()
			} else if (child.alpha == target) continue
			child.setTag(R.id.spotlight_alpha_target, target)
			// Only the row being left fades out as it slides up, and the newly selected row fades in. A row
			// coming in from the top while moving up is hidden at once, or it would flash before fading.
			if (target == 0f && position != leaving) {
				child.setTag(R.id.spotlight_alpha_animator, null)
				child.alpha = 0f
				continue
			}
			val fade = ObjectAnimator.ofFloat(child, View.ALPHA, child.alpha, target).setDuration(FADE_MS)
			child.setTag(R.id.spotlight_alpha_animator, fade)
			fade.start()
		}
	}
}
