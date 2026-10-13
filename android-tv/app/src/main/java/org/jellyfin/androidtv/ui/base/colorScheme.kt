package org.jellyfin.androidtv.ui.base

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.jellyfin.design.Tokens

// Tobyfin: dark navy with purple-to-cyan accents, matching Who's watching (prototype/tobyfin-menus-prototype.html).
fun colorScheme(): ColorScheme = ColorScheme(
	background = Color(0xFF080C1D),
	onBackground = Color(0xFFEEF0FA),
	button = Color(0xCC18213F),
	onButton = Color(0xFFEEF0FA),
	buttonFocused = Color(0xFFFFFFFF),
	onButtonFocused = Color(0xFF0B1022),
	buttonDisabled = Color(0x4D18213F),
	onButtonDisabled = Color(0xFF6B7299),
	buttonActive = Color(0x663CC4F5),
	onButtonActive = Color(0xFFEEF0FA),
	input = Color(0xCC18213F),
	onInput = Color(0xFFEEF0FA),
	inputFocused = Color(0xFF26305E),
	onInputFocused = Color(0xFFFFFFFF),
	rangeControlBackground = Color(0xFF2A3158),
	rangeControlFill = Color(0xFF3CC4F5),
	rangeControlKnob = Color(0xFFFFFFFF),
	seekbarBuffer = Color(0xFF6B7299),
	recording = Tokens.Color.colorRed300,
	onRecording = Tokens.Color.colorRed25,
	badge = Color(0xFF3CC4F5),
	onBadge = Color(0xFF0B1022),
	listHeader = Color(0xFFEEF0FA),
	listOverline = Color(0xFFA2A8C6),
	listHeadline = Color(0xFFEEF0FA),
	listCaption = Color(0xFFA2A8C6),
	listButton = Color.Transparent,
	listButtonFocused = Color(0xFF26305E),
	surface = Color(0xFF121A35),
	scrim = Tokens.Color.colorBlack.copy(alpha = 0.67f),
)

@Immutable
data class ColorScheme(
	val background: Color,
	val onBackground: Color,

	val button: Color,
	val onButton: Color,
	val buttonFocused: Color,
	val onButtonFocused: Color,
	val buttonDisabled: Color,
	val onButtonDisabled: Color,
	val buttonActive: Color,
	val onButtonActive: Color,

	val input: Color,
	val onInput: Color,
	val inputFocused: Color,
	val onInputFocused: Color,

	val rangeControlBackground: Color,
	val rangeControlFill: Color,
	val rangeControlKnob: Color,
	val seekbarBuffer: Color,

	val recording: Color,
	val onRecording: Color,

	val badge: Color,
	val onBadge: Color,

	val listHeader: Color,
	val listOverline: Color,
	val listHeadline: Color,
	val listCaption: Color,
	val listButton: Color,
	val listButtonFocused: Color,

	val surface: Color,
	val scrim: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
