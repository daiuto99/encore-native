package com.encore.core.data.sync

import android.content.Context
import android.content.IntentSender
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * Production [SyncProvider] backed by Google Cloud Storage (GCS).
 *
 * GCS bucket:  gs://encore-cloud-leo-2026-songs
 * GCP project: encore-cloud-leo-2026
 *
 * Object layout:
 *   {userId}/songs/{songId}.md          — song markdown body
 *   {userId}/locks/{songId}.lock        — presence = locked; content = owner name
 *   system/library_health.json          — manifest listing all song hashes + timestamps
 *
 * Authentication uses a GCP service account JWT (RSA-SHA256) exchanged at the token
 * endpoint for a short-lived Bearer token. No user consent flow, no Play Services.
 * Credentials are loaded from assets/gcp_service_account.json at first use.
 *
 * Offline safety: every network call is wrapped in a try/catch; failures return null
 * or [LockResult.Acquired] (for locks) so performers are never blocked by connectivity.
 *
 * @param context  Application context — used to read assets.
 */
class GcpSyncProvider(
    private val context: Context
) : SyncProvider {

    companion object {
        private const val TAG = "GcpSyncProvider"
        private const val BUCKET = "encore-cloud-leo-2026-songs"
        private const val BASE_URL = "https://storage.googleapis.com/storage/v1"
        private const val UPLOAD_URL = "https://storage.googleapis.com/upload/storage/v1"
        private const val SCOPE = "https://www.googleapis.com/auth/devstorage.read_write"
        private const val TOKEN_URI = "https://oauth2.googleapis.com/token"
        private const val MANIFEST_OBJECT = "system/library_health.json"
        private const val TOKEN_TTL_MS = 55 * 60 * 1000L // 55 min (token lasts 60)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Service accounts never need user consent — this flow is never emitted.
    private val _authConsentEvents = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    override val authConsentEvents: SharedFlow<IntentSender> = _authConsentEvents.asSharedFlow()

    // ── Service account credentials ──────────────────────────────────────────

    private data class ServiceAccountCreds(
        val clientEmail: String,
        val privateKeyPem: String
    )

    private val creds: ServiceAccountCreds by lazy {
        Log.d(TAG, "Loading service account credentials from assets")
        val json = context.assets.open("gcp_service_account.json").bufferedReader().readText()
        val obj = JSONObject(json)
        ServiceAccountCreds(
            clientEmail = obj.getString("client_email"),
            privateKeyPem = obj.getString("private_key")
        )
    }

    private fun privateKey(): PrivateKey {
        val pem = creds.privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()
        val keyBytes = Base64.decode(pem, Base64.DEFAULT)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    // ── Token cache ──────────────────────────────────────────────────────────

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L
    // Mutex ensures only one coroutine fetches a new token at a time — prevents
    // concurrent callers from all missing the cache and hammering the token endpoint.
    private val tokenMutex = Mutex()

    // Manifest cache — valid for 60 s so a full sync pass (~96 songs) reads GCS only once.
    @Volatile private var manifestCache: GcpManifest? = null
    @Volatile private var manifestCachedAt: Long = 0L
    private val MANIFEST_CACHE_TTL_MS = 60_000L
    // Mutex serialises manifest read-modify-write operations so concurrent uploads
    // (e.g. global sync + background fire-and-forget) don't clobber each other's entries.
    private val manifestMutex = Mutex()

    private suspend fun token(): String = withContext(Dispatchers.IO) {
        tokenMutex.withLock {
            val now = System.currentTimeMillis()
            cachedToken?.takeIf { now < tokenExpiresAt }?.let { return@withLock it }
            Log.d(TAG, "token() — fetching new token for ${creds.clientEmail}")
            val tok = fetchServiceAccountToken()
            cachedToken = tok
            tokenExpiresAt = now + TOKEN_TTL_MS
            tok
        }
    }

    private fun fetchServiceAccountToken(): String {
        val nowSec = System.currentTimeMillis() / 1000
        val expSec = nowSec + 3600

        // Build base64url-encoded JWT header and payload
        val header = Base64.encodeToString(
            """{"alg":"RS256","typ":"JWT"}""".toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val payload = Base64.encodeToString(
            """{"iss":"${creds.clientEmail}","scope":"$SCOPE","aud":"$TOKEN_URI","exp":$expSec,"iat":$nowSec}"""
                .toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val signingInput = "$header.$payload"

        // Sign with RSA-SHA256
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey())
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()
        val signature = Base64.encodeToString(
            sig, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val jwt = "$signingInput.$signature"
        Log.d(TAG, "fetchServiceAccountToken — JWT built (len=${jwt.length}); posting to token endpoint")

        // Exchange JWT for access token
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()
        val request = Request.Builder()
            .url(TOKEN_URI)
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            Log.d(TAG, "fetchServiceAccountToken — HTTP ${response.code}")
            if (!response.isSuccessful) {
                // Do not log the response body — it may contain partial credential identifiers.
                Log.e(TAG, "fetchServiceAccountToken — FAILED HTTP ${response.code}")
                throw IllegalStateException("Token fetch failed: HTTP ${response.code}")
            }
            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Token fetch returned empty body")
            val json = JSONObject(responseBody)
            return json.getString("access_token")
        }
    }

    // ── EncoreApiService ─────────────────────────────────────────────────────

    override suspend fun getRemoteHash(songId: String): RemoteHashResponse = withContext(Dispatchers.IO) {
        try {
            val tok = token()
            val manifest = readManifestWithToken(tok) ?: return@withContext RemoteHashResponse(songId, null, null)
            val entry = manifest.songs[songId]
                ?: return@withContext RemoteHashResponse(songId, null, null)
            RemoteHashResponse(songId, entry.hash, entry.updatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "getRemoteHash($songId) failed — ${e::class.simpleName}: ${e.message}")
            RemoteHashResponse(songId, null, null)
        }
    }

    // ── Session locks ────────────────────────────────────────────────────────

    override suspend fun requestLock(songId: String): LockResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "requestLock($songId) — starting")
        try {
            val tok = token()
            val lockPath = lockObjectPath(songId)
            val owner = creds.clientEmail

            // Atomic create: ifGenerationMatch=0 means GCS only accepts the write if the object
            // does not yet exist. Two simultaneous callers: one gets 201, the other gets 412.
            // This closes the read-then-write race in the old implementation.
            val created = tryCreateObject(tok, lockPath, owner, "text/plain")
            if (created) {
                Log.d(TAG, "requestLock($songId) — lock acquired")
                return@withContext LockResult.Acquired
            }

            // Object already exists — read current owner to decide if it's us or another client
            val existingOwner = readObject(tok, lockPath)?.trim()
            return@withContext if (existingOwner == null || existingOwner == owner) {
                Log.d(TAG, "requestLock($songId) — we already hold the lock")
                LockResult.Acquired
            } else {
                Log.d(TAG, "requestLock($songId) — lock held by '$existingOwner'")
                LockResult.LockedBy(existingOwner)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestLock($songId) failed — ${e::class.simpleName}: ${e.message}; granting silently")
            LockResult.Acquired
        }
    }

    /**
     * Upload [content] to [objectPath] only if no object exists there yet.
     * Uses GCS precondition `ifGenerationMatch=0` — the request is rejected with 412
     * if an object with any generation already exists, making lock acquisition atomic.
     *
     * @return true if the object was created, false if it already existed (412).
     */
    private fun tryCreateObject(token: String, objectPath: String, content: String, mimeType: String): Boolean {
        val encodedName = encodePath(objectPath)
        val url = "$UPLOAD_URL/b/$BUCKET/o?uploadType=media&name=$encodedName&ifGenerationMatch=0"
        val body = content.toRequestBody(mimeType.toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            Log.d(TAG, "tryCreateObject($objectPath) — HTTP ${response.code}")
            return when (response.code) {
                200, 201 -> true   // Created — we own the lock
                412      -> false  // Precondition Failed — object already exists
                else -> {
                    Log.e(TAG, "tryCreateObject($objectPath) — unexpected HTTP ${response.code}")
                    false
                }
            }
        }
    }

    override suspend fun releaseLock(songId: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "releaseLock($songId) — starting")
            try {
                val tok = token()
                deleteObject(tok, lockObjectPath(songId))
                Log.d(TAG, "releaseLock($songId) — done")
            } catch (e: Exception) {
                Log.e(TAG, "releaseLock($songId) failed — ${e::class.simpleName}: ${e.message}")
            }
        }
    }

    // ── Manifest ─────────────────────────────────────────────────────────────

    override suspend fun fetchManifest(userId: String): GcpManifest? = withContext(Dispatchers.IO) {
        Log.d(TAG, "fetchManifest(userId=$userId)")
        try {
            val tok = token()
            readManifestWithToken(tok)
        } catch (e: Exception) {
            Log.e(TAG, "fetchManifest failed — ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    private fun readManifestWithToken(token: String): GcpManifest? {
        val now = System.currentTimeMillis()
        manifestCache?.takeIf { now - manifestCachedAt < MANIFEST_CACHE_TTL_MS }?.let {
            Log.d(TAG, "readManifestWithToken — cache hit (${it.songs.size} entries)")
            return it
        }
        val body = readObject(token, MANIFEST_OBJECT) ?: return null
        return try {
            val root = JSONObject(body)
            val songs = mutableMapOf<String, GcpSongEntry>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val songId = keys.next()
                val obj = root.getJSONObject(songId)
                songs[songId] = GcpSongEntry(
                    hash = obj.getString("hash"),
                    updatedAt = obj.getLong("updatedAt")
                )
            }
            Log.d(TAG, "readManifestWithToken — fetched ${songs.size} entries from GCS")
            val manifest = GcpManifest(songs)
            manifestCache = manifest
            manifestCachedAt = now
            manifest
        } catch (e: Exception) {
            Log.e(TAG, "readManifestWithToken — JSON parse failed: ${e.message}")
            null
        }
    }

    /** Invalidate the manifest cache so the next read fetches fresh data from GCS. */
    override fun invalidateCache() {
        manifestCache = null
        manifestCachedAt = 0L
    }

    private fun invalidateManifestCache() = invalidateCache()

    // ── Song body upload / download ──────────────────────────────────────────

    override suspend fun uploadSong(userId: String, songId: String, markdownBody: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "uploadSong(userId=$userId, songId=$songId, bodyLen=${markdownBody.length})")
            try {
                val tok = token()
                val path = songObjectPath(userId, songId)
                Log.d(TAG, "uploadSong — uploading to path: $path")
                uploadObject(tok, path, markdownBody, "text/markdown")
                Log.d(TAG, "uploadSong — body uploaded; updating manifest")
                try {
                    updateManifest(tok, songId, markdownBody)
                } catch (e: Exception) {
                    // Manifest is a secondary index — 429 rate limit during bulk sync is expected.
                    // Song body is already uploaded; log and continue so markSynced is called.
                    Log.w(TAG, "uploadSong($songId) — manifest update failed (non-fatal): ${e.message}")
                }
                Log.d(TAG, "uploadSong($songId) — complete")
            } catch (e: Exception) {
                Log.e(TAG, "uploadSong($songId) failed — ${e::class.simpleName}: ${e.message}")
                throw e // re-throw so SongRepositoryImpl.uploadSongToCloud returns false
            }
        }
    }

    override suspend fun downloadSong(userId: String, songId: String): String? =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "downloadSong(userId=$userId, songId=$songId)")
            try {
                val tok = token()
                val result = readObject(tok, songObjectPath(userId, songId))
                Log.d(TAG, "downloadSong($songId) — ${if (result != null) "got ${result.length} chars" else "not found"}")
                result
            } catch (e: Exception) {
                Log.e(TAG, "downloadSong($songId) failed — ${e::class.simpleName}: ${e.message}")
                null
            }
        }

    // ── GCS REST helpers ─────────────────────────────────────────────────────

    private fun readObject(token: String, objectPath: String): String? {
        val encodedPath = encodePath(objectPath)
        val url = "$BASE_URL/b/$BUCKET/o/$encodedPath?alt=media"
        Log.d(TAG, "readObject — GET $url")
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            Log.d(TAG, "readObject($objectPath) — HTTP ${response.code}")
            if (!response.isSuccessful) {
                if (response.code != 404) {
                    Log.e(TAG, "readObject($objectPath) — FAILED ${response.code}: ${response.body?.string()}")
                }
                return null
            }
            return response.body?.string()
        }
    }

    private fun uploadObject(token: String, objectPath: String, content: String, mimeType: String) {
        val encodedName = encodePath(objectPath)
        val url = "$UPLOAD_URL/b/$BUCKET/o?uploadType=media&name=$encodedName"
        Log.d(TAG, "uploadObject — POST $url (mimeType=$mimeType, len=${content.length})")
        val body = content.toRequestBody(mimeType.toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            Log.d(TAG, "uploadObject($objectPath) — HTTP ${response.code}")
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "uploadObject($objectPath) — FAILED ${response.code}: $errorBody")
                throw IllegalStateException("Upload failed: HTTP ${response.code}: $errorBody")
            } else {
                Log.d(TAG, "uploadObject($objectPath) — SUCCESS")
            }
        }
    }

    private fun deleteObject(token: String, objectPath: String) {
        val encodedPath = encodePath(objectPath)
        val url = "$BASE_URL/b/$BUCKET/o/$encodedPath"
        Log.d(TAG, "deleteObject — DELETE $url")
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .delete()
            .build()

        http.newCall(request).execute().use { response ->
            Log.d(TAG, "deleteObject($objectPath) — HTTP ${response.code}")
            if (!response.isSuccessful && response.code != 404) {
                Log.e(TAG, "deleteObject($objectPath) — FAILED ${response.code}")
            }
        }
    }

    /**
     * Atomically read-modify-write the manifest under [manifestMutex].
     * [action] receives the current manifest JSONObject and modifies it in place.
     * The result is written back to GCS and the in-memory cache is invalidated.
     *
     * All callers that mutate the manifest must go through this function to prevent
     * concurrent uploads from clobbering each other's entries.
     */
    private suspend fun mutateManifest(token: String, action: (JSONObject) -> Unit) {
        manifestMutex.withLock {
            val existing = readObject(token, MANIFEST_OBJECT)
            val root = if (existing != null) {
                try { JSONObject(existing) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }
            action(root)
            uploadObject(token, MANIFEST_OBJECT, root.toString(), "application/json")
            invalidateManifestCache()
        }
    }

    private suspend fun updateManifest(token: String, songId: String, markdownBody: String) {
        Log.d(TAG, "updateManifest($songId)")
        mutateManifest(token) { root ->
            root.put(songId, JSONObject().apply {
                put("hash", FileHashUtils.hashMarkdownBodySync(markdownBody))
                put("updatedAt", System.currentTimeMillis())
            })
        }
    }

    // ── Path helpers ─────────────────────────────────────────────────────────

    private fun encodePath(path: String): String =
        URLEncoder.encode(path, "UTF-8").replace("+", "%20")

    private fun songObjectPath(userId: String, songId: String) = "$userId/songs/$songId.md"
    private fun lockObjectPath(songId: String) = "locks/$songId.lock"
    private fun setObjectPath(userId: String, setNumber: Int) = "$userId/sets/set_$setNumber.json"

    override suspend fun uploadSetData(userId: String, setNumber: Int, content: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "uploadSetData(set=$setNumber, len=${content.length})")
            try {
                val tok = token()
                uploadObject(tok, setObjectPath(userId, setNumber), content, "application/json")
                Log.d(TAG, "uploadSetData(set=$setNumber) — done")
            } catch (e: Exception) {
                Log.e(TAG, "uploadSetData(set=$setNumber) failed: ${e.message}")
                throw e
            }
        }
    }

    override suspend fun downloadSetData(userId: String, setNumber: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                val tok = token()
                readObject(tok, setObjectPath(userId, setNumber))
            } catch (e: Exception) {
                Log.e(TAG, "downloadSetData(set=$setNumber) failed: ${e.message}")
                null
            }
        }

    override suspend fun deleteSong(userId: String, songId: String) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "deleteSong(userId=$userId, songId=$songId)")
            try {
                val tok = token()
                deleteObject(tok, songObjectPath(userId, songId))
                mutateManifest(tok) { root -> root.remove(songId) }
                Log.d(TAG, "deleteSong($songId) — object deleted and manifest updated")
            } catch (e: Exception) {
                Log.e(TAG, "deleteSong($songId) failed (non-fatal): ${e.message}")
            }
        }
    }
}
