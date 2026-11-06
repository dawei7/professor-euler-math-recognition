package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * The integration methods of 6 November 2026, each checked against an antiderivative from a table of integrals (written here
 * by hand, independently of the engine): the engine's minus the table's must be one constant at points of one interval where
 * both are defined. And the root sum against the real textbook form, a definite integral now exact, and limits that were
 * wrong before.
 */
class IntegrationMethodsTest {
    private val x = Expr.Sym("x")

    private fun read(latex: String): Expr = Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList()).expression()

    private fun antiderivative(latex: String): Expr =
        (MathCalc.calculate(latex, Choice(Mode.INTEGRAL, "x")).outcome as? Outcome.Expression)?.result ?: error("no antiderivative of $latex")

    /** F − G is one constant at the [points] where both are defined (at least three). */
    private fun sameUpToConstant(f: Expr, g: Expr, points: List<Double>): Boolean {
        val differences = points.mapNotNull { p ->
            val a = Numeric.eval(f, mapOf("x" to p))
            val b = Numeric.eval(g, mapOf("x" to p))
            if (a.isFinite() && b.isFinite()) a - b else null
        }
        return differences.size >= 3 && differences.all { abs(it - differences[0]) <= 1e-9 * max(1.0, abs(differences[0])) }
    }

    private val near = listOf(0.3, 0.7, 1.1, -0.5, 2.0, -1.3, 2.7)
    private val aboveOne = listOf(1.2, 1.7, 2.5, 3.9, 6.1)
    private val inside = listOf(0.1, 0.35, -0.6, 0.8, -0.25)

    @Test fun againstTheTables() {
        val cases = listOf(
            // Rioboo's arctangents and the logarithms over Q(√2).
            Triple("\\frac{1}{x^{4}+1}", "\\frac{1}{4\\sqrt{2}}\\ln\\left(\\frac{x^{2}+\\sqrt{2}x+1}{x^{2}-\\sqrt{2}x+1}\\right)+\\frac{1}{2\\sqrt{2}}\\left(\\arctan(\\sqrt{2}x+1)+\\arctan(\\sqrt{2}x-1)\\right)", near),
            // Hermite's rational part.
            Triple("\\frac{1}{(x^{2}+1)^{2}}", "\\frac{x}{2(x^{2}+1)}+\\frac{\\arctan(x)}{2}", near),
            Triple("\\frac{1}{x^{6}+1}", "\\frac{1}{3}\\arctan(x)+\\frac{1}{6}\\arctan(2x+\\sqrt{3})+\\frac{1}{6}\\arctan(2x-\\sqrt{3})+\\frac{\\sqrt{3}}{12}\\ln\\left(\\frac{x^{2}+\\sqrt{3}x+1}{x^{2}-\\sqrt{3}x+1}\\right)", near),
            // Risch–Norman.
            Triple("xe^{x}\\sin(x)", "\\frac{e^{x}}{2}(x\\sin(x)-x\\cos(x)+\\cos(x))", near),
            Triple("\\ln(x)^{2}", "x\\ln(x)^{2}-2x\\ln(x)+2x", aboveOne),
            Triple("e^{x}\\left(\\frac{1}{x}-\\frac{1}{x^{2}}\\right)", "\\frac{e^{x}}{x}", aboveOne),
            Triple("e^{x+e^{x}}", "e^{e^{x}}", near),
            Triple("\\frac{x}{1+\\cos(x)}", "x\\tan\\left(\\frac{x}{2}\\right)+2\\ln\\left(\\cos\\left(\\frac{x}{2}\\right)\\right)", inside),
            // Bioche's t = tan x and Weierstrass's t = tan(x/2).
            Triple("\\tan(x)\\sec(x)^{2}", "\\frac{\\tan(x)^{2}}{2}", inside),
            Triple("\\frac{1}{2+\\cos(x)}", "\\frac{2}{\\sqrt{3}}\\arctan\\left(\\frac{\\tan(\\frac{x}{2})}{\\sqrt{3}}\\right)", near.filter { abs(it) < 3 }),
            // t = √x, Euler's substitutions, the conjugate.
            Triple("\\frac{\\sqrt{x}}{1+x}", "2\\sqrt{x}-2\\arctan(\\sqrt{x})", aboveOne),
            Triple("\\frac{1}{x\\sqrt{x^{2}-1}}", "\\arctan(\\sqrt{x^{2}-1})", aboveOne),
            Triple("\\frac{1}{\\sqrt{x+1}+\\sqrt{x}}", "\\frac{2}{3}(x+1)^{\\frac{3}{2}}-\\frac{2}{3}x^{\\frac{3}{2}}", aboveOne),
            // x = sin u for arcsin, hyperbolic functions as exponentials.
            Triple("\\arcsin(x)^{2}", "x\\arcsin(x)^{2}+2\\sqrt{1-x^{2}}\\arcsin(x)-2x", inside),
            Triple("\\frac{1}{\\cosh(x)}", "2\\arctan(e^{x})", near),
            Triple("\\tanh(x)", "\\ln(\\cosh(x))", near),
        )
        val wrong = cases.mapNotNull { (integrand, table, points) ->
            val f = antiderivative(integrand)
            if (sameUpToConstant(f, read(table), points)) null else "$integrand: $f"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /**
     * ∫ 1/(x³ − 2) dx as a sum over the roots of α³ − 1/108: its real part differs from the textbook form
     * (ln|x − c| − ln(x² + cx + c²)/2 − √3·arctan((2x + c)/(c√3)))/(3c²), c = ∛2, by one constant.
     */
    @Test fun rootSumAgainstTheTable() {
        val o = MathCalc.calculate("\\frac{1}{x^{3}-2}", Choice(Mode.INTEGRAL, "x")).outcome as Outcome.RootSum
        assertEquals(1, o.sums.size)
        val (q, s) = o.sums[0]
        val c = Math.cbrt(2.0)
        fun table(t: Double) = (kotlin.math.ln(abs(t - c)) - kotlin.math.ln(t * t + c * t + c * c) / 2 -
            kotlin.math.sqrt(3.0) * kotlin.math.atan((2 * t + c) / (c * kotlin.math.sqrt(3.0)))) / (3 * c * c)
        // The roots of q in doubles: α³ = 1/108 (q is checked to be that).
        assertTrue(Verify.same(q.substitute(Expr.Sym(o.root), x), add(pow(x, num(3)), num(Rational.of(-1) / Rational.of(108)))))
        val r = Math.cbrt(1.0 / 108)
        val roots = listOf(Cx(r), Cx(-r / 2, r * kotlin.math.sqrt(3.0) / 2), Cx(-r / 2, -r * kotlin.math.sqrt(3.0) / 2))
        fun sum(t: Double): Double {
            var total = Cx(0.0)
            // S(α, t) = t − 6α (as the engine wrote it, asserted below) at each root.
            for (alpha in roots) total += alpha * (Cx(t) - alpha * Cx(6.0)).ln()
            return total.re
        }
        assertEquals(add(x, mul(num(-6), Expr.Sym(o.root))), s)
        val differences = listOf(-3.0, -0.5, 0.4, 1.0, 2.5, 4.0).map { sum(it) - table(it) }
        assertTrue(differences.toString(), differences.all { abs(it - differences[0]) < 1e-12 })
    }

    /**
     * ∫₀¹ 1/(1 + x⁴) dx = (π + 2 ln(1 + √2))/(4√2) ≈ 0.8669729873, exactly from the antiderivative (the Calculator shows its
     * decimal, arctan(√2 ± 1) being no form it calls exact enough to show).
     */
    @Test fun definiteIntegralExact() {
        val d = Budget.within(3000) { Calculus.definite(read("\\frac{1}{x^{4}+1}"), x, Expr.ZERO, Expr.ONE) }
        val exact = d.exact ?: error("no exact value")
        assertTrue(!exact.hasDecimal())
        val known = (Math.PI + 2 * kotlin.math.ln(1 + kotlin.math.sqrt(2.0))) / (4 * kotlin.math.sqrt(2.0))
        assertEquals(known, Numeric.value(exact)!!, 1e-12)
    }

    /** Limits the engine once gave wrong (∞ from overflowing values), exact now, each value known. */
    @Test fun limitsOnceWrong() {
        fun value(latex: String): Double? {
            val o = MathCalc.calculate(latex).outcome
            val e = (o as? Outcome.Value)?.let { it.exact ?: num(it.value.rational ?: return null) } ?: (o as? Outcome.Expression)?.result ?: return null
            if (e.hasDecimal()) return null
            return if (e == Expr.INF) Double.POSITIVE_INFINITY else Numeric.value(e)
        }
        assertEquals(Math.E, value("\\lim_{x\\to\\infty}\\left(e^{x}+x\\right)^{\\frac{1}{x}}")!!, 1e-12)
        assertEquals(3.0, value("\\lim_{x\\to\\infty}\\left(2^{x}+3^{x}\\right)^{\\frac{1}{x}}")!!, 1e-12)
        assertEquals(0.0, value("\\lim_{x\\to\\infty}\\left(\\ln(e^{x}+x)-x\\right)")!!, 1e-12)
        assertEquals(kotlin.math.ln(2.0), value("\\lim_{x\\to\\infty}\\left(x-\\ln(\\cosh(x))\\right)")!!, 1e-12)
        assertEquals(-1.0, value("\\lim_{x\\to\\infty}\\frac{e^{\\frac{1}{x}-e^{-x}}-e^{\\frac{1}{x}}}{e^{-x}}")!!, 1e-12)
        assertEquals(kotlin.math.exp(-0.5), value("\\lim_{x\\to\\infty}\\left(1+\\frac{1}{x}\\right)^{x^{2}}e^{-x}")!!, 1e-12)
        assertEquals(1.0, value("\\lim_{x\\to\\infty}\\frac{\\ln(1+e^{x})}{x}")!!, 1e-12)
        // A limit the engine cannot confirm by values (it settles too slowly) is declined, never shown.
        assertEquals("NO_LIMIT", answer("\\lim_{x\\to\\infty}\\frac{\\ln(\\ln(x+e^{\\ln(x)\\ln(\\ln(x))}))}{\\ln(\\ln(\\ln(e^{x}+x+\\ln(x))))}"))
    }

    /** No answer from the further methods that does not differentiate back: integrands with no elementary antiderivative stay declined. */
    @Test fun nonElementaryDeclined() {
        for (latex in listOf("e^{x^{2}}", "\\frac{\\ln(x)}{x+1}", "\\frac{x\\arctan(x)}{1+x^{2}}")) {
            assertEquals(latex, "NO_ANTIDERIVATIVE", answer(latex, Choice(Mode.INTEGRAL, "x")))
        }
        // eˣ/x and sin x/x have no elementary antiderivative, but the exponential and sine integrals are the calculator's own.
        assertEquals("Ei(x) + C", answer("\\frac{e^{x}}{x}", Choice(Mode.INTEGRAL, "x")))
        assertEquals("Si(x) + C", answer("\\frac{\\sin(x)}{x}", Choice(Mode.INTEGRAL, "x")))
    }
}
