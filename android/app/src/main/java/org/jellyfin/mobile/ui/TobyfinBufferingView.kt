package org.jellyfin.mobile.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import org.jellyfin.mobile.R

/** Passive buffering artwork. Only the ring rotates; no playback commands are issued. */
class TobyfinBufferingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val dog = BitmapFactory.decodeResource(resources, R.drawable.tobyfin_buffering_dog)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x13FFFFFF
    }
    private val dogBounds = RectF()
    private var rotationDegrees = 0f
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        contentDescription = context.getString(R.string.tobyfin_buffering)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val unit = minOf(w, h) / 92f
        val left = (w - 92f * unit) / 2f
        val top = (h - 92f * unit) / 2f
        // Approved optical centering: the 74px face sits 2px above geometric center.
        dogBounds.set(left + 9f * unit, top + 7f * unit, left + 83f * unit, top + 81f * unit)
        ringPaint.strokeWidth = 3f * unit
        trackPaint.strokeWidth = 2f * unit
        ringPaint.shader = SweepGradient(
            w / 2f, h / 2f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0x339864C9, 0xFF9964D6.toInt(), 0xFF00A4D4.toInt(), Color.TRANSPARENT),
            floatArrayOf(0f, 0.08f, 0.35f, 0.65f, 0.96f, 1f),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - ringPaint.strokeWidth / 2f
        canvas.drawCircle(cx, cy, radius, trackPaint)
        val save = canvas.save()
        canvas.rotate(rotationDegrees - 90f, cx, cy)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.restoreToCount(save)
        canvas.drawBitmap(dog, null, dogBounds, imagePaint)
    }

    private fun updateAnimation() {
        val animationsEnabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        if (isAttachedToWindow && isShown && windowVisibility == VISIBLE && animationsEnabled) {
            if (animator != null) return
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1250L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    rotationDegrees = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animator?.cancel()
            animator = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
