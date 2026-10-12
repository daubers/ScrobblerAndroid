package dev.daubney.scrobbled.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            TextButton(onBack) { Text("Back") }
        }

        Text("Account", style = MaterialTheme.typography.titleMedium)
        Text("${s.username} on ${s.serverUrl}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(vm::requestSignOut, Modifier.fillMaxWidth()) { Text("Sign out") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Music recognition", style = MaterialTheme.typography.titleMedium)
        Text(
            if (s.recognitionActive) "Listen uses ACRCloud."
            else "Demo mode: Listen returns made-up songs. Add your ACRCloud project keys to identify real music.",
            color = if (s.recognitionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            s.host, vm::onHost, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Host") }, placeholder = { Text("identify-eu-west-1.acrcloud.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(s.accessKey, vm::onAccessKey, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Access key") })
        OutlinedTextField(
            s.accessSecret, vm::onAccessSecret, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Access secret") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        s.keysError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        s.keysMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Button(vm::saveKeys, Modifier.fillMaxWidth()) { Text("Save keys") }
        Text(
            "Leave all three empty and save to go back to demo mode.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (s.confirmingSignOut) {
        AlertDialog(
            onDismissRequest = vm::cancelSignOut,
            title = { Text("Sign out?") },
            text = {
                Text(
                    "${s.pendingScrobbles} scrobble${if (s.pendingScrobbles == 1) " hasn't" else "s haven't"} been sent yet " +
                        "and will be discarded. Stay signed in and they'll send when you're back online.",
                )
            },
            confirmButton = { TextButton(vm::confirmSignOut) { Text("Discard and sign out") } },
            dismissButton = { TextButton(vm::cancelSignOut) { Text("Stay signed in") } },
        )
    }
}
