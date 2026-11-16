package com.nbansal.grid.sheet

import java.time.LocalDate

/** Parses EXIF `yyyy:MM:dd HH:mm:ss` (date part only); null when malformed. */
fun parseExifDate(s: String?): LocalDate? {
    val m = Regex("""^\s*(\d{4})[:\-](\d{2})[:\-](\d{2})""").find(s ?: return null) ?: return null
    val (y, mo, d) = m.destructured
    return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
}
