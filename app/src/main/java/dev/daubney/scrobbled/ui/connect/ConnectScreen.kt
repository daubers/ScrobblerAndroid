package dev.daubney.scrobbled.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.daubney.scrobbled.ui.theme.Wordmark

@Composable
fun ConnectScreen(vm: ConnectViewModel) {
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterVertically),
    ) {
        Wordmark(height = 44.dp)
        Text("Connect to your scrobbling server", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            s.serverUrl, vm::onServerUrl, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Server address") }, placeholder = { Text("https://scrobbler.example.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            s.username, vm::onUsername, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Username") },
        )
        OutlinedTextField(
            s.password, vm::onPassword, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(vm::connect, Modifier.fillMaxWidth(), enabled = s.canSubmit) {
            Text(if (s.busy) "Connecting…" else "Connect")
        }
    }
}
