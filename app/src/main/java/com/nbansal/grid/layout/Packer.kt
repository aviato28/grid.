package com.nbansal.grid.layout

import kotlin.math.floor
import kotlin.math.roundToInt

/** Printable area on A4 (mm): the bottom 17 mm are reserved for the ruler and hint. */
val PRINT_AREA = RectMm(5f, 5f, 205f, 280f)

/** Rotation is only worth it when it fits at least this many times more cards per page. */
const val ROTATE_MIN_GAIN = 1.2f

/** One card on a page. [rect] is the on-page bounding box (already swapped for rotated cards). */
data class PlacedCard(val index: Int, val rect: RectMm, val rotated: Boolean)

data class CropMark(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

data class SheetPage(
    val items: List<PlacedCard>,
    /** Bounding box of all placed cards. */
    val bounds: RectMm,
    val marks: List<CropMark>,
)

const val CROP_MARK_LENGTH_MM = 4f
const val CROP_MARK_OFFSET_MM = 1f

fun gridCount(available: Float, size: Float): Int = floor((available + 1e-4f) / size).toInt()

private fun perPage(area: RectMm, w: Float, h: Float) =
    gridCount(area.width, w) * gridCount(area.height, h)

/** Whether cards of [t] are laid rotated by 90 degrees: only if that fits clearly more per page. */
fun chooseRotation(t: CardTemplate, area: RectMm = PRINT_AREA): Boolean {
    if (!t.rotatable) return false
    val plain = perPage(area, t.cardWidthMm, t.cardHeightMm)
    val rot = perPage(area, t.cardHeightMm, t.cardWidthMm)
    return rot > plain && rot >= plain * ROTATE_MIN_GAIN
}

/**
 * Shelf packer. [templates] is one entry per card in sheet order; the result refers to cards by that index.
 * With [groupByShape] cards are stably grouped by template (order of first appearance).
 */
fun packSheet(
    templates: List<CardTemplate>,
    groupByShape: Boolean = true,
    area: RectMm = PRINT_AREA,
): List<SheetPage> {
    if (templates.isEmpty()) return emptyList()
    val order = templates.indices.toList().let { idx ->
        if (!groupByShape) idx else {
            val firstSeen = LinkedHashMap<String, Int>()
            templates.forEach { firstSeen.getOrPut(it.id) { firstSeen.size } }
            idx.sortedBy { firstSeen.getValue(templates[it].id) } // sortedBy is stable
        }
    }
    val rotation = HashMap<String, Boolean>()
    class Item(val index: Int, val w: Float, val h: Float, val rotated: Boolean)
    val items = order.map { i ->
        val t = templates[i]
        val rot = rotation.getOrPut(t.id) { chooseRotation(t, area) }
        Item(i, if (rot) t.cardHeightMm else t.cardWidthMm, if (rot) t.cardWidthMm else t.cardHeightMm, rot)
    }

    // Shelves, then pages of shelves.
    val pages = mutableListOf<MutableList<MutableList<Item>>>()
    var shelf = mutableListOf<Item>()
    var shelfW = 0f
    var shelfH = 0f
    var pageH = 0f
    var page = mutableListOf<MutableList<Item>>()
    fun closeShelf() {
        if (shelf.isEmpty()) return
        page += shelf
        pageH += shelfH
        shelf = mutableListOf(); shelfW = 0f; shelfH = 0f
    }
    for (it in items) {
        if (shelf.isNotEmpty() && shelfW + it.w > area.width + 1e-3f) {
            closeShelf()
        }
        if (shelf.isEmpty() && page.isNotEmpty() && pageH + it.h > area.height + 1e-3f) {
            pages += page; page = mutableListOf(); pageH = 0f
        }
        // A taller card joining an existing shelf could push the page over; start a new shelf/page then.
        if (shelf.isNotEmpty() && pageH + maxOf(shelfH, it.h) > area.height + 1e-3f) {
            closeShelf()
            pages += page; page = mutableListOf(); pageH = 0f
        }
        shelf.add(it); shelfW += it.w; shelfH = maxOf(shelfH, it.h)
    }
    closeShelf()
    if (page.isNotEmpty()) pages += page

    return pages.map { shelves ->
        val blockH = shelves.sumOf { s -> s.maxOf { it.h }.toDouble() }.toFloat()
        var y = area.top + (area.height - blockH) / 2
        val placed = mutableListOf<PlacedCard>()
        val shelfRects = mutableListOf<List<RectMm>>()
        val ys = mutableListOf(y)
        for (s in shelves) {
            val w = s.sumOf { it.w.toDouble() }.toFloat()
            val h = s.maxOf { it.h }
            var x = area.left + (area.width - w) / 2
            val rects = s.map {
                val r = RectMm(x, y, x + it.w, y + it.h)
                placed += PlacedCard(it.index, r, it.rotated)
                x += it.w
                r
            }
            shelfRects += rects
            y += h
            ys += y
        }
        val bounds = RectMm(
            placed.minOf { it.rect.left }, placed.minOf { it.rect.top },
            placed.maxOf { it.rect.right }, placed.maxOf { it.rect.bottom },
        )
        PageBuild(placed, bounds, shelfRects, ys).toPage()
    }
}

private class PageBuild(
    val placed: List<PlacedCard>, val bounds: RectMm, val shelves: List<List<RectMm>>, val ys: List<Float>,
) {
    fun toPage(): SheetPage {
        fun distinct(v: List<Float>) = v.distinctBy { (it * 100).roundToInt() }
        val o = CROP_MARK_OFFSET_MM
        val l = CROP_MARK_LENGTH_MM
        val marks = mutableListOf<CropMark>()
        for (y in distinct(ys)) {
            marks += CropMark(PRINT_AREA.left - o - l, y, PRINT_AREA.left - o, y)
            marks += CropMark(PRINT_AREA.right + o, y, PRINT_AREA.right + o + l, y)
        }
        for (x in distinct(shelves.first().flatMap { listOf(it.left, it.right) })) {
            marks += CropMark(x, bounds.top - o - l, x, bounds.top - o)
        }
        val last = shelves.last()
        val lastBottom = last.maxOf { it.bottom }
        for (x in distinct(last.flatMap { listOf(it.left, it.right) })) {
            marks += CropMark(x, lastBottom + o, x, lastBottom + o + l)
        }
        return SheetPage(placed, bounds, marks)
    }
}
