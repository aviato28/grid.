package com.nbansal.grid.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmProcessorTest {
    private fun gradient(w: Int = 64, h: Int = 48) = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        0xFF000000.toInt() or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or 128
    }

    @Test fun identityLeavesPixels() {
        val px = gradient(); val copy = px.copyOf()
        FilmProcessor.apply(px, 64, 48, FilterSettings(), 1, 10f)
        assertArrayEquals(copy, px)
    }

    @Test fun deterministicForSameSeedDifferentForOther() {
        val s = FilterSettings("trix").withFilter(FilmFilter.TriX)
        val a = gradient(); val b = gradient(); val c = gradient()
        FilmProcessor.apply(a, 64, 48, s, 42, 10f); FilmProcessor.apply(b, 64, 48, s, 42, 10f); FilmProcessor.apply(c, 64, 48, s, 43, 10f)
        assertArrayEquals(a, b)
        assertTrue(!a.contentEquals(c))
    }

    @Test fun everyPresetRunsAndKeepsAlpha() {
        for (f in FilmFilter.All) {
            val px = gradient()
            FilmProcessor.apply(px, 64, 48, FilterSettings().withFilter(f).copy(lightLeak = true), 7, 10f)
            assertTrue(f.id, px.all { (it ushr 24) == 0xFF })
        }
    }

    @Test fun monoIsGreyAndIntensityZeroIsOriginalColours() {
        val px = gradient()
        FilmProcessor.apply(px, 64, 48, FilterSettings("trix"), 1, 10f)
        assertTrue(px.all { ((it shr 16) and 0xFF) == ((it shr 8) and 0xFF) && ((it shr 8) and 0xFF) == (it and 0xFF) })
        val z = gradient(); val o = gradient()
        FilmProcessor.apply(z, 64, 48, FilterSettings("vivid", intensity = 0f), 1, 10f)
        assertArrayEquals(o, z)
    }

    @Test fun brightnessAndCurve() {
        val px = intArrayOf(0xFF808080.toInt())
        FilmProcessor.apply(px, 1, 1, FilterSettings(brightness = 0.5f), 1, 10f)
        assertTrue((px[0] and 0xFF) > 0x90)
        assertEquals(0f, ToneCurve.of(0f, 0f, 1f, 1f).eval(0f), 1e-6f)
        assertEquals(0.5f, ToneCurve.of(0f, 0f, 1f, 1f).eval(0.5f), 1e-4f)
        val lut = FilmFilter.Gold.recipe.master.lut()
        assertTrue((1 until 256).all { lut[it] >= lut[it - 1] - 1e-5f })
    }
}
