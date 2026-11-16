package com.nbansal.grid.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val d = 0.01f
    private val mini = CardTemplate.Mini

    private fun perPage(t: CardTemplate) = packSheet(List(200) { t })[0].items.size

    @Test fun homogeneousCounts() {
        assertEquals(9, perPage(mini))
        assertEquals(4, perPage(CardTemplate.Classic))
        assertEquals(30, perPage(CardTemplate.Passport))
        assertEquals(1, perPage(CardTemplate.ContactSheet))
    }

    @Test fun miniGridIsThreeByThreeAndTouching() {
        val items = packSheet(List(9) { mini })[0].items
        assertEquals(3, items.map { it.rect.top }.distinct().size)
        assertEquals(3, items.map { it.rect.left }.distinct().size)
        assertTrue(items.none { it.rotated })
        val r = items.sortedBy { it.index }
        assertEquals(r[0].rect.right, r[1].rect.left, d)
        assertEquals(r[0].rect.bottom, r[3].rect.top, d)
    }

    @Test fun tenMinisMakeTwoPages() {
        assertEquals(2, packSheet(List(10) { mini }).size)
        assertTrue(packSheet(emptyList()).isEmpty())
    }

    @Test fun rotationChosenWhenItClearlyHelps() {
        assertTrue(chooseRotation(CardTemplate.PhotoStrip))
        assertTrue(chooseRotation(CardTemplate.Ticket))
        assertFalse(chooseRotation(mini))
        assertFalse(chooseRotation(CardTemplate.ContactSheet))
        val strips = packSheet(List(5) { CardTemplate.PhotoStrip })
        assertEquals(1, strips.size)
        assertTrue(strips[0].items.all { it.rotated && Math.abs(it.rect.width - 152f) < d })
    }

    @Test fun mixedNeverOverlapsAndStaysInside() {
        val ts = CardTemplate.All
        val list = (0 until 60).map { ts[(it * 7) % ts.size] }
        for (page in packSheet(list)) {
            val rs = page.items.map { it.rect }
            for (r in rs) {
                assertTrue(r.left >= PRINT_AREA.left - d && r.right <= PRINT_AREA.right + d)
                assertTrue(r.top >= PRINT_AREA.top - d && r.bottom <= PRINT_AREA.bottom + d)
            }
            for (i in rs.indices) for (j in i + 1 until rs.size) {
                val a = rs[i]; val b = rs[j]
                val overlap = a.left < b.right - d && b.left < a.right - d && a.top < b.bottom - d && b.top < a.bottom - d
                assertFalse(overlap)
            }
        }
        val all = packSheet(list).flatMap { it.items }.map { it.index }
        assertEquals(list.indices.toSet(), all.toSet())
        assertEquals(list.size, all.size)
    }

    @Test fun groupingKeepsOrderWithinGroup() {
        val list = listOf(mini, CardTemplate.Classic, mini, CardTemplate.Wallet, CardTemplate.Classic, mini)
        val order = packSheet(list, groupByShape = true).flatMap { it.items }.map { it.index }
        assertEquals(listOf(0, 2, 5, 1, 4, 3), order)
        val plain = packSheet(list, groupByShape = false).flatMap { it.items }.map { it.index }
        assertEquals(list.indices.toList(), plain)
    }

    @Test fun shelvesAreCenteredAndTopAligned() {
        val page = packSheet(listOf(mini, mini))[0]
        val b = page.bounds
        assertEquals(105f, (b.left + b.right) / 2, d)
        assertEquals(142.5f, (b.top + b.bottom) / 2, d)
        assertEquals(page.items[0].rect.top, page.items[1].rect.top, d)
    }

    @Test fun marksSitOutsideCardsAndAboveRuler() {
        val page = packSheet(List(9) { mini })[0]
        val horizontal = page.marks.filter { it.y1 == it.y2 }
        val vertical = page.marks.filter { it.x1 == it.x2 }
        assertEquals(4 * 2, horizontal.size)
        assertEquals(4 * 2, vertical.size)
        for (m in page.marks) {
            val minX = minOf(m.x1, m.x2); val maxX = maxOf(m.x1, m.x2)
            val minY = minOf(m.y1, m.y2); val maxY = maxOf(m.y1, m.y2)
            assertTrue(minX >= 0f && minY >= 0f && maxX <= 210f && maxY <= 285f)
            for (c in page.items.map { it.rect }) {
                assertFalse(maxX > c.left + d && minX < c.right - d && maxY > c.top + d && minY < c.bottom - d)
            }
        }
    }

    @Test fun windowAndFooterGeometry() {
        assertEquals(68f, mini.window.bottom, d)
        assertEquals(18f, mini.footer!!.height, d)
        assertEquals(46f, mini.footer!!.width, d)
        assertEquals(4, CardTemplate.PhotoStrip.windowCount)
        assertEquals(30, CardTemplate.ContactSheet.windowCount)
        assertEquals(3, CardTemplate.FilmStrip.windowCount)
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
