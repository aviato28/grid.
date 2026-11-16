package com.nbansal.grid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbansal.grid.layout.PanZoom
import com.nbansal.grid.sheet.MAX_CAPTION
import com.nbansal.grid.sheet.MAX_LOCATION
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.Slot
import com.nbansal.grid.sheet.Stat
import com.nbansal.grid.sheet.MAX_LABEL
import com.nbansal.grid.sheet.MAX_STAT_NAME
import com.nbansal.grid.layout.CardField
import com.nbansal.grid.layout.CardTemplate
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectTapGestures
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class EditTab(val label: String) { Text("Text"), Style("Style"), Filter("Filter") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCardScreen(vm: SheetViewModel, index: Int, onClose: () -> Unit) {
    val colors = GridTheme.colors
    val cards by vm.cards.collectAsStateWithLifecycle()
    val sheetStyle by vm.style.collectAsStateWithLifecycle()
    val card = cards.getOrNull(index)
    if (card == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val id = card.id
    var draft by remember(id) { mutableStateOf(card) }
    var tab by remember { mutableStateOf(EditTab.Text) }
    var slot by remember(id) { mutableIntStateOf(0) }
    val uris = draft.slots.map { it.uri }
    val bitmaps by produceState(emptyMap<String, android.graphics.Bitmap>(), uris) {
        for (u in uris.filterNotNull().distinct()) {
            if (u in value) continue
            vm.loader.preview(u)?.let { value = value + (u to it) }
        }
    }
    val tpl = draft.template
    val effectiveStyle = draft.styleFor(sheetStyle)
    val activeSlot = slot.coerceIn(0, draft.slots.size - 1)
    val pickPhoto = rememberSinglePhotoPicker { uri ->
        draft = draft.copy(slots = draft.slots.mapIndexed { i, s -> if (i == activeSlot) Slot(uri.toString()) else s })
    }

    fun save() { vm.replace(draft) }
    fun saveAndClose() { save(); onClose() }
    BackHandler { saveAndClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column {
                Text("Edit card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${index + 1} / ${cards.size} · ${tpl.displayName} ${tpl.sizeLabel}", style = MonoLabel, color = colors.muted)
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = tpl.cardWidthMm / tpl.cardHeightMm
            val h = minOf(maxHeight, maxWidth / ratio)
            val w = h * ratio
            Canvas(
                Modifier
                    .size(w, h)
                    .shadow(6.dp, RoundedCornerShape(1.dp))
                    .clip(RoundedCornerShape(1.dp))
                    .pointerInput(id, tpl.id) {
                        detectTapGestures { o ->
                            val s = size.width / tpl.cardWidthMm
                            val i = tpl.photoWindows.indexOfFirst { it.contains(o.x / s, o.y / s) }
                            if (i >= 0) slot = i
                        }
                    }
                    .pointerInput(id, activeSlot) {
                        detectTransformGestures { centroid, pan, z, _ ->
                            val sl = draft.slots.getOrNull(activeSlot) ?: return@detectTransformGestures
                            val b = sl.uri?.let { bitmaps[it] } ?: return@detectTransformGestures
                            val pxPerMm = size.width / tpl.cardWidthMm
                            val r = PanZoom.transform(
                                b.width.toFloat(), b.height.toFloat(), tpl.photoWindows[activeSlot],
                                PanZoom.State(sl.zoom, sl.panX, sl.panY),
                                centroid.x / pxPerMm, centroid.y / pxPerMm, pan.x / pxPerMm, pan.y / pxPerMm, z,
                            )
                            draft = draft.copy(slots = draft.slots.mapIndexed { i, s ->
                                if (i == activeSlot) s.copy(zoom = r.zoom, panX = r.panX, panY = r.panY) else s
                            })
                        }
                    },
            ) {
                val s = size.width / tpl.cardWidthMm
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(s, s)
                    vm.renderer.drawCard(nc, draft, sheetStyle, draft.slots.map { it.uri?.let(bitmaps::get) })
                    nc.restore()
                }
                if (tpl.windowCount > 1) {
                    val win = tpl.photoWindows[activeSlot]
                    drawRect(
                        colors.accent, Offset(win.left * s, win.top * s), Size(win.width * s, win.height * s),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
        }
        val hasPhoto = draft.slots.getOrNull(activeSlot)?.uri != null
        if (tpl.windowCount > 1) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                draft.slots.forEachIndexed { i, s ->
                    val sel = i == activeSlot
                    Box(
                        Modifier.size(32.dp)
                            .border(if (sel) 2.dp else 1.dp, if (sel) colors.accent else colors.line, RoundedCornerShape(6.dp))
                            .clickable { slot = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = MonoLabel, color = if (s.uri == null) colors.muted else MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (hasPhoto) "Pinch to zoom · drag to reposition" else "Empty window",
                Modifier.weight(1f), style = MonoLabel, color = colors.muted,
            )
            TextButton(onClick = pickPhoto) { Text(if (hasPhoto) "Replace photo" else "Add photo", color = colors.accent) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            for (t in EditTab.entries) {
                val sel = t == tab
                Column(Modifier.clickable { tab = t }.padding(vertical = 6.dp)) {
                    Text(t.label.uppercase(), style = MonoLabel, color = if (sel) MaterialTheme.colorScheme.onBackground else colors.muted)
                    Box(
                        Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth()
                            .background(if (sel) colors.accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.line)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (tab) {
                EditTab.Text -> TextTab(draft, effectiveStyle, { draft = it }, { vm.applyCaptionToAll(draft) }) { vm.remove(id); onClose() }
                EditTab.Style -> {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ONLY THIS CARD", style = MonoLabel, modifier = Modifier.weight(1f))
                        Switch(
                            checked = draft.styleOverride != null,
                            onCheckedChange = { on ->
                                draft = draft.copy(styleOverride = if (on) effectiveStyle else null)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
                        )
                    }
                    StyleControls(effectiveStyle) { s ->
                        if (draft.styleOverride != null) draft = draft.copy(styleOverride = s) else vm.setStyle(s)
                    }
                }
                EditTab.Filter -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text("FILTERS · COMING SOON", style = MonoLabel, color = colors.muted)
                }
            }
        }
        Button(
            onClick = ::saveAndClose,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
        ) { Text("Done", fontWeight = FontWeight.Medium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextTab(
    draft: Card, style: com.nbansal.grid.sheet.CardStyle, onChange: (Card) -> Unit,
    onApplyAll: () -> Unit, onRemove: () -> Unit,
) {
    val colors = GridTheme.colors
    val t = draft.template
    var picking by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    if (t.fields.isEmpty()) {
        Text("This shape has no text.", color = colors.muted, style = MonoLabel)
    }
    if (CardField.Caption in t.fields) {
        val long = t.captionMax > 40
        OutlinedTextField(
            value = draft.caption,
            onValueChange = { onChange(draft.copy(caption = (if (long) it else it.replace("\n", "")).take(t.captionMax))) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = !long,
            minLines = if (long) 3 else 1,
            label = { Text(when (t.id) { "postcard" -> "Note"; "bookmark" -> "Quote"; "ticket" -> "Title"; else -> "Caption" }) },
            textStyle = TextStyle(fontFamily = fontFamilyFor(style.font), fontSize = 24.sp),
            supportingText = { Text("${draft.caption.length}/${t.captionMax}", style = MonoLabel, color = colors.muted) },
            keyboardOptions = KeyboardOptions(imeAction = if (long) ImeAction.Default else ImeAction.Done),
            shape = MaterialTheme.shapes.small,
        )
    }
    if (CardField.Label in t.fields) {
        OutlinedTextField(
            value = draft.label,
            onValueChange = { onChange(draft.copy(label = it.replace("\n", "").take(MAX_LABEL))) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            label = { Text("Label") },
            supportingText = { t.labelHint?.let { Text(it, style = MonoLabel, color = colors.muted) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.small,
        )
    }
    if (CardField.Date in t.fields) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { picking = true },
                shape = MaterialTheme.shapes.small,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
            ) {
                Text(
                    draft.date?.let { style.dateFormat.format(it) } ?: "Add date",
                    style = MonoLabel, color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (draft.date != null) TextButton(onClick = { onChange(draft.copy(date = null)) }) {
                Text("Clear", color = colors.muted)
            }
        }
    }
    if (CardField.Location in t.fields) {
        OutlinedTextField(
            value = draft.location,
            onValueChange = { onChange(draft.copy(location = it.replace("\n", "").take(MAX_LOCATION))) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            label = { Text("Location") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.small,
        )
    }
    if (CardField.Stats in t.fields) {
        Text("STATS", style = MonoLabel, color = colors.muted, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        val stats = draft.stats.take(3)
        stats.forEachIndexed { i, st ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = st.name,
                    onValueChange = { v ->
                        onChange(draft.copy(stats = stats.mapIndexed { j, s -> if (j == i) s.copy(name = v.replace("\n", "").take(MAX_STAT_NAME)) else s }))
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Row(Modifier.padding(start = 8.dp)) {
                    for (k in 1..5) {
                        Text(
                            if (k <= st.stars) "★" else "☆",
                            Modifier.clickable {
                                val v = if (k == st.stars) k - 1 else k
                                onChange(draft.copy(stats = stats.mapIndexed { j, s -> if (j == i) s.copy(stars = v) else s }))
                            }.padding(horizontal = 3.dp, vertical = 8.dp),
                            color = colors.accent, fontSize = 22.sp,
                        )
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (CardField.Caption in t.fields) {
            TextButton(onClick = onApplyAll) { Text("Apply caption to all", color = colors.accent) }
        } else Spacer(Modifier.width(1.dp))
        TextButton(onClick = onRemove) { Text("Remove card", color = colors.muted) }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (draft.date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    picking = false
                }) { Text("OK", color = colors.accent) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", color = colors.muted) } },
        ) { DatePicker(state) }
    }
}
