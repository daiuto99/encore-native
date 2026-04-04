import {
  getBlob,
  getDownloadURL,
  listAll,
  ref,
  uploadString,
} from 'firebase/storage';
import { storage } from '../lib/firebase';

const MANIFEST_PATH = 'system/library_health.json';

function normalizePath(value = '') {
  return value.replace(/^\/+/, '').replace(/\\/g, '/');
}

function mapStorageError(error, path = '') {
  const code = error?.code || '';
  const target = path ? `: ${path}` : '';

  if (code === 'storage/unauthorized' || code === 'storage/permission-denied') {
    return new Error(`Permission denied${target}`);
  }

  if (code === 'storage/object-not-found') {
    return new Error(`File not found${target}`);
  }

  if (code === 'storage/bucket-not-found') {
    return new Error('Bucket not found: encore-cloud-leo-2026-songs');
  }

  if (code === 'storage/retry-limit-exceeded') {
    return new Error(`Storage request timed out${target}`);
  }

  return new Error(error?.message || `Storage request failed${target}`);
}

async function readText(storageRef) {
  try {
    const blob = await getBlob(storageRef);
    return await blob.text();
  } catch (error) {
    if (error?.code === 'storage/unauthorized' || error?.code === 'storage/object-not-found') {
      throw mapStorageError(error, storageRef.fullPath);
    }

    try {
      const url = await getDownloadURL(storageRef);
      const response = await fetch(url);
      if (!response.ok) {
        if (response.status === 403) throw new Error(`Permission denied: ${storageRef.fullPath}`);
        if (response.status === 404) throw new Error(`File not found: ${storageRef.fullPath}`);
        throw new Error(`Unable to download ${storageRef.fullPath}`);
      }
      return await response.text();
    } catch (downloadError) {
      throw mapStorageError(downloadError, storageRef.fullPath);
    }
  }
}

async function walk(folderRef, collector) {
  let result;
  try {
    result = await listAll(folderRef);
  } catch (error) {
    throw mapStorageError(error, folderRef.fullPath || '/');
  }

  for (const item of result.items) {
    if (!item.fullPath.toLowerCase().endsWith('.md')) continue;
    collector.push({
      path: item.fullPath,
      name: item.name,
    });
  }

  for (const nested of result.prefixes) {
    await walk(nested, collector);
  }
}

export const CloudLibraryService = {
  async listMarkdownFiles(basePath = '') {
    const collector = [];
    await walk(ref(storage, normalizePath(basePath)), collector);
    collector.sort((a, b) => a.path.localeCompare(b.path));
    return collector;
  },

  async downloadMarkdownFile(path) {
    const cleanPath = normalizePath(path);
    const storageRef = ref(storage, cleanPath);
    try {
      return await readText(storageRef);
    } catch (error) {
      throw mapStorageError(error, cleanPath);
    }
  },

  async uploadMarkdownFile(path, content) {
    const cleanPath = normalizePath(path);
    const storageRef = ref(storage, cleanPath);
    try {
      await uploadString(storageRef, content, 'raw', {
        contentType: 'text/markdown; charset=utf-8',
      });
    } catch (error) {
      throw mapStorageError(error, cleanPath);
    }
  },

  /**
   * Update system/library_health.json so the Android tablet can detect
   * that a web-side save has occurred. Called after every uploadMarkdownFile.
   *
   * @param {string} songPath  Full GCS path of the saved song (e.g. "uid/songs/uuid.md")
   */
  async updateLibraryManifest(songPath) {
    const songId = normalizePath(songPath).split('/').pop()?.replace(/\.md$/i, '');
    if (!songId) return;
    const manifestRef = ref(storage, MANIFEST_PATH);
    let manifest = {};
    try {
      const blob = await getBlob(manifestRef);
      const text = await blob.text();
      manifest = JSON.parse(text);
    } catch (_) {
      // Manifest doesn't exist yet or is unreadable — start fresh
    }
    const now = Date.now();
    manifest[songId] = { hash: now.toString(), updatedAt: now };
    try {
      await uploadString(manifestRef, JSON.stringify(manifest), 'raw', {
        contentType: 'application/json; charset=utf-8',
      });
    } catch (_) {
      // Non-fatal: worst case the tablet won't detect this edit on next sync
    }
  },

  /**
   * Load a set sync file written by the Android tablet.
   * Returns the parsed JSON object or null if the set doesn't exist yet.
   */
  async loadSetFile(userId, setNumber) {
    const storageRef = ref(storage, `${userId}/sets/set_${setNumber}.json`);
    try {
      const blob = await getBlob(storageRef);
      return JSON.parse(await blob.text());
    } catch (_) {
      return null;
    }
  },

  /**
   * Save a set sync file to GCS so the tablet's background poller can detect and apply it.
   * Writes source="web" so the tablet knows to apply this rather than ignore it.
   */
  async saveSetFile(userId, setNumber, songIds) {
    const storageRef = ref(storage, `${userId}/sets/set_${setNumber}.json`);
    await uploadString(
      storageRef,
      JSON.stringify({ version: 1, updatedAt: Date.now(), source: 'web', songIds }),
      'raw',
      { contentType: 'application/json; charset=utf-8' },
    );
  },

  async uploadSetExport(fileName, jsonText) {
    const cleanName = normalizePath(`sets/${fileName}`);
    const storageRef = ref(storage, cleanName);
    try {
      await uploadString(storageRef, jsonText, 'raw', {
        contentType: 'application/json; charset=utf-8',
      });
      return cleanName;
    } catch (error) {
      throw mapStorageError(error, cleanName);
    }
  },
};
