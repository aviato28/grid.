package com.nbansal.grid.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** Copies a picked photo into a draft's own folder: EXIF-upright, long edge <= [MAX_EDGE], JPEG q92. */
object PhotoImporter {
    const val MAX_EDGE = 3000
    private const val QUALITY = 92

    /** Blocking; run on an IO dispatcher. Returns false (and leaves no file) on failure. */
    fun import(context: Context, uri: Uri, dest: File, date: LocalDate?): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val long = max(info.size.width, info.size.height)
                if (long > MAX_EDGE) {
                    val s = MAX_EDGE.toFloat() / long
                    decoder.setTargetSize(max(1, (info.size.width * s).roundToInt()), max(1, (info.size.height * s).roundToInt()))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bmp.recycle()
            if (date != null) runCatching {
                ExifInterface(dest.absolutePath).apply {
                    setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "%04d:%02d:%02d 12:00:00".format(date.year, date.monthValue, date.dayOfMonth))
                    saveAttributes()
                }
            }
            true
        } catch (e: Exception) {
            dest.delete(); false
        } catch (e: OutOfMemoryError) {
            dest.delete(); false
        }
    }
}
