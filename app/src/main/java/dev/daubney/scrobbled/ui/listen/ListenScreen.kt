package dev.daubney.scrobbled.ui.listen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.daubney.scrobbled.ui.TrackFields
import dev.daubney.scrobbled.ui.theme.Glow
import dev.daubney.scrobbled.ui.theme.Panel

@Composable
fun ListenScreen(vm: ListenViewModel) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.onPermissionDenied()
    }
    val listen = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.start() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Listen", style = MaterialTheme.typography.headlineSmall)
        when (s.phase) {
            ListenPhase.Idle -> Idle(s, listen)
            ListenPhase.Recording -> Working("Listening…", s.progress, vm::cancel)
            ListenPhase.Identifying -> Working("Identifying…", null, vm::cancel)
            ListenPhase.Confirm -> Confirm(s, vm, listen)
            ListenPhase.NoMatch -> NoMatch(listen)
        }
    }
}

@Composable
private fun Idle(s: ListenState, onListen: () -> Unit) {
    Text(
        "Hold your phone near the music, then tap to listen for about 10 seconds.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ListenButton(onListen)
    s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
}

/** A big round button in the web UI's amber-on-dark panel style. */
@Composable
private fun ListenButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(168.dp).clip(CircleShape).background(Panel).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("Listen", style = MaterialTheme.typography.titleLarge, color = Glow)
        }
    }
}

@Composable
private fun Working(label: String, progress: Float?, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth()) else CircularProgressIndicator()
        TextButton(onCancel) { Text("Cancel") }
    }
}

@Composable
private fun Confirm(s: ListenState, vm: ListenViewModel, onListenAgain: () -> Unit) {
    Text("Is this right? Fix anything that's off before scrobbling.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    TrackFields(s.artist, s.track, s.album, vm::onArtist, vm::onTrack, vm::onAlbum)
    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(vm::scrobble, Modifier.fillMaxWidth(), enabled = s.canSubmit) { Text(if (s.busy) "Working…" else "Scrobble") }
    OutlinedButton(vm::setNowPlaying, Modifier.fillMaxWidth(), enabled = s.canSubmit) { Text("Set as now playing") }
    TextButton(onListenAgain, Modifier.fillMaxWidth()) { Text("Not right? Listen again") }
    TextButton(vm::cancel, Modifier.fillMaxWidth()) { Text("Discard") }
}

@Composable
private fun NoMatch(onListen: () -> Unit) {
    Text("Couldn't identify that. Try again, closer to the speaker.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onListen, Modifier.fillMaxWidth()) { Text("Try again") }
}
