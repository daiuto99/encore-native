package com.encore.tablet.audit

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.encore.core.data.db.EncoreDatabase
import com.encore.core.data.entities.SongEntity

/**
 * WorkManager worker that audits every song in the library for common chart issues.
 *
 * Checks performed per song:
 *  1. Missing mandatory metadata — title, artist, key.
 *  2. Unclosed [h] harmony tags — open count must match close count.
 *  3. Non-standard section headers — any `# Header` or `## Header` that does not
 *     match the canonical section vocabulary.
 *
 * Results are written back to the `songs` table via [updateValidation]:
 *  - `is_verified = true`  + `validation_errors = null`  → song passed
 *  - `is_verified = false` + `validation_errors = "..."` → song has issues
 *
 * Enqueue via [LibraryAuditViewModel.runScan].
 */
class LibraryAuditWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = EncoreDatabase.getDatabase(applicationContext).songDao()
        val songs = dao.getAllSongsOnce()
        val now = System.currentTimeMillis()
        songs.forEach { song ->
            val errors = auditSong(song)
            dao.updateValidation(
                id = song.id,
                isVerified = errors.isEmpty(),
                errors = if (errors.isEmpty()) null else errors.joinToString(" • "),
                timestamp = now
            )
        }
        return Result.success()
    }

    companion object {
        // Section-name *roots* recognised as valid. A header is accepted when any of its
        // words begins with one of these — so multi-word band-chart labels like
        // "Guitar Solo", "Final Chorus", "Out Chorus", "Fade Out" and "Post-Chorus" pass
        // instead of being flagged. Keep this generous: missing title/artist/key and
        // unclosed [h] tags are the issues worth a performer's attention, not section wording.
        private val KNOWN_SECTIONS = setOf(
            // Core song sections
            "intro", "verse", "chorus", "pre", "prechorus", "post", "postchorus",
            "bridge", "outro", "solo", "interlude", "instrumental", "key", "tag",
            "vamp", "breakdown", "hook", "refrain", "coda", "turnaround",
            // Common band-chart labels and modifiers
            "guitar", "final", "end", "ending", "break", "link", "fade", "riff",
            "lick", "fill", "chord", "voicing", "ride", "build", "drop", "channel",
            "reprise", "segue", "spoken", "out", "first", "last", "lead", "rhythm",
            "harmony", "harmonies", "count", "ramp", "button", "stop", "hit", "false",
        )

        // Markdown plain headers: `# Verse 1` or `## Chorus`
        private val MD_HEADER_RE = Regex("""^#{1,2}\s+(.+)$""", RegexOption.MULTILINE)

        // ChordSidekick span headers: `<span style="...">## Verse 1</span>`
        private val SPAN_HEADER_RE = Regex(
            """<span[^>]*?>##?\s*(.*?)</span>""",
            RegexOption.IGNORE_CASE
        )

        private val OPEN_H_RE = Regex("""\[h]""", RegexOption.IGNORE_CASE)
        private val CLOSE_H_RE = Regex("""\[/h]""", RegexOption.IGNORE_CASE)

        // Alphabetic word tokens within a section header (digits/punctuation dropped).
        private val WORD_RE = Regex("""[a-z]+""")

        fun auditSong(song: SongEntity): List<String> {
            val errors = mutableListOf<String>()
            val body = song.markdownBody

            // ── 1. Mandatory metadata ─────────────────────────────────────────
            if (song.title.isBlank()) errors += "Missing title"
            if (song.artist.isBlank() || song.artist.equals("Unknown Artist", ignoreCase = true)) {
                errors += "Missing artist"
            }
            if (song.displayKey == null) errors += "Missing key"

            // ── 2. Unclosed [h] tags ──────────────────────────────────────────
            val openCount = OPEN_H_RE.findAll(body).count()
            val closeCount = CLOSE_H_RE.findAll(body).count()
            if (openCount != closeCount) {
                errors += "Unclosed [h] tag ($openCount open, $closeCount closed)"
            }

            // ── 3. Non-standard section headers ───────────────────────────────
            // Collect all header text from both span and plain markdown formats.
            val headerTexts = buildList {
                SPAN_HEADER_RE.findAll(body).forEach { add(it.groupValues[1].trim()) }
                MD_HEADER_RE.findAll(body).forEach { add(it.groupValues[1].trim()) }
            }
            headerTexts.forEach { raw ->
                if (raw.isBlank()) return@forEach
                // Split into alphabetic word tokens ("Guitar Solo" → [guitar, solo],
                // "Post-Chorus" → [post, chorus], "Solo 2" → [solo]). A header is recognised
                // when ANY token starts with a known section root, covering the many valid
                // modifier-prefixed labels real charts use.
                val tokens = WORD_RE.findAll(raw.lowercase()).map { it.value }.toList()
                val recognised = tokens.isEmpty() || tokens.any { token ->
                    KNOWN_SECTIONS.any { section -> token.startsWith(section) }
                }
                if (!recognised) {
                    errors += "Non-standard section: \"$raw\""
                }
            }

            return errors
        }
    }
}
