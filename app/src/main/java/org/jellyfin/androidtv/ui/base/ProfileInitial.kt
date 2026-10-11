package org.jellyfin.androidtv.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs

/** Tobyfin's per-person avatar colors, shared by "Who's watching?" and the Home toolbar. */
object ProfileColors {
	private val colors = listOf(Color(0xFF7B4CE0), Color(0xFF0A8FC4), Color(0xFFD2576F), Color(0xFF2F9E6B), Color(0xFFC98A1A))

	fun forName(name: String) = colors[abs(name.hashCode()) % colors.size]
}

/** A colored circle with the first letter of [name], for profiles without a Jellyfin picture. */
@Composable
fun ProfileInitial(name: String, modifier: Modifier = Modifier) {
	BoxWithConstraints(
		modifier = modifier
			.clip(CircleShape)
			.background(ProfileColors.forName(name)),
		contentAlignment = Alignment.Center,
	) {
		val fontSize = with(LocalDensity.current) { (maxHeight * 0.5f).toSp() }
		Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = fontSize)
	}
}
