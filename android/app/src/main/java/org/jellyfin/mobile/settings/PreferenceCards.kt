package org.jellyfin.mobile.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.preferences.CategoryHeader
import org.jellyfin.mobile.R

/**
 * Tobyfin: draws each group of settings as a rounded navy card, with its category header as a small
 * label above it (prototype/tobyfin-menus-prototype.html).
 */
class PreferenceCards(context: Context, private val adapter: PreferencesAdapter) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val sideMargin = (14 * density).toInt()
    private val headerGap = (10 * density).toInt()
    private val radius = 16 * density
    private val dividerInset = 16 * density

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tobyfin_panel)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tobyfin_line)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val dividerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.tobyfin_line)
        strokeWidth = density
    }
    private val labelColor = ContextCompat.getColor(context, R.color.tobyfin_muted)

    private fun isHeader(position: Int): Boolean {
        val screen = adapter.currentScreen
        return position !in 0 until screen.size() || screen[position] is CategoryHeader
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        outRect.left = sideMargin
        outRect.right = sideMargin
        if (isHeader(position)) {
            outRect.top = if (position == 0) 0 else headerGap
            styleHeader(view)
        } else if (position == 0) {
            outRect.top = headerGap
        }
    }

    private fun styleHeader(view: View) {
        val title = view.findViewById<TextView>(android.R.id.title) ?: return
        title.setTextColor(labelColor)
        title.isAllCaps = true
        title.letterSpacing = 0.08f
        title.textSize = 11f
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || isHeader(position)) continue

            val first = position == 0 || isHeader(position - 1)
            val last = isHeader(position + 1)
            val rect = RectF(
                child.left.toFloat(),
                child.top + child.translationY,
                child.right.toFloat(),
                child.bottom + child.translationY,
            )
            val top = if (first) radius else 0f
            val bottom = if (last) radius else 0f
            val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            val path = Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
            canvas.drawPath(path, cardPaint)

            // Outline only the card's outer edges, so the rows read as one card.
            canvas.drawLine(rect.left, rect.top + top, rect.left, rect.bottom - bottom, borderPaint)
            canvas.drawLine(rect.right, rect.top + top, rect.right, rect.bottom - bottom, borderPaint)
            if (first) {
                val arc = Path().apply {
                    moveTo(rect.left, rect.top + radius)
                    arcTo(RectF(rect.left, rect.top, rect.left + 2 * radius, rect.top + 2 * radius), 180f, 90f)
                    lineTo(rect.right - radius, rect.top)
                    arcTo(RectF(rect.right - 2 * radius, rect.top, rect.right, rect.top + 2 * radius), 270f, 90f)
                }
                canvas.drawPath(arc, borderPaint)
            } else {
                canvas.drawLine(rect.left + dividerInset, rect.top, rect.right, rect.top, dividerPaint)
            }
            if (last) {
                val arc = Path().apply {
                    moveTo(rect.right, rect.bottom - radius)
                    arcTo(RectF(rect.right - 2 * radius, rect.bottom - 2 * radius, rect.right, rect.bottom), 0f, 90f)
                    lineTo(rect.left + radius, rect.bottom)
                    arcTo(RectF(rect.left, rect.bottom - 2 * radius, rect.left + 2 * radius, rect.bottom), 90f, 90f)
                }
                canvas.drawPath(arc, borderPaint)
            }
        }
    }
}
