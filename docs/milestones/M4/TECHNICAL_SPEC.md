# Milestone 4 — Technical Specification: Identity & Auth

**Branch:** `feature/performance-viewer` (continuing from M3)
**Phase:** 4.1 — Identity & Auth Integration
**Status:** ✅ COMPLETE (2026-03-27)

---

## Overview

Milestone 4 introduces Google Sign-In, single-device session management, and the
foundation for cloud sync. Phase 4.1 delivers the full auth identity layer — credential
flow, state machine, profile UI, and error surfacing. Cloud sync (Ktor API calls,
conflict resolution) is Phase 4.2+.

---

## 1. Auth Stack

| Concern | Choice | Rationale |
|---------|--------|-----------|
| Sign-in provider | Google Identity | Referenced in `docs/03_Technical_Specification.md` — single account per musician |
| Android API | Credential Manager (`androidx.credentials:1.2.2`) | Modern replacement for deprecated `GoogleSignInClient` |
| Google ID option | `com.google.android.libraries.identity.googleid:googleid:1.1.0` | Works with Credential Manager; returns `GoogleIdTokenCredential` |
| Play Services backend | `credentials-play-services-auth:1.2.2` | `app` module only — provides the OS-level bottom-sheet |
| Token type | Google ID Token (JWT) | Passed to `POST /auth/google` on the Ktor backend in Phase 4.2 |

---

## 2. Google Cloud Console Setup

Two OAuth 2.0 client IDs are required — one is used in code, one is a registration artifact:

| Client type | Purpose | Used in code? |
|-------------|---------|---------------|
| **Web application** | `setServerClientId()` — the audience for the ID token | ✅ Yes — Web Client ID only |
| **Android** (SHA-1 bound) | Play Services uses this to authenticate the APK | ❌ No — Google finds it automatically via SHA-1 |

**Rule:** Only the Web Client ID goes into `local.properties` / `BuildConfig`. Never reference the Android Client ID string in code.

### local.properties entry
```
GOOGLE_WEB_CLIENT_ID=xxxxxxxxxx.apps.googleusercontent.com
```
`local.properties` is git-ignored. The value is injected at build time via `buildConfigField`.

---

## 3. Credential ID Injection Pattern

`BuildConfig.GOOGLE_WEB_CLIENT_ID` is generated in the `app` module. Because `AuthRepositoryImpl` lives in `core:data` (no `BuildConfig` access), the ID is passed through the constructor:

```
local.properties
    ↓ (Gradle buildConfigField)
BuildConfig.GOOGLE_WEB_CLIENT_ID  [app module]
    ↓ (AppContainer constructor arg)
AuthRepositoryImpl(context, webClientId)  [core:data]
    ↓
GetGoogleIdOption.setServerClientId(webClientId)
```

**`app/build.gradle.kts`:**
```kotlin
import java.util.Properties

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    defaultConfig {
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\""
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

---

## 4. Auth Flow

```
[User taps AccountCircle icon — top-right of Song Library header]
        ↓
[ModalBottomSheet: ProfileSheetContent]
        ↓
[User taps "Sign in with Google" (pill-shaped OutlinedButton)]
        ↓
[AuthViewModel.signIn(activityContext)]
  → Log.d AUTH_DEBUG: BuildConfig.GOOGLE_WEB_CLIENT_ID value
        ↓
[AuthRepositoryImpl.signIn()]
  → _authState = Loading
  → Log.d AUTH_DEBUG: webClientId value (should match above)
  → GetGoogleIdOption: setFilterByAuthorizedAccounts(false), setAutoSelectEnabled(false)
  → CredentialManager.getCredential(request)
        ↓ (OS-level Google account picker bottom-sheet)
  → CustomCredential(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
        ↓
  → GoogleIdTokenCredential.createFrom(data)
  → _authState = Authenticated(GoogleUser)
        ↓
[AccountCircle icon turns primary color]
[ModalBottomSheet shows: display name, email, Sign Out button]
```

**Sign-out flow:**
```
[User taps "Sign Out" in DropdownMenu]
        ↓
[AuthViewModel.signOut()]
        ↓
[userPrefs.clearUser()]  → DataStore wiped
[credentialManager.clearCredentialState()]
        ↓
[_authState = Unauthenticated]
[AccountCircle returns to onSurfaceVariant color]
```

**Cold-start session restore (Phase 4.2.2):**
```
[App process starts]
        ↓
[AuthRepositoryImpl.init — _authState stays Loading]
        ↓
[repositoryScope coroutine: userPrefs.persistedUser.first()]
        ↓ (~1 frame DataStore read)
  persisted != null → _authState = Authenticated(GoogleUser from cache)
  persisted == null → _authState = Unauthenticated
        ↓
[UI reflects correct state — avatar appears immediately on warm reopen]
```

**Error flow:**
```
[GetCredentialException (any non-cancellation failure)]
        ↓
[AuthRepositoryImpl wraps as RuntimeException("[ClassName] message")]
        ↓
[AuthViewModel.signIn() — onFailure emits to _signInError SharedFlow]
        ↓
[MainScreen LaunchedEffect collects → snackbarHostState.showSnackbar(
    message = errorMsg,
    duration = Indefinite,
    withDismissAction = true
)]
```
User cancellations (`GetCredentialCancellationException`) are swallowed — no Snackbar shown.

---

## 5. AuthState

```kotlin
sealed class AuthState {
    object Loading : AuthState()        // Initial / in-flight
    object Unauthenticated : AuthState()
    data class Authenticated(val user: GoogleUser) : AuthState()
}

data class GoogleUser(
    val googleAccountId: String,       // Stable account ID (email or sub claim)
    val displayName: String?,
    val givenName: String?,
    val familyName: String?,
    val profilePictureUri: android.net.Uri?,  // Google avatar — loaded via Coil AsyncImage
    val idToken: String                // JWT for Ktor token exchange in Phase 4.3+
                                       // Empty string ("") when restored from DataStore cache
)
```

---

## 6. Database Migration

`SongEntity` gained an optional `owner_id` column in DB version 3.

```sql
ALTER TABLE songs ADD COLUMN owner_id TEXT;
```

`MIGRATION_2_3` registered in `EncoreDatabase`. `fallbackToDestructiveMigration()` enabled for active development — **remove before production release**.

---

## 7. File Inventory

### Phase 4.1

| File | Module | Change |
|------|--------|--------|
| `core/data/.../auth/AuthRepository.kt` | `core:data` | New — `AuthState`, `GoogleUser`, `AuthRepository` interface + `AuthRepositoryImpl` |
| `app/.../auth/AuthViewModel.kt` | `app` | New — wraps repository; `authState: StateFlow`, `signInError: SharedFlow` |
| `app/.../di/AppContainer.kt` | `app` | Added `authRepository` lazy; `private val context` |
| `app/.../di/ViewModelFactory.kt` | `app` | Added `AuthViewModel` case |
| `app/.../ui/MainScreen.kt` | `app` | `AccountCircle` button, `ModalBottomSheet` profile panel, error Snackbar |
| `core/data/.../entities/SongEntity.kt` | `core:data` | Added `ownerId: String? = null` |
| `core/data/.../db/EncoreDatabase.kt` | `core:data` | Version 3, `MIGRATION_2_3`, `fallbackToDestructiveMigration()` |
| `app/build.gradle.kts` | `app` | `buildConfig = true`, `buildConfigField` for Web Client ID |

### Phase 4.2.1 — Avatar UI

| File | Module | Change |
|------|--------|--------|
| `core/data/.../auth/AuthRepository.kt` | `core:data` | Added `profilePictureUri: android.net.Uri?` to `GoogleUser` |
| `app/.../ui/MainScreen.kt` | `app` | `UserAvatar` composable (`AsyncImage` + `CircleShape` + 200ms crossfade); `DropdownMenu` replaces bottom sheet for authenticated state |
| `app/build.gradle.kts` | `app` | Added `io.coil-kt:coil-compose:2.6.0` |

### Phase 4.2.2 — Session Persistence

| File | Module | Change |
|------|--------|--------|
| `core/data/.../preferences/UserPreferencesRepository.kt` | `core:data` | New — Preferences DataStore storing `google_account_id`, `display_name`, `profile_picture_uri` |
| `core/data/.../auth/AuthRepository.kt` | `core:data` | `AuthRepositoryImpl` takes `UserPreferencesRepository`; init restores session; signIn saves; signOut clears |
| `app/.../di/AppContainer.kt` | `app` | Added `userPreferencesRepository` lazy; passed to `AuthRepositoryImpl` |
| `core/data/build.gradle.kts` | `core:data` | Added `androidx.datastore:datastore-preferences:1.0.0` |

---

## 8. DataStore Persistence Detail (Phase 4.2.2)

### What is persisted

| Key | Type | Notes |
|-----|------|-------|
| `google_account_id` | String | Presence of this key = "logged in" |
| `display_name` | String? | Shown in avatar dropdown and profile sheet |
| `profile_picture_uri` | String? | Serialised `android.net.Uri`, parsed back on restore |
| `idToken` | — | **Not persisted** — expires; refreshed on next explicit sign-in |

### `idToken` sentinel
When session is restored from DataStore, `GoogleUser.idToken = ""`. This is transparent to the
UI. Phase 4.3 Ktor calls will detect the empty token and trigger a silent re-auth if needed.

---

## 9. Phase 4.3.5 — Zen Header & Adaptive Song Color

**Status:** ✅ COMPLETE (2026-03-27)

### 9.1 Header Redesign

Replaced legacy Quick Action Cards with a single purpose-built `EncoreHeader` composable
(`app/.../ui/HeaderComponent.kt`). Layout left→right:

| Element | Behavior |
|---------|----------|
| `encore_logo.xml` (26dp height) | Static image — user replaces drawable with real asset |
| `v1.0.2` badge | `labelSmall`, `FontWeight.Light`, `onSurface.copy(alpha=0.45f)` |
| `Spacer(weight=1f)` | Pushes action buttons right |
| Import `IconButton` | Triggers `onImportClick` → shows import `ModalBottomSheet` |
| SAVE SET / LOAD SET `TextButton` | No-op placeholders |
| PERFORM `Button` | No-op placeholder, pill shape (`RoundedCornerShape(50)`) |
| Settings `IconButton` | No-op placeholder |
| `UserAvatar` (32dp) + border | `CircleShape` border `1.dp onSurface.copy(alpha=0.2f)`; tap → DropdownMenu (auth) or profile sheet (unauth) |

### 9.2 Global Dark Background

- `EncoreTheme` (`MainActivity.kt`) updated to `darkColorScheme()`.
- `Scaffold(containerColor = Color(0xFF121212))` and `Column(.background(Color(0xFF121212)))` in `CommandCenterScreen`.

### 9.3 Adaptive Song Row Color

In `SongListItem` (`LibraryScreen.kt`):

```kotlin
val rowAccentColor = remember(sets) {
    sets.minByOrNull { it.number }?.number?.let { SetColor.getSetColor(it) }
}
```

- Title `Text`: `color = rowAccentColor ?: MaterialTheme.colorScheme.onSurface`
- Artist `Text`: `color = rowAccentColor?.copy(alpha = 0.65f) ?: MaterialTheme.colorScheme.onSurfaceVariant`
- Set circles remain on the right side — layout unchanged.

### 9.4 Import Flow (Native Android Behavior)

**Contract:** `ActivityResultContracts.GetMultipleContents()` → `ACTION_GET_CONTENT`

This launches the system file picker as a standard Android Activity. Back navigation is
handled natively by the picker — no custom interception required:

```
Back (in directory)  →  navigate up one level   (standard Android behavior)
Back (at root)       →  return to Encore         (standard Android backstack)
```

**Import sheet** (`ModalBottomSheet` shown before launching picker):
- "Choose Files" → `filePickerLauncher.launch("*/*")`
- "Cancel" → dismisses sheet without entering picker

**In-flight cancel:** `LibraryViewModel.cancelImport()` cancels the running `importJob` coroutine.
Triggered by "Cancel" action on the `Importing…` indefinite Snackbar.

**Removed:** Notification permission request, `POST_NOTIFICATIONS` manifest entry, heads-up
notification helpers — all were workarounds for `ACTION_OPEN_DOCUMENT` behavior; not needed
with `ACTION_GET_CONTENT`.

### 9.5 File Inventory — Phase 4.3.5

| File | Module | Change |
|------|--------|--------|
| `app/.../ui/HeaderComponent.kt` | `app` | New — `EncoreHeader` composable |
| `app/.../ui/MainScreen.kt` | `app` | Removed Quick Action Cards + `QuickActionCard`; added `EncoreHeader` call; dark background; `GetMultipleContents` import launcher; import sheet |
| `app/.../MainActivity.kt` | `app` | `EncoreTheme` uses `darkColorScheme()` |
| `app/src/main/res/drawable/encore_logo.xml` | `app` | New — placeholder vector; replace with real asset |
| `feature/library/.../LibraryScreen.kt` | `feature:library` | `SongListItem` adaptive title/artist color via `rowAccentColor` |
| `feature/library/.../LibraryViewModel.kt` | `feature:library` | `importJob: Job?`; `cancelImport()` |
| `app/src/main/AndroidManifest.xml` | `app` | Removed `POST_NOTIFICATIONS` permission |

---

## 10. Deferred to Phase 4.4+

- **Single-device session policy** — `POST /auth/google` Ktor call; revoke on sign-in from new device
- **`SyncStatus` state machine** — wire `PENDING_UPLOAD` songs to actual Ktor API calls
- **Manual Sync Now** action + conflict resolution UI
- **Setlist management screen**

---

*Phase 4.1 completed 2026-03-27 · Phase 4.2.1–4.2.2 completed 2026-03-27 · Phase 4.3.5 completed 2026-03-27 on branch `feature/performance-viewer`.*
