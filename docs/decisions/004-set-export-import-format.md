# Decision 004 — Set Export/Import via JSON (SAF)

**Date:** 2026-04-01
**Status:** Implemented
**Context:** Phase 4 setlist engine — web companion integration

---

## Problem

Sets live in Room (SQLite). The web companion app needs a way to push setlists into the Android app. Direct DB access is not possible across apps or platforms.

## Options Considered

1. **JSON file exchange via SAF** — export `.encore.json` from Android, import on web; web generates `.encore.json`, import on Android via file picker.
2. **Shared folder polling** — Android watches a folder for `.encore.json` files automatically.
3. **Cloud sync via Ktor** — real-time sync through a backend API (M4 sync work, not yet built).

## Decision

**Option 1 — JSON file exchange via SAF.** Manual but zero infrastructure. The web companion generates a `.encore.json` file following the spec in `docs/api/set-export-format.md`; the user imports it via the Import modal. Android exports via the Load Set dialog share icon.

## Consequences

- No backend required for set handoff — works fully offline.
- Content updates to existing songs are NOT pushed through this flow — deduplication reuses the existing library record. Song content sync is handled separately via markdown file import.
- When Ktor sync is built (M4 remaining work), this format can serve as the serialization layer for setlist sync payloads.
