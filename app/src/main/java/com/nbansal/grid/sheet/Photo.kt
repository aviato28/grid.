package com.nbansal.grid.sheet

import android.net.Uri
import com.nbansal.grid.layout.CardTemplate
import java.time.LocalDate

const val MAX_PHOTOS = 90
const val MAX_CAPTION = 28
const val MAX_LOCATION = 40

/** [panX]/[panY] are normalized to [-1, 1] across the overflow of the zoomed image beyond the window. */
data class Photo(
    val id: Long,
    val uri: Uri,
    val caption: String = "",
    val date: LocalDate? = null,
    val location: String = "",
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Stage B: each photo carries its own card shape. */
    val templateId: String = CardTemplate.Mini.id,
    /** When set, replaces the sheet style for this photo. */
    val styleOverride: CardStyle? = null,
) {
    val template: CardTemplate get() = CardTemplate.byId(templateId)
    fun styleFor(sheet: CardStyle): CardStyle = styleOverride ?: sheet
    val content: FooterContent get() = FooterContent(caption, date, location)
}
