---
name: Project State — Setlist Engine, Performance Mode & Zen UI
description: Current state after M4 UI polish — Zen UI refinement, harmony highlights, song edit actions all complete; next is M5 Ktor sync
type: project
---

All M4 UI work is complete. The app defaults to Light Mode. Performance screens are production-ready.

**Zen UI Refinements (this session):**
- Light Mode is now the default (`MainScreen.kt` `isDarkMode = false`)
- Section card interiors use `encoreColors.cardBackground` (white/dark) — only left accent bar + heading text carry section colour
- Performance Context Bar: 52dp height, `"Set N — Name"` label, 12dp pill radii, 13sp fonts, equal `weight(1f)` on each nav pill for centred set name, `.copy(alpha=0.98f)` on both card surfaces
- Control Pill: `RoundedCornerShape(12.dp)` (was 50)
- Scroll top padding: 152dp

**Harmony rendering:**
- No underlines anywhere — `TextDecoration.Underline` fully removed
- Harmony lines: `background = harmonyColor.copy(alpha = 0.18f)`
- Inline `[h]text[/h]` spans: `background = harmonyColor.copy(alpha = 0.22f)`
- Colour sourced from `AppPreferences` dark/light harmony color

**Song Edit Sheet additions (LibraryScreen.kt + LibraryViewModel.kt):**
- Zoom Reset button (arms blue) → `lastZoomLevel = 1.0f` on Save
- Clear Harmonies button (arms red) → strips `[h]`/`[/h]` tags from `markdownBody` on Save
- `onSave` lambda: 6 params — title, artist, isLeadGuitar, isHarmonyMode, resetZoom, clearHarmonies
- Both call sites updated: `LibraryScreen.kt` and `MainScreen.kt`

**Performance Context Bar (SongDetailScreen.kt):**
- `PerformanceContextBar` composable: 52dp, `"Set N — Name"` dynamic label in SetColor pastel
- Prev/next pills: `RoundedCornerShape(12.dp)`, 13sp, 14dp horizontal padding, `weight(1f)` symmetry
- Clock: `FontFamily.Monospace`, `Modifier.width(76.dp)` fixed — no layout jitter on second ticks

**Performance Dashboard (SongDetailScreen.kt):**
- 68dp floating card, `RoundedCornerShape(12.dp)`, 0.98f alpha
- Control Pill: `RoundedCornerShape(12.dp)`, 60dp IconButtons

**Section Cards (SongDetailScreen.kt — SongContent):**
- `drawBehind` for background + 4dp accent bar (immune to zoom clipping)
- Background: `encoreColors.cardBackground`; bar: `sectionColor.copy(alpha=0.38f)`

**Phase 4 Setlist Engine:**
- HorizontalPager, SAVE/LOAD SET, `pagerResetTrigger` on load, export/import `.encore.json`

**Why:** Aligning with Encore Desktop Manager aesthetic — maximum scannability for live performance.

**How to apply:** M4 UI phase is fully complete. Next milestone is M5 Ktor sync. Start from M4_ACTIVE_CONTEXT.md for remaining sync work.
