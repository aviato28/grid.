package com.nbansal.grid.sheet

import android.net.Uri

const val MAX_PHOTOS = 90
const val MAX_CAPTION = 28

/** [panX]/[panY] are normalized to [-1, 1] across the overflow of the zoomed image beyond the window. */
data class Photo(
    val id: Long,
    val uri: Uri,
    val caption: String = "",
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)
