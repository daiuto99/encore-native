package com.encore.feature.performance

/**
 * Chord transposition utilities.
 *
 * Shifts chord names by a number of semitones while preserving enharmonic spelling
 * consistent with the target key:
 *   - Sharp keys  (G D A E B F# C#)  → sharps (C# D# F# G# A#)
 *   - Flat keys   (F Bb Eb Ab Db Gb) → flats  (Db Eb Gb Ab Bb)
 *   - C major / no key               → sharps
 *
 * Only chord-line rows are transposed. Lyric lines, section headers, and
 * blank lines pass through unchanged. A chord line is defined as a line
 * whose non-whitespace content consists entirely of chord tokens and
 * separator characters (| / spaces).
 */
object TranspositionUtils {

    // Semitone index for each note name — used for interval calculation
    private val NOTE_TO_SEMITONE = mapOf(
        "C" to 0, "C#" to 1, "Db" to 1,
        "D" to 2, "D#" to 3, "Eb" to 3,
        "E" to 4,
        "F" to 5, "F#" to 6, "Gb" to 6,
        "G" to 7, "G#" to 8, "Ab" to 8,
        "A" to 9, "A#" to 10, "Bb" to 10,
        "B" to 11
    )

    private val SHARP_SCALE = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_SCALE  = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** Keys that use flat spelling for their chromatic notes. */
    private val FLAT_KEYS = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm")

    /**
     * Returns the semitone distance to transpose from [originalKey] to [displayKey].
     * Returns 0 if either key is null/blank or if they are the same.
     */
    fun semitoneShift(originalKey: String?, displayKey: String?): Int {
        if (originalKey.isNullOrBlank() || displayKey.isNullOrBlank()) return 0
        val orig = parseKeyRoot(originalKey) ?: return 0
        val disp = parseKeyRoot(displayKey) ?: return 0
        val origSemi = NOTE_TO_SEMITONE[orig] ?: return 0
        val dispSemi = NOTE_TO_SEMITONE[disp] ?: return 0
        return ((dispSemi - origSemi) + 12) % 12
    }

    /**
     * Transpose all chord lines in a markdown body.
     *
     * @param body         Full markdown content of the song
     * @param semitones    Number of semitones to shift (0 = no-op)
     * @param targetKey    The key the output will be in (drives sharp/flat choice)
     * @return             The body with chord lines transposed; all other lines unchanged
     */
    fun transposeBody(body: String, semitones: Int, targetKey: String?): String {
        if (semitones == 0) return body
        val useFlats = useFlatSpelling(targetKey)
        return body.lines().joinToString("\n") { line ->
            if (isChordLine(line)) transposeChordLine(line, semitones, useFlats) else line
        }
    }

    // ── Chord-line detection ──────────────────────────────────────────────────

    /**
     * A line is a chord line if every non-whitespace token is a valid chord symbol
     * or a separator character (| /).
     *
     * Examples of chord lines:
     *   "  G   D   Em   C  "
     *   "| Am  | F  | C  | G |"
     *   "Bm7  F#  A/E"
     *
     * Examples of non-chord lines:
     *   "Verse 1"
     *   "Amazing grace, how sweet the sound"
     *   "[chorus]"
     */
    fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        // Strip separator characters and split on whitespace
        val tokens = trimmed.replace("|", " ").replace("/", " ").trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return false
        return tokens.all { token -> token.isEmpty() || CHORD_PATTERN.matches(token) }
    }

    // ── Internal implementation ───────────────────────────────────────────────

    private fun transposeChordLine(line: String, semitones: Int, useFlats: Boolean): String {
        // Replace each chord token in-place, preserving surrounding whitespace and separators
        return CHORD_TOKEN_REGEX.replace(line) { match ->
            transposeChord(match.value, semitones, useFlats)
        }
    }

    /**
     * Transpose a single chord symbol (e.g. "Am7", "F#maj7", "Bb/D").
     * Handles slash chords by transposing both the root and the bass note.
     */
    private fun transposeChord(chord: String, semitones: Int, useFlats: Boolean): String {
        // Handle slash chord (e.g. "C/E" or "Am/G")
        val slashIdx = chord.indexOf('/')
        return if (slashIdx > 0) {
            val rootPart = chord.substring(0, slashIdx)
            val bassPart = chord.substring(slashIdx + 1)
            "${transposeRoot(rootPart, semitones, useFlats)}/${transposeRoot(bassPart, semitones, useFlats)}"
        } else {
            transposeRoot(chord, semitones, useFlats)
        }
    }

    /**
     * Transpose the root of a chord, preserving the suffix (quality, extensions).
     * E.g. "Am7" → root "A", suffix "m7" → "Bm7" (shifted by 2)
     */
    private fun transposeRoot(chord: String, semitones: Int, useFlats: Boolean): String {
        if (chord.isEmpty()) return chord
        // Extract root note (1 or 2 characters: note + optional # or b)
        val root = extractRoot(chord) ?: return chord
        val suffix = chord.removePrefix(root)
        val origSemi = NOTE_TO_SEMITONE[root] ?: return chord
        val newSemi = (origSemi + semitones + 12) % 12
        val scale = if (useFlats) FLAT_SCALE else SHARP_SCALE
        return scale[newSemi] + suffix
    }

    /** Extract the note root from the start of a chord string (e.g. "Am7" → "A", "F#7" → "F#", "Bb" → "Bb"). */
    private fun extractRoot(chord: String): String? {
        if (chord.isEmpty()) return null
        val base = chord[0].uppercaseChar()
        if (base !in 'A'..'G') return null
        return when {
            chord.length > 1 && chord[1] == '#' -> "${base}#"
            chord.length > 1 && chord[1] == 'b' && (chord.length < 3 || chord[2].isUpperCase() || !chord[2].isLetter()) -> "${base}b"
            else -> base.toString()
        }
    }

    /** Determine whether the target key uses flat spelling. */
    private fun useFlatSpelling(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val root = parseKeyRoot(key) ?: return false
        return root in FLAT_KEYS || key.trimEnd() in FLAT_KEYS
    }

    /** Extract just the root note from a key string (e.g. "F#m" → "F#", "Bb" → "Bb"). */
    private fun parseKeyRoot(key: String): String? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return null
        val base = trimmed[0].uppercaseChar()
        if (base !in 'A'..'G') return null
        return when {
            trimmed.length > 1 && trimmed[1] == '#' -> "${base}#"
            trimmed.length > 1 && trimmed[1] == 'b' -> "${base}b"
            else -> base.toString()
        }
    }

    // ── Regex patterns ────────────────────────────────────────────────────────

    /**
     * Matches a complete chord token including quality and extensions.
     * Handles: Am, F#m7, Bbmaj7, Gsus4, Dadd9, C/E, Bb/D, etc.
     * Does NOT match plain words like "Verse" or "Em-something-invalid".
     */
    private val CHORD_PATTERN = Regex(
        """^[A-G][#b]?(m|maj|min|aug|dim|sus|add|dom)?[0-9]?(maj[0-9]|m[0-9]|[0-9]+)?$"""
    )

    /**
     * Matches chord tokens within a line (for in-place replacement).
     * Anchored to word boundaries so it doesn't match partial words.
     */
    private val CHORD_TOKEN_REGEX = Regex(
        """[A-G][#b]?(?:m(?:aj)?|maj|min|aug|dim|sus|add)?[0-9]*(?:/[A-G][#b]?)?"""
    )
}
