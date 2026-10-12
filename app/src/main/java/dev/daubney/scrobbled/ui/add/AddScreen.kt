package dev.daubney.scrobbled.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun AddScreen(vm: AddViewModel) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add scrobble", style = MaterialTheme.typography.headlineSmall)
        val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        OutlinedTextField(s.artist, vm::onArtist, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Artist") }, keyboardOptions = caps)
        OutlinedTextField(s.track, vm::onTrack, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Track") }, keyboardOptions = caps)
        OutlinedTextField(s.album, vm::onAlbum, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Album (optional)") }, keyboardOptions = caps)

        Text("Played", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhenOption.entries.forEach { o ->
                FilterChip(
                    selected = s.whenOption == o, onClick = { vm.onWhen(o) }, label = { Text(o.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.outline),
                )
            }
        }

        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(vm::submit, Modifier.fillMaxWidth(), enabled = s.canSubmit) {
            Text(if (s.submitting) "Scrobbling…" else "Scrobble")
        }
    }
}
