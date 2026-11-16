package com.nbansal.grid.sheet

import com.nbansal.grid.layout.CardField
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.filter.FilterSettings
import kotlinx.serialization.Serializable
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
@Serializable
data class Slot(
    val uri: String? = null,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

@Serializable
data class Stat(val name: String, val stars: Int)

/** A printable card: a shape ([templateId]), its photo windows and its text. */
@Serializable
data class Card(
    val id: Long,
    val templateId: String = CardTemplate.Mini.id,
    val slots: List<Slot> = listOf(Slot()),
    val caption: String = "",
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate? = null,
    val location: String = "",
    /** Short template-specific text: stamp value, ticket heading, trading-card name, slide label, film edge text. */
    val label: String = defaultLabel(templateId),
    val stats: List<Stat> = defaultStats(templateId),
    /** When set, replaces the sheet style for this card. */
    val styleOverride: CardStyle? = null,
    /** When set, replaces the sheet-wide film filter for this card. */
    val filterOverride: FilterSettings? = null,
) {
    val template: CardTemplate get() = CardTemplate.byId(templateId)
    val content: FooterContent get() = FooterContent(caption, date, location)
    val filledSlots: List<Slot> get() = slots.filter { it.uri != null }
    /** The style the controls edit: the override when present, else the sheet's (still containing "Auto"). */
    fun rawStyle(sheet: CardStyle): CardStyle = styleOverride ?: sheet
    fun filterFor(sheet: FilterSettings): FilterSettings = filterOverride ?: sheet
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

/** Style plus the actual font and frame colour after "Auto" is resolved (templates can default to a colour outside [FrameColor]). */
data class ResolvedStyle(val style: CardStyle, val font: CaptionFont, val frameArgb: Int)

/**
 * "Auto" (null) font / frame colour falls back to the shape's default, then to Handwritten / white.
 * An explicit choice always wins, including white or Handwritten.
 */
fun resolveStyle(t: CardTemplate, override: CardStyle?, sheet: CardStyle): ResolvedStyle {
    val s = override ?: sheet
    return ResolvedStyle(
        s,
        s.font ?: t.defaultFont ?: CaptionFont.Handwritten,
        s.frameColor?.argb ?: t.defaultFrameArgb ?: FrameColor.White.argb,
    )
}

fun CardTemplate.uses(f: CardField) = f in fields

/** Fresh single-photo card of [t] (extra windows empty). */
fun newCard(id: Long, t: CardTemplate, uris: List<String>): Card =
    Card(id, t.id, emptySlots(t).mapIndexed { i, s -> uris.getOrNull(i)?.let { Slot(it) } ?: s })
