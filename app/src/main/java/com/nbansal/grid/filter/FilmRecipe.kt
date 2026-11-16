package com.nbansal.grid.filter

import kotlin.math.max
import kotlin.math.min

/** A tone-curve control point; x and y are 0..1. */
data class Pt(val x: Float, val y: Float)

/** Tone curve defined by control points; evaluated with monotone cubic interpolation into a 256-entry LUT. */
class ToneCurve(points: List<Pt>) {
    val points: List<Pt> = points.sortedBy { it.x }

    fun eval(x: Float): Float {
        val p = points
        if (p.isEmpty()) return x
        if (p.size == 1) return p[0].y
        if (x <= p.first().x) return p.first().y
        if (x >= p.last().x) return p.last().y
        var i = 0
        while (i < p.size - 2 && x > p[i + 1].x) i++
        val h = p[i + 1].x - p[i].x
        if (h <= 0f) return p[i].y
        val t = (x - p[i].x) / h
        val m = tangents()
        val t2 = t * t
        val t3 = t2 * t
        return ((2 * t3 - 3 * t2 + 1) * p[i].y + (t3 - 2 * t2 + t) * h * m[i] +
            (-2 * t3 + 3 * t2) * p[i + 1].y + (t3 - t2) * h * m[i + 1])
    }

    private val tan: FloatArray by lazy {
        val n = points.size
        val d = FloatArray(n - 1) { (points[it + 1].y - points[it].y) / max(1e-6f, points[it + 1].x - points[it].x) }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f } else {
                val a = m[i] / d[i]; val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9f) { val k = 3f / kotlin.math.sqrt(s); m[i] = k * a * d[i]; m[i + 1] = k * b * d[i] }
            }
        }
        m
    }

    private fun tangents(): FloatArray = tan

    /** 256 entries, 0..1. */
    fun lut(): FloatArray = FloatArray(256) { eval(it / 255f).coerceIn(0f, 1f) }

    companion object {
        val Identity = ToneCurve(listOf(Pt(0f, 0f), Pt(1f, 1f)))
        /** `curve(0f, 0.02f, 0.5f, 0.55f, 1f, 1f)`: x,y pairs. */
        fun of(vararg xy: Float): ToneCurve = ToneCurve(xy.toList().chunked(2).map { Pt(it[0], it[1]) })
    }
}

/**
 * A film look as data. Curves are applied master first, then per channel. [saturation] 1 = unchanged;
 * [temperature] / [tint] are -1..1 (warm / magenta positive); [contrast] is a multiplier around mid grey;
 * [fade] lifts the blacks (0..0.3). Grain size is in millimetres on paper so preview and print match.
 */
data class FilmRecipe(
    val master: ToneCurve = ToneCurve.Identity,
    val red: ToneCurve = ToneCurve.Identity,
    val green: ToneCurve = ToneCurve.Identity,
    val blue: ToneCurve = ToneCurve.Identity,
    val saturation: Float = 1f,
    val mono: Boolean = false,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val contrast: Float = 1f,
    val fade: Float = 0f,
    val grainAmount: Float = 0f,
    val grainSizeMm: Float = 0.12f,
    val vignette: Float = 0f,
    val lightLeak: Float = 0f,
    val halation: Float = 0f,
    val halationColor: Int = 0xFFFF5A2A.toInt(),
) {
    val isNeutral: Boolean
        get() = this == None

    companion object {
        val None = FilmRecipe()
    }
}

private fun clamp01(v: Float) = min(1f, max(0f, v))

/** A named preset. [inspiredBy] is a note about the stock it evokes; only [name] is shown to users. */
data class FilmFilter(val id: String, val name: String, val inspiredBy: String, val recipe: FilmRecipe) {
    companion object {
        private fun c(vararg xy: Float) = ToneCurve.of(*xy)

        val None = FilmFilter("none", "None", "", FilmRecipe.None)

        val Gold = FilmFilter(
            "gold", "Gold", "Kodak Gold 200",
            FilmRecipe(
                master = c(0f, 0.02f, 0.25f, 0.24f, 0.5f, 0.53f, 0.75f, 0.79f, 1f, 0.98f),
                red = c(0f, 0f, 0.5f, 0.56f, 1f, 1f), blue = c(0f, 0.03f, 0.5f, 0.42f, 1f, 0.86f),
                saturation = 1.15f, temperature = 0.18f, fade = 0.03f,
                grainAmount = 0.35f, grainSizeMm = 0.12f, vignette = 0.2f, halation = 0.12f, halationColor = 0xFFFF9A3A.toInt(),
            ),
        )
        val Portrait = FilmFilter(
            "portrait", "Portrait", "Kodak Portra 400",
            FilmRecipe(
                master = c(0f, 0.03f, 0.3f, 0.31f, 0.6f, 0.64f, 1f, 0.99f),
                red = c(0f, 0f, 0.5f, 0.54f, 1f, 1f), blue = c(0f, 0.02f, 0.5f, 0.46f, 1f, 0.93f),
                saturation = 0.92f, temperature = 0.08f, fade = 0.02f,
                grainAmount = 0.2f, grainSizeMm = 0.11f, vignette = 0.12f, halation = 0.15f, halationColor = 0xFFFFA78A.toInt(),
            ),
        )
        val Vivid = FilmFilter(
            "vivid", "Vivid", "Kodak Ektar 100",
            FilmRecipe(
                master = c(0f, 0f, 0.25f, 0.19f, 0.5f, 0.5f, 0.75f, 0.82f, 1f, 1f),
                blue = c(0f, 0f, 0.5f, 0.52f, 1f, 1f),
                saturation = 1.45f, temperature = 0.03f, grainAmount = 0.12f, grainSizeMm = 0.09f, vignette = 0.12f,
            ),
        )
        val Superia = FilmFilter(
            "superia", "Superia", "Fuji Superia",
            FilmRecipe(
                master = c(0f, 0.02f, 0.5f, 0.5f, 1f, 0.99f),
                red = c(0f, 0f, 0.5f, 0.47f, 1f, 0.97f), green = c(0f, 0.04f, 0.3f, 0.35f, 1f, 1f),
                blue = c(0f, 0.05f, 0.5f, 0.52f, 1f, 1f),
                saturation = 1.1f, temperature = -0.05f, tint = -0.1f, fade = 0.02f,
                grainAmount = 0.3f, grainSizeMm = 0.12f, vignette = 0.15f,
            ),
        )
        val Instant = FilmFilter(
            "instant", "Instant", "Polaroid",
            FilmRecipe(
                master = c(0f, 0.04f, 0.5f, 0.52f, 1f, 0.95f),
                red = c(0f, 0.04f, 0.5f, 0.45f, 1f, 0.94f), green = c(0f, 0.08f, 0.5f, 0.55f, 1f, 0.98f),
                blue = c(0f, 0.1f, 0.5f, 0.55f, 1f, 0.92f),
                saturation = 0.85f, contrast = 0.92f, fade = 0.07f,
                grainAmount = 0.15f, grainSizeMm = 0.14f, vignette = 0.3f, halation = 0.15f, halationColor = 0xFFB8FFE8.toInt(),
            ),
        )
        val Tungsten = FilmFilter(
            "tungsten", "Tungsten", "CineStill 800T",
            FilmRecipe(
                master = c(0f, 0f, 0.3f, 0.28f, 0.7f, 0.74f, 1f, 1f),
                red = c(0f, 0f, 0.5f, 0.45f, 1f, 0.97f), blue = c(0f, 0.08f, 0.3f, 0.38f, 0.6f, 0.6f, 1f, 0.92f),
                saturation = 1.1f, temperature = -0.25f,
                grainAmount = 0.4f, grainSizeMm = 0.14f, vignette = 0.2f, halation = 0.75f, halationColor = 0xFFFF3B2A.toInt(),
            ),
        )
        val Disposable = FilmFilter(
            "disposable", "Disposable", "Single-use camera",
            FilmRecipe(
                master = c(0f, 0f, 0.25f, 0.18f, 0.5f, 0.5f, 0.78f, 0.88f, 1f, 1f),
                green = c(0f, 0.01f, 0.5f, 0.52f, 1f, 1f),
                saturation = 1.12f, temperature = 0.05f, tint = -0.08f, contrast = 1.05f,
                grainAmount = 0.35f, grainSizeMm = 0.13f, vignette = 0.5f, halation = 0.1f, halationColor = 0xFFFFD08A.toInt(),
            ),
        )
        val Expired = FilmFilter(
            "expired", "Expired", "Out-of-date film",
            FilmRecipe(
                master = c(0f, 0.05f, 0.5f, 0.5f, 1f, 0.94f),
                red = c(0f, 0.06f, 0.5f, 0.58f, 1f, 1f), green = c(0f, 0.03f, 0.5f, 0.44f, 1f, 0.92f),
                blue = c(0f, 0.1f, 0.5f, 0.45f, 1f, 0.84f),
                saturation = 0.8f, temperature = 0.1f, tint = 0.15f, fade = 0.08f,
                grainAmount = 0.4f, grainSizeMm = 0.15f, vignette = 0.25f, lightLeak = 0.35f,
            ),
        )
        val TriX = FilmFilter(
            "trix", "Tri-X", "Kodak Tri-X 400",
            FilmRecipe(
                master = c(0f, 0f, 0.25f, 0.15f, 0.5f, 0.5f, 0.75f, 0.88f, 1f, 1f),
                mono = true, grainAmount = 0.85f, grainSizeMm = 0.17f, vignette = 0.3f,
            ),
        )
        val SoftBw = FilmFilter(
            "softbw", "Soft B&W", "Soft panchromatic",
            FilmRecipe(
                master = c(0f, 0.04f, 0.5f, 0.52f, 1f, 0.97f),
                mono = true, contrast = 0.95f, fade = 0.03f, grainAmount = 0.2f, grainSizeMm = 0.12f, vignette = 0.1f,
            ),
        )
        val Sepia = FilmFilter(
            "sepia", "Sepia", "Toned print",
            FilmRecipe(
                master = c(0f, 0.02f, 0.5f, 0.5f, 1f, 0.98f),
                red = c(0f, 0.08f, 0.5f, 0.6f, 1f, 1f), green = c(0f, 0.04f, 0.5f, 0.48f, 1f, 0.9f),
                blue = c(0f, 0f, 0.5f, 0.3f, 1f, 0.68f),
                mono = true, fade = 0.02f, grainAmount = 0.2f, grainSizeMm = 0.12f, vignette = 0.25f,
            ),
        )

        /** Selectable presets in display order, without [None]. */
        val Presets = listOf(Gold, Portrait, Vivid, Superia, Instant, Tungsten, Disposable, Expired, TriX, SoftBw, Sepia)
        val All = listOf(None) + Presets

        fun byId(id: String): FilmFilter = All.firstOrNull { it.id == id } ?: None
    }
}
