package dev.daubney.scrobbled

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.daubney.scrobbled.ui.ScrobbledApp
import dev.daubney.scrobbled.ui.connect.ConnectScreen
import dev.daubney.scrobbled.ui.connect.ConnectViewModel
import dev.daubney.scrobbled.ui.theme.ScrobbledTheme

class MainActivity : ComponentActivity() {
    private val connectVm by viewModels<ConnectViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ConnectViewModel((application as ScrobbledApplication).auth) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ScrobbledApplication
        setContent {
            ScrobbledTheme {
                val state by connectVm.state.collectAsState()
                LaunchedEffect(Unit) { app.sessionExpired.collect { connectVm.onSessionExpired() } }
                // After (re)connecting, send anything that queued up while we were signed out.
                LaunchedEffect(state.connected) { if (state.connected) app.scheduleFlush() }
                if (state.connected) {
                    val creds = app.auth.current()
                    ScrobbledApp(app.services, creds?.serverUrl.orEmpty(), creds?.username.orEmpty(), onSignOut = connectVm::disconnect)
                } else {
                    ConnectScreen(connectVm)
                }
            }
        }
    }
}
