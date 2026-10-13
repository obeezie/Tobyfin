package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.view.MenuCompat
import androidx.lifecycle.lifecycleScope
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.ui.playback.setSubtitleIndex
import org.jellyfin.androidtv.ui.playback.subtitle.SubtitleSearch
import org.jellyfin.androidtv.ui.playback.subtitle.SubtitleSearchRules
import org.jellyfin.androidtv.ui.playback.subtitle.showFindOtherSubtitles
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID

class ClosedCaptionsAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null
	private val search = SubtitleSearch()

	/** Subtitle tracks a slow server added after playback resumed. Picking one restarts playback with it. */
	private val readyLater = mutableMapOf<UUID, MutableList<MediaStream>>()

	init {
		initializeWithIcon(R.drawable.ic_select_subtitle)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		if (playbackController.currentStreamInfo == null) {
			Timber.w("StreamInfo null trying to obtain subtitles")
			Toast.makeText(context, "Unable to obtain subtitle info", Toast.LENGTH_LONG).show()
			return
		}

		val item = playbackController.currentlyPlayingItem
		val streams = playbackController.currentMediaSource.mediaStreams.orEmpty().filter { it.type == MediaStreamType.SUBTITLE }
		val later = item?.let { readyLater[it.id] }.orEmpty().filter { new -> streams.none { it.index == new.index } }

		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		removePopup()
		popup = PopupMenu(context, view, Gravity.END).apply {
			with(menu) {
				// Tools first so they are one press away, then a divider, then the tracks.
				if (search.isAllowed() && SubtitleSearchRules.canSearch(item)) {
					add(TOOLS, FIND_OTHER, 0, context.getString(R.string.subtitle_search_menu))
				}
				add(TOOLS, OFFSET, 1, context.getString(R.string.subtitle_timing_title))

				var order = 2
				add(TRACKS, -1, order++, context.getString(R.string.lbl_none)).apply {
					isChecked = playbackController.subtitleStreamIndex == -1
				}
				for (sub in streams) {
					add(TRACKS, sub.index, order++, sub.displayTitle).apply {
						isChecked = sub.index == playbackController.subtitleStreamIndex
					}
				}
				for (sub in later) add(TRACKS, LATER + sub.index, order++, sub.displayTitle ?: sub.language ?: sub.title)

				setGroupCheckable(TRACKS, true, true)
				MenuCompat.setGroupDividerEnabled(this, true)
			}
			setOnDismissListener {
				videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
				popup = null
			}
			setOnMenuItemClickListener { menuItem ->
				when {
					menuItem.itemId == FIND_OTHER -> findOtherSubtitles(playbackController, videoPlayerAdapter, context)
					menuItem.itemId == OFFSET -> {
						if (playbackController.supportsSubtitleTiming()) {
							org.jellyfin.androidtv.ui.playback.showSubtitleTimingOverlay(context, view, playbackController.subtitleTiming)
							videoPlayerAdapter.leanbackOverlayFragment.hideOverlay()
						} else Toast.makeText(context, R.string.subtitle_timing_unsupported, Toast.LENGTH_LONG).show()
					}
					menuItem.itemId >= LATER -> playbackController.restartWithSubtitle(menuItem.itemId - LATER)
					else -> playbackController.setSubtitleIndex(menuItem.itemId)
				}
				true
			}
		}
		popup?.show()
	}

	private fun findOtherSubtitles(playbackController: PlaybackController, videoPlayerAdapter: VideoPlayerAdapter, context: Context) {
		val item = playbackController.currentlyPlayingItem ?: return
		val fragment = videoPlayerAdapter.leanbackOverlayFragment
		fragment.hideOverlay()
		showFindOtherSubtitles(
			context = context,
			scope = fragment.lifecycleScope,
			item = item,
			mediaSource = playbackController.currentMediaSource,
			search = search,
			pause = playbackController::pause,
			resume = { playbackController.play(playbackController.currentPosition) },
			onSwitch = { track -> playbackController.restartWithSubtitle(track.index) },
			onReadyLater = { track ->
				readyLater.getOrPut(item.id) { mutableListOf() } += track
				Toast.makeText(context, R.string.subtitle_search_ready_later, Toast.LENGTH_LONG).show()
			},
		)
	}

	fun removePopup() {
		popup?.dismiss()
	}

	private companion object {
		const val TRACKS = 0
		const val TOOLS = 1
		const val FIND_OTHER = -200
		const val OFFSET = -100

		/** Menu ids at or above this are tracks that arrived after playback resumed. */
		const val LATER = 100_000
	}
}
