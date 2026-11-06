package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Calls whose steps reach every step of the iterations, norms and orthogonality ([StepsTest.everyKindOfStepIsReached]). */
internal val ITERATIVE_STEPS = listOf(
    "gramschmidt((1, 1, 0), (1, 0, 1), (0, 1, 1))", "gramschmidt((1, 2), (2, 4), (0, 1))",
    "projection((3, 4), (1, 0))", "projection((1, 2, 3), (1, 1, 0), (1, 0, 1))",
    "norm((3, -4), 1)", "norm([[1, 2], [3, 4]], 1)", "norm([[1, 2], [3, 4]], inf)", "norm([[1, 2], [3, 4]], F)", "norm([[1, 2], [3, 4]])",
    "cond([[1, 2], [3, 4]], 1)", "cond([[1, 2], [3, 4]])",
    "fixedpoint(cos(x), 1)", "fixedpoint(x^2, 2)", "fixedpoint(2 - x, 0)",
    "jacobimethod([[4, 1], [2, 5]], [1, 2])", "gaussseidel([[1, 3], [2, 1]], [4, 3])",
)

/**
 * The secant method, fixed-point iteration, the Jacobi and Gauss–Seidel methods, norms and condition numbers, Gram–Schmidt and
 * projections, and Euler's and the Runge–Kutta method to an end with a step: each answer against a value known independently
 * (a textbook value, a closed form, or the same quantity worked out another way in the test: power iteration, the normal
 * equations, the method's recurrence in doubles).
 */
class IterativeTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun failure(text: String): Failure? = (calc(text).outcome as? Outcome.Failed)?.failure

    private fun table(text: String): Numerics.Table = (calc(text).outcome as Outcome.NumericTable).table

    private fun iteration(text: String): Iterative.Iteration = (calc(text).outcome as Outcome.Iterated).iteration

    private fun value(text: String): Double = when (val o = calc(text).outcome) {
        is Outcome.Value -> o.value.double
        else -> throw AssertionError("$text: $o")
    }

    private fun close(expected: Double, actual: Double?, tolerance: Double = 1e-10) {
        assertNotNull(actual)
        assertTrue("$expected vs $actual", abs(expected - actual!!) <= tolerance * maxOf(1.0, abs(expected)))
    }

    private fun decimal(d: Decimal?): Double = when (d) {
        is Decimal.Digits -> (if (d.negative) "-" else "").plus(d.digits[0]).plus(".").plus(d.digits.substring(1)).plus("e${d.exponent}").toDouble()
        Decimal.Zero -> 0.0
        else -> throw AssertionError("no digits: $d")
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The secant method.

    @Test
    fun secantFindsKnownZeros() {
        close(sqrt(2.0), table("secant(x^2 - 2, 1, 2)").result)
        // The Dottie number, cos x = x.
        close(0.7390851332151607, table("secant(cos(x) = x, 0, 1)").result)
        // Newton's own example x³ − 2x − 5 = 0.
        close(2.0945514815423265, table("secant(x^3 - 2x - 5, 2, 3)").result)
        // A zero proved exactly: 2 for x² − 4, 0 for sin x.
        assertEquals(2.0, table("secant(x^2 - 4, 1, 3)").result!!, 0.0)
        assertEquals(0.0, table("secant(sin(x), -1, 0.5)").result!!, 0.0)
    }

    @Test
    fun secantRowsAreTheMethodsRecurrence() {
        val t = table("secant(x^3 - 2x - 5, 2, 3)")
        val f = { x: Double -> x * x * x - 2 * x - 5 }
        assertEquals(listOf(0.0, 2.0, f(2.0)), t.rows[0])
        assertEquals(listOf(1.0, 3.0, f(3.0)), t.rows[1])
        for (k in 2 until t.rows.size) {
            val (a, b) = t.rows[k - 2][1] to t.rows[k - 1][1]
            val next = b - f(b) * (b - a) / (f(b) - f(a))
            assertEquals(next, t.rows[k][1], 1e-12)
        }
    }

    @Test
    fun secantShowsNoResultItCannotProve() {
        // A double zero has no change of sign; no real zero; a pole with a change of sign.
        assertNull(table("secant((x - 1)^2, 0, 3)").result)
        assertNull(table("secant(x^2 + 1, 0, 1)").result)
        assertNull(table("secant(1/x, -1, 2)").result)
    }

    @Test
    fun secantTakesExactlyTwoNumbersAsStarts() {
        for (text in listOf("secant(x^2 - 2, 1)", "secant(x^2 - 2, 1, 2, 3)", "secant(x^2 - 2, a, 2)", "secant(x^2 - 2, 1, 1)", "secant(pi/3, 1, 2)"))
            assertEquals(text, Failure.ARGUMENTS, failure(text))
        assertEquals(Failure.SEVERAL_UNKNOWNS, failure("secant(x^2 - a, 1, 2)"))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Fixed-point iteration.

    @Test
    fun fixedPointConvergesWhereTheSlopeIsBelowOne() {
        val it = iteration("fixedpoint(cos(x), 1)")
        close(0.7390851332151607, it.result!![0])
        val slope = it.tests.single() as Iterative.Test.Slope
        assertEquals(Iterative.Kind.ATTRACTING, slope.kind)
        // |g′(x*)| = sin x*.
        close(kotlin.math.sin(0.7390851332151607), decimal(slope.digits), 1e-9)
        // The rows are xₙ₊₁ = cos xₙ.
        for (k in 1 until it.table.rows.size) assertEquals(kotlin.math.cos(it.table.rows[k - 1][1]), it.table.rows[k][1], 0.0)
        // x = g(x) written as an equation: the same.
        assertEquals(answerOf(calc("fixedpoint(cos(x), 1)")), answerOf(calc("fixedpoint(x = cos(x), 1)")))
    }

    @Test
    fun fixedPointMatchesItsExactFixedPoint() {
        // Heron's iteration for √2: the fixed point √2, where g′ = 0 (quadratic convergence).
        val heron = iteration("fixedpoint((x + 2/x)/2, 1)")
        assertEquals("√2", calc("fixedpoint((x + 2/x)/2, 1)").text(heron.exact!![0]))
        val slope = heron.tests.single() as Iterative.Test.Slope
        assertTrue(isZero(slope.exact!!))
        // √(x + 2): the fixed point 2, |g′(2)| = 1/(2·√4) = 1/4.
        val root = iteration("fixedpoint(sqrt(x + 2), 0)")
        close(2.0, root.result!![0])
        assertEquals(Rational.of(1).div(Rational.of(4)), rational((root.tests.single() as Iterative.Test.Slope).exact!!))
    }

    @Test
    fun fixedPointSaysWhyItDoesNotConverge() {
        // x²: from 2 it runs away; the fixed point 1 repels (|g′| = 2), 0 attracts.
        val square = iteration("fixedpoint(x^2, 2)")
        assertNull(square.result)
        val kinds = square.tests.map { (it as Iterative.Test.Slope).kind }
        assertEquals(listOf(Iterative.Kind.REPELLING, Iterative.Kind.ATTRACTING), kinds)
        // 2 − x: 0, 2, 0, 2 …, |g′(1)| = 1 decides nothing.
        assertEquals(Iterative.Kind.NEUTRAL, (iteration("fixedpoint(2 - x, 0)").tests.single() as Iterative.Test.Slope).kind)
        // The logistic map at 3.2 goes into a cycle of two: its fixed point 11/16 repels, |3.2·(1 − 2·11/16)| = 6/5.
        val logistic = iteration("fixedpoint(3.2x(1 - x), 0.5)")
        assertNull(logistic.result)
        val first = logistic.tests.first() as Iterative.Test.Slope
        assertEquals(Rational.of(11).div(Rational.of(16)), rational(first.at))
        assertEquals(Rational.of(6).div(Rational.of(5)), rational(first.exact!!))
        assertEquals(Failure.ARGUMENTS, failure("fixedpoint(x^2 = 2, 1)"))
        assertEquals(Failure.ARGUMENTS, failure("fixedpoint(cos(x))"))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The Jacobi and Gauss–Seidel methods.

    private fun times(a: List<List<Double>>, b: List<List<Double>>) = a.map { r -> b[0].indices.map { j -> r.indices.sumOf { k -> r[k] * b[k][j] } } }

    /** The largest |λ| of [t] by the growth of ‖Tᵏ‖ (Gelfand's formula), in doubles: another way than the Schur–Cohn test. */
    private fun gelfand(t: List<List<Double>>): Double {
        var p = t
        var k = 1
        repeat(9) { p = times(p, p); k *= 2; val s = p.maxOf { r -> r.maxOf { abs(it) } }; if (s > 1e100 || s < 1e-100) return Math.pow(s, 1.0 / k) }
        return Math.pow(p.sumOf { r -> r.sumOf { abs(it) } }, 1.0 / k)
    }

    @Test
    fun jacobiAndGaussSeidelSolveTheSystem() {
        for (method in listOf("jacobimethod", "gaussseidel")) {
            val it = iteration("$method([[4, 1], [2, 5]], [1, 2])")
            // 4x + y = 1, 2x + 5y = 2: x = 1/6, y = 1/3.
            assertEquals(listOf(Rational.of(1).div(Rational.of(6)), Rational.of(1).div(Rational.of(3))), it.exact!!.map { rational(it) })
            close(1.0 / 6, it.result!![0])
            close(1.0 / 3, it.result!![1])
            assertTrue((it.tests[0] as Iterative.Test.Dominance).holds)
            // The textbook system of Burden and Faires: x = (1, 2, −1, 1).
            val bf = iteration("$method([[10, -1, 2, 0], [-1, 11, -1, 3], [2, -1, 10, -1], [0, 3, -1, 8]], [6, 25, -11, 15])")
            for ((k, want) in listOf(1.0, 2.0, -1.0, 1.0).withIndex()) close(want, bf.result!![k])
        }
        // The equations as written give the same as A and b.
        assertEquals(answerOf(calc("jacobimethod([[4, 1], [2, 5]], [1, 2])")), answerOf(calc("jacobimethod(4x + y = 1, 2x + 5y = 2)")))
    }

    @Test
    fun theRowsAreTheMethodsRecurrence() {
        val a = listOf(listOf(4.0, -1.0, 0.0), listOf(-1.0, 4.0, -1.0), listOf(0.0, -1.0, 4.0))
        val b = listOf(2.0, 4.0, 10.0)
        for (seidel in listOf(false, true)) {
            val it = iteration((if (seidel) "gaussseidel" else "jacobimethod") + "([[4, -1, 0], [-1, 4, -1], [0, -1, 4]], [2, 4, 10])")
            var x = doubleArrayOf(0.0, 0.0, 0.0)
            for (row in it.table.rows.drop(1)) {
                val next = x.copyOf()
                for (i in 0..2) next[i] = (b[i] - (0..2).filter { j -> j != i }.sumOf { j -> a[i][j] * (if (seidel) next[j] else x[j]) }) / a[i][i]
                for (i in 0..2) assertEquals(next[i], row[i + 1], 0.0)
                x = next
            }
            for ((k, want) in listOf(1.0, 2.0, 3.0).withIndex()) close(want, it.result!![k])
        }
    }

    @Test
    fun theSpectralRadiusDecidesConvergence() {
        // 2 × 2: ρ(T_J) = √|a₁₂a₂₁/(a₁₁a₂₂)| = √(2/20), ρ(T_GS) = 2/20.
        val j = iteration("jacobimethod([[4, 1], [2, 5]], [1, 2])").tests[1] as Iterative.Test.Radius
        close(sqrt(0.1), decimal(j.digits), 1e-9)
        assertEquals("√10/10", calc("jacobimethod([[4, 1], [2, 5]], [1, 2])").text(j.exact!!))
        val g = iteration("gaussseidel([[4, 1], [2, 5]], [1, 2])").tests[1] as Iterative.Test.Radius
        assertEquals(Rational.of(1).div(Rational.of(10)), rational(g.exact!!))
        // Jacobi converges and Gauss–Seidel diverges, and the other way round: ρ against Gelfand's formula.
        val cases = listOf(
            "[[1, -2, 2], [-1, 1, -1], [-2, -2, 1]], [1, 1, 1]" to listOf(true, false),
            "[[2, -1, 1], [2, 2, 2], [-1, -1, 2]], [1, 1, 1]" to listOf(false, true),
            "[[1, 3], [2, 1]], [4, 3]" to listOf(false, false),
        )
        for ((system, converges) in cases) {
            for ((k, method) in listOf("jacobimethod", "gaussseidel").withIndex()) {
                val it = iteration("$method($system)")
                val r = it.tests[1] as Iterative.Test.Radius
                assertEquals("$method($system)", converges[k], r.below)
                assertEquals("$method($system)", converges[k], it.result != null)
                val t = r.matrix.rows.map { row -> row.map { Numeric.value(it)!! } }
                val rho = gelfand(t)
                assertTrue("$method($system): ρ ${decimal(r.digits)} vs $rho", abs(rho - decimal(r.digits)) <= 1e-2 * maxOf(1.0, rho))
            }
        }
        // Jacobi's T for the first is nilpotent, T³ = 0: ρ = 0 exactly.
        val nil = iteration("jacobimethod([[1, -2, 2], [-1, 1, -1], [-2, -2, 1]], [1, 1, 1])").tests[1] as Iterative.Test.Radius
        val t = nil.matrix.rows.map { row -> row.map { Numeric.value(it)!! } }
        assertTrue(times(times(t, t), t).all { r -> r.all { it == 0.0 } })
        assertTrue(isZero(nil.exact!!))
    }

    @Test
    fun theSchurCohnTestAgreesWithTheRoots() {
        val random = java.util.Random(7)
        repeat(300) {
            // A monic polynomial from rational roots: all inside the unit circle exactly when the largest |root| < 1.
            val roots = List(1 + random.nextInt(6)) { Rational.of((random.nextInt(41) - 20).toLong()).div(Rational.of((1 + random.nextInt(16)).toLong())) }
            var c = listOf(Rational.ONE)
            for (r in roots) c = (0..c.size).map { k -> (if (k > 0) c[k - 1] else Rational.ZERO) - r * (if (k < c.size) c[k] else Rational.ZERO) }
            val inside = roots.all { it.abs() < Rational.ONE }
            assertEquals(roots.toString(), inside, Iterative.allInsideUnitCircle(c))
            val (lo, hi) = Iterative.spectralBracket(c.map { Ball.of(it, 256) })!!
            val rho = roots.maxOf { it.abs().toDouble() }
            assertTrue("$roots: $rho in [${lo.toDouble()}, ${hi.toDouble()}]", lo.toDouble() <= rho + 1e-15 && rho <= hi.toDouble() + 1e-15)
        }
        // Roots on the circle are not inside: z² + 1.
        assertFalse(Iterative.allInsideUnitCircle(listOf(Rational.ONE, Rational.ZERO, Rational.ONE)))
    }

    @Test
    fun aSystemTheMethodsCannotTakeIsRefused() {
        assertEquals(Failure.ZERO_DIAGONAL, failure("jacobimethod([[0, 1], [1, 0]], [1, 1])"))
        assertEquals(Failure.SINGULAR, failure("gaussseidel([[1, 2], [2, 4]], [1, 2])"))
        assertEquals(Failure.SIZES, failure("jacobimethod([[4, 1], [2, 5]], [1, 2, 3])"))
        assertEquals(Failure.SIZES, failure("jacobimethod([[4, 1, 0], [2, 5, 1]], [1, 2])"))
        assertEquals(Failure.ARGUMENTS, failure("jacobimethod([[4, 1], [2, 5]])"))
        assertEquals(Failure.ARGUMENTS, failure("jacobimethod([[4, 1], [2, 5]], [1, 2], [0, 0], [1, 1])"))
        assertEquals(Failure.UNSOLVED, failure("jacobimethod(x^2 + y = 1, x - y = 2)"))
        // jacobi(a, n) stays the Jacobi symbol: (2/15) = 1.
        assertEquals("1", answerOf(calc("jacobi(2, 15)")))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Norms and condition numbers.

    /** λmax of a symmetric positive definite matrix by power iteration, in doubles. */
    private fun largest(m: List<List<Double>>): Double {
        var v = List(m.size) { 1.0 + it * 0.1 }
        var lambda = 0.0
        repeat(2000) {
            val w = m.map { r -> r.indices.sumOf { r[it] * v[it] } }
            lambda = sqrt(w.sumOf { it * it })
            v = w.map { it / lambda }
        }
        return lambda
    }

    private fun gram(a: List<List<Double>>) = a[0].indices.map { i -> a[0].indices.map { j -> a.indices.sumOf { k -> a[k][i] * a[k][j] } } }

    private fun inverse2or3(a: List<List<Double>>): List<List<Double>> {
        val n = a.size
        val m = a.mapIndexed { i, r -> (r + List(n) { if (it == i) 1.0 else 0.0 }).toMutableList() }.toMutableList()
        for (c in 0 until n) {
            val p = (c until n).maxBy { abs(m[it][c]) }
            val t = m[p]; m[p] = m[c]; m[c] = t
            val d = m[c][c]
            for (j in 0 until 2 * n) m[c][j] /= d
            for (r in 0 until n) if (r != c) { val f = m[r][c]; for (j in 0 until 2 * n) m[r][j] -= f * m[c][j] }
        }
        return m.map { it.drop(n) }
    }

    @Test
    fun normsOfAMatrix() {
        assertEquals(6.0, value("norm([[1, 2], [3, 4]], 1)"), 0.0)
        assertEquals(7.0, value("norm([[1, 2], [3, 4]], inf)"), 0.0)
        assertEquals("√30 ≈ 5.477225575", answerOf(calc("norm([[1, 2], [3, 4]], F)")))
        // σmax by power iteration on AᵀA.
        val a = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0))
        close(sqrt(largest(gram(a))), value("norm([[1, 2], [3, 4]])"))
        assertEquals("√(15 + √221) ≈ 5.464985704", answerOf(calc("norm([[1, 2], [3, 4]])")))
        val b = listOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0), listOf(7.0, 8.0, 10.0))
        close(sqrt(largest(gram(b))), value("norm([[1, 2, 3], [4, 5, 6], [7, 8, 10]])"), 1e-9)
        // A symmetric matrix: its largest |λ|, 4 + √2.
        assertEquals("4 + √2 ≈ 5.414213562", answerOf(calc("norm([[4, 1, 0], [1, 4, 1], [0, 1, 4]])")))
    }

    @Test
    fun normsOfAVector() {
        assertEquals(5.0, value("norm((3, 4))"), 0.0)
        assertEquals(7.0, value("norm((3, -4), 1)"), 0.0)
        assertEquals(4.0, value("norm((3, -4), inf)"), 0.0)
        close(Math.cbrt(36.0), value("norm((1, 2, 3), 3)"))
        assertEquals("√(x^2 + y^2)", answerOf(calc("norm((x, y))")))
        for (text in listOf("norm((1, 2), 0)", "norm((1, 2), x)", "norm([[1, 2], [3, 4]], 3)", "cond([[1, 2], [3, 4]], 3)"))
            assertEquals(text, Failure.ARGUMENTS, failure(text))
    }

    @Test
    fun conditionNumbers() {
        // cond₂ = σmax/σmin; the textbook value 14.9330343737 for [[1, 2], [3, 4]].
        close(14.933034373659268, value("cond([[1, 2], [3, 4]])"))
        assertEquals("15/2 + √221/2 ≈ 14.93303437", answerOf(calc("cond([[1, 2], [3, 4]])")))
        // ‖A‖₁‖A⁻¹‖₁ = 6 · 7/2, ‖A‖∞‖A⁻¹‖∞ = 7 · 3, ‖A‖F‖A⁻¹‖F = √30 · √30/2.
        assertEquals(21.0, value("cond([[1, 2], [3, 4]], 1)"), 0.0)
        assertEquals(21.0, value("cond([[1, 2], [3, 4]], inf)"), 0.0)
        assertEquals(15.0, value("cond([[1, 2], [3, 4]], F)"), 0.0)
        // The Hilbert matrix of order 3: cond₂ ≈ 524.0567776, cond∞ = 748.
        close(524.0567775860644, value("cond([[1, 1/2, 1/3], [1/2, 1/3, 1/4], [1/3, 1/4, 1/5]])"), 1e-9)
        assertEquals(748.0, value("cond([[1, 1/2, 1/3], [1/2, 1/3, 1/4], [1/3, 1/4, 1/5]], inf)"), 0.0)
        // A 3 × 3 matrix without exact eigenvalues of AᵀA: against power iteration on AᵀA and on (AᵀA)⁻¹.
        val b = listOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0), listOf(7.0, 8.0, 10.0))
        val g = gram(b)
        close(sqrt(largest(g) * largest(inverse2or3(g))), value("cond([[1, 2, 3], [4, 5, 6], [7, 8, 10]])"), 1e-8)
        assertEquals(1.5, value("cond([[2, 0], [0, 3]])"), 0.0)
        assertEquals(Failure.SINGULAR, failure("cond([[1, 1], [1, 1]])"))
        assertEquals(Failure.SIZES, failure("cond((1, 2))"))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Gram–Schmidt and projections.

    private fun vectors(text: String): List<List<Double>> =
        (calc(text).outcome as Outcome.Orthonormalized).basis.orthonormal.map { v -> v.rows.map { Numeric.value(it[0])!! } }

    @Test
    fun gramSchmidtGivesTheTextbookBasis() {
        val e = vectors("gramschmidt((1, 1, 0), (1, 0, 1), (0, 1, 1))")
        val s2 = 1 / sqrt(2.0)
        val s6 = 1 / sqrt(6.0)
        val s3 = 1 / sqrt(3.0)
        val want = listOf(listOf(s2, s2, 0.0), listOf(s6, -s6, 2 * s6), listOf(-s3, s3, s3))
        for (i in 0..2) for (k in 0..2) assertEquals(want[i][k], e[i][k], 1e-12)
        assertEquals("(√2/2, √2/2, 0) | (√6/6, -√6/6, √6/3) | (-√3/3, √3/3, √3/3)", answerOf(calc("gramschmidt((1, 1, 0), (1, 0, 1), (0, 1, 1))")))
        assertEquals("(3/5, 4/5)", answerOf(calc("gramschmidt((3, 4))")))
    }

    @Test
    fun gramSchmidtLeavesOutWhatDepends() {
        val b = (calc("gramschmidt((1, 2), (3, 4), (5, 6))").outcome as Outcome.Orthonormalized).basis
        assertEquals(listOf(2), b.dependent)
        // Orthonormal in doubles, and the plane is spanned.
        val e = b.orthonormal.map { v -> v.rows.map { Numeric.value(it[0])!! } }
        assertEquals(1.0, e[0][0] * e[0][0] + e[0][1] * e[0][1], 1e-12)
        assertEquals(0.0, e[0][0] * e[1][0] + e[0][1] * e[1][1], 1e-12)
        assertEquals(listOf(1), (calc("gramschmidt((1, 2), (2, 4), (0, 1))").outcome as Outcome.Orthonormalized).basis.dependent)
        assertEquals(Failure.UNSOLVED, failure("gramschmidt((a, 1), (1, 0))"))
        assertEquals(Failure.SIZES, failure("gramschmidt((1, 2), (1, 2, 3))"))
    }

    @Test
    fun projectionsAgreeWithTheNormalEquations() {
        assertEquals("(3, 0)", answerOf(calc("projection((3, 4), (1, 0))")))
        assertEquals("(2, 2, 2)", answerOf(calc("projection((1, 2, 3), (1, 1, 1))")))
        assertEquals("(1, 2, 0)", answerOf(calc("projection((1, 2, 3), (1, 0, 0), (0, 1, 0))")))
        // Onto the span of (1, 1, 0) and (1, 0, 1): p = U(UᵀU)⁻¹Uᵀv worked out in doubles.
        val u = listOf(listOf(1.0, 1.0), listOf(1.0, 0.0), listOf(0.0, 1.0))
        val v = listOf(1.0, 2.0, 3.0)
        val utu = gram(u)
        val utv = (0..1).map { j -> (0..2).sumOf { u[it][j] * v[it] } }
        val det = utu[0][0] * utu[1][1] - utu[0][1] * utu[1][0]
        val c = listOf((utu[1][1] * utv[0] - utu[0][1] * utv[1]) / det, (utu[0][0] * utv[1] - utu[1][0] * utv[0]) / det)
        val p = (0..2).map { i -> u[i][0] * c[0] + u[i][1] * c[1] }
        val got = (calc("projection((1, 2, 3), (1, 1, 0), (1, 0, 1))").outcome as Outcome.Expression).result as Expr.Mat
        for (i in 0..2) assertEquals(p[i], Numeric.value(got.rows[i][0])!!, 1e-12)
        // A vector with letters, onto (1, 1).
        assertEquals("((1/2)x + (1/2)y, (1/2)x + (1/2)y)", answerOf(calc("projection((x, y), (1, 1))")))
        assertEquals(Failure.DIVISION_BY_ZERO, failure("projection((1, 2), (0, 0))"))
        assertEquals(Failure.ARGUMENTS, failure("projection((2, 3))"))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Euler's and the Runge–Kutta method to an end, with a step.

    @Test
    fun eulerAndRungeKuttaTakeTheEndAndTheStep() {
        // y′ = y: Euler multiplies by 1 + h, RK4 by 1 + h + h²/2 + h³/6 + h⁴/24 each step.
        close(Math.pow(1.05, 20.0), table("euler(y' = y, y(0) = 1, 1, 0.05)").result)
        close(Math.pow(1.5, 4.0), table("euler(y' = y, y(0) = 1, 2, 0.5)").result)
        close(Math.pow(0.9, 10.0), table("euler(y' = y, y(0) = 1, -1, -0.1)").result)
        val z = -1.0
        close(Math.pow(1 + z + z * z / 2 + z * z * z / 6 + z * z * z * z / 24, 2.0), table("rungekutta(y' = -2y, y(0) = 1, 1, 0.5)").result)
        // To b in ten steps; without b, ten steps of 0.1 as before.
        assertEquals(11, table("euler(y' = y, y(0) = 1, 2)").rows.size)
        close(Math.pow(1.2, 10.0), table("euler(y' = y, y(0) = 1, 2)").result)
        close(Math.pow(1.1, 10.0), table("euler(y' = y, y(0) = 1)").result)
        for (text in listOf("euler(y' = y, y(0) = 1, 1, 0.3)", "euler(y' = y, y(0) = 1, -1, 0.1)", "euler(y' = y, y(0) = 1, 1, 0)",
                "euler(y' = y, y(0) = 1, 0)", "euler(y' = y, y(0) = 1, a)", "rungekutta(y' = y, y(0) = 1, 1, 0.1, 3)"))
            assertEquals(text, Failure.ARGUMENTS, failure(text))
        assertEquals(Failure.TOO_LARGE, failure("euler(y' = y, y(0) = 1, 1, 0.0001)"))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Steps and time.

    @Test
    fun eachStepKeepsItsNumberOfFormulas() {
        val counts = HashMap<StepKey, MutableSet<Int>>()
        for (text in ITERATIVE_STEPS) {
            val t = TextInput.read(text)
            val steps = Steps.of(t.latex, t.choice)
            assertNotNull(text, steps)
            for (s in steps!!) counts.getOrPut(s.why.key) { HashSet() } += s.why.args.size
        }
        assertEquals("", counts.filter { it.value.size > 1 }.entries.joinToString("\n") { "${it.key}: ${it.value}" })
    }

    @Test
    fun largeSystemsStayWithinTheirTime() {
        // An 8 × 8 diagonally dominant system: well within the three seconds.
        val n = 8
        val rows = (0 until n).joinToString(", ") { i -> "[" + (0 until n).joinToString(", ") { j -> if (i == j) "10" else if (abs(i - j) == 1) "-1" else "0" } + "]" }
        val b = "[" + (0 until n).joinToString(", ") { "1" } + "]"
        val start = System.nanoTime()
        val it = iteration("gaussseidel([$rows], $b)")
        assertNotNull(it.result)
        assertTrue((it.tests[1] as Iterative.Test.Radius).below)
        assertTrue(System.nanoTime() - start < 3_000_000_000L)
        val cond = System.nanoTime()
        value("cond([$rows])")
        assertTrue(System.nanoTime() - cond < 3_000_000_000L)
    }
}
