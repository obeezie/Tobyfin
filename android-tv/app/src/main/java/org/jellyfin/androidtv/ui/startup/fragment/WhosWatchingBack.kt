package org.jellyfin.androidtv.ui.startup.fragment

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * One Back press runs [onBack] for this overlay. Compose on TV otherwise spends the first press on
 * clearing focus from the highlighted button, so Back took two presses.
 */
internal fun Modifier.onBackPress(onBack: () -> Unit) = onPreviewKeyEvent { event ->
	if (event.key != Key.Back) return@onPreviewKeyEvent false
	if (event.type == KeyEventType.KeyUp) onBack()
	true
}

/** [onBackPress] plus the system Back, for when nothing inside the overlay has focus. */
@Composable
internal fun OverlayBackHandler(onBack: () -> Unit) = BackHandler(onBack = onBack)
