package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.TobyfinStyle

/** Tobyfin: a setting's current value, shown in cyan on the right of its row. */
@Composable
fun SettingsValue(text: String) = Text(
	text = text,
	style = JellyfinTheme.typography.listCaption.copy(
		color = TobyfinStyle.Cyan,
		fontSize = 12.sp,
		fontWeight = FontWeight.W600,
	),
	maxLines = 1,
	overflow = TextOverflow.Ellipsis,
)
