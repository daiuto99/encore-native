# Encore Android — M4 Active Context

## Current Milestone
Milestone 4 — Sync + Account Behavior

## Milestone Goal
Add cloud-backed account and sync behavior without breaking the offline-first local product.

## What Is Already Done

### Completed foundation for M4
- **Performance Mode (v1):** HorizontalPager, Pinch-to-zoom (0.5x–3.0x) with DB persistence, ChordSidekick parser functional.
- **Set Integrity:** Sets 1–4 auto-initialized in DB.
- Google sign-in/out, auth state, `ownerId`, session persistence via DataStore.
- Import flow, global dark background, adaptive song-row colors.
- Gesture/zoom polish, drag-to-reorder, set sort order.

## Zen UI — Phase 1 COMPLETE
- `EncoreTheme.kt`, Dark/Light toggle, Zen Cards, left accent bars, SetColor pastels.

## Schema & Logic Alignment — COMPLETE
- DB v5: `display_key`, `original_key`, `is_lead_guitar`, `is_verified`, `last_verified_at`.
- Performance Header badges (Not Original Key amber, Lead Guitar icon).
- Edit Modal cleaned up; "Edit Chart" button added.

## Chart Editor Screen — COMPLETE
- `SongChartEditorScreen`: `BasicTextField` + `TextFieldValue`, cursor-safe `[h]` insertion.
- Harmony DSL multi-line fix in `SongDetailScreen`.
- Formatting toolbar in TopAppBar; selection caching; `focusManager.clearFocus()` on exit.
- `SongEditBottomSheet`: "Select All" suppressed via `LocalTextToolbar`; Exit button added.

---

## IN PROGRESS — Global Preference Engine (Phase 1)

### Goal
Hierarchical styling system via Jetpack DataStore: sticky defaults + "Set as Global" feature.

### Architecture decisions locked in
- **Single DataStore file** (`user_prefs`) — visual prefs added as new keys to the existing `UserPreferencesRepository`-owned store via a second repository class (`AppPreferencesRepository`) that shares the same `Context.userDataStore` extension property. No second DataStore file needed.
- **Section styles serialized as a JSON string** — DataStore Preferences doesn't support Maps natively. `Map<String, SectionStyle>` → `JSONObject` string stored under `SECTION_STYLES_JSON` key.
- **`org.json`** used for JSON (already on Android platform, no extra dependency).
- **`DisplayPreferencesHolder` deleted** — replaced by `AppPreferencesRepository` flow. `DisplayPreferences` constants (section colors, set colors) are kept as companion object values; the mutable singleton is removed.
- **`promoteToGlobal` is theme-aware** — takes an `isDarkTheme: Boolean` parameter (passed from the UI layer which reads `isSystemInDarkTheme()`). Updates only the dark-theme or light-theme keys accordingly to prevent a stage dark setup from being overwritten by a rehearsal light config.

### Files being created / modified
| File | Change |
|---|---|
| `core/data/.../preferences/AppPreferences.kt` | **NEW** — `SectionStyle` data class + `AppPreferences` data class |
| `core/data/.../preferences/AppPreferencesRepository.kt` | **NEW** — DataStore-backed prefs repo |
| `core/data/.../preferences/DisplayPreferences.kt` | Remove `DisplayPreferencesHolder` singleton; keep constants |
| `app/.../di/AppContainer.kt` | Add `appPreferencesRepository` lazy val |
| `app/.../di/ViewModelFactory.kt` | Inject `AppPreferencesRepository` into `LibraryViewModel` + new `AppPreferencesViewModel` |
| `feature/library/.../LibraryViewModel.kt` | Collect `appPreferences` as `StateFlow` |
| `feature/performance/.../SongDetailScreen.kt` | Replace `DisplayPreferencesHolder` pull with flow; wire `chordSpacing`, `showLeadIndicator`, `showTranspositionWarning` |

### Deferred to next step (Settings UI)
- Settings screen UI to tweak all values (lyricSize, chordSpacing, sectionStyles, theme backgrounds)
- `fontFamily` enum rendering — no UI to set it yet
- `AppPreferencesViewModel` wiring to a real Settings screen composable

---

## Remaining M4 Work
- Single active device session policy
- Wire `SyncStatus` to real Ktor API calls
- Manual **Sync Now** action
- Conflict detection and conflict-resolution UI
- Setlist management screen

## Known Facts for Next Session
- `SetlistDetailScreen.kt` — do not touch (user does not use it)
- `SongEditBottomSheet` is in `feature/library`, imported by `app` module
- `SongChartEditorScreen` in `feature/library` → `Routes.SONG_CHART_EDITOR = "chart_editor/{songId}"`
- DB is at version 5
- DataStore file name: `user_prefs` (shared by `UserPreferencesRepository` and `AppPreferencesRepository`)
- Build filter: `./gradlew assembleDebug 2>&1 | grep -E "FAILED|^e: |BUILD SUCCESSFUL"`
- ADB path: `~/Library/Android/sdk/platform-tools/adb`
