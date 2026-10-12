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

// Brand palette from ScrobbledDesign: Ink #15131A, Paper #F4F1EA, Violet #8B6CFF, Lime #C8F560, Night #1F1C28.
// Raw violet is too faint for text on paper, so light mode uses a deeper violet and dark mode a lighter one.
// Every Material role is set explicitly: any role left out falls back to Material's purple baseline.
// Text pairs are >= 4.5:1 and field borders >= 3:1 in both themes.

private val Light = lightColorScheme(
    primary = Color(0xFF6444DB), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDD5FF), onPrimaryContainer = Color(0xFF2A1C73),
    secondary = Color(0xFF4A4658), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1DBF5), onSecondaryContainer = Color(0xFF2A2538),
    tertiary = Color(0xFF4A4658), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1DBF5), onTertiaryContainer = Color(0xFF2A2538),
    background = Color(0xFFF4F1EA), onBackground = Color(0xFF15131A),
    surface = Color(0xFFF4F1EA), onSurface = Color(0xFF15131A),
    surfaceVariant = Color(0xFFE6E1D6), onSurfaceVariant = Color(0xFF4A4658),
    surfaceTint = Color(0xFF6444DB),
    surfaceDim = Color(0xFFE2DED3), surfaceBright = Color(0xFFFAF8F3),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8F6F0),
    surfaceContainer = Color(0xFFFAF8F3), surfaceContainerHigh = Color(0xFFFDFCF9),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFF847F92), outlineVariant = Color(0xFFD6D1C6),
    error = Color(0xFFB3412E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6D5CD), onErrorContainer = Color(0xFF4A1409),
    inverseSurface = Color(0xFF1F1C28), inverseOnSurface = Color(0xFFF4F1EA), inversePrimary = Color(0xFFA892FF),
    scrim = Color(0xFF000000),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA892FF), onPrimary = Color(0xFF1A0F4D),
    primaryContainer = Color(0xFF43359A), onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFFC4BFD0), onSecondary = Color(0xFF1F1C28),
    secondaryContainer = Color(0xFF3A3548), onSecondaryContainer = Color(0xFFF4F1EA),
    tertiary = Color(0xFFC8F560), onTertiary = Color(0xFF1F2A00),
    tertiaryContainer = Color(0xFF3A4A0C), onTertiaryContainer = Color(0xFFE2FFA0),
    background = Color(0xFF1F1C28), onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF1F1C28), onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF2E2A3B), onSurfaceVariant = Color(0xFFC4BFD0),
    surfaceTint = Color(0xFFA892FF),
    surfaceDim = Color(0xFF181620), surfaceBright = Color(0xFF383349),
    surfaceContainerLowest = Color(0xFF15131A), surfaceContainerLow = Color(0xFF24212F),
    surfaceContainer = Color(0xFF2A2636), surfaceContainerHigh = Color(0xFF353043),
    surfaceContainerHighest = Color(0xFF403A50),
    outline = Color(0xFF8B86A0), outlineVariant = Color(0xFF3B3649),
    error = Color(0xFFF0907C), onError = Color(0xFF3B0D05),
    errorContainer = Color(0xFF7A2A1B), onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Color(0xFFF4F1EA), inverseOnSurface = Color(0xFF1F1C28), inversePrimary = Color(0xFF6444DB),
    scrim = Color(0xFF000000),
)

/** A dark "hi-fi front panel" card with lime text, used for now-playing and Listen. */
@Immutable
class PanelColors(val background: Color, val edge: Color, val text: Color, val glow: Color)

private val LightPanel = PanelColors(Color(0xFF15131A), Color(0xFF2E2A3B), Color(0xFFCFCADA), Color(0xFFC8F560))
private val DarkPanel = PanelColors(Color(0xFF2E2A3B), Color(0xFF4A4560), Color(0xFFD6D1E0), Color(0xFFC8F560))

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
        MaterialTheme(
            colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content,
        )
    }
}
