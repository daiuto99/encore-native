package com.encore.core.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Domain types ──────────────────────────────────────────────────────────────

data class GoogleUser(
    val googleAccountId: String,  // Stable account identifier (email or sub)
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val idToken: String           // JWT for Ktor API calls in Milestone 4
)

sealed class AuthState {
    /** Initial state — determining whether a saved credential exists. */
    object Loading : AuthState()

    /** No signed-in user. */
    object Unauthenticated : AuthState()

    /** A user has signed in successfully. */
    data class Authenticated(val user: GoogleUser) : AuthState()
}

// ── Interface ─────────────────────────────────────────────────────────────────

interface AuthRepository {
    val authState: StateFlow<AuthState>

    /**
     * Attempt Google Sign-In via Credential Manager.
     *
     * Requires an Activity context — the Credential Manager bottom-sheet is
     * an Activity-level overlay and will fail with application context.
     *
     * @param activityContext Must be an Activity (not Application) context.
     */
    suspend fun signIn(activityContext: Context): Result<GoogleUser>

    /** Clear the stored credential and return to [AuthState.Unauthenticated]. */
    suspend fun signOut()
}

// ── Implementation ────────────────────────────────────────────────────────────

class AuthRepositoryImpl(
    applicationContext: Context,
    private val webClientId: String
) : AuthRepository {

    private val credentialManager = CredentialManager.create(applicationContext)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // No saved credential on cold start in Credential Manager — start unauthenticated.
        // Milestone 4 will add token persistence to DataStore so this can restore sessions.
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun signIn(activityContext: Context): Result<GoogleUser> {
        _authState.value = AuthState.Loading
        // Log the client ID actually being used so mismatches are visible in Logcat
        Log.d(TAG, "signIn() — webClientId: $webClientId")

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)   // Show all accounts, not just previously used
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)            // Always show the picker
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                val user = GoogleUser(
                    googleAccountId = googleIdToken.id,
                    displayName = googleIdToken.displayName,
                    givenName = googleIdToken.givenName,
                    familyName = googleIdToken.familyName,
                    idToken = googleIdToken.idToken
                )
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                _authState.value = AuthState.Unauthenticated
                Result.failure(IllegalStateException("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            // User dismissed the picker — silent, not an error
            _authState.value = AuthState.Unauthenticated
            Result.failure(e)
        } catch (e: GetCredentialException) {
            // Covers NoCredentialException, GetCredentialProviderConfigurationException (24888), etc.
            val label = "[${e::class.simpleName}] ${e.message}"
            Log.e(TAG, "Sign-in failed: $label", e)
            _authState.value = AuthState.Unauthenticated
            Result.failure(RuntimeException(label, e))
        } catch (e: Exception) {
            val label = "[${e::class.simpleName}] ${e.message}"
            Log.e(TAG, "Sign-in unexpected error: $label", e)
            _authState.value = AuthState.Unauthenticated
            Result.failure(RuntimeException(label, e))
        }
    }

    override suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState failed (non-fatal)", e)
        } finally {
            _authState.value = AuthState.Unauthenticated
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
