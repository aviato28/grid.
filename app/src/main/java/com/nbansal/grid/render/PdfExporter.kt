package com.nbansal.grid.render

import android.graphics.pdf.PdfDocument
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.layoutSheet
import com.nbansal.grid.sheet.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

class PdfExporter(
    private val renderer: SheetRenderer,
    private val loader: PhotoLoader,
    private val exportDir: File,
) {
    data class Result(val file: File, val pages: Int)

    suspend fun export(
        photos: List<Photo>,
        paper: Paper = Paper.A4,
        template: CardTemplate = CardTemplate.Mini,
        onProgress: (page: Int, total: Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        exportDir.mkdirs()
        exportDir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(exportDir, "Grid-$stamp.pdf")
        val pages = layoutSheet(paper, template, photos.size)
        val pt = 72f / 25.4f
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, page ->
                coroutineContext.ensureActive()
                onProgress(i + 1, pages.size)
                // One page of bitmaps at a time; recycled right after drawing.
                val bitmaps = HashMap<Long, android.graphics.Bitmap>()
                val first = page.firstIndex
                for (j in page.cards.indices) {
                    val p = photos[first + j]
                    loader.forPrint(p.uri)?.let { bitmaps[p.id] = it }
                }
                val info = PdfDocument.PageInfo.Builder(
                    Math.round(paper.widthMm * pt), Math.round(paper.heightMm * pt), i + 1,
                ).create()
                val pdfPage = doc.startPage(info)
                pdfPage.canvas.scale(pt, pt) // exact mm -> pt; the page size itself must be an int
                renderer.drawPage(pdfPage.canvas, paper, page, photos) { bitmaps[it.id] }
                doc.finishPage(pdfPage)
                bitmaps.values.forEach { it.recycle() }
            }
            coroutineContext.ensureActive()
            FileOutputStream(file).use { doc.writeTo(it) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            doc.close()
        }
        Result(file, pages.size)
    }
}
