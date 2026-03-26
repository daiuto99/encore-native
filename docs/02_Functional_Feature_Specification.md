---
title: "02 Encore Functional Feature Specification"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Functional Feature Specification

*Detailed V1 behavior, user flows, and acceptance criteria*

| Project | Encore - native Android tablet rebuild of the current web MVP |
| --- | --- |
| Package | v1 handoff package |

- Scope note  This document defines the V1 feature set for the native Android tablet build only. Where a behavior from the current Encore MVP is explicitly retained, V1 should preserve the workflow but not the old implementation.

# 1. Users and usage context

- Primary user. An individual musician managing their own library, setlists, and performance workflow.

- Primary device. An 11-inch Android tablet used in portrait orientation.

- Primary contexts. Administrative setup before a show and performance use during a show.

- Connectivity assumption. Internet may be available before the show but cannot be required once the catalog is synchronized.

# 2. Functional modules

## Library and song management

- Single master library per user account.

- Search by song title and artist.

- Single-song import and full-library import.

- Duplicate detection by title + artist with Replace, Keep Both, or Cancel.

- If Keep Both is selected, the app appends (1), (2), and so on.

- Markdown edit mode for quick chord or lyric changes.

- Song changes are global because there is only one master version of each song in V1.

## Setlists

- Setlist name is the only required field.

- A setlist starts with Set 1; the user can add or remove sets.

- Sets use distinct color tabs.

- Removing a set renumbers later sets.

- Empty sets and blank setlists are allowed.

- A song can appear in any set and can appear more than once across a setlist.

- Setlist overview shows each set as a simple visible list with titles and song count.

- Inside a set, users add songs from the song library using the same mental model as the current app.

## Performance mode

- Reliable left/right swipes to move between songs.

- Dark mode optimized for stage use.

- Search icon opens a keyboard-ready overlay; search narrows as the user types.

- Selecting a result opens the song immediately.

- A Return to Set action restores the prior set, song, and scroll position.

- Entire catalog is available offline on device.

- Navigation arrows can be included as backup controls.

## Sync and accounts

- Google sign-in.

- Single active device session in V1.

- Entire catalog and all setlists are cached locally.

- Manual Sync Now action in V1; no automatic background sync required.

- Any edit conflict asks the user to choose a version; no silent overwrite.

## Out of scope for V1

- Native iPad/iPhone application.

- Android phone build.

- In-app AI formatting workflow.

- Dynamic in-app transposition engine.

- Real-time collaboration or multi-user shared libraries.

- Automatic bidirectional sync with Google Drive or iCloud as the live system of record.

# 3. Main user flows

## Import a single song

1. User opens the import flow and selects one markdown song file.

1. App parses title, artist, key, and body content.

1. If title + artist match an existing song, app shows conflict options: Replace, Keep Both, Cancel.

1. On success, song appears in the library and is available for setlists and performance mode.

## Import a full library

1. User selects a folder or batch of files.

1. App scans all candidate files and validates supported formats.

1. Potential duplicates are surfaced as a review queue rather than silently overwritten.

1. After import, all accepted songs are available in the library and cached locally.

## Create a setlist

1. User creates a new setlist by entering a setlist name.

1. Setlist starts with Set 1 by default.

1. User can add additional sets as needed.

1. App opens or returns to the setlist overview, which shows all sets as simple visible lists.

## Add songs to a set

1. User opens a specific set inside a setlist.

1. User opens the song library from that set context.

1. Library view clearly shows which songs are already in the current set.

1. User adds songs; set order is then adjusted inside the set.

## Edit a song

1. User opens Edit Song mode.

1. App loads the markdown text of the imported song.

1. User edits words, chords, harmony markings, lead markings, or other song content.

1. On save, the master version is updated and all set appearances reflect the change.

## Perform with a setlist

1. User opens performance mode.

1. Dark mode presentation is optimized for stage use.

1. Left/right swipe moves between songs.

1. User can search for a song outside the current set via overlay search.

1. Return to Set restores the prior set, song, and scroll position.

## Manual sync

1. User initiates Sync Now.

1. App uploads local changes and downloads remote changes.

1. If any conflict exists, app prompts the user to choose a version.

1. On completion, the entire local catalog remains available offline.

# 4. Detailed feature behavior

## Library requirements

| Requirement | Behavior | V1 rule |
| --- | --- | --- |
| Search fields | Search matches title and artist | Do not search full chart content in V1 |
| Song uniqueness | Duplicate detection uses title + artist | Conflict screen is required before action |
| Duplicate outcomes | Replace, Keep Both, Cancel | Keep Both appends numeric suffix |
| Song storage | One master song record per account | Changes apply everywhere the song appears |
| Import modes | Single file and batch/folder import | Both are required in V1 |

## Setlist requirements

| Requirement | Behavior | V1 rule |
| --- | --- | --- |
| Setlist naming | Setlist name is required | No other show metadata required in V1 |
| Default structure | New setlist starts with Set 1 | User may add or remove sets later |
| Set naming | Sets are numbered Set 1, Set 2, Set 3 | No custom set names in V1 |
| Set deletion | Remaining sets renumber automatically | Empty sets are allowed |
| Song reuse | A song may appear in any set and may appear more than once | Each appearance references the same master song |
| Overview display | Show titles and song count for each set | Use a simple visible list, not collapsible cards |

## Performance mode requirements

| Requirement | Behavior | V1 rule |
| --- | --- | --- |
| Orientation | Portrait only | Optimize specifically for 11-inch Android tablets |
| Song navigation | Swipe left/right to move to previous/next song | Include optional arrows as backup controls |
| Visual mode | Dark mode must be stage-friendly and legible | This is a priority, not an optional theme |
| Out-of-set navigation | Search overlay opens quickly with keyboard ready | Hit Go to open a result immediately |
| Return behavior | Return to Set restores set, song, and scroll position | Treat this as a session bookmark |
| Offline behavior | Performance mode works without internet | Entire catalog must be available locally |

## Editing and sync requirements

| Requirement | Behavior | V1 rule |
| --- | --- | --- |
| Song editing | User edits markdown in a dedicated Edit Song mode | No create-from-scratch flow in V1 |
| Sync initiation | User manually triggers Sync Now | No fully automatic sync required |
| Conflict handling | Any conflict prompts the user | Do not use silent merge or last-write-wins |
| Device policy | Single active device session per account | V1 intentionally keeps this simple |

# 5. Acceptance criteria by feature

## Song import

- A valid markdown song imports successfully and becomes searchable.

- If a duplicate exists, the user is asked what to do before any destructive action occurs.

- Keep Both creates a separately named library entry without damaging the original.

## Song editing

- User can open a song in Edit Song mode, make a small markdown change, save it, and immediately see the updated chart.

- Updated chart appears the same way everywhere that song is used.

## Setlist building

- User can create a named setlist, add and remove sets, add songs from the library, and reorder songs within a set.

- Library view shows current-set membership clearly while adding songs.

## Performance mode

- User can navigate between songs with swipes and backup buttons without obvious lag.

- User can search and jump to a song outside the current set, then return to the exact prior set/song location.

## Offline and sync

- After sync, the entire catalog remains available offline.

- Manual sync completes without data loss; if conflicts occur, the user resolves them.

# 6. Explicit non-goals for V1

- Dynamic in-app transposition.

- AI-assisted formatting/transposition inside the app.

- Shared libraries across multiple musicians.

- Real-time collaboration or shared editing.

- Phone-optimized layouts.

- iOS or web build.

- Automatic cloud-folder sync as the live source of truth.

# 7. Nice-to-have backlog after V1

- Native iPad build.

- Dynamic transposition engine with optional capo logic.

- Setlist export packages for sharing with other musicians.

- Song tags, favorites, and richer metadata filters.

- Optional date/venue metadata on setlists.

- Automatic sync after V1 proves stable.
