---
title: "01 Encore Product Overview"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Product Overview

*Product intent, V1 boundaries, platform recommendation, and delivery summary*

| Project | Encore - native Android tablet rebuild of the current web MVP |
| --- | --- |
| Package | v1 handoff package |

- Recommendation  Build a native Android tablet app first. Keep Claude as the song formatting and transpose workflow in V1. Use the current Encore app as the product reference, but rebuild the architecture, interaction design, and data model cleanly.

# 1. Product context

Encore is a live-performance tool for musicians. It manages a master song library of markdown charts, organizes songs into multi-set setlists, and provides a stage-friendly performance mode. The current web MVP proves the workflow. The rebuild should preserve the winning behavior while removing the fragility caused by browser-based file handling, UI glitches, and web-specific limitations.

# 2. Vision for V1

- Encore should be rebuilt as a native Android tablet application for an 11-inch portrait device, using the current Replit application as the product reference rather than as the code foundation.

- V1 should prioritize stability, native interaction quality, offline reliability, fast library and setlist management, and a dependable performance mode.

- Dynamic in-app transposition should not be part of V1. Claude remains the formatting and transpose tool for imported songs in V1. The new application should be designed so a future transposition engine can be added cleanly.

- Replace the current web-based MVP with a polished, reliable Android tablet app that feels purpose-built for live musicians.

- Preserve the current Encore workflow where it already works well: import songs, organize the library, build multi-set setlists, and perform from a dark, readable, fast interface.

- Fix the MVP's weakest areas by redesigning state management, sync, editing flows, and performance mode around native Android patterns.

- One Google-authenticated user account per musician.

- One master song library per account.

- One active device session per account in V1.

- Tablet-only Android app, portrait-only, optimized for roughly 11-inch screens.

- Full offline use after manual sync.

- Import songs one-by-one or as a full folder/library.

- Create and manage setlists containing Set 1, Set 2, Set 3, and more as needed.

- Edit imported markdown songs inside the app.

- Smooth performance mode with reliable left/right swipes, dark mode, search overlay, and return-to-set behavior.

# 3. Core product principles

- Stage reliability first. Encore is a performance tool before it is a content-management tool. The app must remain responsive, readable, and predictable during a show.

- Tablet-first design. All V1 screens should be designed for an 11-inch Android tablet in portrait orientation.

- Offline by default. Once synchronized, the entire catalog and all setlists must remain usable without connectivity.

- One master truth per song. A song has one active version in V1. If it changes, it changes everywhere.

- Explicit user control. Imports, duplicate handling, sync conflicts, and destructive actions should always be clear and confirmed.

# 4. Current workflow to preserve

1. Use Claude to format a song and, if needed, transpose it before import.

1. Store or move finished markdown song files into the working library/source folder.

1. Import songs into Encore individually or as a group.

1. Search the song library quickly.

1. Build a setlist with multiple numbered sets.

1. Open performance mode and move through songs reliably during a show.

1. Make quick fixes to an imported song when needed.

# 5. V1 feature categories

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

# 6. What changes from the current MVP

| Area | Current MVP | V1 direction |
| --- | --- | --- |
| Platform | Browser-based app built in Replit | Native Android tablet application |
| Source of truth | Mix of local state and file/folder workflow | Backend plus local offline cache |
| Sync model | Folder-driven and browser-driven | Manual Sync Now with conflict prompts |
| Device target | General web, iPad-leaning | 11-inch Android tablet, portrait only |
| Transposition | Handled outside app via Claude | Still external in V1; app designed for future in-app engine |
| Editing | Web-based interactions with some UI glitches | Dedicated in-app markdown edit mode |

# 7. Risks to avoid

- Trying to preserve the web codebase instead of preserving the product behavior.

- Recreating Drive/iCloud folder sync as the live source of truth for a native app.

- Adding dynamic transposition before the core data model and editing flows are stable.

- Expanding scope to phones, iOS, web admin, or collaboration before the tablet experience is solid.

- Building overly clever sync for V1 instead of using a clear manual sync flow.

# 8. Success criteria for private beta

| Category | Beta success target |
| --- | --- |
| Import | User can import a representative full catalog and resolve duplicates cleanly. |
| Library | Search by title/artist feels fast and predictable across the full catalog. |
| Setlists | User can build, edit, and review multi-set setlists without confusion. |
| Performance mode | Swipes, dark mode, and search overlay feel smooth and dependable on stage. |
| Offline | Entire catalog and setlists remain fully usable with no internet connection. |
| Sync | Manual sync completes cleanly and any conflicts are surfaced for user choice. |

# 9. Recommended next moves

1. Freeze the V1 scope defined in this package.

1. Create Android tablet wireframes for library, setlist overview, set editor, song view, edit mode, and performance mode.

1. Define the final data model and sync contract.

1. Build a thin prototype for rendering charts and tuning performance-mode gestures on target hardware.

1. Begin the production build after the rendering and navigation prototype feels right.
