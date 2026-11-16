package com.nbansal.grid.draft

import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.Slot
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A saved sheet. On disk, photo uris are relative (`photos/<file>`); in memory they are absolute `file://` uris. */
@Serializable
data class Draft(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val cards: List<Card> = emptyList(),
    val sheetStyle: CardStyle = CardStyle(),
    val sheetFilter: FilterSettings = FilterSettings(),
    val groupByShape: Boolean = true,
) {
    /** Equality ignoring [updatedAt], to skip no-op saves. */
    fun sameContent(o: Draft) = copy(updatedAt = 0) == o.copy(updatedAt = 0)

    fun mapUris(f: (String) -> String): Draft =
        copy(cards = cards.map { c -> c.copy(slots = c.slots.map { s: Slot -> s.uri?.let { s.copy(uri = f(it)) } ?: s }) })
}

fun defaultDraftName(date: LocalDate): String =
    "Sheet · " + DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).format(date)

/** "just now", "5 min ago", "2h ago", "3 days ago". */
fun timeAgo(nowMs: Long, thenMs: Long): String {
    val m = ((nowMs - thenMs) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60}h ago"
        m < 60 * 24 * 30 -> "${m / (60 * 24)}d ago"
        else -> "${m / (60 * 24 * 30)}mo ago"
    }
}
