---
title: "06 Encore Delivery Checklist"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Delivery Checklist

*Exact milestone deliverables a developer must hand back for review.*

| Project | Encore - Native Android Tablet App V1 |
| --- | --- |
| Package | Version 1.0 |

- Purpose. This document is part of the handoff-ready execution package for rebuilding Encore as a native Android tablet application.

# How to use this checklist

Each milestone should be considered incomplete until the developer provides the listed materials. This is intended to prevent vague status updates such as 'basically done' or 'working on my machine.'

## Milestone 1 - Foundation / Architecture

- Updated architecture diagram.

- Final data model / schema documentation.

- Navigation map or screen flow.

- Parser/render spike notes using representative markdown charts.

- Backend/auth decision memo.

- Runnable test build or technical proof for Google sign-in and local persistence.

- Known risks / open questions list.

## Milestone 2 - Core Library + Setlist Management

- Runnable APK or installable test build.

- Source committed to the agreed repo/branch.

- Single-song import flow.

- Full-library import flow.

- Duplicate conflict modal with Replace / Keep Both / Cancel.

- Library search and browse screens.

- Setlist overview and set editing flows.

- Markdown edit song mode.

- Brief test notes showing import, replace, and keep-both behaviors.

## Milestone 3 - Performance Mode

- Updated APK/test build.

- Performance mode screen(s).

- Dark mode implementation.

- Swipe navigation behavior.

- Search overlay in performance mode.

- Return-to-set behavior and state restore implementation.

- Offline rehearsal test notes or demo.

- Known limitations / edge cases.

## Milestone 4 - Sync + Account Behavior

- Updated APK/test build.

- Google sign-in and sign-out flows.

- Single active device session behavior.

- Sync Now implementation.

- Conflict resolution UI for songs and setlists.

- Test notes for offline-to-online sync and conflict scenarios.

- Cloud schema / API changes documented.

## Milestone 5 - Beta Hardening

- Beta APK or release candidate build.

- Regression checklist results.

- Known issues log.

- Crash/diagnostic setup notes.

- Summary of real-world rehearsal testing.

- Release recommendation with blockers, if any.

# Suggested review artifacts

- Short annotated screen-recording clips are useful for swipe behavior, search overlay, and return-to-set flows.

- When a parser or rendering issue exists, include both the source markdown and a screenshot of the rendered result.

- Every milestone handoff should include a short list of known defects or deferred items.
