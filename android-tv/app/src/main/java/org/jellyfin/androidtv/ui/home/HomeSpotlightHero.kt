package org.jellyfin.androidtv.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.browsing.composable.inforow.BaseItemInfoRow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/** The highlighted card on Home: the row it belongs to and the item itself. */
data class HomeSpotlightSelection(val rowTitle: String?, val item: BaseItemDto)

/** Darkens the left side of the backdrop so the Spotlight copy stays legible; drawn behind the rows. */
@Composable
fun HomeSpotlightScrim() {
	Box(
		Modifier.fillMaxSize().background(
			Brush.horizontalGradient(
				0f to Color(0xE60B0D10),
				0.4f to Color(0xB30B0D10),
				0.75f to Color.Transparent,
			)
		)
	)
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
				.padding(start = 48.dp, top = topInset + 12.dp, end = 48.dp)
				.fillMaxWidth(0.55f),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			current.rowTitle?.takeIf { it.isNotBlank() }?.let {
				Text(it.uppercase(), color = Color(0xFF00B7EF), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
			}
			Text(title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Light, lineHeight = 42.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
			if (isEpisode) item.name?.let { Text(it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
			BaseItemInfoRow(item = item, mediaSource = null, includeRuntime = true)
			item.overview?.takeIf { it.isNotBlank() }?.let {
				Text(it, color = Color(0xFFE3E7EC), fontSize = 15.sp, fontWeight = FontWeight.Light, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
			}
		}
	}
}
