package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Defects the guide writers found: a limit shown as a decimal from values near its point, and taylor(…) giving the function
 * back instead of its series (a polynomial at a point, a power with a letter). Each answer is the right one or a refusal.
 */
class TaylorAndLimitFixesTest {
    private fun typed(text: String): String {
        val t = TextInput.read(text)
        val c = MathCalc.calculate(t.latex, t.choice)
        val o = c.outcome
        return if (o is Outcome.Expression) c.textResult(o) else answerOf(c)
    }

    /** sin x/x at ∞ is 0 exactly (a bounded factor times one tending to 0), never −4.9·10⁻¹¹. */
    @Test fun boundedTimesVanishing() {
        assertEquals("0", typed("limit(sin(x)/x, x, infinity)"))
        assertEquals("0", answer("\\lim_{x\\to 0}x^{2}\\sin\\left(\\frac{1}{x}\\right)"))
        assertEquals("1", answer("\\lim_{x\\to\\infty}\\left(1+\\frac{\\cos(x)}{x}\\right)"))
    }

    /**
     * A limit the engine cannot find exactly is declined, never given from values near the point: x^(x^x) at 0⁺ is 0, and the
     * values (rounding near 0) once said 1.000000053·10⁻¹⁰.
     */
    @Test fun noDecimalFromValues() {
        val got = answer("\\lim_{x\\to 0^{+}}x^{x^{x}}")
        assert(got == "0" || got == "NO_LIMIT") { got }
    }

    /** The Taylor polynomial of a polynomial at another point: x³ − 2x = −1 + (x − 1) + 3(x − 1)² + (x − 1)³ (multiplied back below). */
    @Test fun polynomialAtAPoint() {
        assertEquals("-1 + (x - 1) + 3(x - 1)^2 + (x - 1)^3 + O((x - 1)^7)", typed("taylor(x^3 - 2x, x, 1)"))
        val x = Expr.Sym("x")
        val back = Algebra.expand(add(num(-1), add(x, num(-1)), mul(num(3), pow(add(x, num(-1)), Expr.TWO)), pow(add(x, num(-1)), num(3))))
        assertEquals(Algebra.expand(add(pow(x, num(3)), mul(num(-2), x))), back)
    }

    /** The binomial series: (1 + x)^a = 1 + a x + a(a − 1)/2 x² + a(a − 1)(a − 2)/6 x³ + O(x⁴), coefficient by coefficient. */
    @Test fun binomialSeries() {
        val t = TextInput.read("taylor((1 + x)^a, x, 0, 3)")
        val o = MathCalc.calculate(t.latex, t.choice).outcome as Outcome.Expression
        val a = Expr.Sym("a")
        val expected = listOf(Expr.ONE, a, mul(a, add(a, num(-1)), Expr.HALF), mul(a, add(a, num(-1)), add(a, num(-2)), num(Rational.ONE / Rational.of(6))))
        val got = o.series!!
        assertEquals(listOf(0, 1, 2, 3), got.map { it.second })
        for ((k, pair) in got.withIndex()) assert(Verify.same(pair.first, expected[k])) { "x^$k: ${pair.first}" }
    }

    /** At a pole the Laurent series, with its remainder: 1/x is its own one-term Laurent series, 1/x + O(x⁷). */
    @Test fun laurentAtAPole() {
        assertEquals("1/x + O(x^7)", typed("taylor(1/x, x, 0)"))
        assertEquals("1/x + (1/6)x + (7/360)x^3 + O(x^5)", typed("taylor(1/sin(x), x, 0, 4)"))
    }
}
