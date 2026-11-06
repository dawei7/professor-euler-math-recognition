package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * complexplot(f): the compiled function against ball arithmetic at the same points (another evaluation altogether), its zeros
 * and poles against values known from textbooks or found by another method (Durand–Kerner for a quintic), the argument principle,
 * the default and asked-for squares, and the soft failures at poles and where f has no value.
 */
class ComplexPlotTest {
    private fun plot(text: String): ComplexPlot.Plot {
        val o = Reports.of(TextInput.read(text)).result.outcome
        return (o as? Outcome.DomainColoring)?.plot ?: throw AssertionError("$text: $o")
    }

    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    /** f(x + iy) in ball arithmetic: the expression with that number put in, its certified digits. */
    private fun precise(f: Expr, x: Double, y: Double): Pair<Double, Double> {
        val z = add(num(Rational.near(x, 1_000_000_000, 0.0) ?: error("x")), mul(num(Rational.near(y, 1_000_000_000, 0.0) ?: error("y")), Expr.I))
        val v = Domain.complex { Precise.digits(Calculus.tidy(f.substitute(Expr.Sym("z"), z)), 15) }
        fun d(x: Decimal?) = when (x) {
            null, Decimal.Zero -> 0.0
            is Decimal.Small -> 0.0
            is Decimal.Digits -> x.toString().toDouble()
        }
        return d(v.re) to d(v.im)
    }

    @Test
    fun theCompiledFunctionAgreesWithBallArithmetic() {
        val functions = listOf("z^3 - 2z + 1", "1/(z^2 + 1)", "e^z", "sin(z)", "cos(z)", "tan(z)", "sinh(z)", "cosh(z)", "tanh(z)",
            "e^(i z) + z^2/3", "(z - 1)^2/(z + 2)", "sqrt(z^2 + 4)", "ln(z^2 + 1)", "z^(-3) + 2")
        // Points with short fractions, so that each is the same number in both.
        val points = listOf(0.5 to 0.25, -1.25 to 0.75, 0.375 to -1.5, 1.75 to 1.125, -0.625 to -0.875)
        val wrong = ArrayList<String>()
        for (text in functions) {
            val f = Domain.complex { Calculus.tidy(Calculus.evaluate(Parser(MathCalc.TOKEN.findAll(TextInput.read(text).latex).map { it.value }.toList()).statements()[0].sides[0]!!)) }
            val ev = ComplexFunction.compile(f, "z").evaluator()
            for ((x, y) in points) {
                assertTrue(ev.at(x, y))
                val (re, im) = precise(f, x, y)
                val size = maxOf(1.0, hypot(re, im))
                if (abs(ev.re - re) > 1e-12 * size || abs(ev.im - im) > 1e-12 * size) wrong += "$text at $x + ${y}i: ${ev.re} + ${ev.im}i, not $re + ${im}i"
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun theInverseFunctionsUndoTheirFunctions() {
        // sin(arcsin z) = z, tan(arctan z) = z, cos(arccos z) = z at points off the branch cuts.
        for ((pair, expected) in listOf("sin(arcsin(z))" to 0, "tan(arctan(z))" to 0, "cos(arccos(z))" to 0)) {
            val f = Domain.complex { Calculus.evaluate(Parser(MathCalc.TOKEN.findAll(TextInput.read(pair).latex).map { it.value }.toList()).statements()[0].sides[0]!!) }
            val ev = ComplexFunction.compile(f, "z").evaluator()
            for ((x, y) in listOf(0.3 to 0.4, -0.7 to 0.2, 0.5 to -1.5)) {
                assertTrue(ev.at(x, y))
                assertEquals("$pair re", x, ev.re, 1e-12)
                assertEquals("$pair im", y, ev.im, 1e-12)
            }
        }
    }

    @Test
    fun exactZerosAndPoles() {
        // z³ − 8 = (z − 2)(z² + 2z + 4): 2 and −1 ± i√3.
        assertEquals("square 0 ± 3 zeros -1 - i√3; -1 + i√3; 2 poles none", answer("complexplot(z^3 - 8, 0, 3)"))
        // A double zero and a simple pole.
        assertEquals("square 0 ± 3 zeros 1 (2×) poles -2", answer("complexplot((z - 1)^2/(z + 2), z, 0, 3)"))
        assertEquals("square 0 ± 2 zeros none poles -i; i", answer("complexplot(1/(z^2 + 1))"))
        // A pole of order 3 and its own winding number −3.
        val p = plot("complexplot(1/z^3)")
        assertEquals(listOf(3), p.poles.map { it.order })
        assertEquals(-3, ComplexPlot.windings(p.compiled, 0.0, 0.0, 0.5))
        assertEquals(2, ComplexPlot.windings(plot("complexplot(z^2)").compiled, 0.0, 0.0, 1.0))
        // sin z = 0 at kπ: in ± 4, −π, 0 and π.
        assertEquals("square 0 ± 4 zeros -π; 0; π poles none", answer("complexplot(sin(z), 0, 4)"))
    }

    @Test
    fun zerosFoundNumericallyAgreeWithDurandKerner() {
        // z⁵ − z − 1 has no roots in radicals; Durand–Kerner finds all five (another method).
        val coefficients = doubleArrayOf(-1.0, -1.0, 0.0, 0.0, 0.0, 1.0)
        var roots = Array(5) { k -> Pair(0.4 * Math.cos(2 * PI * k / 5 + 0.4), 0.9 * Math.sin(2 * PI * k / 5 + 0.4)) }
        fun eval(x: Double, y: Double): Pair<Double, Double> {
            var re = 0.0
            var im = 0.0
            for (c in coefficients.reversed()) {
                val r = re * x - im * y + c
                im = re * y + im * x
                re = r
            }
            return re to im
        }
        repeat(500) {
            roots = Array(5) { i ->
                val (x, y) = roots[i]
                var dr = 1.0
                var di = 0.0
                for (j in 0 until 5) if (j != i) {
                    val ar = x - roots[j].first
                    val ai = y - roots[j].second
                    val t = dr * ar - di * ai
                    di = dr * ai + di * ar
                    dr = t
                }
                val (pr, pi) = eval(x, y)
                val d2 = dr * dr + di * di
                Pair(x - (pr * dr + pi * di) / d2, y - (pi * dr - pr * di) / d2)
            }
        }
        val p = plot("complexplot(z^5 - z - 1)")
        assertEquals(5, p.zeros.size)
        for (z in p.zeros) {
            assertFalse(z.exact)
            val tolerance = 1.5 * Math.pow(10.0, -z.decimals!!.toDouble())
            assertTrue("$z", roots.any { (x, y) -> abs(x - z.re) <= tolerance && abs(y - z.im) <= tolerance })
        }
        assertEquals("≈1.16730398", "≈" + ComplexPlot.text(p.zeros.last()))
    }

    @Test
    fun polesOfTangentAndZerosOfTheExponential() {
        val tan = plot("complexplot(tan(z), 0, 4)")
        assertEquals(listOf(-PI, 0.0, PI), tan.zeros.map { it.re })
        assertEquals(2, tan.poles.size)
        for (p in tan.poles) assertEquals(PI / 2, abs(p.re), Math.pow(10.0, -p.decimals!!.toDouble()))
        // e^z = 1 at 2πik.
        val e = plot("complexplot(e^z - 1, 0, 7)")
        assertEquals(3, e.zeros.size)
        assertEquals(2 * PI, e.zeros.last().im, Math.pow(10.0, -e.zeros.last().decimals!!.toDouble()))
    }

    @Test
    fun theSquare() {
        // The default is ± 2, widened to hold the exact zeros (±3 needs ± 5).
        val p = plot("complexplot(z^2 - 9)")
        assertEquals(5.0, p.region.half, 0.0)
        assertEquals(2.0, plot("complexplot(z^2 + 1)").region.half, 0.0)
        val q = plot("complexplot(1/z, 1 + i, 2)")
        assertEquals(1.0, q.region.centerRe, 0.0)
        assertEquals(1.0, q.region.centerIm, 0.0)
        assertEquals(2.0, q.region.half, 0.0)
        assertTrue(q.region.given)
        // Re z and Im z each run over [−1, 3]: the pole 0 is inside.
        assertEquals(1, q.poles.size)
    }

    @Test
    fun evaluatedSoftlyWhereThereIsNoValue() {
        val ev = plot("complexplot(1/z)").compiled.evaluator()
        assertFalse(ev.at(0.0, 0.0))
        assertTrue(ev.infinite)
        val ln = plot("complexplot(ln(z))").compiled.evaluator()
        assertFalse(ln.at(0.0, 0.0))
        assertTrue(ln.infinite)
        // e^(1/z) at 0, its essential singularity, is e^∞: no value, not ∞.
        val nan = plot("complexplot(e^(1/z))").compiled.evaluator()
        assertFalse(nan.at(0.0, 0.0))
        assertFalse(nan.infinite)
        assertTrue(nan.at(1.0, 0.0))
        assertEquals(Math.E, nan.re, 1e-15)
    }

    @Test
    fun aGridAsTheAppDrawsIt() {
        val p = plot("complexplot(z^2)")
        val n = 4
        val out = DoubleArray(2 * n * n)
        p.compiled.grid(p.region, n, out)
        // The first pixel's centre: −2 + 0.5 and 2 − 0.5 (half a pixel of 1 in).
        assertEquals((-1.5) * (-1.5) - 1.5 * 1.5, out[0], 1e-12)
        assertEquals(2 * (-1.5) * 1.5, out[1], 1e-12)
        // 400 × 400 points of a function with several parts: well within a frame or two of drawing.
        val big = plot("complexplot(sin(z)/(z^2 + 1) + e^(z/3))")
        val many = DoubleArray(2 * 400 * 400)
        val start = System.nanoTime()
        big.compiled.grid(big.region, 400, many)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2000)
    }

    @Test
    fun whatIsRefused() {
        assertEquals("FAILED NO_COMPLEX_VALUES", answer("complexplot(floor(z))"))
        assertEquals("FAILED SEVERAL_UNKNOWNS", answer("complexplot(x y)"))
        assertEquals("FAILED ARGUMENTS", answer("complexplot(z^2, 0, -1)"))
        assertEquals("FAILED ARGUMENTS", answer("complexplot(z^2, 1, 2, 3, 4)"))
        // A function of z typed alone offers its domain colouring.
        assertTrue(Reports.of(TextInput.read("z^2 + 1")).panels.any { it.choice?.mode == Mode.COMPLEX_PLOT })
    }
}
