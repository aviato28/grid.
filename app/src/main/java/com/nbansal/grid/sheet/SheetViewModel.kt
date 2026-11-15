package com.nbansal.grid.sheet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nbansal.grid.R
import com.nbansal.grid.layout.CardTemplate
import com.nbansal.grid.layout.Paper
import com.nbansal.grid.layout.SheetPage
import com.nbansal.grid.layout.layoutSheet
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
    val template = CardTemplate.Mini
    val loader = PhotoLoader(app.contentResolver)
    val renderer = SheetRenderer(
        ResourcesCompat.getFont(app, R.font.caveat) ?: Typeface.SERIF,
        template,
    )

    private var nextId = 1L
    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()
    private var exportJob: Job? = null

    private fun wrap(uris: List<Uri>) = uris.map { Photo(nextId++, it) }

    fun setPhotos(uris: List<Uri>) { _photos.value = wrap(uris.take(MAX_PHOTOS)) }

    fun addPhotos(uris: List<Uri>) {
        _photos.update { (it + wrap(uris)).take(MAX_PHOTOS) }
    }

    fun update(id: Long, caption: String, zoom: Float, panX: Float, panY: Float) = _photos.update { list ->
        list.map { if (it.id == id) it.copy(caption = caption, zoom = zoom, panX = panX, panY = panY) else it }
    }

    fun applyCaptionToAll(caption: String) = _photos.update { l -> l.map { it.copy(caption = caption) } }

    fun remove(id: Long) = _photos.update { l -> l.filterNot { it.id == id } }

    /** Renders one page into a bitmap at [pxPerMm] using the same renderer as the PDF. */
    suspend fun renderPreview(page: SheetPage, all: List<Photo>, pxPerMm: Float): Bitmap {
        val bitmaps = HashMap<Long, Bitmap>()
        for (j in page.cards.indices) {
            val p = all.getOrNull(page.firstIndex + j) ?: continue
            loader.preview(p.uri)?.let { bitmaps[p.id] = it }
        }
        return withContext(Dispatchers.Default) {
            val bmp = Bitmap.createBitmap(
                (paper.widthMm * pxPerMm).toInt(), (paper.heightMm * pxPerMm).toInt(), Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            canvas.scale(pxPerMm, pxPerMm)
            renderer.drawPage(canvas, paper, page, all) { bitmaps[it.id] }
            bmp
        }
    }

    fun startExport() {
        val snapshot = _photos.value
        if (snapshot.isEmpty() || exportJob?.isActive == true) return
        val pages = layoutSheet(paper, template, snapshot.size).size
        _export.value = ExportState.Rendering(1, pages)
        val dir = File(getApplication<Application>().cacheDir, "exports")
        exportJob = viewModelScope.launch {
            try {
                val r = PdfExporter(renderer, loader, dir).export(snapshot, paper, template) { p, t ->
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
