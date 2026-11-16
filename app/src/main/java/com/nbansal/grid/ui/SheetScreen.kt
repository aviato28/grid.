package com.nbansal.grid.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp
import com.nbansal.grid.layout.CardGroup
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.packSheet
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.commonTemplate
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
import com.nbansal.grid.sheet.CardStyle
import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.sheet.ExportState
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val PREVIEW_PX_PER_MM = 5f

@Composable
fun SheetScreen(vm: SheetViewModel, onBack: () -> Unit, onEdit: (Int) -> Unit, onReorder: () -> Unit) {
    val colors = GridTheme.colors
    val cards by vm.cards.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val group by vm.groupByShape.collectAsStateWithLifecycle()
    val sheetStyle by vm.style.collectAsStateWithLifecycle()
    val sheetFilter by vm.filter.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showShapes by remember { mutableStateOf(false) }
    val templateIds = cards.map { it.templateId }
    val pages = remember(templateIds, group) { packSheet(cards.map { it.template }, group) }
    val pickMore = rememberPhotoPicker { vm.addPhotos(it) }
    val pagerState = rememberPagerState { pages.size }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { vm.clearSelection() }
    val common = commonTemplate(cards)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selecting) vm.clearSelection() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, if (selecting) "Clear selection" else "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (selecting) "${selection.size} selected" else name,
                    Modifier.clickable(enabled = !selecting) { renaming = true },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${cards.size} ${if (cards.size == 1) "card" else "cards"} · ${pages.size} ${if (pages.size == 1) "page" else "pages"}",
                    style = MonoLabel, color = colors.muted,
                )
            }
            if (selecting) {
                TextButton(onClick = vm::selectAll) { Text("Select all", color = colors.accent) }
                TextButton(onClick = vm::clearSelection) { Text("Clear", color = colors.muted) }
            }
        }
        if (pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No photos. Add some to start.", color = colors.muted)
            }
        } else {
            HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth(), key = { it }) { i ->
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    PagePreview(
                        vm, pages.getOrNull(i) ?: return@Box, cards, sheetStyle, sheetFilter, selection,
                        onTap = { idx ->
                            if (selecting) vm.toggleSelect(cards[idx].id) else onEdit(idx)
                        },
                        onLongPress = { idx -> vm.toggleSelect(cards[idx].id) },
                    )
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
                ToolButton("Shapes", enabled = cards.isNotEmpty()) { showShapes = true }
                ToolButton("Style", enabled = cards.isNotEmpty()) { showStyle = true }
                ToolButton("Filter", enabled = cards.isNotEmpty()) { showFilter = true }
                ToolButton("Reorder", enabled = cards.size > 1, onClick = onReorder)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.border(1.dp, colors.line, MaterialTheme.shapes.small)
                        .clickable(enabled = cards.isNotEmpty()) { showShapes = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) { Text(common?.let { "${it.displayName} ${it.sizeLabel}" } ?: "Mixed shapes", style = MonoLabel) }
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
                enabled = cards.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
            ) { Text("Export PDF", fontWeight = FontWeight.Medium) }
        }
    }
    if (showStyle) StyleSheet(sheetStyle, vm::setStyle) { showStyle = false }
    if (showFilter) FilterSheet(vm, sheetFilter, cards.firstNotNullOfOrNull { c -> c.filledSlots.firstOrNull()?.uri }) { showFilter = false }
    if (renaming) NameDialog("Rename sheet", name, { renaming = false }) { vm.setName(it); renaming = false }
    if (showShapes) ShapeSheet(vm, cards, selection, group) { showShapes = false }
    ExportSheet(vm)
}

@Composable
private fun PagePreview(
    vm: SheetViewModel, page: SheetPage, cards: List<Card>, sheetStyle: CardStyle, sheetFilter: FilterSettings, selection: Set<Long>,
    onTap: (Int) -> Unit, onLongPress: (Int) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    // Only this page's cards matter: changes elsewhere do not trigger a re-render.
    val pageCards = page.items.map { cards.getOrNull(it.index) }
    val sameShape = page.items.map { it.index }.map { i -> cards.take(i).count { c -> c.templateId == cards[i].templateId } }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pageCards, sameShape, page, sheetStyle, sheetFilter) {
        if (pageCards.any { it == null }) return@LaunchedEffect
        val bmp: Bitmap = vm.renderPreview(page, cards, sheetStyle, sheetFilter, PREVIEW_PX_PER_MM)
        image = bmp.asImageBitmap()
    }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val accent = GridTheme.colors.accent
    fun hit(o: Offset): Int {
        if (size.width == 0) return -1
        val x = o.x / size.width * vm.paper.widthMm
        val y = o.y / size.height * vm.paper.heightMm
        return page.items.firstOrNull { it.rect.contains(x, y) }?.index ?: -1
    }
    Box(
        Modifier
            .aspectRatio(vm.paper.widthMm / vm.paper.heightMm)
            .shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(1.dp))
            .background(Color.White)
            .onSizeChanged { size = it }
            .pointerInput(page, size) {
                detectTapGestures(
                    onTap = { o: Offset -> hit(o).let { if (it >= 0) tap(it) } },
                    onLongPress = { o: Offset -> hit(o).let { if (it >= 0) longPress(it) } },
                )
            },
    ) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        if (selection.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            val s = this.size.width / vm.paper.widthMm
            for (item in page.items) {
                if (cards.getOrNull(item.index)?.id !in selection) continue
                val r = item.rect
                val tl = Offset(r.left * s, r.top * s)
                val sz = Size(r.width * s, r.height * s)
                drawRect(accent.copy(alpha = 0.16f), tl, sz)
                drawRect(accent, tl, sz, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShapeSheet(vm: SheetViewModel, cards: List<Card>, selection: Set<Long>, group: Boolean, onDismiss: () -> Unit) {
    val colors = GridTheme.colors
    val targets = if (selection.isEmpty()) cards else cards.filter { it.id in selection }
    val current = commonTemplate(targets)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Shape", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                if (selection.isEmpty()) "Applies to all ${cards.size} cards" else "Applies to ${selection.size} selected",
                style = MonoLabel, color = colors.muted,
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("GROUP SAME SHAPES", style = MonoLabel, modifier = Modifier.weight(1f))
                Switch(
                    checked = group, onCheckedChange = vm::setGroupByShape,
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = Color.White),
                )
            }
            for (g in CardGroup.entries) {
                SectionLabel(g.label.uppercase())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (t in CardTemplate.All.filter { it.group == g }) {
                        ShapeOption(vm, t, selected = current?.id == t.id) { vm.applyShape(t.id); onDismiss() }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShapeOption(vm: SheetViewModel, t: CardTemplate, selected: Boolean, onClick: () -> Unit) {
    val colors = GridTheme.colors
    val thumb by produceState<ImageBitmap?>(null, t.id) { value = vm.thumbnail(t).asImageBitmap() }
    Column(
        Modifier.width(96.dp).border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.line, MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            thumb?.let { Image(it, t.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.height(4.dp))
        Text(t.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Text(t.sizeLabel, style = MonoLabel.copy(fontSize = 10.sp), color = colors.muted, maxLines = 1)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(vm: SheetViewModel, filter: FilterSettings, sampleUri: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Sheet filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("Applies to every card without its own filter", style = MonoLabel, color = GridTheme.colors.muted)
            FilterControls(filter, sampleUri, vm.photos, vm::setFilter)
        }
    }
}
