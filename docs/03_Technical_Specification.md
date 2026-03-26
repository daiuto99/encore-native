---
title: "03 Encore Technical Specification"
source_package: "Encore Full Handoff Package"
format: "obsidian-markdown"
---

# Encore Technical Specification

*Architecture, data model, sync strategy, implementation notes, and handoff guidance*

| Project | Encore - native Android tablet rebuild of the current web MVP |
| --- | --- |
| Package | v1 handoff package |

- Implementation stance  This specification is intentionally opinionated. It chooses the simplest architecture likely to produce a stable Android tablet V1, while leaving room for future iOS support and in-app transposition.

# 1. Technical objectives

- Deliver a native Android tablet application with smooth, dependable UI and offline-first behavior.

- Support one musician account per library with Google authentication.

- Store the full catalog and all setlists locally for performance use.

- Use an explicit manual sync model in V1 with user-facing conflict resolution.

- Keep the song model compatible with markdown import while leaving room for future transposition logic.

# 2. Recommended stack

| Layer | Recommendation | Notes |
| --- | --- | --- |
| Mobile client | Kotlin + Jetpack Compose | Best fit for stable Android-first UI, gestures, and tablet behavior |
| Local persistence | Room database + local file cache if needed | Offline-first storage for full library and setlists |
| Auth | Google Sign-In / OAuth via backend token exchange | Single account per musician |
| Backend API | Kotlin Ktor, Node/TypeScript, or equivalent clean REST/JSON API | Choose based on developer skill; keep contracts explicit |
| Primary database | PostgreSQL | Canonical source for songs, setlists, sync metadata, and versions |
| Object/version storage | Database-first for V1, optional object storage for snapshots | Keep simple unless scale requires more |
| Sync transport | Manual REST-based sync endpoints | Conflict prompts handled explicitly |
| Crash/telemetry | Basic crash reporting and sync logging | Needed for beta hardening |

# 3. High-level architecture

The backend is the canonical system of record after import. The tablet app keeps a full local replica of the user's library and setlists for offline use. Drive and iCloud remain optional import/export channels and backup workflows, but they are not the authoritative live store in V1.

| Component | Responsibility |
| --- | --- |
| Android client | Rendering, local editing, local search, performance mode, local cache, manual sync initiation |
| Backend API | Auth, canonical data persistence, versioning, sync orchestration, conflict detection |
| PostgreSQL | Songs, setlists, sets, membership records, sync versions, user account data |
| Claude workflow | External preprocessing of imported song markdown in V1 |

# 4. Domain model

Proposed core entities and relationships:

| Entity | Key fields | Relationship | Notes |
| --- | --- | --- | --- |
| User | id, google_sub, email, display_name, active_device_id | Owns library and setlists | One library per user in V1 |
| Device | id, user_id, platform, app_version, last_sync_at, active | Tracks active device session | Only one active device session at a time |
| Song | id, user_id, title, artist, current_key, markdown_body, original_import_body, lead_marker, harmony_markup, updated_at, version | Referenced by set entries | Single master version in V1 |
| Setlist | id, user_id, name, updated_at, version | Contains sets | Only required field is name |
| Set | id, setlist_id, number, color_token | Contains ordered entries | Set number renumbers on delete |
| SetEntry | id, set_id, song_id, position | References a song | A song may appear multiple times |
| ConflictRecord | id, user_id, entity_type, local_version, remote_version, status | Created during sync when needed | User must choose |

# 5. Song model details

- Storage model. Store the editable song body as markdown text. Also store key metadata and any parsed display metadata needed for indexing.

- Import model. On import, preserve both the incoming markdown body and normalized fields such as title, artist, and current key.

- Rendering model. Render from markdown to the display view using a deterministic parser on device.

- Edit model. Edit Song mode edits the markdown body; save increments song version.

- Future-proofing. Reserve room for optional future fields such as capo_mode, target_key, source_key, and chord-token indexing even if V1 does not expose them.

# 6. Sync model

V1 should implement an explicit, understandable sync model rather than trying to be magically automatic. The user taps Sync Now. The client sends local changes since the last sync token. The server returns accepted updates, remote changes, and any conflicts.

| Step | Client behavior | Server behavior |
| --- | --- | --- |
| 1. Start sync | Send device id, auth token, last sync token, and local change summaries | Validate user and active device session |
| 2. Upload changes | Upload changed songs, setlists, sets, and set entries with versions | Compare incoming versions to canonical versions |
| 3. Detect conflicts | Pause overwrite when same entity changed elsewhere | Return conflict payload instead of auto-merging |
| 4. Apply non-conflicting changes | Store accepted updates | Return new sync token and remote changes |
| 5. Resolve conflicts | User picks a version on device | Server stores chosen resolution and advances version |

- Conflict rule  For V1, every meaningful conflict asks the user. Do not use last-write-wins, silent merge, or hidden background reconciliation.

# 7. Suggested API surface

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | /auth/google | Exchange Google identity token for application session |
| GET | /bootstrap | Return user profile, active device state, latest sync token, and initial deltas |
| POST | /songs/import | Create songs from uploaded markdown payloads |
| GET | /songs | Return songs for library/search bootstrapping |
| PUT | /songs/{id} | Update song metadata or markdown body |
| POST | /setlists | Create setlist |
| PUT | /setlists/{id} | Rename or update setlist metadata |
| POST | /setlists/{id}/sets | Add set |
| DELETE | /sets/{id} | Delete set and trigger renumbering |
| POST | /sets/{id}/entries | Add song to set |
| PUT | /set-entries/{id} | Reorder entry or update position |
| POST | /sync | Manual sync endpoint for uploads, pulls, and conflicts |
| POST | /conflicts/{id}/resolve | Apply user-selected resolution |

# 8. Android screen modules

| Screen/module | Primary purpose | Important implementation notes |
| --- | --- | --- |
| Auth/onboarding | Google sign-in and device activation | Enforce single active device session |
| Library | Search, browse, import, duplicate handling | Search title/artist only in V1 |
| Setlist overview | See all sets in one setlist with song titles and counts | Simple lists, visible tabs, fast scanning |
| Set editor | Add songs from library and reorder within set | Mirror current app behavior closely |
| Song viewer | View formatted chart in normal mode | Fast switch to edit mode or performance mode |
| Edit Song mode | Edit markdown directly | Provide save/cancel, validation, and version increment |
| Performance mode | Stage usage with dark UI, swipes, search overlay, return-to-set | Must be optimized on real device hardware |
| Sync/conflict UI | Run sync and resolve conflicts | Explain choices clearly and never overwrite silently |

# 9. Performance mode implementation notes

- Treat performance mode as its own Compose screen/state rather than a lightly modified regular viewer.

- Preload adjacent songs or pre-render the next/previous song display to reduce perceived navigation latency.

- Maintain a session bookmark object containing prior setlist id, set number, song id, and scroll offset.

- Use conservative gesture thresholds and real-device tuning to avoid accidental navigation while scrolling.

- Keep the performance theme isolated and simple: dark palette, large readable text, obvious controls, no visual clutter.

# 10. Import pipeline notes

1. Read markdown file.

1. Extract or infer title, artist, and key from metadata/content conventions.

1. Normalize and validate core fields.

1. Check duplicate key of title + artist within the user's library.

1. Prompt user if duplicate exists.

1. Persist accepted song and update local/offline indexes.

# 11. Security and reliability notes

- Use authenticated API calls only; never trust client-side ownership claims for songs or setlists.

- Track entity versions explicitly to support conflict detection.

- Keep destructive actions confirmable and reversible where practical.

- Log sync failures and conflict events for beta diagnostics.

- Do not assume Drive/iCloud availability for runtime app correctness.

# 12. Suggested repository structure

| Area | Suggested structure |
| --- | --- |
| Android app | app/, feature/library, feature/setlists, feature/performance, feature/edit, core/ui, core/data, core/sync |
| Backend | src/auth, src/songs, src/setlists, src/sync, src/conflicts, src/common |
| Contracts | OpenAPI or typed JSON schema definitions |
| Documentation | docs/product, docs/technical, docs/api, docs/wireframes |
| QA | test cases, sync scenarios, gesture checklist, import fixture library |

# 13. Delivery checklist for the first build team

1. Confirm wireframes against this functional spec before coding.

1. Finalize data contracts for Song, Setlist, Set, SetEntry, SyncPayload, and ConflictRecord.

1. Build chart rendering and performance-mode gesture prototype first on target hardware.

1. Implement local database and offline catalog before complex sync behavior.

1. Ship private alpha with import, library, setlists, performance mode, and manual sync.

1. Use beta feedback to decide V1.1 features such as transposition or richer organization.

# 14. Deferred architecture topics

- Shared logic strategy for later iOS build.

- Native in-app transposition service and capo interpretation.

- Background sync and multi-device concurrency.

- Setlist/package sharing between musicians.

- Advanced metadata, tagging, and sorting.
