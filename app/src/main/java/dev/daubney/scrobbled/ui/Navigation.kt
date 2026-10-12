package dev.daubney.scrobbled.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

enum class Destination(val route: String, val label: String) {
    Home("home", "Home"),
    History("history", "History"),
    Add("add", "Add"),
    Listen("listen", "Listen"),
}

@Composable
fun ScrobbledApp(services: AppServices, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = current?.destination?.route == d.route,
                        onClick = { nav.navigate(d.route) { launchSingleTop = true } },
                        icon = {},
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.outline),
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Destination.Home.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Destination.Home.route) {
                val vm = viewModel<HomeViewModel>(factory = viewModelFactory { initializer { HomeViewModel(services.history, services.pendingScrobbles) } })
                HomeScreen(vm, onSignOut)
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
                ListenScreen(vm)
            }
        }
    }
}
