package com.nbansal.grid.sheet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.layout.packSheet
import com.nbansal.grid.render.CaptionFonts
import com.nbansal.grid.render.PdfExporter
import com.nbansal.grid.render.PhotoLoader
import com.nbansal.grid.render.SheetRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Rendering(val page: Int, val total: Int) : ExportState
    data class Done(val file: File, val pages: Int) : ExportState
    data class Failed(val message: String) : ExportState
}

class SheetViewModel(app: Application) : AndroidViewModel(app) {
    val paper = Paper.A4
    val loader = PhotoLoader(app.contentResolver)
    private val typefaces = HashMap<CaptionFont, Typeface>()
    val renderer = SheetRenderer { f -> typefaces.getOrPut(f) { CaptionFonts.load(app, f) } }

    private var nextId = 1L
    private fun newId() = nextId++

    /** Stage C: persist this list (plain data classes; uris are strings). */
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _group = MutableStateFlow(true)
    val groupByShape: StateFlow<Boolean> = _group.asStateFlow()
    fun setGroupByShape(on: Boolean) { _group.value = on }

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()
    private var exportJob: Job? = null

    /** Sheet-wide default style; cards may override it. */
    private val _style = MutableStateFlow(CardStyle())
    val style: StateFlow<CardStyle> = _style.asStateFlow()
    fun setStyle(s: CardStyle) { _style.value = s }

    /** Fills date/location from EXIF in the background; never overwrites what the user already set. */
    private fun prefill(added: List<Card>) {
        viewModelScope.launch {
            for (c in added) {
                val uri = c.slots.firstOrNull()?.uri ?: continue
                val meta = PhotoMetaReader.read(getApplication(), Uri.parse(uri))
                _cards.update { list ->
                    list.map {
                        if (it.id != c.id) it
                        else it.copy(date = it.date ?: meta.date, location = it.location.ifBlank { meta.location })
                    }
                }
            }
        }
    }

    private fun build(uris: List<Uri>, t: CardTemplate, room: Int): List<Card> =
        uris.map { it.toString() }.chunked(t.windowCount).take(room).map { newCard(newId(), t, it) }

    fun setPhotos(uris: List<Uri>) {
        val w = build(uris.take(MAX_PHOTOS), CardTemplate.Mini, MAX_PHOTOS)
        _cards.value = w
        _selection.value = emptySet()
        prefill(w)
    }

    /** New cards use the sheet's shape when all cards share one, else Mini. */
    fun addPhotos(uris: List<Uri>) {
        val t = commonTemplate(_cards.value) ?: CardTemplate.Mini
        val w = build(uris, t, (MAX_PHOTOS - _cards.value.size).coerceAtLeast(0))
        _cards.update { it + w }
        prefill(w)
    }

    /** Replaces the card with the same id by [card]. */
    fun replace(card: Card) = _cards.update { list -> list.map { if (it.id == card.id) card else it } }

    fun applyCaptionToAll(from: Card) = _cards.update { l -> l.map { it.copy(caption = from.caption.take(it.template.captionMax)) } }

    fun remove(id: Long) {
        _cards.update { l -> l.filterNot { it.id == id } }
        _selection.update { it - id }
    }

    // Selection
    fun toggleSelect(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun selectAll() { _selection.value = _cards.value.map { it.id }.toSet() }
    fun clearSelection() { _selection.value = emptySet() }

    fun applyShape(templateId: String) {
        _cards.update { applyShape(it, _selection.value, templateId, ::newId) }
        _selection.value = emptySet()
    }

    // Thumbnails of shapes for the Shape sheet, rendered by the real renderer.
    private val thumbs = HashMap<String, Bitmap>()

    suspend fun thumbnail(t: CardTemplate, maxPx: Int = 180): Bitmap = withContext(Dispatchers.Default) {
        synchronized(thumbs) { thumbs[t.id] }?.let { return@withContext it }
        val ppm = maxPx / maxOf(t.cardWidthMm, t.cardHeightMm)
        val bmp = Bitmap.createBitmap(
            (t.cardWidthMm * ppm).toInt().coerceAtLeast(1), (t.cardHeightMm * ppm).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        canvas.scale(ppm, ppm)
        val sample = Card(0, t.id, emptySlots(t), caption = if (t.fields.isEmpty()) "" else "Hello")
        renderer.drawCard(canvas, sample, CardStyle(), emptyList(), 1, preview = true)
        synchronized(thumbs) { thumbs[t.id] = bmp }
        bmp
    }

    /** Renders one page into a bitmap at [pxPerMm] using the same renderer as the PDF. */
    suspend fun renderPreview(page: SheetPage, all: List<Card>, sheetStyle: CardStyle, pxPerMm: Float): Bitmap {
        val bitmaps = HashMap<String, Bitmap>()
        for (item in page.items) for (s in all[item.index].slots) {
            val u = s.uri ?: continue
            if (u !in bitmaps) loader.preview(u)?.let { bitmaps[u] = it }
        }
        return withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(
                (paper.widthMm * pxPerMm).toInt(), (paper.heightMm * pxPerMm).toInt(), Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            canvas.scale(pxPerMm, pxPerMm)
            renderer.drawPage(canvas, paper, page, all, sheetStyle, { bitmaps[it] }, preview = true)
            bmp
        }
    }

    fun startExport() {
        val snapshot = _cards.value
        if (snapshot.isEmpty() || exportJob?.isActive == true) return
        val group = _group.value
        val pages = packSheet(snapshot.map { it.template }, group).size
        _export.value = ExportState.Rendering(1, pages)
        val dir = File(getApplication<Application>().cacheDir, "exports")
        exportJob = viewModelScope.launch {
            try {
                val r = PdfExporter(renderer, loader, dir).export(snapshot, _style.value, group, paper) { p, t ->
                    _export.value = ExportState.Rendering(p, t)
                }
                _export.value = ExportState.Done(r.file, r.pages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _export.value = ExportState.Failed(e.message ?: "Export failed")
            }
        }
    }

    fun dismissExport() {
        exportJob?.cancel()
        _export.value = ExportState.Idle
    }
}
