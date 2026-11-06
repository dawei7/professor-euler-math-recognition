package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * interpolate and spline: the polynomial through points taken from a polynomial known beforehand is that polynomial, the
 * Lagrange basis is 1 at its own point and 0 at the others, the top divided difference is Σ yᵢ/∏(xᵢ − xⱼ); a spline is
 * checked by differentiating its pieces here (S, S′ and S″ where they meet, S″ = 0 at the ends), reproduces a straight line,
 * and matches the textbook example worked by hand.
 */
class InterpolationTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        val o = r.outcome
        return if (o is Outcome.Failed) "FAILED ${o.failure}" else answerOf(r)
    }

    private fun points(xs: List<Rational>, ys: List<Rational>) = Expr.Mat(xs.indices.map { listOf(num(xs[it]), num(ys[it])) })

    private val x = Expr.Sym("x")

    private fun value(e: Expr, at: Rational): Rational = rational(Calculus.tidy(e.substitute(x, num(at))))!!

    /** Random polynomials of degree up to 6 with fractions as coefficients, sampled at distinct points: recovered exactly. */
    @Test
    fun recoversThePolynomialItCameFrom() {
        val rnd = Random(42)
        repeat(40) {
            val degree = rnd.nextInt(7)
            val coefficients = List(degree + 1) { Rational.of(rnd.nextInt(19) - 9L) / Rational.of(rnd.nextInt(4) + 1L) }
            val p = Calculus.tidy(add(coefficients.mapIndexed { k, c -> mul(num(c), pow(x, num(k.toLong()))) }))
            // Two points at least: one point is no interpolation the function takes.
            val n = maxOf(2, degree + 1 + rnd.nextInt(3))
            val xs = (-10..10).shuffled(rnd).map { Rational.of(it.toLong()) / Rational.of(rnd.nextInt(3) + 1L) }.distinct().take(n)
            val ys = xs.map { value(p, it) }
            val q = Interpolation.interpolate(points(xs, ys))
            assertTrue("$p from $xs: ${q.polynomial}", Verify.same(q.polynomial, p))
            // Lagrange's basis: Lᵢ(xⱼ) = 1 where i = j, else 0.
            for ((i, l) in q.lagrange.withIndex()) for ((j, xj) in xs.withIndex()) {
                assertEquals(if (i == j) Rational.ONE else Rational.ZERO, value(l, xj))
            }
            // The top divided difference, Σ yᵢ / ∏ⱼ≠ᵢ (xᵢ − xⱼ).
            val top = xs.indices.fold(Rational.ZERO) { acc, i ->
                acc + ys[i] / xs.indices.filter { it != i }.fold(Rational.ONE) { d, j -> d * (xs[i] - xs[j]) }
            }
            assertEquals(top, rational(q.newton.last()))
            assertEquals(q.newton, q.differences.map { it.first() })
        }
    }

    @Test
    fun typedAsAFunction() {
        val cases = listOf(
            "interpolate((1, 2), (2, 3), (3, 5))" to "p(x) = (1/2)x^2 - (1/2)x + 2 newton 2, 1, 1/2",
            "interpolate((0, 1), (1, 3), (2, 7), (3, 13))" to "p(x) = x^2 + x + 1 newton 1, 2, 1, 0",
            "interpolate((-1, 1), (0, 0), (1, 1), (2, 4))" to "p(x) = x^2 newton 1, -1, 1, 0",
            "interpolate((0, 0), (1, 1))" to "p(x) = x newton 0, 1",
            "interpolate((0.5, 1.2), (1.5, 2.6), (2.5, 1.1))" to "p(x) = -(29/20)x^2 + (43/10)x - 47/80 newton 6/5, 7/5, -29/20",
            "interpolate((1, 2), (1, 3))" to "FAILED SAME_X",
            "interpolate((1, a), (2, b))" to "FAILED ARGUMENTS",
            "interpolate((1, 2))" to "FAILED ARGUMENTS",
            "spline((0, 0), (1, 1), (2, 0))" to "[0, 1] -(1/2)x^3 + (3/2)x | [1, 2] (1/2)x^3 - 3x^2 + (9/2)x - 1",
            "spline((0, 1), (1, 2))" to "[0, 1] x + 1",
            "spline((2, 0), (0, 0), (1, 1))" to "[0, 1] -(1/2)x^3 + (3/2)x | [1, 2] (1/2)x^3 - 3x^2 + (9/2)x - 1",
            "spline((1, 2), (1, 3), (2, 2))" to "FAILED SAME_X",
        )
        val wrong = cases.mapNotNull { (text, expected) -> answer(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
        val many = (0..20).joinToString(", ") { "($it, ${it * it})" }
        assertEquals("FAILED TOO_LARGE", answer("interpolate($many)"))
    }

    /** Splines through random points: their pieces differentiated here meet with equal S, S′ and S″, and S″ is 0 at both ends. */
    @Test
    fun splinesAreTwiceContinuouslyDifferentiable() {
        val rnd = Random(8)
        repeat(30) {
            val n = 2 + rnd.nextInt(8)
            val xs = (0..30).shuffled(rnd).take(n).map { Rational.of(it.toLong()) / Rational.of(2) }.sorted()
            val ys = xs.map { Rational.of(rnd.nextInt(21) - 10L) / Rational.of(rnd.nextInt(3) + 1L) }
            val s = Interpolation.spline(points(xs, ys))
            val pieces = s.pieces.map { it.polynomial }
            assertEquals(n - 1, pieces.size)
            for ((i, p) in pieces.withIndex()) {
                assertEquals(ys[i], value(p, xs[i]))
                assertEquals(ys[i + 1], value(p, xs[i + 1]))
                if (i + 1 < pieces.size) {
                    val q = pieces[i + 1]
                    assertEquals(value(Calculus.derivative(p, x), xs[i + 1]), value(Calculus.derivative(q, x), xs[i + 1]))
                    val pp = Calculus.derivative(Calculus.derivative(p, x), x)
                    val qq = Calculus.derivative(Calculus.derivative(q, x), x)
                    assertEquals(value(pp, xs[i + 1]), value(qq, xs[i + 1]))
                    assertEquals(rational(s.moments[i + 1]), value(pp, xs[i + 1]))
                }
                // Each piece is a cubic at most.
                assertTrue(Verify.same(Calculus.derivative(Calculus.derivative(Calculus.derivative(Calculus.derivative(p, x), x), x), x), Expr.ZERO))
            }
            assertEquals(Rational.ZERO, value(Calculus.derivative(Calculus.derivative(pieces.first(), x), x), xs.first()))
            assertEquals(Rational.ZERO, value(Calculus.derivative(Calculus.derivative(pieces.last(), x), x), xs.last()))
        }
    }

    /** Points on a straight line: every piece of the natural spline is that line. */
    @Test
    fun aSplineReproducesALine() {
        val s = Interpolation.spline(points((0..5).map { Rational.of(it * 2L) }, (0..5).map { Rational.of(3L * it * 2 - 7) }))
        for (p in s.pieces) assertTrue(Verify.same(p.polynomial, add(mul(num(3), x), num(-7))))
        assertTrue(s.moments.all { isZero(it) })
    }

    @Test
    fun theirSteps() {
        fun keys(text: String) = TextInput.read(text).let { t -> Steps.of(t.latex, t.choice) }?.joinToString(" ") { it.why.key.name } ?: "none"
        assertEquals("START INTERP_DIFFERENCES INTERP_DIFFERENCES INTERP_NEWTON INTERP_CHECK RESULT", keys("interpolate((1, 2), (2, 3), (3, 5))"))
        assertEquals("START SPLINE_WIDTHS SPLINE_SYSTEM SPLINE_MOMENTS SPLINE_PIECE SPLINE_PIECE RESULT", keys("spline((0, 0), (1, 1), (2, 0))"))
        val newton = TextInput.read("interpolate((1, 2), (2, 3), (3, 5))").let { t -> Steps.of(t.latex, t.choice)!! }
            .first { it.why.key == StepKey.INTERP_NEWTON }.latex
        assertEquals("p(x) = 2 + \\left(x - 1\\right) + \\frac{1}{2}\\left(x - 1\\right)\\left(x - 2\\right)", newton)
        val piece = TextInput.read("spline((0, 0), (1, 1), (2, 0))").let { t -> Steps.of(t.latex, t.choice)!! }
            .filter { it.why.key == StepKey.SPLINE_PIECE }[1].latex
        assertEquals("S_{1}(x) = 1 - \\frac{3}{2}\\left(x - 1\\right)^{2} + \\frac{1}{2}\\left(x - 1\\right)^{3}", piece)
    }
}
