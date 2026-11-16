package com.nbansal.grid.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nbansal.grid.filter.FilmFilter
import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.render.FilteredPhotos
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import kotlin.math.roundToInt

/** Filter picker + adjustments; shared by the card editor's Filter tab and the sheet-wide Filter sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterControls(
    settings: FilterSettings, sampleUri: String?, photos: FilteredPhotos, onChange: (FilterSettings) -> Unit,
) {
    val colors = GridTheme.colors
    SectionLabel("Film")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(FilmFilter.All, key = { it.id }) { f ->
            FilterThumb(f, settings.filterId == f.id, sampleUri, photos) { onChange(settings.withFilter(f)) }
        }
    }
    SectionLabel("Intensity")
    LabeledSlider("", settings.intensity, 0f, 1f, { "${(it * 100).roundToInt()}%" }, settings.filterId != FilmFilter.None.id) {
        onChange(settings.copy(intensity = it))
    }
    SectionLabel("Effects")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(settings.grain, { onChange(settings.copy(grain = !settings.grain)) }) { Text("Grain", style = MonoLabel) }
        Chip(settings.vignette, { onChange(settings.copy(vignette = !settings.vignette)) }) { Text("Vignette", style = MonoLabel) }
        Chip(settings.lightLeak, { onChange(settings.copy(lightLeak = !settings.lightLeak)) }) { Text("Light leak", style = MonoLabel) }
    }
    SectionLabel("Adjust")
    val signed: (Float) -> String = { "%+d".format((it * 100).roundToInt()) }
    LabeledSlider("Brightness", settings.brightness, -1f, 1f, signed) { onChange(settings.copy(brightness = it)) }
    LabeledSlider("Contrast", settings.contrast, -1f, 1f, signed) { onChange(settings.copy(contrast = it)) }
    LabeledSlider("Warmth", settings.warmth, -1f, 1f, signed) { onChange(settings.copy(warmth = it)) }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, min: Float, max: Float, fmt: (Float) -> String, enabled: Boolean = true, onChange: (Float) -> Unit,
) {
    val colors = GridTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) Text(label.uppercase(), style = MonoLabel, color = colors.muted, modifier = Modifier.width(92.dp))
        Slider(
            value, onChange, Modifier.weight(1f), enabled = enabled, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.line),
        )
        Text(fmt(value), style = MonoLabel, color = colors.muted, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun FilterThumb(f: FilmFilter, selected: Boolean, sampleUri: String?, photos: FilteredPhotos, onClick: () -> Unit) {
    val colors = GridTheme.colors
    val settings = FilterSettings().withFilter(f)
    val bmp by produceState<Bitmap?>(null, sampleUri, f.id) {
        value = sampleUri?.let { photos.sample(it, settings) }
    }
    Column(Modifier.width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp, 96.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, MaterialTheme.shapes.small)
                .padding(3.dp).background(colors.line.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            val img: ImageBitmap? = bmp?.asImageBitmap()
            if (img != null) Image(img, f.name, Modifier.fillMaxWidth().height(90.dp), contentScale = ContentScale.Crop)
        }
        Text(
            f.name, style = MonoLabel.copy(fontSize = 10.sp), maxLines = 1, textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onBackground else colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
