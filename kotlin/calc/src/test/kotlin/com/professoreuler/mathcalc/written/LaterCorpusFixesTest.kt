package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The wrong answers the corpus of the later areas found (7 November 2026), each with its right answer worked out here without the
 * engine.
 */
class LaterCorpusFixesTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    /**
     * A root of a fraction beyond a double's range has a value: interestrate(6256, 8227, 36, 4) is 4((8227/6256)^(1/144) − 1),
     * which the canonical form keeps as the 144th root of a number of over 400 digits, and was refused as undefined.
     */
    @Test
    fun rootsOfLargeNumbersHaveAValue() {
        for ((p, f, n, m) in listOf(listOf(6256, 8227, 36, 4), listOf(3302, 14379, 38, 12), listOf(4092, 13513, 17, 12))) {
            val want = m * ((f.toDouble() / p).pow(1.0 / (m * n)) - 1)
            val c = calc("interestrate($p, $f, $n, $m)")
            val o = c.outcome as? Outcome.Value ?: error("interestrate($p, $f, $n, $m): ${answerOf(c)}")
            val got = Numeric.value(o.exact!!)!!
            assertTrue("$got, not $want", abs(got - want) <= 1e-12 * want)
        }
        // A whole number of 1,001 digits to the power 1/1000 is 10 (Numeric's value of it was ∞).
        assertEquals(10.0, Numeric.eval(Expr.Pow(Expr.Num(Rational.of(java.math.BigInteger.TEN.pow(1000))), Expr.Num(Rational.of(1) / Rational.of(1000))), emptyMap()), 1e-12)
    }

    /**
     * Gauss–Seidel on a 4 × 4 system took over three seconds (the exact eigenvalues of its iteration matrix, which the certified
     * radius does not need) and was stopped; its solution is the one elimination gives, −649/647, 447/1294, 3979/2588, −2525/2588
     * (checked here by multiplying back).
     */
    @Test
    fun gaussSeidelOfFourUnknownsIsQuick() {
        val a = listOf(listOf(-10, -1, -3, -3), listOf(2, 7, 3, -1), listOf(-1, 1, -8, -2), listOf(-3, -2, -2, -10))
        val b = listOf(8, 6, -9, 9)
        val x = listOf(Rational.of(-649) / Rational.of(647), Rational.of(447) / Rational.of(1294), Rational.of(3979) / Rational.of(2588),
            Rational.of(-2525) / Rational.of(2588))
        for (i in 0..3) assertEquals(Rational.of(b[i].toLong()), (0..3).fold(Rational.ZERO) { s, j -> s + Rational.of(a[i][j].toLong()) * x[j] })
        val start = System.nanoTime()
        val o = calc("gaussseidel([[-10, -1, -3, -3], [2, 7, 3, -1], [-1, 1, -8, -2], [-3, -2, -2, -10]], [8, 6, -9, 9])").outcome as Outcome.Iterated
        assertTrue("took ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 2_000_000_000L)
        assertEquals(x.map { num(it) }, o.iteration.exact)
    }

    /**
     * Fixed-point iteration certifies the value it converged to: x = e^(−x)/2 at W(1/2) = 0.351733711249… (the Lambert W function,
     * x·eˣ = 1/2), from 2 as from 1; from 2 it had no result.
     */
    @Test
    fun aFixedPointIsCertifiedFromEveryStart() {
        for (start in listOf(1, 2, 3)) {
            val it = (calc("fixedpoint(e^(-x)/2, $start)").outcome as Outcome.Iterated).iteration
            val v = it.result!!.single()
            assertTrue("from $start: $v", abs(v - 0.35173371124919582) < 1e-12)
        }
    }

    private fun points(l: List<ComplexPlot.Point>) = l.map { Triple(Math.round(it.re * 1e6) / 1e6, Math.round(it.im * 1e6) / 1e6, it.order) }.toSet()

    /**
     * A complex plot finds the poles off the real axis (it gave "poles none" for (z − 1/2)/((z − (1 + i))(z + 2 + 2i)) and for
     * z²/(z − (3 + 2i))²: Newton's method on 1/f jumped away from them, f′'s formula losing its digits there), lists every zero
     * and pole the argument principle counts inside the square, or declines; and residues are not "none" where the poles'
     * places are not found (z − (1 + i)), nor one of two (z² − 2i).
     */
    @Test
    fun complexPolesAreFound() {
        fun plot(text: String) = (calc(text).outcome as Outcome.DomainColoring).plot
        val a = plot("complexplot((z - 1/2)/((z - (1 + i))(z + 2 + 2i)), z, 0, 4)")
        assertEquals(setOf(Triple(0.5, 0.0, 1)), points(a.zeros))
        assertEquals(setOf(Triple(1.0, 1.0, 1), Triple(-2.0, -2.0, 1)), points(a.poles))
        assertEquals(setOf(Triple(3.0, 2.0, 2)), points(plot("complexplot(z^2/((z - (3 + 2i))^2), z, 0, 4)").poles))
        assertEquals(setOf(Triple(1.0, 1.0, 1)), points(plot("complexplot(1/(z - (1 + i)), z, 0, 4)").poles))
        // Double zeros and double poles together, within the time (its zeros' orders took over six seconds).
        val start = System.nanoTime()
        val b = plot("complexplot((z + 1)^2 z^2 (z + 3)^2/((z - 2i)^2 (z + 0.5 - 1.5i)^2), z, 0, 4)")
        assertTrue(System.nanoTime() - start < 2_500_000_000L)
        assertEquals(setOf(Triple(-3.0, 0.0, 2), Triple(-1.0, 0.0, 2), Triple(0.0, 0.0, 2)), points(b.zeros))
        assertEquals(setOf(Triple(0.0, 2.0, 2), Triple(-0.5, 1.5, 2)), points(b.poles))
        // z − (1 + i)'s zero is found now (solve with i); a base whose zeros are not all found is declined (allZeros).
        assertEquals("at 1 + i order 1 residue 1", answerOf(calc("residues(1/(z - (1 + i)))")))
        assertEquals("at -i order 1 residue i/2; at i order 1 residue -i/2", answerOf(calc("residues(1/(z^2 + 1))")))
    }

    /**
     * Quadratics with i in their coefficients, solved by hand: z² = 2i has the roots ±(1 + i) (both complex: the real formula
     * listed −√2·√i as a real solution and lost the other), z² + 2iz = z(z + 2i) has the real root 0 (said to have none), and
     * z² − (1 + i)z + i = (z − 1)(z − i) the real root 1 (rejected: its residual has no real value).
     */
    @Test
    fun quadraticsWithI() {
        assertEquals("z: none (complex -1 - i; 1 + i)", answerOf(calc("solve(z^2 - 2i = 0, z)")))
        assertEquals("z = 0", answerOf(calc("solve(z^2 + 2i z = 0, z)")))
        assertEquals("z = 1", answerOf(calc("solve(z^2 - (1 + i)z + i = 0, z)")))
        assertEquals("x = 2", answerOf(calc("solve(i x = 2i, x)")))
        // In the complex numbers the poles of 1/(z² − 2i) are both roots.
        assertEquals("at -1 - i order 1 residue -1/4 + i/4; at 1 + i order 1 residue 1/4 - i/4", answerOf(calc("residues(1/(z^2 - 2i))")))
        for ((re, im) in listOf(-1.0 to -1.0, 1.0 to 1.0)) {
            val z = Cx(re, im)
            assertTrue(((z * z) - Cx(0.0, 2.0)).abs < 1e-15)
        }
    }

    /**
     * The decimal of a line integral is its exact form's certified one: ∫ (y² − 4)|r′| dt along (t² − 2t + 2, −t − 1) from −1 to 0
     * is −11.7726470944204 (mpmath at 30 digits; the same as integral((t^2 + 2t - 3) sqrt(4t^2 - 8t + 5), t, -1, 0)), and was
     * shown as −11.77264711, its antiderivative's double, whose terms cancel.
     */
    @Test
    fun aLineIntegralsDigitsAreCertified() {
        assertEquals("≈ -11.77264709", answerOf(calc("lineintegral(y^2 - 4, (t^2 - 2t + 2, -t - 1), t, -1, 0)")))
        assertEquals("≈ -11.77264709", answerOf(calc("integral((t^2 + 2t - 3) sqrt(4t^2 - 8t + 5), t, -1, 0)")))
        // ∫ x·y² ds along (−2t² + t − 2, −2t) from −1 to 1: −33.9437182397133.
        assertEquals("≈ -33.94371824", answerOf(calc("lineintegral(x y^2, (-2t^2 + t - 2, -2t), t, -1, 1)")))
    }
}
