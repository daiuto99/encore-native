# Encore Set Export Format — v1

**Used by:** Web companion → Android import flow
**File extension convention:** `.encore.json`
**Importer entry point:** `LibraryViewModel.importSetFromJson(context, uri)`
**Exporter entry point:** `LibraryViewModel.exportSetlistToUri(context, setlistId, uri)`

---

## Purpose

Portable exchange format for moving named setlists between the Encore web companion and the Android tablet app. One file = one setlist = one ordered flat list of songs.

---

## File Naming

```
<any_name>.encore.json
```

The `.encore.json` double extension is convention. The Android importer accepts any file with a `.json` mime type via the system picker. The exporter generates filenames of the form `Set_Name.encore.json` (spaces replaced with underscores).

---

## Top-Level Shape

```json
{
  "version": 1,
  "name": "Friday Night",
  "songs": [ ... ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `version` | integer | no | Currently `1`. Ignored by importer — reserved for future migrations |
| `name` | string | yes | Becomes the setlist name in Encore. Falls back to `"Imported Set"` if blank or missing |
| `songs` | array | yes | Ordered list of song objects. Import preserves this order in the set |

---

## Song Object

```json
{
  "title": "All The Small Things",
  "artist": "Blink-182",
  "displayKey": "C",
  "markdownBody": "# All The Small Things\n**Key:** C\n**BPM:** 148\n\n[Intro]\n..."
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | string | **yes** | Songs with empty or missing title are silently skipped |
| `artist` | string | yes | Use `"Unknown Artist"` as fallback — empty string is accepted but looks bad in the UI |
| `displayKey` | string or null | no | Omit or `null` if unknown. Format: `[A-G][#b]?m?` — e.g. `"C"`, `"Bb"`, `"F#m"`, `"D#"` |
| `markdownBody` | string | yes | Full song chart as Encore markdown. Can be `""` but will render as a blank chart |

Any additional fields are ignored by the importer.

---

## Deduplication Behaviour

On import, each song is looked up by **title + artist** using a case-insensitive match (`LOWER()` on both sides at the DB layer).

- **Match found:** The existing library song is added to the new setlist. Its `markdownBody` is **not overwritten** — the user's local edits are preserved.
- **No match:** A new song is created in the library with the exported content, then added to the set.

The status snackbar reports the outcome: `Imported "Name" (N new, M matched)`.

> **Implication for the web app:** If the user has edited a song locally and you want to push updated chart content, the set import flow is not the right channel — use the markdown file import (`Import Files`) for content updates. The set import is for set *structure*, not content sync.

---

## markdownBody Format

Encore parses the markdown body to extract key, BPM, and section structure. Follow this structure for best results:

```markdown
# Song Title

**Key:** Am
**BPM:** 120

[Intro]
Am  F  C  G

[Verse]
Am           F
Words words words
```

### Parsed fields (extracted automatically on import)

| Pattern | Example | What it populates |
|---|---|---|
| `**Key:** X` or `**key:** X` | `**Key:** Am` | `displayKey` in DB |
| `**BPM:** N` or `**Tempo:** N` | `**BPM:** 120` | Shown in Performance Dashboard status pill |
| `[Section Name]` on its own line | `[Verse]` | Section header — rendered as a floating card |

### Chord lines

Chord lines are detected heuristically: a line where ≥ 50% of tokens match chord patterns is rendered in the chord accent color. Keep chords on their own line above lyrics.

### Harmony lines (optional)

Prefix a line with `>>` to render it in the harmony accent color:

```
>> Ooh yeah  yeah
```

---

## Minimal Valid Example

```json
{
  "version": 1,
  "name": "Saturday Set",
  "songs": [
    {
      "title": "Mr. Brightside",
      "artist": "The Killers",
      "displayKey": "D#",
      "markdownBody": "# Mr. Brightside\n**Key:** D#\n**BPM:** 148\n\n[Intro]\nE|---\n\n[Verse]\nD#  Bb  Gm  F\nComing out of my cage..."
    },
    {
      "title": "Wonderwall",
      "artist": "Oasis",
      "markdownBody": "# Wonderwall\n**Key:** Em\n\n[Intro]\nEm7  G  Dsus4  A7sus4\n"
    }
  ]
}
```

---

## What the Importer Does NOT Read

- Fields beyond the spec above (ignored silently)
- Multiple sets in one file — the format is one setlist, one flat song list
- Song-level metadata beyond `title`, `artist`, `displayKey`, `markdownBody` — `isLeadGuitar`, `tags`, `notes` etc. are not yet in the format (encode them in `markdownBody` for now)
- Content updates to existing songs — deduplication reuses the existing record as-is

---

## Android Implementation Reference

| Concern | Location |
|---|---|
| Data classes | `core/data/src/main/kotlin/com/encore/core/data/transfer/EncoreSetExport.kt` |
| Export function | `LibraryViewModel.exportSetlistToUri(context, setlistId, outputUri)` |
| Import function | `LibraryViewModel.importSetFromJson(context, uri)` |
| SAF launchers | `CommandCenterScreen` in `app/.../ui/MainScreen.kt` |
| Export UI | Share icon per row in Load Set dialog |
| Import UI | "Import Set (.json)" button in Import modal |
