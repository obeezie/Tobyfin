package org.jellyfin.mobile.player.upnext

import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * The "Up next" card's content.
 *
 * @param item the next episode.
 * @param secondsLeft the autoplay countdown, or null when autoplay is off and the viewer starts it.
 * @param atEnd true once the current episode has finished and the player is waiting on the viewer.
 */
data class UpNextPrompt(
    val item: BaseItemDto,
    val secondsLeft: Int?,
    val atEnd: Boolean,
)
