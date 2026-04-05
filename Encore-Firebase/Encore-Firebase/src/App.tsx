import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  ChevronDown,
  Cloud,
  FileText,
  FolderOpen,
  Guitar,
  Hash,
  ListMusic,
  LogIn,
  LogOut,
  Music2,
  RefreshCw,
  Save,
  Search,
  Tag,
  Upload,
  X,
} from 'lucide-react';
import { GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut, type User } from 'firebase/auth';
import { auth, googleProvider } from './lib/firebase';
import { CloudLibraryService } from './services/CloudLibraryService';

// ── Types ─────────────────────────────────────────────────────────────────────

type AppMode = 'cloud' | 'local';
type AppTab  = 'editor' | 'setlist' | 'health';

type SongMeta = {
  title: string;
  artist: string;
  display_key: string;
  original_key: string;
  is_lead_guitar: boolean;
  bpm: string;
  capo: string;
};

type SongRecord = {
  path: string;
  body: string;
  raw: string;
  metadata: SongMeta;
};

type SetSong = {
  title: string;
  artist: string;
  displayKey?: string;
  markdownBody: string;
};

// A parsed block in the block editor: either a section header or a content chunk
type Block =
  | { type: 'section'; label: string }
  | { type: 'content'; text: string };

type HealthItem = { song: SongRecord; issues: string[] };

type FsDirectoryHandle = FileSystemDirectoryHandle & { values(): AsyncIterable<FileSystemHandle> };
type FsFileHandle = FileSystemFileHandle;

// ── Constants ─────────────────────────────────────────────────────────────────

const SECTION_PRESETS = [
  'Intro', 'Verse 1', 'Verse 2', 'Verse 3', 'Verse 4',
  'Pre-Chorus', 'Chorus', 'Bridge', 'Outro', 'Tag',
  'Solo', 'Instrumental', 'Breakdown', 'Interlude', 'Vamp', 'Coda', 'Hook',
];

// Must match Android's LibraryAuditWorker.KNOWN_SECTIONS
const KNOWN_SECTIONS = new Set([
  'intro', 'verse', 'chorus', 'pre-chorus', 'prechorus', 'pre chorus',
  'bridge', 'outro', 'solo', 'interlude', 'instrumental', 'key', 'tag',
  'vamp', 'breakdown', 'hook', 'refrain', 'coda', 'turnaround',
]);

// ── String helpers ─────────────────────────────────────────────────────────────

function normalizeLineEndings(value: string) {
  return value.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
}

function stripYaml(raw: string) {
  const normalized = normalizeLineEndings(raw);
  if (!normalized.startsWith('---\n')) return { yaml: '', body: normalized };
  const end = normalized.indexOf('\n---\n', 4);
  if (end === -1) return { yaml: '', body: normalized };
  return { yaml: normalized.slice(4, end), body: normalized.slice(end + 5) };
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
  if (dashMatch) return { title: dashMatch[1].trim(), artist: dashMatch[2].trim() };
  return { title: normalized, artist: '' };
}

function extractBodyMetadata(body: string) {
  const normalized = normalizeLineEndings(body);
  const keyMatch = normalized.match(/^\*\*Key:\*\*\s*([^\n]+)$/im) || normalized.match(/^key:\s*([^\n]+)$/im);
  return { displayKey: keyMatch?.[1]?.trim() ?? '' };
}

function cleanSectionLabel(value: string) {
  const cleaned = value.replace(/^#+\s*/, '').replace(/<[^>]+>/g, '').replace(/[\[\]]/g, '').trim();
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
  const inferred  = inferTitleArtistFromPath(path);
  const bodyMeta  = extractBodyMetadata(body);
  return {
    path,
    raw: normalized,
    body,
    metadata: {
      title:          frontMatter.title       || inferred.title,
      artist:         frontMatter.artist      || inferred.artist,
      display_key:    frontMatter.display_key || frontMatter.key || bodyMeta.displayKey,
      original_key:   frontMatter.original_key || '',
      is_lead_guitar: (frontMatter.is_lead_guitar || '').toLowerCase() === 'true',
      bpm:            frontMatter.bpm         || '',
      capo:           frontMatter.capo        || '',
    },
  };
}

function buildYaml(meta: SongMeta) {
  const lines = [
    '---',
    `title: ${meta.title || ''}`,
    `artist: ${meta.artist || ''}`,
    `display_key: ${meta.display_key || ''}`,
    `original_key: ${meta.original_key || ''}`,
    `is_lead_guitar: ${meta.is_lead_guitar ? 'true' : 'false'}`,
  ];
  if (meta.bpm)  lines.push(`bpm: ${meta.bpm}`);
  if (meta.capo) lines.push(`capo: ${meta.capo}`);
  lines.push('---');
  return lines.join('\n');
}

function serializeSong(record: SongRecord) {
  return `${buildYaml(record.metadata)}\n${normalizeMarkdownBody(record.body)}\n`;
}

function sanitizeSetFileName(name: string) {
  return (name || 'Imported Set').replace(/[/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim();
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
    if (entry.kind === 'directory') yield* walkDirectory(entry as FsDirectoryHandle, path);
  }
}

// ── Block editor helpers ───────────────────────────────────────────────────────

function parseBlocks(draft: string): Block[] {
  const lines = draft.split('\n');
  const blocks: Block[] = [];
  let contentLines: string[] = [];

  for (const line of lines) {
    if (/^\[[^\]]+\]$/.test(line.trim())) {
      if (contentLines.length > 0) {
        blocks.push({ type: 'content', text: contentLines.join('\n') });
        contentLines = [];
      }
      blocks.push({ type: 'section', label: line.trim().slice(1, -1) });
    } else {
      contentLines.push(line);
    }
  }
  if (contentLines.length > 0) {
    blocks.push({ type: 'content', text: contentLines.join('\n') });
  }
  return blocks;
}

function blocksToString(blocks: Block[]): string {
  return blocks
    .map((b) => (b.type === 'section' ? `[${b.label}]` : b.text))
    .join('\n');
}

// ── Preview renderer ─────────────────────────────────────────────────────────

const MARK_STYLE = 'background:#fef3c7;color:#92400e;border-radius:3px;padding:0 2px';

function renderPreview(body: string): string {
  const normalized = normalizeMarkdownBody(body);
  const lines = normalized.split('\n');

  let inHarmony = false;
  return lines.map((line) => {
    let text = line;

    // Replace fully-contained same-line [h]...[/h] pairs first
    text = text.replace(/\[h\](.*?)\[\/h\]/g, `<mark style="${MARK_STYLE}">$1</mark>`);

    const hasOpen  = text.includes('[h]');
    const hasClose = text.includes('[/h]');
    const startsHarmony = hasOpen  && !hasClose;
    const endsHarmony   = !hasOpen && hasClose;

    if (startsHarmony) text = text.replace('[h]', '');
    if (endsHarmony)   text = text.replace('[/h]', '');

    // Determine if this line should be highlighted
    const lineHighlighted = inHarmony || startsHarmony || endsHarmony;

    // Advance state for next line
    if (startsHarmony) inHarmony = true;
    if (endsHarmony)   inHarmony = false;

    const trimmed = text.trim();
    if (!trimmed) return '<div style="height:12px"></div>';

    // Section header [Verse 1]
    if (/^\[[^\]]+\]$/.test(trimmed)) {
      return `<div style="margin-top:20px;margin-bottom:6px;font-size:11px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:#94a3b8">${trimmed.slice(1, -1)}</div>`;
    }

    // Chord lines: short uppercase tokens, no long lowercase words
    const isChord = /^[A-G][#b]?[^\s]*(\s+[A-G][#b]?[^\s]*)*\s*$/.test(trimmed) && !/[a-z]{3}/.test(trimmed);
    const baseStyle = isChord
      ? 'font-family:monospace;font-size:13px;font-weight:600;color:#3b82f6;letter-spacing:.05em;line-height:1.8'
      : 'line-height:1.8;font-size:15px;color:#1e293b';

    const content = lineHighlighted
      ? `<mark style="${MARK_STYLE}">${trimmed}</mark>`
      : trimmed;

    return `<div style="${baseStyle}">${content}</div>`;
  }).join('');
}

// ── Health check (mirrors Android LibraryAuditWorker exactly) ─────────────────

function checkSongHealth(song: SongRecord): string[] {
  const issues: string[] = [];
  const body = song.body;

  // 1. Mandatory metadata (same thresholds as Android)
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(song.metadata.title);
  if (!song.metadata.title || isUuid) issues.push('Missing title');
  if (!song.metadata.artist || song.metadata.artist.toLowerCase() === 'unknown artist') {
    issues.push('Missing artist');
  }
  if (!song.metadata.display_key) issues.push('Missing key');

  // 2. Unclosed [h] tags
  const opens  = (body.match(/\[h\]/gi)  || []).length;
  const closes = (body.match(/\[\/h\]/gi) || []).length;
  if (opens !== closes) issues.push(`Unclosed [h] tags (${opens} open / ${closes} close)`);

  // 3. Non-standard section headers (span format + markdown # format + bracket format)
  const spanHeaders    = Array.from(body.matchAll(/<span[^>]*>##?\s*(.*?)<\/span>/gi)).map((m) => m[1].trim());
  const mdHeaders      = Array.from(body.matchAll(/^#{1,2}\s+(.+)$/gm)).map((m) => m[1].trim());
  const bracketHeaders = Array.from(body.matchAll(/^\[([A-Za-z][^\]]*)\]$/gm)).map((m) => m[1].trim());

  [...spanHeaders, ...mdHeaders, ...bracketHeaders].forEach((raw) => {
    const normalised = raw.toLowerCase().replace(/[\s\d]+$/, '').trim();
    const recognised = [...KNOWN_SECTIONS].some(
      (s) => normalised === s || normalised.startsWith(s),
    );
    if (!recognised && raw) issues.push(`Non-standard section: "${raw}"`);
  });

  return issues;
}

const EMPTY_META: SongMeta = {
  title: '', artist: '', display_key: '', original_key: '',
  is_lead_guitar: false, bpm: '', capo: '',
};

// ── Sub-components ─────────────────────────────────────────────────────────────

function MetaField({
  label, value, onChange, width = 'auto', type = 'text',
}: {
  label: string; value: string; onChange: (v: string) => void; width?: string; type?: string;
}) {
  return (
    <label className="flex flex-col gap-0.5">
      <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{ width }}
        className="rounded-md border border-slate-200 bg-white px-2 py-1 text-sm text-slate-800 outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-200"
      />
    </label>
  );
}

// ── Section button (rendered in the block editor) ─────────────────────────────

function SectionButton({
  label,
  onChangeLabel,
}: {
  label: string;
  onChangeLabel: (newLabel: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div ref={ref} className="relative my-3 flex items-center">
      <button
        onClick={() => setOpen((o) => !o)}
        className={`flex items-center gap-1.5 rounded-lg border px-3 py-1 text-xs font-bold uppercase tracking-widest transition ${
          open
            ? 'border-blue-400 bg-blue-50 text-blue-700'
            : 'border-slate-200 bg-slate-50 text-slate-500 hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700'
        }`}
      >
        {label}
        <ChevronDown size={11} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-20 mt-1 max-h-64 w-44 overflow-y-auto rounded-lg border border-slate-200 bg-white shadow-xl">
          <div className="p-1">
            {SECTION_PRESETS.map((preset) => (
              <button
                key={preset}
                onClick={() => { onChangeLabel(preset); setOpen(false); }}
                className={`w-full rounded-md px-3 py-1.5 text-left text-sm transition hover:bg-slate-50 ${
                  preset === label ? 'font-semibold text-blue-600' : 'text-slate-700'
                }`}
              >
                {preset}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ── App ────────────────────────────────────────────────────────────────────────

export default function App() {
  const [mode, setMode]                 = useState<AppMode>('cloud');
  const [activeTab, setActiveTab]       = useState<AppTab>('editor');
  const [authReady, setAuthReady]       = useState(false);
  const [user, setUser]                 = useState<User | null>(null);
  const [gcsToken, setGcsToken]         = useState<string | null>(() => sessionStorage.getItem('gcs_token'));
  const [isBusy, setIsBusy]             = useState(false);
  const [status, setStatus]             = useState('Ready');
  const [authError, setAuthError]       = useState('');
  const [songs, setSongs]               = useState<SongRecord[]>([]);
  const [selectedPath, setSelectedPath] = useState('');
  const [draft, setDraft]               = useState('');
  const [draftMeta, setDraftMeta]       = useState<SongMeta>(EMPTY_META);
  const [searchQuery, setSearchQuery]   = useState('');
  const [setName, setSetName]           = useState('Friday Night');
  const [setSongIds, setSetSongIds]     = useState<string[]>([]);
  const [libraryHandle, setLibraryHandle]   = useState<FsDirectoryHandle | null>(null);
  const [fileHandles, setFileHandles]       = useState<Record<string, FsFileHandle>>({});

  // The currently-focused content textarea (for harmony marking)
  const activeTextareaRef = useRef<HTMLTextAreaElement | null>(null);

  // ── Auth ──────────────────────────────────────────────────────────────────

  useEffect(() => {
    const unsub = onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser);
      setAuthReady(true);
      setAuthError('');
    });
    return unsub;
  }, []);

  useEffect(() => {
    if (mode !== 'cloud' || !authReady || !user || !gcsToken) return;
    void refreshCloudLibrary();
  }, [mode, authReady, user, gcsToken]);

  // ── Derived state ─────────────────────────────────────────────────────────

  const selectedSong = useMemo(
    () => songs.find((s) => s.path === selectedPath) || null,
    [songs, selectedPath],
  );

  const filteredSongs = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return songs;
    return songs.filter((s) =>
      s.metadata.title.toLowerCase().includes(q) ||
      s.metadata.artist.toLowerCase().includes(q) ||
      s.metadata.display_key.toLowerCase().includes(q),
    );
  }, [songs, searchQuery]);

  const activeSetSongs = useMemo(
    () => setSongIds.map((p) => songs.find((s) => s.path === p)).filter((s): s is SongRecord => Boolean(s)),
    [setSongIds, songs],
  );

  // Block editor: sections + content chunks derived from draft
  const blocks = useMemo(() => parseBlocks(draft), [draft]);

  const healthItems = useMemo<HealthItem[]>(
    () => songs.map((song) => ({ song, issues: checkSongHealth(song) })).filter((h) => h.issues.length > 0),
    [songs],
  );

  const healthMap = useMemo(() => {
    const m: Record<string, number> = {};
    healthItems.forEach(({ song, issues }) => { m[song.path] = issues.length; });
    return m;
  }, [healthItems]);

  const cloudReady = Boolean(user) && authReady && Boolean(gcsToken);

  // ── Sync draft/meta when selection changes ────────────────────────────────

  useEffect(() => {
    if (selectedSong) {
      setDraftMeta(selectedSong.metadata);
      setDraft(normalizeMarkdownBody(selectedSong.body));
    } else {
      setDraftMeta(EMPTY_META);
      setDraft('');
    }
  }, [selectedSong?.path]);

  // ── Block editor helpers ──────────────────────────────────────────────────

  function updateContentBlock(blockIdx: number, newText: string) {
    const newBlocks = blocks.map((b, i) =>
      i === blockIdx && b.type === 'content' ? { ...b, text: newText } : b,
    );
    setDraft(blocksToString(newBlocks));
  }

  function changeSectionLabel(blockIdx: number, newLabel: string) {
    const newBlocks = blocks.map((b, i) =>
      i === blockIdx && b.type === 'section' ? { ...b, label: newLabel } : b,
    );
    setDraft(blocksToString(newBlocks));
  }

  // ── Harmony markup ────────────────────────────────────────────────────────

  function handleMarkHarmony() {
    const ta = activeTextareaRef.current;
    if (!ta) return;
    const start = ta.selectionStart;
    const end   = ta.selectionEnd;
    if (start === end) return;

    // Find which block this textarea belongs to (via data-block-idx attribute)
    const blockIdx = Number(ta.dataset['blockIdx'] ?? -1);
    if (blockIdx < 0) return;

    const block = blocks[blockIdx];
    if (!block || block.type !== 'content') return;

    const text = block.text;
    const newText = text.slice(0, start) + '[h]' + text.slice(start, end) + '[/h]' + text.slice(end);
    updateContentBlock(blockIdx, newText);

    requestAnimationFrame(() => {
      if (!ta) return;
      ta.focus();
      ta.selectionStart = start + 3;
      ta.selectionEnd   = end   + 3;
    });
  }

  // ── Cloud/local actions ───────────────────────────────────────────────────

  async function handleSignIn() {
    setAuthError('');
    setStatus('Opening Google sign-in…');
    try {
      const result     = await signInWithPopup(auth, googleProvider);
      const credential = GoogleAuthProvider.credentialFromResult(result);
      const token      = credential?.accessToken ?? null;
      setGcsToken(token);
      if (token) sessionStorage.setItem('gcs_token', token);
      setStatus('Signed in.');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to sign in with Google.';
      setAuthError(message);
      setStatus(message);
    }
  }

  async function refreshCloudLibrary() {
    if (!user?.email || !gcsToken) { setStatus('Sign in first.'); return; }
    setIsBusy(true);
    setStatus('Reading songs from cloud…');
    try {
      const items  = await CloudLibraryService.listMarkdownFiles(user.email, gcsToken);
      const loaded: SongRecord[] = [];
      for (let i = 0; i < items.length; i++) {
        setStatus(`Loading ${i + 1} / ${items.length}…`);
        const raw = await CloudLibraryService.downloadMarkdownFile(items[i].path, gcsToken);
        loaded.push(parseSong(items[i].path, raw));
      }
      loaded.sort((a, b) => {
        const ta = a.metadata.title.toLowerCase();
        const tb = b.metadata.title.toLowerCase();
        return ta < tb ? -1 : ta > tb ? 1 : 0;
      });
      setSongs(loaded);
      setSelectedPath((cur) => cur || loaded[0]?.path || '');
      setStatus(`${loaded.length} songs loaded.`);
    } catch (error) {
      setSongs([]);
      setSelectedPath('');
      const msg = error instanceof Error ? error.message : 'Unable to load songs.';
      setStatus(msg);
      if (msg.includes('expired') || msg.includes('401')) {
        sessionStorage.removeItem('gcs_token');
        setGcsToken(null);
      }
    } finally {
      setIsBusy(false);
    }
  }

  async function pickLocalLibrary() {
    const handle = await (window as any).showDirectoryPicker({ mode: 'readwrite' });
    setLibraryHandle(handle as FsDirectoryHandle);
    await refreshLocalLibrary(handle as FsDirectoryHandle);
  }

  async function refreshLocalLibrary(handle = libraryHandle) {
    if (!handle) return;
    setIsBusy(true);
    setStatus('Reading local folder…');
    try {
      const nextHandles: Record<string, FsFileHandle> = {};
      const loaded: SongRecord[] = [];
      for await (const item of walkDirectory(handle)) {
        nextHandles[item.path] = item.handle;
        const file = await item.handle.getFile();
        loaded.push(parseSong(item.path, await file.text()));
      }
      loaded.sort((a, b) => a.path.localeCompare(b.path));
      setFileHandles(nextHandles);
      setSongs(loaded);
      setSelectedPath((cur) => cur || loaded[0]?.path || '');
      setStatus(`${loaded.length} songs loaded.`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Unable to load local songs.');
    } finally {
      setIsBusy(false);
    }
  }

  async function handleSaveSong() {
    if (!selectedSong) return;
    const updated: SongRecord = { ...selectedSong, body: draft, metadata: draftMeta };
    const serialized = serializeSong(updated);
    setIsBusy(true);
    setStatus('Saving…');
    try {
      if (mode === 'cloud') {
        await CloudLibraryService.uploadMarkdownFile(updated.path, serialized, gcsToken!);
      } else {
        const fh = fileHandles[updated.path];
        if (!fh) throw new Error('No file handle for this song.');
        const writable = await fh.createWritable();
        await writable.write(serialized);
        await writable.close();
      }
      setSongs((cur) => cur.map((s) => s.path === updated.path ? parseSong(updated.path, serialized) : s));
      setStatus(`Saved "${draftMeta.title || updated.metadata.title}".`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Save failed.');
    } finally {
      setIsBusy(false);
    }
  }

  async function exportSet() {
    const setData: SetSong[] = activeSetSongs.map((s) => ({
      title:        s.metadata.title  || inferTitleArtistFromPath(s.path).title,
      artist:       s.metadata.artist || inferTitleArtistFromPath(s.path).artist || 'Unknown Artist',
      ...(s.metadata.display_key ? { displayKey: s.metadata.display_key } : {}),
      markdownBody: normalizeMarkdownBody(s.body),
    }));
    const payload  = JSON.stringify({ version: 1, name: setName || 'Imported Set', songs: setData }, null, 2);
    const fileName = `${sanitizeSetFileName(setName || 'Imported Set')}.encore.json`;
    setIsBusy(true);
    setStatus('Exporting set…');
    try {
      if (mode === 'cloud') {
        const savedPath = await CloudLibraryService.uploadSetExport(user!.email!, fileName, payload, gcsToken!);
        setStatus(`Exported to cloud: ${savedPath}`);
      } else {
        if (!libraryHandle) throw new Error('Choose a local folder first.');
        const setsHandle = await libraryHandle.getDirectoryHandle('sets', { create: true });
        const fh = await setsHandle.getFileHandle(fileName, { create: true });
        const writable = await fh.createWritable();
        await writable.write(payload);
        await writable.close();
        setStatus(`Exported: sets/${fileName}`);
      }
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Export failed.');
    } finally {
      setIsBusy(false);
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-slate-50 text-slate-900">

      {/* ── Header ── */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600">
            <Music2 size={16} className="text-white" />
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-xs font-semibold uppercase tracking-widest text-slate-400">Encore</span>
            <span className="text-sm font-semibold text-slate-700">Cloud Manager</span>
          </div>
          <div className="ml-6 flex items-center gap-1 rounded-lg border border-slate-200 bg-slate-100 p-1">
            <button
              onClick={() => setMode('cloud')}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium transition ${
                mode === 'cloud' ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              <Cloud size={12} /> Cloud
            </button>
            <button
              onClick={() => setMode('local')}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium transition ${
                mode === 'local' ? 'bg-white text-blue-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              <FolderOpen size={12} /> Local
            </button>
          </div>
          {isBusy && (
            <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-600">
              <RefreshCw size={11} className="mr-1 inline animate-spin" />{status}
            </span>
          )}
        </div>

        <div className="flex items-center gap-3">
          {!authReady ? (
            <span className="text-xs text-slate-400">Checking auth…</span>
          ) : user ? (
            <>
              {!gcsToken && mode === 'cloud' && (
                <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-medium text-amber-700">
                  Session expired — sign in again
                </span>
              )}
              <div className="flex items-center gap-2">
                {user.photoURL ? (
                  <img src={user.photoURL} alt={userLabel(user)}
                    className="h-8 w-8 rounded-full border border-slate-200 object-cover" />
                ) : (
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-xs font-semibold text-blue-700">
                    {getInitials(user.displayName || '', user.email)}
                  </div>
                )}
                <span className="text-sm font-medium text-slate-700">{userLabel(user)}</span>
              </div>
              <button
                onClick={() => { sessionStorage.removeItem('gcs_token'); setGcsToken(null); signOut(auth); }}
                className="flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-500 hover:bg-slate-50"
              >
                <LogOut size={12} /> Sign out
              </button>
            </>
          ) : (
            <div className="flex flex-col items-end gap-1">
              <button
                onClick={handleSignIn}
                className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
              >
                <LogIn size={14} /> Sign in with Google
              </button>
              {authError && <span className="text-xs text-red-500">{authError}</span>}
            </div>
          )}
        </div>
      </header>

      {/* ── Body ── */}
      <div className="flex flex-1 overflow-hidden">

        {/* ── Sidebar ── */}
        <aside className="flex w-[34rem] shrink-0 flex-col border-r border-slate-200 bg-white">
          <div className="shrink-0 space-y-2 border-b border-slate-100 p-3">
            {mode === 'cloud' ? (
              <button
                onClick={refreshCloudLibrary}
                disabled={isBusy || !cloudReady}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-40"
              >
                <RefreshCw size={14} className={isBusy ? 'animate-spin' : ''} />
                {isBusy ? status : 'Refresh library'}
              </button>
            ) : (
              <button
                onClick={libraryHandle ? () => refreshLocalLibrary() : pickLocalLibrary}
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
              >
                <FolderOpen size={14} />
                {libraryHandle ? 'Refresh local' : 'Open local folder'}
              </button>
            )}
            {songs.length > 0 && (
              <div className="flex items-center justify-between px-1">
                <span className="text-xs text-slate-400">{songs.length} songs</span>
                {healthItems.length > 0 && (
                  <button
                    onClick={() => setActiveTab('health')}
                    className="flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 hover:bg-amber-100"
                  >
                    <AlertCircle size={11} /> {healthItems.length} issues
                  </button>
                )}
              </div>
            )}
          </div>

          <div className="shrink-0 border-b border-slate-100 p-3">
            <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
              <Search size={14} className="shrink-0 text-slate-400" />
              <input
                type="text"
                placeholder="Search songs…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="flex-1 bg-transparent text-sm text-slate-700 placeholder:text-slate-400 outline-none"
              />
              {searchQuery && (
                <button onClick={() => setSearchQuery('')} className="text-slate-300 hover:text-slate-500">
                  <X size={13} />
                </button>
              )}
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-2">
            {filteredSongs.length === 0 && songs.length === 0 && !isBusy && (
              <div className="mt-8 px-4 text-center text-sm text-slate-400">
                {mode === 'cloud' && !cloudReady ? 'Sign in to load your library.' : 'No songs found.'}
              </div>
            )}
            {filteredSongs.map((song) => {
              const isSelected = song.path === selectedPath;
              const isUuid     = /^[0-9a-f-]{36}$/i.test(song.metadata.title);
              const issueCount = healthMap[song.path] || 0;
              return (
                <button
                  key={song.path}
                  onClick={() => { setSelectedPath(song.path); setActiveTab('editor'); }}
                  className={`mb-1 w-full rounded-lg px-3 py-2.5 text-left transition ${
                    isSelected ? 'bg-blue-50 ring-1 ring-blue-200' : 'hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className={`font-medium leading-tight ${isUuid ? 'font-mono text-xs text-slate-400' : 'text-sm text-slate-800'}`}>
                      {song.metadata.title || 'Untitled'}
                    </div>
                    {issueCount > 0 && <AlertCircle size={13} className="mt-0.5 shrink-0 text-amber-500" />}
                  </div>
                  {!isUuid && (
                    <div className="mt-1 flex flex-wrap items-center gap-1.5">
                      <span className="text-xs text-slate-500">
                        {song.metadata.artist || <span className="italic text-slate-300">No artist</span>}
                      </span>
                      {song.metadata.display_key && (
                        <span className="rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700">
                          {song.metadata.display_key}
                        </span>
                      )}
                      {song.metadata.bpm && (
                        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500">
                          {song.metadata.bpm} BPM
                        </span>
                      )}
                      {song.metadata.capo && (
                        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500">
                          Capo {song.metadata.capo}
                        </span>
                      )}
                      {song.metadata.is_lead_guitar && (
                        <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-medium text-emerald-700">
                          Lead
                        </span>
                      )}
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        </aside>

        {/* ── Main panel ── */}
        <main className="flex flex-1 flex-col overflow-hidden">

          {/* ── Metadata bar ── */}
          <div className="shrink-0 border-b border-slate-200 bg-white px-5 py-3">
            {selectedSong ? (
              <div className="flex flex-wrap items-end gap-3">
                <MetaField label="Title"  value={draftMeta.title}        onChange={(v) => setDraftMeta((m) => ({ ...m, title: v }))}          width="180px" />
                <MetaField label="Artist" value={draftMeta.artist}       onChange={(v) => setDraftMeta((m) => ({ ...m, artist: v }))}         width="160px" />
                <MetaField label="Key"    value={draftMeta.display_key}  onChange={(v) => setDraftMeta((m) => ({ ...m, display_key: v }))}    width="60px" />
                <MetaField label="BPM"    value={draftMeta.bpm}          onChange={(v) => setDraftMeta((m) => ({ ...m, bpm: v }))}            width="56px" type="number" />
                <MetaField label="Capo"   value={draftMeta.capo}         onChange={(v) => setDraftMeta((m) => ({ ...m, capo: v }))}           width="48px" type="number" />
                <label className="flex cursor-pointer flex-col gap-0.5">
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400">Lead Guitar</span>
                  <button
                    type="button"
                    onClick={() => setDraftMeta((m) => ({ ...m, is_lead_guitar: !m.is_lead_guitar }))}
                    className={`flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs font-medium transition ${
                      draftMeta.is_lead_guitar
                        ? 'border-emerald-300 bg-emerald-50 text-emerald-700'
                        : 'border-slate-200 bg-white text-slate-500'
                    }`}
                  >
                    <Guitar size={12} />
                    {draftMeta.is_lead_guitar ? 'Lead' : 'Rhythm'}
                  </button>
                </label>
                <div className="ml-auto flex items-end">
                  <button
                    onClick={handleSaveSong}
                    disabled={isBusy || (mode === 'cloud' && !cloudReady)}
                    className="flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-40"
                  >
                    <Save size={14} /> Save
                  </button>
                </div>
              </div>
            ) : (
              <p className="py-1 text-sm text-slate-400">Select a song from the library to start editing.</p>
            )}
          </div>

          {/* ── Tab bar ── */}
          <div className="shrink-0 flex items-center justify-between border-b border-slate-200 bg-white px-4">
            <div className="flex">
              {([
                ['editor',  'Song Editor'],
                ['setlist', 'Setlist Architect'],
                ['health',  'Library Health'],
              ] as const).map(([tab, label]) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm font-medium transition ${
                    activeTab === tab
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-slate-500 hover:text-slate-700'
                  }`}
                >
                  {tab === 'health' && healthItems.length > 0 && (
                    <span className="rounded-full bg-amber-100 px-1.5 text-[10px] font-bold text-amber-700">
                      {healthItems.length}
                    </span>
                  )}
                  {label}
                </button>
              ))}
            </div>
            {activeTab === 'setlist' && (
              <button
                onClick={exportSet}
                disabled={setSongIds.length === 0 || (mode === 'cloud' && !cloudReady)}
                className="flex items-center gap-2 rounded-lg bg-orange-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-orange-600 disabled:opacity-40"
              >
                <Upload size={12} /> Export set
              </button>
            )}
          </div>

          {/* ── Song Editor tab ── */}
          {activeTab === 'editor' && (
            <div className="flex flex-1 overflow-hidden">

              {/* Block editor */}
              <div className="flex flex-1 flex-col border-r border-slate-200 overflow-hidden">
                <div className="shrink-0 flex items-center justify-between border-b border-slate-100 bg-slate-50 px-4 py-2">
                  <span className="text-xs font-semibold uppercase tracking-widest text-slate-400">Song Editor</span>
                  <button
                    onClick={handleMarkHarmony}
                    disabled={!selectedSong}
                    title="Select text in a section, then click to wrap with [h]...[/h]"
                    className="flex items-center gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 hover:bg-amber-100 disabled:opacity-40"
                  >
                    <Tag size={11} /> Mark [h] Harmony
                  </button>
                </div>

                {/* Scrollable block editor area */}
                <div className="flex-1 overflow-y-auto px-4 pb-8">
                  {!selectedSong && (
                    <p className="mt-8 text-center text-sm text-slate-400">Select a song to start editing.</p>
                  )}
                  {blocks.map((block, i) => {
                    if (block.type === 'section') {
                      return (
                        <SectionButton
                          key={i}
                          label={block.label}
                          onChangeLabel={(newLabel) => changeSectionLabel(i, newLabel)}
                        />
                      );
                    }
                    // Content block — auto-height textarea
                    return (
                      <textarea
                        key={i}
                        data-block-idx={i}
                        value={block.text}
                        onChange={(e) => updateContentBlock(i, e.target.value)}
                        onFocus={(e) => { activeTextareaRef.current = e.currentTarget; }}
                        spellCheck={false}
                        rows={Math.max(2, block.text.split('\n').length)}
                        className="mb-1 w-full resize-none bg-transparent font-mono text-sm leading-7 text-slate-800 outline-none placeholder:text-slate-300"
                        placeholder={i === 0 && blocks.length <= 1 ? 'Start typing your song…' : ''}
                      />
                    );
                  })}
                </div>
              </div>

              {/* Song Preview */}
              <div className="flex flex-1 flex-col overflow-hidden">
                <div className="shrink-0 border-b border-slate-100 bg-slate-50 px-4 py-2">
                  <span className="text-xs font-semibold uppercase tracking-widest text-slate-400">Song Preview</span>
                </div>
                <div className="flex-1 overflow-y-auto p-6">
                  {selectedSong ? (
                    <>
                      <div className="mb-4 border-b border-slate-100 pb-4">
                        <h2 className="text-lg font-bold text-slate-900">{draftMeta.title || 'Untitled'}</h2>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-slate-500">
                          {draftMeta.artist && <span>{draftMeta.artist}</span>}
                          {draftMeta.display_key && (
                            <span className="font-semibold text-blue-600">Key of {draftMeta.display_key}</span>
                          )}
                          {draftMeta.bpm && (
                            <span className="flex items-center gap-1 text-xs text-slate-400">
                              <Hash size={10} />{draftMeta.bpm} BPM
                            </span>
                          )}
                          {draftMeta.capo && (
                            <span className="text-xs text-slate-400">Capo {draftMeta.capo}</span>
                          )}
                          {draftMeta.is_lead_guitar && (
                            <span className="flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
                              <Guitar size={10} /> Lead Guitar
                            </span>
                          )}
                        </div>
                      </div>
                      <div dangerouslySetInnerHTML={{ __html: renderPreview(draft) }} />
                    </>
                  ) : (
                    <p className="text-sm text-slate-400">Select a song to see preview.</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ── Setlist tab ── */}
          {activeTab === 'setlist' && (
            <div className="flex flex-1 flex-col overflow-hidden p-5">
              <div className="mb-4 flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
                  Set name
                  <input
                    value={setName}
                    onChange={(e) => setSetName(e.target.value)}
                    className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-800 outline-none focus:ring-2 focus:ring-blue-300"
                  />
                </label>
                <span className="text-xs text-slate-400">
                  {activeSetSongs.length} song{activeSetSongs.length !== 1 ? 's' : ''} in set
                </span>
              </div>
              <div className="grid flex-1 grid-cols-2 gap-4 overflow-hidden">
                <div className="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
                  <div className="shrink-0 flex items-center gap-2 border-b border-slate-100 px-4 py-3 text-sm font-semibold text-slate-700">
                    <Music2 size={14} /> All songs
                    <span className="ml-auto text-xs font-normal text-slate-400">{songs.length}</span>
                  </div>
                  <div className="flex-1 overflow-y-auto p-2">
                    {songs.map((song) => {
                      const inSet = setSongIds.includes(song.path);
                      return (
                        <button
                          key={song.path}
                          onClick={() =>
                            setSetSongIds((cur) =>
                              inSet ? cur.filter((id) => id !== song.path) : [...cur, song.path],
                            )
                          }
                          className={`mb-1 w-full rounded-lg px-3 py-2 text-left text-sm transition ${
                            inSet ? 'bg-emerald-50 ring-1 ring-emerald-200' : 'hover:bg-slate-50'
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <span className={`font-medium ${inSet ? 'text-emerald-700' : 'text-slate-800'}`}>
                              {song.metadata.title || 'Untitled'}
                            </span>
                            {song.metadata.display_key && (
                              <span className="rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700">
                                {song.metadata.display_key}
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-slate-400">{song.metadata.artist || 'Unknown artist'}</div>
                        </button>
                      );
                    })}
                  </div>
                </div>
                <div className="flex flex-col overflow-hidden rounded-xl border border-slate-200 bg-white">
                  <div className="shrink-0 flex items-center gap-2 border-b border-slate-100 px-4 py-3 text-sm font-semibold text-slate-700">
                    <FileText size={14} /> {setName || 'Unnamed'}
                    <span className="ml-auto text-xs font-normal text-slate-400">{activeSetSongs.length}</span>
                  </div>
                  <div className="flex-1 overflow-y-auto p-2">
                    {activeSetSongs.length === 0 ? (
                      <p className="mt-6 text-center text-sm text-slate-400">Click songs on the left to add them.</p>
                    ) : (
                      activeSetSongs.map((song, i) => (
                        <div key={song.path} className="mb-1 flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-slate-50">
                          <span className="w-5 shrink-0 text-right text-xs font-bold text-slate-400">{i + 1}</span>
                          <div className="min-w-0 flex-1">
                            <div className="truncate text-sm font-medium text-slate-800">
                              {song.metadata.title || 'Untitled'}
                            </div>
                            <div className="flex items-center gap-2 text-xs text-slate-400">
                              <span>{song.metadata.artist || 'Unknown artist'}</span>
                              {song.metadata.display_key && (
                                <span className="rounded bg-blue-100 px-1 py-0.5 text-[10px] font-semibold text-blue-600">
                                  {song.metadata.display_key}
                                </span>
                              )}
                            </div>
                          </div>
                          <button
                            onClick={() => setSetSongIds((cur) => cur.filter((id) => id !== song.path))}
                            className="shrink-0 rounded p-1 text-slate-300 hover:bg-slate-100 hover:text-slate-500"
                          >
                            <X size={13} />
                          </button>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ── Library Health tab ── */}
          {activeTab === 'health' && (
            <div className="flex-1 overflow-y-auto p-6">
              <div className="mb-5 flex items-center gap-3">
                <h2 className="text-base font-semibold text-slate-800">Library Health</h2>
                <span className="text-sm text-slate-400">{songs.length} songs scanned</span>
                {healthItems.length === 0 ? (
                  <span className="flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700">
                    <CheckCircle2 size={12} /> All clean
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700">
                    <AlertCircle size={12} /> {healthItems.length} songs with issues
                  </span>
                )}
              </div>

              {healthItems.length === 0 ? (
                <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-8 text-center">
                  <CheckCircle2 size={28} className="mx-auto mb-3 text-emerald-500" />
                  <p className="font-medium text-emerald-800">Library looks great!</p>
                  <p className="mt-1 text-sm text-emerald-600">All {songs.length} songs have title, artist, and key.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {healthItems.map(({ song, issues }) => (
                    <div
                      key={song.path}
                      className="flex items-start justify-between gap-4 rounded-xl border border-slate-200 bg-white px-4 py-3 transition hover:border-amber-300 hover:bg-amber-50/30"
                    >
                      <div className="min-w-0 flex-1">
                        <button
                          onClick={() => { setSelectedPath(song.path); setActiveTab('editor'); }}
                          className="text-left"
                        >
                          <div className="text-sm font-semibold text-blue-600 hover:underline">
                            {song.metadata.title || <span className="italic text-slate-400">No title</span>}
                          </div>
                          <div className="text-xs text-slate-400">{song.metadata.artist || 'No artist'}</div>
                        </button>
                      </div>
                      <div className="flex flex-wrap justify-end gap-1.5">
                        {issues.map((issue) => (
                          <span key={issue} className="rounded-full bg-amber-100 px-2.5 py-0.5 text-[11px] font-medium text-amber-800">
                            {issue}
                          </span>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
