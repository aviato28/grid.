package com.nbansal.grid.layout

import kotlin.math.floor
import kotlin.math.roundToInt

/** All units are millimetres. No Android imports so this is unit-testable on the JVM. */
data class Paper(val widthMm: Float, val heightMm: Float) {
    companion object {
        val A4 = Paper(210f, 297f)
    }
}

data class RectMm(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float) = x >= left && x <= right && y >= top && y <= bottom
}

enum class CardGroup { Instant, Prints, Novelty, Extras }

/** Extra artwork drawn around a card. Stage B adds FilmStrip, Stamp, Ticket, Postcard, Bookmark, TradingCard, SlideMount, ContactSheet. */
enum class Decoration { Plain }

data class SizeMm(val width: Float, val height: Float)

/** A card shape as data. All rects are relative to the card's top-left, in mm. */
data class CardTemplate(
    val id: String,
    val displayName: String,
    val group: CardGroup,
    val sizeMm: SizeMm,
    val photoWindows: List<RectMm>,
    /** Strip below/around the photos that carries caption/date/location text; null if the shape has none. */
    val footer: RectMm?,
    val decoration: Decoration = Decoration.Plain,
) {
    val cardWidthMm get() = sizeMm.width
    val cardHeightMm get() = sizeMm.height

    /** The first (for now only) photo window. */
    val window get() = photoWindows.first()

    companion object {
        val Mini = CardTemplate(
            id = "mini",
            displayName = "Mini",
            group = CardGroup.Instant,
            sizeMm = SizeMm(54f, 86f),
            photoWindows = listOf(RectMm(4f, 6f, 50f, 68f)),
            footer = RectMm(4f, 68f, 50f, 86f),
        )

        /** Registry; Stage B appends the other shapes here. */
        val All: List<CardTemplate> = listOf(Mini)

        fun byId(id: String): CardTemplate = All.firstOrNull { it.id == id } ?: Mini
    }
}

data class SheetPage(
    /** Index of the first photo on this page (in the overall photo list). */
    val firstIndex: Int,
    val cards: List<RectMm>,
    /** Bounds of the full grid (also when the page is only partly filled). */
    val grid: RectMm,
)

fun gridCount(available: Float, size: Float, gap: Float): Int =
    floor((available + gap + 1e-4f) / (size + gap)).toInt()

fun layoutSheet(
    paper: Paper,
    template: CardTemplate,
    count: Int,
    marginMm: Float = 5f,
    gapMm: Float = 0f,
): List<SheetPage> {
    if (count <= 0) return emptyList()
    val cols = gridCount(paper.widthMm - 2 * marginMm, template.cardWidthMm, gapMm)
    val rows = gridCount(paper.heightMm - 2 * marginMm, template.cardHeightMm, gapMm)
    require(cols >= 1 && rows >= 1) { "Card does not fit on paper" }
    val gridW = cols * template.cardWidthMm + (cols - 1) * gapMm
    val gridH = rows * template.cardHeightMm + (rows - 1) * gapMm
    val x0 = (paper.widthMm - gridW) / 2
    val y0 = (paper.heightMm - gridH) / 2
    val grid = RectMm(x0, y0, x0 + gridW, y0 + gridH)
    val perPage = cols * rows
    return (0 until count step perPage).map { first ->
        val n = minOf(perPage, count - first)
        val cards = (0 until n).map { i ->
            val l = x0 + (i % cols) * (template.cardWidthMm + gapMm)
            val t = y0 + (i / cols) * (template.cardHeightMm + gapMm)
            RectMm(l, t, l + template.cardWidthMm, t + template.cardHeightMm)
        }
        SheetPage(first, cards, grid)
    }
}

data class CropMark(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

const val CROP_MARK_LENGTH_MM = 4f
const val CROP_MARK_OFFSET_MM = 1f

/**
 * Marks for every distinct card-edge line of the page, drawn in the margin outside the grid
 * (1 mm away from it, 4 mm long) so they never touch a card.
 */
fun cropMarks(page: SheetPage): List<CropMark> {
    fun distinct(values: List<Float>) = values.distinctBy { (it * 100).roundToInt() }
    val xs = distinct(page.cards.flatMap { listOf(it.left, it.right) })
    val ys = distinct(page.cards.flatMap { listOf(it.top, it.bottom) })
    val g = page.grid
    val o = CROP_MARK_OFFSET_MM
    val l = CROP_MARK_LENGTH_MM
    val marks = mutableListOf<CropMark>()
    for (x in xs) {
        marks += CropMark(x, g.top - o - l, x, g.top - o)
        marks += CropMark(x, g.bottom + o, x, g.bottom + o + l)
    }
    for (y in ys) {
        marks += CropMark(g.left - o - l, y, g.left - o, y)
        marks += CropMark(g.right + o, y, g.right + o + l, y)
    }
    return marks
}
