# 002 — Song Schema Refactor & Chart Editor

**Date:** 2026-03-31
**Status:** Implemented

## Decisions

### Schema changes (DB v5)
- Renamed `current_key` → `display_key`; added `original_key` (nullable) to support transposition tracking
- Added `is_lead_guitar` (Boolean), `is_verified` (Boolean), `last_verified_at` (Long)
- Removed `lead_marker` and `harmony_markup` columns (unused)
- Migration uses full table-recreation pattern (CREATE → INSERT SELECT → DROP → RENAME) because SQLite does not support `RENAME COLUMN` or `DROP COLUMN` in older API levels
- "Not Original Key" amber badge shown in Performance Header when `displayKey != originalKey`
- Lead guitar icon (MusicNote in circle) shown in Performance Header when `isLeadGuitar = true`

### set_assignment column — skipped
Decided not to add a `set_assignment` column to `songs`. Current set membership lives in the `SetlistEntry` join table. A future "Projects" feature (acoustic band, jazz band, rock band) will introduce a proper `Project` table. Denormalizing onto `songs` would conflict with that model.

### Harmony DSL — multi-line `[h]...[/h]` blocks
The `[h]...[/h]` harmony tag can span multiple lines. The `buildChordLine` function works per-line, so a single-line regex match would miss multi-line blocks.
**Fix:** Pre-scan lines for `[h]`/`[/h]` boundary markers in the `SongSection.Body` renderer. Lines inside a block receive `isHarmonyLine = true`, which applies orange + bold + underline to all lyric text on those lines. Self-contained single-line tags are handled by the existing `HARMONY_TAG_PATTERN` regex path.

### Chart Editor screen (SongChartEditorScreen)
- `BasicTextField` + `TextFieldValue` chosen over `TextField` for cursor-safe tag insertion
- `VisualTransformation` (`HarmonyHighlightTransformation`) provides live `[h]` orange highlighting in the editor without altering the underlying text
- Formatting buttons (`[h]`, Chord, Section) placed in the TopAppBar alongside Cancel/Save so they are always visible regardless of keyboard state
- Selection caching (`lastNonCollapsedSelection`) works around Android collapsing `BasicTextField` selection when a button outside the field is tapped
- `focusManager.clearFocus()` called on Save, Cancel, and Back to dismiss the selection action bar and cursor — prevents the editor getting stuck in an active-but-no-keyboard state
- Inline chord/section edit panel floats above keyboard via `imePadding()` as a separate overlay, only visible when active

### SongEditBottomSheet — text selection
- Wrapped with `LocalTextToolbar` override that passes `null` for `onSelectAllRequested`, suppressing the "Select All" option from the system floating text selection bar
- Added explicit "Exit" `TextButton` below Save to dismiss the sheet without saving
