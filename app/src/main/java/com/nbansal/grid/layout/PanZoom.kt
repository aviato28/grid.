package com.nbansal.grid.layout

import kotlin.math.max

/** Pure pan/zoom math for a photo inside a window; everything in mm except bitmap sizes in px. */
object PanZoom {
    const val MAX_ZOOM = 5f

    /** mm of image per bitmap pixel at zoom 1 (center-crop "cover"). */
    fun baseScale(bw: Float, bh: Float, window: RectMm) = max(window.width / bw, window.height / bh)

    /** Maximum pan in mm (per axis) at [zoom] such that the window stays covered. */
    fun panRangeMm(bw: Float, bh: Float, zoom: Float, window: RectMm): Pair<Float, Float> {
        val s = baseScale(bw, bh, window) * zoom.coerceAtLeast(1f)
        return max(0f, (bw * s - window.width) / 2) to max(0f, (bh * s - window.height) / 2)
    }

    data class State(val zoom: Float, val panX: Float, val panY: Float)

    /**
     * Applies a pinch/drag step. [cx]/[cy] is the gesture centroid in card mm, [dxMm]/[dyMm] the
     * centroid movement in mm and [zoomFactor] the pinch factor. The image point under the
     * centroid stays under it.
     */
    fun transform(
        bw: Float, bh: Float, window: RectMm, state: State,
        cx: Float, cy: Float, dxMm: Float, dyMm: Float, zoomFactor: Float,
    ): State {
        val z0 = state.zoom.coerceAtLeast(1f)
        val z1 = (z0 * zoomFactor).coerceIn(1f, MAX_ZOOM)
        val (rx0, ry0) = panRangeMm(bw, bh, z0, window)
        val (rx1, ry1) = panRangeMm(bw, bh, z1, window)
        val wcx = (window.left + window.right) / 2
        val wcy = (window.top + window.bottom) / 2
        val ratio = z1 / z0
        // Image centre offset from the window centre, before and after.
        val ox0 = state.panX.coerceIn(-1f, 1f) * rx0
        val oy0 = state.panY.coerceIn(-1f, 1f) * ry0
        val ox1 = (cx - wcx) + (ox0 - (cx - wcx)) * ratio + dxMm
        val oy1 = (cy - wcy) + (oy0 - (cy - wcy)) * ratio + dyMm
        val px = if (rx1 > 0f) (ox1 / rx1).coerceIn(-1f, 1f) else 0f
        val py = if (ry1 > 0f) (oy1 / ry1).coerceIn(-1f, 1f) else 0f
        return State(z1, px, py)
    }
}
