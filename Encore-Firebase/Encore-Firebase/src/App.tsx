import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  ChevronDown,
  ClipboardPaste,
  Cloud,
  Copy,
  FileText,
  FolderOpen,
  GripVertical,
  Guitar,
  Hash,
  ListMusic,
  LogIn,
  LogOut,
  Moon,
  Music2,
  Maximize2,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Search,
  Sparkles,
  Sun,
  Tag,
  Trash2,
  Upload,
  Wand2,
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
type AppTab  = 'editor' | 'setlist' | 'shows' | 'health';
type AddTab  = 'paste' | 'blank' | 'ai';

type ShowSummary = { name: string; path: string; counts: number[]; total: number };

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

type Block =
  | { type: 'section'; label: string }
  | { type: 'content'; text: string };

type HealthItem = { song: SongRecord; issues: string[] };

type ImportRow = {
  set: number;
  csvTitle: string;
  csvArtist: string;
  matched: SongRecord | null;
  confidence: 'high' | 'fuzzy' | 'none';
  override: string; // song path, or '' to skip
};

type FsDirectoryHandle = FileSystemDirectoryHandle & { values(): AsyncIterable<FileSystemHandle> };
type FsFileHandle = FileSystemFileHandle;

// ── Cover palette ─────────────────────────────────────────────────────────────

const SET_BADGE_COLORS: Record<number, { bg: string; fg: string }> = {
  1: { bg: 'rgba(59,130,246,0.13)',  fg: '#2563EB' },
  2: { bg: 'rgba(16,185,129,0.13)',  fg: '#059669' },
  3: { bg: 'rgba(249,115,22,0.13)',  fg: '#EA580C' },
  4: { bg: 'rgba(139,92,246,0.13)',  fg: '#7C3AED' },
};

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
  'post-chorus', 'post chorus', 'postchorus',
  'bridge', 'outro', 'fade', 'fade out', 'fadeout', 'ending', 'end',
  'solo', 'guitar solo', 'lead', 'interlude', 'instrumental', 'key', 'tag',
  'vamp', 'breakdown', 'hook', 'refrain', 'coda', 'turnaround', 'riff', 'fill',
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

/** Normalize a raw section label for matching against KNOWN_SECTIONS:
 *  - strip leading markdown `#` markers and spaces
 *  - strip leading ordinals like "1st", "2nd", "3rd"
 *  - lowercase, strip trailing digits/spaces */
function normalizeSectionKey(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/^[#\s*]+/, '')            // strip leading # * whitespace
    .replace(/^\d+(?:st|nd|rd|th)\s+/, '') // strip leading ordinal "1st ", "2nd " etc.
    .replace(/[\s\d]+$/, '')            // strip trailing whitespace and digits
    .trim();
}

const UUID_RE  = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Matches chord-like tokens: A–G + optional # or b + optional quality + optional number
const CHORD_RE = /^[A-G][#b]?(?:m(?:aj)?|maj|min|sus|dim|aug|add)?[0-9]*(?:\/[A-G][#b]?)?$/;

function looksLikeChord(raw: string): boolean {
  return CHORD_RE.test(raw.trim());
}

function checkSongHealth(song: SongRecord): string[] {
  const issues: string[] = [];
  const body = song.body;
  const isUuid = UUID_RE.test(song.metadata.title);
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
    if (looksLikeChord(raw)) return; // chord annotation, not a section header
    const n = normalizeSectionKey(raw);
    if (![...KNOWN_SECTIONS].some((s) => n === s || n.startsWith(s)) && raw) {
      issues.push(`Non-standard section: "${raw}"`);
    }
  });
  return issues;
}

// ── Fix suggestions ────────────────────────────────────────────────────────────

type FixSuggestion = {
  issue: string;
  label: string;
  apply: (song: SongRecord) => SongRecord;
};

/** Extract title from first heading line in the markdown body. */
function inferTitleFromBody(body: string): string {
  for (const line of body.split('\n')) {
    const t = line.trim();
    if (!t) continue;
    // Markdown heading: # Title
    const mdMatch = t.match(/^#{1,3}\s+(.+)$/);
    if (mdMatch) return mdMatch[1].replace(/<[^>]+>/g, '').trim();
    // Bracket heading: [Title] — skip if it looks like a chord
    const brMatch = t.match(/^\[([A-Za-z][^\]]*)\]$/);
    if (brMatch && !looksLikeChord(brMatch[1])) return brMatch[1].trim();
    break; // only inspect first non-blank line
  }
  return '';
}

/** Return the canonical display name for a non-standard section label, or null if no match. */
function closestSection(raw: string): string | null {
  const n = normalizeSectionKey(raw);
  // Already valid after normalization → no rename needed
  if ([...KNOWN_SECTIONS].some((s) => n === s || n.startsWith(s))) return null;
  // Alias map: normalized key → canonical display label
  const ALIASES: Record<string, string> = {
    // Pre/Post chorus
    'prechorus': 'Pre-Chorus',
    'pre chorus': 'Pre-Chorus',
    'pre-chorus': 'Pre-Chorus',
    'postchorus': 'Post-Chorus',
    'post chorus': 'Post-Chorus',
    'post-chorus': 'Post-Chorus',
    // Outro / fade
    'ending': 'Outro',
    'end': 'Outro',
    'fade out': 'Outro',
    'fadeout': 'Outro',
    'fade': 'Outro',
    // Final / last section
    'final chorus': 'Chorus',
    'last chorus': 'Chorus',
    'closing': 'Outro',
    // Solo
    'guitar solo': 'Solo',
    'lead guitar': 'Solo',
    'lead solo': 'Solo',
    // Instrumental/groove
    'riff': 'Instrumental',
    'groove': 'Vamp',
    'jam': 'Vamp',
    'fill': 'Interlude',
    // Misc
    'refrain': 'Chorus',
    'turn': 'Turnaround',
    'stanza': 'Verse',
    'section': 'Verse',
  };
  if (ALIASES[n]) return ALIASES[n];
  // Prefix match against known sections
  const match = [...KNOWN_SECTIONS].find((s) => n.startsWith(s) || s.startsWith(n));
  if (match) return match.split('-').map((w) => w[0].toUpperCase() + w.slice(1)).join('-');
  return null;
}

function suggestFixes(song: SongRecord): FixSuggestion[] {
  const fixes: FixSuggestion[] = [];
  const isUuid = UUID_RE.test(song.metadata.title);

  // ── Missing title ──────────────────────────────────────────────────────────
  if (!song.metadata.title || isUuid) {
    // Try filename first (only if not UUID)
    const inferred = inferTitleArtistFromPath(song.path);
    const filenameTitle = UUID_RE.test(inferred.title) ? '' : inferred.title;
    const bodyTitle = inferTitleFromBody(song.body);
    const suggested = filenameTitle || bodyTitle;
    if (suggested) {
      fixes.push({
        issue: 'Missing title',
        label: `Set title → "${suggested}"`,
        apply: (s) => ({ ...s, metadata: { ...s.metadata, title: suggested } }),
      });
    }
  }

  // ── Missing artist ─────────────────────────────────────────────────────────
  if (!song.metadata.artist || song.metadata.artist.toLowerCase() === 'unknown artist') {
    const inferred = inferTitleArtistFromPath(song.path);
    if (inferred.artist) {
      fixes.push({
        issue: 'Missing artist',
        label: `Set artist → "${inferred.artist}"`,
        apply: (s) => ({ ...s, metadata: { ...s.metadata, artist: inferred.artist } }),
      });
    } else {
      const artistMatch = song.body.match(/^(?:Artist|By|Performed by)[:\s]+(.+)$/im);
      if (artistMatch) {
        const found = artistMatch[1].trim();
        fixes.push({
          issue: 'Missing artist',
          label: `Set artist → "${found}"`,
          apply: (s) => ({ ...s, metadata: { ...s.metadata, artist: found } }),
        });
      }
    }
  }

  // ── Missing key ────────────────────────────────────────────────────────────
  if (!song.metadata.display_key) {
    const bodyMeta = extractBodyMetadata(song.body);
    if (bodyMeta.displayKey) {
      fixes.push({
        issue: 'Missing key',
        label: `Set key → "${bodyMeta.displayKey}"`,
        apply: (s) => ({ ...s, metadata: { ...s.metadata, display_key: bodyMeta.displayKey, original_key: s.metadata.original_key || bodyMeta.displayKey } }),
      });
    } else {
      // Scan for first inline chord `[X]` or standalone bracket chord line [X]
      const chordMatch =
        song.body.match(/`\[([A-G][#b]?(?:m(?:aj)?|maj|min|sus|dim|aug|add)?[0-9]*(?:\/[A-G][#b]?)?)\]`/) ||
        song.body.match(/^\[([A-G][#b]?(?:m(?:aj)?|maj|min|sus|dim|aug|add)?[0-9]*)\]$/m);
      if (chordMatch) {
        const root = chordMatch[1].match(/^[A-G][#b]?/)?.[0];
        const isMinor = /^[A-G][#b]?m(?!aj)/i.test(chordMatch[1]);
        const key = root ? (isMinor ? root + 'm' : root) : null;
        if (key) {
          fixes.push({
            issue: 'Missing key',
            label: `Set key → "${key}" (from first chord)`,
            apply: (s) => ({ ...s, metadata: { ...s.metadata, display_key: key, original_key: s.metadata.original_key || key } }),
          });
        }
      }
    }
  }

  // ── Unclosed [h] tags ──────────────────────────────────────────────────────
  const opens  = (song.body.match(/\[h\]/gi)  || []).length;
  const closes = (song.body.match(/\[\/h\]/gi) || []).length;
  if (opens !== closes) {
    fixes.push({
      issue: `Unclosed [h] tags (${opens} open / ${closes} close)`,
      label: 'Remove all [h] / [/h] tags',
      apply: (s) => ({ ...s, body: s.body.replace(/\[\/?\s*h\s*\]/gi, '') }),
    });
  }

  // ── Non-standard sections ──────────────────────────────────────────────────
  const body = song.body;
  const spanH    = Array.from(body.matchAll(/<span[^>]*>##?\s*(.*?)<\/span>/gi)).map((m) => m[1].trim());
  const mdH      = Array.from(body.matchAll(/^#{1,2}\s+(.+)$/gm)).map((m) => m[1].trim());
  const bracketH = Array.from(body.matchAll(/^\[([A-Za-z][^\]]*)\]$/gm)).map((m) => m[1].trim());
  const nonStandard = [...spanH, ...mdH, ...bracketH].filter((raw) => {
    if (looksLikeChord(raw)) return false;
    const n = normalizeSectionKey(raw);
    return ![...KNOWN_SECTIONS].some((s) => n === s || n.startsWith(s)) && !!raw;
  });
  const seen = new Set<string>();
  for (const raw of nonStandard) {
    if (seen.has(raw)) continue;
    seen.add(raw);
    const closest = closestSection(raw);
    if (closest) {
      // Preserve trailing number if present (e.g. "Pre Chorus 1" → "Pre-Chorus 1")
      const trailingNum = raw.match(/\s*(\d+)$/)?.[1];
      const renamed = trailingNum ? `${closest} ${trailingNum}` : closest;
      fixes.push({
        issue: `Non-standard section: "${raw}"`,
        label: `Rename "${raw}" → "${renamed}"`,
        apply: (s) => ({
          ...s,
          body: s.body.replace(
            new RegExp(`(^|(?<=\\[))${raw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?=\\]|$)`, 'gm'),
            renamed,
          ),
        }),
      });
    }
  }

  return fixes;
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

// ── CSV import + song matching ────────────────────────────────────────────────

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  for (const line of text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').split('\n')) {
    if (!line.trim()) continue;
    const fields: string[] = [];
    let field = '', inQ = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '"') { if (inQ && line[i + 1] === '"') { field += '"'; i++; } else inQ = !inQ; }
      else if (ch === ',' && !inQ) { fields.push(field.trim()); field = ''; }
      else field += ch;
    }
    fields.push(field.trim());
    rows.push(fields);
  }
  return rows;
}

/** Returns 1–4 if header looks like a set column name, null otherwise. */
function headerToSetNumber(header: string): number | null {
  const h = header.toLowerCase().trim();
  if (/set\s*(one|1|i)\b/.test(h))           return 1;
  if (/set\s*(two|2|ii)\b/.test(h))          return 2;
  if (/set\s*(three|3|iii)\b/.test(h))       return 3;
  if (/set\s*(four|4|iv)\b/.test(h))         return 4;
  if (/extra|encore|bonus|late/.test(h))     return 4;
  return null;
}

function detectCsvCols(headers: string[]): { setCol: number; titleCol: number; artistCol: number } {
  const h = headers.map((s) => s.toLowerCase().replace(/[^a-z]/g, ''));
  return {
    setCol:    h.findIndex((c) => /^(set|setnumber|setnum|num)$/.test(c)),
    titleCol:  h.findIndex((c) => /title|song|name/.test(c)),
    artistCol: h.findIndex((c) => /artist|band|performer/.test(c)),
  };
}

function normStr(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9\s]/g, '').replace(/\b(the|a|an)\b\s*/g, '').replace(/\s+/g, ' ').trim();
}

function wordOverlap(a: string, b: string): number {
  const words = (s: string) => new Set(s.split(' ').filter((w) => w.length > 2));
  const wa = words(a), wb = words(b);
  if (!wa.size || !wb.size) return 0;
  let n = 0; wa.forEach((w) => { if (wb.has(w)) n++; });
  return n / Math.max(wa.size, wb.size);
}

function levenshtein(a: string, b: string): number {
  const m = a.length, n = b.length;
  const dp = Array.from({ length: m + 1 }, (_, i) => Array.from({ length: n + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)));
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]);
  return dp[m][n];
}

function matchSong(title: string, artist: string, songs: SongRecord[]): { song: SongRecord; confidence: 'high' | 'fuzzy'; score: number } | null {
  const ni = normStr(title);
  const na = artist.toLowerCase().trim();
  let best: { song: SongRecord; confidence: 'high' | 'fuzzy'; score: number } | null = null;
  for (const song of songs) {
    const nt = normStr(song.metadata.title);
    const nsa = song.metadata.artist.toLowerCase().trim();
    let score = 0;
    if (ni === nt)                                                          score = 100;
    else if (nt.includes(ni) || ni.includes(nt))                           score = 85;
    else {
      const ov = wordOverlap(ni, nt);
      if (ov >= 0.6)                                                        score = 60 + ov * 20;
      else {
        const d = levenshtein(ni, nt), maxL = Math.max(ni.length, nt.length);
        if (d <= 2)                                                         score = 55 - d * 5;
        else if (maxL > 0 && d / maxL < 0.3)                               score = 40;
      }
    }
    if (score === 0) continue;
    if (na && nsa && (nsa.includes(na) || na.includes(nsa))) score += 10;
    const confidence: 'high' | 'fuzzy' = score >= 80 ? 'high' : 'fuzzy';
    if (!best || score > best.score) best = { song, confidence, score };
  }
  return best;
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
  // ── Add Song modal (paste / blank / AI) ──
  const [showAddModal, setShowAddModal] = useState(false);
  const [addTab, setAddTab]             = useState<AddTab>('paste');
  const [addTitle, setAddTitle]         = useState('');
  const [addArtist, setAddArtist]       = useState('');
  const [addKey, setAddKey]             = useState('G');
  const [addCapo, setAddCapo]           = useState('');
  const [addBody, setAddBody]           = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [isDraggingFile, setIsDraggingFile] = useState(false);
  const mdFileRef = useRef<HTMLInputElement>(null);
  const dragDepth = useRef(0);
  // ── Bulk .md import ──
  const [bulkRows, setBulkRows]   = useState<{ name: string; meta: SongMeta; body: string; include: boolean }[]>([]);
  const [showBulkModal, setShowBulkModal] = useState(false);
  const [bulkBusy, setBulkBusy]   = useState(false);
  const [bulkProgress, setBulkProgress] = useState('');
  // ── Shows browser ──
  const [showsList, setShowsList]       = useState<ShowSummary[]>([]);
  const [showsLoading, setShowsLoading] = useState(false);
  const [renamingShow, setRenamingShow] = useState<string | null>(null);
  const [renameInput, setRenameInput]   = useState('');
  const [confirmDeleteShow, setConfirmDeleteShow] = useState<string | null>(null);
  const [liveSets, setLiveSets]         = useState<Record<number, string[]>>({ 1: [], 2: [], 3: [], 4: [] });
  const [activeSetNumber, setActiveSetNumber] = useState(1);
  const [setSaveStatus, setSetSaveStatus] = useState('');
  const [importRows, setImportRows]     = useState<ImportRow[]>([]);
  const [showImport, setShowImport]     = useState(false);
  const importRef = useRef<HTMLInputElement>(null);
  const [currentShowName, setCurrentShowName] = useState('');
  const [showCloudSetPicker, setShowCloudSetPicker] = useState(false);
  const [cloudSetFiles, setCloudSetFiles] = useState<{name: string; path: string}[]>([]);
  const [cloudSetFilesLoading, setCloudSetFilesLoading] = useState(false);
  const [showSaveShowModal, setShowSaveShowModal] = useState(false);
  const [saveShowNameInput, setSaveShowNameInput] = useState('');

  const dndSensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );


  const [libraryHandle, setLibraryHandle] = useState<FsDirectoryHandle | null>(null);
  const [fileHandles, setFileHandles]     = useState<Record<string, FsFileHandle>>({});
  const activeTextareaRef = useRef<HTMLTextAreaElement | null>(null);

  // ── Quick Edit modal ───────────────────────────────────────────────────────
  const [quickEditSong,    setQuickEditSong]    = useState<SongRecord | null>(null);
  const [qeConfirmDelete,  setQeConfirmDelete]  = useState(false);
  const [qeTitle,       setQeTitle]       = useState('');
  const [qeArtist,      setQeArtist]      = useState('');
  const [qeKey,         setQeKey]         = useState('');
  const [qeIsLead,      setQeIsLead]      = useState(false);
  const [qeCapo,        setQeCapo]        = useState(false);
  const [qeCapoFret,    setQeCapoFret]    = useState(1);
  const [qeBpm,         setQeBpm]         = useState('');

  function openQuickEdit(song: SongRecord) {
    setQeConfirmDelete(false);
    setQuickEditSong(song);
    setQeTitle(song.metadata.title);
    setQeArtist(song.metadata.artist);
    setQeKey(song.metadata.display_key || '');
    setQeIsLead(song.metadata.is_lead_guitar);
    setQeCapo(Boolean(song.metadata.capo && Number(song.metadata.capo) > 0));
    setQeCapoFret(Number(song.metadata.capo) || 1);
    setQeBpm(song.metadata.bpm || '');
  }

  async function saveQuickEdit() {
    if (!quickEditSong) return;
    const updated: SongRecord = {
      ...quickEditSong,
      metadata: {
        ...quickEditSong.metadata,
        title:          qeTitle.trim(),
        artist:         qeArtist.trim(),
        display_key:    qeKey,
        original_key:   quickEditSong.metadata.original_key || qeKey,
        is_lead_guitar: qeIsLead,
        capo:           qeCapo ? String(qeCapoFret) : '',
        bpm:            qeBpm.trim(),
      },
    };
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
      if (selectedPath === updated.path) {
        setDraftMeta(updated.metadata);
      }
      setStatus(`Saved "${updated.metadata.title}".`);
      setQuickEditSong(null);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Save failed.');
    } finally {
      setIsBusy(false);
    }
  }

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

  useEffect(() => {
    if (activeTab === 'shows' && mode === 'cloud' && authReady && user && gcsToken) {
      void refreshShows();
    }
  }, [activeTab, mode, authReady, user, gcsToken]);

  // Drag a .md file anywhere in the window to start a new song from it.
  useEffect(() => {
    const hasFiles = (e: DragEvent) => Array.from(e.dataTransfer?.types ?? []).includes('Files');
    const onEnter = (e: DragEvent) => { if (!hasFiles(e)) return; e.preventDefault(); dragDepth.current++; setIsDraggingFile(true); };
    const onOver  = (e: DragEvent) => { if (hasFiles(e)) e.preventDefault(); };
    const onLeave = (e: DragEvent) => { if (!hasFiles(e)) return; dragDepth.current = Math.max(0, dragDepth.current - 1); if (dragDepth.current === 0) setIsDraggingFile(false); };
    const onDrop  = (e: DragEvent) => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      dragDepth.current = 0;
      setIsDraggingFile(false);
      const mds = Array.from(e.dataTransfer?.files ?? []).filter((f) => /\.(md|markdown|txt)$/i.test(f.name));
      if (mds.length === 0) setStatus('Only .md files can be dropped in.');
      else if (mds.length === 1) void loadMdFile(mds[0]);
      else void openBulkImport(mds);
    };
    window.addEventListener('dragenter', onEnter);
    window.addEventListener('dragover',  onOver);
    window.addEventListener('dragleave', onLeave);
    window.addEventListener('drop',      onDrop);
    return () => {
      window.removeEventListener('dragenter', onEnter);
      window.removeEventListener('dragover',  onOver);
      window.removeEventListener('dragleave', onLeave);
      window.removeEventListener('drop',      onDrop);
    };
  }, []);

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

  const songSetMap = useMemo(() => {
    const map: Record<string, number[]> = {};
    ([1, 2, 3, 4] as const).forEach((n) => {
      (liveSets[n] ?? []).forEach((path) => {
        if (!map[path]) map[path] = [];
        map[path].push(n);
      });
    });
    return map;
  }, [liveSets]);

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
      // Load sets 1–4 from GCS
      const sets: Record<number, string[]> = { 1: [], 2: [], 3: [], 4: [] };
      await Promise.all([1, 2, 3, 4].map(async (n) => {
        try {
          const data = await CloudLibraryService.loadSetFile(user.email, n, gcsToken);
          if (data?.songIds) sets[n] = data.songIds;
        } catch { /* set doesn't exist yet */ }
      }));
      setLiveSets(sets);
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

  async function applyHealthFix(fix: FixSuggestion, song: SongRecord) {
    const updated = fix.apply(song);
    const serialized = serializeSong(updated);
    setIsBusy(true);
    setStatus('Applying fix…');
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
      // If this song is currently open in the editor, sync draft state too
      if (selectedPath === updated.path) {
        setDraftMeta(updated.metadata);
        setDraft(stripLeadingTitle(normalizeMarkdownBody(updated.body), updated.metadata.title));
      }
      setStatus(`Fixed "${updated.metadata.title || song.metadata.title}".`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Fix failed.');
    } finally {
      setIsBusy(false);
    }
  }

  async function deleteSong(song: SongRecord) {
    setIsBusy(true);
    setStatus('Deleting…');
    try {
      if (mode === 'cloud') {
        await CloudLibraryService.deleteFile(song.path, gcsToken!);
      } else {
        // For local mode, remove from in-memory list only (can't delete via File System API without extra permissions)
      }
      setSongs((cur) => cur.filter((s) => s.path !== song.path));
      if (selectedPath === song.path) setSelectedPath('');
      setQuickEditSong(null);
      setStatus(`Deleted "${song.metadata.title || 'song'}".`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Delete failed.');
    } finally {
      setIsBusy(false);
    }
  }

  // ── Add Song ──────────────────────────────────────────────────────────────
  //
  // Every add path funnels through createAndSaveSong, which UPLOADS to the cloud
  // (or writes the local file) BEFORE touching UI state — so a new song can never
  // be lost by navigating away or refreshing, unlike the old in-memory-only flow.

  function openAddModal(tab: AddTab = 'paste') {
    setAddTitle(''); setAddArtist(''); setAddKey('G'); setAddCapo(''); setAddBody('');
    setAddTab(tab);
    setShowAddModal(true);
  }

  function closeAddModal() {
    setShowAddModal(false);
    setAddTitle(''); setAddArtist(''); setAddKey('G'); setAddCapo(''); setAddBody('');
  }

  /** Upload one song (cloud or local) and return the parsed record. No UI-state side
   *  effects beyond registering a local file handle — callers decide what to select. */
  async function persistSong(meta: SongMeta, body: string): Promise<SongRecord> {
    const uid  = crypto.randomUUID();
    const path = mode === 'cloud' && user
      ? `${user.email}/songs/${uid}.md`
      : `${(meta.title || 'untitled').trim()} - ${(meta.artist || '').trim()}`.replace(/[/:*?"<>|]+/g, '-') + '.md';
    const serialized = serializeSong({ path, raw: '', body, metadata: meta });
    if (mode === 'cloud') {
      await CloudLibraryService.uploadMarkdownFile(path, serialized, gcsToken!);
    } else {
      if (!libraryHandle) throw new Error('Open a local folder first.');
      const fh = await libraryHandle.getFileHandle(path, { create: true });
      const writable = await fh.createWritable();
      await writable.write(serialized);
      await writable.close();
      setFileHandles((cur) => ({ ...cur, [path]: fh }));
    }
    return parseSong(path, serialized);
  }

  /** Build a SongRecord, persist it, then open it in the editor. Returns success. */
  async function createAndSaveSong(meta: SongMeta, body: string): Promise<boolean> {
    if (mode === 'cloud' && (!user?.email || !gcsToken)) { setStatus('Sign in first.'); return false; }
    setIsBusy(true);
    setStatus('Saving…');
    try {
      const saved = await persistSong(meta, body);
      setSongs((cur) => [saved, ...cur]);
      setSelectedPath(saved.path);
      setActiveTab('editor');
      setStatus(`Added "${saved.metadata.title || 'song'}".`);
      return true;
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Save failed.');
      return false;
    } finally {
      setIsBusy(false);
    }
  }

  /** Load a dropped/browsed .md file into the Add modal: pre-fill metadata from its
   *  front matter (and filename) for review, keep the full text in the body so nothing
   *  is lost, then it saves to the cloud on Add like any other paste. */
  async function loadMdFile(file: File) {
    try {
      const text   = await file.text();
      const parsed = parseSong(file.name, text);
      setAddTitle(parsed.metadata.title);
      setAddArtist(parsed.metadata.artist);
      setAddKey(parsed.metadata.display_key || 'G');
      setAddCapo(parsed.metadata.capo || '');
      setAddBody(text);
      setAddTab('paste');
      setShowAddModal(true);
      setStatus(`Loaded "${file.name}" — review metadata, then Add.`);
    } catch {
      setStatus('Could not read that file.');
    }
  }

  /** Multiple .md files dropped/selected: parse each and open the bulk review modal. */
  async function openBulkImport(files: File[]) {
    try {
      const rows = await Promise.all(files.map(async (f) => {
        const parsed = parseSong(f.name, await f.text());
        return { name: f.name, meta: parsed.metadata, body: parsed.body, include: true };
      }));
      setBulkRows(rows);
      setBulkProgress('');
      setShowBulkModal(true);
    } catch {
      setStatus('Could not read those files.');
    }
  }

  /** Import all checked rows to the cloud, one after another, then refresh the list. */
  async function commitBulkImport() {
    if (mode === 'cloud' && (!user?.email || !gcsToken)) { setStatus('Sign in first.'); return; }
    const rows = bulkRows.filter((r) => r.include);
    if (rows.length === 0) return;
    setBulkBusy(true);
    const added: SongRecord[] = [];
    let ok = 0, fail = 0;
    for (let i = 0; i < rows.length; i++) {
      setBulkProgress(`Importing ${i + 1} / ${rows.length}…`);
      try {
        added.push(await persistSong(rows[i].meta, rows[i].body));
        ok++;
      } catch {
        fail++;
      }
    }
    if (added.length) {
      setSongs((cur) => {
        const map = new Map(cur.map((s) => [s.path, s]));
        added.forEach((s) => map.set(s.path, s));
        return Array.from(map.values()).sort((a, b) =>
          a.metadata.title.toLowerCase().localeCompare(b.metadata.title.toLowerCase()));
      });
    }
    setBulkBusy(false);
    setBulkProgress('');
    setShowBulkModal(false);
    setBulkRows([]);
    setStatus(`Imported ${ok} song${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}.`);
  }

  /** Paste tab: parseSong first so a full .md (with front matter) is handled gracefully;
   *  entered fields override anything the paste already carried. */
  async function handleAddPaste() {
    const parsed = parseSong('paste.md', addBody);
    const meta: SongMeta = {
      ...parsed.metadata,
      title:        addTitle.trim()  || parsed.metadata.title,
      artist:       addArtist.trim() || parsed.metadata.artist,
      display_key:  addKey           || parsed.metadata.display_key,
      original_key: parsed.metadata.original_key || addKey || parsed.metadata.display_key,
      capo:         addCapo          || parsed.metadata.capo,
    };
    if (await createAndSaveSong(meta, parsed.body)) closeAddModal();
  }

  /** Blank tab: metadata only, empty body — saved immediately, then editable. */
  async function handleAddBlank() {
    const meta: SongMeta = {
      ...EMPTY_META,
      title: addTitle.trim(), artist: addArtist.trim(),
      display_key: addKey, original_key: addKey, capo: addCapo,
    };
    if (await createAndSaveSong(meta, '')) closeAddModal();
  }

  /** AI tab: ChordSidekick generates a full chart, then it persists immediately. */
  async function handleGenerateAI() {
    if (!addTitle.trim() || !addArtist.trim()) return;
    setIsGenerating(true);
    setStatus('ChordSidekick is writing your chart…');
    try {
      const userMsg = [`Title: ${addTitle.trim()}`, `Artist: ${addArtist.trim()}`, `Key: ${addKey}`,
        addCapo ? `Capo: ${addCapo}` : '', addBody.trim() ? `\nAdditional notes / existing lyrics:\n${addBody.trim()}` : '']
        .filter(Boolean).join('\n');
      const generated = await callChordSidekick(userMsg);
      const parsed = parseSong('gen.md', generated);
      const meta: SongMeta = {
        ...parsed.metadata,
        title:        parsed.metadata.title  || addTitle.trim(),
        artist:       parsed.metadata.artist || addArtist.trim(),
        display_key:  parsed.metadata.display_key || addKey,
        original_key: parsed.metadata.original_key || addKey,
        capo:         addCapo || parsed.metadata.capo,
      };
      if (await createAndSaveSong(meta, parsed.body)) closeAddModal();
    } catch (err) {
      setStatus(err instanceof Error ? err.message : 'Generation failed.');
    } finally {
      setIsGenerating(false);
    }
  }

  /** Paste tab helper: reformat the pasted sheet into Encore format via ChordSidekick. */
  async function handleCleanupPaste() {
    if (!addBody.trim()) return;
    setIsGenerating(true);
    setStatus('ChordSidekick is cleaning up…');
    try {
      const msg = [
        'Re-format this existing chord sheet into Encore format. Keep every section,',
        'lyric, and chord exactly as given — only fix the formatting.',
        '',
        `Title: ${addTitle.trim()}`, `Artist: ${addArtist.trim()}`, `Key: ${addKey}`,
        '', addBody,
      ].join('\n');
      const cleaned = await callChordSidekick(msg);
      setAddBody(cleaned);
      setStatus('Cleaned up — review, then Add.');
    } catch (err) {
      setStatus(err instanceof Error ? err.message : 'Cleanup failed.');
    } finally {
      setIsGenerating(false);
    }
  }

  async function saveSetToCloud(setNumber: number, ids: string[]) {
    if (mode !== 'cloud' || !user?.email || !gcsToken) return;
    if (!currentShowName) {
      // No show loaded — save to numbered fallback silently
      try { await CloudLibraryService.saveSetFile(user.email, setNumber, ids, gcsToken); } catch { /* ignore */ }
      return;
    }
    // Auto-save to the named show file AND the numbered set_N.json file.
    // The numbered file keeps Android's background sync in sync so it
    // doesn't restore stale data after a show is loaded on the tablet.
    const allSets = { ...liveSets, [setNumber]: ids };
    try {
      await Promise.all([
        CloudLibraryService.saveShowFile(user.email, currentShowName, allSets, gcsToken),
        CloudLibraryService.saveSetFile(user.email, setNumber, ids, gcsToken),
      ]);
      setSetSaveStatus('✓ Saved');
      setTimeout(() => setSetSaveStatus(''), 2000);
    } catch {
      setSetSaveStatus('Save failed');
    }
  }

  async function openLoadShowPicker() {
    if (mode !== 'cloud' || !user?.email || !gcsToken) return;
    setShowCloudSetPicker(true);
    setCloudSetFilesLoading(true);
    try {
      const files = await CloudLibraryService.listShowFiles(user.email, gcsToken);
      setCloudSetFiles(files);
    } catch {
      setCloudSetFiles([]);
    } finally {
      setCloudSetFilesLoading(false);
    }
  }

  async function handlePickShow(showName: string) {
    if (!user?.email || !gcsToken) return;
    setShowCloudSetPicker(false);
    try {
      const allSets = await CloudLibraryService.loadShowFile(user.email, showName, gcsToken);
      if (allSets) {
        setLiveSets((prev) => ({ ...prev, ...allSets }));
        setCurrentShowName(showName);
        setSetSaveStatus(`Loaded "${showName}"`);
        setTimeout(() => setSetSaveStatus(''), 2500);
      } else {
        setSetSaveStatus('Could not load show');
      }
    } catch {
      setSetSaveStatus('Load failed');
    }
  }

  async function handleSaveShow(name: string) {
    if (!user?.email || !gcsToken || !name.trim()) return;
    setShowSaveShowModal(false);
    const trimmed = name.trim();
    try {
      // Save the named show file (all 4 sets in one JSON)
      await CloudLibraryService.saveShowFile(user.email, trimmed, liveSets, gcsToken);
      // Also update the numbered set_N.json files so the Android sync
      // sees the same data and doesn't overwrite the loaded show
      await Promise.all(
        ([1, 2, 3, 4] as const).map((n) =>
          CloudLibraryService.saveSetFile(user.email, n, liveSets[n] ?? [], gcsToken)
        )
      );
      setCurrentShowName(trimmed);
      setSetSaveStatus(`Saved "${trimmed}"`);
      setTimeout(() => setSetSaveStatus(''), 2500);
    } catch {
      setSetSaveStatus('Save failed');
    }
  }

  // ── Shows browser ───────────────────────────────────────────────────────────

  async function refreshShows() {
    if (mode !== 'cloud' || !user?.email || !gcsToken) return;
    setShowsLoading(true);
    try {
      const files = await CloudLibraryService.listShowFiles(user.email, gcsToken);
      const withCounts = await Promise.all((files as { name: string; path: string }[]).map(async (f) => {
        try {
          const sets   = await CloudLibraryService.loadShowFile(user.email!, f.name, gcsToken) as Record<number, string[]> | null;
          const counts = [1, 2, 3, 4].map((n) => sets?.[n]?.length ?? 0);
          return { name: f.name, path: f.path, counts, total: counts.reduce((a, b) => a + b, 0) };
        } catch {
          return { name: f.name, path: f.path, counts: [0, 0, 0, 0], total: 0 };
        }
      }));
      withCounts.sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
      setShowsList(withCounts);
    } catch {
      setShowsList([]);
    } finally {
      setShowsLoading(false);
    }
  }

  async function handleLoadShowFromTab(name: string) {
    await handlePickShow(name);
    setActiveTab('setlist');
  }

  async function handleDuplicateShow(name: string) {
    if (!user?.email || !gcsToken) return;
    try {
      const sets = await CloudLibraryService.loadShowFile(user.email, name, gcsToken);
      if (!sets) return;
      const existing = new Set(showsList.map((s) => s.name));
      let copyName = `${name} copy`, i = 2;
      while (existing.has(copyName)) copyName = `${name} copy ${i++}`;
      await CloudLibraryService.saveShowFile(user.email, copyName, sets, gcsToken);
      await refreshShows();
    } catch {
      setStatus('Duplicate failed.');
    }
  }

  async function handleRenameShow(oldName: string, newName: string) {
    const trimmed = newName.trim();
    setRenamingShow(null);
    if (!user?.email || !gcsToken || !trimmed || trimmed === oldName) return;
    try {
      const sets = await CloudLibraryService.loadShowFile(user.email, oldName, gcsToken);
      if (!sets) return;
      await CloudLibraryService.saveShowFile(user.email, trimmed, sets, gcsToken);
      await CloudLibraryService.deleteFile(`${user.email}/sets/${oldName}.json`, gcsToken);
      if (currentShowName === oldName) setCurrentShowName(trimmed);
      await refreshShows();
    } catch {
      setStatus('Rename failed.');
    }
  }

  async function handleDeleteShow(name: string) {
    if (!user?.email || !gcsToken) return;
    setConfirmDeleteShow(null);
    try {
      await CloudLibraryService.deleteFile(`${user.email}/sets/${name}.json`, gcsToken);
      if (currentShowName === name) setCurrentShowName('');
      await refreshShows();
    } catch {
      setStatus('Delete failed.');
    }
  }

  function toggleSongInSet(songPath: string) {
    const current = liveSets[activeSetNumber] ?? [];
    const newIds = current.includes(songPath)
      ? current.filter((id) => id !== songPath)
      : [...current, songPath];
    setLiveSets((prev) => ({ ...prev, [activeSetNumber]: newIds }));
    void saveSetToCloud(activeSetNumber, newIds);
  }

  function removeSongFromSet(songPath: string, setNumber: number) {
    const newIds = (liveSets[setNumber] ?? []).filter((id) => id !== songPath);
    setLiveSets((prev) => ({ ...prev, [setNumber]: newIds }));
    void saveSetToCloud(setNumber, newIds);
  }

  function handleImportFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      const raw = parseCsv(ev.target?.result as string);
      if (!raw.length) return;
      const results: ImportRow[] = [];

      // ── Detect format ──────────────────────────────────────────────────────
      const firstRow = raw[0];
      const colSetNums = firstRow.map(headerToSetNumber);
      const isColumnar = colSetNums.some((n) => n !== null);

      if (isColumnar) {
        // Each column is a set — songs listed vertically per column
        for (let rowIdx = 1; rowIdx < raw.length; rowIdx++) {
          const row = raw[rowIdx];
          for (let colIdx = 0; colIdx < firstRow.length; colIdx++) {
            const setNum = colSetNums[colIdx];
            if (!setNum) continue;
            const title = row[colIdx]?.trim();
            if (!title) continue;
            const m = matchSong(title, '', songs);
            results.push({ set: setNum, csvTitle: title, csvArtist: '',
              matched: m?.song ?? null, confidence: m ? m.confidence : 'none',
              override: m?.song?.path ?? '' });
          }
        }
      } else {
        // Row-based format: Set, Title, Artist
        const hasHeader = firstRow.some((c) => /set|title|song|artist|name/i.test(c));
        const cols = hasHeader ? detectCsvCols(firstRow) : { setCol: 0, titleCol: 1, artistCol: 2 };
        const data = hasHeader ? raw.slice(1) : raw;
        for (const row of data) {
          const setNum = cols.setCol >= 0 ? parseInt(row[cols.setCol] ?? '', 10) : NaN;
          const title  = cols.titleCol >= 0 ? (row[cols.titleCol] ?? '').trim() : (row[1] ?? '').trim();
          const artist = cols.artistCol >= 0 ? (row[cols.artistCol] ?? '').trim() : (row[2] ?? '').trim();
          if (!title || isNaN(setNum) || setNum < 1 || setNum > 4) continue;
          const m = matchSong(title, artist, songs);
          results.push({ set: setNum, csvTitle: title, csvArtist: artist,
            matched: m?.song ?? null, confidence: m ? m.confidence : 'none',
            override: m?.song?.path ?? '' });
        }
      }

      setImportRows(results);
      setShowImport(true);
      if (importRef.current) importRef.current.value = '';
    };
    reader.readAsText(file);
  }

  function commitImport() {
    // Build each set in row order, deduplicating
    const newSets: Record<number, string[]> = { 1: [], 2: [], 3: [], 4: [] };
    for (const row of importRows) {
      if (!row.override || row.set < 1 || row.set > 4) continue;
      if (!newSets[row.set].includes(row.override)) newSets[row.set].push(row.override);
    }
    // Only replace sets that have imported songs; leave empty sets unchanged
    const merged = { ...liveSets };
    ([1, 2, 3, 4] as const).forEach((n) => { if (newSets[n].length > 0) merged[n] = newSets[n]; });
    setLiveSets(merged);
    ([1, 2, 3, 4] as const).forEach((n) => { if (newSets[n].length > 0) void saveSetToCloud(n, merged[n]); });
    setShowImport(false);
    setImportRows([]);
  }

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <>
    {isDraggingFile && (
      <div className="pointer-events-none fixed inset-0 z-[70] flex items-center justify-center bg-blue-500/10 backdrop-blur-sm">
        <div className="rounded-3xl border-2 border-dashed border-blue-400 bg-white/95 px-12 py-10 text-center shadow-2xl dark:bg-[#1C1C1E]/95">
          <FileText size={40} className="mx-auto mb-3 text-blue-500" />
          <p className="text-lg font-semibold text-[#1C1C1E] dark:text-white">Drop .md files to add songs</p>
          <p className="mt-1 text-sm text-[#3C3C43]/55 dark:text-white/40">One file opens for review; several import together.</p>
        </div>
      </div>
    )}
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
        <aside className="flex w-[30%] shrink-0 flex-col border-r border-black/[0.08] bg-white dark:border-white/[0.08] dark:bg-[#1C1C1E]">

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
            <div className="flex items-center justify-between px-1">
              <span className="text-xs text-[#3C3C43]/45 dark:text-white/30">{songs.length} songs</span>
              <div className="flex items-center gap-2">
                {healthItems.length > 0 && (
                  <button onClick={() => setActiveTab('health')}
                    className="flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 hover:bg-amber-100 dark:bg-amber-500/10 dark:text-amber-400 dark:hover:bg-amber-500/20 transition">
                    <AlertCircle size={11} /> {healthItems.length} issues
                  </button>
                )}
                <button onClick={() => openAddModal('paste')}
                  title="Add a song — paste, blank, or AI"
                  className="flex items-center gap-1 rounded-full bg-blue-500 px-2.5 py-1 text-xs font-semibold text-white hover:bg-blue-400 transition">
                  <Plus size={12} /> Add Song
                </button>
              </div>
            </div>
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
                onClick={() => openAddModal('ai')}
                title="Create a new song with AI"
                className="flex shrink-0 items-center gap-1 rounded-xl border border-black/[0.08] bg-transparent px-2.5 py-2 text-xs font-medium text-[#3C3C43]/50 transition hover:border-purple-400 hover:text-purple-700 dark:border-white/10 dark:text-white/40 dark:hover:border-purple-500/30 dark:hover:text-purple-400"
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

          </div>

          <div className="flex-1 overflow-y-auto p-2">
            {filteredSongs.length === 0 && songs.length === 0 && !isBusy && (
              <div className="mt-8 px-4 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">
                {mode === 'cloud' && !cloudReady ? 'Sign in to load your library.' : 'No songs found.'}
              </div>
            )}
            {filteredSongs.map((song) => {
              const isSelected   = song.path === selectedPath;
              const isUuid       = /^[0-9a-f-]{36}$/i.test(song.metadata.title);
              const issueCount   = healthMap[song.path] || 0;
              const songSets     = songSetMap[song.path] ?? [];
              const inActiveSet  = songSets.includes(activeSetNumber);
              return (
                <div key={song.path} className="group mb-0.5 flex items-stretch gap-0.5">
                  <button
                    onClick={() => { setSelectedPath(song.path); setActiveTab('editor'); }}
                    className={`min-w-0 flex-1 rounded-xl px-2.5 py-2 text-left transition ${
                      isSelected
                        ? 'bg-blue-50 ring-1 ring-blue-300/50 dark:bg-blue-500/10 dark:ring-blue-500/30'
                        : 'hover:bg-black/[0.03] dark:hover:bg-white/[0.04]'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <SongCoverTile path={song.path} title={song.metadata.title} />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1">
                          <span className={`truncate text-sm font-medium leading-tight ${
                            isUuid ? 'font-mono text-xs text-[#3C3C43]/40 dark:text-white/30'
                                 : isSelected ? 'text-blue-700 dark:text-white' : 'text-[#1C1C1E] dark:text-white'
                          }`}>
                            {song.metadata.title || 'Untitled'}
                          </span>
                          {issueCount > 0 && <AlertCircle size={11} className="shrink-0 text-amber-500" />}
                        </div>
                        {!isUuid && (
                          <div className="mt-0.5 flex flex-wrap items-center gap-1">
                            <span className="text-xs text-[#3C3C43]/50 dark:text-white/40">
                              {song.metadata.artist || <span className="italic text-[#3C3C43]/30 dark:text-white/20">No artist</span>}
                            </span>
                            {song.metadata.display_key && (
                              <span className="rounded bg-amber-100 px-1 py-px text-[10px] font-semibold text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                                {song.metadata.display_key}
                              </span>
                            )}
                            {songSets.map((n) => (
                              <span key={n} style={{ background: SET_BADGE_COLORS[n].bg, color: SET_BADGE_COLORS[n].fg }}
                                className="rounded px-1 py-px text-[10px] font-bold">
                                S{n}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  </button>

                  {/* + add to active set */}
                  {!inActiveSet ? (
                    <button
                      onClick={(e) => { e.stopPropagation(); toggleSongInSet(song.path); }}
                      title={`Add to Set ${activeSetNumber}`}
                      className="flex shrink-0 items-center rounded-lg px-1.5 text-lg font-light leading-none text-[#3C3C43]/20 opacity-0 transition hover:bg-blue-50 hover:text-blue-600 group-hover:opacity-100 dark:text-white/15 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                      +
                    </button>
                  ) : (
                    <div className="flex shrink-0 items-center px-1.5 text-xs text-emerald-500/60 dark:text-emerald-400/40">✓</div>
                  )}

                  {/* Quick edit */}
                  <button
                    onClick={(e) => { e.stopPropagation(); openQuickEdit(song); }}
                    title="Quick edit"
                    className="flex shrink-0 items-center rounded-md px-1 text-[#3C3C43]/20 opacity-0 transition hover:bg-black/[0.06] hover:text-[#3C3C43]/70 group-hover:opacity-100 dark:text-white/15 dark:hover:bg-white/10 dark:hover:text-white/60">
                    ✏
                  </button>
                </div>
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
              {([['editor','Song Editor'],['setlist','Sets'],['shows','Shows'],['health','Library Health']] as const).map(([tab, label]) => (
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
            {activeTab === 'setlist' && setSaveStatus && (
              <span className="text-xs font-medium text-emerald-600 dark:text-emerald-400">{setSaveStatus}</span>
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

          {/* ── Sets tab ── */}
          {activeTab === 'setlist' && (
            <div className="flex flex-1 flex-col min-h-0">
              {/* Active set selector */}
              <div className="shrink-0 flex items-center gap-3 border-b border-black/[0.08] bg-[#F9F9FB] px-4 py-2.5 dark:border-white/[0.08] dark:bg-[#0A0A0B]">
                <span className="text-xs font-medium text-[#3C3C43]/40 dark:text-white/30">+ adds to:</span>
                <div className="flex gap-1">
                  {[1, 2, 3, 4].map((n) => (
                    <button
                      key={n}
                      onClick={() => setActiveSetNumber(n)}
                      style={activeSetNumber === n ? { background: SET_BADGE_COLORS[n].bg, color: SET_BADGE_COLORS[n].fg, outline: `1.5px solid ${SET_BADGE_COLORS[n].fg}50` } : {}}
                      className={`rounded-full px-3.5 py-1 text-xs font-semibold transition ${
                        activeSetNumber === n
                          ? 'outline-offset-0'
                          : 'text-[#3C3C43]/40 hover:bg-black/[0.05] hover:text-[#1C1C1E] dark:text-white/30 dark:hover:bg-white/[0.06] dark:hover:text-white/60'
                      }`}
                    >
                      Set {n}
                    </button>
                  ))}
                </div>
                <div className="ml-auto flex items-center gap-2">
                  {currentShowName && (
                    <span className="text-xs font-medium text-[#3C3C43]/50 dark:text-white/40 max-w-[160px] truncate">{currentShowName}</span>
                  )}
                  {setSaveStatus && <span className="text-xs font-medium text-emerald-600 dark:text-emerald-400">{setSaveStatus}</span>}
                  {mode === 'cloud' && (
                    <>
                      <button
                        onClick={openLoadShowPicker}
                        className="flex items-center gap-1.5 rounded-lg border border-black/[0.08] px-3 py-1 text-xs font-medium text-[#3C3C43]/50 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 dark:border-white/[0.08] dark:text-white/30 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                        <FolderOpen size={11} /> Load Show
                      </button>
                      {showSaveShowModal ? (
                        <form
                          className="flex items-center gap-1"
                          onSubmit={(e) => { e.preventDefault(); void handleSaveShow(saveShowNameInput); }}
                        >
                          <input
                            autoFocus
                            type="text"
                            placeholder="Show name…"
                            value={saveShowNameInput}
                            onChange={(e) => setSaveShowNameInput(e.target.value)}
                            onKeyDown={(e) => { if (e.key === 'Escape') setShowSaveShowModal(false); }}
                            onBlur={() => { if (!saveShowNameInput.trim()) setShowSaveShowModal(false); }}
                            className="w-36 rounded-lg border border-emerald-300 bg-white px-2 py-1 text-xs text-[#1C1C1E] outline-none focus:border-emerald-500 dark:border-emerald-500/40 dark:bg-white/[0.06] dark:text-white"
                          />
                          <button type="submit" disabled={!saveShowNameInput.trim()}
                            className="rounded-lg bg-emerald-500 px-2.5 py-1 text-xs font-semibold text-white hover:bg-emerald-600 disabled:opacity-40">
                            Save
                          </button>
                        </form>
                      ) : (
                        <button
                          onClick={() => { setSaveShowNameInput(currentShowName); setShowSaveShowModal(true); }}
                          className="flex items-center gap-1.5 rounded-lg border border-black/[0.08] px-3 py-1 text-xs font-medium text-[#3C3C43]/50 transition hover:border-emerald-300 hover:bg-emerald-50 hover:text-emerald-600 dark:border-white/[0.08] dark:text-white/30 dark:hover:border-emerald-500/40 dark:hover:bg-emerald-500/10 dark:hover:text-emerald-400">
                          <Save size={11} /> Save Show
                        </button>
                      )}
                    </>
                  )}
                  <button
                    onClick={() => importRef.current?.click()}
                    className="flex items-center gap-1.5 rounded-lg border border-black/[0.08] px-3 py-1 text-xs font-medium text-[#3C3C43]/50 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 dark:border-white/[0.08] dark:text-white/30 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                    <Upload size={11} /> Import CSV
                  </button>
                  <input ref={importRef} type="file" accept=".csv,.tsv,text/csv" className="hidden" onChange={handleImportFile} />
                </div>
              </div>

              {/* Set cards */}
              <div className="sets-scroller flex-1 min-h-0 p-4">
                <div className="flex h-full gap-4">
                  {[1, 2, 3, 4].map((n) => {
                    const ids      = liveSets[n] ?? [];
                    const setSongs = ids.map((p) => songs.find((s) => s.path === p)).filter((s): s is SongRecord => Boolean(s));
                    const isActive = n === activeSetNumber;
                    const col      = SET_BADGE_COLORS[n];
                    return (
                      <div
                        key={n}
                        style={isActive ? { borderColor: col.fg + '55', width: '640px' } : { width: '640px' }}
                        className={`flex shrink-0 flex-col overflow-hidden rounded-2xl border bg-white dark:bg-[#1C1C1E] ${
                          isActive ? '' : 'border-black/[0.08] dark:border-white/[0.08]'
                        }`}
                      >
                        {/* Card header */}
                        <div className="shrink-0 flex items-center gap-2.5 border-b border-black/[0.06] px-4 py-3 dark:border-white/[0.06]">
                          <span
                            style={{ background: col.bg, color: col.fg }}
                            className="rounded-full px-2.5 py-0.5 text-xs font-bold"
                          >
                            Set {n}
                          </span>
                          {isActive && (
                            <span className="text-[10px] font-medium text-[#3C3C43]/35 dark:text-white/25">active</span>
                          )}
                          <span className="ml-auto text-xs text-[#3C3C43]/40 dark:text-white/30">
                            {setSongs.length} song{setSongs.length !== 1 ? 's' : ''}
                          </span>
                        </div>

                        {/* Songs list */}
                        <div className="flex-1 overflow-y-auto p-2">
                          {setSongs.length === 0 ? (
                            <p className="py-6 text-center text-sm text-[#3C3C43]/30 dark:text-white/20">
                              Click + next to a song to add it.
                            </p>
                          ) : (
                            <DndContext sensors={dndSensors} collisionDetection={closestCenter}
                              onDragEnd={(event) => {
                                const { active, over } = event;
                                if (!over || active.id === over.id) return;
                                const current = liveSets[n] ?? [];
                                const newIds  = arrayMove(current, current.indexOf(active.id as string), current.indexOf(over.id as string));
                                setLiveSets((prev) => ({ ...prev, [n]: newIds }));
                                void saveSetToCloud(n, newIds);
                              }}
                            >
                              <SortableContext items={ids} strategy={verticalListSortingStrategy}>
                                {setSongs.map((song, i) => (
                                  <SortableSongItem key={song.path} id={song.path} position={i + 1}
                                    title={song.metadata.title} artist={song.metadata.artist} displayKey={song.metadata.display_key}
                                    onRemove={() => removeSongFromSet(song.path, n)} />
                                ))}
                              </SortableContext>
                            </DndContext>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          )}

          {/* ── Shows tab ── */}
          {activeTab === 'shows' && (
            <div className="flex-1 overflow-y-auto p-6">
              <div className="mb-5 flex flex-wrap items-center gap-3">
                <h2 className="text-base font-semibold text-[#1C1C1E] dark:text-white">Saved Shows</h2>
                <span className="text-sm text-[#3C3C43]/40 dark:text-white/30">
                  {showsList.length} saved
                </span>
                {currentShowName && (
                  <span className="flex items-center gap-1.5 rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-blue-600 dark:bg-blue-500/10 dark:text-blue-400">
                    <Cloud size={11} /> Loaded: {currentShowName}
                  </span>
                )}
                <button onClick={() => void refreshShows()} disabled={showsLoading || mode !== 'cloud'}
                  className="ml-auto flex items-center gap-1.5 rounded-lg border border-black/[0.08] px-3 py-1.5 text-xs font-medium text-[#3C3C43]/50 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 disabled:opacity-40 dark:border-white/[0.08] dark:text-white/30 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                  <RefreshCw size={11} className={showsLoading ? 'animate-spin' : ''} /> Refresh
                </button>
              </div>

              {mode !== 'cloud' ? (
                <p className="rounded-2xl border border-black/[0.06] bg-white p-10 text-center text-sm text-[#3C3C43]/45 dark:border-white/[0.06] dark:bg-[#1C1C1E] dark:text-white/40">
                  Shows are stored in the cloud. Switch to <strong>Cloud</strong> mode and sign in to manage them.
                </p>
              ) : showsLoading ? (
                <p className="py-10 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">Loading shows…</p>
              ) : showsList.length === 0 ? (
                <div className="rounded-2xl border border-black/[0.06] bg-white p-10 text-center dark:border-white/[0.06] dark:bg-[#1C1C1E]">
                  <ListMusic size={28} className="mx-auto mb-3 text-[#3C3C43]/30 dark:text-white/20" />
                  <p className="font-semibold text-[#1C1C1E] dark:text-white">No saved shows yet</p>
                  <p className="mx-auto mt-1 max-w-sm text-sm text-[#3C3C43]/45 dark:text-white/40">
                    Build your sets in the <strong>Sets</strong> tab, then hit <strong>Save Show</strong> to name and store the whole night here.
                  </p>
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  {showsList.map((show) => {
                    const isLoaded    = currentShowName === show.name;
                    const isRenaming  = renamingShow === show.name;
                    const isConfirming = confirmDeleteShow === show.name;
                    return (
                      <div key={show.path}
                        style={isLoaded ? { borderColor: SET_BADGE_COLORS[1].fg + '55' } : {}}
                        className={`flex flex-col rounded-2xl border bg-white px-4 py-3 dark:bg-[#1C1C1E] ${
                          isLoaded ? '' : 'border-black/[0.06] dark:border-white/[0.06]'
                        }`}>
                        {/* Title row */}
                        <div className="flex items-start gap-2.5">
                          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-500 dark:bg-blue-500/10 dark:text-blue-400">
                            <ListMusic size={16} />
                          </div>
                          <div className="min-w-0 flex-1">
                            {isRenaming ? (
                              <form onSubmit={(e) => { e.preventDefault(); void handleRenameShow(show.name, renameInput); }}>
                                <input autoFocus value={renameInput} onChange={(e) => setRenameInput(e.target.value)}
                                  onKeyDown={(e) => { if (e.key === 'Escape') setRenamingShow(null); }}
                                  onBlur={() => void handleRenameShow(show.name, renameInput)}
                                  className="w-full rounded-lg border border-blue-300 bg-white px-2 py-1 text-sm text-[#1C1C1E] outline-none focus:border-blue-500 dark:border-blue-500/40 dark:bg-white/[0.06] dark:text-white" />
                              </form>
                            ) : (
                              <>
                                <div className="truncate text-sm font-semibold text-[#1C1C1E] dark:text-white">{show.name}</div>
                                <div className="text-xs text-[#3C3C43]/45 dark:text-white/35">
                                  {show.total} song{show.total !== 1 ? 's' : ''}
                                </div>
                              </>
                            )}
                          </div>
                          {isLoaded && (
                            <span className="shrink-0 rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-bold text-blue-700 dark:bg-blue-500/20 dark:text-blue-400">
                              loaded
                            </span>
                          )}
                        </div>

                        {/* Per-set counts */}
                        <div className="mt-2.5 flex flex-wrap gap-1.5">
                          {[1, 2, 3, 4].map((n) => (
                            <span key={n} style={{ background: SET_BADGE_COLORS[n].bg, color: SET_BADGE_COLORS[n].fg }}
                              className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${show.counts[n - 1] === 0 ? 'opacity-35' : ''}`}>
                              S{n} · {show.counts[n - 1]}
                            </span>
                          ))}
                        </div>

                        {/* Actions */}
                        {isConfirming ? (
                          <div className="mt-3 flex items-center gap-2 border-t border-black/[0.05] pt-2.5 dark:border-white/[0.05]">
                            <span className="flex-1 text-xs text-red-500 dark:text-red-400">Delete "{show.name}"?</span>
                            <button onClick={() => setConfirmDeleteShow(null)}
                              className="rounded-lg border border-black/[0.10] px-2.5 py-1 text-xs font-medium text-[#3C3C43]/60 transition hover:bg-black/[0.04] dark:border-white/[0.10] dark:text-white/50 dark:hover:bg-white/[0.06]">
                              Cancel
                            </button>
                            <button onClick={() => void handleDeleteShow(show.name)}
                              className="rounded-lg bg-red-500 px-2.5 py-1 text-xs font-semibold text-white transition hover:bg-red-400">
                              Delete
                            </button>
                          </div>
                        ) : (
                          <div className="mt-3 flex items-center gap-1.5 border-t border-black/[0.05] pt-2.5 dark:border-white/[0.05]">
                            <button onClick={() => void handleLoadShowFromTab(show.name)}
                              className="flex items-center gap-1.5 rounded-lg bg-blue-500 px-3 py-1 text-xs font-semibold text-white transition hover:bg-blue-400">
                              <FolderOpen size={11} /> Load
                            </button>
                            <button onClick={() => { setRenameInput(show.name); setRenamingShow(show.name); }}
                              title="Rename" className="flex items-center gap-1 rounded-lg border border-black/[0.08] px-2.5 py-1 text-xs font-medium text-[#3C3C43]/50 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 dark:border-white/[0.08] dark:text-white/30 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                              <Pencil size={11} /> Rename
                            </button>
                            <button onClick={() => void handleDuplicateShow(show.name)}
                              title="Duplicate" className="flex items-center gap-1 rounded-lg border border-black/[0.08] px-2.5 py-1 text-xs font-medium text-[#3C3C43]/50 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 dark:border-white/[0.08] dark:text-white/30 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                              <Copy size={11} /> Duplicate
                            </button>
                            <button onClick={() => setConfirmDeleteShow(show.name)}
                              title="Delete" className="ml-auto flex items-center gap-1 rounded-lg border border-red-200 px-2.5 py-1 text-xs font-medium text-red-500 transition hover:bg-red-50 dark:border-red-500/20 dark:text-red-400 dark:hover:bg-red-500/10">
                              <Trash2 size={11} />
                            </button>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* ── Health tab ── */}
          {activeTab === 'health' && (
            <div className="flex-1 overflow-y-auto p-6">
              <div className="mb-5 flex items-center gap-3 flex-wrap">
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
                {healthItems.length > 0 && (() => {
                  const allFixes = healthItems.flatMap(({ song }) => suggestFixes(song).map((fix) => ({ fix, song })));
                  if (allFixes.length === 0) return null;
                  return (
                    <button
                      disabled={isBusy}
                      onClick={async () => {
                        for (const { fix, song } of allFixes) await applyHealthFix(fix, song);
                      }}
                      className="ml-auto flex items-center gap-1.5 rounded-lg bg-blue-500 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-blue-400 disabled:opacity-40">
                      <Wand2 size={12} /> Fix all ({allFixes.length})
                    </button>
                  );
                })()}
              </div>
              {healthItems.length === 0 ? (
                <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-10 text-center dark:border-emerald-500/20 dark:bg-emerald-500/5">
                  <CheckCircle2 size={28} className="mx-auto mb-3 text-emerald-500" />
                  <p className="font-semibold text-emerald-700 dark:text-emerald-400">Library looks great!</p>
                  <p className="mt-1 text-sm text-emerald-600/80 dark:text-emerald-500/70">All {songs.length} songs have title, artist, and key.</p>
                </div>
              ) : (
                <div className="space-y-3">
                  {healthItems.map(({ song, issues }) => {
                    const fixes = suggestFixes(song);
                    return (
                      <div key={song.path}
                        className="rounded-2xl border border-black/[0.06] bg-white px-4 py-3 dark:border-white/[0.06] dark:bg-[#1C1C1E]">
                        <div className="flex items-start justify-between gap-4 mb-2.5">
                          <div className="flex items-center gap-2 min-w-0">
                          <button onClick={() => { setSelectedPath(song.path); setActiveTab('editor'); }} className="text-left min-w-0">
                            <div className="text-sm font-semibold text-blue-600 hover:underline dark:text-blue-400 truncate">
                              {song.metadata.title || <span className="italic text-[#3C3C43]/35 dark:text-white/30">No title</span>}
                            </div>
                            <div className="text-xs text-[#3C3C43]/45 dark:text-white/40">{song.metadata.artist || 'No artist'}</div>
                          </button>
                          <button
                            onClick={() => openQuickEdit(song)}
                            title="Quick edit"
                            className="shrink-0 flex items-center gap-1 rounded-md border border-black/[0.08] bg-white px-2 py-1 text-[11px] font-medium text-[#3C3C43]/50 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600 dark:border-white/[0.08] dark:bg-transparent dark:text-white/30 dark:hover:border-blue-500/40 dark:hover:bg-blue-500/10 dark:hover:text-blue-400">
                            ✏ Edit
                          </button>
                        </div>
                          <div className="flex flex-wrap justify-end gap-1.5 shrink-0">
                            {issues.map((issue) => (
                              <span key={issue} className="rounded-full bg-amber-100 px-2.5 py-0.5 text-[11px] font-medium text-amber-800 dark:bg-amber-500/10 dark:text-amber-400">
                                {issue}
                              </span>
                            ))}
                          </div>
                        </div>
                        {fixes.length > 0 && (
                          <div className="flex flex-wrap gap-1.5 border-t border-black/[0.05] pt-2.5 dark:border-white/[0.05]">
                            {fixes.map((fix) => (
                              <button
                                key={fix.issue}
                                disabled={isBusy}
                                onClick={() => applyHealthFix(fix, song)}
                                className="flex items-center gap-1 rounded-md border border-blue-200 bg-blue-50 px-2.5 py-1 text-[11px] font-medium text-blue-700 transition hover:bg-blue-100 disabled:opacity-40 dark:border-blue-500/20 dark:bg-blue-500/10 dark:text-blue-400 dark:hover:bg-blue-500/20">
                                <Wand2 size={10} /> {fix.label}
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}
        </main>
      </div>
    </div>

    {/* ── Load show picker ── */}
    {showCloudSetPicker && (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowCloudSetPicker(false)}>
        <div className="w-80 rounded-2xl bg-white dark:bg-[#1C1C1E] shadow-2xl overflow-hidden" onClick={(e) => e.stopPropagation()}>
          <div className="flex items-center justify-between border-b border-black/[0.08] dark:border-white/[0.08] px-4 py-3">
            <span className="text-sm font-semibold text-[#1C1C1E] dark:text-white">Load Show</span>
            <button onClick={() => setShowCloudSetPicker(false)} className="rounded p-1 text-[#3C3C43]/40 hover:bg-black/[0.05] dark:text-white/30 dark:hover:bg-white/[0.06]"><X size={14} /></button>
          </div>
          <div className="max-h-72 overflow-y-auto p-2">
            {cloudSetFilesLoading ? (
              <p className="py-6 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">Loading…</p>
            ) : cloudSetFiles.length === 0 ? (
              <p className="py-6 text-center text-sm text-[#3C3C43]/40 dark:text-white/30">No shows found in cloud.</p>
            ) : cloudSetFiles.map((f) => (
              <button key={f.path} onClick={() => handlePickShow(f.name)}
                className="flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-left text-sm text-[#1C1C1E] hover:bg-[#F2F2F7] dark:text-white dark:hover:bg-white/[0.06]">
                <Cloud size={13} className="shrink-0 text-blue-500" />
                {f.name}
              </button>
            ))}
          </div>
        </div>
      </div>
    )}

    {/* ── Add Song modal (paste / blank / AI) ── */}
    {showAddModal && (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
        onClick={(e) => { if (e.target === e.currentTarget) closeAddModal(); }}>
        <div className="flex w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-black/[0.08] bg-white shadow-2xl dark:border-white/[0.10] dark:bg-[#1C1C1E]" style={{ maxHeight: '88vh' }}>

          {/* Header */}
          <div className="flex shrink-0 items-center justify-between border-b border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <h2 className="flex items-center gap-2 text-sm font-semibold text-[#1C1C1E] dark:text-white">
              <Plus size={15} /> Add Song
            </h2>
            <button onClick={closeAddModal}
              className="rounded-lg p-1.5 text-[#3C3C43]/40 hover:bg-black/[0.06] hover:text-[#3C3C43]/80 dark:text-white/30 dark:hover:bg-white/10 dark:hover:text-white/70">
              <X size={15} />
            </button>
          </div>

          {/* Method tabs */}
          <div className="flex shrink-0 gap-1 border-b border-black/[0.06] px-4 pt-3 dark:border-white/[0.06]">
            {([['paste', 'Paste sheet', ClipboardPaste], ['blank', 'From scratch', FileText], ['ai', 'AI generate', Sparkles]] as const).map(([t, label, Icon]) => (
              <button key={t} onClick={() => setAddTab(t)}
                className={`flex items-center gap-1.5 border-b-2 px-3 py-2 text-xs font-medium transition ${
                  addTab === t
                    ? 'border-blue-500 text-blue-600 dark:text-white'
                    : 'border-transparent text-[#3C3C43]/50 hover:text-[#1C1C1E] dark:text-white/40 dark:hover:text-white/70'
                }`}>
                <Icon size={12} /> {label}
              </button>
            ))}
          </div>

          {/* Body */}
          <div className="flex-1 space-y-3 overflow-y-auto p-5">
            {/* Shared metadata */}
            <div className="flex flex-wrap gap-2">
              <input placeholder={addTab === 'paste' ? 'Title (optional)' : 'Song title *'} value={addTitle} onChange={(e) => setAddTitle(e.target.value)}
                className="min-w-[140px] flex-1 rounded-lg border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-sm text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white dark:focus:ring-blue-500/40" />
              <input placeholder={addTab === 'paste' ? 'Artist (optional)' : 'Artist *'} value={addArtist} onChange={(e) => setAddArtist(e.target.value)}
                className="min-w-[120px] flex-1 rounded-lg border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-sm text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white dark:focus:ring-blue-500/40" />
            </div>
            <div className="flex gap-2">
              <label className="flex flex-1 items-center gap-2">
                <span className="text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">Key</span>
                <select value={addKey} onChange={(e) => setAddKey(e.target.value)}
                  className="flex-1 rounded-lg border border-black/[0.08] bg-[#F2F2F7] px-2 py-2 text-sm text-[#1C1C1E] outline-none dark:border-white/[0.08] dark:bg-[#2C2C2E] dark:text-white">
                  {ALL_KEYS.map((k) => <option key={k} value={k}>{k}</option>)}
                </select>
              </label>
              <label className="flex items-center gap-2">
                <span className="text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">Capo</span>
                <input type="number" min={0} max={12} placeholder="—" value={addCapo} onChange={(e) => setAddCapo(e.target.value)}
                  className="w-16 rounded-lg border border-black/[0.08] bg-[#F2F2F7] px-2 py-2 text-sm text-[#1C1C1E] outline-none placeholder:text-[#3C3C43]/30 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white dark:placeholder:text-white/20" />
              </label>
            </div>

            {/* Paste tab */}
            {addTab === 'paste' && (
              <div className="space-y-2">
                <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-dashed border-black/[0.14] bg-[#F2F2F7] px-3 py-2 text-xs text-[#3C3C43]/55 dark:border-white/[0.14] dark:bg-white/[0.04] dark:text-white/45">
                  <FileText size={13} className="text-blue-500" />
                  Drag <strong>.md</strong> files anywhere to load them, or
                  <button onClick={() => mdFileRef.current?.click()}
                    className="font-semibold text-blue-600 hover:underline dark:text-blue-400">browse</button>
                  <input ref={mdFileRef} type="file" accept=".md,.markdown,.txt,text/markdown" multiple className="hidden"
                    onChange={(e) => {
                      const fs = Array.from(e.target.files ?? []);
                      if (fs.length === 1) void loadMdFile(fs[0]);
                      else if (fs.length > 1) void openBulkImport(fs);
                      if (mdFileRef.current) mdFileRef.current.value = '';
                    }} />
                </div>
                <textarea placeholder="Paste a chord sheet here — chords over lyrics, inline, or a full Encore .md all work…"
                  value={addBody} onChange={(e) => setAddBody(e.target.value)} rows={9} spellCheck={false}
                  className="w-full resize-none rounded-lg border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 font-mono text-xs leading-6 text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white/90 dark:focus:ring-blue-500/40" />
                <button onClick={() => void handleCleanupPaste()} disabled={isGenerating || isBusy || !addBody.trim()}
                  className="flex items-center gap-1.5 rounded-lg border border-purple-300 bg-purple-50 px-3 py-1.5 text-xs font-medium text-purple-700 transition hover:bg-purple-100 disabled:opacity-40 dark:border-purple-500/30 dark:bg-purple-500/10 dark:text-purple-400 dark:hover:bg-purple-500/20">
                  {isGenerating ? <><RefreshCw size={12} className="animate-spin" /> Cleaning…</> : <><Wand2 size={12} /> Clean up with AI</>}
                </button>
              </div>
            )}

            {/* Blank tab */}
            {addTab === 'blank' && (
              <p className="rounded-lg bg-[#F2F2F7] px-3 py-3 text-xs text-[#3C3C43]/55 dark:bg-white/[0.04] dark:text-white/40">
                Creates an empty song with this metadata and opens it in the editor. Saved to the cloud immediately.
              </p>
            )}

            {/* AI tab */}
            {addTab === 'ai' && (
              <div className="space-y-2">
                <p className="flex items-center gap-1.5 text-xs font-medium text-purple-700 dark:text-purple-400">
                  <Sparkles size={11} /> ChordSidekick will write a full chart from the title, artist, and key.
                </p>
                <textarea placeholder="Optional: paste existing lyrics/chords or add notes to guide the AI…"
                  value={addBody} onChange={(e) => setAddBody(e.target.value)} rows={5}
                  className="w-full resize-none rounded-lg border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-xs leading-6 text-[#1C1C1E] outline-none focus:ring-2 focus:ring-purple-300 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white/90 dark:focus:ring-purple-500/30" />
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex shrink-0 items-center justify-end gap-2 border-t border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <button onClick={closeAddModal}
              className="rounded-xl border border-black/[0.10] px-4 py-2 text-sm font-medium text-[#3C3C43]/60 transition hover:bg-black/[0.04] dark:border-white/[0.10] dark:text-white/50 dark:hover:bg-white/[0.06]">
              Cancel
            </button>
            {addTab === 'paste' && (
              <button onClick={() => void handleAddPaste()} disabled={isBusy || isGenerating || !addBody.trim()}
                className="flex items-center gap-2 rounded-xl bg-blue-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-400 disabled:opacity-40">
                {isBusy ? <><RefreshCw size={13} className="animate-spin" /> Saving…</> : <><Plus size={14} /> Add Song</>}
              </button>
            )}
            {addTab === 'blank' && (
              <button onClick={() => void handleAddBlank()} disabled={isBusy || !addTitle.trim()}
                className="flex items-center gap-2 rounded-xl bg-blue-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-400 disabled:opacity-40">
                {isBusy ? <><RefreshCw size={13} className="animate-spin" /> Saving…</> : <><Plus size={14} /> Create</>}
              </button>
            )}
            {addTab === 'ai' && (
              <button onClick={() => void handleGenerateAI()} disabled={isBusy || isGenerating || !addTitle.trim() || !addArtist.trim()}
                className="flex items-center gap-2 rounded-xl bg-purple-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-purple-500 disabled:opacity-40">
                {isGenerating ? <><RefreshCw size={13} className="animate-spin" /> Generating…</> : isBusy ? <><RefreshCw size={13} className="animate-spin" /> Saving…</> : <><Sparkles size={14} /> Generate &amp; Save</>}
              </button>
            )}
          </div>
        </div>
      </div>
    )}

    {/* ── Bulk .md import modal ── */}
    {showBulkModal && (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-6"
        onClick={(e) => { if (e.target === e.currentTarget && !bulkBusy) setShowBulkModal(false); }}>
        <div className="flex w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-black/[0.08] bg-white shadow-2xl dark:border-white/10 dark:bg-[#1C1C1E]" style={{ maxHeight: '82vh' }}>

          {/* Header */}
          <div className="flex shrink-0 items-center justify-between border-b border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <div>
              <h2 className="flex items-center gap-2 text-sm font-semibold text-[#1C1C1E] dark:text-white">
                <FileText size={15} /> Import {bulkRows.length} song{bulkRows.length !== 1 ? 's' : ''}
              </h2>
              <p className="mt-0.5 text-xs text-[#3C3C43]/50 dark:text-white/40">
                Metadata is read from each file's front matter. Uncheck any you want to skip.
              </p>
            </div>
            <button onClick={() => { if (!bulkBusy) setShowBulkModal(false); }}
              className="rounded-lg p-1.5 text-[#3C3C43]/40 hover:bg-black/[0.06] disabled:opacity-40 dark:text-white/30 dark:hover:bg-white/10" disabled={bulkBusy}>
              <X size={15} />
            </button>
          </div>

          {/* Rows */}
          <div className="flex-1 overflow-y-auto">
            {bulkRows.map((row, idx) => {
              const missing = !row.meta.title.trim() || UUID_RE.test(row.meta.title);
              return (
                <label key={idx}
                  className={`flex cursor-pointer items-center gap-3 border-b border-black/[0.04] px-5 py-2.5 dark:border-white/[0.04] ${
                    row.include ? '' : 'opacity-45'
                  } ${missing ? 'bg-amber-50/50 dark:bg-amber-500/5' : ''}`}>
                  <input type="checkbox" checked={row.include}
                    onChange={(e) => setBulkRows((rs) => rs.map((r, i) => i === idx ? { ...r, include: e.target.checked } : r))}
                    className="h-4 w-4 shrink-0 accent-blue-500" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="truncate text-sm font-medium text-[#1C1C1E] dark:text-white">
                        {row.meta.title.trim() && !UUID_RE.test(row.meta.title) ? row.meta.title : <span className="italic text-amber-600 dark:text-amber-400">No title in file</span>}
                      </span>
                      {missing && <AlertCircle size={11} className="shrink-0 text-amber-500" />}
                    </div>
                    <div className="flex items-center gap-2 text-xs text-[#3C3C43]/45 dark:text-white/35">
                      <span className="truncate">{row.meta.artist || 'No artist'}</span>
                      <span className="truncate text-[#3C3C43]/30 dark:text-white/20">· {row.name}</span>
                    </div>
                  </div>
                  {row.meta.display_key && (
                    <span className="shrink-0 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                      {row.meta.display_key}
                    </span>
                  )}
                </label>
              );
            })}
          </div>

          {/* Footer */}
          <div className="flex shrink-0 items-center justify-between border-t border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <p className="text-xs text-[#3C3C43]/40 dark:text-white/30">
              {bulkBusy ? bulkProgress : `${bulkRows.filter((r) => r.include).length} of ${bulkRows.length} selected`}
            </p>
            <div className="flex gap-2">
              <button onClick={() => setShowBulkModal(false)} disabled={bulkBusy}
                className="rounded-xl border border-black/[0.10] px-4 py-2 text-sm font-medium text-[#3C3C43]/60 transition hover:bg-black/[0.04] disabled:opacity-40 dark:border-white/10 dark:text-white/50 dark:hover:bg-white/[0.06]">
                Cancel
              </button>
              <button onClick={() => void commitBulkImport()}
                disabled={bulkBusy || bulkRows.filter((r) => r.include).length === 0}
                className="flex items-center gap-2 rounded-xl bg-blue-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-400 disabled:opacity-40">
                {bulkBusy
                  ? <><RefreshCw size={13} className="animate-spin" /> Importing…</>
                  : <><Plus size={14} /> Import {bulkRows.filter((r) => r.include).length} songs</>}
              </button>
            </div>
          </div>
        </div>
      </div>
    )}

    {/* ── Quick Edit modal ── */}
    {quickEditSong && (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
        onClick={(e) => { if (e.target === e.currentTarget) { setQuickEditSong(null); setQeConfirmDelete(false); } }}>
        <div className="w-full max-w-sm rounded-2xl border border-black/[0.08] bg-white shadow-2xl dark:border-white/[0.10] dark:bg-[#1C1C1E]">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <h2 className="text-sm font-semibold text-[#1C1C1E] dark:text-white">Quick Edit</h2>
            <button onClick={() => { setQuickEditSong(null); setQeConfirmDelete(false); }}
              className="rounded-lg p-1.5 text-[#3C3C43]/40 hover:bg-black/[0.06] hover:text-[#3C3C43]/80 dark:text-white/30 dark:hover:bg-white/10 dark:hover:text-white/70">
              <X size={15} />
            </button>
          </div>

          {qeConfirmDelete ? (
            /* Delete confirmation */
            <div className="p-5 text-center">
              <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-500/15">
                <X size={22} className="text-red-500" />
              </div>
              <p className="mb-1 font-semibold text-[#1C1C1E] dark:text-white">Delete from library?</p>
              <p className="mb-5 text-sm text-[#3C3C43]/50 dark:text-white/40">
                "{quickEditSong.metadata.title || 'This song'}" will be permanently removed.
              </p>
              <div className="flex gap-2">
                <button onClick={() => setQeConfirmDelete(false)}
                  className="flex-1 rounded-xl border border-black/[0.10] py-2 text-sm font-medium text-[#3C3C43]/60 transition hover:bg-black/[0.04] dark:border-white/[0.10] dark:text-white/50 dark:hover:bg-white/[0.06]">
                  Cancel
                </button>
                <button onClick={() => deleteSong(quickEditSong)} disabled={isBusy}
                  className="flex-1 rounded-xl bg-red-500 py-2 text-sm font-semibold text-white transition hover:bg-red-400 disabled:opacity-40">
                  {isBusy ? 'Deleting…' : 'Delete'}
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* Fields */}
              <div className="space-y-3 p-5">
                <div>
                  <label className="mb-1 block text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">Title</label>
                  <input type="text" value={qeTitle} onChange={(e) => setQeTitle(e.target.value)} placeholder="Song title"
                    className="w-full rounded-xl border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-sm text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white dark:focus:ring-blue-500/40" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">Artist</label>
                  <input type="text" value={qeArtist} onChange={(e) => setQeArtist(e.target.value)} placeholder="Artist name"
                    className="w-full rounded-xl border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-sm text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white dark:focus:ring-blue-500/40" />
                </div>
                <div>
                  <label className="mb-1 block text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">Key</label>
                  <select value={qeKey} onChange={(e) => setQeKey(e.target.value)}
                    className="w-full rounded-xl border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-sm text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-[#2C2C2E] dark:text-white dark:focus:ring-blue-500/40">
                    <option value="">— Select key —</option>
                    {['C','C#','Db','D','D#','Eb','E','F','F#','Gb','G','G#','Ab','A','A#','Bb','B',
                      'Cm','C#m','Dm','D#m','Ebm','Em','Fm','F#m','Gm','G#m','Am','A#m','Bbm','Bm',
                    ].map((k) => <option key={k} value={k}>{k}</option>)}
                  </select>
                </div>
                {/* BPM */}
                <div>
                  <label className="mb-1 block text-xs font-medium text-[#3C3C43]/50 dark:text-white/40">BPM</label>
                  <input type="number" min={40} max={300} value={qeBpm} onChange={(e) => setQeBpm(e.target.value)} placeholder="e.g. 120"
                    className="w-full rounded-xl border border-black/[0.08] bg-[#F2F2F7] px-3 py-2 text-sm text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-400/50 dark:border-white/[0.08] dark:bg-white/[0.06] dark:text-white dark:focus:ring-blue-500/40" />
                </div>
                {/* Lead guitar toggle */}
                <button onClick={() => setQeIsLead((v) => !v)}
                  className="flex w-full items-center justify-between rounded-xl border border-black/[0.06] bg-[#F2F2F7] px-3 py-2.5 dark:border-white/[0.06] dark:bg-white/[0.04]">
                  <span className="text-sm text-[#1C1C1E] dark:text-white">Lead guitar</span>
                  <div style={{ width: 51, height: 28, backgroundColor: qeIsLead ? '#22c55e' : '#E5E5EA', flexShrink: 0 }}
                    className="relative rounded-full transition-colors duration-200">
                    <span style={{ position: 'absolute', top: 2, left: qeIsLead ? 25 : 2, width: 24, height: 24, transition: 'left 0.2s' }}
                      className="rounded-full bg-white shadow-md" />
                  </div>
                </button>
                {/* Capo toggle + fret */}
                <div className="rounded-xl border border-black/[0.06] bg-[#F2F2F7] px-3 py-2.5 dark:border-white/[0.06] dark:bg-white/[0.04]">
                  <button onClick={() => setQeCapo((v) => !v)} className="flex w-full items-center justify-between">
                    <span className="text-sm text-[#1C1C1E] dark:text-white">Capo</span>
                    <div style={{ width: 51, height: 28, backgroundColor: qeCapo ? '#f59e0b' : '#E5E5EA', flexShrink: 0 }}
                      className="relative rounded-full transition-colors duration-200">
                      <span style={{ position: 'absolute', top: 2, left: qeCapo ? 25 : 2, width: 24, height: 24, transition: 'left 0.2s' }}
                        className="rounded-full bg-white shadow-md" />
                    </div>
                  </button>
                  {qeCapo && (
                    <div className="mt-3 flex items-center gap-3">
                      <span className="text-xs text-[#3C3C43]/50 dark:text-white/40">Fret</span>
                      <div className="flex items-center gap-3">
                        <button onClick={() => setQeCapoFret((f) => Math.max(1, f - 1))}
                          className="flex h-8 w-8 items-center justify-center rounded-xl border border-black/[0.10] bg-white text-base font-bold text-[#1C1C1E] hover:bg-black/[0.06] dark:border-white/[0.10] dark:bg-white/[0.08] dark:text-white dark:hover:bg-white/[0.14]">−</button>
                        <span className="w-5 text-center text-lg font-bold text-[#1C1C1E] dark:text-white">{qeCapoFret}</span>
                        <button onClick={() => setQeCapoFret((f) => Math.min(12, f + 1))}
                          className="flex h-8 w-8 items-center justify-center rounded-xl border border-black/[0.10] bg-white text-base font-bold text-[#1C1C1E] hover:bg-black/[0.06] dark:border-white/[0.10] dark:bg-white/[0.08] dark:text-white dark:hover:bg-white/[0.14]">+</button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
              {/* Actions */}
              <div className="flex items-center gap-2 border-t border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
                <button onClick={() => setQeConfirmDelete(true)}
                  className="rounded-xl border border-red-200 px-3 py-2 text-sm font-medium text-red-500 transition hover:bg-red-50 dark:border-red-500/20 dark:text-red-400 dark:hover:bg-red-500/10">
                  Delete
                </button>
                <div className="flex flex-1 gap-2">
                  <button onClick={() => { setQuickEditSong(null); setQeConfirmDelete(false); }}
                    className="flex-1 rounded-xl border border-black/[0.10] py-2 text-sm font-medium text-[#3C3C43]/60 transition hover:bg-black/[0.04] dark:border-white/[0.10] dark:text-white/50 dark:hover:bg-white/[0.06]">
                    Cancel
                  </button>
                  <button onClick={saveQuickEdit} disabled={isBusy}
                    className="flex-1 rounded-xl bg-blue-500 py-2 text-sm font-semibold text-white transition hover:bg-blue-400 disabled:opacity-40">
                    {isBusy ? 'Saving…' : 'Save'}
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    )}

    {/* ── CSV Import modal ── */}
    {showImport && (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-6"
        onClick={(e) => { if (e.target === e.currentTarget) setShowImport(false); }}>
        <div className="flex w-full max-w-2xl flex-col overflow-hidden rounded-2xl border border-black/[0.08] bg-white shadow-2xl dark:border-white/10 dark:bg-[#1C1C1E]" style={{ maxHeight: '80vh' }}>

          {/* Header */}
          <div className="shrink-0 flex items-center justify-between border-b border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <div>
              <h2 className="text-sm font-semibold text-[#1C1C1E] dark:text-white">Import Preview</h2>
              <p className="mt-0.5 text-xs text-[#3C3C43]/50 dark:text-white/40">
                {importRows.filter((r) => r.confidence === 'high').length} matched ·{' '}
                {importRows.filter((r) => r.confidence === 'fuzzy').length} fuzzy ·{' '}
                {importRows.filter((r) => r.confidence === 'none').length} unmatched
              </p>
            </div>
            <button onClick={() => setShowImport(false)}
              className="rounded-lg p-1.5 text-[#3C3C43]/40 hover:bg-black/[0.06] dark:text-white/30 dark:hover:bg-white/10">
              <X size={15} />
            </button>
          </div>

          {/* Rows */}
          <div className="flex-1 overflow-y-auto">
            {/* Column headers */}
            <div className="sticky top-0 grid grid-cols-[3rem_1fr_1fr_3rem] gap-2 border-b border-black/[0.05] bg-[#F9F9FB] px-5 py-2 text-[10px] font-semibold uppercase tracking-widest text-[#3C3C43]/40 dark:border-white/[0.05] dark:bg-[#0A0A0B] dark:text-white/30">
              <span>Set</span><span>From sheet</span><span>Matched to</span><span></span>
            </div>

            {importRows.map((row, idx) => {
              const isHigh  = row.confidence === 'high';
              const isFuzzy = row.confidence === 'fuzzy';
              const isNone  = row.confidence === 'none';
              return (
                <div key={idx} className={`grid grid-cols-[3rem_1fr_1fr_3rem] items-center gap-2 border-b border-black/[0.04] px-5 py-2.5 dark:border-white/[0.04] ${
                  isHigh  ? '' :
                  isFuzzy ? 'bg-amber-50/60 dark:bg-amber-500/5' :
                            'bg-red-50/60 dark:bg-red-500/5'
                }`}>
                  {/* Set badge */}
                  <span style={{ background: SET_BADGE_COLORS[row.set]?.bg, color: SET_BADGE_COLORS[row.set]?.fg }}
                    className="w-fit rounded-full px-2 py-px text-[10px] font-bold">
                    S{row.set}
                  </span>

                  {/* Sheet title/artist */}
                  <div className="min-w-0">
                    <div className="truncate text-sm font-medium text-[#1C1C1E] dark:text-white">{row.csvTitle}</div>
                    {row.csvArtist && <div className="truncate text-xs text-[#3C3C43]/45 dark:text-white/35">{row.csvArtist}</div>}
                  </div>

                  {/* Match / picker */}
                  {isHigh ? (
                    <div className="min-w-0">
                      <div className="truncate text-sm text-emerald-700 dark:text-emerald-400">{row.matched?.metadata.title}</div>
                      <div className="truncate text-xs text-[#3C3C43]/40 dark:text-white/30">{row.matched?.metadata.artist}</div>
                    </div>
                  ) : (
                    <select
                      value={row.override}
                      onChange={(e) => setImportRows((rows) => rows.map((r, i) => i === idx ? { ...r, override: e.target.value } : r))}
                      className="w-full rounded-lg border border-black/[0.08] bg-white px-2 py-1.5 text-xs text-[#1C1C1E] outline-none focus:ring-2 focus:ring-blue-300 dark:border-white/10 dark:bg-white/5 dark:text-white">
                      <option value="">— skip —</option>
                      {songs.map((s) => (
                        <option key={s.path} value={s.path}>
                          {s.metadata.title}{s.metadata.artist ? ` — ${s.metadata.artist}` : ''}
                        </option>
                      ))}
                    </select>
                  )}

                  {/* Indicator */}
                  <span className={`text-center text-base ${isHigh ? 'text-emerald-500' : isFuzzy ? 'text-amber-400' : 'text-red-400'}`}>
                    {isHigh ? '✓' : isFuzzy ? '~' : '?'}
                  </span>
                </div>
              );
            })}
          </div>

          {/* Footer */}
          <div className="shrink-0 flex items-center justify-between border-t border-black/[0.06] px-5 py-4 dark:border-white/[0.06]">
            <p className="text-xs text-[#3C3C43]/40 dark:text-white/30">
              {importRows.filter((r) => r.override).length} of {importRows.length} songs will be imported
            </p>
            <div className="flex gap-2">
              <button onClick={() => setShowImport(false)}
                className="rounded-xl border border-black/[0.10] px-4 py-2 text-sm font-medium text-[#3C3C43]/60 transition hover:bg-black/[0.04] dark:border-white/10 dark:text-white/50 dark:hover:bg-white/[0.06]">
                Cancel
              </button>
              <button onClick={commitImport}
                disabled={importRows.filter((r) => r.override).length === 0}
                className="rounded-xl bg-blue-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-400 disabled:opacity-40">
                Import {importRows.filter((r) => r.override).length} songs
              </button>
            </div>
          </div>
        </div>
      </div>
    )}

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
