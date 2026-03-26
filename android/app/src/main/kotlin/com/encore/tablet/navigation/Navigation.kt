package com.encore.tablet.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.encore.feature.library.LibraryScreen
import com.encore.feature.library.LibraryViewModel
import com.encore.tablet.di.ViewModelFactory

/**
 * Navigation routes for the app.
 */
object Routes {
    const val LIBRARY = "library"
    // Future routes:
    // const val SONG_DETAIL = "song/{songId}"
    // const val SETLIST_OVERVIEW = "setlist"
    // const val SETLIST_DETAIL = "setlist/{setlistId}"
}

/**
 * Main navigation graph.
 *
 * Milestone 2: Library is the start destination.
 * Future milestones will add more routes.
 */
@Composable
fun EncoreNavHost(
    navController: NavHostController,
    viewModelFactory: ViewModelFactory
) {
    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        // Library Screen
        composable(Routes.LIBRARY) {
            val viewModel: LibraryViewModel = viewModel(factory = viewModelFactory)
            LibraryScreen(
                viewModel = viewModel,
                onSongClick = { songId ->
                    // TODO: Navigate to song detail in future milestone
                    // navController.navigate("song/$songId")
                },
                onImportClick = {
                    // TODO: Navigate to import flow in future task
                    // For now, this is a placeholder
                }
            )
        }

        // Future routes will be added here:
        // - Song Detail Screen
        // - Setlist Overview Screen
        // - Setlist Detail Screen
        // - Performance Mode Screen
    }
}
