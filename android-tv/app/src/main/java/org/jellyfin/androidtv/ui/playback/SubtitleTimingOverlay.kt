package org.jellyfin.androidtv.ui.playback

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.jellyfin.playback.media3.exoplayer.subtitle.SubtitleTimingSession
import org.jellyfin.androidtv.R

/** A small, undimmed window. Opening and changing it never invokes playback commands. */
fun showSubtitleTimingOverlay(
    context: Context,
    anchor: View,
    timing: SubtitleTimingSession,
    onDismiss: () -> Unit = {},
): Dialog {
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun background(focused: Boolean) = GradientDrawable().apply {
        setColor(Color.parseColor(if (focused) "#476958" else "#253B33"))
        cornerRadius = dp(5).toFloat()
        if (focused) setStroke(dp(2), Color.parseColor("#D4F59A"))
    }
    fun button(label: String, description: String) = Button(context).apply {
        text = label
        contentDescription = description
        textSize = 20f
        setTextColor(Color.WHITE)
        isAllCaps = false
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(4), 0, dp(4), 0)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), background(true))
            addState(intArrayOf(android.R.attr.state_pressed), background(true))
            addState(intArrayOf(), background(false))
        }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(2), 0, dp(2), 0) }
    }
    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply { setColor(Color.parseColor("#EA101B17")); cornerRadius = dp(10).toFloat() }
    }
    val bar = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    var step = 100L
    val earlier = button("−", context.getString(R.string.subtitle_timing_earlier))
    val later = button("+", context.getString(R.string.subtitle_timing_later))
    val value = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 17f
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(80), dp(44))
        contentDescription = context.getString(R.string.subtitle_timing_seconds)
    }
    val more = button("⋯", context.getString(R.string.subtitle_timing_more))
    val close = button("×", context.getString(R.string.subtitle_timing_close))
    listOf(earlier, value, later, more, close).forEach { bar.addView(it) }
    root.addView(bar)

    val advanced = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
    }
    val options = LinearLayout(context)
    val stepButton = button("", context.getString(R.string.subtitle_timing_step)).apply { textSize = 13f }
    val reset = button(context.getString(R.string.subtitle_timing_reset), context.getString(R.string.subtitle_timing_reset)).apply { textSize = 13f }
    val exact = button(context.getString(R.string.subtitle_timing_exact), context.getString(R.string.subtitle_timing_exact)).apply { textSize = 13f }
    listOf(stepButton, reset, exact).forEach { child ->
        options.addView(child, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(2), dp(6), dp(2), 0) })
    }
    advanced.addView(options)
    val entry = LinearLayout(context).apply { visibility = View.GONE; gravity = Gravity.CENTER_VERTICAL }
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
        textSize = 16f
        setTextColor(Color.WHITE)
        contentDescription = context.getString(R.string.subtitle_timing_seconds)
        setSingleLine()
    }
    val apply = button(context.getString(R.string.subtitle_timing_apply), context.getString(R.string.subtitle_timing_apply)).apply { textSize = 13f }
    entry.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
    entry.addView(apply, LinearLayout.LayoutParams(dp(76), dp(44)))
    advanced.addView(entry)
    root.addView(advanced, LinearLayout.LayoutParams(dp(272), ViewGroup.LayoutParams.WRAP_CONTENT))

    fun update() {
        value.text = SubtitleTimingSession.formatSeconds(timing.delayMillis) + " s"
        stepButton.text = context.getString(R.string.subtitle_timing_step) + ": " + SubtitleTimingSession.formatSeconds(step) + " s"
        input.setText(SubtitleTimingSession.formatSeconds(timing.delayMillis))
    }
    fun applyEntry() {
        val parsed = SubtitleTimingSession.parseSeconds(input.text.toString())
        if (parsed == null) { input.error = context.getString(R.string.subtitle_timing_invalid); return }
        timing.setDelayMillis(parsed)
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        entry.visibility = View.GONE
        update()
        earlier.requestFocus()
    }
    earlier.setOnClickListener { timing.nudge(-step); update() }
    later.setOnClickListener { timing.nudge(step); update() }
    reset.setOnClickListener { timing.reset(); update() }
    stepButton.setOnClickListener { step = if (step == 100L) 1000L else 100L; update() }
    more.setOnClickListener {
        advanced.visibility = if (advanced.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }
    exact.setOnClickListener {
        entry.visibility = View.VISIBLE
        input.setText(SubtitleTimingSession.formatSeconds(timing.delayMillis))
        input.requestFocus()
        input.selectAll()
    }
    apply.setOnClickListener { applyEntry() }
    input.setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_DONE) { applyEntry(); true } else false
    }
    close.setOnClickListener { dialog.dismiss() }
    update()
    dialog.setContentView(root)
    dialog.setCanceledOnTouchOutside(false)
    val lifecycle = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { dialog.dismiss() }
    }
    anchor.addOnAttachStateChangeListener(lifecycle)
    dialog.setOnDismissListener {
        anchor.removeOnAttachStateChangeListener(lifecycle)
        onDismiss()
    }
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        attributes = attributes.apply { y = dp(12); dimAmount = 0f }
    }
    earlier.requestFocus()
    return dialog
}
