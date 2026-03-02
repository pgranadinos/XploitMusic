package com.auramusic.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auramusic.app.core.theme.XploitMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Mutable state observed by Compose to trigger navigation to player
    private var _openPlayerState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _openPlayerState.value = isPlayerIntent(intent)
        setContent {
            XploitMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XploitAppNavigation(openPlayerState = _openPlayerState)
                }
            }
        }
    }

    // Handle taps when Activity is already running (singleTask/singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isPlayerIntent(intent)) {
            // Flip the state to trigger LaunchedEffect re-navigation
            _openPlayerState.value = true
        }
    }

    /**
     * Checks if the intent is a deep link to the player screen.
     * Supports both:
     * - Deep link URI: app://xploitmusic/player
     * - Legacy: OPEN_PLAYER boolean extra
     */
    private fun isPlayerIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val data = intent.data
        if (data != null && data.scheme == "app" && data.host == "xploitmusic" && data.path == "/player") {
            return true
        }
        return intent.getBooleanExtra("OPEN_PLAYER", false)
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun XploitAppNavigation(openPlayer: Boolean = false) {
    val navController = rememberNavController()

    // Scope ViewModel to the Activity so it persists across navigation
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerViewModel: com.auramusic.app.presentation.player.PlayerViewModel =
        hiltViewModel(context as androidx.activity.ComponentActivity)

    // If launched from notification, navigate to player immediately
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            navController.navigate("player") {
                launchSingleTop = true
            }
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem("home", "Inicio", Icons.Default.Home),
        BottomNavItem("library", "Biblioteca", Icons.Default.List),
        BottomNavItem("local", "Local", Icons.Default.PlayArrow)
    )

    // Track current route to hide bottom bar on player screen
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute != "player"

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                com.auramusic.app.presentation.home.HomeScreen(
                    onSongClick = { song, playlist ->
                        playerViewModel.playSong(song, playlist)
                        navController.navigate("player")
                    },
                    onSearchSongClick = { song ->
                        playerViewModel.playSingleSong(song)
                        navController.navigate("player")
                    }
                )
            }
            composable("library") {
                com.auramusic.app.presentation.library.LibraryScreen(
                    onSongClick = { song, playlist ->
                        playerViewModel.playSong(song, playlist)
                        navController.navigate("player")
                    }
                )
            }
            composable("local") {
                com.auramusic.app.presentation.local.LocalMusicScreen(
                    onSongClick = { song, playlist ->
                        playerViewModel.playSong(song, playlist)
                        navController.navigate("player")
                    }
                )
            }
            composable("player") {
                com.auramusic.app.presentation.player.PlayerScreen(
                    onBackClick = { navController.popBackStack() },
                    viewModel = playerViewModel
                )
            }
        }
    }
}

