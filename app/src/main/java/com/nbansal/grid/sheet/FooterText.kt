package com.nbansal.grid.sheet

import com.nbansal.grid.layout.RectMm
import java.time.LocalDate

/** Text content of one card. */
data class FooterContent(val caption: String = "", val date: LocalDate? = null, val location: String = "")

enum class TextRole { Primary, Secondary }

/**
 * One positioned line of footer text. [x] is the anchor (left edge, centre or right edge depending on
 * [align]); [baseline] is the y of the text baseline. All mm, card coordinates.
 */
data class TextRun(
    val text: String,
    val role: TextRole,
    val sizeMm: Float,
    val align: Align,
    val x: Float,
    val baseline: Float,
    val widthMm: Float,
) {
    val rect: RectMm
        get() {
            val l = when (align) { Align.Start -> x; Align.Center -> x - widthMm / 2; Align.End -> x - widthMm }
            return RectMm(l, baseline - sizeMm * ASCENT, l + widthMm, baseline + sizeMm * DESCENT)
        }
}

const val ASCENT = 0.8f
const val DESCENT = 0.25f
const val BASE_CAPTION_MM = 11f * 25.4f / 72f // 11 pt
const val SECONDARY_RATIO = 0.62f
const val MIN_SHRINK = 0.7f
const val ELLIPSIS = "…"
private const val LINE_GAP_MM = 0.6f
private const val SIDE_GAP_MM = 1.5f

/** Width of [text] at [sizeMm]; the renderer supplies a real font, tests a fake. */
fun interface TextMeasurer { fun width(text: String, sizeMm: Float): Float }

/** Shrink to [MIN_SHRINK] of [sizeMm] to fit [maxW], then ellipsize. Returns (text, size, width). */
fun fitText(text: String, sizeMm: Float, maxW: Float, m: TextMeasurer): Triple<String, Float, Float> {
    val w0 = m.width(text, sizeMm)
    if (w0 <= maxW) return Triple(text, sizeMm, w0)
    val size = maxOf(sizeMm * maxW / w0, sizeMm * MIN_SHRINK)
    var w = m.width(text, size)
    if (w <= maxW) return Triple(text, size, w)
    var n = text.length
    var shown = text
    while (n > 0) {
        n--
        shown = text.take(n).trimEnd() + ELLIPSIS
        w = m.width(shown, size)
        if (w <= maxW) return Triple(shown, size, w)
    }
    return Triple("", size, 0f)
}

fun datePlace(content: FooterContent, style: CardStyle): String =
    listOfNotNull(content.date?.let { style.dateFormat.format(it) }, content.location.trim().ifEmpty { null })
        .joinToString(" · ")

/** Lays out the footer text of a card. Pure; every returned run's [TextRun.rect] lies inside [footer]. */
fun layoutFooter(footer: RectMm, style: CardStyle, content: FooterContent, m: TextMeasurer): List<TextRun> {
    val caption = content.caption.trim()
    val date = content.date?.let { style.dateFormat.format(it) } ?: ""
    val location = content.location.trim()
    val primary = BASE_CAPTION_MM * style.textSize.scale
    val secondary = primary * SECONDARY_RATIO
    val w = footer.width

    data class Line(val text: String, val role: TextRole, val size: Float)

    val lines: List<Line> = when (style.layout) {
        FooterLayout.CaptionOnly, FooterLayout.FilmStamp ->
            listOf(Line(caption, TextRole.Primary, primary))
        FooterLayout.CaptionAndDate ->
            listOf(Line(caption, TextRole.Primary, primary), Line(date, TextRole.Secondary, secondary))
        FooterLayout.CaptionDateLocation ->
            listOf(Line(caption, TextRole.Primary, primary), Line(datePlace(content, style), TextRole.Secondary, secondary))
        FooterLayout.DateOnly -> listOf(Line(date, TextRole.Primary, primary))
        FooterLayout.LocationOnly -> listOf(Line(location, TextRole.Primary, primary))
        FooterLayout.CaptionLeftDateRight, FooterLayout.Blank -> emptyList()
    }.filter { it.text.isNotEmpty() }

    fun anchor(a: Align) = when (a) { Align.Start -> footer.left; Align.Center -> footer.left + w / 2; Align.End -> footer.right }

    if (style.layout == FooterLayout.CaptionLeftDateRight) {
        val dateFit = if (date.isEmpty()) null else fitText(date, secondary, w * 0.55f, m)
        val capMax = if (dateFit == null) w else w - dateFit.third - SIDE_GAP_MM
        val capFit = if (caption.isEmpty()) null else fitText(caption, primary, capMax, m)
        val size = maxOf(capFit?.second ?: 0f, dateFit?.second ?: 0f)
        if (size == 0f) return emptyList()
        val baseline = footer.top + footer.height / 2 + (ASCENT - DESCENT) * size / 2
        val out = mutableListOf<TextRun>()
        capFit?.let { out += TextRun(it.first, TextRole.Primary, it.second, Align.Start, footer.left, baseline, it.third) }
        dateFit?.let { out += TextRun(it.first, TextRole.Secondary, it.second, Align.End, footer.right, baseline, it.third) }
        return out
    }

    if (lines.isEmpty()) return emptyList()
    val fitted = lines.map { it to fitText(it.text, it.size, w, m) }
    val heights = fitted.map { (_, f) -> f.second * (ASCENT + DESCENT) }
    val total = heights.sum() + LINE_GAP_MM * (fitted.size - 1)
    var top = footer.top + (footer.height - total) / 2
    val out = mutableListOf<TextRun>()
    fitted.forEachIndexed { i, (line, f) ->
        val baseline = top + f.second * ASCENT
        out += TextRun(f.first, line.role, f.second, style.align, anchor(style.align), baseline, f.third)
        top += heights[i] + LINE_GAP_MM
    }
    return out
}

/** Film stamp placement inside the photo window: bottom-right. */
fun filmStampRun(window: RectMm, date: LocalDate, m: TextMeasurer): TextRun {
    val size = 2.7f
    val text = filmStampText(date)
    val w = m.width(text, size)
    return TextRun(text, TextRole.Secondary, size, Align.End, window.right - 2f, window.bottom - 2f - size * DESCENT, w)
}
