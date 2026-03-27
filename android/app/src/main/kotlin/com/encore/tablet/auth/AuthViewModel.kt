package com.encore.tablet.auth

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.auth.AuthRepository
import com.encore.core.data.auth.AuthState
import com.encore.tablet.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for auth state — thin wrapper around AuthRepository.
 *
 * Lives in the app module. Does NOT store Context — Activity context is passed
 * per-call to signIn() to avoid leaking the Activity reference.
 *
 * Milestone 4: Phase 4.1 — Identity & Auth Integration
 */
private const val TAG = "AUTH_DEBUG"

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    /**
     * One-shot error events for the UI to display as a Snackbar.
     * User cancellations are intentional and are NOT emitted here.
     */
    private val _signInError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val signInError: SharedFlow<String> = _signInError.asSharedFlow()

    /**
     * Initiate Google Sign-In.
     *
     * @param activityContext Must be an Activity context (for Credential Manager overlay).
     *   Pass [androidx.compose.ui.platform.LocalContext.current] from the composable.
     */
    fun signIn(activityContext: Context) {
        Log.d(TAG, "signIn() — BuildConfig.GOOGLE_WEB_CLIENT_ID: ${BuildConfig.GOOGLE_WEB_CLIENT_ID}")
        viewModelScope.launch {
            val result = authRepository.signIn(activityContext)
            result.onFailure { e ->
                // Cancellation is intentional — no error shown
                if (e !is GetCredentialCancellationException) {
                    _signInError.tryEmit(e.message ?: "Sign-in failed")
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
