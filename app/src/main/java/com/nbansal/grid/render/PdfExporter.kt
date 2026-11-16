package com.nbansal.grid.render

import android.graphics.pdf.PdfDocument
import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.packSheet
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.Card
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
    private val photos: FilteredPhotos,
    private val exportDir: File,
) {
    data class Result(val file: File, val pages: Int)

    suspend fun export(
        cards: List<Card>,
        sheetStyle: CardStyle,
        sheetFilter: FilterSettings = FilterSettings(),
        groupByShape: Boolean = true,
        paper: Paper = Paper.A4,
        onProgress: (page: Int, total: Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        exportDir.mkdirs()
        exportDir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val file = File(exportDir, "Grid-$stamp.pdf")
        val pages = packSheet(cards.map { it.template }, groupByShape)
        val pt = 72f / 25.4f
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, page ->
                coroutineContext.ensureActive()
                onProgress(i + 1, pages.size)
                // One page of (filtered) bitmaps at a time; recycled right after drawing.
                val bitmaps = HashMap<Pair<Int, Int>, android.graphics.Bitmap>()
                for (item in page.items) {
                    val card = cards[item.index]
                    card.slots.forEachIndexed { k, slot ->
                        if (slot.uri == null) return@forEachIndexed
                        photos.forPrint(card, k, card.filterFor(sheetFilter))?.let { bitmaps[item.index to k] = it }
                    }
                }
                val info = PdfDocument.PageInfo.Builder(
                    Math.round(paper.widthMm * pt), Math.round(paper.heightMm * pt), i + 1,
                ).create()
                val pdfPage = doc.startPage(info)
                pdfPage.canvas.scale(pt, pt) // exact mm -> pt; the page size itself must be an int
                renderer.drawPage(pdfPage.canvas, paper, page, cards, sheetStyle, { ci, k -> bitmaps[ci to k] }, preview = false)
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
