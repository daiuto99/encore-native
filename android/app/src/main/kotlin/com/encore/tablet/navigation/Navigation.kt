package com.encore.tablet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.encore.feature.library.LibraryScreen
import com.encore.feature.library.LibraryViewModel
import com.encore.feature.setlists.SetlistScreen
import com.encore.feature.setlists.SetlistViewModel
import com.encore.tablet.di.ViewModelFactory

/**
 * Navigation routes for the app.
 */
object Routes {
    const val LIBRARY = "library"
    const val SETLISTS = "setlists"
    // Future routes:
    // const val SONG_DETAIL = "song/{songId}"
    // const val SETLIST_DETAIL = "setlist/{setlistId}"
}

/**
 * Main navigation graph.
 *
 * Milestone 2: Bottom navigation between Library and Setlists.
 */
@Composable
fun EncoreNavHost(
    navController: NavHostController,
    viewModelFactory: ViewModelFactory,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY,
        modifier = modifier
    ) {
        // Library Screen
        composable(Routes.LIBRARY) {
            val viewModel: LibraryViewModel = viewModel(factory = viewModelFactory)
            LibraryScreen(
                viewModel = viewModel,
                onSongClick = { songId ->
                    // TODO: Navigate to song detail in future milestone
                    // navController.navigate("song/$songId")
                }
            )
        }

        // Setlist Overview Screen
        composable(Routes.SETLISTS) {
            val viewModel: SetlistViewModel = viewModel(factory = viewModelFactory)
            SetlistScreen(
                viewModel = viewModel,
                onSetlistClick = { setlistId ->
                    // TODO: Navigate to setlist detail
                    // navController.navigate("setlist/$setlistId")
                }
            )
        }

        // Future routes will be added here:
        // - Song Detail Screen
        // - Setlist Detail Screen (with sets and song management)
        // - Performance Mode Screen
    }
}
