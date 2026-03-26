# Encore Architecture Diagram

**Milestone:** 1 - Foundation
**Status:** Draft
**Last Updated:** 2026-03-26

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Tablet Client                     │
│                  (Kotlin + Jetpack Compose)                  │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌─────────────┐  ┌──────────┐ │
│  │  Auth    │  │ Library  │  │  Setlists   │  │Performance│ │
│  │ Feature  │  │ Feature  │  │   Feature   │  │  Feature  │ │
│  └────┬─────┘  └────┬─────┘  └──────┬──────┘  └────┬─────┘ │
│       │             │                │               │       │
│  ┌────┴─────────────┴────────────────┴───────────────┴────┐ │
│  │               Core UI Components Layer                  │ │
│  └────┬─────────────┬────────────────┬───────────────┬────┘ │
│       │             │                │               │       │
│  ┌────┴─────────────┴────────────────┴───────────────┴────┐ │
│  │                   Data Layer                            │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │ Room DB      │  │ Repositories │  │ Sync Manager │  │ │
│  │  │ (Local Cache)│  │              │  │              │  │ │
│  │  └──────────────┘  └──────────────┘  └──────┬───────┘  │ │
│  └───────────────────────────────────────────────┼──────────┘ │
└────────────────────────────────────────────────┼─────────────┘
                                                   │
                                        HTTPS/REST │
                                                   │
┌──────────────────────────────────────────────────┼─────────────┐
│                      Backend API                 │             │
│                   (Kotlin Ktor)                  │             │
├──────────────────────────────────────────────────┴─────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │   Auth   │  │  Songs   │  │ Setlists │  │    Sync      │  │
│  │ Service  │  │ Service  │  │ Service  │  │   Service    │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────┬───────┘  │
│       └─────────────┴──────────────┴────────────────┘          │
│                              │                                 │
│                    ┌─────────┴──────────┐                      │
│                    │   PostgreSQL DB    │                      │
│                    │ (System of Record) │                      │
│                    └────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

## Architecture Principles

### Offline-First Design
- **Local Room database is the runtime master** during performance
- Full catalog cached locally after sync
- All read operations work without network
- Write operations queue for next manual sync

### Single Master Song Version
- One canonical version per song in PostgreSQL
- Local cache mirrors the server state
- Changes propagate globally
- No per-setlist song variants in V1

### Manual Sync Model
- User initiates "Sync Now" action
- Client sends local changes with versions
- Server detects conflicts by comparing versions
- User resolves conflicts explicitly via UI

### Modular Feature Architecture
- Features isolated by domain (auth, library, setlists, performance, edit)
- Shared UI components in core:ui
- Shared data logic in core:data
- Sync logic in core:sync

## Data Flow

### Import Flow
1. User selects markdown file(s)
2. Parser extracts metadata (title, artist, key)
3. Duplicate check against local Room DB
4. User resolves duplicates (Replace/Keep Both/Cancel)
5. Save to local DB
6. Queue for sync on next "Sync Now"

### Performance Mode Flow
1. User opens setlist in performance mode
2. Load all songs for setlist from Room DB
3. Pre-render adjacent songs for fast swipe
4. Navigate with gestures (left/right swipe)
5. Search overlay queries local DB only
6. Return-to-set restores previous state from local session cache

### Sync Flow
1. User taps "Sync Now"
2. Collect local changes since last sync token
3. Send to backend with version numbers
4. Backend compares versions, detects conflicts
5. Backend returns: accepted changes, remote updates, conflicts
6. If conflicts exist, show conflict resolution UI
7. User picks version for each conflict
8. Apply accepted changes to local DB
9. Update sync token

## Technology Stack

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Android Client | Kotlin + Jetpack Compose | Modern, declarative UI; excellent tablet support |
| Local Database | Room (SQLite) | Robust offline-first persistence |
| Backend | Kotlin Ktor | Type-safe, Kotlin-native, lightweight |
| API Database | PostgreSQL | Reliable, supports versioning and conflict detection |
| Auth | Google Sign-In OAuth | Simple, secure, familiar to users |
| API Protocol | REST/JSON | Simple, explicit, easy to debug |

## Key Decisions

- **No background sync:** Manual "Sync Now" reduces complexity and gives user control
- **No per-device song variants:** Simplifies conflict resolution
- **Portrait-only tablet:** Allows fixed layout optimization
- **Single active device:** Eliminates concurrent edit complexity in V1

## Open Questions

1. Markdown parser library selection (CommonMark? Markwon? Custom?)
2. Crash reporting service choice
3. Beta distribution method (Google Play Internal Testing? Firebase App Distribution?)

---

**Next Steps:**
- Finalize data model schema
- Create navigation map
- Prototype markdown parser
- Document API contracts
