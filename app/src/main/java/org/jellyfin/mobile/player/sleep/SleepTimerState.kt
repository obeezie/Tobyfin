package org.jellyfin.mobile.player.sleep

/** A device-local, elapsed-time timer. Expiration stays latched through stream/episode handoffs. */
class SleepTimerState(private val now: () -> Long) {
    var deadline: Long? = null
        private set
    var afterItem = false
        private set
    var expired = false
        private set
    val remainingMillis: Long? get() = deadline?.let { (it - now()).coerceAtLeast(0) }
    fun start(minutes: Int) {
        require(minutes in 1..1440)
        cancel()
        deadline = now() + minutes * 60_000L
    }
    fun endOfItem() { cancel(); afterItem = true }
    fun extend(minutes: Int = 15) {
        require(minutes in 1..1440)
        deadline = maxOf(deadline ?: now(), now()) + minutes * 60_000L
        afterItem = false
        expired = false
    }
    fun cancel() { deadline = null; afterItem = false; expired = false }
    /** True exactly once when an elapsed-time deadline is reached. */
    fun check(): Boolean {
        if (!expired && deadline?.let { now() >= it } == true) {
            deadline = null
            expired = true
            return true
        }
        return false
    }
    fun finishItem(): Boolean {
        check()
        if (afterItem) { afterItem = false; expired = true }
        return expired
    }
}
