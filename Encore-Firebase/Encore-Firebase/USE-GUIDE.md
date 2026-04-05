# Encore Desktop Manager - Use Guide

## Big picture

This app is your local control room for four main jobs:

- checking your song library for missing metadata
- cleaning up song information in batches
- building setlists
- previewing how charts will feel in performance mode

## 1. Command Center

This is the main health-check area.

### What it shows
For each markdown song file, the app checks whether it is missing:

- YAML front matter
- BPM
- key / display key
- title
- artist

### Why this matters
This gives you a quick way to find broken or incomplete songs before they cause problems later.

### Bulk editing
Select multiple songs from the list, then use the right-side controls to update shared metadata like:

- artist
- display key
- original key
- BPM
- lead guitar flag

This is meant for quick cleanup across a group of files.

## 2. Setlist Architect

This is the visual set builder.

### How it works
- songs appear on the left
- Set 1 through Set 4 appear on the right
- drag songs into the active set
- drag again to reorder them

### Export
Use **Export Sync Bundle** to create a JSON file containing:

- songs
- setlists
- theme settings

That gives you a future-friendly handoff format for backend sync.

## 3. Harmony Editor

This is where you edit a chart directly.

### What makes it useful
- markdown editing on the left
- live visual preview on the right
- harmony highlighting with `[h]...[/h]`

### Example
```md
[h]3rd above[/h]
```

### Auto-cleaning behavior
The preview tries to remove obvious repeated title / artist lines at the top so the performance view is cleaner.

## 4. Performance Visualizer

This is the polished rehearsal / stage preview.

### Includes
- top context bar
- set pills
- set name color
- 12-hour clock
- bottom dashboard
- key display
- chart identity
- guitar / BPM status area

### Theme controls
You can live-edit:

- background color
- lyric color
- chord color
- harmony color
- section style

You can also preview two modes:

- Midnight Mainstage
- Studio Daylight

## Recommended workflow

A simple way to use the app is:

1. Load your library folder
2. Fix missing metadata in Command Center
3. Build the current rehearsal set in Setlist Architect
4. Clean key songs in Harmony Editor
5. Review the visual feel in Performance Visualizer
6. Export the sync bundle when ready

## Good to know

- the app stores working state in the browser so your last session is easier to resume
- the folder scan is recursive, so nested subfolders are included
- saving only writes markdown changes when the folder was selected through the app


## Android set export

Use **Setlist Architect → Export active set to /sets**. This writes one `.encore.json` file into the selected library folder's `/sets` folder using the strict Encore set format: top-level `version`, `name`, and ordered `songs`; each song includes `title`, `artist`, optional `displayKey`, and `markdownBody`.
