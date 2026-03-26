package com.encore.tablet.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.encore.feature.library.LibraryScreen
import com.encore.feature.library.LibraryViewModel
import com.encore.feature.setlists.SetlistDetailScreen
import com.encore.feature.setlists.SetlistScreen
import com.encore.feature.setlists.SetlistViewModel
import com.encore.tablet.di.ViewModelFactory

/**
 * Navigation routes for the app.
 */
object Routes {
    const val LIBRARY = "library"
    const val SETLISTS = "setlists"
    const val SETLIST_DETAIL = "setlist/{setlistId}"

    fun setlistDetail(setlistId: String) = "setlist/$setlistId"
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
    // Shared ViewModels across navigation
    val setlistViewModel: SetlistViewModel = viewModel(factory = viewModelFactory)

    // State for "Add to Setlist" dialog
    var songToAdd by remember { mutableStateOf<String?>(null) }

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
                },
                onAddToSetlist = { songId ->
                    songToAdd = songId
                }
            )

            // Show setlist selection dialog
            songToAdd?.let { songId ->
                SetlistSelectionDialog(
                    setlistViewModel = setlistViewModel,
                    onDismiss = { songToAdd = null },
                    onSetlistSelected = { setlistId ->
                        setlistViewModel.addSongToSetlist(setlistId, songId)
                        songToAdd = null
                    }
                )
            }
        }

        // Setlist Overview Screen
        composable(Routes.SETLISTS) {
            SetlistScreen(
                viewModel = setlistViewModel,
                onSetlistClick = { setlistId ->
                    navController.navigate(Routes.setlistDetail(setlistId))
                }
            )
        }

        // Setlist Detail Screen
        composable(
            route = Routes.SETLIST_DETAIL,
            arguments = listOf(
                navArgument("setlistId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val setlistId = backStackEntry.arguments?.getString("setlistId") ?: return@composable
            SetlistDetailScreen(
                viewModel = setlistViewModel,
                setlistId = setlistId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Future routes will be added here:
        // - Song Detail Screen
        // - Performance Mode Screen
    }
}

/**
 * Dialog for selecting which setlist to add a song to.
 */
@Composable
fun SetlistSelectionDialog(
    setlistViewModel: SetlistViewModel,
    onDismiss: () -> Unit,
    onSetlistSelected: (String) -> Unit
) {
    val setlists by setlistViewModel.setlists.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Setlist") },
        text = {
            LazyColumn {
                items(
                    items = setlists,
                    key = { setlist -> setlist.id }
                ) { setlist ->
                    TextButton(
                        onClick = { onSetlistSelected(setlist.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = setlist.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
