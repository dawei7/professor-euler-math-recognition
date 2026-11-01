package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Graphs: windows that show what matters, exact marks, no line across a pole, closed implicit curves, and speed. */
class PlotTest {

    private fun graph(latex: String, mode: Mode? = Mode.GRAPH): Outcome.Graph =
        MathCalc.calculate(latex, mode).outcome as? Outcome.Graph ?: error("$latex is no graph: ${MathCalc.calculate(latex, mode).outcome}")

    private fun marks(p: PreparedPlot, kind: PlotMark.Kind) = p.marks.filter { it.kind == kind }.sortedBy { it.x }

    private fun text(e: Expr?) = e?.let { MathCalc.calculate("1").text(it) }

    @Test
    fun aParabolasZerosAndVertex() {
        val p = Plot.prepare(graph("f(x)=x^{2}-4"))
        assertEquals(listOf("-2", "2"), marks(p, PlotMark.Kind.ZERO).map { text(it.exactX) })
        val min = marks(p, PlotMark.Kind.MINIMUM).single()
        assertEquals("0" to "-4", text(min.exactX) to text(min.exactY))
        val w = p.window
        assertTrue("$w shows the zeros", w.xMin < -2 && w.xMax > 2)
        assertTrue("$w shows the vertex and the axis", w.yMin < -4 && w.yMax > 0)
        assertEquals(-4.0, marks(p, PlotMark.Kind.Y_INTERCEPT).single().y, 1e-12)
    }

    @Test
    fun sineOverTwoPeriodsWithExactMarks() {
        val p = Plot.prepare(graph("f(x)=\\sin(x)"))
        assertTrue(p.trigonometric)
        assertTrue("${p.window}", p.window.xMin <= -2 * PI + 0.2 && p.window.xMax >= 2 * PI - 0.2)
        val zeros = marks(p, PlotMark.Kind.ZERO).filter { it.x in p.window.xMin..p.window.xMax }.map { text(it.exactX) }
        assertEquals(listOf("-2π", "-π", "0", "π", "2π"), zeros)
        val max = marks(p, PlotMark.Kind.MAXIMUM).first { abs(it.x - PI / 2) < 1e-9 }
        assertEquals("π/2" to "1", text(max.exactX) to text(max.exactY))
    }

    @Test
    fun noLineAcrossThePoleOfOneOverX() {
        val p = Plot.prepare(graph("f(x)=\\frac{1}{x}"))
        assertEquals(listOf(0.0), p.asymptotes)
        val s = p.sample(p.window, 800)
        for (piece in s.curves.single().pieces) {
            val xs = (0 until piece.size / 2).map { piece[2 * it] }
            assertTrue("a piece crosses 0: ${xs.first()}..${xs.last()}", xs.all { it < 0 } || xs.all { it > 0 })
        }
        assertTrue(s.poles.any { abs(it) < 1e-6 })
        // The pieces run up to the edge of the window near the pole.
        val ys = s.curves.single().pieces.flatMap { piece -> (0 until piece.size / 2).map { piece[2 * it + 1] } }
        assertTrue(ys.max() > p.window.yMax && ys.min() < p.window.yMin)
    }

    @Test
    fun noLineAcrossThePolesOfTan() {
        val p = Plot.prepare(graph("f(x)=\\tan(x)"))
        val poles = (-3..3).map { PI / 2 + it * PI }
        val s = p.sample(p.window, 800)
        for (piece in s.curves.single().pieces) {
            val a = piece[0]
            val b = piece[piece.size - 2]
            assertTrue("a piece from $a to $b crosses a pole", poles.none { it > a + 1e-9 && it < b - 1e-9 })
        }
        assertTrue(s.poles.size >= 2)
        assertTrue(p.asymptotes.any { abs(it - PI / 2) < 1e-9 })
    }

    @Test
    fun aLogarithmsZeroAndAsymptote() {
        val p = Plot.prepare(graph("f(x)=\\ln(x)"))
        assertEquals("1", text(marks(p, PlotMark.Kind.ZERO).single().exactX))
        assertTrue(p.asymptotes.contains(0.0))
        val s = p.sample(p.window, 400)
        assertTrue(s.curves.single().pieces.all { piece -> (0 until piece.size / 2).all { piece[2 * it] > 0 } })
    }

    @Test
    fun anExponentialCrossesTheAxisAtOne() {
        val p = Plot.prepare(graph("f(x)=e^{x}"))
        assertTrue(marks(p, PlotMark.Kind.ZERO).isEmpty())
        val y = marks(p, PlotMark.Kind.Y_INTERCEPT).single()
        assertEquals("1", text(y.exactY))
        assertTrue(p.window.xMin < 0 && p.window.xMax > 0)
    }

    @Test
    fun bothSidesOfAnEquationMeetAtItsSolutions() {
        val p = Plot.prepare(graph("x^{2}=x+2"))
        assertEquals(2, p.curves)
        assertEquals(listOf("-1", "2"), marks(p, PlotMark.Kind.INTERSECTION).map { text(it.exactX) })
        assertEquals(listOf(1.0, 4.0), marks(p, PlotMark.Kind.INTERSECTION).map { it.y })
    }

    @Test
    fun anImplicitCircleCloses() {
        val p = Plot.prepare(graph("x^{2}+y^{2}=4"))
        assertTrue(p.implicit)
        val w = p.window
        assertTrue("$w holds the circle", w.xMin < -2 && w.xMax > 2 && w.yMin < -2 && w.yMax > 2)
        val segments = p.sample(w, 800).curves.single().pieces
        assertTrue(segments.size > 50)
        for (s in segments) for (k in 0..1) {
            val x = s[2 * k]
            val y = s[2 * k + 1]
            assertEquals(4.0, x * x + y * y, 0.05)
        }
        // Every end meets another segment's end: no gap.
        val ends = segments.flatMap { listOf(it[0] to it[1], it[2] to it[3]) }
        for ((x, y) in ends) {
            val meeting = ends.count { (u, v) -> abs(u - x) < 1e-9 && abs(v - y) < 1e-9 }
            assertTrue("a loose end at ($x, $y)", meeting >= 2)
        }
    }

    @Test
    fun aHyperbolaHasTwoBranches() {
        val p = Plot.prepare(graph("xy=1"))
        val segments = p.sample(p.window, 800).curves.single().pieces
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.all { s -> s[0] * s[1] > 0.9 && s[2] * s[3] > 0.9 })
    }

    @Test
    fun samplingIsQuick() {
        val graphs = listOf("f(x)=x^{3}-3x+1", "f(x)=\\sin(2x)+\\cos(x)", "f(x)=\\frac{x^{2}-1}{x^{2}-4}", "f(x)=e^{-x^{2}}", "f(x)=\\tan(x)")
            .map { Plot.prepare(graph(it)) }
        graphs.forEach { it.sample(it.window, 800) }
        val start = System.nanoTime()
        val rounds = 20
        repeat(rounds) { graphs.forEach { it.sample(it.window.zoomed(1.3, 0.1, 0.0), 800) } }
        val each = (System.nanoTime() - start) / 1e6 / (rounds * graphs.size)
        assertTrue("$each ms a sample", each < 50)
    }

    @Test
    fun windowsZoomAndPan() {
        val w = PlotWindow(-10.0, 10.0, -5.0, 5.0)
        val z = w.zoomed(2.0, 0.0, 0.0)
        assertEquals(PlotWindow(-5.0, 5.0, -2.5, 2.5), z)
        assertEquals(PlotWindow(-9.0, 11.0, -5.0, 5.0), w.panned(1.0, 0.0))
        val e = PlotWindow(-1.0, 1.0, -1.0, 1.0).equalScale(800, 400)
        assertEquals(e.width / 800, e.height / 400, 1e-12)
    }
}
