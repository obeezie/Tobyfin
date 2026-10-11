package org.jellyfin.androidtv.ui.playback.sleep

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Compact, non-dimming overlay; choosing or editing a timer never pauses playback. */
class SleepTimerDialog(context: Context, private val timer: SleepTimerController, private val allowEnd: Boolean = true) : Dialog(context) {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private val refresh = object : Runnable {
        override fun run() { updateStatus(); handler.postDelayed(this, 1000) }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(0xF0222222.toInt()); cornerRadius = dp(16).toFloat() }
        }
        fun label(text: String, size: Float) = TextView(context).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        content.addView(label("Sleep timer", 21f))
        status = label("", 15f)
        content.addView(status)
        fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; content.addView(this) }
        fun button(parent: LinearLayout, title: String, action: () -> Unit) = Button(context).apply {
            text = title; isAllCaps = false; textSize = 14f
            parent.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { action() }
        }
        val presets = row()
        listOf(15, 30, 60, 90).forEach { minutes -> button(presets, "$minutes min") { timer.start(minutes); dismiss() } }
        val custom = row()
        val input = EditText(context).apply {
            hint = "Minutes (1–1440)"; setHintTextColor(Color.LTGRAY); setTextColor(Color.WHITE)
            textSize = 14f; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(true)
            contentDescription = "Custom sleep timer in minutes"
        }
        custom.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
        button(custom, "Set") {
            val minutes = input.text.toString().toIntOrNull()
            if (minutes == null || minutes !in 1..1440) input.error = "Enter 1–1440 minutes"
            else { timer.start(minutes); dismiss() }
        }
        if (allowEnd) button(row(), "Stop after this episode / movie") { timer.endOfItem(); dismiss() }
        val actions = row()
        button(actions, "+15 min") { timer.extend(); updateStatus() }
        button(actions, "Cancel timer") { timer.cancel(); updateStatus() }
        button(row(), "Done") { dismiss() }
        val scroll = ScrollView(context).apply { addView(content); isFillViewport = false }
        setContentView(scroll)
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.TOP or Gravity.END)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { x = dp(16); y = dp(16) }
            val display = context.resources.displayMetrics
            setLayout(minOf(dp(420), display.widthPixels - dp(32)), minOf(dp(340), display.heightPixels - dp(32)))
        }
        presets.getChildAt(0)?.requestFocus()
    }
    private fun updateStatus() {
        val state = timer.state
        status.text = when {
            state.expired -> "Timer ended · playback paused"
            state.afterItem -> "Stops at the end of this video"
            state.remainingMillis != null -> {
                val seconds = ((state.remainingMillis ?: 0) + 999) / 1000
                "Pauses in ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
            }
            else -> "Off · keeps playing until you set a timer"
        }
    }
    override fun onStart() { super.onStart(); handler.post(refresh) }
    override fun onStop() { handler.removeCallbacks(refresh); super.onStop() }
}
