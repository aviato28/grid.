package com.nbansal.grid.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.PanZoom
import com.nbansal.grid.layout.RectMm
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.layout.cropMarks
import com.nbansal.grid.sheet.CaptionFont
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.FILM_STAMP_ARGB
import com.nbansal.grid.sheet.FooterLayout
import com.nbansal.grid.sheet.FooterContent
import com.nbansal.grid.sheet.Photo
import com.nbansal.grid.sheet.Align
import com.nbansal.grid.sheet.TextMeasurer
import com.nbansal.grid.sheet.TextRole
import com.nbansal.grid.sheet.TextRun
import com.nbansal.grid.sheet.effectiveTextColor
import com.nbansal.grid.sheet.filmStampRun
import com.nbansal.grid.sheet.isDarkColor
import com.nbansal.grid.sheet.layoutFooter
import com.nbansal.grid.sheet.secondaryColor

/**
 * Text is always drawn in magnified space (canvas scaled by 1/K, sizes and positions times K) so glyph
 * advances are not rounded at tiny mm sizes.
 */
private const val K = 100f

/**
 * The single renderer for both the on-screen preview and the PDF. Everything is in millimetres:
 * callers must scale the canvas to px/mm (or points/mm) before calling.
 */
class SheetRenderer(private val typefaceFor: (CaptionFont) -> Typeface) {
    private val lightOutline = Color.parseColor("#E0DCD3")
    private val darkOutline = Color.parseColor("#9A9A9A")
    private val mutedColor = Color.parseColor("#6D685D")

    private val fill = Paint().apply { style = Paint.Style.FILL }
    private val placeholder = Paint().apply { style = Paint.Style.FILL; color = Color.parseColor("#EDEAE3") }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.1f }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val marks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.15f; color = Color.BLACK
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true; isSubpixelText = true; isLinearText = true
    }
    private val matrix = Matrix()

    private fun measurer(tf: Typeface) = TextMeasurer { text, sizeMm ->
        textPaint.typeface = tf
        textPaint.textSize = sizeMm * K
        textPaint.measureText(text) / K
    }

    private fun drawRun(canvas: Canvas, run: TextRun, tf: Typeface, color: Int) {
        textPaint.typeface = tf
        textPaint.textSize = run.sizeMm * K
        textPaint.color = color
        textPaint.textAlign = when (run.align) {
            Align.Start -> Paint.Align.LEFT; Align.Center -> Paint.Align.CENTER; Align.End -> Paint.Align.RIGHT
        }
        canvas.save()
        canvas.scale(1f / K, 1f / K)
        canvas.drawText(run.text, run.x * K, run.baseline * K, textPaint)
        canvas.restore()
    }

    fun drawPage(
        canvas: Canvas, paper: Paper, page: SheetPage, photos: List<Photo>, sheetStyle: CardStyle,
        bitmapFor: (Photo) -> Bitmap?,
    ) {
        fill.color = Color.WHITE
        canvas.drawRect(0f, 0f, paper.widthMm, paper.heightMm, fill)
        for (m in cropMarks(page)) canvas.drawLine(m.x1, m.y1, m.x2, m.y2, marks)
        page.cards.forEachIndexed { i, rect ->
            val photo = photos.getOrNull(page.firstIndex + i)
            canvas.save()
            canvas.translate(rect.left, rect.top)
            drawCard(canvas, photo?.let(bitmapFor), photo, sheetStyle)
            canvas.restore()
        }
        drawFooter(canvas, page)
    }

    /** Draws one card with its top-left at the canvas origin. A null [photo] draws an empty card. */
    fun drawCard(canvas: Canvas, bitmap: Bitmap?, photo: Photo?, sheetStyle: CardStyle) {
        val t = photo?.template ?: CardTemplate.Mini
        val style = photo?.styleFor(sheetStyle) ?: sheetStyle
        val content = photo?.content ?: FooterContent()
        fill.color = style.frameColor.argb
        canvas.drawRect(0f, 0f, t.cardWidthMm, t.cardHeightMm, fill)
        outline.color = if (isDarkColor(style.frameColor.argb)) darkOutline else lightOutline
        canvas.drawRect(0f, 0f, t.cardWidthMm, t.cardHeightMm, outline)
        val tf = typefaceFor(style.font)
        val m = measurer(tf)
        for (w in t.photoWindows) {
            drawWindow(canvas, bitmap, w, photo?.zoom ?: 1f, photo?.panX ?: 0f, photo?.panY ?: 0f)
        }
        if (style.layout == FooterLayout.FilmStamp && content.date != null) {
            drawRun(canvas, filmStampRun(t.window, content.date, measurer(typefaceFor(CaptionFont.Mono))),
                typefaceFor(CaptionFont.Mono), FILM_STAMP_ARGB)
        }
        val footer = t.footer
        if (footer != null) {
            val color = effectiveTextColor(style)
            for (run in layoutFooter(footer, style, content, m)) {
                drawRun(canvas, run, tf, if (run.role == TextRole.Secondary) secondaryColor(color) else color)
            }
        }
    }

    private fun drawWindow(canvas: Canvas, bitmap: Bitmap?, w: RectMm, zoom: Float, panX: Float, panY: Float) {
        if (bitmap == null) {
            canvas.drawRect(w.left, w.top, w.right, w.bottom, placeholder)
            return
        }
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val s = PanZoom.baseScale(bw, bh, w) * zoom.coerceAtLeast(1f)
        val (rx, ry) = PanZoom.panRangeMm(bw, bh, zoom, w)
        val cx = (w.left + w.right) / 2 + panX.coerceIn(-1f, 1f) * rx
        val cy = (w.top + w.bottom) / 2 + panY.coerceIn(-1f, 1f) * ry
        matrix.reset()
        matrix.setScale(s, s)
        matrix.postTranslate(cx - bw * s / 2, cy - bh * s / 2)
        canvas.save()
        canvas.clipRect(w.left, w.top, w.right, w.bottom)
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
        canvas.restore()
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
        val mono = Typeface.MONOSPACE
        val hint = "Print at 100% / actual size · Grid."
        val sz = 2.1f
        drawRun(canvas, TextRun(hint, TextRole.Secondary, sz, Align.Start, x0 + 13f, y - 0.2f, 0f), mono, mutedColor)
        drawRun(canvas, TextRun("10 mm", TextRole.Secondary, sz, Align.Start, x0, y + 3f, 0f), mono, mutedColor)
    }
}
