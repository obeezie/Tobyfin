package org.jellyfin.androidtv.ui.startup.fragment

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jellyfin.androidtv.R

/** Colors and type of "Who's watching?" and its add-profile panel (prototype/whos-watching-prototype.html). */
internal object WhosWatchingStyle {
	val Night = Color(0xFF080C1D)
	val Panel = Color(0xFF121A35)
	val PanelRow = Color(0xFF18213F)
	val RowFocused = Color(0xFF26305E)
	val Field = Color(0xFF0C1229)
	val Line = Color(0xFF263056)
	val Muted = Color(0xFFA2A8C6)
	val Purple = Color(0xFFB27DFF)
	val Cyan = Color(0xFF3CC4F5)
	val Danger = Color(0xFFFF8A8A)
	val Scrim = Color(0xBD04060E)
	val Jakarta = FontFamily(
		Font(R.font.plus_jakarta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
		Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
		Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
	)
}
