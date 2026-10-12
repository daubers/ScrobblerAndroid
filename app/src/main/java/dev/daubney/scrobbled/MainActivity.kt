package dev.daubney.scrobbled

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.daubney.scrobbled.ui.ScrobbledApp
import dev.daubney.scrobbled.ui.theme.ScrobbledTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScrobbledTheme { ScrobbledApp() } }
    }
}
