---
name: Project State — Setlist Engine & Performance Mode
description: Current state after Phase 4 + Context Bar — both floating cards complete, next feature TBD
type: project
---

Phase 4 Horizontal Setlist Engine is complete. Performance Dashboard and Performance Context Bar are both complete.

**Performance Context Bar (floating card, below dashboard, SongDetailScreen.kt):**
- `PerformanceContextBar` composable: 44dp height, `Surface(RoundedCornerShape(12dp))`, same border/card style as dashboard
- Layout: ← prev pill | set name (SetColor) | next pill → | 1dp divider | HH:MM:SS live clock (76dp fixed width)
- Pills show truncated title or "..." at first/last song; clicking animates pagerState
- Hidden when `setName` is empty (single-song / no-set mode)
- Song content scroll top padding: `144dp` (was 84dp)

**ViewModel additions for Context Bar (SongDetailViewModel.kt):**
- `setName: StateFlow<String>` — from `setlistRepository.getSetlistWithSets()?.setlist?.name`, fallback `"Set $N"`
- `prevSong: StateFlow<SongEntity?>` and `nextSong: StateFlow<SongEntity?>` — set in `loadSong()` and `onPageChanged()`
- Set color: `SetColor.getSetColor(setNumber)` — no DB change; `SetlistEntity` has no color field

**Performance Dashboard (floating card, above context bar, SongDetailScreen.kt):**
- `PerformanceDashboard` composable: `Surface(RoundedCornerShape(12dp))`, 68dp height, 8dp float inset, 1dp border
- Layout: Key Anchor | Identity (weight 1f) | Status Pill (guitar pick + BPM) | 12dp spacer | 1dp divider | 12dp spacer | Control Pill (☀ ✏ ✕)
- Guitar pick icon: `feature/performance/src/main/res/drawable/ic_guitar_pick.xml`
- Private parsers: `parseBpm()`, `splitKey()`, `stripLeadingTitle()`

**Phase 4 Setlist Engine:**
- '+' button in library stages directly to Set 1 DB via `addToPerformSet(songId)`
- SAVE SET / LOAD SET wired in header → dialogs in MainScreen.kt
- HorizontalPager nav with "Page X of Y" fade indicator (2s auto-hide)

**Section Cards (SongDetailScreen.kt — SongContent composable):**
- Each section (Intro/Verse/Chorus etc.) wrapped in a subtle card: 4dp left accent bar + 7% tinted background, `RoundedCornerShape(8dp)`
- Grouping via `remember(sections)` pass before rendering; `SectionBodyLines` extracted as private `@Composable`
- Tune: bar alpha `0.38f`, background alpha `0.07f`, gap `vp.sectionTopPaddingDp` (20dp default)
- `IntrinsicSize.Min` on the card Row makes the accent bar stretch full card height

**Why:** User wants set-level context visible during performance — know where they are in the set and what's coming next. Section cards add polish and professionalism.

**How to apply:** Both floating cards and section cards are done. Next session start from M4_ACTIVE_CONTEXT.md for remaining work.
