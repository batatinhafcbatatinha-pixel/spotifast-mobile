package io.github.batatinha.spotifast.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF1ED760)
val Background = Color(0xFF0F1114)
val Surface = Color(0xFF181B20)
val Muted = Color(0xFFA5ADBA)

private val colors = darkColorScheme(
    primary = Green,
    onPrimary = Background,
    secondary = Green,
    background = Background,
    onBackground = Color(0xFFE8EAED),
    surface = Background,
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Surface,
    onSurfaceVariant = Muted,
    surfaceContainer = Surface,
    surfaceContainerHigh = Color(0xFF20242A),
)

@Composable
fun SpotifastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
