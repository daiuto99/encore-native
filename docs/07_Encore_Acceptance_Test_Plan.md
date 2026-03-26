---
title: "07 Encore Acceptance Test Plan"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Acceptance Test Plan

*Pass/fail criteria for major features, especially import, editing, setlists, performance mode, offline use, and sync.*

| Project | Encore - Native Android Tablet App V1 |
| --- | --- |
| Package | Version 1.0 |

- Purpose. This document is part of the handoff-ready execution package for rebuilding Encore as a native Android tablet application.

# Testing philosophy

Encore V1 should only move forward when critical user workflows pass under realistic conditions. The acceptance criteria below are intentionally concrete so the developer, tester, and product owner are all measuring the same thing.

# Critical acceptance criteria

| Area | Test / Condition | Pass Criteria | Priority |
| --- | --- | --- | --- |
| Import | Import a single song file | Representative .md file imports successfully and renders without corruption. | P1 |
| Import | Import a full library | Large batch import completes; duplicate prompts appear when expected; no silent overwrites. | P1 |
| Import | Replace existing song | Replacing an existing title+artist updates the master song everywhere it appears. | P1 |
| Import | Keep both duplicate | New copy is retained and auto-renamed with (1), (2), etc. | P2 |
| Library | Find songs quickly | Search returns matching songs fast enough for normal use on the target tablet. | P1 |
| Setlists | Create and edit setlist | User can create a setlist, add/remove sets, renumber automatically, and add songs from the library. | P1 |
| Editing | Edit markdown chart | Minor lyric/chord change saves correctly and updates all references to that song. | P1 |
| Performance | Offline stage use | Songs, setlists, search, navigation, and dark mode all work without internet once synced. | P1 |
| Performance | Swipe navigation | Left/right swipes move reliably between songs without frequent accidental triggers. | P1 |
| Performance | Search overlay | Search icon opens overlay, keyboard is ready, user can open a song quickly. | P1 |
| Performance | Return to set | After jumping to another song, Return to Set restores the prior set, song, and current-song scroll position. | P1 |
| Performance | Dark mode readability | Dark mode is readable and comfortable on the target 11-inch Android tablet in rehearsal conditions. | P1 |
| Account | Google sign-in | User can authenticate successfully and reach their own library/state. | P1 |
| Sync | Manual Sync Now | User can sync data on demand; local device remains usable before, during, and after sync. | P1 |
| Sync | Conflict handling | If edits conflict between devices, the app asks the user which version to keep. | P1 |
| Session | Single device session | Signing in on a second device invalidates or blocks the first device as specified. | P2 |

# Milestone exit gates

## Milestone 1 - Foundation / Architecture

- One .md chart parses and renders correctly; auth + local persistence proved.

## Milestone 2 - Core Library + Setlist Management

- User can import songs, build a full setlist, and update a master song safely.

## Milestone 3 - Performance Mode

- Show workflow works fully offline with reliable navigation and restore.

## Milestone 4 - Sync + Account Behavior

- New device sync works; all conflicts ask the user; offline use remains intact.

## Milestone 5 - Beta Hardening

- Real-world rehearsal pass; no blocker bugs in core workflows.

# Recommended real-world beta scenarios

- Build a setlist from scratch on the tablet only.

- Import a new song that matches an existing title + artist and resolve the conflict intentionally.

- Edit a chord or lyric in one song and confirm the update appears everywhere that song is used.

- Run a full rehearsal offline using performance mode only.

- Jump out of the current set through search, then use Return to Set to restore the original context.

- Create a deliberate conflict between two devices, then confirm the app asks which version to keep during sync.
