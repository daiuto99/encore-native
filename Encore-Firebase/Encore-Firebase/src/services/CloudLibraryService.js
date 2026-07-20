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
   * Load a numbered set file from {userEmail}/sets/set_{N}.json.
   * Returns null if the file doesn't exist yet.
   */
  async loadSetFile(userEmail, setNumber, token) {
    const path = `${userEmail}/sets/set_${setNumber}.json`;
    try {
      const text = await gcsGet(path, token);
      const data = JSON.parse(text);
      // Expand bare UUIDs to full GCS paths so the web app can match songs
      if (Array.isArray(data?.songIds)) {
        data.songIds = data.songIds.map((id) =>
          id.includes('/') ? id : `${userEmail}/songs/${id}.md`
        );
      }
      return data;
    } catch (e) {
      if (e.message && e.message.includes('Not found')) return null;
      throw e;
    }
  },

  /**
   * Save a numbered set file to {userEmail}/sets/set_{N}.json.
   * Strips full GCS paths to bare UUIDs so Android can read them.
   */
  async saveSetFile(userEmail, setNumber, songIds, token) {
    const path = `${userEmail}/sets/set_${setNumber}.json`;
    const bareIds = songIds.map((id) => {
      const filename = id.split('/').pop() ?? id;
      return filename.replace(/\.md$/i, '');
    });
    const payload = JSON.stringify({ version: 1, updatedAt: Date.now(), source: 'web', songIds: bareIds });
    return gcsUpload(path, payload, 'application/json; charset=utf-8', token);
  },

  /**
   * List all .json files under {userEmail}/sets/ in the bucket.
   * Returns array of { name, path } sorted by name.
   */
  async listSetFiles(userEmail, token) {
    const prefix = `${userEmail}/sets/`;
    const params = new URLSearchParams({ prefix, maxResults: '200' });
    const res = await fetch(
      `${GCS_BASE}/b/${BUCKET}/o?${params}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (!res.ok) return [];
    const data = await res.json();
    return (data.items ?? [])
      .filter(item => item.name.toLowerCase().endsWith('.json'))
      .map(item => ({
        path: item.name,
        name: item.name.replace(`${prefix}`, '').replace(/\.json$/i, ''),
      }))
      .filter(f => f.name)
      .sort((a, b) => a.name.localeCompare(b.name));
  },

  /**
   * List saved *show* files under {userEmail}/sets/ — i.e. all named shows,
   * excluding the four numbered working files (set_1.json … set_4.json).
   * Returns array of { name, path } sorted by name.
   */
  async listShowFiles(userEmail, token) {
    const all = await this.listSetFiles(userEmail, token);
    return all.filter((f) => !/^set_[1-4]$/i.test(f.name));
  },

  /**
   * Download a named set from {userEmail}/sets/{name}.json.
   * Returns the parsed JSON object or null if not found.
   */
  async downloadNamedSet(userEmail, setName, token) {
    const path = `${userEmail}/sets/${setName}.json`;
    try {
      const text = await gcsGet(path, token);
      const data = JSON.parse(text);
      if (Array.isArray(data?.songIds)) {
        data.songIds = data.songIds.map((id) =>
          id.includes('/') ? id : `${userEmail}/songs/${id}.md`
        );
      }
      return data;
    } catch (e) {
      if (e.message && e.message.includes('Not found')) return null;
      throw e;
    }
  },

  /**
   * Save the current set as a named file to {userEmail}/sets/{name}.json.
   */
  async saveNamedSet(userEmail, setName, songIds, token) {
    const path = `${userEmail}/sets/${setName}.json`;
    const bareIds = songIds.map((id) => {
      const filename = id.split('/').pop() ?? id;
      return filename.replace(/\.md$/i, '');
    });
    const payload = JSON.stringify({
      version: 1,
      name: setName,
      updatedAt: Date.now(),
      source: 'web',
      songIds: bareIds,
    });
    return gcsUpload(path, payload, 'application/json; charset=utf-8', token);
  },

  /**
   * Save all 4 sets together as a named "show" file at {userEmail}/sets/{showName}.json.
   * allSets is { 1: [fullPaths...], 2: [...], 3: [...], 4: [...] }
   */
  async saveShowFile(userEmail, showName, allSets, token) {
    const path = `${userEmail}/sets/${showName}.json`;
    const bareSets = {};
    for (const [n, ids] of Object.entries(allSets)) {
      bareSets[n] = (ids ?? []).map((id) => {
        const filename = id.split('/').pop() ?? id;
        return filename.replace(/\.md$/i, '');
      });
    }
    const payload = JSON.stringify({
      version: 2,
      name: showName,
      updatedAt: Date.now(),
      source: 'web',
      sets: bareSets,
    });
    return gcsUpload(path, payload, 'application/json; charset=utf-8', token);
  },

  /**
   * Load a show file from {userEmail}/sets/{showName}.json.
   * Returns { 1: [fullPaths], 2: [...], 3: [...], 4: [...] } or null if not found.
   * Also handles legacy single-set files (songIds array) by placing them in slot 1.
   */
  async loadShowFile(userEmail, showName, token) {
    const path = `${userEmail}/sets/${showName}.json`;
    try {
      const text = await gcsGet(path, token);
      const data = JSON.parse(text);
      // Multi-set show format
      if (data?.sets && typeof data.sets === 'object') {
        const result = { 1: [], 2: [], 3: [], 4: [] };
        for (const [n, ids] of Object.entries(data.sets)) {
          result[Number(n)] = (ids ?? []).map((id) =>
            id.includes('/') ? id : `${userEmail}/songs/${id}.md`
          );
        }
        return result;
      }
      // Legacy single-set format
      if (Array.isArray(data?.songIds)) {
        const ids = data.songIds.map((id) =>
          id.includes('/') ? id : `${userEmail}/songs/${id}.md`
        );
        return { 1: ids, 2: [], 3: [], 4: [] };
      }
      return null;
    } catch (e) {
      if (e.message && e.message.includes('Not found')) return null;
      throw e;
    }
  },

  /** Delete a single object by its full GCS path. */
  async deleteFile(path, token) {
    const res = await fetch(
      `${GCS_BASE}/b/${BUCKET}/o/${enc(path)}`,
      { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } }
    );
    // 404 is fine — already gone
    if (!res.ok && res.status !== 404) throw gcsError(res.status, path);
  },
};
