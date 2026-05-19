package com.babyphotos.archive.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Album : Screen("album")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "首页", Icons.Default.Home),
    BottomNavItem(Screen.Album, "相册", Icons.Default.PhotoLibrary),
    BottomNavItem(Screen.History, "记录", Icons.Default.History),
    BottomNavItem(Screen.Settings, "设置", Icons.Default.Settings),
)

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == item.screen.route
                            } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.then(Modifier) // consume innerPadding in screens
        ) {
            composable(Screen.Home.route) {
                com.babyphotos.archive.ui.screen.home.HomeScreen(
                    paddingValues = innerPadding
                )
            }
            composable(Screen.Album.route) {
                com.babyphotos.archive.ui.screen.album.AlbumScreen(paddingValues = innerPadding)
            }
            composable(Screen.History.route) {
                com.babyphotos.archive.ui.screen.history.HistoryScreen(paddingValues = innerPadding)
            }
            composable(Screen.Settings.route) {
                com.babyphotos.archive.ui.screen.settings.SettingsScreen(paddingValues = innerPadding)
            }
        }
    }
}
