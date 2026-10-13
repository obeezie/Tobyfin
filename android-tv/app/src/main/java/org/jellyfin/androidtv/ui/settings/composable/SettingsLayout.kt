package org.jellyfin.androidtv.ui.settings.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import org.jellyfin.androidtv.ui.shared.toolbar.Logo
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.settings.Routes
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.TobyfinStyle
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.design.Tokens

@Composable
fun SettingsLayout(
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	Box(
		modifier = modifier
			.padding(Tokens.Space.spaceMd)
			.clip(LocalShapes.current.large)
			// Tobyfin: the same navy as Customize Discovery, so no gray strip shows above it.
			.background(Brush.verticalGradient(listOf(TobyfinStyle.PanelTop, TobyfinStyle.PanelBottom)))
			.border(1.dp, TobyfinStyle.Line, LocalShapes.current.large)
			.then(if (LocalRouter.current.backStack.lastOrNull()?.route == Routes.DISCOVERY) Modifier.fillMaxWidth() else Modifier.width(350.dp))
			.fillMaxHeight()
			.focusRestorer(),
		content = {
            Column {
                Logo(modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 8.dp).width(120.dp).height(36.dp))
                Box(modifier = Modifier.weight(1f), content = content)
            }
        }
	)
}
