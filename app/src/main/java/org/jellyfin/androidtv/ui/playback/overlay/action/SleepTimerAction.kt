package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.View
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.ui.playback.sleep.SleepTimerDialog

class SleepTimerAction(context: Context, glue: CustomPlaybackTransportControlGlue) : CustomAction(context, glue) {
    private var dialog: SleepTimerDialog? = null
    init { initializeWithIcon(R.drawable.ic_sleep_timer) }
    override fun handleClickAction(controller: PlaybackController, adapter: VideoPlayerAdapter, context: Context, view: View) {
        dismissPopup()
        adapter.leanbackOverlayFragment.setFading(false)
        dialog = SleepTimerDialog(context, controller.sleepTimer, !controller.isLiveTv).apply {
            setOnDismissListener { adapter.leanbackOverlayFragment.setFading(true); dialog = null }
            show()
        }
    }
    fun dismissPopup() { dialog?.dismiss(); dialog = null }
}
