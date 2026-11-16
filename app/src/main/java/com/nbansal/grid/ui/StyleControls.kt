package com.nbansal.grid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.grid.render.CaptionFonts
import com.nbansal.grid.sheet.Align
import com.nbansal.grid.sheet.CaptionFont
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.DateFormat
import com.nbansal.grid.sheet.FILM_STAMP_ARGB
import com.nbansal.grid.sheet.FooterLayout
import com.nbansal.grid.sheet.FrameColor
import com.nbansal.grid.sheet.TextColor
import com.nbansal.grid.sheet.TextSize
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel

@Composable
fun fontFamilyFor(font: CaptionFont): FontFamily {
    val context = LocalContext.current
    return remember(font) {
        val res = CaptionFonts.resId(font)
        if (res != null) FontFamily(Font(res)) else FontFamily(Font(CaptionFonts.CLEAN_ASSET, context.assets))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), style = MonoLabel, color = GridTheme.colors.muted,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun Chip(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = GridTheme.colors
    Box(
        Modifier
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Dot(argb: Int, selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = GridTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            Modifier
                .size(34.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, CircleShape)
                .padding(4.dp)
                .background(Color(argb), CircleShape)
                .border(1.dp, colors.line, CircleShape)
                .clickable(onClick = onClick),
        )
        Text(label, style = MonoLabel.copy(fontSize = 9.sp), color = colors.muted, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** Tiny drawing of a footer layout. */
@Composable
private fun LayoutThumb(layout: FooterLayout, selected: Boolean, onClick: () -> Unit) {
    val colors = GridTheme.colors
    val ink = MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable(onClick = onClick)) {
        Canvas(
            Modifier
                .size(44.dp, 54.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, MaterialTheme.shapes.small),
        ) {
            val w = size.width
            val h = size.height
            val photoH = h * 0.62f
            drawRect(colors.line, Offset(w * 0.1f, h * 0.08f), Size(w * 0.8f, photoH - h * 0.08f))
            fun bar(x: Float, y: Float, bw: Float, c: Color = ink) =
                drawRoundRect(c, Offset(w * x, h * y), Size(w * bw, h * 0.05f), CornerRadius(2f, 2f))
            val y1 = 0.74f
            when (layout) {
                FooterLayout.CaptionOnly -> bar(0.25f, 0.8f, 0.5f)
                FooterLayout.CaptionAndDate -> { bar(0.25f, y1, 0.5f); bar(0.33f, 0.86f, 0.34f, colors.muted) }
                FooterLayout.CaptionLeftDateRight -> { bar(0.1f, 0.8f, 0.42f); bar(0.66f, 0.8f, 0.24f, colors.muted) }
                FooterLayout.CaptionDateLocation -> { bar(0.25f, y1, 0.5f); bar(0.2f, 0.86f, 0.6f, colors.muted) }
                FooterLayout.DateOnly -> bar(0.3f, 0.8f, 0.4f, colors.muted)
                FooterLayout.LocationOnly -> bar(0.2f, 0.8f, 0.6f, colors.muted)
                FooterLayout.FilmStamp -> {
                    bar(0.25f, 0.8f, 0.5f)
                    drawRoundRect(Color(FILM_STAMP_ARGB), Offset(w * 0.52f, photoH - h * 0.1f), Size(w * 0.34f, h * 0.045f), CornerRadius(2f, 2f))
                }
                FooterLayout.Blank -> {}
            }
        }
        Text(layout.label, style = MonoLabel.copy(fontSize = 9.sp), color = colors.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

/** All style controls; shared by the Edit Photo "Style" tab and the sheet-wide Style bottom sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleControls(style: CardStyle, onChange: (CardStyle) -> Unit) {
    SectionLabel("Font")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (f in CaptionFont.entries) {
            Chip(style.font == f, { onChange(style.copy(font = f)) }) {
                Text(f.label, style = TextStyle(fontFamily = fontFamilyFor(f), fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    SectionLabel("Size")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in TextSize.entries) Chip(style.textSize == s, { onChange(style.copy(textSize = s)) }) {
            Text(s.label, style = MonoLabel, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
        }
    }
    SectionLabel("Text colour")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in TextColor.entries) Dot(c.argb, style.textColor == c, c.label) { onChange(style.copy(textColor = c)) }
    }
    SectionLabel("Frame colour")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in FrameColor.entries) Dot(c.argb, style.frameColor == c, c.label) { onChange(style.copy(frameColor = c)) }
    }
    SectionLabel("Layout")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (l in FooterLayout.entries) LayoutThumb(l, style.layout == l) { onChange(style.copy(layout = l)) }
    }
    SectionLabel("Alignment")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (a in Align.entries) Chip(style.align == a, { onChange(style.copy(align = a)) }) {
            Text(a.name.uppercase(), style = MonoLabel)
        }
    }
    SectionLabel("Date format")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in DateFormat.entries) Chip(style.dateFormat == d, { onChange(style.copy(dateFormat = d)) }) {
            Text(d.label, style = MonoLabel)
        }
    }
    Spacer(Modifier.height(16.dp))
}
