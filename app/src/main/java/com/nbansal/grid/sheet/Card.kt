package com.nbansal.grid.sheet

import com.nbansal.grid.layout.CardField
import com.nbansal.grid.layout.CardTemplate
import java.time.LocalDate

const val MAX_PHOTOS = 90
const val MAX_CAPTION = 28
const val MAX_LOCATION = 40
const val MAX_LABEL = 24
const val MAX_STAT_NAME = 12

/**
 * One photo window of a card. [uri] is null for an empty window. [panX]/[panY] are normalized to [-1, 1]
 * across the overflow of the zoomed image beyond the window. Plain data (Uri kept as a string) so the
 * whole model can be serialized in Stage C.
 */
data class Slot(
    val uri: String? = null,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

data class Stat(val name: String, val stars: Int)

/** A printable card: a shape ([templateId]), its photo windows and its text. */
data class Card(
    val id: Long,
    val templateId: String = CardTemplate.Mini.id,
    val slots: List<Slot> = listOf(Slot()),
    val caption: String = "",
    val date: LocalDate? = null,
    val location: String = "",
    /** Short template-specific text: stamp value, ticket heading, trading-card name, slide label, film edge text. */
    val label: String = defaultLabel(templateId),
    val stats: List<Stat> = defaultStats(templateId),
    /** When set, replaces the sheet style for this card. */
    val styleOverride: CardStyle? = null,
) {
    val template: CardTemplate get() = CardTemplate.byId(templateId)
    val content: FooterContent get() = FooterContent(caption, date, location)
    val filledSlots: List<Slot> get() = slots.filter { it.uri != null }
    fun styleFor(sheet: CardStyle): CardStyle = resolveStyle(sheet).style
    fun resolveStyle(sheet: CardStyle): ResolvedStyle = resolveStyle(template, styleOverride, sheet)
}

fun defaultLabel(templateId: String): String = when (templateId) {
    "stamp" -> "₹5"
    "ticket" -> "ADMIT ONE"
    "trading" -> "PLAYER ONE"
    "film" -> "GRID 400"
    "slide" -> "SLIDE"
    else -> ""
}

fun defaultStats(templateId: String): List<Stat> =
    if (templateId == "trading") listOf(Stat("Coffee", 4), Stat("Vibes", 5), Stat("Rain", 2)) else emptyList()

fun emptySlots(t: CardTemplate): List<Slot> = List(t.windowCount) { Slot() }

/** Style plus the actual frame colour (templates can default to a colour that is not in [FrameColor]). */
data class ResolvedStyle(val style: CardStyle, val frameArgb: Int)

/**
 * Template defaults apply only while the card has no override and the sheet still uses the default
 * white frame / default font, so a user choice always wins.
 */
fun resolveStyle(t: CardTemplate, override: CardStyle?, sheet: CardStyle): ResolvedStyle {
    if (override != null) return ResolvedStyle(override, override.frameColor.argb)
    val dflt = CardStyle()
    val font = if (t.defaultFont != null && sheet.font == dflt.font) t.defaultFont else sheet.font
    val frame = if (t.defaultFrameArgb != null && sheet.frameColor == dflt.frameColor) t.defaultFrameArgb else sheet.frameColor.argb
    return ResolvedStyle(sheet.copy(font = font), frame)
}

fun CardTemplate.uses(f: CardField) = f in fields

/** Fresh single-photo card of [t] (extra windows empty). */
fun newCard(id: Long, t: CardTemplate, uris: List<String>): Card =
    Card(id, t.id, emptySlots(t).mapIndexed { i, s -> uris.getOrNull(i)?.let { Slot(it) } ?: s })
