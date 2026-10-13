package org.jellyfin.mobile.ui.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = remember {
        @Suppress("MagicNumber")
        // Tobyfin: dark navy with purple-to-cyan accents (prototype/tobyfin-menus-prototype.html).
        darkColors(
            primary = Color(0xFF3CC4F5),
            primaryVariant = Color(0xFF080C1D),
            secondary = Color(0xFFB27DFF),
            background = Color(0xFF080C1D),
            surface = Color(0xFF121A35),
            error = Color(0xFFFF8A8A),
            onPrimary = Color(0xFF0B1022),
            onSecondary = Color.White,
            onBackground = Color(0xFFEEF0FA),
            onSurface = Color(0xFFEEF0FA),
            onError = Color.White,
        )
    }
    MaterialTheme(
        colors = colors,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
        ),
        content = content,
    )
}
