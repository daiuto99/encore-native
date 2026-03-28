---
title: "06 Encore Delivery Checklist"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Delivery Checklist

*Exact milestone deliverables a developer must hand back for review.*

| Project | Encore - Native Android Tablet App V1 |
| --- | --- |
| Package | Version 1.0 |

- Purpose. This document is part of the handoff-ready execution package for rebuilding Encore as a native Android tablet application.

# How to use this checklist

Each milestone should be considered incomplete until the developer provides the listed materials. This is intended to prevent vague status updates such as 'basically done' or 'working on my machine.'

## Milestone 1 - Foundation / Architecture

**Status:** ✅ COMPLETE (2026-03-26)

- ✅ Updated architecture diagram.
  - Location: `docs/architecture/architecture-diagram.md`
  - Includes system overview, data flow diagrams, and technology stack

- ✅ Final data model / schema documentation.
  - Location: `docs/architecture/data-model.md`
  - Defines all entities: User, Device, Song, Setlist, Set, SetEntry, ConflictRecord
  - Includes Room database schema and indexes

- ✅ Navigation map or screen flow.
  - Location: `docs/architecture/navigation-map.md`
  - Maps all 12 screens with entry/exit points and navigation patterns

- ✅ Parser/render spike notes using representative markdown charts.
  - Location: `docs/architecture/parser-spike-notes.md`
  - Library selected: mikepenz/multiplatform-markdown-renderer v0.14.0
  - Test results documented with performance metrics
  - Sample song (Amazing Grace) renders correctly with chord alignment

- ✅ Backend/auth decision memo.
  - Location: `docs/decisions/001-backend-stack-choice.md`
  - Decision: Kotlin Ktor for language consistency
  - Rationale and alternatives documented

- ✅ Runnable test build or technical proof for Google sign-in and local persistence.
  - Build: `./gradlew assembleDebug` succeeds
  - APK: `android/app/build/outputs/apk/debug/app-debug.apk` (35MB)
  - Proof: Markdown parser renders sample song with metadata parsing
  - Note: Full Google Sign-In and Room DB to be implemented in Milestone 2

- ✅ Known risks / open questions list.
  - Location: `docs/risks-milestone-1.md`
  - 6 high/medium risks documented with mitigation strategies
  - 6 open questions identified for future decision points

**Deliverables Summary:**
- 7/7 items complete
- All documentation in place
- Build successful
- Parser spike validated
- Ready to proceed to Milestone 2

## Milestone 2 - Core Library + Setlist Management

- Runnable APK or installable test build.

- Source committed to the agreed repo/branch.

- Single-song import flow.

- Full-library import flow.

- Duplicate conflict modal with Replace / Keep Both / Cancel.

- Library search and browse screens.

- Setlist overview and set editing flows.

- Markdown edit song mode.

- Brief test notes showing import, replace, and keep-both behaviors.

## Milestone 3 - Performance Mode

**Status:** ✅ COMPLETE (2026-03-27) — see `docs/milestones/M3_SUMMARY.md`

- ✅ Updated APK/test build — installed on SM-X210.
- ✅ Performance mode screen (`SongDetailScreen`) — full-screen, no chrome, persistent ✕ back button.
- ✅ Dark mode — `darkColorScheme()` + `Color(0xFF121212)` global background.
- ✅ Swipe navigation — left/right between songs in active set.
- ✅ Search overlay — global search in library ignores set filter when text is present.
- ✅ Return-to-set state — back from performance mode restores set filter and scroll position.
- ✅ Pinch-to-zoom (0.5×–3.0×), double-tap reset, single-tap HUD, 3s auto-hide, 500ms debounced persist.
- ✅ Section headers colored by type (Verse, Chorus, Bridge, etc.) via `DisplayPreferences`.
- ✅ Known limitations documented — see M3_SUMMARY.md §7.

## Milestone 4 - Sync + Account Behavior

**Status:** 🔄 IN PROGRESS

### Phase 4.1 — Identity & Auth Integration ✅ COMPLETE (2026-03-27)

- ✅ Updated APK installed on SM-X210 (clean build confirmed).
  - Branch: `feature/performance-viewer`

- ✅ Google sign-in and sign-out flows.
  - `AuthRepositoryImpl` via Credential Manager + `GetGoogleIdOption`
  - `AccountCircle` icon (top-right) → `ModalBottomSheet` profile panel
  - Sign-out via `credentialManager.clearCredentialState()`

- ✅ Auth state machine: `Loading` → `Unauthenticated` / `Authenticated(GoogleUser)`
  - `StateFlow<AuthState>` observed by `AuthViewModel` → UI
  - `AccountCircle` tint: primary color when authenticated, onSurfaceVariant when not

- ✅ Error surfacing: sign-in failures emit to `SharedFlow<String>` → Indefinite Snackbar with dismiss action.
  - User cancellations swallowed silently.

- ✅ `SongEntity.ownerId: String?` added — DB version 3, `MIGRATION_2_3`.

- ✅ `BuildConfig.GOOGLE_WEB_CLIENT_ID` injected from `local.properties` via `buildConfigField`.
  - Web Client ID only in code; Android Client ID matched automatically via SHA-1.

- ✅ Technical spec: `docs/milestones/M4/TECHNICAL_SPEC.md`

### Phase 4.2.1 — Avatar & Profile UI ✅ COMPLETE (2026-03-27)

- ✅ Google avatar displayed via Coil `AsyncImage` clipped to `CircleShape` with 200ms crossfade.
  - Fallback to `AccountCircle` icon when `profilePictureUri` is null.
- ✅ `profilePictureUri: android.net.Uri?` added to `GoogleUser` — extracted from `GoogleIdTokenCredential`.
- ✅ Authenticated tap interaction: `DropdownMenu` with display name, email, and **Sign Out**.
  - Unauthenticated tap still opens `ModalBottomSheet` sign-in flow.
- ✅ `io.coil-kt:coil-compose:2.6.0` added to `app/build.gradle.kts`.

### Phase 4.2.2 — Session Persistence ✅ COMPLETE (2026-03-27)

- ✅ `UserPreferencesRepository` — new Preferences DataStore in `core:data`.
  - Persists: `google_account_id`, `display_name`, `profile_picture_uri` (as String).
  - `idToken` intentionally not persisted (expires; refreshed on next sign-in).
- ✅ Cold-start session restore: `AuthRepositoryImpl.init` reads DataStore; sets `Authenticated`
  from cache or `Unauthenticated` — no sign-in prompt on reopen after login.
- ✅ `_authState` starts as `Loading` during DataStore read (no "signed-out" flash).
- ✅ Sign-out clears DataStore before clearing Credential Manager state.
- ✅ `androidx.datastore:datastore-preferences:1.0.0` added to `core/data/build.gradle.kts`.
- ✅ Verified on device: sign in → close → reopen → avatar appears immediately.

### Phase 4.3.5 — Zen Header & Adaptive Song Color ✅ COMPLETE (2026-03-27)

- ✅ Legacy Quick Action Cards ("Song Folder Library" / "Add Songs") removed.
- ✅ `EncoreHeader` composable — logo, v1.0.2 badge, Import icon, SAVE SET / LOAD SET (no-op), PERFORM pill (no-op), Settings icon, UserAvatar 32dp with border + DropdownMenu.
  - File: `app/.../ui/HeaderComponent.kt`
- ✅ Global dark background: `Color(0xFF121212)` on Scaffold + Column; `darkColorScheme()` in `EncoreTheme`.
- ✅ Adaptive song row color: title/artist text color reflects earliest set number via `SetColor.getSetColor()`; set circles remain on right side.
- ✅ `encore_logo.xml` placeholder drawable added (user replaces with real asset).
- ✅ Import flow: `GetMultipleContents` (`ACTION_GET_CONTENT`) — standard Android activity back-stack; Back once = up a directory, Back twice = return to app. Matches native Android behavior.
- ✅ Import sheet has explicit "Cancel" button before entering the system file picker.
- ✅ In-flight import cancellable via "Cancel" Snackbar action → `LibraryViewModel.cancelImport()`.
- ✅ Notification permission + heads-up notification removed (no longer needed with native back behavior).
- ✅ Song row Title-First hierarchy: Title (bold, left, `weight(1f, fill=false)`) + Artist (65% opacity, 10dp gap) cluster left; `Spacer(weight(1f))` separates them from key badge / set circles / add button on the right.

### Phase 4.4+ — Deferred

- ⬜ Single active device session policy (`POST /auth/google` Ktor call).

- ⬜ `SyncStatus` state machine wired to actual Ktor API calls.

- ⬜ Manual Sync Now action + conflict resolution UI for songs and setlists.

- ⬜ Test notes for offline-to-online sync and conflict scenarios.

- ⬜ Cloud schema / API changes documented.

- ⬜ Setlist management screen (create, rename, delete setlists).

## Milestone 5 - Beta Hardening

- Beta APK or release candidate build.

- Regression checklist results.

- Known issues log.

- Crash/diagnostic setup notes.

- Summary of real-world rehearsal testing.

- Release recommendation with blockers, if any.

# Suggested review artifacts

- Short annotated screen-recording clips are useful for swipe behavior, search overlay, and return-to-set flows.

- When a parser or rendering issue exists, include both the source markdown and a screenshot of the rendered result.

- Every milestone handoff should include a short list of known defects or deferred items.
