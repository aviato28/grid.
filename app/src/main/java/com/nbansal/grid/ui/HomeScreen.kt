package com.nbansal.grid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbansal.grid.draft.Draft
import com.nbansal.grid.draft.timeAgo
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.sheet.commonTemplate
import kotlinx.coroutines.launch
import com.nbansal.grid.BuildConfig
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import com.nbansal.grid.ui.theme.SpaceGrotesk

@Composable
fun HomeScreen(vm: SheetViewModel, checking: Boolean, onNewSheet: () -> Unit, onOpenDraft: (String) -> Unit, onCheckForUpdates: () -> Unit) {
    val colors = GridTheme.colors
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.refreshDrafts() }
    var renaming by remember { mutableStateOf<Draft?>(null) }
    var deleting by remember { mutableStateOf<Draft?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            buildAnnotatedString {
                append("Grid")
                withStyle(SpanStyle(color = colors.accent)) { append(".") }
            },
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNewSheet,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ),
        ) { Text("New sheet", fontWeight = FontWeight.Medium) }

        Spacer(Modifier.height(32.dp))
        Text("DRAFTS", style = MonoLabel, color = colors.muted)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.line)
        if (drafts.isEmpty()) {
            Text("No drafts yet. Start a sheet above; it saves automatically.", color = colors.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(drafts, key = { it.id }) { d ->
                    DraftRow(
                        vm, d, onOpen = { onOpenDraft(d.id) }, onRename = { renaming = d },
                        onDuplicate = { scope.launch { vm.duplicateDraft(d.id) } }, onDelete = { deleting = d },
                    )
                }
            }
        }
        HorizontalDivider(color = colors.line)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("v${BuildConfig.VERSION_NAME}", style = MonoLabel, color = colors.muted)
            TextButton(onClick = onCheckForUpdates, enabled = !checking) {
                Text(if (checking) "Checking…" else "Check for updates", color = colors.accent)
            }
        }
    }
    renaming?.let { d ->
        NameDialog("Rename sheet", d.name, { renaming = null }) { n -> scope.launch { vm.renameDraft(d.id, n) }; renaming = null }
    }
    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${d.name}”?") },
            text = { Text("This removes the sheet and its saved photos. It can’t be undone.") },
            confirmButton = { TextButton(onClick = { scope.launch { vm.deleteDraft(d.id) }; deleting = null }) { Text("Delete", color = colors.accent) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = colors.muted) } },
        )
    }
}

@Composable
private fun DraftRow(
    vm: SheetViewModel, d: Draft, onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit,
) {
    val colors = GridTheme.colors
    val thumb by produceState<ImageBitmap?>(null, d.id, d.updatedAt) { value = vm.draftThumbnail(d)?.asImageBitmap() }
    var menu by remember { mutableStateOf(false) }
    val shape = commonTemplate(d.cards)?.displayName ?: if (d.cards.isEmpty()) "Empty" else "Mixed"
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp, 80.dp).background(androidx.compose.ui.graphics.Color.White).border(1.dp, colors.line)) {
            thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                "${d.cards.size} ${if (d.cards.size == 1) "card" else "cards"} · $shape · edited ${timeAgo(System.currentTimeMillis(), d.updatedAt)}",
                style = MonoLabel, color = colors.muted,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
