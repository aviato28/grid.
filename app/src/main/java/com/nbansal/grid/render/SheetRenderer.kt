package com.nbansal.grid.render

import android.graphics.Bitmap
import kotlin.math.roundToInt
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.graphics.DashPathEffect
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.ContactGeometry
import com.nbansal.grid.layout.Decoration
import com.nbansal.grid.layout.PRINT_AREA
import com.nbansal.grid.layout.PanZoom
import com.nbansal.grid.layout.RectMm
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.sheet.CaptionFont
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.FILM_STAMP_ARGB
import com.nbansal.grid.sheet.FooterLayout
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.Slot
import com.nbansal.grid.sheet.wrapToFit
import com.nbansal.grid.sheet.fitText
import com.nbansal.grid.sheet.BASE_CAPTION_MM
import com.nbansal.grid.sheet.ASCENT
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

private const val INK = 0xFF141414.toInt()
private const val ORANGE = 0xFFFF8A3D.toInt()
private const val ACCENT = 0xFFFF5A1F.toInt()

/**
 * The single renderer for both the on-screen preview and the PDF. Everything is in millimetres:
 * callers must scale the canvas to px/mm (or points/mm) before calling.
 *
 * Stage C hook: apply the film filter (`CardStyle.filter`) to the bitmap/Paint inside [drawWindow].
 */
class SheetRenderer(private val typefaceFor: (CaptionFont) -> Typeface) {
    private val lightOutline = Color.parseColor("#E0DCD3")
    private val darkOutline = Color.parseColor("#9A9A9A")
    private val mutedColor = Color.parseColor("#6D685D")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val placeholder = Paint().apply { style = Paint.Style.FILL; color = Color.parseColor("#EDEAE3") }
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.parseColor("#B8B3A8"); strokeCap = Paint.Cap.ROUND
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.1f }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.2f }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val marks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.15f; color = Color.BLACK
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true; isSubpixelText = true; isLinearText = true
    }
    private val matrix = Matrix()
    private val rectF = RectF()
    private val path = Path()

    private fun measurer(tf: Typeface) = TextMeasurer { text, sizeMm ->
        textPaint.typeface = tf
        textPaint.textSize = sizeMm * K
        textPaint.isFakeBoldText = false
        textPaint.measureText(text) / K
    }

    private fun drawText(
        canvas: Canvas, text: String, x: Float, baseline: Float, sizeMm: Float, tf: Typeface, color: Int,
        align: Align = Align.Start, bold: Boolean = false,
    ) {
        if (text.isEmpty()) return
        textPaint.typeface = tf
        textPaint.textSize = sizeMm * K
        textPaint.color = color
        textPaint.isFakeBoldText = bold
        textPaint.textAlign = when (align) {
            Align.Start -> Paint.Align.LEFT; Align.Center -> Paint.Align.CENTER; Align.End -> Paint.Align.RIGHT
        }
        canvas.save()
        canvas.scale(1f / K, 1f / K)
        canvas.drawText(text, x * K, baseline * K, textPaint)
        canvas.restore()
        textPaint.isFakeBoldText = false
    }

    private fun drawRun(canvas: Canvas, run: TextRun, tf: Typeface, color: Int) =
        drawText(canvas, run.text, run.x, run.baseline, run.sizeMm, tf, color, run.align)

    private fun rr(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, p: Paint) {
        rectF.set(l, t, r, b)
        canvas.drawRoundRect(rectF, rad, rad, p)
    }

    private fun fillColor(c: Int) { fill.color = c }

    /** Number of earlier cards (in sheet order) sharing the template of card [i], plus one. */
    private fun ordinalOf(cards: List<Card>, i: Int): Int =
        1 + (0 until i).count { cards[it].templateId == cards[i].templateId }

    @Synchronized
    fun drawPage(
        canvas: Canvas, paper: Paper, page: SheetPage, cards: List<Card>, sheetStyle: CardStyle,
        bitmapFor: (String) -> Bitmap?, preview: Boolean = true,
    ) {
        fill.color = Color.WHITE
        canvas.drawRect(0f, 0f, paper.widthMm, paper.heightMm, fill)
        for (m in page.marks) canvas.drawLine(m.x1, m.y1, m.x2, m.y2, marks)
        for (item in page.items) {
            val card = cards.getOrNull(item.index) ?: continue
            canvas.save()
            if (item.rotated) {
                // Rotate 90 degrees clockwise about the card's top-left so the card fills the rect.
                canvas.translate(item.rect.right, item.rect.top)
                canvas.rotate(90f)
            } else {
                canvas.translate(item.rect.left, item.rect.top)
            }
            drawCard(canvas, card, sheetStyle, card.slots.map { s -> s.uri?.let(bitmapFor) }, ordinalOf(cards, item.index), preview)
            canvas.restore()
        }
        drawFooter(canvas)
    }

    /**
     * Draws one card with its top-left at the canvas origin. [bitmaps] is aligned with [Card.slots];
     * [ordinal] is the 1-based position among cards of the same shape (ticket numbers, film frame numbers).
     * Empty windows show a placeholder with "+" only when [preview] is true; in the PDF they stay blank.
     */
    @Synchronized
    fun drawCard(
        canvas: Canvas, card: Card, sheetStyle: CardStyle, bitmaps: List<Bitmap?>,
        ordinal: Int = 1, preview: Boolean = true,
    ) {
        val t = card.template
        val rs = card.resolveStyle(sheetStyle)
        val style = rs.style
        val dark = t.decoration == Decoration.FilmStrip || t.decoration == Decoration.ContactSheet
        val frame = if (dark) INK else rs.frameArgb
        val w = t.cardWidthMm
        val h = t.cardHeightMm
        val radius = when (t.decoration) { Decoration.TradingCard -> 3.5f; Decoration.SlideMount -> 2.5f; else -> 0f }
        fillColor(frame)
        outline.color = if (isDarkColor(frame)) darkOutline else lightOutline
        if (radius > 0f) {
            rr(canvas, 0f, 0f, w, h, radius, fill)
            rr(canvas, 0f, 0f, w, h, radius, outline)
        } else {
            canvas.drawRect(0f, 0f, w, h, fill)
            canvas.drawRect(0f, 0f, w, h, outline)
        }
        val tf = typefaceFor(style.font)
        val mono = typefaceFor(CaptionFont.Mono)
        val textColor = if (dark) Color.WHITE else effectiveTextColor(style, frame)
        val ctx = Ctx(card, t, style, frame, textColor, tf, mono, ordinal)

        if (t.decoration == Decoration.TradingCard) {
            fillColor(Color.WHITE)
            rr(canvas, 2.5f, 2.5f, w - 2.5f, h - 2.5f, 2f, fill)
        }
        t.photoWindows.forEachIndexed { i, win ->
            drawWindow(canvas, bitmaps.getOrNull(i), card.slots.getOrNull(i) ?: Slot(), win, t.windowCornerMm, preview)
        }
        when (t.decoration) {
            Decoration.Plain -> decoratePlain(canvas, ctx)
            Decoration.FilmStrip -> decorateFilm(canvas, ctx)
            Decoration.Stamp -> decorateStamp(canvas, ctx)
            Decoration.Ticket -> decorateTicket(canvas, ctx)
            Decoration.Postcard -> decoratePostcard(canvas, ctx)
            Decoration.Bookmark -> decorateBookmark(canvas, ctx)
            Decoration.TradingCard -> decorateTrading(canvas, ctx)
            Decoration.SlideMount -> decorateSlide(canvas, ctx)
            Decoration.ContactSheet -> decorateContact(canvas, ctx)
        }
    }

    private class Ctx(
        val card: Card, val t: CardTemplate, val style: CardStyle, val frame: Int, val text: Int,
        val tf: Typeface, val mono: Typeface, val ordinal: Int,
    )

    private fun decoratePlain(canvas: Canvas, c: Ctx) {
        val content = c.card.content
        if (c.style.layout == FooterLayout.FilmStamp && content.date != null) {
            drawRun(canvas, filmStampRun(c.t.window, content.date, measurer(c.mono)), c.mono, FILM_STAMP_ARGB)
        }
        val footer = c.t.footer ?: return
        val m = measurer(c.tf)
        for (run in layoutFooter(footer, c.style, content, m)) {
            drawRun(canvas, run, c.tf, if (run.role == TextRole.Secondary) secondaryColor(c.text) else c.text)
        }
    }

    private fun holes(canvas: Canvas, count: Int, width: Float, x0: Float, y: Float, hw: Float, hh: Float) {
        fillColor(Color.WHITE)
        val pitch = width / count
        for (i in 0 until count) {
            val x = x0 + i * pitch + (pitch - hw) / 2
            rr(canvas, x, y, x + hw, y + hh, 0.5f, fill)
        }
    }

    private fun decorateFilm(canvas: Canvas, c: Ctx) {
        val w = c.t.cardWidthMm
        holes(canvas, 40, w, 0f, 1.2f, 2f, 2.4f)
        holes(canvas, 40, w, 0f, 31.4f, 2f, 2.4f)
        val label = c.card.label.trim().uppercase()
        c.t.photoWindows.forEachIndexed { i, win ->
            drawText(canvas, label, win.left, 5.1f, 1.5f, c.mono, ORANGE)
            val n = 3 * (c.ordinal - 1) + i + 1
            drawText(canvas, "$n ▸ ${n}A", (win.left + win.right) / 2, 30.6f, 1.7f, c.mono, ORANGE, Align.Center)
        }
    }

    private fun decorateStamp(canvas: Canvas, c: Ctx) {
        val w = c.t.cardWidthMm
        val h = c.t.cardHeightMm
        fillColor(Color.WHITE)
        for (len in listOf(w, h)) {
            val n = (len / 2.8f).roundToInt()
            val pitch = len / n
            for (i in 0..n) {
                val p = i * pitch
                if (len == w) { canvas.drawCircle(p, 0f, 1f, fill); canvas.drawCircle(p, h, 1f, fill) }
                else { canvas.drawCircle(0f, p, 1f, fill); canvas.drawCircle(w, p, 1f, fill) }
            }
        }
        val base = 43.4f
        val label = c.card.label.trim()
        val labelW = if (label.isEmpty()) 0f else measurer(c.tf).width(label, 3.4f)
        drawText(canvas, label, 36f, base, 3.4f, c.tf, c.text, Align.End, bold = true)
        val cap = c.card.caption.trim()
        if (cap.isNotEmpty()) {
            val (txt, size, _) = fitText(cap, 2.6f, 32f - labelW - 1.5f, measurer(c.tf))
            drawText(canvas, txt, 4f, base, size, c.tf, c.text)
        }
    }

    private fun decorateTicket(canvas: Canvas, c: Ctx) {
        val w = c.t.cardWidthMm
        val h = c.t.cardHeightMm
        fillColor(Color.WHITE)
        for ((x, y) in listOf(0f to h / 2, w to h / 2, 90f to 0f, 90f to h)) canvas.drawCircle(x, y, 2.4f, fill)
        stroke.color = (c.text and 0x00FFFFFF) or (0x80 shl 24)
        stroke.strokeWidth = 0.35f
        stroke.pathEffect = DashPathEffect(floatArrayOf(0.4f, 1.1f), 0f)
        stroke.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(90f, 4.5f, 90f, h - 4.5f, stroke)
        stroke.pathEffect = null; stroke.strokeWidth = 0.2f; stroke.strokeCap = Paint.Cap.BUTT
        val m = measurer(c.tf)
        val label = c.card.label.trim().uppercase()
        if (label.isNotEmpty()) {
            val (txt, size, _) = fitText(label, 6f, 32f, m)
            drawText(canvas, txt, 56f, 15f, size, c.tf, c.text, bold = true)
        }
        val cap = c.card.caption.trim()
        if (cap.isNotEmpty()) {
            val (txt, size, _) = fitText(cap, 4f, 32f, m)
            drawText(canvas, txt, 56f, 23f, size, c.tf, c.text)
        }
        val sec = secondaryColor(c.text)
        val mm = measurer(c.mono)
        c.card.date?.let {
            val (txt, size, _) = fitText("DATE " + c.style.dateFormat.format(it).uppercase(), 2.4f, 32f, mm)
            drawText(canvas, txt, 56f, 36f, size, c.mono, sec)
        }
        val loc = c.card.location.trim()
        if (loc.isNotEmpty()) {
            val (txt, size, _) = fitText("LOC " + loc.uppercase(), 2.4f, 32f, mm)
            drawText(canvas, txt, 56f, 41f, size, c.mono, sec)
        }
        canvas.save()
        canvas.translate(105f, h / 2)
        canvas.rotate(90f)
        drawText(canvas, "No. %04d".format(c.ordinal), 0f, 1f, 3.2f, c.mono, sec, Align.Center)
        canvas.restore()
    }

    private fun decoratePostcard(canvas: Canvas, c: Ctx) {
        val m = measurer(c.tf)
        val base = BASE_CAPTION_MM * c.style.textSize.scale
        val note = wrapToFit(c.card.caption.trim(), 40f, 42f, base, 2.4f, m)
        note.lines.forEachIndexed { i, line ->
            drawText(canvas, line, 103f, 24f + note.sizeMm * ASCENT + i * note.lineHeightMm, note.sizeMm, c.tf, c.text)
        }
        stroke.color = (c.text and 0x00FFFFFF) or (0x33 shl 24)
        for (y in listOf(72f, 79f, 86f, 93f)) canvas.drawLine(103f, y, 143f, y, stroke)
        stroke.color = mutedColor
        stroke.pathEffect = DashPathEffect(floatArrayOf(1f, 0.8f), 0f)
        stroke.strokeWidth = 0.3f
        canvas.drawRect(127f, 5f, 143f, 21f, stroke)
        stroke.pathEffect = null; stroke.strokeWidth = 0.2f
        drawText(canvas, "STAMP", 135f, 13.7f, 2f, c.mono, mutedColor, Align.Center)
    }

    private fun decorateBookmark(canvas: Canvas, c: Ctx) {
        val m = measurer(c.tf)
        val base = BASE_CAPTION_MM * c.style.textSize.scale
        val q = wrapToFit(c.card.caption.trim(), 42f, 36f, base, 2.4f, m)
        q.lines.forEachIndexed { i, line ->
            drawText(canvas, line, 25f, 101f + q.sizeMm * ASCENT + i * q.lineHeightMm, q.sizeMm, c.tf, c.text, Align.Center)
        }
        stroke.color = ACCENT
        stroke.strokeWidth = 0.5f
        canvas.drawLine(20f, 142f, 30f, 142f, stroke)
        stroke.strokeWidth = 0.2f
    }

    private fun decorateTrading(canvas: Canvas, c: Ctx) {
        fillColor(INK)
        rr(canvas, 5f, 5f, 58f, 12f, 1f, fill)
        val label = c.card.label.trim().uppercase()
        if (label.isNotEmpty()) {
            val (txt, size, _) = fitText(label, 3.6f, 49f, measurer(c.tf))
            drawText(canvas, txt, 31.5f, 9.9f, size, c.tf, Color.WHITE, Align.Center, bold = true)
        }
        fillColor(0xFFF6F3EA.toInt())
        rr(canvas, 6f, 53f, 57f, 83f, 1f, fill)
        outline.color = lightOutline
        rr(canvas, 6f, 53f, 57f, 83f, 1f, outline)
        c.card.stats.take(3).forEachIndexed { i, s ->
            val y = 60.5f + i * 8f
            drawText(canvas, s.name, 8.5f, y, 3.2f, c.mono, INK)
            val stars = "★".repeat(s.stars.coerceIn(0, 5)) + "☆".repeat(5 - s.stars.coerceIn(0, 5))
            drawText(canvas, stars, 54.5f, y, 3.2f, c.mono, INK, Align.End)
        }
    }

    private fun decorateSlide(canvas: Canvas, c: Ctx) {
        drawText(canvas, c.card.label.trim().uppercase(), 25f, 47.2f, 2.1f, c.mono, mutedColor, Align.Center)
    }

    private fun decorateContact(canvas: Canvas, c: Ctx) {
        val label = c.card.label.trim().uppercase()
        drawText(canvas, if (label.isEmpty()) "CONTACT SHEET" else "CONTACT SHEET · $label", 6f, 9f, 3.6f, c.mono, Color.WHITE)
        for (r in 0 until ContactGeometry.ROWS) {
            val top = ContactGeometry.rowTop(r)
            holes(canvas, 50, c.t.cardWidthMm, 0f, top + 1.2f, 2.2f, 2.4f)
            holes(canvas, 50, c.t.cardWidthMm, 0f, top + 38.8f, 2.2f, 2.4f)
            for (col in 0 until ContactGeometry.COLS) {
                val win = c.t.photoWindows[r * ContactGeometry.COLS + col]
                drawText(canvas, "${r * ContactGeometry.COLS + col + 1}", win.left, top + 37.6f, 1.7f, c.mono, ORANGE)
            }
        }
    }

    private fun drawWindow(canvas: Canvas, bitmap: Bitmap?, slot: Slot, w: RectMm, corner: Float, preview: Boolean) {
        canvas.save()
        if (corner > 0f) {
            path.reset()
            rectF.set(w.left, w.top, w.right, w.bottom)
            path.addRoundRect(rectF, corner, corner, Path.Direction.CW)
            canvas.clipPath(path)
        } else {
            canvas.clipRect(w.left, w.top, w.right, w.bottom)
        }
        if (bitmap == null) {
            if (slot.uri == null && !preview) {
                // blank in the PDF
            } else {
                canvas.drawRect(w.left, w.top, w.right, w.bottom, placeholder)
                if (slot.uri == null) {
                    val cx = (w.left + w.right) / 2
                    val cy = (w.top + w.bottom) / 2
                    val a = minOf(w.width, w.height) * 0.12f
                    canvas.drawLine(cx - a, cy, cx + a, cy, plus)
                    canvas.drawLine(cx, cy - a, cx, cy + a, plus)
                }
            }
            canvas.restore()
            return
        }
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val s = PanZoom.baseScale(bw, bh, w) * slot.zoom.coerceAtLeast(1f)
        val (rx, ry) = PanZoom.panRangeMm(bw, bh, slot.zoom, w)
        val cx = (w.left + w.right) / 2 + slot.panX.coerceIn(-1f, 1f) * rx
        val cy = (w.top + w.bottom) / 2 + slot.panY.coerceIn(-1f, 1f) * ry
        matrix.reset()
        matrix.setScale(s, s)
        matrix.postTranslate(cx - bw * s / 2, cy - bh * s / 2)
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
        canvas.restore()
    }

    /** 10 mm calibration ruler (1 mm ticks) and a hint, in the reserved bottom zone (below y = 280). */
    private fun drawFooter(canvas: Canvas) {
        val x0 = PRINT_AREA.left
        val y = 288f
        canvas.drawLine(x0, y, x0 + 10f, y, marks)
        for (i in 0..10) {
            val h = if (i == 0 || i == 5 || i == 10) 2.2f else 1.2f
            canvas.drawLine(x0 + i, y - h, x0 + i, y, marks)
        }
        val mono = Typeface.MONOSPACE
        val hint = "Print at 100% / actual size · Grid."
        val sz = 2.1f
        drawText(canvas, hint, x0 + 13f, y - 0.2f, sz, mono, mutedColor)
        drawText(canvas, "10 mm", x0, y + 3f, sz, mono, mutedColor)
    }
}
