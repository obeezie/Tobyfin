package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jellyfin.androidtv.ui.base.dialog.DialogBase

internal val LocalSettingsBackAction = compositionLocalOf<MutableState<(() -> Unit)?>> { error("Settings dialog required") }

@Composable
fun SettingsDialog(
	visible: Boolean,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
	screen: @Composable BoxScope.() -> Unit,
) {
	val backAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val duration = 400
	DialogBase(
		visible = visible,
		onDismissRequest = { backAction.value?.invoke() ?: onDismissRequest() },
		modifier = modifier,
		contentAlignment = Alignment.TopEnd,
		enterTransition = slideInHorizontally(
			initialOffsetX = { it },
			animationSpec = tween(duration, easing = FastOutSlowInEasing)
		) + fadeIn(
			animationSpec = tween(duration)
		),
		exitTransition = slideOutHorizontally(
			targetOffsetX = { it },
			animationSpec = tween(duration, easing = FastOutSlowInEasing)
		) + fadeOut(
			animationSpec = tween(duration)
		),
	) {
		CompositionLocalProvider(LocalSettingsBackAction provides backAction) {
        SettingsLayout {
			screen()
        }
		}
	}
}
