package com.encore.feature.performance

import org.junit.Assert.*
import org.junit.Test

class TranspositionUtilsTest {

    // ── semitoneShift ─────────────────────────────────────────────────────────

    @Test fun `semitoneShift returns 0 for null original`() =
        assertEquals(0, TranspositionUtils.semitoneShift(null, "G"))

    @Test fun `semitoneShift returns 0 for null display`() =
        assertEquals(0, TranspositionUtils.semitoneShift("C", null))

    @Test fun `semitoneShift returns 0 for blank keys`() =
        assertEquals(0, TranspositionUtils.semitoneShift("", "  "))

    @Test fun `semitoneShift returns 0 for same key`() =
        assertEquals(0, TranspositionUtils.semitoneShift("G", "G"))

    @Test fun `semitoneShift C to G is 7`() =
        assertEquals(7, TranspositionUtils.semitoneShift("C", "G"))

    @Test fun `semitoneShift G to C is 5`() =
        assertEquals(5, TranspositionUtils.semitoneShift("G", "C"))

    @Test fun `semitoneShift C to Db is 1`() =
        assertEquals(1, TranspositionUtils.semitoneShift("C", "Db"))

    @Test fun `semitoneShift wraps around octave`() {
        // A(9) -> G(7): (7-9+12)%12 = 10
        assertEquals(10, TranspositionUtils.semitoneShift("A", "G"))
    }

    @Test fun `semitoneShift handles flat original key`() {
        // Bb(10) -> C(0): (0-10+12)%12 = 2
        assertEquals(2, TranspositionUtils.semitoneShift("Bb", "C"))
    }

    @Test fun `semitoneShift handles sharp original key`() {
        // F#(6) -> B(11): (11-6)%12 = 5
        assertEquals(5, TranspositionUtils.semitoneShift("F#", "B"))
    }

    @Test fun `semitoneShift handles minor key roots`() {
        // Am -> Em: A(9) -> E(4): (4-9+12)%12 = 7
        assertEquals(7, TranspositionUtils.semitoneShift("Am", "Em"))
    }

    @Test fun `semitoneShift all 12 chromatic roots up 7 semitones`() {
        // Verify every root can be shifted by a known interval
        val roots = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        for (i in roots.indices) {
            val from = roots[i]
            val to   = roots[(i + 7) % 12]
            assertEquals("$from to $to should be 7", 7, TranspositionUtils.semitoneShift(from, to))
        }
    }

    @Test fun `semitoneShift all 12 chromatic flat roots up 7 semitones`() {
        val flatRoots  = listOf("C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B")
        val sharpRoots = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        for (i in flatRoots.indices) {
            val from = flatRoots[i]
            val to   = sharpRoots[(i + 7) % 12]
            assertEquals("$from to $to should be 7", 7, TranspositionUtils.semitoneShift(from, to))
        }
    }

    // ── isChordLine ───────────────────────────────────────────────────────────

    @Test fun `isChordLine true for simple chord row`() =
        assertTrue(TranspositionUtils.isChordLine("  G   D   Em   C  "))

    @Test fun `isChordLine true for pipe-separated chords`() =
        assertTrue(TranspositionUtils.isChordLine("| Am  | F  | C  | G |"))

    @Test fun `isChordLine true for chords with extensions`() =
        assertTrue(TranspositionUtils.isChordLine("Bm7  Gmaj7  Dmaj7  A"))

    @Test fun `isChordLine true for slash chord in line`() =
        assertTrue(TranspositionUtils.isChordLine("C  G  Am  F/C"))

    @Test fun `isChordLine true for single chord`() =
        assertTrue(TranspositionUtils.isChordLine("Am"))

    @Test fun `isChordLine false for lyric line`() =
        assertFalse(TranspositionUtils.isChordLine("Amazing grace, how sweet the sound"))

    @Test fun `isChordLine false for section header`() =
        assertFalse(TranspositionUtils.isChordLine("[Chorus]"))

    @Test fun `isChordLine false for blank line`() =
        assertFalse(TranspositionUtils.isChordLine(""))

    @Test fun `isChordLine false for whitespace only`() =
        assertFalse(TranspositionUtils.isChordLine("   "))

    @Test fun `isChordLine false for Verse 1`() =
        assertFalse(TranspositionUtils.isChordLine("Verse 1"))

    @Test fun `isChordLine false for lyrics with accidental chord-like words`() =
        assertFalse(TranspositionUtils.isChordLine("Before the morning"))

    // ── transposeBody ─────────────────────────────────────────────────────────

    @Test fun `transposeBody with 0 semitones is identity`() {
        val body = "G  D  Em  C\nAmazing grace\n"
        assertEquals(body, TranspositionUtils.transposeBody(body, 0, "G"))
    }

    @Test fun `transposeBody round-trip 12 semitones is identity`() {
        val body = "C  G  Am  F\nsome lyric"
        // 12 semitones = full octave, should return identical chord names
        assertEquals(body, TranspositionUtils.transposeBody(body, 12, "C"))
    }

    @Test fun `transposeBody transposes chord lines only`() {
        // C→D, G→A, Am→Bm, F→G  (2 semitones, D = sharp key)
        val body = "C  G  Am  F\nhow sweet the sound\n"
        val result = TranspositionUtils.transposeBody(body, 2, "D")
        assertEquals("D  A  Bm  G\nhow sweet the sound\n", result)
    }

    @Test fun `transposeBody uses flat spelling for flat target key`() {
        // 10 semitones up, target Bb (flat key)
        // F(5)+10=3=Eb, G(7)+10=5=F, Am→Gm, C(0)+10=10=Bb
        val body = "F  G  Am  C"
        val result = TranspositionUtils.transposeBody(body, 10, "Bb")
        assertEquals("Eb  F  Gm  Bb", result)
    }

    @Test fun `transposeBody uses sharp spelling for sharp target key`() {
        // 7 semitones up, target G (sharp key)
        // C→G, F→C, Am→Em, Bb→F
        val body = "C  F  Am  Bb"
        val result = TranspositionUtils.transposeBody(body, 7, "G")
        assertEquals("G  C  Em  F", result)
    }

    @Test fun `transposeBody handles slash chords`() {
        // 2 semitones up, target E (sharp key)
        // D→E, D/F#→E/G#, G→A
        val body = "D  D/F#  G"
        val result = TranspositionUtils.transposeBody(body, 2, "E")
        assertEquals("E  E/G#  A", result)
    }

    @Test fun `transposeBody preserves lyric lines unchanged`() {
        val body = "G  D\nhow sweet the sound\nEm  C\nthat saved a wretch like me"
        // 2 semitones up, target D (sharp): G→A, D→E, Em→F#m, C→D
        val result = TranspositionUtils.transposeBody(body, 2, "D")
        val lines = result.lines()
        assertEquals("A  E",                          lines[0])
        assertEquals("how sweet the sound",           lines[1])
        assertEquals("F#m  D",                        lines[2])
        assertEquals("that saved a wretch like me",   lines[3])
    }

    @Test fun `transposeBody handles chord extensions and qualities`() {
        // 2 semitones up, target D (sharp): Cmaj7→Dmaj7, Fm7→Gm7, Gsus4→Asus4
        val body = "Cmaj7  Fm7  Gsus4"
        val result = TranspositionUtils.transposeBody(body, 2, "D")
        assertEquals("Dmaj7  Gm7  Asus4", result)
    }

    @Test fun `transposeBody handles entire sharp chromatic scale up 1 semitone`() {
        // Each note shifts to its neighbor in the sharp scale
        val sharpNotes = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
        val expected   = listOf("C#","D","D#","E","F","F#","G","G#","A","A#","B","C")
        val body = sharpNotes.joinToString("  ")
        val result = TranspositionUtils.transposeBody(body, 1, "C#")
        assertEquals(expected.joinToString("  "), result)
    }

    @Test fun `transposeBody handles entire flat chromatic scale up 1 semitone`() {
        // Each note shifts to its neighbor in the flat scale
        val flatNotes = listOf("C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B")
        val expected  = listOf("Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B","C")
        val body = flatNotes.joinToString("  ")
        val result = TranspositionUtils.transposeBody(body, 1, "Db")
        assertEquals(expected.joinToString("  "), result)
    }
}
