package com.nbansal.grid.sheet

import com.nbansal.grid.layout.CardTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CardStyleTest {
    private val date = LocalDate.of(2026, 11, 15)
    private val fake = TextMeasurer { t, s -> t.length * s * 0.5f }

    @Test fun dateFormats() {
        assertEquals("15 Nov 2026", DateFormat.DayMonthYear.format(date))
        assertEquals("15.11.26", DateFormat.Dotted.format(date))
        assertEquals("NOV 2026", DateFormat.MonthYear.format(date))
        assertEquals("Sunday, 15 Nov", DateFormat.Weekday.format(date))
        assertEquals("'26 11 15", filmStampText(date))
    }

    @Test fun exifDate() {
        assertEquals(date, parseExifDate("2026:11:15 10:22:01"))
        assertNull(parseExifDate("garbage"))
        assertNull(parseExifDate(null))
        assertNull(parseExifDate("2026:13:45 00:00:00"))
    }

    @Test fun autoContrast() {
        val dark = CardStyle(frameColor = FrameColor.Black, textColor = TextColor.Ink)
        assertEquals(TextColor.White.argb, effectiveTextColor(dark))
        val light = CardStyle(frameColor = FrameColor.Cream, textColor = TextColor.White)
        assertEquals(TextColor.Ink.argb, effectiveTextColor(light))
        assertEquals(TextColor.PenBlue.argb, effectiveTextColor(CardStyle(textColor = TextColor.PenBlue)))
        assertEquals(TextColor.White.argb, effectiveTextColor(CardStyle(frameColor = FrameColor.Black, textColor = TextColor.White)))
        assertTrue(isDarkColor(FrameColor.Black.argb))
        FrameColor.entries.filter { it != FrameColor.Black }.forEach { assertTrue(!isDarkColor(it.argb)) }
    }

    @Test fun footerRunsStayInsideFooterForEveryLayout() {
        val footer = CardTemplate.Mini.footer!!
        val contents = listOf(
            FooterContent(),
            FooterContent("Hi", date, "Lisbon, Portugal"),
            FooterContent("A very long caption that cannot possibly fit on one line", date, "A very long location name, Somewhere Far Away"),
            FooterContent("", date, ""),
        )
        for (layout in FooterLayout.entries) for (align in Align.entries) for (size in TextSize.entries) {
            for (c in contents) {
                val style = CardStyle(layout = layout, align = align, textSize = size)
                for (r in layoutFooter(footer, style, c, fake)) {
                    val rect = r.rect
                    val msg = "$layout $align $size '${r.text}'"
                    assertTrue(msg, rect.left >= footer.left - 0.001f && rect.right <= footer.right + 0.001f)
                    assertTrue(msg, rect.top >= footer.top - 0.001f && rect.bottom <= footer.bottom + 0.001f)
                }
            }
        }
    }

    @Test fun shrinkThenEllipsize() {
        val (t1, s1, _) = fitText("abcdefghij", 4f, 20f, fake) // 20mm exactly fits
        assertEquals("abcdefghij", t1); assertEquals(4f, s1, 0.001f)
        val (t2, s2, w2) = fitText("abcdefghijkl", 4f, 20f, fake) // needs 24 -> shrink to 3.33
        assertEquals("abcdefghijkl", t2); assertTrue(s2 < 4f && s2 >= 2.8f - 0.001f); assertTrue(w2 <= 20f + 0.001f)
        val (t3, s3, w3) = fitText("abcdefghijklmnopqrstuvwxyz", 4f, 20f, fake)
        assertTrue(t3.endsWith(ELLIPSIS)); assertEquals(2.8f, s3, 0.001f); assertTrue(w3 <= 20f + 0.001f)
    }

    @Test fun layoutsDropEmptyLines() {
        val footer = CardTemplate.Mini.footer!!
        assertTrue(layoutFooter(footer, CardStyle(layout = FooterLayout.Blank), FooterContent("x", date, "y"), fake).isEmpty())
        assertTrue(layoutFooter(footer, CardStyle(layout = FooterLayout.DateOnly), FooterContent("x"), fake).isEmpty())
        assertEquals(2, layoutFooter(footer, CardStyle(layout = FooterLayout.CaptionAndDate), FooterContent("x", date), fake).size)
        assertEquals("15 Nov 2026 · Rome", layoutFooter(footer, CardStyle(layout = FooterLayout.CaptionDateLocation), FooterContent("x", date, "Rome"), fake)[1].text)
        assertEquals(1, layoutFooter(footer, CardStyle(layout = FooterLayout.FilmStamp), FooterContent("x", date), fake).size)
    }

    @Test fun filmStampInsideWindow() {
        val w = CardTemplate.Mini.window
        val r = filmStampRun(w, date, fake).rect
        assertTrue(r.left >= w.left && r.right <= w.right && r.top >= w.top && r.bottom <= w.bottom)
    }
}
