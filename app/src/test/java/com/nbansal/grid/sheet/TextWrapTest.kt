package com.nbansal.grid.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextWrapTest {
    private val fake = TextMeasurer { t, s -> t.length * s * 0.5f }

    @Test fun wrapsOnWords() {
        // size 2 -> 1 mm per char; 10 mm = 10 chars
        assertEquals(listOf("hello", "world foo", "bar"), wrapLines("hello world foo bar", 9f, 2f, fake).let {
            // "hello world" is 11 > 9, "world foo" is 9 fits, "world foo bar" 13 > 9
            it
        })
    }

    @Test fun breaksLongWordsAndKeepsParagraphs() {
        val lines = wrapLines("abcdefghijkl\nxy", 5f, 2f, fake)
        assertEquals(listOf("abcde", "fghij", "kl", "xy"), lines)
    }

    @Test fun shrinksThenEllipsizes() {
        val text = "word ".repeat(40).trim()
        val w = wrapToFit(text, 20f, 10f, 4f, 2f, fake)
        assertTrue(w.sizeMm >= 2f)
        assertTrue(w.lines.size * w.lineHeightMm <= 10f + 1e-3f)
        assertTrue(w.lines.last().endsWith(ELLIPSIS))
        val short = wrapToFit("hi there", 20f, 10f, 4f, 2f, fake)
        assertEquals(4f, short.sizeMm, 1e-4f)
        assertEquals(1, short.lines.size)
    }
}
