package org.jellyfin.mobile.player.sleep

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Owned by the playback session, never by a menu or a video surface. */
class SleepTimerController {
    val state = SleepTimerState(SystemClock::elapsedRealtime)
    private val handler = Handler(Looper.getMainLooper())
    private var owner: Any? = null
    private var onExpire: Runnable? = null
    private val tick = object : Runnable {
        override fun run() {
            check()
            if (state.deadline != null) handler.postDelayed(this, 500)
        }
    }
    val blocked: Boolean get() { check(); return state.expired }
    fun bind(owner: Any, onExpire: Runnable) { this.owner = owner; this.onExpire = onExpire }
    fun unbind(owner: Any) { if (this.owner === owner) { this.owner = null; onExpire = null } }
    fun release(owner: Any, preserve: Boolean) {
        if (this.owner !== owner) return
        if (!preserve) cancel()
        unbind(owner)
    }
    fun start(minutes: Int) { state.start(minutes); schedule() }
    fun endOfItem() { state.endOfItem(); schedule() }
    fun extend() { state.extend(); schedule() }
    fun cancel() { state.cancel(); handler.removeCallbacks(tick) }
    fun resumeManually() { if (state.expired) cancel() }
    fun finishItem() = state.finishItem()
    private fun check() { if (state.check()) onExpire?.run() }
    private fun schedule() { handler.removeCallbacks(tick); if (state.deadline != null) handler.post(tick) }
}
