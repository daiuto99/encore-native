package com.encore.tablet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.encore.tablet.di.AppContainer
import com.encore.tablet.di.ViewModelFactory
import com.encore.tablet.ui.MainScreen

/**
 * Main entry point for Encore Android tablet application.
 *
 * Milestone 2 - Library Management: Shows library and setlists with bottom nav.
 */
class MainActivity : ComponentActivity() {

    // Dependency injection container
    private lateinit var appContainer: AppContainer
    private lateinit var viewModelFactory: ViewModelFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize DI container
        appContainer = AppContainer(applicationContext)
        viewModelFactory = ViewModelFactory(appContainer)

        setContent {
            EncoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModelFactory = viewModelFactory,
                        appContainer = appContainer
                    )
                }
            }
        }
    }
}

/**
 * Encore app theme.
 *
 * Uses Material 3 design system with default color scheme.
 */
@Composable
fun EncoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
