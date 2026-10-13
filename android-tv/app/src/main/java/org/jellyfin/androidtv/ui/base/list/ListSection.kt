package org.jellyfin.androidtv.ui.base.list

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.TobyfinStyle

@Composable
fun ListSection(
	modifier: Modifier = Modifier,
	headingContent: @Composable () -> Unit,
	overlineContent: (@Composable () -> Unit)? = null,
	captionContent: (@Composable () -> Unit)? = null,
	leadingContent: (@Composable () -> Unit)? = null,
	trailingContent: (@Composable () -> Unit)? = null,
	footerContent: (@Composable () -> Unit)? = null,
) {
	// Tobyfin: a heading on its own is a small label above a group of rows;
	// with an overline or caption it is the page's title.
	val isLabel = overlineContent == null && captionContent == null && leadingContent == null && trailingContent == null
	val headingStyle = if (isLabel) {
		JellyfinTheme.typography.listOverline.copy(
			color = TobyfinStyle.Muted,
			fontSize = 11.sp,
			letterSpacing = 1.1.sp,
			fontWeight = FontWeight.W700,
		)
	} else {
		JellyfinTheme.typography.listHeader.copy(
			color = JellyfinTheme.colorScheme.listHeader,
			fontFamily = TobyfinStyle.Jakarta,
			fontSize = 22.sp,
			lineHeight = 28.sp,
			fontWeight = FontWeight.Bold,
		)
	}

	ListItemContent(
		headingContent = headingContent,
		overlineContent = overlineContent,
		captionContent = captionContent,
		leadingContent = leadingContent,
		trailingContent = trailingContent,
		footerContent = footerContent,
		headingStyle = headingStyle,
		modifier = if (isLabel) modifier.padding(top = 10.dp) else modifier,
	)
}
