# Milestone 5: v2 UI Redesign — Summary

**Branch:** `milestone-5/ui-redesign`
**Status:** ✅ COMPLETE
**Closed:** 2026-04-27
**Final Commit:** `98691f0`

---

## Overview

Milestone 5 delivered a full visual redesign of the Encore Android tablet app (UI2 build) and a complete overhaul of the Encore web app. The Android UI2 build ships alongside the production build as `com.encore.tablet.ui2` — both can run on the tablet simultaneously without DB schema conflicts. The web app was redesigned with iOS-style aesthetics, a dark/light theme toggle, and an auth reconnect fix.

---

## Android — UI2 Build

### Design Tokens (`EncoreTheme.kt`)
- `screenBackground` → `#000000` (OLED black)
- `cardBackground` → `#1C1C1E` (iOS dark card)
- Added: `surfaceRaised`, `surfaceSunken`, `borderSubtle`, `borderStrong`
- Updated `artistText`, `searchBarBackground`, `divider` to iOS-spec values
- Light tokens aligned to iOS spec

### Set Cover Palette (`SetColor.kt`)
- Replaced flat 4-color set identity system with a 10-color cycling `SET_COVER_PALETTE`
- `SetCoverColors(bg, fg)` data class — each entry has background + foreground pair
- `setCoverColors(setNumber)` — cycles modulo palette size
- Old `getSetColor()` kept for backward compat in SongDetailScreen

### SongListItem + SongTile
- New `SongTile` (44×44dp, 8dp radius): deterministic color from palette + two-letter glyph from title words
- Simplified row: `SongTile` | Title — Artist (single line) | Key · Capo N · Lead (second line) | SyncDot
- SwipeToDelete removed from default row interaction (edit via long-press/sheet)

### Portrait Library Screen (`MainScreen.kt`)
- `LibraryScreen` replaces split-pane `CommandCenterScreen` as root for UI2
- iOS-style header: 34sp weight-800 title, subtitle with song/set counts, theme toggle + settings buttons
- `SetTile` (130×130dp, 14dp radius): large set number centered, "Set" label top-left, active ring + glow
- Horizontal scroll "Tonight's Sets" rail with `LibrarySetTile` (∞ → LibraryMusic icon)
- Single-column portrait layout — landscape split-pane untouched in production build

### Settings Visual Alignment
- Category nav dots, accent system, PresetChip color strip, ColorRow cards aligned to design tokens

### Theme Presets (`BuiltInThemes.kt`)
- `ANALOG_LUXE_DARK` bgColor updated to `#1A0F08`
- `NEON_NIGHT_SHIFT` bgColor → `#0A0A1A`, lyric → `#E0E0FF`

### Brand Assets
- `drawable-nodpi/encore_mark.png` — orange note mark (267×264px)
- `drawable-nodpi/encore_logo_full.png` — full logo (1024×1024px)
- Splash screen: `ic_splash_icon.xml` (oval white + mark inset 40dp), white bg
- Adaptive icon: `mipmap-anydpi-v26/ic_launcher.xml` with mark foreground

---

## Web App (`Encore-Firebase/Encore-Firebase/`)

### Dark/Light Theme System
- `tailwind.config.js`: `darkMode: 'class'` enabled
- `isDark` state defaults to `false` (light mode is default)
- Sun/Moon toggle button in header; root div receives `dark` class conditionally
- All color classes have both light defaults and `dark:` variant overrides
- Light: `#F2F2F7` screen, `white` cards, `#1C1C1E` text
- Dark: `#000000` screen, `#1C1C1E` cards, `white` text

### Song Cover Tiles
- `SongCoverTile` component: 36×36px, 10-color palette matching Android
- `songCoverIndex(path)` — stable djb2-style hash of song path for deterministic color assignment
- Two-letter glyph from first letters of first two title words
- Applied to all song rows in sidebar and setlist panels

### Auth Fix — Reconnect Button
- **Problem:** Firebase Auth persists (localStorage) but GCS OAuth token expires (sessionStorage). App showed "logged in" in Firebase but GCS ops failed — confusing UX.
- **Solution:** `needsReconnect = authReady && Boolean(user) && !gcsToken && mode === 'cloud'`
- When true: blue "Reconnect" button replaces sign-in area — one click calls `signInWithPopup` for fresh GCS token
- The confusing "Session expired" amber warning was removed

### Header + Brand
- Encore mark (PNG) in header: white rounded-xl badge
- `index.css`: dark scrollbars, `color-scheme: dark` in `:root`, dark `select option` backgrounds

### Library Health Tab (pre-existing, documented here)
- `checkSongHealth(song)` detects: missing title (inc. UUID-as-title), missing artist, missing key, unclosed `[h]` tags, non-standard section names
- Health tab shows all flagged songs with issue badges; click-through to editor

---

## What Did NOT Change

- All ViewModels, Repositories, Room DB
- All sync/GCS logic and bidirectional sync protocol
- SongDetailScreen structure and performance mode functionality
- SwipeToDismiss bug fix (gesture intact)
- DB schema version (still v9 — M4 production build unaffected)
- `applicationId` for production build (`com.encore.tablet`)
- Production Android build (`src/main/`) is untouched

---

## Known Gaps / Next Milestone Candidates

- Android icon/splash: mark PNG clips under launcher circle mask — not confirmed working on device
- Set Builder screen redesign (Phase 4 from plan) — not implemented
- Web library management: add/edit/delete songs, bulk upload
- Web Library Health → suggested fix feature (auto-detect and one-click repair metadata issues)
