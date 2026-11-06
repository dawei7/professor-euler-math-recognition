package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Hypothesis tests, confidence intervals, fits and Markov chains, each against a reference worked out here without the engine:
 * the t distribution's tail by Simpson's rule on its density (normalized by the same integral over the whole line, so no Γ is
 * needed), statistics and intervals by their textbook formulas in doubles, exact values by hand, a logistic fit's gradient by
 * differences.
 */
class InferenceTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun result(text: String): StatResult =
        (calc(text).outcome as? Outcome.Statistical)?.result ?: error("$text: ${calc(text).outcome}")

    private fun failure(text: String): String = (calc(text).outcome as? Outcome.Failed)?.failure?.name ?: "no failure"

    private fun number(r: StatResult, q: Quantity, group: Int = 0): Double = Numeric.value(r.value(q, group)!!)!!

    private fun coefficient(r: StatResult, symbol: String): Double = Numeric.value(r.rows.first { it.symbol == symbol }.value)!!

    private fun close(expected: Double, got: Double, rel: Double = 1e-8, what: String = "") =
        assertTrue("$what: $got, not $expected", abs(got - expected) <= rel * maxOf(abs(expected), 1e-300))

    /** ∫ₐᵇ f by Simpson's rule with [n] (even) panels. */
    private fun simpson(f: (Double) -> Double, a: Double, b: Double, n: Int = 20000): Double {
        val h = (b - a) / n
        var s = f(a) + f(b)
        for (i in 1 until n) s += f(a + i * h) * if (i % 2 == 1) 4 else 2
        return s * h / 3
    }

    /** P(T ≥ t) for t ≥ 0 and any ν > 0: x = tan u maps the line to (−π/2, π/2), where the density is smooth. */
    private fun tTail(t: Double, nu: Double): Double {
        val g = { u: Double -> val x = Math.tan(u); val c = Math.cos(u); Math.pow(1 + x * x / nu, -(nu + 1) / 2) / (c * c) }
        val whole = simpson(g, -Math.PI / 2 + 1e-12, Math.PI / 2 - 1e-12)
        return simpson(g, Math.atan(t), Math.PI / 2 - 1e-12) / whole
    }

    /** Φ by Simpson's rule on the density from 0. */
    private fun phi(z: Double): Double = 0.5 + Math.signum(z) * simpson({ x -> exp(-x * x / 2) }, 0.0, abs(z)) / sqrt(2 * Math.PI)

    @Test
    fun tTests() {
        // A textbook's summary: x̄ = 52, s = 4, n = 25, μ₀ = 50: t = 2.5 with 24 degrees of freedom.
        val r = result("ttest(52, 4, 25, 50)")
        close(2.5, number(r, Quantity.STATISTIC))
        close(24.0, number(r, Quantity.DF))
        close(2 * tTail(2.5, 24.0), number(r, Quantity.P_VALUE), 1e-7, "p")
        assertEquals(true, r.reject)
        val one = result("ttest(52, 4, 25, 50, 0.01, greater)")
        close(tTail(2.5, 24.0), number(one, Quantity.P_VALUE), 1e-7, "one-sided p")
        assertEquals(Alternative.GREATER, one.alternative)
        // The critical value has the share α beyond it.
        close(0.01, tTail(number(one, Quantity.CRITICAL), 24.0), 1e-6, "critical")
        // Data: t = (x̄ − μ₀)/(s/√n) by hand.
        val data = listOf(5.1, 4.9, 5.3, 5.2)
        val mean = data.average()
        val s = sqrt(data.sumOf { (it - mean) * (it - mean) } / 3)
        val d = result("ttest([5.1, 4.9, 5.3, 5.2], 5)")
        close((mean - 5) / (s / 2), number(d, Quantity.STATISTIC))
        close(2 * tTail((mean - 5) / (s / 2), 3.0), number(d, Quantity.P_VALUE), 1e-7, "data p")
        assertEquals(false, d.reject)
        assertEquals(Alternative.LESS, result("ttest(52, 4, 25, 50, less)").alternative)
    }

    @Test
    fun zTest() {
        // σ known: z = (52 − 50)/(6/√36) = 2; P(Z ≥ 2) = 1 − Φ(2).
        val r = result("ztest(52, 36, 50, 6, greater)")
        close(2.0, number(r, Quantity.STATISTIC))
        close(1 - 0.9772498680518208, number(r, Quantity.P_VALUE), 1e-9)
        close(1.6448536269514722, number(r, Quantity.CRITICAL), 1e-9)
        assertEquals(true, r.reject)
        val two = result("ztest([5.1, 4.9, 5.3, 5.2], 5, 0.2)")
        close(1.25, number(two, Quantity.STATISTIC))
        close(2 * (1 - phi(1.25)), number(two, Quantity.P_VALUE), 1e-8)
    }

    @Test
    fun welch() {
        // By hand: v₁ = 6.5/5, v₂ = 2.5/5, t = −6/√1.8 = −2√5, ν = 1.8²/(1.3²/4 + 0.5²/4) = 648/97.
        val r = result("welchtest([20, 22, 19, 24, 25], [28, 27, 30, 26, 29])")
        assertEquals(Calculus.tidy(mul(num(-2), sqrt(num(5)))), r.value(Quantity.STATISTIC))
        assertEquals(num(Rational.of(648) / Rational.of(97)), r.value(Quantity.DF))
        close(2 * tTail(2 * sqrt(5.0), 648.0 / 97), number(r, Quantity.P_VALUE), 1e-7, "Welch p")
        assertEquals(true, r.reject)
        // From the summaries the same.
        val s = result("welchtest(22, 2.549509757, 5, 28, 1.58113883, 5)")
        close(number(r, Quantity.P_VALUE), number(s, Quantity.P_VALUE), 1e-7, "summary")
    }

    @Test
    fun chiSquareTests() {
        // Goodness of fit, four categories expected equal: χ² = (49 + 9 + 25 + 225)/25 = 308/25 with 3 degrees of freedom.
        val r = result("chisqgof([18, 22, 20, 40], [25, 25, 25, 25])")
        assertEquals(num(Rational.of(308) / Rational.of(25)), r.value(Quantity.STATISTIC))
        close(3.0, number(r, Quantity.DF))
        // Three categories: 2 degrees of freedom, P(χ²₂ ≥ x) = e^(−x/2).
        val three = result("chisqgof([30, 50, 20], [0.25, 0.5, 0.25])")
        val chi = (30 - 25.0) * (30 - 25.0) / 25 + 0.0 + (20 - 25.0) * (20 - 25.0) / 25
        close(chi, number(three, Quantity.STATISTIC))
        close(exp(-chi / 2), number(three, Quantity.P_VALUE), 1e-9)
        // Independence, 3 × 2: 2 degrees of freedom; the expected counts row total · column total / n.
        val t = result("chisqindep([[20, 30], [30, 20], [25, 25]])")
        val observed = listOf(20.0, 30.0, 30.0, 20.0, 25.0, 25.0)
        val stat = observed.sumOf { (it - 25) * (it - 25) / 25 }
        close(stat, number(t, Quantity.STATISTIC))
        close(2.0, number(t, Quantity.DF))
        close(exp(-stat / 2), number(t, Quantity.P_VALUE), 1e-9)
        assertEquals(LinAlg.matrix(List(3) { List(2) { num(25) } }), t.expected)
        // A small expected count is said.
        assertEquals(listOf(StatWarning.SMALL_EXPECTED), result("chisqindep([[1, 2], [3, 4]])").warnings)
    }

    @Test
    fun intervals() {
        val t = result("tinterval(52, 4, 25, 0.99)")
        val c = number(t, Quantity.CRITICAL)
        close(0.005, tTail(c, 24.0), 1e-6, "t*")
        close(52 - c * 0.8, number(t, Quantity.LOWER))
        close(52 + c * 0.8, number(t, Quantity.UPPER))
        val z = result("zinterval([5.1, 4.9, 5.3, 5.2], 0.2, 0.9)")
        close(1.6448536269514722, number(z, Quantity.CRITICAL), 1e-9)
        close(5.125 - 1.6448536269514722 * 0.1, number(z, Quantity.LOWER))
        // A proportion: Wald and Wilson by their formulas.
        val p = result("propinterval(45, 100)")
        val zz = 1.959963984540054
        val ph = 0.45
        val se = sqrt(ph * (1 - ph) / 100)
        close(ph - zz * se, number(p, Quantity.LOWER))
        close(ph + zz * se, number(p, Quantity.UPPER))
        val centre = (ph + zz * zz / 200) / (1 + zz * zz / 100)
        val half = zz * sqrt(ph * (1 - ph) / 100 + zz * zz / 40000) / (1 + zz * zz / 100)
        close(centre - half, number(p, Quantity.WILSON_LOWER))
        close(centre + half, number(p, Quantity.WILSON_UPPER))
        assertEquals(listOf(StatWarning.FEW_SUCCESSES), result("propinterval(2, 30)").warnings)
    }

    @Test
    fun fits() {
        // The quadratic through (1, 2), (2, 4), (3, 5), (4, 4): its residuals are orthogonal to 1, x and x², exactly.
        val q = result("fitpoly((1, 2), (2, 4), (3, 5), (4, 4), 2)")
        val a = listOf("a_0", "a_1", "a_2").map { s -> rational(q.rows.first { it.symbol == s }.value)!! }
        val xs = listOf(1, 2, 3, 4).map { Rational.of(it.toLong()) }
        val ys = listOf(2, 4, 5, 4).map { Rational.of(it.toLong()) }
        val residuals = xs.indices.map { ys[it] - (a[0] + a[1] * xs[it] + a[2] * xs[it] * xs[it]) }
        for (j in 0..2) assertEquals(Rational.ZERO, xs.indices.fold(Rational.ZERO) { s, k -> s + residuals[k] * (xs[k].pow(j)!!) })
        assertEquals(num(Rational.of(94) / Rational.of(95)), q.value(Quantity.R_SQUARED))
        // Exact exponential data: y = 2ˣ, so a = 1 and k = ln 2.
        val e = result("fitexp((0, 1), (1, 2), (2, 4), (3, 8))")
        close(ln(2.0), coefficient(e, "k"), 1e-9)
        close(1.0, coefficient(e, "a"), 1e-9)
        close(2.0, coefficient(e, "b"), 1e-9)
        // Least squares of ln y by the textbook formulas.
        val px = listOf(1.0, 2.0, 3.0, 4.0)
        val py = listOf(3.0, 5.0, 10.0, 19.0).map { ln(it) }
        val mx = px.average()
        val my = py.average()
        val k = px.indices.sumOf { (px[it] - mx) * (py[it] - my) } / px.sumOf { (it - mx) * (it - mx) }
        val g = result("fitexp((1, 3), (2, 5), (3, 10), (4, 19))")
        close(k, coefficient(g, "k"), 1e-9)
        close(exp(my - k * mx), coefficient(g, "a"), 1e-9)
        val pw = result("fitpow((1, 2), (2, 8), (3, 18))")
        close(2.0, coefficient(pw, "a"), 1e-9)
        close(2.0, coefficient(pw, "b"), 1e-9)
        val lg = result("fitlog((1, 2), (2, 3), (4, 4), (8, 5.1))")
        val lx = listOf(1.0, 2.0, 4.0, 8.0).map { ln(it) }
        val ly = listOf(2.0, 3.0, 4.0, 5.1)
        val b = lx.indices.sumOf { (lx[it] - lx.average()) * (ly[it] - ly.average()) } / lx.sumOf { (it - lx.average()) * (it - lx.average()) }
        close(b, coefficient(lg, "b"), 1e-9)
        close(ly.average() - b * lx.average(), coefficient(lg, "a"), 1e-9)
    }

    @Test
    fun logisticFitIsAMinimum() {
        val points = listOf(0.0 to 10.0, 1.0 to 25.0, 2.0 to 55.0, 3.0 to 85.0, 4.0 to 95.0, 5.0 to 99.0)
        val r = result("fitlogistic((0, 10), (1, 25), (2, 55), (3, 85), (4, 95), (5, 99))")
        val t = doubleArrayOf(coefficient(r, "c"), coefficient(r, "a"), coefficient(r, "k"))
        fun ss(v: DoubleArray) = points.sumOf { (x, y) -> val d = y - v[0] / (1 + v[1] * exp(-v[2] * x)); d * d }
        // The gradient by central differences is 0 there, and every nearby point has more squared residuals.
        for (j in 0..2) {
            val h = 1e-6 * abs(t[j])
            val up = t.copyOf().also { it[j] += h }
            val down = t.copyOf().also { it[j] -= h }
            val slope = (ss(up) - ss(down)) / (2 * h)
            assertTrue("dSS/dθ$j = $slope", abs(slope) < 1e-4)
            val far = t.copyOf().also { it[j] += 1e-3 * abs(t[j]) }
            assertTrue(ss(far) > ss(t))
        }
        close(ss(t), number(r, Quantity.RESIDUAL_SUM), 1e-8)
    }

    @Test
    fun markovChains() {
        assertEquals("[[5/6, 1/6]]", answerOf(calc("stationary([[0.9, 0.1], [0.5, 0.5]])")))
        assertEquals("[[1/4, 1/2, 1/4]]", answerOf(calc("stationary([[0.5, 0.5, 0], [0.25, 0.5, 0.25], [0, 0.5, 0.5]])")))
        // [1, 0]·P³ by hand: P² row 1 is (0.86, 0.14), P³ row 1 (0.844, 0.156).
        assertEquals("[[211/250, 39/250]]", answerOf(calc("markov([[0.9, 0.1], [0.5, 0.5]], [1, 0], 3)")))
        assertEquals("[[1, 0]]", answerOf(calc("markov([[0.9, 0.1], [0.5, 0.5]], [1, 0], 0)")))
        assertEquals("MANY_SOLUTIONS", failure("stationary([[1, 0], [0, 1]])"))
        assertEquals("UNDEFINED", failure("stationary([[0.5, 0.6], [0.5, 0.5]])"))
        assertEquals("SIZES", failure("markov([[0.9, 0.1], [0.5, 0.5]], [1, 0, 0], 2)"))
    }

    @Test
    fun argumentsTheyDoNotTake() {
        assertEquals("ARGUMENTS", failure("ttest([1, 2, 3])"))
        assertEquals("ARGUMENTS", failure("ttest([1, 2, 3], 2, greater, 0.05)"))
        assertEquals("ARGUMENTS", failure("ttest([1, 2, 3], 2, 0.05, sideways)"))
        assertEquals("ARGUMENTS", failure("chisqgof([1, 2], [1, 1], greater)"))
        assertEquals("UNDEFINED", failure("ttest([1, 2, 3], 2, 1.5)"))
        assertEquals("UNDEFINED", failure("ttest([1, 1, 1], 0)"))
        assertEquals("UNDEFINED", failure("tinterval([1, 2, 3], 95)"))
        assertEquals("UNDEFINED", failure("propinterval(5, 3)"))
        assertEquals("UNDEFINED", failure("fitexp((1, 2), (2, -1), (3, 4))"))
        assertEquals("SIZES", failure("chisqgof([1, 2], [1, 1, 1])"))
        assertEquals("COMMAND_INSIDE", failure("2·ttest([1, 2, 3], 2)"))
    }
}
