package com.auramusic.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auramusic.app.core.theme.XploitMusicTheme
import com.auramusic.app.presentation.components.MiniPlayerBar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Mutable state observed by Compose to trigger navigation to player
    private var _openPlayerState = mutableStateOf(false)
    private var _openSpotifyImportState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _openPlayerState.value = isPlayerIntent(intent)
        _openSpotifyImportState.value = isSpotifyImportIntent(intent)
        setContent {
            XploitMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    XploitAppNavigation(
                        openPlayer = _openPlayerState.value,
                        onPlayerOpened = { _openPlayerState.value = false },
                        openSpotifyImport = _openSpotifyImportState.value,
                        onSpotifyImportOpened = { _openSpotifyImportState.value = false }
                    )
                }
            }
        }
    }

    // Handle taps when Activity is already running (singleTask/singleTop)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isSpotifyImportIntent(intent)) {
            _openSpotifyImportState.value = true
        }
        if (isPlayerIntent(intent)) {
            _openPlayerState.value = true
        }
    }

    private fun isSpotifyImportIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        return data.scheme == "app" &&
            data.host == "xploitmusic" &&
            data.path == "/spotify-import"
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
fun XploitAppNavigation(
    openPlayer: Boolean = false,
    onPlayerOpened: () -> Unit = {},
    openSpotifyImport: Boolean = false,
    onSpotifyImportOpened: () -> Unit = {}
) {
    val navController = rememberNavController()

    // ── PlayerViewModel scoped to the Activity ───────────────────────────────
    // This is the single source of truth for playback state. Scoping to the
    // Activity (not the NavBackStackEntry) guarantees it survives navigation
    // between Home / Library / Local tabs and configuration changes.
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerViewModel: com.auramusic.app.presentation.player.PlayerViewModel =
        hiltViewModel(context as androidx.activity.ComponentActivity)

    // If launched from notification/deep link, navigate to player immediately
    // CRITICAL: Reset the trigger after navigating to prevent infinite re-navigation
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            navController.navigate("player") {
                launchSingleTop = true
            }
            onPlayerOpened()
        }
    }

    LaunchedEffect(openSpotifyImport) {
        if (openSpotifyImport) {
            navController.navigate("spotifyImport") {
                launchSingleTop = true
            }
            onSpotifyImportOpened()
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem("home",    "Inicio",    Icons.Default.Home),
        BottomNavItem("search",  "Buscar",    Icons.Default.Search),
        BottomNavItem("library", "Biblioteca", Icons.Default.LibraryMusic)
    )

    // Track current route to hide bottom bar on player screen
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnPlayerScreen = currentRoute == "player"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            // Hide the entire bottom bar area when the full player is open
            if (!isOnPlayerScreen) {
                Column {
                    // ── Mini-Player Overlay ───────────────────────────────────────
                    // Animates in/out based on whether a song is loaded.
                    // Tapping navigates to the full PlayerScreen.
                    MiniPlayerBar(
                        viewModel = playerViewModel,
                        onTap = {
                            navController.navigate("player") {
                                launchSingleTop = true
                            }
                        }
                    )

                    // ── Bottom Navigation Bar ─────────────────────────────────────
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp
                    ) {
                        Column {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                            )
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                bottomNavItems.forEach { item ->
                                    val selected = navBackStackEntry?.destination?.hierarchy?.any {
                                        it.route == item.route
                                    } == true

                                    NavigationBarItem(
                                        icon = {
                                            Icon(
                                                item.icon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
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
                                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        )
                                    )
                                }
                            }
                        }
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
                    }
                )
            }
            composable("search") {
                com.auramusic.app.presentation.search.SearchScreen(
                    onSongClick = { song ->
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
                    },
                    onNavigateToSpotifyImport = {
                        navController.navigate("spotifyImport")
                    },
                    onNavigateToDownloads = {
                        navController.navigate("downloads")
                    },
                    onNavigateToLocal = {
                        navController.navigate("local")
                    }
                )
            }
            composable("spotifyImport") {
                com.auramusic.app.presentation.library.SpotifyImportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("downloads") {
                com.auramusic.app.presentation.downloads.DownloadsScreen(
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
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "player",
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "app://xploitmusic/player" }
                )
            ) {
                com.auramusic.app.presentation.player.PlayerScreen(
                    onBackClick = { navController.popBackStack() },
                    onEmptySession = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    viewModel = playerViewModel
                )
            }
        }
    }
}

