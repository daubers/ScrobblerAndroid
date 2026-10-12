package dev.daubney.scrobbled.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** The artist / track / album inputs shared by every screen that lets the user edit a track before submitting. */
@Composable
fun TrackFields(
    artist: String, track: String, album: String,
    onArtist: (String) -> Unit, onTrack: (String) -> Unit, onAlbum: (String) -> Unit,
) {
    val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(artist, onArtist, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Artist") }, keyboardOptions = caps)
        OutlinedTextField(track, onTrack, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Track") }, keyboardOptions = caps)
        OutlinedTextField(album, onAlbum, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Album (optional)") }, keyboardOptions = caps)
    }
}
