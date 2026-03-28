package com.encore.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.encore.core.data.auth.GoogleUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore instance per process — top-level delegate is the required pattern
private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * The minimal user profile needed to restore the UI after a cold start.
 * idToken is intentionally excluded — it expires and is refreshed on the next explicit sign-in.
 */
data class PersistedUser(
    val googleAccountId: String,
    val displayName: String?,
    val profilePictureUri: String?   // android.net.Uri serialised to String
)

/**
 * Preferences DataStore wrapper for Google account session persistence.
 *
 * Persists just enough to restore the authenticated UI state on cold start:
 *   googleAccountId, displayName, profilePictureUri (as String).
 *
 * Milestone 4 — Phase 4.2.2
 */
class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val GOOGLE_ACCOUNT_ID     = stringPreferencesKey("google_account_id")
        val DISPLAY_NAME          = stringPreferencesKey("display_name")
        val PROFILE_PICTURE_URI   = stringPreferencesKey("profile_picture_uri")
        val CONNECTED_FOLDER_URI  = stringPreferencesKey("connected_folder_uri")
    }

    /**
     * Emits the saved user, or null if no session has been persisted.
     * The presence of [GOOGLE_ACCOUNT_ID] is the authoritative "logged in" signal.
     */
    val persistedUser: Flow<PersistedUser?> = context.userDataStore.data.map { prefs ->
        val id = prefs[Keys.GOOGLE_ACCOUNT_ID] ?: return@map null
        PersistedUser(
            googleAccountId    = id,
            displayName        = prefs[Keys.DISPLAY_NAME],
            profilePictureUri  = prefs[Keys.PROFILE_PICTURE_URI]
        )
    }

    /** Called immediately after a successful Credential Manager sign-in. */
    suspend fun saveUser(user: GoogleUser) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCOUNT_ID] = user.googleAccountId
            user.displayName?.let        { prefs[Keys.DISPLAY_NAME]        = it }
            user.profilePictureUri?.let  { prefs[Keys.PROFILE_PICTURE_URI] = it.toString() }
        }
    }

    /**
     * The URI string of the last folder synced via SAF OpenDocumentTree.
     * Device-scoped, not user-scoped — persists across sign-out.
     */
    val connectedFolderUri: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[Keys.CONNECTED_FOLDER_URI]
    }

    /** Save the folder URI after a successful OpenDocumentTree sync. */
    suspend fun saveConnectedFolderUri(uri: android.net.Uri) {
        context.userDataStore.edit { prefs ->
            prefs[Keys.CONNECTED_FOLDER_URI] = uri.toString()
        }
    }

    /** Clear the connected folder (e.g. user disconnects it explicitly). */
    suspend fun clearConnectedFolderUri() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.CONNECTED_FOLDER_URI)
        }
    }

    /**
     * Sign-out — clears only auth keys.
     * Connected folder URI is intentionally kept: it is device-scoped, not user-scoped.
     */
    suspend fun clearUser() {
        context.userDataStore.edit { prefs ->
            prefs.remove(Keys.GOOGLE_ACCOUNT_ID)
            prefs.remove(Keys.DISPLAY_NAME)
            prefs.remove(Keys.PROFILE_PICTURE_URI)
        }
    }
}
