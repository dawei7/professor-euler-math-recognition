package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 3 of the Calculator: exact roots of cubics and quartics, certain real roots, factors over ℝ and ℂ, partial
 * fractions, trigonometric simplification, polynomial systems, graphs with sliders and regions.
 */
class AlgebraFunctionsTest {
    private fun factored(latex: String): Outcome.Factored =
        Domain.complex { MathCalc.calculate(latex, Mode.FACTOR) }.outcome as Outcome.Factored

    private fun text(e: Expr?) = e?.let { Format.expr(it, Style.PLAIN, latex = false) }

    @Test
    fun cubicsAndQuarticsExactly() {
        assertEquals("x = ∛2 ≈ 1.25992105", answer("x^{3}-2=0"))
        // Cardano: one real root, as real cube roots.
        assertEquals("x = ∛(-1/2 + √93/18) - ∛(1/2 + √93/18) ≈ -0.6823278038", answer("x^{3}+x+1=0"))
        // Casus irreducibilis: three real roots no radicals of real numbers write; their cosines do.
        assertEquals("x = 2·cos(10π/9) ≈ -1.879385242; 2·cos(4π/9) ≈ 0.3472963553; 2·cos(2π/9) ≈ 1.532088886", answer("x^{3}-3x+1=0"))
        // A biquadratic with its nested roots taken apart: √(5 + 2√6) = √2 + √3.
        assertEquals("x = -√2 - √3 ≈ -3.14626437; √2 - √3 ≈ -0.3178372452; √3 - √2 ≈ 0.3178372452; √2 + √3 ≈ 3.14626437",
            answer("x^{4}-10x^{2}+1=0"))
        // Rational roots stay as they were found.
        assertEquals("x = 1; 2; 3", answer("x^{3}-6x^{2}+11x-6=0"))
    }

    @Test
    fun complexNumbersReadRealPartFirst() {
        val out = answer("x^{4}+x+1=0")
        assertTrue(out, out.contains("-0.7271360845 + 0.4300142883i"))
    }

    @Test
    fun realRootsAreNeverMissed() {
        // (x² − 2)(x² − 2 − 10⁻⁸): four roots, two pairs 10⁻⁸/(2√2) apart.
        val e = Rational.ONE / Rational.of(100_000_000)
        val a = Rational.of(-2)
        val b = Rational.of(-2) - e
        val c = listOf(a * b, Rational.ZERO, a + b, Rational.ZERO, Rational.ONE)
        val roots = Sturm.realRoots(c)
        assertEquals(4, roots.size)
        assertEquals(Math.sqrt(2.0), roots[2], 1e-12)
        assertEquals(Math.sqrt(2.0 + 1e-8), roots[3], 1e-12)
        assertTrue(roots[3] > roots[2])
        assertEquals(listOf(1.1673039782614187), Sturm.realRoots(listOf(Rational.of(-1), Rational.of(-1), Rational.ZERO, Rational.ZERO, Rational.ZERO, Rational.ONE)))
    }

    @Test
    fun factorsOverTheRealsAndTheComplexNumbers() {
        assertEquals("(x - √2)(x + √2)", text(factored("x^{2}-2").overReals))
        assertEquals("(x + i)(x - i)", text(factored("x^{2}+1").overComplex))
        assertEquals(null, factored("x^{2}+1").overReals)
        assertEquals("(x - √2)(x + √2)(x^2 + 2)", text(factored("x^{4}-4").overReals))
        assertEquals("2(x - √3)(x + √3)", text(factored("2x^{2}-6").overReals))
        assertEquals("(x - √3)^2(x + √3)^2", text(factored("(x^{2}-3)^{2}").overReals))
        // Fully factored over the rationals: nothing further.
        assertEquals(null, factored("x^{2}-1").overReals)
        // Without complex numbers, nothing over ℂ.
        assertEquals(null, (MathCalc.calculate("x^{2}+1", Mode.FACTOR).outcome as Outcome.Factored).overComplex)
    }

    @Test
    fun partialFractions() {
        val p = Choice(Mode.PARTIAL_FRACTIONS)
        assertEquals("1/(2(x - 1)) - 1/(2(x + 1))", answer("\\frac{1}{x^{2}-1}", p))
        assertEquals("4/(x - 3) - 3/(x - 2)", answer("\\frac{x+1}{x^{2}-5x+6}", p))
        assertEquals("x + 1/(2(x - 1)) + 1/(2(x + 1))", answer("\\frac{x^{3}}{x^{2}-1}", p))
        assertEquals("-x/(x^2 + 1) + 1/x", answer("\\frac{1}{x^{3}+x}", p))
        // Offered where it says something; not for a polynomial, nor a fraction with one factor below.
        assertTrue(MathCalc.calculate("\\frac{1}{x^{2}-1}").choices.contains(p))
        assertFalse(MathCalc.calculate("x^{2}-1").choices.contains(p))
        assertFalse(MathCalc.calculate("\\frac{1}{x+1}").choices.contains(p))
        // And as its function.
        assertEquals("1/(2(x - 1)) - 1/(2(x + 1))", answerOf(Reports.of(TextInput.read("partialfractions(1/(x^2-1))")).result))
    }

    @Test
    fun trigonometricForms() {
        val s = Choice(Mode.SIMPLIFY)
        assertEquals("2·cos(x)", answer("\\frac{\\sin(2x)}{\\sin(x)}", s))
        assertEquals("1", answer("\\cos(2x)+2\\sin(x)^{2}", s))
        assertEquals("-cos(2x)", answer("\\sin(x)^{4}-\\cos(x)^{4}", s))
        assertEquals("sin(y)cos(x)", answer("\\sin(x+y)-\\sin(x)\\cos(y)", s))
        assertEquals("sin(x)", answer("\\tan(x)\\cos(x)", s))
        // What is simplest already stays.
        assertEquals("sin(x)", answer("\\sin(x)", s))
    }

    @Test
    fun inverseFunctions() {
        fun inverse(latex: String): String? {
            val e = Parser(Regex("""\\[a-zA-Z]+|\\.|\S""").findAll(latex).map { it.value }.toList()).statements().single().sides.single()!!
            return text(Budget.within(20_000) { Analysis.of(e, Expr.Sym("x")) }!!.inverse)
        }
        assertEquals("(1/2)x - 1/2", inverse("2x+1"))
        assertEquals("ln(x)", inverse("e^{x}"))
        assertEquals("∛(x - 1)", inverse("x^{3}+1"))
        // Not one-to-one on its domain: none.
        assertEquals(null, inverse("x^{2}"))
        assertEquals(null, inverse("\\sin(x)"))
    }

    @Test
    fun parametricAndPolarCurves() {
        // x = cos t, y = sin t: the unit circle, drawn first; the system still solvable.
        val c = MathCalc.calculate("\\begin{cases}x=\\cos(t)\\\\y=\\sin(t)\\end{cases}")
        val g = c.outcome as Outcome.Graph
        assertEquals("t", g.variable)
        assertTrue(c.choices.contains(Choice(Mode.SOLVE)))
        val p = Plot.prepare(g)
        assertTrue(p.implicit)
        val points = p.sample(p.window, 400).curves.single().pieces.flatMap { it.toList() }.chunked(2)
        assertTrue(points.size > 100)
        assertTrue(points.all { (x, y) -> kotlin.math.abs(x * x + y * y - 1) < 1e-9 })
        // r = 1 + cos θ: a cardioid, as (r cos θ, r sin θ); it reaches x = 2 and the origin.
        val polar = MathCalc.calculate("r=1+\\cos(\\theta)").outcome as Outcome.Graph
        val q = Plot.prepare(polar)
        val xs = q.sample(q.window, 400).curves.single().pieces.flatMap { piece -> piece.toList().filterIndexed { i, _ -> i % 2 == 0 } }
        assertEquals(2.0, xs.max(), 1e-6)
        assertEquals(-0.25, xs.min(), 1e-3)
    }

    @Test
    fun aSineAndACosineAsOne() {
        // a sin x + b cos x = c as √(a² + b²)·sin(x + φ) = c: every solution, in families.
        assertEquals("x = π/4 + kπ (k)", answer("\\cos(x)-\\sin(x)=0"))
        assertEquals("x = 2kπ; π/2 + 2kπ (k)", answer("\\sin(x)+\\cos(x)=1"))
        // And so the curve sketch of sin x + cos x: period 2π, extrema ±√2.
        val e = Parser(Regex("""\\[a-zA-Z]+|\\.|\S""").findAll("\\sin(x)+\\cos(x)").map { it.value }.toList()).statements().single().sides.single()!!
        val f = Budget.within(20_000) { Analysis.of(e, Expr.Sym("x")) }!!
        assertEquals("2π", text(f.period))
        assertEquals("-√2 ≤ y ≤ √2", Format.intervals(f.range!!, Style.PLAIN, latex = false))
    }

    @Test
    fun polynomialSystems() {
        assertEquals("x = -2, y = -1; x = -1, y = -2; x = 1, y = 2; x = 2, y = 1", answer("\\begin{cases}xy=2\\\\x^{2}+y^{2}=5\\end{cases}"))
        assertEquals("x = 1, y = 2, z = 3; x = 1, y = 3, z = 2; x = 2, y = 1, z = 3; x = 2, y = 3, z = 1; x = 3, y = 1, z = 2; x = 3, y = 2, z = 1",
            answer("\\begin{cases}x+y+z=6\\\\xy+yz+zx=11\\\\xyz=6\\end{cases}"))
        // No solution: 1 in the basis.
        assertEquals("no solution", answer("\\begin{cases}x^{2}+y^{2}=1\\\\x^{2}+y^{2}=4\\end{cases}"))
    }

    @Test
    fun graphsWithSlidersAndRegions() {
        val g = MathCalc.calculate("y=ax^{2}+bx+c").outcome as Outcome.Graph
        assertEquals(listOf("a", "b", "c"), g.parameters)
        val at = Plot.at(g, mapOf("a" to 2.0, "b" to 0.0, "c" to -8.0))
        assertEquals("2x^2 - 8", text(at.functions.single()))
        // Prepared with every parameter at 1 until a slider moves; numeric marks only, fast, while it does.
        assertTrue(Plot.prepare(g).marks.isNotEmpty())
        Plot.prepare(at, exact = false)
        // A region: where y > x² holds.
        val r = MathCalc.calculate("y>x^{2}").outcome as Outcome.Graph
        assertEquals(Rel.GT, r.region)
        val p = Plot.prepare(r)
        assertTrue(p.holds(0.0, 1.0))
        assertFalse(p.holds(0.0, -1.0))
        assertFalse(p.holds(2.0, 3.0))
        // An expression in several letters is a graph in its main letter with the others as sliders.
        assertTrue(MathCalc.calculate("ax^{2}+b").choices.contains(Choice(Mode.GRAPH, "x")))
    }
}
