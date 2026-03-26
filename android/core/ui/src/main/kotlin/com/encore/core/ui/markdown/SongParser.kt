package com.encore.core.ui.markdown

/**
 * Simple data class representing a parsed song with metadata.
 *
 * This is a simplified version for the Milestone 1 spike.
 * Full Room entity will be implemented in core:data module in Milestone 2.
 */
data class ParsedSong(
    val title: String,
    val artist: String,
    val key: String?,
    val markdownBody: String,
    val fullMarkdown: String
)

/**
 * Parses markdown song files with YAML front matter.
 *
 * Expected format:
 * ```
 * ---
 * title: Song Title
 * artist: Artist Name
 * key: G
 * ---
 *
 * # Verse 1
 * [lyrics and chords]
 * ```
 *
 * For Milestone 1 spike - simple string parsing.
 * Future: Consider using a YAML library for robustness.
 */
object SongParser {

    private val YAML_FRONT_MATTER_REGEX = Regex(
        """^---\s*\n(.*?)\n---\s*\n(.*)$""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * Parse a markdown song file.
     *
     * @param markdown The full markdown content including YAML front matter
     * @return ParsedSong with extracted metadata and body
     */
    fun parse(markdown: String): ParsedSong {
        val matchResult = YAML_FRONT_MATTER_REGEX.find(markdown)

        return if (matchResult != null) {
            val frontMatter = matchResult.groupValues[1]
            val body = matchResult.groupValues[2].trim()

            val metadata = parseFrontMatter(frontMatter)

            ParsedSong(
                title = metadata["title"] ?: "Untitled",
                artist = metadata["artist"] ?: "Unknown Artist",
                key = metadata["key"],
                markdownBody = body,
                fullMarkdown = markdown
            )
        } else {
            // No YAML front matter, treat entire content as body
            ParsedSong(
                title = "Untitled",
                artist = "Unknown Artist",
                key = null,
                markdownBody = markdown,
                fullMarkdown = markdown
            )
        }
    }

    /**
     * Parse simple YAML front matter into key-value pairs.
     *
     * Example:
     * ```
     * title: Amazing Grace
     * artist: John Newton
     * key: G
     * ```
     *
     * @param frontMatter The YAML content between --- markers
     * @return Map of metadata key-value pairs
     */
    private fun parseFrontMatter(frontMatter: String): Map<String, String> {
        return frontMatter
            .lines()
            .mapNotNull { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    key to value
                } else {
                    null
                }
            }
            .toMap()
    }
}
