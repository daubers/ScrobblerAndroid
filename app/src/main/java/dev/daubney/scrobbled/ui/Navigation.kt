package dev.daubney.scrobbled.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import dev.daubney.scrobbled.ui.add.AddScreen
import dev.daubney.scrobbled.ui.add.AddViewModel
import dev.daubney.scrobbled.ui.history.HistoryScreen
import dev.daubney.scrobbled.ui.history.HistoryViewModel
import dev.daubney.scrobbled.ui.home.HomeScreen
import dev.daubney.scrobbled.ui.home.HomeViewModel
import dev.daubney.scrobbled.ui.listen.ListenScreen
import dev.daubney.scrobbled.ui.listen.ListenViewModel
import dev.daubney.scrobbled.ui.settings.SettingsScreen
import dev.daubney.scrobbled.ui.settings.SettingsViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

const val SETTINGS_ROUTE = "settings"

enum class Destination(val route: String, val label: String) {
    Home("home", "Home"),
    History("history", "History"),
    Add("add", "Add"),
    Listen("listen", "Listen"),
}

@Composable
fun ScrobbledApp(services: AppServices, serverUrl: String, username: String, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current?.destination?.route == d.route,
                        onClick = { nav.navigate(d.route) { launchSingleTop = true } },
                        icon = {},
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Destination.Home.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Destination.Home.route) {
                val vm = viewModel<HomeViewModel>(factory = viewModelFactory { initializer { HomeViewModel(services.history, services.pendingScrobbles) } })
                HomeScreen(vm, onSettings = { nav.navigate(SETTINGS_ROUTE) })
            }
            composable(Destination.History.route) {
                val vm = viewModel<HistoryViewModel>(factory = viewModelFactory { initializer { HistoryViewModel(services.history) } })
                HistoryScreen(vm)
            }
            composable(Destination.Add.route) {
                val vm = viewModel<AddViewModel>(factory = viewModelFactory { initializer { AddViewModel(services.scrobbles, services.trackSearch) } })
                AddScreen(vm)
            }
            composable(Destination.Listen.route) {
                val vm = viewModel<ListenViewModel>(
                    factory = viewModelFactory { initializer { ListenViewModel(services.recorder, services.recognizer, services.scrobbles) } },
                )
                val demo by services.recognitionSettings.config.collectAsState()
                ListenScreen(vm, demoMode = demo?.isComplete != true)
            }
            composable(SETTINGS_ROUTE) {
                val vm = viewModel<SettingsViewModel>(
                    factory = viewModelFactory {
                        initializer { SettingsViewModel(serverUrl, username, services.recognitionSettings, services.queue, onSignOut) }
                    },
                )
                SettingsScreen(vm, onBack = { nav.popBackStack() })
            }
        }
    }
}
