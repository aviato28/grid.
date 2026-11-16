package com.nbansal.grid.filter

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * CPU film filter on ARGB_8888 pixels. Pure Kotlin (no android imports) so it runs in JVM tests.
 * Deterministic: grain and light-leak position come only from [seed]; all spatial sizes are specified in
 * millimetres and converted with [pxPerMm], so a preview and a 300 DPI render look alike.
 */
object FilmProcessor {
    private const val DEFAULT_GRAIN = 0.3f
    private const val DEFAULT_VIGNETTE = 0.3f
    private const val DEFAULT_LEAK = 0.55f
    private const val HALATION_RADIUS_MM = 1.8f

    fun apply(px: IntArray, w: Int, h: Int, s: FilterSettings, seed: Long, pxPerMm: Float) {
        if (s.isIdentity || w <= 0 || h <= 0) return
        val r = s.filter.recipe
        val k = s.intensity.coerceIn(0f, 1f)

        colour(px, r, k, s)
        if (r.halation > 0f && k > 0f) halation(px, w, h, r, k, pxPerMm)
        if (s.lightLeak) lightLeak(px, w, h, if (r.lightLeak > 0f) r.lightLeak else DEFAULT_LEAK, seed)
        if (s.vignette) vignette(px, w, h, if (r.vignette > 0f) r.vignette else DEFAULT_VIGNETTE)
        if (s.grain) grain(px, w, h, if (r.grainAmount > 0f) r.grainAmount else DEFAULT_GRAIN, r.grainSizeMm, seed, pxPerMm)
    }

    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
    private fun to255(v: Float) = (clamp01(v) * 255f + 0.5f).toInt()

    /** Grade (film curves, saturation, mono) mixed by [k] with the original, then user adjustments. */
    private fun colour(px: IntArray, r: FilmRecipe, k: Float, s: FilterSettings) {
        val master = r.master.lut()
        fun channelLut(c: ToneCurve, offset: Float): IntArray {
            val cl = c.lut()
            return IntArray(256) { i ->
                val x = clamp01((i / 255f - 0.5f) * r.contrast + 0.5f + offset)
                val v = cl[(master[(x * 255f + 0.5f).toInt()] * 255f + 0.5f).toInt()]
                to255(r.fade + v * (1f - r.fade))
            }
        }
        // Temperature warms red / cools blue; tint pushes green against magenta.
        val lutR = channelLut(r.red, r.temperature * 0.07f + r.tint * 0.02f)
        val lutG = channelLut(r.green, -r.tint * 0.06f)
        val lutB = channelLut(r.blue, -r.temperature * 0.09f + r.tint * 0.02f)

        // User adjustments (not scaled by intensity).
        val cf = 1f + s.contrast.coerceIn(-1f, 1f) * 0.6f
        val bo = s.brightness.coerceIn(-1f, 1f) * 0.35f
        val wo = s.warmth.coerceIn(-1f, 1f) * 0.1f
        fun adjLut(off: Float) = IntArray(256) { i -> to255((i / 255f - 0.5f) * cf + 0.5f + bo + off) }
        val adjR = adjLut(wo)
        val adjG = adjLut(0f)
        val adjB = adjLut(-wo)

        val sat = r.saturation
        val mono = r.mono
        val grade = !r.isNeutral && k > 0f
        val ik = (k * 256f).toInt()
        for (i in px.indices) {
            val p = px[i]
            var rr = (p shr 16) and 0xFF
            var gg = (p shr 8) and 0xFF
            var bb = p and 0xFF
            if (grade) {
                var fr = rr
                var fg = gg
                var fb = bb
                val l = (fr * 77 + fg * 151 + fb * 28) shr 8
                if (mono) {
                    fr = l; fg = l; fb = l
                } else if (sat != 1f) {
                    fr = (l + (fr - l) * sat).toInt().coerceIn(0, 255)
                    fg = (l + (fg - l) * sat).toInt().coerceIn(0, 255)
                    fb = (l + (fb - l) * sat).toInt().coerceIn(0, 255)
                }
                fr = lutR[fr]; fg = lutG[fg]; fb = lutB[fb]
                rr += ((fr - rr) * ik) shr 8
                gg += ((fg - gg) * ik) shr 8
                bb += ((fb - bb) * ik) shr 8
            }
            px[i] = (p and 0xFF000000.toInt()) or (adjR[rr] shl 16) or (adjG[gg] shl 8) or adjB[bb]
        }
    }

    private fun screen(base: Int, add: Float): Int = (255f - (255f - base) * (1f - add.coerceIn(0f, 1f)) + 0.5f).toInt().coerceIn(0, 255)

    private fun halation(px: IntArray, w: Int, h: Int, r: FilmRecipe, k: Float, pxPerMm: Float) {
        val radiusPx = HALATION_RADIUS_MM * pxPerMm
        val ds = max(1, (radiusPx / 2f).roundToInt())
        val sw = (w + ds - 1) / ds
        val sh = (h + ds - 1) / ds
        var mask = FloatArray(sw * sh)
        val cnt = IntArray(sw * sh)
        for (y in 0 until h) {
            val row = (y / ds) * sw
            for (x in 0 until w) {
                val p = px[y * w + x]
                val l = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 151 + (p and 0xFF) * 28) / (256f * 255f)
                val m = clamp01((l - 0.72f) / 0.28f)
                mask[row + x / ds] += m * m
                cnt[row + x / ds]++
            }
        }
        for (i in mask.indices) if (cnt[i] > 0) mask[i] /= cnt[i]
        val br = max(1, (radiusPx / ds).roundToInt())
        repeat(2) { mask = boxBlur(mask, sw, sh, br) }
        val hc = r.halationColor
        val hr = ((hc shr 16) and 0xFF) / 255f
        val hg = ((hc shr 8) and 0xFF) / 255f
        val hb = (hc and 0xFF) / 255f
        val amount = r.halation * k * 1.6f
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / ds - 0.5f).coerceIn(0f, sh - 1f)
            val y0 = floor(fy).toInt()
            val y1 = min(sh - 1, y0 + 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / ds - 0.5f).coerceIn(0f, sw - 1f)
                val x0 = floor(fx).toInt()
                val x1 = min(sw - 1, x0 + 1)
                val tx = fx - x0
                val a = mask[y0 * sw + x0] * (1 - tx) + mask[y0 * sw + x1] * tx
                val b = mask[y1 * sw + x0] * (1 - tx) + mask[y1 * sw + x1] * tx
                val m = (a * (1 - ty) + b * ty) * amount
                if (m < 0.003f) continue
                val p = px[y * w + x]
                px[y * w + x] = (p and 0xFF000000.toInt()) or
                    (screen((p shr 16) and 0xFF, hr * m) shl 16) or
                    (screen((p shr 8) and 0xFF, hg * m) shl 8) or
                    screen(p and 0xFF, hb * m)
            }
        }
    }

    private fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val norm = 1f / (2 * r + 1)
        for (y in 0 until h) {
            var acc = 0f
            for (i in -r..r) acc += src[y * w + i.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = acc * norm
                acc += src[y * w + min(w - 1, x + r + 1)] - src[y * w + max(0, x - r)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (i in -r..r) acc += tmp[i.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc * norm
                acc += tmp[min(h - 1, y + r + 1) * w + x] - tmp[max(0, y - r) * w + x]
            }
        }
        return out
    }

    private fun lightLeak(px: IntArray, w: Int, h: Int, strength: Float, seed: Long) {
        val hs = hash(seed.toInt(), (seed shr 32).toInt(), 77)
        val side = (hs ushr 3) and 3
        val along = ((hs ushr 8) and 0xFFFF) / 65535f * 0.6f + 0.2f
        val cx = when (side) { 0 -> 0f; 1 -> w.toFloat(); else -> along * w }
        val cy = when (side) { 2 -> 0f; 3 -> h.toFloat(); else -> along * h }
        val palette = arrayOf(floatArrayOf(1f, 0.45f, 0.12f), floatArrayOf(1f, 0.22f, 0.2f), floatArrayOf(1f, 0.7f, 0.25f))
        val col = palette[((hs ushr 24) and 0xFF) % palette.size]
        val radius = 0.62f * max(w, h)
        val r2 = radius * radius
        for (y in 0 until h) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val d2 = dx * dx + dy * dy
                if (d2 >= r2) continue
                val t = 1f - sqrt(d2) / radius
                val a = t * t * strength
                val p = px[y * w + x]
                px[y * w + x] = (p and 0xFF000000.toInt()) or
                    (screen((p shr 16) and 0xFF, col[0] * a) shl 16) or
                    (screen((p shr 8) and 0xFF, col[1] * a) shl 8) or
                    screen(p and 0xFF, col[2] * a)
            }
        }
    }

    private fun vignette(px: IntArray, w: Int, h: Int, strength: Float) {
        val cx = (w - 1) / 2f
        val cy = (h - 1) / 2f
        val inv = 1f / sqrt(cx * cx + cy * cy)
        for (y in 0 until h) {
            val dy = (y - cy)
            for (x in 0 until w) {
                val dx = x - cx
                val d = sqrt(dx * dx + dy * dy) * inv
                val t = max(0f, d - 0.3f) / 0.7f
                val m = 1f - strength * 0.85f * t * t
                val p = px[y * w + x]
                val rr = (((p shr 16) and 0xFF) * m).toInt()
                val gg = (((p shr 8) and 0xFF) * m).toInt()
                val bb = ((p and 0xFF) * m).toInt()
                px[y * w + x] = (p and 0xFF000000.toInt()) or (rr shl 16) or (gg shl 8) or bb
            }
        }
    }

    private fun grain(px: IntArray, w: Int, h: Int, amount: Float, sizeMm: Float, seed: Long, pxPerMm: Float) {
        val cell = max(0.75f, sizeMm * pxPerMm)
        val s1 = seed.toInt()
        val s2 = (seed shr 32).toInt() xor 0x5bd1e995
        val amp = amount * 0.24f * 255f
        for (y in 0 until h) {
            val cy = floor(y / cell).toInt()
            for (x in 0 until w) {
                val cx = floor(x / cell).toInt()
                val hh = hash(cx, cy, s1 xor s2)
                val n = (((hh and 0xFFFF) + ((hh ushr 16) and 0xFFFF)) / 65535f) - 1f // triangular, -1..1
                val p = px[y * w + x]
                val rr = (p shr 16) and 0xFF
                val gg = (p shr 8) and 0xFF
                val bb = p and 0xFF
                val l = (rr * 77 + gg * 151 + bb * 28) / (256f * 255f)
                val wgt = 0.35f + 0.65f * (1f - abs(2f * l - 1f))
                val d = n * amp * wgt
                px[y * w + x] = (p and 0xFF000000.toInt()) or
                    ((rr + d).toInt().coerceIn(0, 255) shl 16) or
                    ((gg + d).toInt().coerceIn(0, 255) shl 8) or
                    (bb + d).toInt().coerceIn(0, 255)
            }
        }
    }

    private fun hash(x: Int, y: Int, s: Int): Int {
        var h = x * 374761393 + y * 668265263 + s * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        h *= 0x2c1b3c6d
        return h xor (h ushr 12)
    }
}
