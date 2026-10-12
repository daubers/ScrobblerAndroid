package dev.daubney.scrobbled.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Mirrors the web UI's paper/ink palette (frontend/css/style.css in ScrobblingService).
val Glow = Color(0xFFF2A541)
val Panel = Color(0xFF1C2330)
val PanelText = Color(0xFFB9C3D2)

private val Light = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    background = Color(0xFFE8ECF1),
    surface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFFF4F6F9),
    onBackground = Color(0xFF18202C),
    onSurface = Color(0xFF18202C),
    onSurfaceVariant = Color(0xFF4A5566),
    outline = Color(0xFFC9D0DA),
    error = Color(0xFFB3412E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FB4F0),
    background = Color(0xFF12171F),
    surface = Color(0xFF12171F),
    surfaceVariant = Color(0xFF19202B),
    onBackground = Color(0xFFE3E8EF),
    onSurface = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFFA7B1C0),
    outline = Color(0xFF2C3543),
    error = Color(0xFFE0806C),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun ScrobbledTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = AppShapes, content = content)
}
