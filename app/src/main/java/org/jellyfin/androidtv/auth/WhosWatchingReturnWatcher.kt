package org.jellyfin.androidtv.auth

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.auth.store.AuthenticationPreferences
import org.jellyfin.androidtv.auth.store.AuthenticationStore
import org.jellyfin.androidtv.ui.navigation.ActivityDestinations
import org.jellyfin.androidtv.ui.playback.MediaManager
import org.jellyfin.androidtv.ui.startup.StartupActivity
import org.koin.core.Koin
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Asks "Who's watching?" again when Tobyfin returns after [WhosWatching.awayLimit] in the background.
 * SystemClock.elapsedRealtime includes time the TV spent asleep.
 */
class WhosWatchingReturnWatcher(
	private val application: Application,
	private val koin: Koin,
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {
	private var backgroundedAt: Long? = null
	private var currentActivity: Activity? = null

	fun register() {
		application.registerActivityLifecycleCallbacks(this)
		ProcessLifecycleOwner.get().lifecycle.addObserver(this)
	}

	override fun onStop(owner: LifecycleOwner) {
		backgroundedAt = SystemClock.elapsedRealtime()
	}

	override fun onStart(owner: LifecycleOwner) {
		val since = backgroundedAt ?: return
		backgroundedAt = null

		val preferences = koin.get<AuthenticationPreferences>()
		val sessionRepository = koin.get<SessionRepository>()
		val savedProfiles = koin.get<AuthenticationStore>().getServers().values.sumOf { it.users.size }
		val ask = WhosWatching.askOnReturn(
			behavior = preferences[AuthenticationPreferences.autoLoginUserBehavior],
			savedProfiles = savedProfiles,
			signedIn = sessionRepository.currentSession.value != null,
			away = (SystemClock.elapsedRealtime() - since).milliseconds,
		)
		// Music playing in the background keeps its listener.
		if (!ask || koin.get<MediaManager>().isPlayingAudio) return

		Timber.i("Returned after the away limit, asking who's watching")
		sessionRepository.destroyCurrentSession()
		// The startup screen already watches the session; reopened from elsewhere, it keeps the requested item.
		if (currentActivity !is StartupActivity) {
			application.startActivity(
				ActivityDestinations.startup(application)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
			)
		}
	}

	override fun onActivityResumed(activity: Activity) {
		currentActivity = activity
	}

	override fun onActivityPaused(activity: Activity) {
		if (currentActivity === activity) currentActivity = null
	}

	override fun onActivityStarted(activity: Activity) {
		currentActivity = activity
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
	override fun onActivityStopped(activity: Activity) = Unit
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
	override fun onActivityDestroyed(activity: Activity) = Unit
}
