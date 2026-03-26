package com.encore.tablet.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.encore.tablet.di.AppContainer
import com.encore.tablet.di.ViewModelFactory
import com.encore.tablet.navigation.EncoreNavHost
import com.encore.tablet.navigation.Routes

/**
 * Main screen with bottom navigation.
 *
 * Provides navigation between Library and Setlists screens.
 */
@Composable
fun MainScreen(
    viewModelFactory: ViewModelFactory,
    appContainer: AppContainer
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                // Library destination
                NavigationBarItem(
                    selected = currentRoute == Routes.LIBRARY,
                    onClick = {
                        if (currentRoute != Routes.LIBRARY) {
                            navController.navigate(Routes.LIBRARY) {
                                // Pop up to start destination to avoid building large back stack
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of same destination
                                launchSingleTop = true
                                // Restore state when reselecting previously selected item
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Library"
                        )
                    },
                    label = {
                        Text("Library")
                    }
                )

                // Setlists destination
                NavigationBarItem(
                    selected = currentRoute == Routes.SETLISTS,
                    onClick = {
                        if (currentRoute != Routes.SETLISTS) {
                            navController.navigate(Routes.SETLISTS) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Setlists"
                        )
                    },
                    label = {
                        Text("Setlists")
                    }
                )
            }
        }
    ) { paddingValues ->
        EncoreNavHost(
            navController = navController,
            viewModelFactory = viewModelFactory,
            appContainer = appContainer,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Bottom navigation destinations.
 */
sealed class BottomNavDestination(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Library : BottomNavDestination(
        route = Routes.LIBRARY,
        icon = Icons.Default.List,
        label = "Library"
    )

    object Setlists : BottomNavDestination(
        route = Routes.SETLISTS,
        icon = Icons.Default.DateRange,
        label = "Setlists"
    )
}
