---
title: "05 Encore Build Roadmap"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Build Roadmap

*Milestone-by-milestone sequence, dependencies, and recommended order of development.*

| Project | Encore - Native Android Tablet App V1 |
| --- | --- |
| Package | Version 1.0 |

- Purpose. This document is part of the handoff-ready execution package for rebuilding Encore as a native Android tablet application.

# Roadmap assumptions

- Scope is limited to Encore V1 for native Android tablet only.

- Primary target is an 11-inch Android tablet in portrait orientation.

- The app must work fully offline after a successful sync.

- Dynamic in-app transposition is out of scope for V1; import-time transposition remains external via Claude.

- Admin/maintenance mode and performance mode both live inside the same tablet app.

# Milestone roadmap

## Phase Summary

| # | Phase | Primary Outcome | Key Deliverables | Exit Gate |
| --- | --- | --- | --- | --- |
| 1 | Foundation / Architecture | Lock scope, schema, navigation, and stack before feature build. | Architecture diagram, schema, API contract, navigation map, parser spike. | One .md chart parses and renders correctly; auth + local persistence proved. |
| 2 | Core Library + Setlist Management | Deliver useful offstage workflow on tablet. | Import flow, duplicate handling, library search, setlist overview, song edit mode. | User can import songs, build a full setlist, and update a master song safely. |
| 3 | Performance Mode | Make the product stage-ready. | Dark mode, swipe navigation, performance search overlay, return-to-set state. | Show workflow works fully offline with reliable navigation and restore. |
| 4 | Sync + Account Behavior | Support cloud-backed single-user sync with manual control. | Google auth, single-device session control, full local cache, Sync Now, conflict UI. | New device sync works; all conflicts ask the user; offline use remains intact. |
| 5 | Beta Hardening | Polish and verify stability for rehearsal/show testing. | Beta APK, QA checklist, regression list, known issues log. | Real-world rehearsal pass; no blocker bugs in core workflows. |

## Milestone 1

### Key work included

- Finalize the song, setlist, set, and sync-state data model.

- Choose the backend/auth/storage stack and document final rationale.

- Define the import contract for markdown songs and duplicate handling.

- Prototype markdown parsing and rendering against representative charts.

- Confirm navigation model for library, setlist overview, set editing, edit song mode, and performance mode.

### Dependencies

- Representative sample song files.

- Agreement on cloud/backend stack and Google authentication flow.

- Approval to treat the current app as a behavioral reference, not an implementation baseline.

### Decision gate

- Architecture is approved before any feature-heavy UI work begins.

## Milestone 2

### Key work included

- Single-song import and full-library import.

- Duplicate detection by title + artist with Replace / Keep Both / Cancel.

- Master song library with search.

- Setlist creation, set add/remove/renumber, set overview, and set editing.

- Simple markdown edit mode for minor chart fixes.

### Dependencies

- Phase 1 schema and parser approved.

- Stable local persistence and repository structure in place.

### Decision gate

- Tablet-only management workflow is functional before performance mode work starts.

## Milestone 3

### Key work included

- Dark mode optimized for stage use.

- Reliable left/right swipe navigation between songs.

- Optional navigation arrows if needed.

- Search overlay inside performance mode with immediate keyboard focus.

- Return-to-set state restoration for set, song, and current-song scroll position.

### Dependencies

- Setlists and songs must already load from local storage cleanly.

- Representative rehearsal testing on the target tablet.

### Decision gate

- The app can be trusted for an offline rehearsal before sync complexity is added.

## Milestone 4

### Key work included

- Google sign-in.

- Single active device session policy.

- Full catalog local caching.

- Manual Sync Now action.

- Conflict detection and user-driven resolution for any conflicting edits.

### Dependencies

- Local-first workflows proven first.

- Cloud schema/API settled in production-like form.

### Decision gate

- Cross-device sync is added only after the local product is stable.

## Milestone 5

### Key work included

- Regression testing across import, edit, set building, performance mode, and sync.

- Large-library testing and edge-case chart rendering review.

- Crash and diagnostics setup.

- Polish pass for UI rough edges and navigation friction.

- Private alpha/beta packaging and deployment process.

### Dependencies

- End-to-end feature set complete.

- Beta test plan and known-issues logging process.

### Decision gate

- Release candidate is approved only after real rehearsal use and no blocker bugs.

# Recommended implementation order within milestones

1. Parser and song model before UI polish.

2. Library/search before set-building complexity.

3. Setlist overview before performance mode.

4. Performance mode before sync.

5. Sync before beta hardening.
