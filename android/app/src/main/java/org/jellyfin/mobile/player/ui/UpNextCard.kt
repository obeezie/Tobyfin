package org.jellyfin.mobile.player.ui

import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.toBitmap
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.upnext.UpNextPrompt
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/** The "Up next" card shown near the end of an episode. Its content comes from [UpNextPrompt]. */
class UpNextCard(
    private val binding: FragmentPlayerBinding,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) : KoinComponent {
    private val api: ApiClient by inject()
    private val imageLoader: ImageLoader by inject()
    private val card = binding.upNextCard
    private val context = card.context
    private val maxWidth = context.resources.getDimensionPixelSize(R.dimen.up_next_card_width)
    private val sideMargin = context.resources.getDimensionPixelSize(R.dimen.exo_player_controls_display_padding)
    private var shownItemId: UUID? = null
    private var imageRequest: Disposable? = null

    init {
        binding.upNextPlay.setOnClickListener { onPlay() }
        binding.upNextDismiss.setOnClickListener { onDismiss() }
    }

    fun show(prompt: UpNextPrompt?, inPictureInPicture: Boolean) {
        if (prompt == null || inPictureInPicture) {
            card.isVisible = false
            return
        }

        binding.upNextHeading.text = when (val seconds = prompt.secondsLeft) {
            null -> context.getString(R.string.up_next_title)
            else -> context.getString(R.string.up_next_countdown, seconds)
        }
        binding.upNextDismiss.contentDescription = context.getString(
            if (prompt.atEnd) R.string.up_next_close else R.string.up_next_dismiss,
        )

        val item = prompt.item
        if (shownItemId != item.id) {
            shownItemId = item.id
            binding.upNextEpisode.text = episodeLabel(item)
            loadImage(item)
        }

        fitWidth()
        if (!card.isVisible) {
            card.isVisible = true
            binding.upNextPlay.requestFocus()
        }
    }

    fun release() {
        imageRequest?.dispose()
        imageRequest = null
    }

    /** Keep the card inside narrow screens. */
    private fun fitWidth() {
        val parentWidth = (card.parent as? ViewGroup)?.let { parent ->
            parent.width - parent.paddingLeft - parent.paddingRight
        } ?: 0
        val width = when {
            parentWidth > 0 -> minOf(maxWidth, parentWidth - 2 * sideMargin)
            else -> maxWidth
        }
        if (card.layoutParams.width != width) card.updateLayoutParams<ViewGroup.LayoutParams> { this.width = width }
    }

    private fun episodeLabel(item: BaseItemDto): String {
        val name = item.name.orEmpty()
        val episode = item.indexNumber ?: return name
        val season = item.parentIndexNumber
        return when (season) {
            null -> context.getString(R.string.up_next_episode_label_no_season, episode, name)
            else -> context.getString(R.string.up_next_episode_label, season, episode, name)
        }
    }

    private fun loadImage(item: BaseItemDto) {
        imageRequest?.dispose()
        binding.upNextImage.setImageDrawable(null)

        val url = imageUrl(item) ?: return
        val authorization = AuthorizationHeaderBuilder.buildHeader(
            clientName = api.clientInfo.name,
            clientVersion = api.clientInfo.version,
            deviceId = api.deviceInfo.id,
            deviceName = api.deviceInfo.name,
            accessToken = api.accessToken,
        )
        val itemId = item.id
        imageRequest = imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .httpHeaders(NetworkHeaders.Builder().set("Authorization", authorization).build())
                .target(
                    onSuccess = { image ->
                        if (shownItemId == itemId) binding.upNextImage.setImageBitmap(image.toBitmap())
                    },
                )
                .build(),
        )
    }

    /** The episode's own image, else the series thumbnail, else a series backdrop. */
    private fun imageUrl(item: BaseItemDto): String? {
        val imageApi = api.imageApi
        item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            return imageApi.getItemImageUrl(itemId = item.id, imageType = ImageType.PRIMARY, maxWidth = IMAGE_WIDTH, tag = tag)
        }
        val thumbItemId = item.parentThumbItemId
        val thumbTag = item.parentThumbImageTag
        if (thumbItemId != null && thumbTag != null) {
            return imageApi.getItemImageUrl(itemId = thumbItemId, imageType = ImageType.THUMB, maxWidth = IMAGE_WIDTH, tag = thumbTag)
        }
        val backdropItemId = item.parentBackdropItemId
        val backdropTag = item.parentBackdropImageTags?.firstOrNull()
        if (backdropItemId != null && backdropTag != null) {
            return imageApi.getItemImageUrl(itemId = backdropItemId, imageType = ImageType.BACKDROP, maxWidth = IMAGE_WIDTH, tag = backdropTag)
        }
        return null
    }

    private companion object {
        const val IMAGE_WIDTH = 400
    }
}
