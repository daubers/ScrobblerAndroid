package dev.daubney.scrobbled.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.daubney.scrobbled.R

/** Bricolage Grotesque (SIL OFL, see design/OFL.txt): the brand typeface, used for headings and labels. */
private val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque_regular, FontWeight.Normal),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

private val Base = Typography()

private fun TextStyle.brand() = copy(fontFamily = Bricolage, fontWeight = FontWeight.Bold)

/** Headings and titles use the brand face; body text stays on the system font for legibility. */
val AppTypography = Base.copy(
    headlineLarge = Base.headlineLarge.brand(),
    headlineMedium = Base.headlineMedium.brand(),
    headlineSmall = Base.headlineSmall.brand(),
    titleLarge = Base.titleLarge.brand(),
    titleMedium = Base.titleMedium.brand(),
    labelLarge = Base.labelLarge.brand(),
    labelMedium = Base.labelMedium.brand(),
)
