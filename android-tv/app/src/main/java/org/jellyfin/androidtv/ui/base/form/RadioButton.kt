package org.jellyfin.androidtv.ui.base.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.TobyfinStyle

/** Tobyfin: the current choice shows a gradient dot inside a ring. */
@Composable
fun RadioButton(
	checked: Boolean,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
			.size(20.dp)
			.then(
				if (checked) Modifier.border(2.dp, TobyfinStyle.Gradient, CircleShape)
				else Modifier.border(2.dp, TobyfinStyle.Faint, CircleShape)
			),
		contentAlignment = Alignment.Center,
	) {
		if (checked) {
			Box(
				Modifier
					.size(10.dp)
					.background(TobyfinStyle.Gradient, CircleShape)
			)
		}
	}
}
