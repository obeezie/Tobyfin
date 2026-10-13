package org.jellyfin.androidtv.ui.base

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jellyfin.androidtv.R

/** Tobyfin's colors and heading font for the Settings panel (prototype/tobyfin-menus-prototype.html). */
object TobyfinStyle {
	val Night = Color(0xFF080C1D)
	val Panel = Color(0xFF121A35)
	val PanelTop = Color(0xFF10183A)
	val PanelBottom = Color(0xFF0B1129)
	val Tile = Color(0xFF18213F)
	val TileIcon = Color(0xFFC9CFF0)
	val RowFocused = Color(0xFF26305E)
	val RowSelected = Color(0xFF142046)
	val Line = Color(0xFF263056)
	val Muted = Color(0xFFA2A8C6)
	val Faint = Color(0xFF6B7299)
	val SwitchOff = Color(0xFF2A3158)
	val KnobOff = Color(0xFF8B92B8)
	val Purple = Color(0xFFB27DFF)
	val Cyan = Color(0xFF3CC4F5)
	val OnAccent = Color(0xFF0B1022)
	val Gradient = Brush.linearGradient(listOf(Purple, Cyan))
	val Jakarta = FontFamily(
		Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
		Font(R.font.plus_jakarta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
		Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
	)
}
