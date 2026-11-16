package com.nbansal.grid.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.nbansal.grid.filter.FilmFilter
import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.RectMm
import com.nbansal.grid.layout.packSheet
import com.nbansal.grid.sheet.Align
import com.nbansal.grid.sheet.CaptionFont
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.FooterLayout
import com.nbansal.grid.sheet.FrameColor
import com.nbansal.grid.sheet.Slot
import com.nbansal.grid.sheet.TextColor
import com.nbansal.grid.sheet.defaultLabel
import com.nbansal.grid.sheet.defaultStats
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * Renders reference images with the real [SheetRenderer] and filter pipeline into app/build/renders/ so
 * output can be inspected without a device. Run: ./gradlew testDebugUnitTest --tests '*GoldenRenderTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GoldenRenderTest {
    private val out = File("build/renders").also { it.mkdirs() }
    private val context = org.robolectric.RuntimeEnvironment.getApplication()
    private val faces = HashMap<CaptionFont, Typeface>()
    private val renderer = SheetRenderer { f -> faces.getOrPut(f) { CaptionFonts.load(context, f) } }
    private val date = LocalDate.of(2026, 11, 15)

    /** Sky gradient, sun, two hills and a path: enough colour and tone to judge a filter. */
    private fun photo(w: Int = 480, h: Int = 640): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.62f, intArrayOf(0xFF2F6FB5.toInt(), 0xFF9CCBEA.toInt(), 0xFFFFE2B0.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w * 0.7f, h * 0.36f, w * 0.34f, intArrayOf(0xFFFFFFF0.toInt(), 0x66FFE9A0, 0x00FFE9A0), floatArrayOf(0.15f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(w * 0.7f, h * 0.36f, w * 0.34f, p)
        p.shader = null
        p.color = 0xFFFFF7D6.toInt()
        c.drawCircle(w * 0.7f, h * 0.36f, w * 0.07f, p)
        fun hill(color: Int, y: Float, amp: Float, ph: Float) {
            p.color = color
            val path = Path().apply {
                moveTo(0f, h.toFloat())
                var x = 0f
                while (x <= w) { lineTo(x, y + amp * Math.sin((x / w * 6.0 + ph)).toFloat()); x += 4f }
                lineTo(w.toFloat(), h.toFloat()); close()
            }
            c.drawPath(path, p)
        }
        hill(0xFF5B8C4A.toInt(), h * 0.60f, h * 0.035f, 0.5f)
        hill(0xFF2E6B3A.toInt(), h * 0.72f, h * 0.045f, 2.2f)
        hill(0xFF1B3F2A.toInt(), h * 0.86f, h * 0.03f, 4f)
        p.color = 0xFFD9B77E.toInt()
        c.drawRect(w * 0.1f, h * 0.9f, w * 0.9f, h * 0.915f, p)
        p.color = 0xFFC8452B.toInt() // a red "jacket" patch for skin/colour reference
        c.drawRect(w * 0.18f, h * 0.76f, w * 0.26f, h * 0.84f, p)
        return b
    }

    private val photo = photo()

    private fun card(t: CardTemplate, id: Long = 1, caption: String = "Golden hour", layout: FooterLayout? = null) = Card(
        id, t.id, t.photoWindows.map { Slot("test:photo") },
        caption = caption.take(t.captionMax), date = date, location = "Lisbon, Portugal",
        label = defaultLabel(t.id), stats = defaultStats(t.id),
    )

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 40); textSize = 22f; typeface = Typeface.MONOSPACE }

    private fun save(b: Bitmap, name: String) {
        FileOutputStream(File(out, name)).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(out, name).length() > 1000)
    }

    private fun drawCardAt(canvas: Canvas, card: Card, style: CardStyle, x: Float, y: Float, ppm: Float, filter: FilterSettings = FilterSettings()) {
        val bitmaps = card.slots.mapIndexed { i, _ ->
            if (filter.isIdentity) photo else filterBitmap(photo, card.template.photoWindows[i], filter, card.id * 31 + i)
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(ppm, ppm)
        renderer.drawCard(canvas, card, style, bitmaps, 1, preview = true)
        canvas.restore()
    }

    /** Lays out [tiles] (w, h px, label, draw) left to right, wrapping at [width]. */
    private fun sheet(width: Int, tiles: List<Triple<Pair<Float, Float>, String, (Canvas, Float, Float) -> Unit>>): Bitmap {
        val pad = 28f
        val labelH = 34f
        var x = pad; var y = pad; var rowH = 0f
        val placed = tiles.map { (size, label, draw) ->
            if (x + size.first + pad > width) { x = pad; y += rowH + labelH + pad; rowH = 0f }
            val pos = x to y
            x += size.first + pad; rowH = maxOf(rowH, size.second)
            Triple(pos, size, label to draw)
        }
        val height = (y + rowH + labelH + pad).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(244, 241, 234))
        for ((pos, size, ld) in placed) {
            c.save(); c.translate(pos.first, pos.second)
            ld.second(c, pos.first, pos.second)
            c.restore()
            c.drawText(ld.first, pos.first, pos.second + size.second + 24f, labelPaint)
        }
        return bmp
    }

    @Test fun shapes() {
        val ppm = 8f
        val style = CardStyle(layout = FooterLayout.CaptionDateLocation)
        val tiles = CardTemplate.All.map { t ->
            val s = if (t.id == "contact") 3f else ppm
            Triple((t.cardWidthMm * s) to (t.cardHeightMm * s), "${t.displayName} ${t.sizeLabel}" + if (s != ppm) " (3px/mm)" else "",
                { c: Canvas, _: Float, _: Float -> drawCardAt(c, card(t), style, 0f, 0f, s) })
        }
        save(sheet(3400, tiles), "shapes.png")
    }

    @Test fun mixedPage() {
        val ppm = 4f
        // Ordered so the straight-cut packer shows a real mix on page 1; the rest lands on page 2.
        val spec = listOf(
            CardTemplate.Classic to 2, CardTemplate.Wide to 1, CardTemplate.Mini to 1, CardTemplate.Stamp to 5,
            CardTemplate.Mini to 3, CardTemplate.Square to 1, CardTemplate.Ticket to 1, CardTemplate.PhotoStrip to 1,
        )
        val cards = ArrayList<Card>()
        spec.forEach { (t, n) -> repeat(n) { cards += card(t, cards.size + 1L, caption = "No. ${cards.size + 1}") } }
        val pages = packSheet(cards.map { it.template }, false)
        val paper = Paper.A4
        pages.take(2).forEachIndexed { i, page ->
            val bmp = Bitmap.createBitmap((paper.widthMm * ppm).toInt(), (paper.heightMm * ppm).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.scale(ppm, ppm)
            renderer.drawPage(canvas, paper, page, cards, CardStyle(layout = FooterLayout.CaptionAndDate), { _, _ -> photo }, preview = true)
            save(bmp, if (i == 0) "mixed_page.png" else "mixed_page_2.png")
        }
        println("mixed: ${cards.size} cards, ${pages.size} pages")
    }

    @Test fun filters() {
        val t = CardTemplate.Mini
        val win: RectMm = t.window
        val ppm = 8f
        val tiles = FilmFilter.All.map { f ->
            val s = FilterSettings().withFilter(f).copy(grain = true)
            Triple((win.width * ppm) to (win.height * ppm), f.name + if (f.inspiredBy.isNotEmpty()) " · ${f.inspiredBy}" else "", { c: Canvas, _: Float, _: Float ->
                val bm = if (s.isIdentity) photo else filterBitmap(photo, win, s, 7)
                c.scale(win.width * ppm / bm.width, win.height * ppm / bm.height)
                c.drawBitmap(bm, 0f, 0f, null)
            })
        }
        save(sheet(1650, tiles), "filters.png")
    }

    @Test fun styles() {
        val ppm = 6f
        val t = CardTemplate.Mini
        val tiles = ArrayList<Triple<Pair<Float, Float>, String, (Canvas, Float, Float) -> Unit>>()
        for (l in FooterLayout.entries) {
            tiles += Triple((t.cardWidthMm * ppm) to (t.cardHeightMm * ppm), l.label, { c: Canvas, _: Float, _: Float ->
                drawCardAt(c, card(t), CardStyle(layout = l), 0f, 0f, ppm)
            })
        }
        val looks = listOf(
            Triple("Serif · Black", CardStyle(font = CaptionFont.Serif, frameColor = FrameColor.Black, layout = FooterLayout.CaptionAndDate), 0),
            Triple("Marker · Cream", CardStyle(font = CaptionFont.Marker, frameColor = FrameColor.Cream, textColor = TextColor.Red, layout = FooterLayout.CaptionLeftDateRight), 0),
            Triple("Typewriter · Sage", CardStyle(font = CaptionFont.Typewriter, frameColor = FrameColor.Sage, layout = FooterLayout.CaptionDateLocation, align = Align.Start), 0),
            Triple("Script · Blush", CardStyle(font = CaptionFont.Script, frameColor = FrameColor.Blush, textColor = TextColor.PenBlue), 0),
            Triple("Pencil · Sky", CardStyle(font = CaptionFont.Pencil, frameColor = FrameColor.Sky, textColor = TextColor.Pencil, layout = FooterLayout.CaptionAndDate), 0),
            Triple("Mono · White", CardStyle(font = CaptionFont.Mono, frameColor = FrameColor.White, layout = FooterLayout.CaptionDateLocation), 0),
        )
        for ((name, st, _) in looks) {
            tiles += Triple((t.cardWidthMm * ppm) to (t.cardHeightMm * ppm), name, { c: Canvas, _: Float, _: Float ->
                drawCardAt(c, card(t), st, 0f, 0f, ppm)
            })
        }
        save(sheet(1700, tiles), "styles.png")
    }
}
