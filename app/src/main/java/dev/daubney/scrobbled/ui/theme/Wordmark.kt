package dev.daubney.scrobbled.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.daubney.scrobbled.R

/** The lowercase "scrobbled" wordmark: paper with a lime "b" on dark, ink with a violet "b" on light. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, height: Dp = 32.dp) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val res = if (dark) R.drawable.wordmark_on_dark else R.drawable.wordmark_on_light
    Image(painterResource(res), contentDescription = "Scrobbled", modifier = modifier.height(height))
}
