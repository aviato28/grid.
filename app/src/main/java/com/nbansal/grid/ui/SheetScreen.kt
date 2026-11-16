package com.nbansal.grid.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.layout.layoutSheet
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.sheet.ExportState
import com.nbansal.grid.sheet.Photo
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREVIEW_PX_PER_MM = 5f

@Composable
fun SheetScreen(vm: SheetViewModel, onBack: () -> Unit, onEdit: (Int) -> Unit) {
    val colors = GridTheme.colors
    val photos by vm.photos.collectAsStateWithLifecycle()
    val sheetStyle by vm.style.collectAsStateWithLifecycle()
    var showStyle by remember { mutableStateOf(false) }
    val pages = remember(photos.size) { layoutSheet(vm.paper, vm.template, photos.size) }
    val pickMore = rememberPhotoPicker { vm.addPhotos(it) }
    val pagerState = rememberPagerState { pages.size }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column {
                Text("New sheet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${photos.size} ${if (photos.size == 1) "photo" else "photos"} · ${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                    style = MonoLabel, color = colors.muted,
                )
            }
        }
        if (pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No photos. Add some to start.", color = colors.muted)
            }
        } else {
            HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth(), key = { it }) { i ->
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    PagePreview(vm, pages[i], photos, sheetStyle, onTapPhoto = onEdit)
                }
            }
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                style = MonoLabel, color = colors.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        HorizontalDivider(color = colors.line)
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolButton("Shapes", enabled = false) {}
                ToolButton("Style", enabled = photos.isNotEmpty()) { showStyle = true }
                ToolButton("Filter", enabled = false) {}
                ToolButton("Reorder", enabled = false) {}
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.border(1.dp, colors.line, MaterialTheme.shapes.small).padding(horizontal = 10.dp, vertical = 8.dp),
                ) { Text("Mini 54×86", style = MonoLabel) }
                OutlinedButton(
                    onClick = pickMore,
                    shape = MaterialTheme.shapes.small,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                ) { Text("+ Add", color = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::startExport,
                enabled = photos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
            ) { Text("Export PDF", fontWeight = FontWeight.Medium) }
        }
    }
    if (showStyle) StyleSheet(sheetStyle, vm::setStyle) { showStyle = false }
    ExportSheet(vm)
}

@Composable
private fun PagePreview(vm: SheetViewModel, page: SheetPage, photos: List<Photo>, sheetStyle: CardStyle, onTapPhoto: (Int) -> Unit) {
    val onTap by rememberUpdatedState(onTapPhoto)
    // Only this page's photos matter: changes elsewhere do not trigger a re-render.
    val pagePhotos = remember(photos, page) {
        photos.subList(page.firstIndex, page.firstIndex + page.cards.size)
    }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pagePhotos, page, sheetStyle) {
        val local = page.copy(firstIndex = 0)
        val bmp: Bitmap = vm.renderPreview(local, pagePhotos, sheetStyle, PREVIEW_PX_PER_MM)
        image = bmp.asImageBitmap()
    }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(
        Modifier
            .aspectRatio(vm.paper.widthMm / vm.paper.heightMm)
            .shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(1.dp))
            .background(Color.White)
            .onSizeChanged { size = it }
            .pointerInput(page, size) {
                detectTapGestures { o: Offset ->
                    if (size.width == 0) return@detectTapGestures
                    val x = o.x / size.width * vm.paper.widthMm
                    val y = o.y / size.height * vm.paper.heightMm
                    val idx = page.cards.indexOfFirst { it.contains(x, y) }
                    if (idx >= 0) onTap(page.firstIndex + idx)
                }
            },
    ) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(vm: SheetViewModel) {
    val state by vm.export.collectAsStateWithLifecycle()
    if (state is ExportState.Idle) return
    val colors = GridTheme.colors
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val doneFile = (state as? ExportState.Done)?.file
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = doneFile
        if (uri != null && f != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) "Saved" else "Could not save", Toast.LENGTH_SHORT).show()
        }
    }
    ModalBottomSheet(onDismissRequest = vm::dismissExport, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            when (val s = state) {
                is ExportState.Rendering -> {
                    Text("Exporting PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (s.page - 1f) / s.total },
                        Modifier.fillMaxWidth().height(2.dp),
                        color = colors.accent, trackColor = colors.line,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Rendering page ${s.page} / ${s.total}", style = MonoLabel, color = colors.muted)
                    Spacer(Modifier.height(16.dp))
                }
                is ExportState.Done -> {
                    Text(s.file.name, style = MonoLabel)
                    Text("${s.pages} ${if (s.pages == 1) "page" else "pages"} · A4", style = MonoLabel, color = colors.muted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Print at 100% / Actual size — don’t ‘fit to page’.",
                        color = colors.muted, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { share(context, s.file) },
                        Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                    ) { Text("Share", fontWeight = FontWeight.Medium) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { saver.launch(s.file.name) },
                        Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.small,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
                    ) { Text("Save to device", color = MaterialTheme.colorScheme.onSurface) }
                }
                is ExportState.Failed -> {
                    Text("Export failed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = colors.muted)
                    TextButton(onClick = vm::dismissExport) { Text("Close", color = colors.accent) }
                }
                ExportState.Idle -> {}
            }
        }
    }
}

private fun share(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share PDF"))
}

@Composable
private fun ToolButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label.uppercase(), style = MonoLabel, color = if (enabled) MaterialTheme.colorScheme.onBackground else GridTheme.colors.muted.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleSheet(style: CardStyle, onChange: (CardStyle) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
            Text("Sheet style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("Applies to every card without its own override", style = MonoLabel, color = GridTheme.colors.muted)
            StyleControls(style, onChange)
        }
    }
}
