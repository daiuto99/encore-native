import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  ChevronDown,
  Cloud,
  FileText,
  FolderOpen,
  GripVertical,
  Guitar,
  Hash,
  LogIn,
  LogOut,
  Moon,
  Music2,
  Maximize2,
  RefreshCw,
  Save,
  Search,
  Sparkles,
  Sun,
  Tag,
  Upload,
  X,
} from 'lucide-react';
import { GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut, type User } from 'firebase/auth';
import { auth, googleProvider } from './lib/firebase';
import { CloudLibraryService } from './services/CloudLibraryService';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import encoreMark from './assets/encore_mark.png';

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

type Block =
  | { type: 'section'; label: string }
  | { type: 'content'; text: string };

type HealthItem = { song: SongRecord; issues: string[] };

type FsDirectoryHandle = FileSystemDirectoryHandle & { values(): AsyncIterable<FileSystemHandle> };
type FsFileHandle = FileSystemFileHandle;

// ── Cover palette ─────────────────────────────────────────────────────────────

const SET_COVER_PALETTE = [
  { bg: '#F87171', fg: '#7F1D1D' },
  { bg: '#FB923C', fg: '#7C2D12' },
  { bg: '#FBBF24', fg: '#78350F' },
  { bg: '#A3E635', fg: '#365314' },
  { bg: '#34D399', fg: '#064E3B' },
  { bg: '#22D3EE', fg: '#164E63' },
  { bg: '#60A5FA', fg: '#1E3A8A' },
  { bg: '#A78BFA', fg: '#4C1D95' },
  { bg: '#F472B6', fg: '#831843' },
  { bg: '#94A3B8', fg: '#0F172A' },
];

function songCoverIndex(path: string): number {
  let h = 0;
  for (const c of path) h = (Math.imul(31, h) + c.charCodeAt(0)) | 0;
  return Math.abs(h) % SET_COVER_PALETTE.length;
}

// ── Transposition utilities ────────────────────────────────────────────────────

const NOTE_TO_SEMITONE: Record<string, number> = {
  C: 0, 'C#': 1, Db: 1,
  D: 2, 'D#': 3, Eb: 3,
  E: 4,
  F: 5, 'F#': 6, Gb: 6,
  G: 7, 'G#': 8, Ab: 8,
  A: 9, 'A#': 10, Bb: 10,
  B: 11,
};
const SHARP_SCALE = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
const FLAT_SCALE  = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B'];
const FLAT_KEYS   = new Set(['F', 'Bb', 'Eb', 'Ab', 'Db', 'Gb', 'Dm', 'Gm', 'Cm', 'Fm', 'Bbm', 'Ebm']);

function parseKeyRoot(key: string): string | null {
  const t = key.trim();
  if (!t) return null;
  const base = t[0].toUpperCase();
  if (base < 'A' || base > 'G') return null;
  if (t.length > 1 && t[1] === '#') return base + '#';
  if (t.length > 1 && t[1] === 'b') return base + 'b';
  return base;
}

function semitoneShift(originalKey: string, displayKey: string): number {
  if (!originalKey || !displayKey) return 0;
  const orig = parseKeyRoot(originalKey);
  const disp = parseKeyRoot(displayKey);
  if (!orig || !disp) return 0;
  const o = NOTE_TO_SEMITONE[orig];
  const d = NOTE_TO_SEMITONE[disp];
  if (o === undefined || d === undefined) return 0;
  return ((d - o) + 12) % 12;
}

function useFlatSpelling(key: string): boolean {
  const root = parseKeyRoot(key);
  if (!root) return false;
  return FLAT_KEYS.has(root) || FLAT_KEYS.has(key.trim());
}

function extractRoot(chord: string): string | null {
  if (!chord) return null;
  const base = chord[0].toUpperCase();
  if (base < 'A' || base > 'G') return null;
  if (chord.length > 1 && chord[1] === '#') return base + '#';
  if (chord.length > 1 && chord[1] === 'b' && (chord.length < 3 || chord[2] === chord[2].toUpperCase() || !/[a-z]/.test(chord[2]))) return base + 'b';
  return base;
}

function transposeChordToken(chord: string, semitones: number, useFlats: boolean): string {
  const slashIdx = chord.indexOf('/');
  if (slashIdx > 0) {
    const root = chord.slice(0, slashIdx);
    const bass = chord.slice(slashIdx + 1);
    return transposeChordToken(root, semitones, useFlats) + '/' + transposeChordToken(bass, semitones, useFlats);
  }
  const root = extractRoot(chord);
  if (!root) return chord;
  const suffix = chord.slice(root.length);
  const origSemi = NOTE_TO_SEMITONE[root];
  if (origSemi === undefined) return chord;
  const newSemi = ((origSemi + semitones) + 12) % 12;
  const scale = useFlats ? FLAT_SCALE : SHARP_SCALE;
  return scale[newSemi] + suffix;
}

const CHORD_TOKEN_RE = /[A-G][#b]?(?:m(?:aj)?|maj|min|aug|dim|sus|add)?[0-9]*(?:\/[A-G][#b]?)?/g;

function transposeInlineChordLine(line: string, semitones: number, useFlats: boolean): string {
  return line.replace(/`\[([^\]]+)\]`/g, (_match, chord) => {
    const transposed = chord.replace(CHORD_TOKEN_RE, (c: string) => transposeChordToken(c, semitones, useFlats));
    return '`[' + transposed + ']`';
  });
}

function transposeLegacyChordLine(line: string, semitones: number, useFlats: boolean): string {
  return line.replace(CHORD_TOKEN_RE, (c) => transposeChordToken(c, semitones, useFlats));
}

// ── Constants ─────────────────────────────────────────────────────────────────

const SECTION_PRESETS = [
  'Intro', 'Verse 1', 'Verse 2', 'Verse 3', 'Verse 4',
  'Pre-Chorus', 'Chorus', 'Bridge', 'Outro', 'Tag',
  'Solo', 'Instrumental', 'Breakdown', 'Interlude', 'Vamp', 'Coda', 'Hook',
];

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

function stripLeadingTitle(body: string, title: string): string {
  if (!title.trim()) return body;
  const lines = body.split('\n');
  const firstNonBlank = lines.findIndex((l) => l.trim().length > 0);
  if (firstNonBlank === -1) return body;
  const raw = lines[firstNonBlank].trim();
  let candidate: string;
  if (raw.startsWith('[') && raw.endsWith(']')) {
    candidate = raw.slice(1, -1).trim();
  } else {
    candidate = raw.replace(/^#+\s*/, '').trim();
  }
  const normalizedCandidate = candidate.replace(/\s*\([^)]*\)/g, '').trim();
  if (normalizedCandidate.toLowerCase() === title.trim().toLowerCase()) {
    const result = [...lines];
    result.splice(firstNonBlank, 1);
    return result.join('\n');
  }
  return body;
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
  if (contentLines.length > 0) blocks.push({ type: 'content', text: contentLines.join('\n') });
  return blocks;
}

function blocksToString(blocks: Block[]): string {
  return blocks.map((b) => (b.type === 'section' ? `[${b.label}]` : b.text)).join('\n');
}

// ── Renderers (theme-aware) ───────────────────────────────────────────────────

function renderPreview(body: string, dark: boolean): string {
  const normalized = normalizeMarkdownBody(body);
  const sectionColor = dark ? 'rgba(235,235,245,0.45)' : 'rgba(60,60,67,0.45)';
  const chordColor   = dark ? '#60A5FA' : '#2563EB';
  const lyricColor   = dark ? 'rgba(235,235,245,0.9)' : '#1C1C1E';
  const markStyle    = dark
    ? 'background:rgba(245,158,11,0.15);color:#FCD34D;border-radius:3px;padding:0 3px'
    : 'background:rgba(245,158,11,0.18);color:#92400e;border-radius:3px;padding:0 3px';

  let inHarmony = false;
  return normalizeMarkdownBody(body).split('\n').map((line) => {
    let text = line;
    text = text.replace(/\[h\](.*?)\[\/h\]/g, `<mark style="${markStyle}">$1</mark>`);
    const hasOpen = text.includes('[h]'), hasClose = text.includes('[/h]');
    const startsH = hasOpen && !hasClose, endsH = !hasOpen && hasClose;
    if (startsH) text = text.replace('[h]', '');
    if (endsH)   text = text.replace('[/h]', '');
    const highlighted = inHarmony || startsH || endsH;
    if (startsH) inHarmony = true;
    if (endsH)   inHarmony = false;

    const trimmed = text.trim();
    if (!trimmed) return '<div style="height:12px"></div>';
    if (/^\[[^\]]+\]$/.test(trimmed)) {
      return `<div style="margin-top:20px;margin-bottom:6px;font-size:11px;font-weight:700;letter-spacing:.1em;text-transform:uppercase;color:${sectionColor}">${trimmed.slice(1, -1)}</div>`;
    }
    const isChord = /^[A-G][#b]?[^\s]*(\s+[A-G][#b]?[^\s]*)*\s*$/.test(trimmed) && !/[a-z]{3}/.test(trimmed);
    const base = isChord
      ? `font-family:monospace;font-size:13px;font-weight:600;color:${chordColor};letter-spacing:.05em;line-height:1.8`
      : `line-height:1.8;font-size:15px;color:${lyricColor}`;
    const content = highlighted ? `<mark style="${markStyle}">${trimmed}</mark>` : trimmed;
    return `<div style="${base}">${content}</div>`;
  }).join('');
}

const PERF_SECTION_COLORS: Record<string, string> = {
  intro: '#3B82F6', verse: '#F97316', chorus: '#EF4444', bridge: '#8B5CF6',
  outro: '#F59E0B', solo: '#10B981', interlude: '#06B6D4', instrumental: '#EC4899',
  'pre-chorus': '#F97316', prechorus: '#F97316', tag: '#F59E0B',
  hook: '#EF4444', coda: '#F59E0B', breakdown: '#8B5CF6', vamp: '#06B6D4',
};

function getPerfSectionColor(label: string): string {
  const n = label.toLowerCase().trim();
  for (const [key, color] of Object.entries(PERF_SECTION_COLORS)) {
    if (n.includes(key)) return color;
  }
  return '#94a3b8';
}

function renderPerformance(body: string, semitones = 0, displayKey = ''): string {
  const useFlats = semitones !== 0 ? useFlatSpelling(displayKey) : false;
  return normalizeMarkdownBody(body).split('\n').map((line) => {
    const trimmed = line.trim();
    if (!trimmed) return '<div style="height:10px"></div>';
    const bracketM = trimmed.match(/^\[([^\]]+)\]$/);
    const mdM      = trimmed.match(/^#{1,2}\s+(.+)$/);
    const spanM    = trimmed.match(/<span[^>]*>##?\s*(.*?)<\/span>/i);
    const label    = bracketM?.[1] ?? mdM?.[1] ?? spanM?.[1] ?? null;
    if (label) {
      const color = getPerfSectionColor(label);
      return `<div style="margin-top:28px;margin-bottom:10px;font-size:12px;font-weight:800;letter-spacing:.14em;text-transform:uppercase;color:${color};border-left:3px solid ${color};padding-left:10px">${label}</div>`;
    }
    if (trimmed.includes('`[')) {
      const t = semitones !== 0 ? transposeInlineChordLine(trimmed, semitones, useFlats) : trimmed;
      const r = t.replace(/`\[([^\]]+)\]`/g, '<span style="color:#FFD60A;font-weight:700;font-size:15px;font-family:monospace">$1 </span>');
      return `<div style="font-size:20px;line-height:2;color:#e2e8f0;font-family:monospace">${r}</div>`;
    }
    const isChordLine = /^[A-G][#b]?[^\s]*(\s+[A-G][#b]?[^\s]*)*\s*$/.test(trimmed) && !/[a-z]{3}/.test(trimmed);
    if (isChordLine) {
      const t = semitones !== 0 ? transposeLegacyChordLine(trimmed, semitones, useFlats) : trimmed;
      return `<div style="font-family:monospace;font-size:15px;font-weight:700;color:#FFD60A;letter-spacing:.06em;line-height:1.6">${t}</div>`;
    }
    return `<div style="font-size:20px;line-height:1.9;color:#e2e8f0;font-family:monospace">${trimmed}</div>`;
  }).join('');
}

// ── Health check ──────────────────────────────────────────────────────────────

function checkSongHealth(song: SongRecord): string[] {
  const issues: string[] = [];
  const body = song.body;
  const isUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(song.metadata.title);
  if (!song.metadata.title || isUuid) issues.push('Missing title');
  if (!song.metadata.artist || song.metadata.artist.toLowerCase() === 'unknown artist') issues.push('Missing artist');
  if (!song.metadata.display_key) issues.push('Missing key');
  const opens  = (body.match(/\[h\]/gi)  || []).length;
  const closes = (body.match(/\[\/h\]/gi) || []).length;
  if (opens !== closes) issues.push(`Unclosed [h] tags (${opens} open / ${closes} close)`);
  const spanH    = Array.from(body.matchAll(/<span[^>]*>##?\s*(.*?)<\/span>/gi)).map((m) => m[1].trim());
  const mdH      = Array.from(body.matchAll(/^#{1,2}\s+(.+)$/gm)).map((m) => m[1].trim());
  const bracketH = Array.from(body.matchAll(/^\[([A-Za-z][^\]]*)\]$/gm)).map((m) => m[1].trim());
  [...spanH, ...mdH, ...bracketH].forEach((raw) => {
    const n = raw.toLowerCase().replace(/[\s\d]+$/, '').trim();
    if (![...KNOWN_SECTIONS].some((s) => n === s || n.startsWith(s)) && raw) {
      issues.push(`Non-standard section: "${raw}"`);
    }
  });
  return issues;
}

const EMPTY_META: SongMeta = {
  title: '', artist: '', display_key: '', original_key: '',
  is_lead_guitar: false, bpm: '', capo: '',
};

// ── ChordSidekick ─────────────────────────────────────────────────────────────

const CHORDSIDEKICK_SYSTEM = `You are ChordSidekick, a guitar-savvy assistant specialized in producing chord sheets for the Encore tablet performance app.

Your job: given a song title, artist, key, and optional capo or existing lyrics, produce a clean, complete chord sheet in Encore format.

ENCORE FORMAT RULES (mandatory):
- Start with YAML front matter: ---\\ntitle: ...\\nartist: ...\\ndisplay_key: ...\\noriginal_key: ...\\nis_lead_guitar: false\\n---
- After the front matter, body begins immediately — NO title line in the body
- Section headers: plain markdown only — ## Intro, ## Verse 1, ## Chorus, ## Bridge, ## Outro, ## Solo, etc. NO <span> HTML tags
- Chords: inline backtick notation placed immediately before the syllable where the change falls, same line as the lyric: \`[G]\` She loves to laugh  \`[D/F#]\` She loves to sing
- Combine short lyric fragments onto one line (~80 chars per line = one musical phrase)
- No blank lines between chord-lyric lines within a section
- Separate sections with one blank line
- If the user provides raw lyrics with chords above the lyrics, convert to inline format

CHORD PLACEMENT:
- Place \`[Chord]\` immediately before the syllable where the chord change occurs
- For instrumental sections: \`[G]\` / \`[Em]\` / \`[C]\` / \`[D]\`
- For repeated sections, write them out fully — no "repeat Chorus" shortcuts

OUTPUT: Produce only the markdown content. No commentary, no explanation, no markdown code fences.`;

const ALL_KEYS = ['C','C#','Db','D','D#','Eb','E','F','F#','Gb','G','G#','Ab','A','A#','Bb','B'];

async function callChordSidekick(userMessage: string): Promise<string> {
  const apiKey = import.meta.env.VITE_ANTHROPIC_API_KEY as string;
  if (!apiKey) throw new Error('Anthropic API key not configured.');
  const res = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'x-api-key': apiKey,
      'anthropic-version': '2023-06-01',
      'content-type': 'application/json',
      'anthropic-dangerous-direct-browser-access': 'true',
    },
    body: JSON.stringify({
      model: 'claude-sonnet-4-6',
      max_tokens: 4096,
      system: CHORDSIDEKICK_SYSTEM,
      messages: [{ role: 'user', content: userMessage }],
    }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error((err as { error?: { message?: string } }).error?.message ?? `API error ${res.status}`);
  }
  const data = await res.json() as { content: Array<{ type: string; text: string }> };
  return data.content.find((b) => b.type === 'text')?.text ?? '';
}

// ── Sub-components ────────────────────────────────────────────────────────────

function SongCoverTile({ path, title }: { path: string; title: string }) {
  const { bg, fg } = SET_COVER_PALETTE[songCoverIndex(path)];
  const glyph = title.split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('');
  return (
    <div
      className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-[11px] font-extrabold"
      style={{ background: bg, color: fg }}
    >
      {glyph || '♪'}
    </div>
  );
}

function MetaField({
  label, value, onChange, width = 'auto', type = 'text',
}: {
  label: string; value: string; onChange: (v: string) => void; width?: string; type?: string;
}) {
  return (
    <label className="flex flex-col gap-0.5">
      <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400 dark:text-white/40">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{ width }}
        className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-800 outline-none focus:border-blue-400 dark:border-white/10 dark:bg-white/5 dark:text-white dark:focus:border-blue-500/60"
      />
    </label>
  );
}

function SectionButton({ label, onChangeLabel }: { label: string; onChangeLabel: (l: string) => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const h = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, [open]);
  return (
    <div ref={ref} className="relative my-3 flex items-center">
      <button
        onClick={() => setOpen((o) => !o)}
        className={`flex items-center gap-1.5 rounded-lg border px-3 py-1 text-xs font-bold uppercase tracking-widest transition ${
          open
            ? 'border-blue-400 bg-blue-50 text-blue-700 dark:border-blue-500/50 dark:bg-blue-500/10 dark:text-blue-400'
            : 'border-slate-200 bg-slate-50 text-slate-500 hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700 dark:border-white/10 dark:bg-white/5 dark:text-white/50 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400'
        }`}
      >
        {label}
        <ChevronDown size={11} className={`transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="absolute left-0 top-full z-20 mt-1 max-h-64 w-44 overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-xl dark:border-white/10 dark:bg-[#2C2C2E] dark:shadow-2xl">
          <div className="p-1">
            {SECTION_PRESETS.map((preset) => (
              <button
                key={preset}
                onClick={() => { onChangeLabel(preset); setOpen(false); }}
                className={`w-full rounded-lg px-3 py-1.5 text-left text-sm transition hover:bg-slate-50 dark:hover:bg-white/5 ${
                  preset === label ? 'font-semibold text-blue-600 dark:text-blue-400' : 'text-slate-700 dark:text-white/70'
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

function SortableSongItem({ id, position, title, artist, displayKey, onRemove }: {
  id: string; position: number; title: string; artist: string; displayKey: string; onRemove: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style = { transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 };
  return (
    <div
      ref={setNodeRef}
      style={style}
      className="mb-1 flex items-center gap-2 rounded-xl border border-slate-100 bg-white px-3 py-2 hover:bg-slate-50 dark:border-white/[0.06] dark:bg-white/[0.03] dark:hover:bg-white/[0.06]"
    >
      <button {...attributes} {...listeners}
        className="shrink-0 cursor-grab touch-none rounded p-1 text-slate-300 hover:text-slate-500 active:cursor-grabbing dark:text-white/20 dark:hover:text-white/50">
        <GripVertical size={14} />
      </button>
      <span className="w-5 shrink-0 text-right text-xs font-bold text-slate-400 dark:text-white/30">{position}</span>
      <SongCoverTile path={id} title={title} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium text-slate-800 dark:text-white">{title || 'Untitled'}</div>
        <div className="flex items-center gap-2 text-xs text-slate-400 dark:text-white/40">
          <span>{artist || 'Unknown artist'}</span>
          {displayKey && (
            <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-500/20 dark:text-amber-400">
              {displayKey}
            </span>
          )}
        </div>
      </div>
      <button onClick={onRemove}
        className="shrink-0 rounded-lg p-1.5 text-slate-300 hover:bg-slate-100 hover:text-slate-500 dark:text-white/20 dark:hover:bg-white/10 dark:hover:text-white/60">
        <X size={13} />
      </button>
    </div>
  );
}

// ── App ────────────────────────────────────────────────────────────────────────

export default function App() {
  const [isDark, setIsDark]             = useState(false);
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
  const [sortBy, setSortBy]             = useState<'title' | 'artist' | 'key'>('title');
  const [showPerfView, setShowPerfView] = useState(false);
  const [showCreatePanel, setShowCreatePanel] = useState(false);
  const [createTitle, setCreateTitle]   = useState('');
  const [createArtist, setCreateArtist] = useState('');
  const [createKey, setCreateKey]       = useState('G');
  const [createCapo, setCreateCapo]     = useState('');
  const [createNotes, setCreateNotes]   = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [setName, setSetName]           = useState('Friday Night');
  const [setSongIds, setSetSongIds]     = useState<string[]>([]);

  const dndSensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  function handleSetDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (over && active.id !== over.id) {
      setSetSongIds((ids) => arrayMove(ids, ids.indexOf(active.id as string), ids.indexOf(over.id as string)));
    }
  }

  const [libraryHandle, setLibraryHandle] = useState<FsDirectoryHandle | null>(null);
  const [fileHandles, setFileHandles]     = useState<Record<string, FsFileHandle>>({});
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

  const selectedSong = useMemo(() => songs.find((s) => s.path === selectedPath) || null, [songs, selectedPath]);

  const filteredSongs = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const base = !q ? songs : songs.filter((s) =>
      s.metadata.title.toLowerCase().includes(q) ||
      s.metadata.artist.toLowerCase().includes(q) ||
      s.metadata.display_key.toLowerCase().includes(q),
    );
    return [...base].sort((a, b) => {
      if (sortBy === 'artist') {
        const cmp = a.metadata.artist.toLowerCase().localeCompare(b.metadata.artist.toLowerCase());
        return cmp !== 0 ? cmp : a.metadata.title.toLowerCase().localeCompare(b.metadata.title.toLowerCase());
      }
      if (sortBy === 'key') {
        const cmp = a.metadata.display_key.toLowerCase().localeCompare(b.metadata.display_key.toLowerCase());
        return cmp !== 0 ? cmp : a.metadata.title.toLowerCase().localeCompare(b.metadata.title.toLowerCase());
      }
      return a.metadata.title.toLowerCase().localeCompare(b.metadata.title.toLowerCase());
    });
  }, [songs, searchQuery, sortBy]);

  const activeSetSongs = useMemo(
    () => setSongIds.map((p) => songs.find((s) => s.path === p)).filter((s): s is SongRecord => Boolean(s)),
    [setSongIds, songs],
  );

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
  const needsReconnect = authReady && Boolean(user) && !gcsToken && mode === 'cloud';

  // ── Sync draft/meta ───────────────────────────────────────────────────────

  useEffect(() => {
    if (selectedSong) {
      setDraftMeta(selectedSong.metadata);
      setDraft(stripLeadingTitle(normalizeMarkdownBody(selectedSong.body), selectedSong.metadata.title));
    } else {
      setDraftMeta(EMPTY_META);
      setDraft('');
    }
  }, [selectedSong?.path]);

  // ── Block helpers ─────────────────────────────────────────────────────────

  function updateContentBlock(blockIdx: number, newText: string) {
    setDraft(blocksToString(blocks.map((b, i) => i === blockIdx && b.type === 'content' ? { ...b, text: newText } : b)));
  }

  function changeSectionLabel(blockIdx: number, newLabel: string) {
    setDraft(blocksToString(blocks.map((b, i) => i === blockIdx && b.type === 'section' ? { ...b, label: newLabel } : b)));
  }

  function handleMarkHarmony() {
    const ta = activeTextareaRef.current;
    if (!ta) return;
    const { selectionStart: start, selectionEnd: end } = ta;
    if (start === end) return;
    const blockIdx = Number(ta.dataset['blockIdx'] ?? -1);
    if (blockIdx < 0) return;
    const block = blocks[blockIdx];
    if (!block || block.type !== 'content') return;
    const newText = block.text.slice(0, start) + '[h]' + block.text.slice(start, end) + '[/h]' + block.text.slice(end);
    updateContentBlock(blockIdx, newText);
    requestAnimationFrame(() => { if (!ta) return; ta.focus(); ta.selectionStart = start + 3; ta.selectionEnd = end + 3; });
  }

  // ── Actions ───────────────────────────────────────────────────────────────

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
        loaded.push(parseSong(items[i].path, await CloudLibraryService.downloadMarkdownFile(items[i].path, gcsToken)));
      }
      loaded.sort((a, b) => a.metadata.title.toLowerCase().localeCompare(b.metadata.title.toLowerCase()));
      setSongs(loaded);
      setSelectedPath((cur) => cur || loaded[0]?.path || '');
      setStatus(`${loaded.length} songs loaded.`);
    } catch (error) {
      setSongs([]);
      setSelectedPath('');
      const msg = error instanceof Error ? error.message : 'Unable to load songs.';
      setStatus(msg);
      if (msg.includes('expired') || msg.includes('401')) { sessionStorage.removeItem('gcs_token'); setGcsToken(null); }
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
        loaded.push(parseSong(item.path, await (await item.handle.getFile()).text()));
      }
      loaded.sort((a, b) => a.metadata.title.toLowerCase().localeCompare(b.metadata.title.toLowerCase()));
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

  async function handleCreateSong() {
    if (!createTitle.trim() || !createArtist.trim()) return;
    setIsGenerating(true);
    setStatus('ChordSidekick is writing your chart…');
    try {
      const userMsg = [`Title: ${createTitle.trim()}`, `Artist: ${createArtist.trim()}`, `Key: ${createKey}`,
        createCapo ? `Capo: ${createCapo}` : '', createNotes.trim() ? `\nAdditional notes / existing lyrics:\n${createNotes.trim()}` : '']
        .filter(Boolean).join('\n');
      const generated = await callChordSidekick(userMsg);
      const uid = crypto.randomUUID();
      const newPath = mode === 'cloud' && user
        ? `${user.email}/songs/${uid}.md`
        : `${createTitle.trim()} - ${createArtist.trim()}`.replace(/[/:*?"<>|]+/g, '-') + '.md';
      const newRecord = parseSong(newPath, generated);
      setSongs((cur) => [newRecord, ...cur]);
      setSelectedPath(newPath);
      setActiveTab('editor');
      setShowCreatePanel(false);
      setCreateTitle(''); setCreateArtist(''); setCreateKey('G'); setCreateCapo(''); setCreateNotes('');
      setStatus(`"${newRecord.metadata.title}" created — review and Save to keep it.`);
    } catch (err) {
      setStatus(err instanceof Error ? err.message : 'Generation failed.');
    } finally {
      setIsGenerating(false);
    }
  }

  async function exportSet() {
    const setData: SetSong[] = activeSetSongs.map((s) => ({
      title: s.metadata.title || inferTitleArtistFromPath(s.path).title,
      artist: s.metadata.artist || inferTitleArtistFromPath(s.path).artist || 'Unknown Artist',
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
    <>
    <div className={`flex h-screen flex-col overflow-hidden bg-[#F2F2F7] text-[#1C1C1E] dark:bg-black dark:text-white${isDark ? ' dark' : ''}`}>

      {/* ── Header ── */}
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-black/[0.08] bg-white/80 px-6 backdrop-blur dark:border-white/[0.08] dark:bg-black">
        <div className="flex items-center gap-4">

          {/* Brand */}
          <div className="flex items-center gap-2.5">
            <div className="flex h-8 w-8 items-center justify-center overflow-hidden rounded-xl bg-white shadow-sm">
              <img src={encoreMark} alt="Encore" className="h-7 w-7 object-contain" />
            </div>
            <div className="flex items-baseline gap-1.5">
              <span className="text-base font-bold tracking-tight text-[#1C1C1E] dark:text-white">Encore</span>
              <span className="text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">Cloud Manager</span>
            </div>
          </div>

          {/* Mode toggle */}
          <div className="flex items-center gap-0.5 rounded-full border border-black/[0.08] bg-black/[0.04] p-1 dark:border-white/10 dark:bg-white/5">
            {(['cloud', 'local'] as const).map((m) => (
              <button key={m} onClick={() => setMode(m)}
                className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition ${
                  mode === m
                    ? 'bg-white text-[#1C1C1E] shadow-sm dark:bg-white/10 dark:text-white'
                    : 'text-[#3C3C43]/50 hover:text-[#1C1C1E] dark:text-white/40 dark:hover:text-white/70'
                }`}
              >
                {m === 'cloud' ? <Cloud size={11} /> : <FolderOpen size={11} />}
                {m.charAt(0).toUpperCase() + m.slice(1)}
              </button>
            ))}
          </div>

          {isBusy && (
            <span className="flex items-center gap-1.5 rounded-full bg-blue-500/10 px-3 py-1 text-xs font-medium text-blue-600 dark:text-blue-400">
              <RefreshCw size={11} className="animate-spin" />{status}
            </span>
          )}
        </div>

        <div className="flex items-center gap-3">
          {/* Dark/light toggle */}
          <button
            onClick={() => setIsDark((d) => !d)}
            className="flex h-8 w-8 items-center justify-center rounded-full border border-black/[0.08] bg-black/[0.04] text-[#3C3C43]/60 transition hover:bg-black/[0.08] hover:text-[#1C1C1E] dark:border-white/10 dark:bg-white/5 dark:text-white/40 dark:hover:bg-white/10 dark:hover:text-white"
            title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
          >
            {isDark ? <Sun size={14} /> : <Moon size={14} />}
          </button>

          {/* Auth */}
          {!authReady ? (
            <span className="text-xs text-[#3C3C43]/40 dark:text-white/30">Checking auth…</span>
          ) : user ? (
            <div className="flex items-center gap-2.5">
              {user.photoURL ? (
                <img src={user.photoURL} alt={userLabel(user)}
                  className="h-8 w-8 rounded-full border border-black/10 object-cover dark:border-white/10" />
              ) : (
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-blue-100 text-xs font-semibold text-blue-700 dark:bg-blue-500/20 dark:text-blue-400">
                  {getInitials(user.displayName || '', user.email)}
                </div>
              )}
              <span className="max-w-[140px] truncate text-sm font-medium text-[#1C1C1E]/80 dark:text-white/80">
                {userLabel(user)}
              </span>
              {/* Reconnect when GCS token is gone — no scary warning, just a clean button */}
              {needsReconnect && (
                <button
                  onClick={handleSignIn}
                  className="flex items-center gap-1.5 rounded-lg bg-blue-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-400 transition"
                >
                  <RefreshCw size={11} /> Reconnect
                </button>
              )}
              <button
                onClick={() => { sessionStorage.removeItem('gcs_token'); setGcsToken(null); signOut(auth); }}
                className="flex items-center gap-1.5 rounded-lg border border-black/10 px-3 py-1.5 text-xs font-medium text-[#3C3C43]/60 transition hover:border-black/20 hover:text-[#1C1C1E] dark:border-white/10 dark:text-white/50 dark:hover:border-white/20 dark:hover:text-white/80"
              >
                <LogOut size={12} /> Sign out
              </button>
            </div>
          ) : (
            <div className="flex flex-col items-end gap-1">
              <button
                onClick={handleSignIn}
                className="flex items-center gap-2 rounded-lg bg-blue-500 px-4 py-1.5 text-sm font-semibold text-white hover:bg-blue-400 transition"
              >
                <LogIn size={14} /> Sign in with Google
              </button>
              {authError && <span className="text-xs text-red-500 dark:text-red-400">{authError}</span>}
            </div>
          )}
        </div>
      </header>

      {/* ── Body ── */}
      <div className="flex flex-1 overflow-hidden">

        {/* ── Sidebar ── */}
        <aside className="flex w-80 shrink-0 flex-col border-r border-black/[0.08] bg-white dark:border-white/[0.08] dark:bg-[#1C1C1E]">

          <div className="shrink-0 space-y-2 border-b border-black/[0.06] p-3 dark:border-white/[0.06]">
            {mode === 'cloud' ? (
              <button
                onClick={refreshCloudLibrary}
                disabled={isBusy || !cloudReady}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-500 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-400 disabled:opacity-30 transition"
              >
                <RefreshCw size={14} className={isBusy ? 'animate-spin' : ''} />
                {isBusy ? status : 'Refresh library'}
              </button>
            ) : (
              <button
                onClick={libraryHandle ? () => refreshLocalLibrary() : pickLocalLibrary}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-blue-500 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-400 transition"
              >
                <FolderOpen size={14} />
                {libraryHandle ? 'Refresh local' : 'Open local folder'}
              </button>
            )}
            {songs.length > 0 && (
              <div className="flex items-center justify-between px-1">
                <span className="text-xs text-[#3C3C43]/45 dark:text-white/30">{songs.length} songs</span>
                {healthItems.length > 0 && (
                  <button onClick={() => setActiveTab('health')}
                    className="flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 hover:bg-amber-100 dark:bg-amber-500/10 dark:text-amber-400 dark:hover:bg-amber-500/20 transition">
                    <AlertCircle size={11} /> {healthItems.length} issues
                  </button>
                )}
              </div>
            )}
          </div>

          <div className="shrink-0 border-b border-black/[0.06] p-3 dark:border-white/[0.06]">
            <div className="flex items-center gap-2">
              <div className="flex flex-1 items-center gap-2 rounded-xl border border-black/[0.06] bg-black/[0.04] px-3 py-2 dark:border-white/[0.06] dark:bg-white/[0.06]">
                <Search size={13} className="shrink-0 text-[#3C3C43]/40 dark:text-white/30" />
                <input
                  type="text"
                  placeholder="Search songs…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="flex-1 bg-transparent text-sm text-[#1C1C1E] placeholder:text-[#3C3C43]/40 outline-none dark:text-white dark:placeholder:text-white/30"
                />
                {searchQuery && (
                  <button onClick={() => setSearchQuery('')} className="text-[#3C3C43]/30 hover:text-[#3C3C43]/60 dark:text-white/20 dark:hover:text-white/50">
                    <X size={12} />
                  </button>
                )}
              </div>
              <button
                onClick={() => setShowCreatePanel((v) => !v)}
                title="Create a new song with AI"
                className={`flex shrink-0 items-center gap-1 rounded-xl border px-2.5 py-2 text-xs font-medium transition ${
                  showCreatePanel
                    ? 'border-purple-400 bg-purple-50 text-purple-700 dark:border-purple-500/40 dark:bg-purple-500/10 dark:text-purple-400'
                    : 'border-black/[0.08] bg-transparent text-[#3C3C43]/50 hover:border-purple-400 hover:text-purple-700 dark:border-white/10 dark:text-white/40 dark:hover:border-purple-500/30 dark:hover:text-purple-400'
                }`}
              >
                <Sparkles size={13} />
              </button>
            </div>

            <div className="mt-2 flex items-center gap-1">
              <span className="mr-1 text-[10px] text-[#3C3C43]/40 dark:text-white/30">Sort:</span>
              {(['title', 'artist', 'key'] as const).map((opt) => (
                <button key={opt} onClick={() => setSortBy(opt)}
                  className={`rounded-full px-2.5 py-0.5 text-[10px] font-medium transition ${
                    sortBy === opt
                      ? 'bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-400'
                      : 'text-[#3C3C43]/45 hover:text-[#1C1C1E] dark:text-white/30 dark:hover:text-white/60'
                  }`}>
                  {opt.charAt(0).toUpperCase() + opt.slice(1)}
                </button>
              ))}
            </div>

            {showCreatePanel && (
              <div className="mt-3 rounded-xl border border-purple-200 bg-purple-50 p-3 dark:border-purple-500/20 dark:bg-purple-500/5">
                <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-purple-700 dark:text-purple-400">
                  <Sparkles size={11} /> ChordSidekick — Create Song
                </p>
                <div className="space-y-2">
                  {[
                    { ph: 'Song title *', val: createTitle, set: setCreateTitle },
                    { ph: 'Artist *',     val: createArtist, set: setCreateArtist },
                  ].map(({ ph, val, set }) => (
                    <input key={ph} placeholder={ph} value={val} onChange={(e) => set(e.target.value)}
                      className="w-full rounded-lg border border-purple-200 bg-white px-2.5 py-1.5 text-sm text-slate-800 outline-none focus:ring-2 focus:ring-purple-300 placeholder:text-slate-400 dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-white/30 dark:focus:ring-purple-500/30" />
                  ))}
                  <div className="flex gap-2">
                    <select value={createKey} onChange={(e) => setCreateKey(e.target.value)}
                      className="flex-1 rounded-lg border border-purple-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none dark:border-white/10 dark:bg-[#2C2C2E] dark:text-white">
                      {ALL_KEYS.map((k) => <option key={k} value={k}>{k}</option>)}
                    </select>
                    <input placeholder="Capo" type="number" min={1} max={12} value={createCapo} onChange={(e) => setCreateCapo(e.target.value)}
                      className="w-16 rounded-lg border border-purple-200 bg-white px-2 py-1.5 text-sm text-slate-800 outline-none placeholder:text-slate-400 dark:border-white/10 dark:bg-white/5 dark:text-white dark:placeholder:text-white/30" />
                  </div>
                  <textarea placeholder="Paste existing chords/lyrics, or leave blank for AI to generate…"
                    value={createNotes} onChange={(e) => setCreateNotes(e.target.value)} rows={3}
                    className="w-full resize-none rounded-lg border border-purple-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 outline-none focus:ring-2 focus:ring-purple-300 placeholder:text-slate-400 dark:border-white/10 dark:bg-white/5 dark:text-white/80 dark:placeholder:text-white/30 dark:focus:ring-purple-500/30" />
                  <button onClick={handleCreateSong} disabled={isGenerating || !createTitle.trim() || !createArtist.trim()}
                    className="flex w-full items-center justify-center gap-2 rounded-lg bg-purple-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-purple-500 disabled:opacity-40 transition">
                    {isGenerating ? <><RefreshCw size={13} className="animate-spin" /> Generating…</> : <><Sparkles size={13} /> Generate Chart</>}
                  </button>
                </div>
              </div>
            )}
          </div>

          <div className="flex-1 overflow-y-auto p-2">
            {filteredSongs.length === 0 && songs.length === 0 && !isBusy && (
              <div className="mt-8 px-4 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">
                {mode === 'cloud' && !cloudReady ? 'Sign in to load your library.' : 'No songs found.'}
              </div>
            )}
            {filteredSongs.map((song) => {
              const isSelected = song.path === selectedPath;
              const isUuid     = /^[0-9a-f-]{36}$/i.test(song.metadata.title);
              const issueCount = healthMap[song.path] || 0;
              return (
                <button key={song.path}
                  onClick={() => { setSelectedPath(song.path); setActiveTab('editor'); }}
                  className={`mb-0.5 w-full rounded-xl px-2.5 py-2 text-left transition ${
                    isSelected
                      ? 'bg-blue-50 ring-1 ring-blue-300/50 dark:bg-blue-500/10 dark:ring-blue-500/30'
                      : 'hover:bg-black/[0.03] dark:hover:bg-white/[0.04]'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <SongCoverTile path={song.path} title={song.metadata.title} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-start justify-between gap-1">
                        <span className={`truncate text-sm font-medium leading-tight ${
                          isUuid ? 'font-mono text-xs text-[#3C3C43]/40 dark:text-white/30'
                               : isSelected ? 'text-blue-700 dark:text-white' : 'text-[#1C1C1E] dark:text-white'
                        }`}>
                          {song.metadata.title || 'Untitled'}
                        </span>
                        {issueCount > 0 && <AlertCircle size={12} className="mt-0.5 shrink-0 text-amber-500 dark:text-amber-400" />}
                      </div>
                      {!isUuid && (
                        <div className="mt-0.5 flex flex-wrap items-center gap-1.5">
                          <span className="text-xs text-[#3C3C43]/50 dark:text-white/40">
                            {song.metadata.artist || <span className="italic text-[#3C3C43]/30 dark:text-white/20">No artist</span>}
                          </span>
                          {song.metadata.display_key && (
                            <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                              {song.metadata.display_key}
                            </span>
                          )}
                          {song.metadata.is_lead_guitar && (
                            <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-medium text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400">Lead</span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </button>
              );
            })}
          </div>
        </aside>

        {/* ── Main panel ── */}
        <main className="flex flex-1 flex-col overflow-hidden bg-[#F2F2F7] dark:bg-black">

          {/* Metadata bar */}
          <div className="shrink-0 border-b border-black/[0.08] bg-white px-5 py-3 dark:border-white/[0.08] dark:bg-[#0A0A0B]">
            {selectedSong ? (
              <div className="flex flex-wrap items-end gap-3">
                <MetaField label="Title"  value={draftMeta.title}  onChange={(v) => setDraftMeta((m) => ({ ...m, title: v }))}  width="180px" />
                <MetaField label="Artist" value={draftMeta.artist} onChange={(v) => setDraftMeta((m) => ({ ...m, artist: v }))} width="160px" />
                <label className="flex flex-col gap-0.5">
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400 dark:text-white/40">Key</span>
                  <select value={draftMeta.display_key} onChange={(e) => setDraftMeta((m) => ({ ...m, display_key: e.target.value }))}
                    className="rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-800 outline-none focus:border-blue-400 dark:border-white/10 dark:bg-white/5 dark:text-white">
                    <option value="">—</option>
                    {ALL_KEYS.map((k) => <option key={k} value={k}>{k}</option>)}
                  </select>
                </label>
                <MetaField label="BPM"  value={draftMeta.bpm}  onChange={(v) => setDraftMeta((m) => ({ ...m, bpm: v }))}  width="56px" type="number" />
                <MetaField label="Capo" value={draftMeta.capo} onChange={(v) => setDraftMeta((m) => ({ ...m, capo: v }))} width="48px" type="number" />
                <label className="flex cursor-pointer flex-col gap-0.5">
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400 dark:text-white/40">Lead Guitar</span>
                  <button type="button" onClick={() => setDraftMeta((m) => ({ ...m, is_lead_guitar: !m.is_lead_guitar }))}
                    className={`flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition ${
                      draftMeta.is_lead_guitar
                        ? 'border-emerald-300 bg-emerald-50 text-emerald-700 dark:border-emerald-500/40 dark:bg-emerald-500/10 dark:text-emerald-400'
                        : 'border-slate-200 bg-white text-slate-500 dark:border-white/10 dark:bg-white/5 dark:text-white/40'
                    }`}>
                    <Guitar size={12} />
                    {draftMeta.is_lead_guitar ? 'Lead' : 'Rhythm'}
                  </button>
                </label>
                <div className="ml-auto flex items-end">
                  <button onClick={handleSaveSong} disabled={isBusy || (mode === 'cloud' && !cloudReady)}
                    className="flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-30 transition">
                    <Save size={14} /> Save
                  </button>
                </div>
              </div>
            ) : (
              <p className="py-1 text-sm text-[#3C3C43]/45 dark:text-white/30">Select a song from the library to start editing.</p>
            )}
          </div>

          {/* Tab bar */}
          <div className="shrink-0 flex items-center justify-between border-b border-black/[0.08] bg-white px-4 dark:border-white/[0.08] dark:bg-[#0A0A0B]">
            <div className="flex">
              {([['editor','Song Editor'],['setlist','Setlist Architect'],['health','Library Health']] as const).map(([tab, label]) => (
                <button key={tab} onClick={() => setActiveTab(tab)}
                  className={`flex items-center gap-1.5 border-b-2 px-4 py-2.5 text-sm font-medium transition ${
                    activeTab === tab
                      ? 'border-blue-500 text-blue-600 dark:text-white'
                      : 'border-transparent text-[#3C3C43]/50 hover:text-[#1C1C1E] dark:text-white/40 dark:hover:text-white/70'
                  }`}>
                  {tab === 'health' && healthItems.length > 0 && (
                    <span className="rounded-full bg-amber-100 px-1.5 text-[10px] font-bold text-amber-700 dark:bg-amber-500/20 dark:text-amber-400">
                      {healthItems.length}
                    </span>
                  )}
                  {label}
                </button>
              ))}
            </div>
            {activeTab === 'setlist' && (
              <button onClick={exportSet} disabled={setSongIds.length === 0 || (mode === 'cloud' && !cloudReady)}
                className="flex items-center gap-2 rounded-lg bg-orange-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-orange-400 disabled:opacity-30 transition">
                <Upload size={12} /> Export set
              </button>
            )}
          </div>

          {/* ── Song Editor tab ── */}
          {activeTab === 'editor' && (
            <div className="flex flex-1 overflow-hidden">
              <div className="flex flex-1 flex-col border-r border-black/[0.08] overflow-hidden dark:border-white/[0.08]">
                <div className="shrink-0 flex items-center justify-between border-b border-black/[0.06] bg-[#F9F9FB] px-4 py-2 dark:border-white/[0.06] dark:bg-[#0A0A0B]">
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-[#3C3C43]/40 dark:text-white/30">Song Editor</span>
                  <button onClick={handleMarkHarmony} disabled={!selectedSong}
                    title="Select text in a section, then click to wrap with [h]...[/h]"
                    className="flex items-center gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-medium text-amber-700 hover:bg-amber-100 disabled:opacity-30 transition dark:border-amber-500/30 dark:bg-amber-500/8 dark:text-amber-400 dark:hover:bg-amber-500/15">
                    <Tag size={11} /> Mark [h] Harmony
                  </button>
                </div>
                <div className="flex-1 overflow-y-auto bg-white px-5 pb-8 pt-2 dark:bg-black">
                  {!selectedSong && <p className="mt-8 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">Select a song to start editing.</p>}
                  {blocks.map((block, i) => {
                    if (block.type === 'section') return (
                      <SectionButton key={i} label={block.label} onChangeLabel={(l) => changeSectionLabel(i, l)} />
                    );
                    return (
                      <textarea key={i} data-block-idx={i} value={block.text}
                        onChange={(e) => updateContentBlock(i, e.target.value)}
                        onFocus={(e) => { activeTextareaRef.current = e.currentTarget; }}
                        spellCheck={false}
                        rows={Math.max(2, block.text.split('\n').length)}
                        className="mb-1 w-full resize-none bg-transparent font-mono text-sm leading-7 text-[#1C1C1E] outline-none placeholder:text-[#3C3C43]/30 dark:text-white/80 dark:placeholder:text-white/20 dark:caret-blue-400"
                        placeholder={i === 0 && blocks.length <= 1 ? 'Start typing your song…' : ''}
                      />
                    );
                  })}
                </div>
              </div>

              {/* Preview */}
              <div className="flex flex-1 flex-col overflow-hidden">
                <div className="shrink-0 flex items-center justify-between border-b border-black/[0.06] bg-[#F9F9FB] px-4 py-2 dark:border-white/[0.06] dark:bg-[#0A0A0B]">
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-[#3C3C43]/40 dark:text-white/30">Preview</span>
                  {selectedSong && (
                    <button onClick={() => setShowPerfView(true)}
                      className="flex items-center gap-1.5 rounded-lg border border-black/10 bg-white px-2.5 py-1 text-xs font-medium text-[#3C3C43]/50 hover:border-black/20 hover:text-[#1C1C1E] transition dark:border-white/10 dark:bg-white/5 dark:text-white/50 dark:hover:border-white/20 dark:hover:text-white/80">
                      <Maximize2 size={11} /> Performance View
                    </button>
                  )}
                </div>
                <div className="flex-1 overflow-y-auto bg-white p-6 dark:bg-[#0A0A0B]">
                  {selectedSong ? (
                    <>
                      <div className="mb-5 border-b border-black/[0.08] pb-4 dark:border-white/[0.08]">
                        <h2 className="text-lg font-bold text-[#1C1C1E] dark:text-white">{draftMeta.title || 'Untitled'}</h2>
                        <div className="mt-1.5 flex flex-wrap items-center gap-2">
                          {draftMeta.artist && <span className="text-sm text-[#3C3C43]/55 dark:text-white/50">{draftMeta.artist}</span>}
                          {draftMeta.display_key && (
                            <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-bold text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                              Key of {draftMeta.display_key}
                            </span>
                          )}
                          {draftMeta.bpm && <span className="flex items-center gap-1 text-xs text-[#3C3C43]/40 dark:text-white/30"><Hash size={10} />{draftMeta.bpm} BPM</span>}
                          {draftMeta.capo && <span className="text-xs text-[#3C3C43]/40 dark:text-white/30">Capo {draftMeta.capo}</span>}
                          {draftMeta.is_lead_guitar && (
                            <span className="flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400">
                              <Guitar size={10} /> Lead Guitar
                            </span>
                          )}
                        </div>
                      </div>
                      <div dangerouslySetInnerHTML={{ __html: renderPreview(draft, isDark) }} />
                    </>
                  ) : (
                    <p className="text-sm text-[#3C3C43]/40 dark:text-white/30">Select a song to see preview.</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* ── Setlist tab ── */}
          {activeTab === 'setlist' && (
            <div className="flex flex-1 flex-col overflow-hidden p-5">
              <div className="mb-4 flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm font-medium text-[#1C1C1E]/70 dark:text-white/70">
                  Set name
                  <input value={setName} onChange={(e) => setSetName(e.target.value)}
                    className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-800 outline-none focus:ring-2 focus:ring-blue-300 dark:border-white/10 dark:bg-white/5 dark:text-white" />
                </label>
                <span className="text-xs text-[#3C3C43]/40 dark:text-white/30">
                  {activeSetSongs.length} song{activeSetSongs.length !== 1 ? 's' : ''} in set
                </span>
              </div>
              <div className="grid flex-1 grid-cols-2 gap-4 overflow-hidden">
                {[
                  { header: <><Music2 size={14} className="text-[#3C3C43]/40 dark:text-white/40" /> All songs</>, count: songs.length, content: (
                    songs.map((song) => {
                      const inSet = setSongIds.includes(song.path);
                      return (
                        <button key={song.path}
                          onClick={() => setSetSongIds((cur) => inSet ? cur.filter((id) => id !== song.path) : [...cur, song.path])}
                          className={`mb-0.5 w-full rounded-xl px-3 py-2 text-left text-sm transition ${
                            inSet ? 'bg-emerald-50 ring-1 ring-emerald-300/60 dark:bg-emerald-500/10 dark:ring-emerald-500/20'
                                  : 'hover:bg-black/[0.03] dark:hover:bg-white/[0.04]'
                          }`}>
                          <div className="flex items-center gap-2.5">
                            <SongCoverTile path={song.path} title={song.metadata.title} />
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-2">
                                <span className={`truncate font-medium ${inSet ? 'text-emerald-700 dark:text-emerald-400' : 'text-[#1C1C1E] dark:text-white'}`}>
                                  {song.metadata.title || 'Untitled'}
                                </span>
                                {song.metadata.display_key && (
                                  <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                                    {song.metadata.display_key}
                                  </span>
                                )}
                              </div>
                              <div className="text-xs text-[#3C3C43]/45 dark:text-white/40">{song.metadata.artist || 'Unknown artist'}</div>
                            </div>
                          </div>
                        </button>
                      );
                    })
                  )},
                  { header: <><FileText size={14} className="text-[#3C3C43]/40 dark:text-white/40" /> {setName || 'Unnamed'}</>, count: activeSetSongs.length, content: (
                    activeSetSongs.length === 0 ? (
                      <p className="mt-6 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">Click songs on the left to add them.</p>
                    ) : (
                      <DndContext sensors={dndSensors} collisionDetection={closestCenter} onDragEnd={handleSetDragEnd}>
                        <SortableContext items={setSongIds} strategy={verticalListSortingStrategy}>
                          {activeSetSongs.map((song, i) => (
                            <SortableSongItem key={song.path} id={song.path} position={i + 1}
                              title={song.metadata.title} artist={song.metadata.artist} displayKey={song.metadata.display_key}
                              onRemove={() => setSetSongIds((cur) => cur.filter((id) => id !== song.path))} />
                          ))}
                        </SortableContext>
                      </DndContext>
                    )
                  )},
                ].map((panel, pi) => (
                  <div key={pi} className="flex flex-col overflow-hidden rounded-2xl border border-black/[0.08] bg-white dark:border-white/[0.08] dark:bg-[#1C1C1E]">
                    <div className="shrink-0 flex items-center gap-2 border-b border-black/[0.06] px-4 py-3 text-sm font-semibold text-[#1C1C1E]/80 dark:border-white/[0.06] dark:text-white/80">
                      {panel.header}
                      <span className="ml-auto text-xs font-normal text-[#3C3C43]/40 dark:text-white/30">{panel.count}</span>
                    </div>
                    <div className="flex-1 overflow-y-auto p-2">{panel.content}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ── Health tab ── */}
          {activeTab === 'health' && (
            <div className="flex-1 overflow-y-auto p-6">
              <div className="mb-5 flex items-center gap-3">
                <h2 className="text-base font-semibold text-[#1C1C1E] dark:text-white">Library Health</h2>
                <span className="text-sm text-[#3C3C43]/40 dark:text-white/30">{songs.length} songs scanned</span>
                {healthItems.length === 0 ? (
                  <span className="flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400">
                    <CheckCircle2 size={12} /> All clean
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-medium text-amber-700 dark:bg-amber-500/10 dark:text-amber-400">
                    <AlertCircle size={12} /> {healthItems.length} songs with issues
                  </span>
                )}
              </div>
              {healthItems.length === 0 ? (
                <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-10 text-center dark:border-emerald-500/20 dark:bg-emerald-500/5">
                  <CheckCircle2 size={28} className="mx-auto mb-3 text-emerald-500" />
                  <p className="font-semibold text-emerald-700 dark:text-emerald-400">Library looks great!</p>
                  <p className="mt-1 text-sm text-emerald-600/80 dark:text-emerald-500/70">All {songs.length} songs have title, artist, and key.</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {healthItems.map(({ song, issues }) => (
                    <div key={song.path}
                      className="flex items-start justify-between gap-4 rounded-2xl border border-black/[0.06] bg-white px-4 py-3 transition hover:border-amber-300/60 hover:bg-amber-50/30 dark:border-white/[0.06] dark:bg-[#1C1C1E] dark:hover:border-amber-500/20 dark:hover:bg-amber-500/[0.03]">
                      <div className="min-w-0 flex-1">
                        <button onClick={() => { setSelectedPath(song.path); setActiveTab('editor'); }} className="text-left">
                          <div className="text-sm font-semibold text-blue-600 hover:underline dark:text-blue-400">
                            {song.metadata.title || <span className="italic text-[#3C3C43]/35 dark:text-white/30">No title</span>}
                          </div>
                          <div className="text-xs text-[#3C3C43]/45 dark:text-white/40">{song.metadata.artist || 'No artist'}</div>
                        </button>
                      </div>
                      <div className="flex flex-wrap justify-end gap-1.5">
                        {issues.map((issue) => (
                          <span key={issue} className="rounded-full bg-amber-100 px-2.5 py-0.5 text-[11px] font-medium text-amber-800 dark:bg-amber-500/10 dark:text-amber-400">
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

    {/* ── Performance View modal (always dark) ── */}
    {showPerfView && selectedSong && (
      <div className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-black">
        <div className="shrink-0 flex items-center gap-4 border-b border-white/[0.08] px-8 py-4">
          <div className="flex-1 min-w-0">
            <h1 className="truncate text-xl font-bold text-white">{draftMeta.title || 'Untitled'}</h1>
            <div className="flex items-center gap-3 mt-0.5">
              {draftMeta.artist && <span className="text-sm text-white/40">{draftMeta.artist}</span>}
              {draftMeta.display_key && (
                <span className="rounded-full bg-amber-500/15 px-2.5 py-0.5 text-xs font-bold text-amber-400">
                  Key of {draftMeta.display_key}
                </span>
              )}
              {draftMeta.bpm  && <span className="text-xs text-white/30">{draftMeta.bpm} BPM</span>}
              {draftMeta.capo && <span className="text-xs text-white/30">Capo {draftMeta.capo}</span>}
            </div>
          </div>
          <button onClick={() => setShowPerfView(false)}
            className="shrink-0 rounded-lg border border-white/10 p-2 text-white/40 hover:border-white/20 hover:text-white transition">
            <X size={18} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-12 py-6">
          <div dangerouslySetInnerHTML={{ __html: renderPerformance(draft, semitoneShift(draftMeta.original_key, draftMeta.display_key), draftMeta.display_key) }} />
        </div>
      </div>
    )}
    </>
  );
}
