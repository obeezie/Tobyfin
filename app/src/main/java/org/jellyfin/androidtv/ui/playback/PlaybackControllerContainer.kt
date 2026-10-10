package org.jellyfin.androidtv.ui.playback

class PlaybackControllerContainer {
    val sleepTimer = org.jellyfin.androidtv.ui.playback.sleep.SleepTimerController()
	var playbackController: PlaybackController? = null
}
