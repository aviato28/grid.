package com.nbansal.grid.render

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Decodes photos (EXIF-aware) downsampled to what is needed. */
class PhotoLoader(private val resolver: ContentResolver) {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    /** Screen-sized, cached. */
    suspend fun preview(uri: String): Bitmap? =
        cache.get(uri) ?: decode(uri, PREVIEW_W, PREVIEW_H)?.also { cache.put(uri, it) }

    /** ~300 DPI for a [wMm] x [hMm] window with 1.3x zoom headroom; not cached. */
    suspend fun forPrint(uri: String, wMm: Float, hMm: Float): Bitmap? =
        decode(uri, (wMm * PRINT_PX_PER_MM).roundToInt().coerceAtLeast(64), (hMm * PRINT_PX_PER_MM).roundToInt().coerceAtLeast(64))

    private suspend fun decode(uriString: String, targetW: Int, targetH: Int): Bitmap? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val s = min(1f, max(targetW.toFloat() / w, targetH.toFloat() / h))
                decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    companion object {
        const val PREVIEW_W = 480
        const val PREVIEW_H = 640
        const val PRINT_PX_PER_MM = 300f / 25.4f * 1.3f
    }
}
