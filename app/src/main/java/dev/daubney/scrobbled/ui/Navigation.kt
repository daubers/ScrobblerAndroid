package dev.daubney.scrobbled.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
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
fun ScrobbledApp() {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    Scaffold(
        bottomBar = {
            NavigationBar {
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
        NavHost(nav, startDestination = Destination.Home.route, modifier = Modifier.padding(padding)) {
            Destination.entries.forEach { d ->
                composable(d.route) { Placeholder(d.label) }
            }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier, contentAlignment = Alignment.Center) { Text(title) }
}
