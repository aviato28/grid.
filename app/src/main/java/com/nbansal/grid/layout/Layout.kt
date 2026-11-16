package com.nbansal.grid.layout


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

enum class CardGroup(val label: String) { Instant("Instant"), Prints("Prints"), Novelty("Novelty"), Extras("Extras") }

/** Extra artwork drawn around a card by the renderer. */
enum class Decoration { Plain, FilmStrip, Stamp, Ticket, Postcard, Bookmark, TradingCard, SlideMount, ContactSheet }

/** Text fields a template actually uses; the Edit card Text tab shows only these. */
enum class CardField { Caption, Date, Location, Label, Stats }

data class SizeMm(val width: Float, val height: Float)

/** Geometry of the contact sheet (190 x 270): 5 columns x 6 film rows. */
object ContactGeometry {
    const val COLS = 5
    const val ROWS = 6
    const val ROW_PITCH = 42f
    const val FIRST_ROW = 14f
    fun rowTop(r: Int) = FIRST_ROW + r * ROW_PITCH
    val windows: List<RectMm> = (0 until ROWS).flatMap { r ->
        (0 until COLS).map { c -> RectMm(c * 38f + 3f, rowTop(r) + 6.5f, c * 38f + 35f, rowTop(r) + 35.5f) }
    }
}

private val TEXT_FOOTER = setOf(CardField.Caption, CardField.Date, CardField.Location)

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
    val fields: Set<CardField> = if (footer != null) TEXT_FOOTER else emptySet(),
    val captionMax: Int = 28,
    val labelHint: String? = null,
    val windowCornerMm: Float = 0f,
    /** Frame colour used while the sheet frame is still the default white; null = keep white. */
    val defaultFrameArgb: Int? = null,
    /** Font used while the sheet font is still the default. */
    val defaultFont: com.nbansal.grid.sheet.CaptionFont? = null,
    val rotatable: Boolean = true,
) {
    val cardWidthMm get() = sizeMm.width
    val cardHeightMm get() = sizeMm.height
    val window get() = photoWindows.first()
    val windowCount get() = photoWindows.size
    val sizeLabel: String get() = "${fmt(cardWidthMm)}×${fmt(cardHeightMm)}"

    private fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

    companion object {
        private fun rect(x: Float, y: Float, w: Float, h: Float) = RectMm(x, y, x + w, y + h)
        private fun single(
            id: String, name: String, group: CardGroup, w: Float, h: Float, win: RectMm, footer: RectMm?,
        ) = CardTemplate(id, name, group, SizeMm(w, h), listOf(win), footer)

        val Mini = single("mini", "Mini", CardGroup.Instant, 54f, 86f, RectMm(4f, 6f, 50f, 68f), RectMm(4f, 68f, 50f, 86f))
        val Square = single("square", "Square", CardGroup.Instant, 72f, 86f, rect(5f, 5f, 62f, 62f), rect(5f, 67f, 62f, 19f))
        val Wide = single("wide", "Wide", CardGroup.Instant, 108f, 86f, rect(4.5f, 5f, 99f, 62f), rect(4.5f, 67f, 99f, 19f))
        val Classic = single("classic", "Classic", CardGroup.Instant, 88f, 107f, rect(4.5f, 6f, 79f, 79f), rect(4.5f, 88f, 79f, 16f))
        val Wallet = single("wallet", "Wallet", CardGroup.Prints, 64f, 89f, RectMm(2f, 2f, 62f, 87f), null)
        val PhotoStrip = CardTemplate(
            "strip", "Photo strip", CardGroup.Prints, SizeMm(51f, 152f),
            listOf(4f, 38f, 72f, 106f).map { rect(4f, it, 43f, 31f) }, rect(4f, 140f, 43f, 10f),
        )
        val Passport = single("passport", "Passport", CardGroup.Prints, 35f, 45f, RectMm(0f, 0f, 35f, 45f), null)

        val FilmStrip = CardTemplate(
            "film", "Film strip", CardGroup.Novelty, SizeMm(150f, 35f),
            listOf(7.5f, 55f, 102.5f).map { rect(it, 5.5f, 40f, 23f) }, null, Decoration.FilmStrip,
            fields = setOf(CardField.Label), labelHint = "Film edge text, e.g. GRID 400",
            defaultFrameArgb = 0xFF141414.toInt(),
        )
        val Stamp = CardTemplate(
            "stamp", "Stamp", CardGroup.Novelty, SizeMm(40f, 50f), listOf(rect(4f, 4f, 32f, 34f)), null, Decoration.Stamp,
            fields = setOf(CardField.Caption, CardField.Label), captionMax = 14, labelHint = "Value, e.g. ₹5",
            defaultFrameArgb = 0xFFF4EBDD.toInt(),
        )
        val Ticket = CardTemplate(
            "ticket", "Ticket", CardGroup.Novelty, SizeMm(120f, 50f), listOf(rect(5f, 5f, 46f, 40f)), null, Decoration.Ticket,
            fields = setOf(CardField.Caption, CardField.Date, CardField.Location, CardField.Label), captionMax = 24,
            labelHint = "Heading, e.g. ADMIT ONE", defaultFrameArgb = 0xFFF4EBDD.toInt(),
        )
        val Postcard = CardTemplate(
            "postcard", "Postcard", CardGroup.Extras, SizeMm(148f, 105f), listOf(rect(5f, 5f, 92f, 95f)), null, Decoration.Postcard,
            fields = setOf(CardField.Caption), captionMax = 200,
        )
        val Bookmark = CardTemplate(
            "bookmark", "Bookmark", CardGroup.Extras, SizeMm(50f, 150f), listOf(rect(4f, 4f, 42f, 92f)), null, Decoration.Bookmark,
            fields = setOf(CardField.Caption), captionMax = 120, defaultFont = com.nbansal.grid.sheet.CaptionFont.Serif,
        )
        val TradingCard = CardTemplate(
            "trading", "Trading card", CardGroup.Extras, SizeMm(63f, 88f), listOf(rect(6f, 14f, 51f, 36f)), null, Decoration.TradingCard,
            fields = setOf(CardField.Label, CardField.Stats), labelHint = "Name, e.g. PLAYER ONE",
            defaultFrameArgb = 0xFFE9B949.toInt(),
        )
        val SlideMount = CardTemplate(
            "slide", "Slide mount", CardGroup.Extras, SizeMm(50f, 50f), listOf(rect(7f, 7f, 36f, 36f)), null, Decoration.SlideMount,
            fields = setOf(CardField.Label), labelHint = "Slide label, e.g. KODAK 12", windowCornerMm = 3f,
            defaultFrameArgb = 0xFFECE8DA.toInt(),
        )
        val ContactSheet = CardTemplate(
            "contact", "Contact sheet", CardGroup.Extras, SizeMm(190f, 270f), ContactGeometry.windows, null, Decoration.ContactSheet,
            fields = setOf(CardField.Label), labelHint = "Header, e.g. Summer 2026", rotatable = false,
            defaultFrameArgb = 0xFF141414.toInt(),
        )

        val All: List<CardTemplate> = listOf(
            Mini, Square, Wide, Classic,
            Wallet, PhotoStrip, Passport,
            FilmStrip, Stamp, Ticket,
            Postcard, Bookmark, TradingCard, SlideMount, ContactSheet,
        )

        fun byId(id: String): CardTemplate = All.firstOrNull { it.id == id } ?: Mini
    }
}

