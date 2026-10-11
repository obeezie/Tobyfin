package org.jellyfin.androidtv.auth

import org.jellyfin.androidtv.preference.constant.UserSelectBehavior
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * When Tobyfin asks "Who's watching?" instead of signing straight in.
 *
 * Profiles are the accounts saved on this device. With one saved profile there is nobody to choose
 * between, so the app signs straight in.
 */
object WhosWatching {
	/** Time with Tobyfin in the background (or the TV asleep) after which it asks again. */
	val awayLimit: Duration = 30.minutes

	fun askOnLaunch(behavior: UserSelectBehavior, savedProfiles: Int) =
		behavior == UserSelectBehavior.ASK && savedProfiles > 1

	fun askOnReturn(behavior: UserSelectBehavior, savedProfiles: Int, signedIn: Boolean, away: Duration) =
		signedIn && away >= awayLimit && askOnLaunch(behavior, savedProfiles)

	/** The profile the highlight starts on: the last one used, or the first. */
	fun <T> initialFocus(profiles: List<T>, isLastUsed: (T) -> Boolean): Int =
		profiles.indexOfFirst(isLastUsed).coerceAtLeast(0)
}
