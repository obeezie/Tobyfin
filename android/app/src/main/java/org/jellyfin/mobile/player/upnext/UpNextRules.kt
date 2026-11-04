package org.jellyfin.mobile.player.upnext

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * When the "Up next" card appears near the end of an episode, and when it starts the next one.
 *
 * Positions are in the episode's own timeline. Pausing freezes the countdown because it is measured
 * against the playback position, not the wall clock.
 */
object UpNextRules {
    /** Without a credits marker, the card appears this long before the end and counts down to the end. */
    val END_LEAD: Duration = 30.seconds

    /** With a credits (outro) marker, the card appears when the credits start and counts down this long. */
    val CREDITS_COUNTDOWN: Duration = 10.seconds

    /**
     * @param showAt the position at which the card appears.
     * @param advanceAt the position at which the next episode starts when autoplay is on.
     * @param runTime the episode's length.
     */
    data class Timing(val showAt: Duration, val advanceAt: Duration, val runTime: Duration) {
        /** True when the countdown starts the next episode before the video ends, skipping the credits. */
        val skipsCredits: Boolean get() = advanceAt < runTime
    }

    /**
     * @param runTime the episode's length; no card for unknown or very short videos.
     * @param creditsStart the start of the episode's outro media segment, if the server has one.
     */
    fun timing(runTime: Duration, creditsStart: Duration?): Timing? {
        if (runTime <= END_LEAD * 2) return null
        val endLead = runTime - END_LEAD
        // Ignore markers in the first half (mislabelled) or at the very end (nothing left to count down).
        val credits = creditsStart?.takeIf { it >= runTime / 2 && it < endLead }
        return when (credits) {
            null -> Timing(showAt = endLead, advanceAt = runTime, runTime = runTime)
            else -> Timing(showAt = credits, advanceAt = minOf(credits + CREDITS_COUNTDOWN, runTime), runTime = runTime)
        }
    }

    /** Whole seconds left on the countdown, rounded up so it reads 10, 9, … 1. */
    fun secondsLeft(position: Duration, advanceAt: Duration): Int {
        val left = advanceAt - position
        if (left <= Duration.ZERO) return 0
        return ceil(left.inWholeMilliseconds / 1000.0).toInt()
    }
}
