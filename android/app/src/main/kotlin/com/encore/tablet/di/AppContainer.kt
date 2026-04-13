package com.encore.tablet.di

import android.content.Context
import com.encore.core.data.db.EncoreDatabase
import com.encore.core.data.auth.AuthRepository
import com.encore.core.data.auth.AuthRepositoryImpl
import com.encore.core.data.preferences.AppPreferencesRepository
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.tablet.BuildConfig
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SetlistRepositoryImpl
import com.encore.core.data.repository.SongRepository
import com.encore.core.data.repository.SongRepositoryImpl
import com.encore.core.data.sync.GcpSyncProvider
import com.encore.core.data.sync.SyncProvider
import org.json.JSONObject

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
    val syncProvider: SyncProvider by lazy {
        GcpSyncProvider(context)
    }

    val songRepository: SongRepository by lazy {
        SongRepositoryImpl(database.songDao(), syncProvider)
    }

    val setlistRepository: SetlistRepository by lazy {
        SetlistRepositoryImpl(
            setlistDao = database.setlistDao(),
            setDao = database.setDao(),
            setEntryDao = database.setEntryDao(),
            songDao = database.songDao(),
            syncProvider = syncProvider
        )
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    val appPreferencesRepository: AppPreferencesRepository by lazy {
        AppPreferencesRepository(context)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(context, BuildConfig.GOOGLE_WEB_CLIENT_ID, userPreferencesRepository)
    }

    val anthropicApiKey: String by lazy {
        try {
            val json = context.assets.open("anthropic_config.json").bufferedReader().readText()
            JSONObject(json).getString("api_key").trim()
        } catch (_: Exception) { "" }
    }
}
