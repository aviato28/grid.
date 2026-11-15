package com.nbansal.grid.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Extra tokens that Material3's ColorScheme has no slot for. */
@Immutable
data class GridColors(
    val muted: Color,
    val line: Color,
    val accent: Color,
)

// Stationery (light)
val StationeryBackground = Color(0xFFF4F1EA)
val StationerySurface = Color(0xFFFFFFFF)
val StationeryInk = Color(0xFF141414)
val StationeryMuted = Color(0xFF6D685D)
val StationeryLine = Color(0xFF141414).copy(alpha = 0.18f)
val StationeryAccent = Color(0xFFFF5A1F)

// Darkroom (dark)
val DarkroomBackground = Color(0xFF161514)
val DarkroomSurface = Color(0xFF22201E)
val DarkroomInk = Color(0xFFEDE8E0)
val DarkroomMuted = Color(0xFF8D867B)
val DarkroomLine = Color(0xFF3A3632)
val DarkroomAccent = Color(0xFFE5483B)
