package com.nbansal.grid.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbansal.grid.R
import com.nbansal.grid.sheet.MAX_CAPTION
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel

private val Caveat = FontFamily(Font(R.font.caveat))

@Composable
fun EditPhotoScreen(vm: SheetViewModel, index: Int, onClose: () -> Unit) {
    val colors = GridTheme.colors
    val photos by vm.photos.collectAsStateWithLifecycle()
    val photo = photos.getOrNull(index)
    if (photo == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }
        return
    }
    val id = photo.id
    var caption by remember(id) { mutableStateOf(photo.caption) }
    var zoom by remember(id) { mutableFloatStateOf(photo.zoom) }
    var panX by remember(id) { mutableFloatStateOf(photo.panX) }
    var panY by remember(id) { mutableFloatStateOf(photo.panY) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.uri) { value = vm.loader.preview(photo.uri) }
    val tpl = vm.template

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column {
                Text("Edit photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${index + 1} / ${photos.size}", style = MonoLabel, color = colors.muted)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
                val ratio = tpl.cardWidthMm / tpl.cardHeightMm
                val h = minOf(maxHeight, maxWidth / ratio)
                val w = h * ratio
                val density = LocalDensity.current
                Canvas(
                    Modifier
                        .size(w, h)
                        .shadow(6.dp, RoundedCornerShape(1.dp))
                        .clip(RoundedCornerShape(1.dp))
                        .pointerInput(id) {
                            detectTransformGestures { _, pan, z, _ ->
                                val b = bitmap ?: return@detectTransformGestures
                                val pxPerMm = size.width / tpl.cardWidthMm
                                zoom = (zoom * z).coerceIn(1f, 5f)
                                val (rx, ry) = vm.renderer.panRangeMm(b.width.toFloat(), b.height.toFloat(), zoom)
                                if (rx > 0f) panX = (panX + pan.x / (rx * pxPerMm)).coerceIn(-1f, 1f)
                                if (ry > 0f) panY = (panY + pan.y / (ry * pxPerMm)).coerceIn(-1f, 1f)
                            }
                        },
                ) {
                    val s = size.width / tpl.cardWidthMm
                    drawIntoCanvas { c ->
                        val nc = c.nativeCanvas
                        nc.save()
                        nc.scale(s, s)
                        vm.renderer.drawCard(nc, bitmap, caption, zoom, panX, panY)
                        nc.restore()
                    }
                }
            }
            Text(
                "Pinch to zoom · drag to reposition",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MonoLabel, color = colors.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.replace("\n", "").take(MAX_CAPTION) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Caption") },
                textStyle = TextStyle(fontFamily = Caveat, fontSize = 24.sp),
                supportingText = { Text("${caption.length}/$MAX_CAPTION", style = MonoLabel, color = colors.muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.applyCaptionToAll(caption) }) {
                    Text("Apply caption to all", color = colors.accent)
                }
                TextButton(onClick = { vm.remove(id); onClose() }) {
                    Text("Remove photo", color = colors.muted)
                }
            }
        }
        Button(
            onClick = { vm.update(id, caption, zoom, panX, panY); onClose() },
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
        ) { Text("Done", fontWeight = FontWeight.Medium) }
    }
}
