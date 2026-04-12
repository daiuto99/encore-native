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
        // Sections that are recognised as valid in the Encore chart schema.
        private val KNOWN_SECTIONS = setOf(
            "intro", "verse", "chorus", "pre-chorus", "prechorus", "pre chorus",
            "bridge", "outro", "solo", "interlude", "instrumental", "key", "tag",
            "vamp", "breakdown", "hook", "refrain", "coda", "turnaround"
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
                // Strip trailing numbers/spaces so "Verse 1" normalises to "verse"
                val normalised = raw.lowercase()
                    .replace(Regex("""[\s\d]+$"""), "")
                    .trim()
                val recognised = KNOWN_SECTIONS.any { section ->
                    normalised == section || normalised.startsWith(section)
                }
                if (!recognised && raw.isNotBlank()) {
                    errors += "Non-standard section: \"$raw\""
                }
            }

            return errors
        }
    }
}
