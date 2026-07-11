package com.laioffer.spotify.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.laioffer.spotify.player.PlayerViewModel
import com.laioffer.spotify.ui.favorites.FavoritesScreen
import com.laioffer.spotify.ui.favorites.FavoritesViewModel
import com.laioffer.spotify.ui.home.HomeScreen
import com.laioffer.spotify.ui.home.HomeViewModel
import com.laioffer.spotify.ui.player.PlayerBar
import com.laioffer.spotify.ui.playlist.PlaylistScreen
import com.laioffer.spotify.ui.playlist.PlaylistViewModel

private const val HomeRoute = "home"
private const val FavoritesRoute = "favorites"
private const val PlaylistRoute = "playlist/{albumId}"

@Composable
fun SpotifyApp(
    navController: NavHostController = rememberNavController(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            Column {
                PlayerBar(
                    state = playerState,
                    onTogglePlay = playerViewModel::togglePlay,
                    onSeek = playerViewModel::seekTo,
                )
                NavigationBar {
                    listOf(
                        Triple(HomeRoute, "Home", Icons.Default.Home),
                        Triple(FavoritesRoute, "Favorites", Icons.Default.Favorite),
                    ).forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = { navController.navigateTopLevel(route) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            composable(HomeRoute) {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onAlbumClick = { navController.navigate("playlist/$it") },
                    onRetry = { viewModel.refresh(force = true) },
                )
            }
            composable(FavoritesRoute) {
                val viewModel: FavoritesViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                FavoritesScreen(
                    state = state,
                    onAlbumClick = { navController.navigate("playlist/$it") },
                )
            }
            composable(
                route = PlaylistRoute,
                arguments = listOf(navArgument("albumId") { type = NavType.IntType }),
            ) {
                val viewModel: PlaylistViewModel = hiltViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                PlaylistScreen(
                    state = state,
                    playerState = playerState,
                    onBack = { navController.popBackStack() },
                    onRetry = viewModel::retry,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onSongClick = { song ->
                        state.album?.let { album -> playerViewModel.load(song, album) }
                    },
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
