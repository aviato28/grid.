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

    @Test fun windowAndFooterGeometry() {
        assertEquals(68f, mini.window.bottom, d)
        assertEquals(18f, mini.footer!!.height, d)
        assertEquals(46f, mini.footer!!.width, d)
    }

    @Test fun templatesAreSane() {
        assertEquals(CardTemplate.All.size, CardTemplate.All.map { it.id }.toSet().size)
        for (t in CardTemplate.All) {
            val card = RectMm(0f, 0f, t.cardWidthMm, t.cardHeightMm)
            fun inside(r: RectMm) = r.left >= card.left && r.top >= card.top && r.right <= card.right && r.bottom <= card.bottom
            assertTrue(t.photoWindows.isNotEmpty())
            t.photoWindows.forEach { assertTrue(inside(it)) }
            t.footer?.let { f ->
                assertTrue(inside(f))
                t.photoWindows.forEach { assertTrue(it.bottom <= f.top + d || it.right <= f.left || it.left >= f.right) }
            }
        }
        assertEquals(mini, CardTemplate.byId("mini"))
        assertEquals(mini, CardTemplate.byId("unknown"))
    }

    @Test fun zoomKeepsPointUnderCentroid() {
        val w = mini.window
        val bw = 800f; val bh = 1000f
        val cx = w.right - 5f; val cy = w.top + 5f
        // image point under the centroid before and after a pinch
        fun pointUnder(st: PanZoom.State): Pair<Float, Float> {
            val s = PanZoom.baseScale(bw, bh, w) * st.zoom
            val (rx, ry) = PanZoom.panRangeMm(bw, bh, st.zoom, w)
            val icx = (w.left + w.right) / 2 + st.panX * rx
            val icy = (w.top + w.bottom) / 2 + st.panY * ry
            return ((cx - icx) / s) to ((cy - icy) / s)
        }
        val s0 = PanZoom.State(2f, 0.2f, -0.1f)
        val s1 = PanZoom.transform(bw, bh, w, s0, cx, cy, 0f, 0f, 1.5f)
        val a = pointUnder(s0); val b = pointUnder(s1)
        assertEquals(a.first, b.first, 0.5f)
        assertEquals(a.second, b.second, 0.5f)
    }
}
