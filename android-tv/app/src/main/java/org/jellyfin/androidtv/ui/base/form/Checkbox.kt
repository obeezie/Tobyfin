package org.jellyfin.androidtv.ui.base.form

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.ui.base.TobyfinStyle

/** Tobyfin: on/off settings show as a switch, filled with the purple-to-cyan gradient when on. */
@Composable
fun Checkbox(
	checked: Boolean,
	modifier: Modifier = Modifier,
) {
	val knobOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "switch knob")

	Box(
		modifier = modifier
			.size(width = 38.dp, height = 22.dp)
			.then(
				if (checked) Modifier.background(TobyfinStyle.Gradient, CircleShape)
				else Modifier.background(TobyfinStyle.SwitchOff, CircleShape)
			),
		contentAlignment = Alignment.CenterStart,
	) {
		Box(
			modifier = Modifier
				.padding(start = 3.dp)
				.offset(x = knobOffset)
				.size(16.dp)
				.background(if (checked) Color.White else TobyfinStyle.KnobOff, CircleShape)
		)
	}
}
