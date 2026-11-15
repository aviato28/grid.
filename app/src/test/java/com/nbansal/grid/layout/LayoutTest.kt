package com.nbansal.grid.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val a4 = Paper.A4
    private val mini = CardTemplate.Mini
    private val d = 0.001f

    @Test fun nineCardsPerA4Page() {
        val pages = layoutSheet(a4, mini, 9)
        assertEquals(1, pages.size)
        assertEquals(9, pages[0].cards.size)
    }

    @Test fun tenPhotosMakeTwoPages() {
        val pages = layoutSheet(a4, mini, 10)
        assertEquals(listOf(9, 1), pages.map { it.cards.size })
        assertEquals(listOf(0, 9), pages.map { it.firstIndex })
    }

    @Test fun zeroPhotosNoPages() = assertTrue(layoutSheet(a4, mini, 0).isEmpty())

    @Test fun gridIsCentered() {
        val g = layoutSheet(a4, mini, 9)[0].grid
        assertEquals(162f, g.width, d)
        assertEquals(258f, g.height, d)
        assertEquals(a4.widthMm - g.right, g.left, d)
        assertEquals(a4.heightMm - g.bottom, g.top, d)
    }

    @Test fun cardsInsideMarginsAndTouching() {
        val cards = layoutSheet(a4, mini, 9)[0].cards
        for (c in cards) {
            assertTrue(c.left >= 5f && c.top >= 5f)
            assertTrue(c.right <= 205f && c.bottom <= 292f)
            assertEquals(54f, c.width, d)
            assertEquals(86f, c.height, d)
        }
        assertEquals(cards[0].right, cards[1].left, d)
        assertEquals(cards[0].bottom, cards[3].top, d)
    }

    @Test fun gapReducesGridCapacity() {
        // 3 cols need 162 + 2*gap <= 200; 3 rows need 258 + 2*gap <= 287
        assertEquals(9, layoutSheet(a4, mini, 20, gapMm = 5f)[0].cards.size)
        assertEquals(4, layoutSheet(a4, mini, 20, gapMm = 25f)[0].cards.size)
    }

    @Test fun cropMarkCountForFullGrid() {
        val marks = cropMarks(layoutSheet(a4, mini, 9)[0])
        val vertical = marks.filter { it.x1 == it.x2 }
        val horizontal = marks.filter { it.y1 == it.y2 }
        assertEquals(4 * 2, vertical.size)
        assertEquals(4 * 2, horizontal.size)
    }

    @Test fun cropMarksGeometryAndNeverOverCards() {
        val page = layoutSheet(a4, mini, 9)[0]
        val g = page.grid
        for (m in cropMarks(page)) {
            val len = Math.hypot((m.x2 - m.x1).toDouble(), (m.y2 - m.y1).toDouble()).toFloat()
            assertEquals(4f, len, d)
            // entirely outside the grid by at least 1 mm
            val minX = minOf(m.x1, m.x2); val maxX = maxOf(m.x1, m.x2)
            val minY = minOf(m.y1, m.y2); val maxY = maxOf(m.y1, m.y2)
            val outside = maxY <= g.top - 1f + d || minY >= g.bottom + 1f - d ||
                maxX <= g.left - 1f + d || minX >= g.right + 1f - d
            assertTrue(outside)
            for (c in page.cards) {
                val overlaps = maxX > c.left + d && minX < c.right - d && maxY > c.top + d && minY < c.bottom - d
                assertTrue(!overlaps)
            }
            // stays on paper
            assertTrue(minX >= 0f && minY >= 0f && maxX <= a4.widthMm && maxY <= a4.heightMm)
        }
    }

    @Test fun partialPageMarksOnlyForPresentLines() {
        val marks = cropMarks(layoutSheet(a4, mini, 1)[0])
        assertEquals(2 * 2 + 2 * 2, marks.size)
    }

    @Test fun windowAndCaptionGeometry() {
        assertEquals(68f, mini.windowBottomMm, d)
        assertEquals(18f, mini.captionHeightMm, d)
    }
}
