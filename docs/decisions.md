# Architecture Decisions

## GCP Sync Authentication — Service Account JWT (2026-04-03)

**Decision:** Use a GCP service account with RSA-SHA256 JWT to obtain Bearer tokens for GCS REST API calls.

**Alternatives tried first:**
1. `GoogleAuthUtil.getToken` (legacy) — requires a registered Android OAuth client in GCP; throws `UserRecoverableAuthException: NeedRemoteConsent` on every call when the client is missing or misconfigured.
2. `Identity.getAuthorizationClient` (new Google Identity SDK) — also requires a registered Android OAuth client + matching SHA-1 fingerprint; returns `DEVELOPER_ERROR / should not be retried` without it.
3. Registering an Android OAuth client in GCP Console — attempted but failed due to credential scope mismatch between two GCP projects (`encore-native` vs `encore-cloud-leo-2026`). Even after consolidating to one project, the `DEVELOPER_ERROR` persisted.

**Why service account JWT:**
- Completely bypasses Android Play Services and the OAuth consent flow.
- No GCP Android client registration required.
- Token exchange is a plain HTTPS POST to `oauth2.googleapis.com/token` — works on any Android device without Google Play Services.
- Token lasts 60 minutes; we cache for 55 and auto-refresh.

**Trade-offs:**
- The private key is embedded in the APK assets (`gcp_service_account.json`). This is acceptable for an internal-only tablet app but would need a secrets server (e.g., Firebase Remote Config + KMS) before public distribution.
- Service account has project-level GCS access, not user-scoped. All tablets share the same service account identity. Per-user path scoping (`{userId}/songs/`) is enforced by the app, not by IAM.

**Files:**
- `core/data/…/sync/GcpSyncProvider.kt` — JWT construction + token exchange + GCS REST calls
- `app/src/main/assets/gcp_service_account.json` — credentials (gitignored)
- `.gitignore` — `android/app/src/main/assets/gcp_service_account.json` added

---

## Bidirectional Sync — Timestamp vs Hash comparison (2026-04-04)

**Decision:** Use `remote.serverUpdatedAt > song.lastSyncedAt` (both epoch-ms longs) as the RemoteAhead guard, rather than comparing hash strings.

**Why:** Web app writes `hash = Date.now().toString()` (epoch-ms string) into `library_health.json`; Android writes MD5 hex strings. Direct hash comparison would always disagree. Comparing timestamps is format-agnostic and semantically correct: if the remote file is newer than our last sync, pull it.

**Files:** `SongRepository.kt` — `checkSyncStatus()` block.

---

## Set Sync Protocol — source field + in-memory timestamp guard (2026-04-04)

**Decision:** Set sync files at `{userId}/sets/set_N.json` include a `source` field (`"tablet"` or `"web"`). Android only applies set changes when `source == "web"` AND `updatedAt > lastSeenSetUpdatedAt[N]`.

**Why:** Prevents tablets from re-applying their own uploads. `lastSeenSetUpdatedAt` is in-memory (not persisted) — on restart the `source` field is the first-line guard; the timestamp check prevents double-apply within a session. Web similarly only displays, never silently overwrites, what it loaded from GCS.

**Files:** `LibraryViewModel.kt` (`checkAndApplyWebSetChanges`), `CloudLibraryService.js` (`saveSetFile`).

---

## GCS Manifest Cache — 60s TTL (2026-04-04)

**Decision:** `GcpSyncProvider` caches the parsed `library_health.json` manifest for 60 seconds (`MANIFEST_CACHE_TTL_MS = 60_000`). `invalidateManifestCache()` is called after any local write to the manifest.

**Why:** A startup sync iterating all 96 songs would make 96 separate GCS reads without the cache. One read per 60s window (or per write) is the correct trade-off for an offline-first app.

**Files:** `GcpSyncProvider.kt` — `readManifestWithToken`, `invalidateManifestCache`.

---

## SyncProvider Interface Layering (2026-04-03)

**Decision:** `SyncProvider` extends `EncoreApiService` and adds lock/manifest/upload/download + `authConsentEvents`.

**Why:** Keeps the fake and real implementations behind a single injection point (`SongRepositoryImpl(dao, syncProvider)`). `AppContainer` swaps `FakeSyncProvider` ↔ `GcpSyncProvider` without touching any feature code.
