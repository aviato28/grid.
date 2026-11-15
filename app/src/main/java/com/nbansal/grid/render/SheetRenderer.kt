package com.nbansal.grid.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.layout.cropMarks
import com.nbansal.grid.sheet.Photo
import kotlin.math.max

/**
 * The single renderer for both the on-screen preview and the PDF. Everything is in millimetres:
 * callers must scale the canvas to px/mm (or points/mm) before calling.
 */
class SheetRenderer(
    private val captionTypeface: Typeface,
    private val template: CardTemplate = CardTemplate.Mini,
) {
    private val inkColor = Color.parseColor("#141414")
    private val outlineColor = Color.parseColor("#E6E2DA")
    private val mutedColor = Color.parseColor("#6D685D")

    private val fill = Paint().apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val placeholder = Paint().apply { style = Paint.Style.FILL; color = Color.parseColor("#EDEAE3") }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.1f; color = outlineColor
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val marks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.15f; color = Color.BLACK
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = captionTypeface; color = inkColor; textAlign = Paint.Align.CENTER
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = Typeface.MONOSPACE; color = mutedColor; textSize = 2.1f
    }
    private val matrix = Matrix()

    fun drawPage(canvas: Canvas, paper: Paper, page: SheetPage, photos: List<Photo>, bitmapFor: (Photo) -> Bitmap?) {
        canvas.drawRect(0f, 0f, paper.widthMm, paper.heightMm, fill)
        for (m in cropMarks(page)) canvas.drawLine(m.x1, m.y1, m.x2, m.y2, marks)
        page.cards.forEachIndexed { i, rect ->
            val photo = photos.getOrNull(page.firstIndex + i)
            canvas.save()
            canvas.translate(rect.left, rect.top)
            if (photo != null) {
                drawCard(canvas, bitmapFor(photo), photo.caption, photo.zoom, photo.panX, photo.panY)
            } else {
                drawCard(canvas, null, "", 1f, 0f, 0f)
            }
            canvas.restore()
        }
        drawFooter(canvas, page)
    }

    /** Draws one card with its top-left at the canvas origin. */
    fun drawCard(canvas: Canvas, bitmap: Bitmap?, caption: String, zoom: Float, panX: Float, panY: Float) {
        val t = template
        canvas.drawRect(0f, 0f, t.cardWidthMm, t.cardHeightMm, fill)
        canvas.drawRect(0f, 0f, t.cardWidthMm, t.cardHeightMm, outline)
        val l = t.windowLeftMm
        val tp = t.windowTopMm
        val r = l + t.windowWidthMm
        val b = tp + t.windowHeightMm
        if (bitmap == null) {
            canvas.drawRect(l, tp, r, b, placeholder)
        } else {
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val s = baseScale(bw, bh) * zoom.coerceAtLeast(1f)
            val (rx, ry) = panRangeMm(bw, bh, zoom)
            val cx = l + t.windowWidthMm / 2 + panX.coerceIn(-1f, 1f) * rx
            val cy = tp + t.windowHeightMm / 2 + panY.coerceIn(-1f, 1f) * ry
            matrix.reset()
            matrix.setScale(s, s)
            matrix.postTranslate(cx - bw * s / 2, cy - bh * s / 2)
            canvas.save()
            canvas.clipRect(l, tp, r, b)
            canvas.drawBitmap(bitmap, matrix, bitmapPaint)
            canvas.restore()
        }
        drawCaption(canvas, caption)
    }

    /** mm of image per bitmap pixel at zoom 1 (center-crop "cover"). */
    fun baseScale(bw: Float, bh: Float) = max(template.windowWidthMm / bw, template.windowHeightMm / bh)

    /** Maximum pan in mm (per axis) at the given zoom such that the window stays covered. */
    fun panRangeMm(bw: Float, bh: Float, zoom: Float): Pair<Float, Float> {
        val s = baseScale(bw, bh) * zoom.coerceAtLeast(1f)
        return max(0f, (bw * s - template.windowWidthMm) / 2) to max(0f, (bh * s - template.windowHeightMm) / 2)
    }

    private fun drawCaption(canvas: Canvas, caption: String) {
        val text = caption.trim()
        if (text.isEmpty()) return
        val t = template
        val maxW = t.windowWidthMm
        val base = 11f * 25.4f / 72f // 11 pt in mm
        captionPaint.textSize = base
        val w = captionPaint.measureText(text)
        if (w > maxW) captionPaint.textSize = max(base * maxW / w, base * 0.55f)
        var shown = text
        if (captionPaint.measureText(shown) > maxW) {
            val n = captionPaint.breakText(text, true, maxW - captionPaint.measureText("…"), null)
            shown = text.take(n).trimEnd() + "…"
        }
        val cy = t.captionTopMm + t.captionHeightMm / 2
        val fm = captionPaint.fontMetrics
        canvas.drawText(shown, t.cardWidthMm / 2, cy - (fm.ascent + fm.descent) / 2, captionPaint)
    }

    /** 10 mm calibration ruler (1 mm ticks) and a hint, in the bottom margin below the crop marks. */
    private fun drawFooter(canvas: Canvas, page: SheetPage) {
        val x0 = page.grid.left
        val y = page.grid.bottom + 9f
        canvas.drawLine(x0, y, x0 + 10f, y, marks)
        for (i in 0..10) {
            val h = if (i == 0 || i == 5 || i == 10) 2.2f else 1.2f
            canvas.drawLine(x0 + i, y - h, x0 + i, y, marks)
        }
        canvas.drawText("Print at 100% / actual size · Grid.", x0 + 13f, y - 0.2f, footerPaint)
        canvas.drawText("10 mm", x0, y + 3f, footerPaint)
    }
}
