package org.jellyfin.androidtv.ui.base.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.TobyfinStyle

object ListControlDefaults {
	@ReadOnlyComposable
	@Composable
	fun colors(
		containerColor: Color = JellyfinTheme.colorScheme.listButton,
		focusedContainerColor: Color = JellyfinTheme.colorScheme.listButtonFocused,
	) = ListControlColors(
		containerColor = containerColor,
		focusedContainerColor = focusedContainerColor,
	)
}

@Composable
fun ListControl(
	headingContent: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	selected: Boolean = false,
	interactionSource: InteractionSource? = null,
	colors: ListControlColors = ListControlDefaults.colors(),
	overlineContent: (@Composable () -> Unit)? = null,
	captionContent: (@Composable () -> Unit)? = null,
	leadingContent: (@Composable () -> Unit)? = null,
	trailingContent: (@Composable () -> Unit)? = null,
	footerContent: (@Composable () -> Unit)? = null,
) {
	@Suppress("NAME_SHADOWING")
	val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val pressed by interactionSource.collectIsPressedAsState()

	val backgroundColor = when {
		!enabled -> colors.containerColor
		pressed -> colors.focusedContainerColor
		focused -> colors.focusedContainerColor
		selected -> TobyfinStyle.RowSelected
		else -> colors.containerColor
	}

	// Tobyfin: a white outline shows focus, a cyan outline the current choice.
	val borderColor = when {
		focused && enabled -> Color.White
		selected -> TobyfinStyle.Cyan
		else -> Color.Transparent
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.background(backgroundColor, LocalShapes.current.large)
			.border(2.dp, borderColor, LocalShapes.current.large)
			.clip(LocalShapes.current.large)
			.alpha(if (enabled) 1f else 0.4f),
	) {
		ListItemContent(
			headingContent = headingContent,
			overlineContent = overlineContent,
			captionContent = captionContent,
			leadingContent = leadingContent,
			trailingContent = trailingContent,
			footerContent = footerContent,
			headingStyle = JellyfinTheme.typography.listHeadline
				.copy(color = JellyfinTheme.colorScheme.listHeadline),
		)
	}
}
