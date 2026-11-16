package com.nbansal.grid.sheet

import com.nbansal.grid.layout.CardTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeOpsTest {
    private var next = 100L
    private val gen = { next++ }
    private fun mini(id: Long, uri: String?, caption: String = "") =
        Card(id, "mini", listOf(Slot(uri)), caption = caption)

    @Test fun singleToSingleKeepsContent() {
        val c = mini(1, "a", "hi").copy(location = "Goa")
        val out = applyShape(listOf(c), emptySet(), "classic", gen)
        assertEquals(1, out.size)
        assertEquals("classic", out[0].templateId)
        assertEquals("hi", out[0].caption)
        assertEquals("Goa", out[0].location)
        assertEquals("a", out[0].slots[0].uri)
    }

    @Test fun mergeSelectedIntoStrips() {
        val cards = (1..6L).map { mini(it, "u$it", "c$it") }
        val out = applyShape(cards, setOf(2L, 3L, 4L, 5L, 6L), "strip", gen)
        // card 1 stays; five photos -> 2 strips (4 + 1) placed after it
        assertEquals(listOf("mini", "strip", "strip"), out.map { it.templateId })
        assertEquals(listOf("u2", "u3", "u4", "u5"), out[1].slots.map { it.uri })
        assertEquals(listOf("u6", null, null, null), out[2].slots.map { it.uri })
        assertEquals("c2", out[1].caption)
        assertEquals(4, out[2].slots.size)
    }

    @Test fun splitMultiIntoSingles() {
        val strip = Card(1, "strip", listOf(Slot("a"), Slot(null), Slot("b"), Slot("c")), caption = "x")
        val out = applyShape(listOf(strip), emptySet(), "mini", gen)
        assertEquals(listOf("a", "b", "c"), out.map { it.slots.single().uri })
        assertEquals(1L, out[0].id)
        assertEquals(3, out.map { it.id }.distinct().size)
        assertTrue(out.all { it.templateId == "mini" })
    }

    @Test fun noOpWhenAlreadyThatShape() {
        val cards = listOf(mini(1, "a"))
        assertSame(cards, applyShape(cards, emptySet(), "mini", gen))
    }

    @Test fun labelAndStatsDefaultsFollowTemplate() {
        val out = applyShape(listOf(mini(1, "a")), emptySet(), "trading", gen)[0]
        assertEquals("PLAYER ONE", out.label)
        assertEquals(3, out.stats.size)
        val back = applyShape(listOf(out), emptySet(), "mini", gen)[0]
        assertEquals("", back.label)
        assertTrue(back.stats.isEmpty())
    }

    @Test fun captionTruncatedToNewLimit() {
        val c = mini(1, "a", "x".repeat(28))
        assertEquals(14, applyShape(listOf(c), emptySet(), "stamp", gen)[0].caption.length)
    }

    @Test fun commonTemplate() {
        assertNull(commonTemplate(listOf(mini(1, "a"), Card(2, "wide"))))
        assertEquals(CardTemplate.Mini, commonTemplate(listOf(mini(1, "a"), mini(2, "b"))))
    }

    @Test fun styleDefaultsYieldToUserChoice() {
        val stamp = Card(1, "stamp")
        assertEquals(0xFFF4EBDD.toInt(), stamp.resolveStyle(CardStyle()).frameArgb)
        assertEquals(FrameColor.Sage.argb, stamp.resolveStyle(CardStyle(frameColor = FrameColor.Sage)).frameArgb)
        assertEquals(CaptionFont.Serif, Card(2, "bookmark").resolveStyle(CardStyle()).font)
        assertEquals(CaptionFont.Handwritten, Card(2, "bookmark").resolveStyle(CardStyle(font = CaptionFont.Handwritten)).font)
        assertEquals(0xFFFFFFFF.toInt(), stamp.resolveStyle(CardStyle(frameColor = FrameColor.White)).frameArgb)
        assertEquals(CaptionFont.Mono, Card(2, "bookmark").resolveStyle(CardStyle(font = CaptionFont.Mono)).font)
    }
}
