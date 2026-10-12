package dev.daubney.scrobbled.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Palette from the web UI (frontend/css/style.css in ScrobblingService). Every Material role is set
// explicitly: any role left out falls back to Material's purple baseline, which clashes and (in dark
// mode) gave low-contrast text. Text pairs are >= 4.5:1 and field borders >= 3:1 in both themes.

private val Light = lightColorScheme(
    primary = Color(0xFF2F5DA8), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E0F7), onPrimaryContainer = Color(0xFF0E2A57),
    secondary = Color(0xFF5B6B82), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDD6E2), onSecondaryContainer = Color(0xFF18202C),
    tertiary = Color(0xFF5B6B82), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDD6E2), onTertiaryContainer = Color(0xFF18202C),
    background = Color(0xFFE8ECF1), onBackground = Color(0xFF18202C),
    surface = Color(0xFFE8ECF1), onSurface = Color(0xFF18202C),
    surfaceVariant = Color(0xFFDCE2EA), onSurfaceVariant = Color(0xFF434F60),
    surfaceTint = Color(0xFF2F5DA8),
    surfaceDim = Color(0xFFDDE2E9), surfaceBright = Color(0xFFF4F6F9),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF2F6),
    surfaceContainer = Color(0xFFF4F6F9), surfaceContainerHigh = Color(0xFFFAFBFC),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF6B7789), outlineVariant = Color(0xFFC9D0DA),
    error = Color(0xFFB3412E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6D5CD), onErrorContainer = Color(0xFF4A1409),
    inverseSurface = Color(0xFF18202C), inverseOnSurface = Color(0xFFE8ECF1), inversePrimary = Color(0xFF8FB4F0),
    scrim = Color(0xFF000000),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FB4F0), onPrimary = Color(0xFF0A1D3D),
    primaryContainer = Color(0xFF26416D), onPrimaryContainer = Color(0xFFD9E5FB),
    secondary = Color(0xFF8795AA), onSecondary = Color(0xFF0F1622),
    secondaryContainer = Color(0xFF2D3A50), onSecondaryContainer = Color(0xFFE3E8EF),
    tertiary = Color(0xFF8795AA), onTertiary = Color(0xFF0F1622),
    tertiaryContainer = Color(0xFF2D3A50), onTertiaryContainer = Color(0xFFE3E8EF),
    background = Color(0xFF12171F), onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF12171F), onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF273142), onSurfaceVariant = Color(0xFFB3BDCC),
    surfaceTint = Color(0xFF8FB4F0),
    surfaceDim = Color(0xFF0E131A), surfaceBright = Color(0xFF2A3342),
    surfaceContainerLowest = Color(0xFF0C1016), surfaceContainerLow = Color(0xFF151B25),
    surfaceContainer = Color(0xFF19202B), surfaceContainerHigh = Color(0xFF222B38),
    surfaceContainerHighest = Color(0xFF2A3442),
    outline = Color(0xFF7D8AA0), outlineVariant = Color(0xFF2C3543),
    error = Color(0xFFF0907C), onError = Color(0xFF3B0D05),
    errorContainer = Color(0xFF7A2A1B), onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Color(0xFFE3E8EF), inverseOnSurface = Color(0xFF18202C), inversePrimary = Color(0xFF2F5DA8),
    scrim = Color(0xFF000000),
)

/** The web UI's "hi-fi front panel": a dark card with amber text, used for now-playing and Listen. */
@Immutable
class PanelColors(val background: Color, val edge: Color, val text: Color, val glow: Color)

private val LightPanel = PanelColors(Color(0xFF1C2330), Color(0xFF2A3445), Color(0xFFB9C3D2), Color(0xFFF2A541))
private val DarkPanel = PanelColors(Color(0xFF232D3E), Color(0xFF3A4860), Color(0xFFC5CEDB), Color(0xFFF2A541))

private val LocalPanelColors = staticCompositionLocalOf { LightPanel }

object AppColors {
    val panel: PanelColors
        @Composable @ReadOnlyComposable get() = LocalPanelColors.current
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun ScrobbledTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPanelColors provides if (dark) DarkPanel else LightPanel) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = AppShapes, content = content)
    }
}
