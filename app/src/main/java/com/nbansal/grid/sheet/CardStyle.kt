package com.nbansal.grid.sheet

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CaptionFont(val label: String) {
    Handwritten("Handwritten"), Script("Script"), Pencil("Pencil"), Marker("Marker"),
    Typewriter("Typewriter"), Serif("Serif"), Mono("Mono"), Clean("Clean"),
}

enum class TextSize(val label: String, val scale: Float) { S("S", 0.85f), M("M", 1f), L("L", 1.2f) }

enum class TextColor(val label: String, val argb: Int) {
    Ink("Ink", 0xFF141414.toInt()),
    PenBlue("Pen blue", 0xFF1F3A93.toInt()),
    Red("Red", 0xFFC0392B.toInt()),
    Pencil("Pencil", 0xFF6D685D.toInt()),
    White("White", 0xFFFFFFFF.toInt()),
}

enum class FrameColor(val label: String, val argb: Int) {
    White("White", 0xFFFFFFFF.toInt()),
    Cream("Cream", 0xFFF4EBDD.toInt()),
    Black("Black", 0xFF141414.toInt()),
    Blush("Blush", 0xFFF2D4CF.toInt()),
    Sage("Sage", 0xFFD5DECF.toInt()),
    Sky("Sky", 0xFFD6E4F0.toInt()),
}

enum class FooterLayout(val label: String) {
    CaptionOnly("Caption"),
    CaptionAndDate("Caption + date"),
    CaptionLeftDateRight("Caption | date"),
    CaptionDateLocation("Caption + date · place"),
    DateOnly("Date"),
    LocationOnly("Location"),
    FilmStamp("Film stamp"),
    Blank("Blank"),
}

enum class Align { Start, Center, End }

enum class DateFormat(val label: String, private val pattern: String) {
    DayMonthYear("15 Nov 2026", "d MMM yyyy"),
    Dotted("15.11.26", "dd.MM.yy"),
    MonthYear("NOV 2026", "MMM yyyy"),
    Weekday("Sunday, 15 Nov", "EEEE, d MMM");

    fun format(date: LocalDate): String {
        val s = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(date)
        return if (this == MonthYear) s.uppercase(Locale.ENGLISH) else s
    }
}

/** Sheet-level default; a photo may override it entirely via [Photo.styleOverride]. */
data class CardStyle(
    val font: CaptionFont = CaptionFont.Handwritten,
    val textSize: TextSize = TextSize.M,
    val textColor: TextColor = TextColor.Ink,
    val frameColor: FrameColor = FrameColor.White,
    val layout: FooterLayout = FooterLayout.CaptionOnly,
    val align: Align = Align.Center,
    val dateFormat: DateFormat = DateFormat.DayMonthYear,
    /** Stage C: film filter id. */
    val filter: String? = null,
)

const val FILM_STAMP_ARGB = 0xFFFF7A1A.toInt()

/** `'26 11 15` */
fun filmStampText(date: LocalDate): String = DateTimeFormatter.ofPattern("''yy MM dd", Locale.ENGLISH).format(date)

/** Relative luminance (0..1) of an ARGB colour, sRGB-linearised. */
fun luminance(argb: Int): Float {
    fun ch(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return (0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)).toFloat()
}

fun isDarkColor(argb: Int) = luminance(argb) < 0.18f

/** Dark frame + Ink text renders White; light frame + White text renders Ink. */
fun effectiveTextColor(style: CardStyle): Int {
    val dark = isDarkColor(style.frameColor.argb)
    return when {
        dark && style.textColor == TextColor.Ink -> TextColor.White.argb
        !dark && style.textColor == TextColor.White -> TextColor.Ink.argb
        else -> style.textColor.argb
    }
}

/** Same colour at ~75% alpha, for date/location lines. */
fun secondaryColor(argb: Int): Int = (argb and 0x00FFFFFF) or (0xBF shl 24)
