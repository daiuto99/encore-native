import { useEffect, useMemo, useState } from 'react';
import {
  Cloud,
  FolderOpen,
  LoaderCircle,
  LogIn,
  LogOut,
  RefreshCw,
  Save,
} from 'lucide-react';
import { onAuthStateChanged, signInWithPopup, signOut, type User } from 'firebase/auth';
import { auth, googleProvider } from './lib/firebase';
import { CloudLibraryService } from './services/CloudLibraryService';

type AppMode = 'cloud' | 'local';

type SongMeta = {
  title: string;
  artist: string;
  display_key: string;
  original_key: string;
  bpm: string;
  is_lead_guitar: boolean;
};

type SongRecord = {
  path: string;
  body: string;
  raw: string;
  metadata: SongMeta;
};


type FsDirectoryHandle = FileSystemDirectoryHandle & { values(): AsyncIterable<FileSystemHandle> };
type FsFileHandle = FileSystemFileHandle;

const card = 'rounded-3xl border border-white/10 bg-white/5 shadow-[0_20px_60px_rgba(0,0,0,0.25)]';
const button = 'inline-flex items-center gap-2 rounded-2xl px-4 py-3 text-sm font-semibold transition';

function normalizeLineEndings(value: string) {
  return value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
}

function stripYaml(raw: string) {
  const normalized = normalizeLineEndings(raw);
  if (!normalized.startsWith('---\n')) {
    return { yaml: '', body: normalized };
  }
  const end = normalized.indexOf('\n---\n', 4);
  if (end === -1) {
    return { yaml: '', body: normalized };
  }
  return {
    yaml: normalized.slice(4, end),
    body: normalized.slice(end + 5),
  };
}

function parseYaml(raw: string) {
  const { yaml, body } = stripYaml(raw);
  const map: Record<string, string> = {};
  yaml.split('\n').forEach((line) => {
    const match = line.match(/^([A-Za-z0-9_\-]+):\s*(.*)$/);
    if (!match) return;
    map[match[1]] = match[2].trim().replace(/^['"]|['"]$/g, '');
  });
  return { frontMatter: map, body };
}

function inferTitleArtistFromPath(path: string) {
  const file = (path.split('/').pop() || '').replace(/\.md$/i, '').trim();
  const normalized = file.replace(/[_]+/g, ' ').replace(/\s+/g, ' ').trim();
  const dashMatch = normalized.match(/^(.*?)\s+-\s+(.*?)$/);
  if (dashMatch) {
    return { title: dashMatch[1].trim(), artist: dashMatch[2].trim() };
  }
  return { title: normalized, artist: '' };
}

function extractBodyMetadata(body: string) {
  const normalized = normalizeLineEndings(body);
  const keyMatch = normalized.match(/^\*\*Key:\*\*\s*([^\n]+)$/im) || normalized.match(/^key:\s*([^\n]+)$/im);
  const bpmMatch = normalized.match(/^\*\*BPM:\*\*\s*([^\n]+)$/im) || normalized.match(/^bpm:\s*([^\n]+)$/im);
  return {
    displayKey: keyMatch?.[1]?.trim() ?? '',
    bpm: bpmMatch?.[1]?.trim() ?? '',
  };
}

function cleanSectionLabel(value: string) {
  const cleaned = value
    .replace(/^#+\s*/, '')
    .replace(/<[^>]+>/g, '')
    .replace(/[\[\]]/g, '')
    .trim();
  return cleaned ? `[${cleaned}]` : '';
}

function normalizeSectionLine(line: string) {
  const spanHeading = line.match(/<span[^>]*>\s*(#{1,6}\s*[^<]+?)\s*<\/span>/i);
  if (spanHeading) return cleanSectionLabel(spanHeading[1]);
  if (/^#{1,6}\s+/.test(line.trim())) return cleanSectionLabel(line.trim());
  return line;
}

function normalizeMarkdownBody(body: string) {
  return normalizeLineEndings(body)
    .split('\n')
    .map(normalizeSectionLine)
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function parseSong(path: string, raw: string): SongRecord {
  const normalized = normalizeLineEndings(raw);
  const { frontMatter, body } = parseYaml(normalized);
  const inferred = inferTitleArtistFromPath(path);
  const bodyMeta = extractBodyMetadata(body);

  return {
    path,
    raw: normalized,
    body,
    metadata: {
      title: frontMatter.title || inferred.title,
      artist: frontMatter.artist || inferred.artist,
      display_key: frontMatter.display_key || frontMatter.key || bodyMeta.displayKey,
      original_key: frontMatter.original_key || '',
      bpm: frontMatter.bpm || bodyMeta.bpm,
      is_lead_guitar: (frontMatter.is_lead_guitar || '').toLowerCase() === 'true',
    },
  };
}

function buildYaml(meta: SongMeta) {
  return [
    '---',
    `title: ${meta.title || ''}`,
    `artist: ${meta.artist || ''}`,
    `display_key: ${meta.display_key || ''}`,
    `original_key: ${meta.original_key || ''}`,
    `bpm: ${meta.bpm || ''}`,
    `is_lead_guitar: ${meta.is_lead_guitar ? 'true' : 'false'}`,
    '---',
  ].join('\n');
}

function serializeSong(record: SongRecord) {
  return `${buildYaml(record.metadata)}\n${normalizeMarkdownBody(record.body)}\n`;
}


function getInitials(name: string, email?: string | null) {
  const source = (name || email || '').trim();
  if (!source) return 'U';
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0] || ''}${parts[1][0] || ''}`.toUpperCase();
}

function userLabel(user: User) {
  return user.displayName || user.email || 'Signed-in user';
}

async function* walkDirectory(
  handle: FsDirectoryHandle,
  prefix = '',
): AsyncGenerator<{ path: string; handle: FsFileHandle }> {
  for await (const entry of handle.values()) {
    const path = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.kind === 'file' && entry.name.toLowerCase().endsWith('.md')) {
      yield { path, handle: entry as FsFileHandle };
    }
    if (entry.kind === 'directory') {
      yield* walkDirectory(entry as FsDirectoryHandle, path);
    }
  }
}

function renderPreview(body: string) {
  const normalized = normalizeMarkdownBody(body)
    .replace(/\[h\](.*?)\[\/h\]/g, '<span class="text-amber-300">$1</span>')
    .replace(/`\[([^`]+)\]`/g, '<span class="text-sky-300 font-semibold">[$1]</span>');

  return normalized
    .split('\n')
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed) return '<div class="h-4"></div>';
      if (/^\[[^\]]+\]$/.test(trimmed)) {
        return `<div class="mt-4 inline-flex rounded-full border border-white/10 bg-white/10 px-3 py-1 text-xs uppercase tracking-[0.24em] text-orange-300">${trimmed.slice(1, -1)}</div>`;
      }
      return `<div>${trimmed}</div>`;
    })
    .join('');
}

export default function App() {
  const [mode, setMode] = useState<AppMode>('cloud');
  const [authReady, setAuthReady] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [status, setStatus] = useState('Ready');
  const [authError, setAuthError] = useState('');
  const [songs, setSongs] = useState<SongRecord[]>([]);
  const [selectedPath, setSelectedPath] = useState('');
  const [draft, setDraft] = useState('');
  const [liveSets, setLiveSets] = useState<{ [setNumber: number]: string[] }>({});
  const [activeLiveSet, setActiveLiveSet] = useState(1);
  const [libraryHandle, setLibraryHandle] = useState<FsDirectoryHandle | null>(null);
  const [fileHandles, setFileHandles] = useState<Record<string, FsFileHandle>>({});

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser);
      setAuthReady(true);
      setAuthError('');
    });
    return unsub;
  }, []);

  useEffect(() => {
    if (mode !== 'cloud' || !authReady || !user) return;
    void refreshCloudLibrary();
  }, [mode, authReady, user]);

  const selectedSong = useMemo(
    () => songs.find((song) => song.path === selectedPath) || null,
    [songs, selectedPath],
  );

  function getUserId(): string | null {
    const path = songs[0]?.path;
    if (!path) return null;
    const parts = path.split('/');
    return parts.length > 1 ? parts[0] : null;
  }

  function uuidFromPath(path: string): string {
    return path.split('/').pop()?.replace(/\.md$/i, '') ?? path;
  }

  function songByUuid(uuid: string): SongRecord | undefined {
    return songs.find((s) => s.path.endsWith(`/${uuid}.md`));
  }

  useEffect(() => {
    setDraft(selectedSong?.body ?? '');
  }, [selectedSong?.path, selectedSong?.body]);

  async function handleSignIn() {
    setAuthError('');
    setStatus('Opening Google sign-in...');
    try {
      await signInWithPopup(auth, googleProvider);
      setStatus('Signed in. Loading cloud songs...');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to sign in with Google.';
      setAuthError(message);
      setStatus(message);
    }
  }

  async function refreshCloudLibrary() {
    setIsBusy(true);
    setStatus('Reading songs from cloud bucket...');
    try {
      const items = await CloudLibraryService.listMarkdownFiles();
      const loaded: SongRecord[] = [];
      for (let i = 0; i < items.length; i += 1) {
        const item = items[i];
        setStatus(`Loading ${i + 1} of ${items.length} cloud songs...`);
        try {
          const raw = await CloudLibraryService.downloadMarkdownFile(item.path);
          loaded.push(parseSong(item.path, raw));
        } catch (error) {
          throw new Error(error instanceof Error ? error.message : `Unable to load ${item.path}`);
        }
      }
      setSongs(loaded);
      setSelectedPath((current) => current || loaded[0]?.path || '');
      // Load live set data from GCS (tablet writes these on every set change)
      const uid = loaded[0]?.path.split('/')[0];
      if (uid) {
        const setData: { [n: number]: string[] } = {};
        for (let n = 1; n <= 4; n++) {
          const d = await CloudLibraryService.loadSetFile(uid, n);
          if (d?.songIds) setData[n] = d.songIds;
        }
        setLiveSets(setData);
        const firstSet = Object.keys(setData).map(Number).sort()[0];
        if (firstSet) setActiveLiveSet(firstSet);
      }
      setStatus(`Loaded ${loaded.length} songs from cloud.`);
    } catch (error) {
      setSongs([]);
      setSelectedPath('');
      setStatus(error instanceof Error ? error.message : 'Unable to load cloud songs.');
    } finally {
      setIsBusy(false);
    }
  }

  async function pickLocalLibrary() {
    const handle = await window.showDirectoryPicker({ mode: 'readwrite' });
    setLibraryHandle(handle as FsDirectoryHandle);
    await refreshLocalLibrary(handle as FsDirectoryHandle);
  }

  async function refreshLocalLibrary(handle = libraryHandle) {
    if (!handle) return;
    setIsBusy(true);
    setStatus('Reading songs from local folder...');
    try {
      const nextHandles: Record<string, FsFileHandle> = {};
      const loaded: SongRecord[] = [];
      for await (const item of walkDirectory(handle)) {
        nextHandles[item.path] = item.handle;
        const file = await item.handle.getFile();
        const raw = await file.text();
        loaded.push(parseSong(item.path, raw));
      }
      loaded.sort((a, b) => a.path.localeCompare(b.path));
      setFileHandles(nextHandles);
      setSongs(loaded);
      setSelectedPath((current) => current || loaded[0]?.path || '');
      setStatus(`Loaded ${loaded.length} songs from local folder.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Unable to load local songs.');
    } finally {
      setIsBusy(false);
    }
  }

  async function handleRefresh() {
    if (mode === 'cloud') {
      await refreshCloudLibrary();
      return;
    }
    await refreshLocalLibrary();
  }

  async function handleSaveSong() {
    if (!selectedSong) return;
    const bodyMeta = extractBodyMetadata(draft);
    const updated: SongRecord = {
      ...selectedSong,
      body: draft,
      metadata: {
        ...selectedSong.metadata,
        display_key: bodyMeta.displayKey || selectedSong.metadata.display_key,
        bpm: bodyMeta.bpm || selectedSong.metadata.bpm,
      },
    };
    const serialized = serializeSong(updated);
    setIsBusy(true);
    setStatus('Saving song...');
    try {
      if (mode === 'cloud') {
        await CloudLibraryService.uploadMarkdownFile(updated.path, serialized);
        // Update the manifest so the Android tablet can detect this web edit on next sync
        CloudLibraryService.updateLibraryManifest(updated.path).catch(() => {});
      } else {
        const fileHandle = fileHandles[updated.path];
        if (!fileHandle) throw new Error('No local file handle found for this song.');
        const writable = await fileHandle.createWritable();
        await writable.write(serialized);
        await writable.close();
      }
      setSongs((current) =>
        current.map((song) => (song.path === updated.path ? parseSong(updated.path, serialized) : song)),
      );
      setStatus(`Saved ${updated.metadata.title || updated.path}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Unable to save song.');
    } finally {
      setIsBusy(false);
    }
  }

  async function addToLiveSet(song: SongRecord) {
    const userId = getUserId();
    if (!userId || !cloudReady) return;
    const uuid = uuidFromPath(song.path);
    const current = liveSets[activeLiveSet] ?? [];
    if (current.includes(uuid)) return;
    const updated = [...current, uuid];
    setLiveSets((prev) => ({ ...prev, [activeLiveSet]: updated }));
    try {
      await CloudLibraryService.saveSetFile(userId, activeLiveSet, updated);
    } catch (e) {
      setStatus(e instanceof Error ? e.message : 'Failed to save set.');
    }
  }

  async function removeFromLiveSet(uuid: string) {
    const userId = getUserId();
    if (!userId || !cloudReady) return;
    const current = liveSets[activeLiveSet] ?? [];
    const updated = current.filter((id) => id !== uuid);
    setLiveSets((prev) => ({ ...prev, [activeLiveSet]: updated }));
    try {
      await CloudLibraryService.saveSetFile(userId, activeLiveSet, updated);
    } catch (e) {
      setStatus(e instanceof Error ? e.message : 'Failed to save set.');
    }
  }

  async function createLiveSet() {
    const userId = getUserId();
    if (!userId || !cloudReady) return;
    const nums = Object.keys(liveSets).map(Number);
    const next = nums.length > 0 ? Math.max(...nums) + 1 : 1;
    setLiveSets((prev) => ({ ...prev, [next]: [] }));
    setActiveLiveSet(next);
    await CloudLibraryService.saveSetFile(userId, next, []).catch(() => {});
  }


  const cloudReady = Boolean(user) && authReady;

  return (
    <div className="min-h-screen bg-[#08101f] text-slate-100">
      <div className="mx-auto flex min-h-screen max-w-[1600px] gap-6 p-6">
        <aside className={`${card} flex w-[320px] flex-col gap-4 p-5`}>
          <div>
            <div className="text-xs uppercase tracking-[0.32em] text-slate-400">Encore</div>
            <h1 className="mt-2 text-3xl font-semibold">Cloud Manager</h1>
            <p className="mt-2 text-sm text-slate-400">
              Manage the same song library your Android tablet uses, while still keeping a Local Mode fallback.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-2 rounded-3xl border border-white/10 bg-black/20 p-2">
            <button
              className={`${button} ${mode === 'cloud' ? 'bg-orange-500 text-white' : 'bg-transparent text-slate-300'}`}
              onClick={() => setMode('cloud')}
            >
              <Cloud size={16} /> Cloud Mode
            </button>
            <button
              className={`${button} ${mode === 'local' ? 'bg-orange-500 text-white' : 'bg-transparent text-slate-300'}`}
              onClick={() => setMode('local')}
            >
              <FolderOpen size={16} /> Local Mode
            </button>
          </div>

          {mode === 'cloud' ? (
            <div className="rounded-3xl border border-white/10 bg-black/20 p-4">
              {!authReady ? (
                <div className="flex items-center gap-3 text-sm text-slate-300">
                  <LoaderCircle className="animate-spin" size={16} /> Checking sign-in...
                </div>
              ) : user ? (
                <div className="space-y-3 text-sm text-slate-300">
                  <div className="flex items-center gap-3">
                    {user.photoURL ? (
                      <img
                        src={user.photoURL}
                        alt={userLabel(user)}
                        className="h-11 w-11 rounded-full border border-white/10 object-cover"
                      />
                    ) : (
                      <div className="flex h-11 w-11 items-center justify-center rounded-full border border-white/10 bg-orange-500/20 text-sm font-semibold text-orange-200">
                        {getInitials(user.displayName || '', user.email)}
                      </div>
                    )}
                    <div className="min-w-0">
                      <div className="font-medium text-white">{userLabel(user)}</div>
                      <div className="truncate text-xs text-slate-400">{user.email}</div>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button className={`${button} flex-1 bg-emerald-500 text-white`} onClick={refreshCloudLibrary}>
                      <RefreshCw size={16} /> Refresh cloud songs
                    </button>
                    <button className={`${button} bg-white/5 text-slate-200`} onClick={() => signOut(auth)}>
                      <LogOut size={16} /> Sign out
                    </button>
                  </div>
                </div>
              ) : (
                <div className="space-y-3">
                  <button
                    className={`${button} w-full justify-center bg-orange-500 text-white`}
                    onClick={handleSignIn}
                  >
                    <LogIn size={16} /> Sign in with Google
                  </button>
                  {authError ? <div className="text-sm text-rose-300">{authError}</div> : null}
                </div>
              )}
            </div>
          ) : (
            <div className="rounded-3xl border border-white/10 bg-black/20 p-4">
              <button className={`${button} w-full justify-center bg-orange-500 text-white`} onClick={pickLocalLibrary}>
                <FolderOpen size={16} /> Select local library folder
              </button>
            </div>
          )}

          <div className="flex gap-2">
            <button
              className={`${button} flex-1 justify-center bg-white/5 text-slate-200`}
              onClick={handleRefresh}
              disabled={isBusy || (mode === 'cloud' && !cloudReady) || (mode === 'local' && !libraryHandle)}
            >
              <RefreshCw size={16} className={isBusy ? 'animate-spin' : ''} /> Refresh
            </button>
          </div>

          <div className="rounded-3xl border border-white/10 bg-black/20 p-4 text-sm text-slate-300">
            <div className="text-xs uppercase tracking-[0.24em] text-slate-400">Status</div>
            <div className="mt-2">{status}</div>
            <div className="mt-3 text-slate-400">Songs loaded: {songs.length}</div>
          </div>

          <div className="flex-1 overflow-hidden rounded-3xl border border-white/10 bg-black/20">
            <div className="border-b border-white/10 px-4 py-3 text-xs uppercase tracking-[0.24em] text-slate-400">
              Library
            </div>
            <div className="max-h-[520px] overflow-auto p-2">
              {songs.map((song) => (
                <button
                  key={song.path}
                  className={`mb-2 w-full rounded-2xl border p-3 text-left transition ${
                    selectedPath === song.path ? 'border-orange-400 bg-orange-500/10' : 'border-white/5 bg-white/5 hover:border-white/15'
                  }`}
                  onClick={() => setSelectedPath(song.path)}
                >
                  <div className="font-medium text-white">{song.metadata.title || 'Untitled song'}</div>
                  <div className="text-sm text-slate-400">{song.metadata.artist || 'Unknown artist'}</div>
                  <div className="mt-1 text-xs text-slate-500">{song.path}</div>
                </button>
              ))}
            </div>
          </div>
        </aside>

        <main className="grid flex-1 grid-cols-[1.1fr_0.9fr] gap-6">
          <section className={`${card} flex min-h-[calc(100vh-3rem)] flex-col p-5`}>
            <div className="flex items-center justify-between gap-4 border-b border-white/10 pb-4">
              <div>
                <div className="text-xs uppercase tracking-[0.32em] text-slate-400">Harmony Editor</div>
                <h2 className="mt-2 text-2xl font-semibold">{selectedSong?.metadata.title || 'Select a song'}</h2>
                <div className="mt-1 text-sm text-slate-400">
                  {selectedSong?.path || 'Open a song from the library to start editing.'}
                </div>
              </div>
              <button
                className={`${button} ${
                  selectedSong ? 'bg-emerald-500 text-white' : 'cursor-not-allowed bg-white/5 text-slate-500'
                }`}
                onClick={handleSaveSong}
                disabled={!selectedSong || isBusy || (mode === 'cloud' && !cloudReady)}
              >
                <Save size={16} /> Save
              </button>
            </div>

            <div className="mt-5 grid flex-1 grid-cols-2 gap-4">
              <textarea
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                className="min-h-[720px] w-full rounded-3xl border border-white/10 bg-[#050b16] p-4 font-mono text-sm leading-7 text-slate-100 outline-none"
                placeholder="Song markdown appears here"
                disabled={!selectedSong}
              />
              <div className="min-h-[720px] rounded-3xl border border-white/10 bg-[#050b16] p-4">
                <div className="mb-4 text-xs uppercase tracking-[0.24em] text-slate-400">Rendered preview</div>
                <div
                  className="space-y-1 font-sans text-lg leading-8 text-slate-100"
                  dangerouslySetInnerHTML={{ __html: renderPreview(draft) }}
                />
              </div>
            </div>
          </section>

          <section className="flex min-h-[calc(100vh-3rem)] flex-col gap-6">
            <div className={`${card} flex flex-col p-5`}>
              <div className="flex items-center justify-between gap-4 border-b border-white/10 pb-4">
                <div>
                  <div className="text-xs uppercase tracking-[0.32em] text-slate-400">Live Sets</div>
                  <h2 className="mt-2 text-2xl font-semibold">Tablet Set Manager</h2>
                  <p className="mt-1 text-sm text-slate-400">Changes sync to the tablet within 2 minutes.</p>
                </div>
                {mode === 'cloud' && cloudReady && (
                  <button className={`${button} bg-white/5 text-slate-200`} onClick={createLiveSet}>
                    + New Set
                  </button>
                )}
              </div>

              {/* Set tabs */}
              {Object.keys(liveSets).length > 0 ? (
                <div className="mt-4 flex gap-2 border-b border-white/10 pb-4">
                  {Object.keys(liveSets)
                    .map(Number)
                    .sort((a, b) => a - b)
                    .map((n) => (
                      <button
                        key={n}
                        className={`rounded-2xl px-4 py-2 text-sm font-medium transition ${
                          activeLiveSet === n ? 'bg-orange-500 text-white' : 'bg-white/5 text-slate-300 hover:bg-white/10'
                        }`}
                        onClick={() => setActiveLiveSet(n)}
                      >
                        Set {n}
                      </button>
                    ))}
                </div>
              ) : mode === 'cloud' && cloudReady ? (
                <p className="mt-4 text-sm text-slate-500">
                  No sets found. Sync from your tablet first, or tap "+ New Set".
                </p>
              ) : null}

              {/* Two-column: current set | library picker */}
              <div className="mt-4 grid flex-1 grid-cols-2 gap-4">
                <div className="rounded-3xl border border-white/10 bg-black/20 p-4">
                  <div className="mb-3 text-sm font-semibold text-white">
                    Set {activeLiveSet}{' '}
                    <span className="font-normal text-slate-400">
                      — {(liveSets[activeLiveSet] ?? []).length} songs
                    </span>
                  </div>
                  <div className="max-h-[480px] space-y-2 overflow-auto">
                    {(liveSets[activeLiveSet] ?? []).map((uuid, idx) => {
                      const song = songByUuid(uuid);
                      return (
                        <div
                          key={uuid}
                          className="flex items-center gap-2 rounded-2xl border border-white/10 bg-white/5 px-3 py-2 text-sm"
                        >
                          <span className="w-5 shrink-0 text-center text-slate-500">{idx + 1}</span>
                          <div className="min-w-0 flex-1">
                            <div className="truncate font-medium text-white">
                              {song?.metadata.title || uuid}
                            </div>
                            <div className="truncate text-xs text-slate-400">
                              {song?.metadata.artist || ''}
                              {song?.metadata.display_key ? ` · ${song.metadata.display_key}` : ''}
                            </div>
                          </div>
                          {mode === 'cloud' && cloudReady && (
                            <button
                              className="shrink-0 rounded-lg px-2 py-1 text-xs text-slate-500 hover:bg-white/10 hover:text-rose-400"
                              onClick={() => removeFromLiveSet(uuid)}
                            >
                              ✕
                            </button>
                          )}
                        </div>
                      );
                    })}
                    {(liveSets[activeLiveSet] ?? []).length === 0 && (
                      <p className="text-sm text-slate-500">No songs yet — add from the library →</p>
                    )}
                  </div>
                </div>

                <div className="rounded-3xl border border-white/10 bg-black/20 p-4">
                  <div className="mb-3 text-sm font-semibold text-white">Library</div>
                  <div className="max-h-[480px] space-y-2 overflow-auto">
                    {songs.map((song) => {
                      const uuid = uuidFromPath(song.path);
                      const inSet = (liveSets[activeLiveSet] ?? []).includes(uuid);
                      return (
                        <button
                          key={song.path}
                          disabled={!cloudReady || mode !== 'cloud'}
                          className={`w-full rounded-2xl border px-3 py-2 text-left text-sm transition ${
                            inSet
                              ? 'border-emerald-400/60 bg-emerald-500/10'
                              : 'border-white/10 bg-white/5 hover:border-orange-400/40 hover:bg-orange-500/5'
                          }`}
                          onClick={() => { if (!inSet) addToLiveSet(song); }}
                        >
                          <div className="font-medium text-white">{song.metadata.title || 'Untitled'}</div>
                          <div className="text-xs text-slate-400">
                            {song.metadata.artist || 'Unknown artist'}
                            {song.metadata.display_key ? ` · ${song.metadata.display_key}` : ''}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}
