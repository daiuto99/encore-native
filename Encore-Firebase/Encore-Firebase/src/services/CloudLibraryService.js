/**
 * CloudLibraryService — direct GCS REST API client.
 *
 * Uses the Google OAuth2 access token obtained from GoogleAuthProvider during
 * sign-in. All paths are scoped to {userEmail}/songs/ and {userEmail}/sets/,
 * matching the Android app's path convention exactly.
 *
 * Bucket: gs://encore-cloud-leo-2026-songs
 */

const BUCKET   = 'encore-cloud-leo-2026-songs';
const GCS_BASE = 'https://storage.googleapis.com/storage/v1';
const GCS_UP   = 'https://storage.googleapis.com/upload/storage/v1';

function enc(path) {
  return encodeURIComponent(path);
}

function gcsError(status, path) {
  if (status === 401) return new Error('Token expired — please sign in again.');
  if (status === 403) return new Error(`Permission denied: ${path}`);
  if (status === 404) return new Error(`Not found: ${path}`);
  return new Error(`GCS error ${status}: ${path}`);
}

async function gcsGet(path, token) {
  const res = await fetch(
    `${GCS_BASE}/b/${BUCKET}/o/${enc(path)}?alt=media`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!res.ok) throw gcsError(res.status, path);
  return res.text();
}

async function gcsUpload(path, body, contentType, token) {
  const res = await fetch(
    `${GCS_UP}/b/${BUCKET}/o?uploadType=media&name=${enc(path)}`,
    {
      method:  'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': contentType },
      body,
    }
  );
  if (!res.ok) throw gcsError(res.status, path);
  return path;
}

export const CloudLibraryService = {
  /**
   * List all .md files under {userEmail}/songs/ in the bucket.
   * Handles GCS pagination automatically.
   */
  async listMarkdownFiles(userEmail, token) {
    const prefix    = `${userEmail}/songs/`;
    const collector = [];
    let   pageToken = null;

    do {
      const params = new URLSearchParams({ prefix, maxResults: '1000' });
      if (pageToken) params.set('pageToken', pageToken);

      const res = await fetch(
        `${GCS_BASE}/b/${BUCKET}/o?${params}`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      if (!res.ok) throw gcsError(res.status, prefix);

      const data = await res.json();
      for (const item of data.items ?? []) {
        if (item.name.toLowerCase().endsWith('.md')) {
          collector.push({ path: item.name, name: item.name.split('/').pop() });
        }
      }
      pageToken = data.nextPageToken ?? null;
    } while (pageToken);

    collector.sort((a, b) => a.path.localeCompare(b.path));
    return collector;
  },

  /** Download a single markdown file by its full GCS path. */
  async downloadMarkdownFile(path, token) {
    return gcsGet(path, token);
  },

  /**
   * Upload (overwrite) a markdown file.
   * path is the full GCS object name, e.g. "user@example.com/songs/uuid.md"
   */
  async uploadMarkdownFile(path, content, token) {
    return gcsUpload(path, content, 'text/markdown; charset=utf-8', token);
  },

  /**
   * Export a set JSON to {userEmail}/sets/{fileName}.
   * Mirrors Android's sets path under the same user prefix.
   */
  async uploadSetExport(userEmail, fileName, jsonText, token) {
    const path = `${userEmail}/sets/${fileName}`;
    return gcsUpload(path, jsonText, 'application/json; charset=utf-8', token);
  },
};
