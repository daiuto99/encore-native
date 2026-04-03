package com.encore.core.data.transfer

/**
 * Top-level container for an Encore set export file (.encore.json).
 *
 * JSON shape:
 * {
 *   "version": 1,
 *   "name": "Friday Night",
 *   "songs": [
 *     { "title": "...", "artist": "...", "displayKey": "Am", "markdownBody": "..." },
 *     ...
 *   ]
 * }
 *
 * Serialization is handled via org.json in LibraryViewModel — no extra dependency required.
 */
data class EncoreSetExport(
    val version: Int,
    val name: String,
    val songs: List<EncoreSongExport>
)

/**
 * A single song entry within an export.
 * Contains all data needed to recreate the song in a fresh Encore install.
 */
data class EncoreSongExport(
    val title: String,
    val artist: String,
    val displayKey: String?,
    val markdownBody: String
)
