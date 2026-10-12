package dev.daubney.scrobbled.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.daubney.scrobbled.data.history.NowPlaying
import dev.daubney.scrobbled.data.history.Summary
import dev.daubney.scrobbled.ui.ScrobbleRow
import dev.daubney.scrobbled.ui.theme.Glow
import dev.daubney.scrobbled.ui.theme.Panel
import dev.daubney.scrobbled.ui.theme.PanelText

private const val POLL_MS = 15_000L

@Composable
fun HomeScreen(vm: HomeViewModel, onSettings: () -> Unit) {
    val state by vm.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Full refresh on each resume, then poll now-playing every 15s while visible.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refresh()
            while (true) {
                kotlinx.coroutines.delay(POLL_MS)
                vm.refreshNowPlaying()
            }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Scrobbled", style = MaterialTheme.typography.headlineSmall)
                TextButton(onSettings) { Text("Settings") }
            }
        }
        item { NowPlayingPanel(state.nowPlaying) }
        state.summary?.let { item { SummaryRow(it) } }
        if (state.pending > 0) {
            item {
                Text(
                    if (state.pending == 1) "1 scrobble waiting to send" else "${state.pending} scrobbles waiting to send",
                    color = Glow, style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.recent.isNotEmpty()) {
            item { Text("Recent", style = MaterialTheme.typography.titleMedium) }
            items(state.recent, key = { it.key }) { ScrobbleRow(it) }
        } else if (state.loaded && state.error == null) {
            item { Text("No scrobbles yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The dark "hi-fi front panel" from the web UI, glowing amber while something plays. */
@Composable
private fun NowPlayingPanel(np: NowPlaying?) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(if (np != null) "NOW PLAYING" else "NOTHING PLAYING", style = MaterialTheme.typography.labelMedium, color = PanelText)
        if (np != null) {
            Text(np.track, style = MaterialTheme.typography.titleLarge, color = Glow)
            Text(np.artist, style = MaterialTheme.typography.bodyLarge, color = Glow)
            np.album?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = PanelText) }
        } else {
            Text("—", style = MaterialTheme.typography.titleLarge, color = PanelText)
        }
    }
}

@Composable
private fun SummaryRow(s: Summary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat("Scrobbles", s.scrobbles)
        Stat("Artists", s.artists)
        Stat("Tracks", s.tracks)
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text("%,d".format(value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
