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
import com.nbansal.grid.sheet.Photo
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class EditTab(val label: String) { Text("Text"), Style("Style"), Filter("Filter") }

@Composable
fun EditPhotoScreen(vm: SheetViewModel, index: Int, onClose: () -> Unit) {
    val colors = GridTheme.colors
    val photos by vm.photos.collectAsStateWithLifecycle()
    val sheetStyle by vm.style.collectAsStateWithLifecycle()
    val photo = photos.getOrNull(index)
    if (photo == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val id = photo.id
    var draft by remember(id) { mutableStateOf(photo) }
    var tab by remember { mutableStateOf(EditTab.Text) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.uri) { value = vm.loader.preview(photo.uri) }
    val tpl = draft.template
    val effectiveStyle = draft.styleFor(sheetStyle)

    fun save() { vm.replace(draft) }
    fun saveAndClose() { save(); onClose() }
    BackHandler { saveAndClose() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::saveAndClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column {
                Text("Edit photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${index + 1} / ${photos.size}", style = MonoLabel, color = colors.muted)
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
                    .pointerInput(id) {
                        detectTransformGestures { centroid, pan, z, _ ->
                            val b = bitmap ?: return@detectTransformGestures
                            val pxPerMm = size.width / tpl.cardWidthMm
                            val r = PanZoom.transform(
                                b.width.toFloat(), b.height.toFloat(), tpl.window,
                                PanZoom.State(draft.zoom, draft.panX, draft.panY),
                                centroid.x / pxPerMm, centroid.y / pxPerMm, pan.x / pxPerMm, pan.y / pxPerMm, z,
                            )
                            draft = draft.copy(zoom = r.zoom, panX = r.panX, panY = r.panY)
                        }
                    },
            ) {
                val s = size.width / tpl.cardWidthMm
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.scale(s, s)
                    vm.renderer.drawCard(nc, bitmap, draft, sheetStyle)
                    nc.restore()
                }
            }
        }
        Text(
            "Pinch to zoom · drag to reposition",
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            style = MonoLabel, color = colors.muted, textAlign = TextAlign.Center,
        )
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
                EditTab.Text -> TextTab(draft, effectiveStyle, { draft = it }, { vm.applyToAll(draft) }) { vm.remove(id); onClose() }
                EditTab.Style -> {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ONLY THIS PHOTO", style = MonoLabel, modifier = Modifier.weight(1f))
                        Switch(
                            checked = draft.styleOverride != null,
                            onCheckedChange = { on ->
                                draft = draft.copy(styleOverride = if (on) sheetStyle else null)
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
    draft: Photo, style: com.nbansal.grid.sheet.CardStyle, onChange: (Photo) -> Unit,
    onApplyAll: () -> Unit, onRemove: () -> Unit,
) {
    val colors = GridTheme.colors
    var picking by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = draft.caption,
        onValueChange = { onChange(draft.copy(caption = it.replace("\n", "").take(MAX_CAPTION))) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Caption") },
        textStyle = TextStyle(fontFamily = fontFamilyFor(style.font), fontSize = 24.sp),
        supportingText = { Text("${draft.caption.length}/$MAX_CAPTION", style = MonoLabel, color = colors.muted) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.small,
    )
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
    OutlinedTextField(
        value = draft.location,
        onValueChange = { onChange(draft.copy(location = it.replace("\n", "").take(MAX_LOCATION))) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        singleLine = true,
        label = { Text("Location") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.small,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onApplyAll) { Text("Apply caption to all", color = colors.accent) }
        TextButton(onClick = onRemove) { Text("Remove photo", color = colors.muted) }
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
