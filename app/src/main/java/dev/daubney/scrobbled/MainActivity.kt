package dev.daubney.scrobbled

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
                if (state.connected) ScrobbledApp(app.history, app.queue.observeCount(), app.scrobbles, app.trackSearch, onSignOut = connectVm::disconnect) else ConnectScreen(connectVm)
            }
        }
    }
}
