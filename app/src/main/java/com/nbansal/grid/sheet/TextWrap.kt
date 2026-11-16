package com.nbansal.grid.sheet

data class Wrapped(val lines: List<String>, val sizeMm: Float, val lineHeightMm: Float)

/** Greedy word wrap to [maxW]; words longer than a line are broken by character. */
fun wrapLines(text: String, maxW: Float, sizeMm: Float, m: TextMeasurer): List<String> {
    val out = mutableListOf<String>()
    for (para in text.split('\n')) {
        var line = ""
        fun flush() { if (line.isNotEmpty()) { out += line; line = "" } }
        for (word in para.split(' ').filter { it.isNotEmpty() }) {
            val tryLine = if (line.isEmpty()) word else "$line $word"
            if (m.width(tryLine, sizeMm) <= maxW) { line = tryLine; continue }
            flush()
            var rest = word
            while (m.width(rest, sizeMm) > maxW && rest.length > 1) {
                var n = rest.length - 1
                while (n > 1 && m.width(rest.take(n), sizeMm) > maxW) n--
                out += rest.take(n)
                rest = rest.drop(n)
            }
            line = rest
        }
        flush()
    }
    return out
}

/**
 * Wraps [text] into [maxW] x [maxH], shrinking from [baseSize] down to [minSize]; if it still does not fit
 * the last visible line is ellipsized.
 */
fun wrapToFit(
    text: String, maxW: Float, maxH: Float, baseSize: Float, minSize: Float, m: TextMeasurer, lineRatio: Float = 1.3f,
): Wrapped {
    var size = baseSize
    while (true) {
        val lines = wrapLines(text, maxW, size, m)
        val lh = size * lineRatio
        if (lines.size * lh <= maxH + 1e-4f) return Wrapped(lines, size, lh)
        if (size <= minSize + 1e-4f) {
            val fit = maxOf(1, (maxH / lh).toInt())
            val shown = lines.take(fit).toMutableList()
            if (lines.size > fit) {
                var last = shown.last()
                while (last.isNotEmpty() && m.width("$last$ELLIPSIS", size) > maxW) last = last.dropLast(1).trimEnd()
                shown[shown.size - 1] = "$last$ELLIPSIS"
            }
            return Wrapped(shown, size, lh)
        }
        size = maxOf(minSize, size * 0.92f)
    }
}
