package com.nbansal.grid.render

import android.graphics.Bitmap
import android.util.LruCache
import com.nbansal.grid.filter.FilmProcessor
import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.layout.PanZoom
import com.nbansal.grid.layout.RectMm
import com.nbansal.grid.sheet.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies the film filter to photo bitmaps on [Dispatchers.Default] and caches preview results.
 * Filtering happens on the whole decoded photo (before pan/zoom), so panning never re-filters.
 * Grain/blur sizes are converted with the photo's pixels-per-mm inside its window, so the preview and the
 * 300 DPI PDF look the same. Seeds derive from card id + slot, so every render of a card is identical.
 */
class FilteredPhotos(private val loader: PhotoLoader) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Screen-sized, filtered by [settings], cached. Returns the loader's bitmap itself when the filter is a no-op. */
    suspend fun preview(card: Card, slot: Int, settings: FilterSettings): Bitmap? {
        val uri = card.slots.getOrNull(slot)?.uri ?: return null
        val base = loader.preview(uri) ?: return null
        if (settings.isIdentity) return base
        val key = "$uri|$settings|${base.width}x${base.height}|${card.id}:$slot"
        cache.get(key)?.let { return it }
        return run(base, card.template.photoWindows[slot], settings, seed(card, slot)).also { cache.put(key, it) }
    }

    /** ~300 DPI, filtered, not cached; the caller recycles it unless it is the same instance as the base. */
    suspend fun forPrint(card: Card, slot: Int, settings: FilterSettings): Bitmap? {
        val uri = card.slots.getOrNull(slot)?.uri ?: return null
        val win = card.template.photoWindows[slot]
        val base = loader.forPrint(uri, win.width, win.height) ?: return null
        if (settings.isIdentity) return base
        return run(base, win, settings, seed(card, slot)).also { base.recycle() }
    }

    /** Small filtered sample of [uri] for the filter picker; [maxEdge] px on the long side. */
    suspend fun sample(uri: String, settings: FilterSettings, maxEdge: Int = 140): Bitmap? {
        val key = "S|$uri|$settings|$maxEdge"
        cache.get(key)?.let { return it }
        val base = loader.preview(uri) ?: return null
        val s = maxEdge.toFloat() / maxOf(base.width, base.height)
        val small = withContext(Dispatchers.Default) {
            Bitmap.createScaledBitmap(base, (base.width * s).toInt().coerceAtLeast(1), (base.height * s).toInt().coerceAtLeast(1), true)
        }
        val out = if (settings.isIdentity) small else run(small, SAMPLE_WINDOW, settings, 1L).also { if (it !== small) small.recycle() }
        cache.put(key, out)
        return out
    }

    private fun seed(card: Card, slot: Int) = card.id * 31 + slot

    private suspend fun run(base: Bitmap, win: RectMm, s: FilterSettings, seed: Long): Bitmap =
        withContext(Dispatchers.Default) { filterBitmap(base, win, s, seed) }

    private companion object {
        /** A Mini-sized window, so sample grain is proportioned like a real card. */
        val SAMPLE_WINDOW = RectMm(0f, 0f, 46f, 62f)
    }
}

/**
 * Returns a filtered copy of [base] (not modified). [win] is the card window the photo fills, used to turn
 * pixels into millimetres for grain and glow sizes. Deterministic for a given [seed].
 */
fun filterBitmap(base: Bitmap, win: RectMm, s: FilterSettings, seed: Long): Bitmap {
    val w = base.width
    val h = base.height
    val px = IntArray(w * h)
    base.getPixels(px, 0, w, 0, 0, w, h)
    val pxPerMm = 1f / PanZoom.baseScale(w.toFloat(), h.toFloat(), win)
    FilmProcessor.apply(px, w, h, s, seed, pxPerMm)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, w, 0, 0, w, h) }
}
