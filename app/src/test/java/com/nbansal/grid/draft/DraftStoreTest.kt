package com.nbansal.grid.draft

import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.sheet.CaptionFont
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.FrameColor
import com.nbansal.grid.sheet.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class DraftStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun draft(store: DraftStore, id: String = "d1"): Draft {
        val photo = File(store.photosDir(id), "a.jpg").also { it.parentFile.mkdirs(); it.writeText("x") }
        return Draft(
            id, "Sheet · 16 Nov", 1000, 2000,
            listOf(Card(1, "mini", listOf(Slot(store.fileUri(photo), zoom = 1.5f)), caption = "hi", date = LocalDate.of(2026, 11, 15),
                filterOverride = FilterSettings("gold", 0.5f, grain = true))),
            CardStyle(font = null, frameColor = FrameColor.White), FilterSettings("vivid"), false,
        )
    }

    @Test fun roundTripKeepsEverythingAndRelativizesPhotos() {
        val store = DraftStore(tmp.newFolder("drafts"))
        val d = draft(store)
        store.save(d)
        assertTrue(File(store.dir("d1"), "draft.json").readText().contains("\"photos/a.jpg\""))
        assertFalse(File(store.dir("d1"), "draft.json.tmp").exists())
        assertEquals(d, store.load("d1"))
        assertNull(store.load("nope"))
    }

    @Test fun listIsNewestFirstDuplicateAndDelete() {
        val store = DraftStore(tmp.newFolder("drafts"))
        store.save(draft(store, "a"))
        store.save(draft(store, "b").copy(updatedAt = 5000))
        assertEquals(listOf("b", "a"), store.list().map { it.id })
        val copy = store.duplicate("a", "c", "Copy", 9000)
        assertNotNull(copy)
        assertTrue(copy!!.cards[0].slots[0].uri!!.contains("/c/photos/"))
        assertTrue(File(store.photosDir("c"), "a.jpg").exists())
        store.delete("a")
        assertFalse(store.dir("a").exists())
        assertEquals(setOf("b", "c"), store.list().map { it.id }.toSet())
        assertTrue(File(store.photosDir("c"), "a.jpg").exists())
    }

    @Test fun autoStyleSurvivesJsonAndFontEnumsByName() {
        val store = DraftStore(tmp.newFolder("drafts"))
        store.save(draft(store).copy(sheetStyle = CardStyle(font = CaptionFont.Mono)))
        assertEquals(CaptionFont.Mono, store.load("d1")!!.sheetStyle.font)
        assertNull(store.load("d1")!!.sheetStyle.frameColor)
    }

    @Test fun timeAgoAndPrune() {
        assertEquals("just now", timeAgo(1000, 1000))
        assertEquals("5 min ago", timeAgo(5 * 60_000L, 0))
        assertEquals("2h ago", timeAgo(2 * 3_600_000L, 0))
        val store = DraftStore(tmp.newFolder("drafts"))
        val d = draft(store)
        File(store.photosDir("d1"), "orphan.jpg").writeText("y")
        store.prunePhotos(d)
        assertFalse(File(store.photosDir("d1"), "orphan.jpg").exists())
        assertTrue(File(store.photosDir("d1"), "a.jpg").exists())
    }
}
