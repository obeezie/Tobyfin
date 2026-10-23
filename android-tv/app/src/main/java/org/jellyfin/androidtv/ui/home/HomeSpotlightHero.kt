package org.jellyfin.androidtv.ui.home

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.parentBackdropImages
import org.jellyfin.sdk.api.client.ApiClient
import org.koin.compose.koinInject
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** The highlighted card on Home: the row it belongs to and the item itself. */
data class HomeSpotlightSelection(val rowTitle: String?, val item: BaseItemDto)

private val SpotlightBackground = Color(0xFF080C1D)

/** Backdrops are shown across about two thirds of a 1080p screen, so load them at that size, not full HD. */
private const val BACKDROP_WIDTH = 1280
private const val BACKDROP_HEIGHT = 720

/** The backdrop Spotlight shows for [item]: its own, or its show's for an episode. */
fun spotlightBackdropUrl(item: BaseItemDto, api: ApiClient): String? =
	(item.itemBackdropImages + item.parentBackdropImages).firstOrNull()?.getUrl(api, maxWidth = BACKDROP_WIDTH)

/** One request shape for showing and preloading, so a preloaded backdrop is a memory-cache hit. */
fun spotlightBackdropRequest(context: Context, url: String) = ImageRequest.Builder(context)
	.data(url)
	.size(BACKDROP_WIDTH, BACKDROP_HEIGHT)
	.build()

/**
 * The highlighted item's backdrop for Spotlight, drawn behind the rows: sharp and unblurred in the top-right of the
 * screen, fading into the dark area on the left where the title and overview sit, and into the rows below. The
 * current backdrop stays up until the next one has loaded, then crossfades, so moving never flashes empty.
 */
@Composable
fun HomeSpotlightBackdrop(selection: HomeSpotlightSelection?) {
	val api = koinInject<ApiClient>()
	val imageLoader = koinInject<ImageLoader>()
	val context = LocalContext.current
	var shown by remember { mutableStateOf<ImageBitmap?>(null) }
	val target = selection?.item?.let { spotlightBackdropUrl(it, api) }

	LaunchedEffect(target) {
		if (target == null) return@LaunchedEffect
		val image = withContext(Dispatchers.IO) {
			imageLoader.execute(spotlightBackdropRequest(context, target)).image?.toBitmap()?.asImageBitmap()
		}
		if (image != null) shown = image
	}

	Box(Modifier.fillMaxSize().background(SpotlightBackground)) {
		Crossfade(targetState = shown, animationSpec = tween(250), label = "spotlight backdrop") { bitmap ->
			Box(Modifier.fillMaxSize()) {
				if (bitmap != null) {
					val frame = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight(0.8f)
					Image(
						bitmap = bitmap,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						alignment = Alignment.TopCenter,
						modifier = frame,
					)
					// Fade the image's left edge into the text area, and its bottom edge into the rows.
					Box(frame.background(Brush.horizontalGradient(0f to SpotlightBackground, 0.4f to Color.Transparent)))
					Box(frame.background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to SpotlightBackground)))
				}
			}
		}
	}
}

/**
 * Title, details and overview of the highlighted item, in the band between the toolbar and the
 * bottom-anchored selected row. Keeps the last item while nothing is highlighted so the text never
 * flashes empty between selections.
 */
@Composable
fun HomeSpotlightHero(selection: HomeSpotlightSelection?, topInset: Dp) {
	val last = remember { arrayOfNulls<HomeSpotlightSelection>(1) }
	if (selection != null) last[0] = selection
	val current = last[0] ?: return

	AnimatedVisibility(visible = selection != null, enter = fadeIn(), exit = fadeOut()) {
		val item = current.item
		val isEpisode = item.type == BaseItemKind.EPISODE
		val title = (if (isEpisode) item.seriesName else null) ?: item.name.orEmpty()
		Column(
			modifier = Modifier
				// Starts just under the toolbar so a title, an episode name, the info row and two lines of overview
				// all fit above the selected row's header.
				.padding(start = 48.dp, top = (topInset - 16.dp).coerceAtLeast(0.dp), end = 48.dp)
				.fillMaxWidth(0.55f),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			current.rowTitle?.takeIf { it.isNotBlank() }?.let {
				Text(it.uppercase(), color = Color(0xFF00B7EF), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
			}
			Text(title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light, lineHeight = 42.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (isEpisode) item.name?.let { Text(it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
			// The info row's badges fill the height they are given; without a bound it took the rest of the
			// screen, drawing itself over the cards and pushing the overview off-screen.
			Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
				BaseItemInfoRow(item = item, mediaSource = null, includeRuntime = true)
			}
			item.overview?.takeIf { it.isNotBlank() }?.let {
				Text(it, color = Color(0xFFE3E7EC), fontSize = 15.sp, fontWeight = FontWeight.Light, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
			}
		}
	}
}
