package com.nbansal.grid.sheet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbansal.grid.draft.Draft
import com.nbansal.grid.draft.DraftStore
import com.nbansal.grid.draft.defaultDraftName
import com.nbansal.grid.filter.FilterSettings
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.layout.packSheet
import com.nbansal.grid.render.CaptionFonts
import com.nbansal.grid.render.FilteredPhotos
import com.nbansal.grid.render.PdfExporter
import com.nbansal.grid.render.PhotoImporter
import com.nbansal.grid.render.PhotoLoader
import com.nbansal.grid.render.SheetRenderer
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID
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
    val photos = FilteredPhotos(loader)
    val store = DraftStore(File(app.filesDir, "drafts"))
    private val typefaces = HashMap<CaptionFont, Typeface>()
    val renderer = SheetRenderer { f -> typefaces.getOrPut(f) { CaptionFonts.load(app, f) } }

    private var nextId = 1L
    private fun newId() = nextId++

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

    /** Sheet-wide film filter; cards may override it. */
    private val _filter = MutableStateFlow(FilterSettings())
    val filter: StateFlow<FilterSettings> = _filter.asStateFlow()
    fun setFilter(f: FilterSettings) { _filter.value = f }

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    fun setName(n: String) { _name.value = n.trim().ifEmpty { _name.value }.take(60) }

    // ---- Drafts -----------------------------------------------------------------------------------------

    private var draftId: String? = null
    private var createdAt = 0L
    private var lastSaved: Draft? = null
    private val saveLock = Mutex()
    private val _drafts = MutableStateFlow<List<Draft>>(emptyList())
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()
    private val draftThumbs = LruCache<String, Bitmap>(24)

    @OptIn(FlowPreview::class)
    private val autosave = viewModelScope.launch {
        combine(_cards, _style, _filter, _group, _name) { _, _, _, _, _ -> }
            .debounce(500)
            .collect { saveIfChanged() }
    }

    private fun snapshot(): Draft? {
        val id = draftId ?: return null
        return Draft(id, _name.value, createdAt, System.currentTimeMillis(), _cards.value, _style.value, _filter.value, _group.value)
    }

    /** Writes the open draft when it differs from what is on disk. Atomic; serialised with other saves. */
    suspend fun saveIfChanged() {
        val d = snapshot() ?: return
        saveLock.withLock {
            if (lastSaved?.sameContent(d) == true) return
            withContext(Dispatchers.IO) { store.save(d) }
            lastSaved = d
        }
    }

    /** Saves right now (screen left / app stopped); survives viewModelScope cancellation. */
    fun flush() { ioScope.launch { saveIfChanged() } }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun refreshDrafts() {
        saveIfChanged()
        val list = withContext(Dispatchers.IO) { saveLock.withLock { store.list() } }
        _drafts.value = list
    }

    private fun resetTo(d: Draft?) {
        draftId = null
        _cards.value = d?.cards ?: emptyList()
        _style.value = d?.sheetStyle ?: CardStyle()
        _filter.value = d?.sheetFilter ?: FilterSettings()
        _group.value = d?.groupByShape ?: true
        _name.value = d?.name ?: defaultDraftName(LocalDate.now())
        _selection.value = emptySet()
        nextId = (d?.cards?.maxOfOrNull { it.id } ?: 0L) + 1
        createdAt = d?.createdAt ?: System.currentTimeMillis()
        lastSaved = d
        draftId = d?.id
    }

    /** Creates a draft from freshly picked photos and makes it the open sheet. */
    fun startNewSheet(uris: List<Uri>) {
        resetTo(null)
        draftId = UUID.randomUUID().toString()
        val w = build(uris.take(MAX_PHOTOS), CardTemplate.Mini, MAX_PHOTOS)
        _cards.value = w
        importPhotos(w)
    }

    suspend fun openDraft(id: String): Boolean {
        val d = withContext(Dispatchers.IO) { saveLock.withLock { store.load(id)?.also { store.prunePhotos(it) } } } ?: return false
        resetTo(d)
        // Finish copying photos whose import was interrupted (e.g. the app was closed mid-import).
        importPhotos(d.cards.filter { c -> c.slots.any { it.uri?.startsWith("content:") == true } })
        return true
    }

    suspend fun renameDraft(id: String, name: String) {
        withContext(Dispatchers.IO) { saveLock.withLock { store.rename(id, name.trim().take(60).ifEmpty { "Untitled" }, System.currentTimeMillis()) } }
        refreshDrafts()
    }

    suspend fun duplicateDraft(id: String) {
        withContext(Dispatchers.IO) {
            saveLock.withLock {
                val src = store.load(id) ?: return@withLock
                store.duplicate(id, UUID.randomUUID().toString(), "${src.name} copy".take(60), System.currentTimeMillis())
            }
        }
        refreshDrafts()
    }

    suspend fun deleteDraft(id: String) {
        withContext(Dispatchers.IO) { saveLock.withLock { store.delete(id) } }
        if (draftId == id) { draftId = null; lastSaved = null }
        refreshDrafts()
    }

    /** Page-1 thumbnail of a stored draft, cached per revision. */
    suspend fun draftThumbnail(d: Draft): Bitmap? {
        val key = "${d.id}:${d.updatedAt}"
        draftThumbs.get(key)?.let { return it }
        val page = packSheet(d.cards.map { it.template }, d.groupByShape).firstOrNull() ?: return null
        return renderPreview(page, d.cards, d.sheetStyle, d.sheetFilter, 1.4f).also { draftThumbs.put(key, it) }
    }

    /** Copies a freshly picked photo into the open draft; falls back to the original uri if that fails. */
    suspend fun importPhoto(uri: Uri): String {
        val id = draftId ?: return uri.toString()
        val dest = File(store.photosDir(id), "${UUID.randomUUID()}.jpg")
        val meta = PhotoMetaReader.read(getApplication(), uri)
        val ok = withContext(Dispatchers.IO) { PhotoImporter.import(getApplication(), uri, dest, meta.date) }
        return if (ok) store.fileUri(dest) else uri.toString()
    }

    /**
     * Copies every photo of [added] into the draft folder in the background and swaps the card's uri for
     * the copy; also fills date/location from the original's EXIF without overwriting user input.
     */
    private fun importPhotos(added: List<Card>) {
        val id = draftId ?: return
        viewModelScope.launch {
            for (c in added) c.slots.forEachIndexed { i, s ->
                val original = s.uri ?: return@forEachIndexed
                if (draftId != id) return@launch
                if (original.startsWith("file://")) return@forEachIndexed
                val meta = if (i == 0) PhotoMetaReader.read(getApplication(), Uri.parse(original)) else null
                val dest = File(store.photosDir(id), "${UUID.randomUUID()}.jpg")
                val ok = withContext(Dispatchers.IO) { PhotoImporter.import(getApplication(), Uri.parse(original), dest, meta?.date) }
                if (draftId != id) { dest.delete(); return@launch }
                _cards.update { list ->
                    list.map { cur ->
                        if (cur.id != c.id) cur else cur.copy(
                            slots = if (!ok) cur.slots else cur.slots.map { sl -> if (sl.uri == original) sl.copy(uri = store.fileUri(dest)) else sl },
                            date = cur.date ?: meta?.date,
                            location = cur.location.ifBlank { meta?.location ?: "" },
                        )
                    }
                }
            }
        }
    }

    private fun build(uris: List<Uri>, t: CardTemplate, room: Int): List<Card> =
        uris.map { it.toString() }.chunked(t.windowCount).take(room).map { newCard(newId(), t, it) }

    /** New cards use the sheet's shape when all cards share one, else Mini. */
    fun addPhotos(uris: List<Uri>) {
        val t = commonTemplate(_cards.value) ?: CardTemplate.Mini
        val w = build(uris, t, (MAX_PHOTOS - _cards.value.size).coerceAtLeast(0))
        _cards.update { it + w }
        importPhotos(w)
    }

    /** Replaces the card with the same id by [card]. */
    fun replace(card: Card) = _cards.update { list -> list.map { if (it.id == card.id) card else it } }

    fun applyCaptionToAll(from: Card) = _cards.update { l -> l.map { it.copy(caption = from.caption.take(it.template.captionMax)) } }

    fun remove(id: Long) {
        _cards.update { l -> l.filterNot { it.id == id } }
        _selection.update { it - id }
    }

    /** Moves the card at [from] to position [to] (drag to reorder). */
    fun move(from: Int, to: Int) = _cards.update { l ->
        if (from !in l.indices || to !in l.indices || from == to) l else l.toMutableList().also { it.add(to, it.removeAt(from)) }
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
        val bmp = cardBitmap(Card(0, t.id, emptySlots(t), caption = if (t.fields.isEmpty()) "" else "Hello"), CardStyle(), emptyList(), maxPx)
        synchronized(thumbs) { thumbs[t.id] = bmp }
        bmp
    }

    private fun cardBitmap(card: Card, style: CardStyle, bitmaps: List<Bitmap?>, maxPx: Int): Bitmap {
        val t = card.template
        val ppm = maxPx / maxOf(t.cardWidthMm, t.cardHeightMm)
        val bmp = Bitmap.createBitmap(
            (t.cardWidthMm * ppm).toInt().coerceAtLeast(1), (t.cardHeightMm * ppm).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        canvas.scale(ppm, ppm)
        renderer.drawCard(canvas, card, style, bitmaps, 1, preview = true)
        return bmp
    }

    private val cardThumbs = LruCache<String, Bitmap>(160)

    /** Thumbnail of one card for the Reorder grid, cached by content. */
    suspend fun cardThumbnail(card: Card, sheetStyle: CardStyle, sheetFilter: FilterSettings, maxPx: Int = 260): Bitmap {
        val key = "${card.hashCode()}|${sheetStyle.hashCode()}|${sheetFilter.hashCode()}|$maxPx"
        cardThumbs.get(key)?.let { return it }
        val bitmaps = card.slots.indices.map { photos.preview(card, it, card.filterFor(sheetFilter)) }
        return withContext(Dispatchers.Default) { cardBitmap(card, sheetStyle, bitmaps, maxPx) }.also { cardThumbs.put(key, it) }
    }

    /** Renders one page into a bitmap at [pxPerMm] using the same renderer as the PDF. */
    suspend fun renderPreview(page: SheetPage, all: List<Card>, sheetStyle: CardStyle, sheetFilter: FilterSettings, pxPerMm: Float): Bitmap {
        val bitmaps = HashMap<Pair<Int, Int>, Bitmap>()
        for (item in page.items) {
            val c = all[item.index]
            c.slots.indices.forEach { k -> photos.preview(c, k, c.filterFor(sheetFilter))?.let { bitmaps[item.index to k] = it } }
        }
        return withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(
                (paper.widthMm * pxPerMm).toInt(), (paper.heightMm * pxPerMm).toInt(), Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            canvas.scale(pxPerMm, pxPerMm)
            renderer.drawPage(canvas, paper, page, all, sheetStyle, { ci, k -> bitmaps[ci to k] }, preview = true)
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
                val r = PdfExporter(renderer, photos, dir).export(snapshot, _style.value, _filter.value, group, paper) { p, t ->
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
