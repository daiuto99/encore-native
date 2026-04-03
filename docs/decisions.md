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

## SyncProvider Interface Layering (2026-04-03)

**Decision:** `SyncProvider` extends `EncoreApiService` and adds lock/manifest/upload/download + `authConsentEvents`.

**Why:** Keeps the fake and real implementations behind a single injection point (`SongRepositoryImpl(dao, syncProvider)`). `AppContainer` swaps `FakeSyncProvider` ↔ `GcpSyncProvider` without touching any feature code.
