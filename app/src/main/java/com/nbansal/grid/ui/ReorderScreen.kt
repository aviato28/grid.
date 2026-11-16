package com.nbansal.grid.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nbansal.grid.sheet.Card
import com.nbansal.grid.sheet.SheetViewModel
import com.nbansal.grid.ui.theme.GridTheme
import com.nbansal.grid.ui.theme.MonoLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Full-screen 3-column grid of card thumbnails; long-press a tile and drag to reorder with live re-flow. */
@Composable
fun ReorderScreen(vm: SheetViewModel, onDone: () -> Unit) {
    val colors = GridTheme.colors
    val cards by vm.cards.collectAsStateWithLifecycle()
    val style by vm.style.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val group by vm.groupByShape.collectAsStateWithLifecycle()
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cardsNow by rememberUpdatedState(cards)

    var draggedId by remember { mutableStateOf<Long?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }
    var boxHeight by remember { mutableStateOf(0) }

    fun endDrag() { draggedId = null }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Reorder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("Long-press a card, then drag", style = MonoLabel, color = colors.muted)
            }
            TextButton(onClick = onDone) { Text("Done", color = colors.accent, fontWeight = FontWeight.Medium) }
        }
        if (group && cards.map { it.templateId }.distinct().size > 1) {
            Text(
                "Group same shapes is on: order applies within each shape group.",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MonoLabel, color = colors.muted,
            )
        }
        HorizontalDivider(color = colors.line, modifier = Modifier.padding(top = 4.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { boxHeight = it.height }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o ->
                            val hit = state.layoutInfo.visibleItemsInfo.firstOrNull {
                                o.x >= it.offset.x && o.x < it.offset.x + it.size.width &&
                                    o.y >= it.offset.y && o.y < it.offset.y + it.size.height
                            }
                            if (hit != null) {
                                draggedId = hit.key as? Long
                                itemSize = hit.size
                                grab = Offset(o.x - hit.offset.x, o.y - hit.offset.y)
                                pointer = o
                            }
                        },
                        onDrag = { change, delta ->
                            val id = draggedId ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            pointer += delta
                            val centre = pointer - grab + Offset(itemSize.width / 2f, itemSize.height / 2f)
                            val target = state.layoutInfo.visibleItemsInfo.firstOrNull {
                                centre.x >= it.offset.x && centre.x < it.offset.x + it.size.width &&
                                    centre.y >= it.offset.y && centre.y < it.offset.y + it.size.height
                            }
                            val from = cardsNow.indexOfFirst { it.id == id }
                            if (target != null && from >= 0 && target.index != from) vm.move(from, target.index)
                            val edge = 90.dp.toPx()
                            val step = when {
                                pointer.y < edge -> -28f
                                pointer.y > boxHeight - edge -> 28f
                                else -> 0f
                            }
                            if (step != 0f) scope.launch { state.scrollBy(step) }
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                    )
                },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), state = state,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = draggedId == null,
            ) {
                itemsIndexed(cards, key = { _, c -> c.id }) { i, c ->
                    Tile(vm, c, i, style, filter, Modifier.animateItem().alpha(if (draggedId == c.id) 0.25f else 1f))
                }
            }
            val dragged = cards.firstOrNull { it.id == draggedId }
            if (dragged != null) {
                val pos = pointer - grab
                Tile(
                    vm, dragged, cards.indexOf(dragged), style, filter,
                    Modifier
                        .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                        .size(with(density) { itemSize.width.toDp() }, with(density) { itemSize.height.toDp() })
                        .graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }
                        .shadow(14.dp, MaterialTheme.shapes.small),
                )
            }
        }
    }
}

@Composable
private fun Tile(vm: SheetViewModel, card: Card, index: Int, style: com.nbansal.grid.sheet.CardStyle, filter: com.nbansal.grid.filter.FilterSettings, modifier: Modifier) {
    val colors = GridTheme.colors
    val thumb by produceState<ImageBitmap?>(null, card, style, filter) { value = vm.cardThumbnail(card, style, filter).asImageBitmap() }
    Box(
        modifier.fillMaxWidth().aspectRatio(0.78f)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(1.dp, colors.line, MaterialTheme.shapes.small)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Box(
            Modifier.align(Alignment.TopStart).background(colors.accent, MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 1.dp),
        ) { Text("${index + 1}", style = MonoLabel, color = Color.White) }
    }
}
