package dev.daubney.scrobbled.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.daubney.scrobbled.data.search.TrackResult
import dev.daubney.scrobbled.ui.TrackFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(vm: AddViewModel) {
    val s by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Text("Add scrobble", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        PrimaryTabRow(selectedTabIndex = s.tab.ordinal) {
            AddTab.entries.forEach { t ->
                Tab(
                    selected = s.tab == t, onClick = { vm.onTab(t) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = { Text(if (t == AddTab.Search) "Search" else "Manual") },
                )
            }
        }
        when (s.tab) {
            AddTab.Search -> SearchTab(s, vm)
            AddTab.Manual -> ManualTab(s, vm)
        }
    }
}

@Composable
private fun SearchTab(s: AddState, vm: AddViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            s.query, vm::onQuery, Modifier.fillMaxWidth().padding(top = 12.dp), singleLine = true,
            label = { Text("Search MusicBrainz") }, placeholder = { Text("artist and/or track") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        )
        Button(vm::search, Modifier.fillMaxWidth(), enabled = s.query.isNotBlank() && !s.searching) {
            Text(if (s.searching) "Searching…" else "Search")
        }
        s.searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            s.searching -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.searched && s.results.isEmpty() && s.searchError == null ->
                Text("No matches. Try fewer words, or add it manually.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(s.results, key = { it.mbid }) { ResultRow(it) { vm.pick(it) } }
            }
        }
    }
}

@Composable
private fun ResultRow(r: TrackResult, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(r.track, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOfNotNull(r.artist, r.album).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ManualTab(s: AddState, vm: AddViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackFields(s.artist, s.track, s.album, vm::onArtist, vm::onTrack, vm::onAlbum)
        if (s.mbid.isNotEmpty()) {
            Text("Matched on MusicBrainz", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

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
