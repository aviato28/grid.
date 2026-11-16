package com.nbansal.grid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightGridColors = GridColors(StationeryMuted, StationeryLine, StationeryAccent)
private val DarkGridColors = GridColors(DarkroomMuted, DarkroomLine, DarkroomAccent)

val LocalGridColors = staticCompositionLocalOf { LightGridColors }

private val LightScheme = lightColorScheme(
    primary = StationeryAccent,
    onPrimary = Color.White,
    background = StationeryBackground,
    onBackground = StationeryInk,
    surface = StationerySurface,
    onSurface = StationeryInk,
    surfaceContainerLow = StationerySurface,
    surfaceContainerHigh = StationerySurface,
    surfaceContainerHighest = StationerySurface,
    onSurfaceVariant = StationeryMuted,
    outline = StationeryMuted,
    outlineVariant = StationeryLine,
)

private val DarkScheme = darkColorScheme(
    primary = DarkroomAccent,
    onPrimary = Color.White,
    background = DarkroomBackground,
    onBackground = DarkroomInk,
    surface = DarkroomSurface,
    onSurface = DarkroomInk,
    surfaceContainerLow = DarkroomSurface,
    surfaceContainerHigh = DarkroomSurface,
    surfaceContainerHighest = DarkroomSurface,
    onSurfaceVariant = DarkroomMuted,
    outline = DarkroomMuted,
    outlineVariant = DarkroomLine,
)

private val GridShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
)

object GridTheme {
    val colors: GridColors
        @Composable get() = LocalGridColors.current
}

@Composable
fun GridTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val gridColors = if (darkTheme) DarkGridColors else LightGridColors
    CompositionLocalProvider(LocalGridColors provides gridColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = GridTypography,
            shapes = GridShapes,
        ) {
            GridBackground(showGrid = !darkTheme, content = content)
        }
    }
}

/** Page background; light mode draws a faint 8dp grid. */
@Composable
private fun GridBackground(showGrid: Boolean, content: @Composable () -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    val lineColor = StationeryInk.copy(alpha = 0.05f)
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(bg)
                if (showGrid) {
                    val step = 8.dp.toPx()
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                }
            },
    ) {
        // Without a Surface, text/icons default to black: make them follow the theme.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
    }
}
