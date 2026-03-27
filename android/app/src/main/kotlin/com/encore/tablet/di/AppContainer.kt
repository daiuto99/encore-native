package com.encore.tablet.di

import android.content.Context
import com.encore.core.data.db.EncoreDatabase
import com.encore.core.data.auth.AuthRepository
import com.encore.core.data.auth.AuthRepositoryImpl
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.tablet.BuildConfig
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SetlistRepositoryImpl
import com.encore.core.data.repository.SongRepository
import com.encore.core.data.repository.SongRepositoryImpl

/**
 * Simple dependency injection container.
 *
 * Holds singleton instances of:
 * - Database
 * - Repositories
 * - Other app-level dependencies
 *
 * Milestone 2: Manual DI (will migrate to Hilt in Milestone 4)
 */
class AppContainer(private val context: Context) {

    // Database singleton
    private val database: EncoreDatabase by lazy {
        EncoreDatabase.getDatabase(context)
    }

    // Repositories
    val songRepository: SongRepository by lazy {
        SongRepositoryImpl(database.songDao())
    }

    val setlistRepository: SetlistRepository by lazy {
        SetlistRepositoryImpl(
            setlistDao = database.setlistDao(),
            setDao = database.setDao(),
            setEntryDao = database.setEntryDao(),
            songDao = database.songDao()
        )
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(context, BuildConfig.GOOGLE_WEB_CLIENT_ID, userPreferencesRepository)
    }
}
