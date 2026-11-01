package com.professoreuler.mathreader

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** An ink as the model sees it: [height] rows of [width] pixels, ink 1 on 0. */
class InkImage(val width: Int, val height: Int, val pixels: FloatArray)

/**
 * Draws an ink exactly as `recognizer/data.py`'s `render` does when reading, so the model sees the pixels it was
 * trained on. The scale comes from the writing: the median size of a stroke is drawn [SYMBOL_PX] high. Each pixel's
 * ink is how far its centre lies within a line of the pen's width with round ends.
 */
object InkRenderer {
    const val SYMBOL_PX = 28.0
    const val MAX_H = 256
    const val MAX_W = 1280
    const val MARGIN = 6
    const val PEN = 2.4

    /** [strokes] as x0, y0, x1, y1, … with y downwards, in any unit. */
    fun render(strokes: List<DoubleArray>): InkImage {
        require(strokes.isNotEmpty() && strokes.all { it.size >= 2 && it.size % 2 == 0 }) { "no ink" }
        var lowX = Double.POSITIVE_INFINITY
        var lowY = Double.POSITIVE_INFINITY
        var highX = Double.NEGATIVE_INFINITY
        var highY = Double.NEGATIVE_INFINITY
        for (s in strokes) {
            for (k in s.indices step 2) {
                lowX = min(lowX, s[k]); highX = max(highX, s[k])
                lowY = min(lowY, s[k + 1]); highY = max(highY, s[k + 1])
            }
        }
        val extentX = max(highX - lowX, 1e-6)
        val extentY = max(highY - lowY, 1e-6)
        var scale = SYMBOL_PX / max(symbolSize(strokes), 1e-6)
        scale = minOf(scale, (MAX_W - 2 * MARGIN) / extentX, (MAX_H - 2 * MARGIN) / extentY)
        val w = ceil(extentX * scale).toInt() + 2 * MARGIN
        val h = max(ceil(extentY * scale).toInt() + 2 * MARGIN, 32)
        val offsetX = MARGIN.toDouble()
        val offsetY = (h - extentY * scale) / 2
        val image = DoubleArray(w * h)
        for (s in strokes) {
            val pts = DoubleArray(s.size) { k -> if (k % 2 == 0) (s[k] - lowX) * scale + offsetX else (s[k] - lowY) * scale + offsetY }
            draw(image, w, h, pts, PEN / 2)
        }
        return InkImage(w, h, FloatArray(w * h) { image[it].toFloat() })
    }

    /** The median of the strokes' larger side, among strokes of more than one point. */
    fun symbolSize(strokes: List<DoubleArray>): Double {
        val sizes = strokes.filter { it.size > 2 }.map { s ->
            var x0 = Double.POSITIVE_INFINITY; var x1 = Double.NEGATIVE_INFINITY
            var y0 = Double.POSITIVE_INFINITY; var y1 = Double.NEGATIVE_INFINITY
            for (k in s.indices step 2) {
                x0 = min(x0, s[k]); x1 = max(x1, s[k]); y0 = min(y0, s[k + 1]); y1 = max(y1, s[k + 1])
            }
            max(x1 - x0, y1 - y0)
        }.sorted()
        if (sizes.isEmpty()) return 1.0
        val n = sizes.size
        return if (n % 2 == 1) sizes[n / 2] else (sizes[n / 2 - 1] + sizes[n / 2]) / 2
    }

    private fun draw(image: DoubleArray, w: Int, h: Int, pts: DoubleArray, r: Double) {
        val n = pts.size / 2
        for (k in 0 until max(n - 1, 1)) {
            val ax = pts[2 * k]
            val ay = pts[2 * k + 1]
            val bx = if (n > 1) pts[2 * k + 2] else ax
            val by = if (n > 1) pts[2 * k + 3] else ay
            val dx = bx - ax
            val dy = by - ay
            val length = dx * dx + dy * dy
            val x0 = max(floor(min(ax, bx) - r - 1).toInt(), 0)
            val x1 = min(ceil(max(ax, bx) + r + 1).toInt(), w - 1)
            val y0 = max(floor(min(ay, by) - r - 1).toInt(), 0)
            val y1 = min(ceil(max(ay, by) + r + 1).toInt(), h - 1)
            for (y in y0..y1) {
                val py = y + 0.5
                for (x in x0..x1) {
                    val px = x + 0.5
                    val t = if (length > 0) min(max(((px - ax) * dx + (py - ay) * dy) / length, 0.0), 1.0) else 0.0
                    val ex = px - (ax + t * dx)
                    val ey = py - (ay + t * dy)
                    val c = min(r + 0.5 - sqrt(ex * ex + ey * ey), 1.0)
                    if (c > image[y * w + x]) image[y * w + x] = c
                }
            }
        }
    }
}
