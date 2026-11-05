package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Curve sketches a teacher sets (Kurvendiskussion), each property as the textbook has it. */
class AnalysisTest {
    private val token = Regex("""\\[a-zA-Z]+|\\.|\S""")

    private fun facts(latex: String): FunctionFacts {
        val e = Parser(token.findAll(latex).map { it.value }.toList()).statements().single().sides.single()!!
        return Budget.within(20_000) { Analysis.of(e, Expr.Sym("x")) } ?: error("no facts for $latex")
    }

    private fun t(e: Expr?): String = e?.let { Format.expr(it, Style.PLAIN, latex = false) } ?: "-"

    private fun intervals(list: List<Interval>?): String = list?.joinToString(" U ") { i ->
        (if (i.lowerClosed) "[" else "(") + (i.lower?.let(::t) ?: "-inf") + ", " + (i.upper?.let(::t) ?: "inf") + (if (i.upperClosed) "]" else ")")
    } ?: "?"

    private fun extrema(f: FunctionFacts) = f.extrema?.joinToString("; ") { (if (it.maximum) "max" else "min") + " (${t(it.x)}, ${t(it.y)})" } ?: "?"
    private fun inflections(f: FunctionFacts) = f.inflections?.joinToString("; ") { "(${t(it.x)}, ${t(it.y)})" } ?: "?"
    private fun asymptotes(f: FunctionFacts) = f.asymptotes?.joinToString("; ") {
        when (it) {
            is Asymptote.Vertical -> "x = ${t(it.at)}"
            is Asymptote.Line -> (if (it.side < 0) "-inf: " else "inf: ") + "y = ${t(it.line)}"
        }
    } ?: "?"
    private fun zeros(f: FunctionFacts) = f.zeros?.values?.joinToString("; ") { t(it) } ?: "?"

    @Test
    fun aCubic() {
        val f = facts("x^{3}-3x")
        assertEquals("(-inf, inf)", intervals(f.domain.intervals))
        assertEquals("-√3; 0; √3", zeros(f))
        assertEquals("0", t(f.yIntercept))
        assertEquals(Symmetry.ODD, f.symmetry)
        assertEquals("max (-1, 2); min (1, -2)", extrema(f))
        assertEquals("(0, 0)", inflections(f))
        assertEquals("(-inf, -1) U (1, inf)", intervals(f.increasing))
        assertEquals("(-1, 1)", intervals(f.decreasing))
        assertEquals("(0, inf)", intervals(f.convex))
        assertEquals("(-inf, 0)", intervals(f.concave))
        assertEquals("", asymptotes(f))
        assertEquals(neg(Expr.INF), f.limits.first)
        assertEquals(Expr.INF, f.limits.second)
    }

    @Test
    fun aHyperbola() {
        val f = facts("\\frac{1}{x-1}")
        assertEquals("(-inf, 1) U (1, inf)", intervals(f.domain.intervals))
        assertEquals("", zeros(f))
        assertEquals("-1", t(f.yIntercept))
        assertEquals(Symmetry.NEITHER, f.symmetry)
        assertEquals("", extrema(f))
        assertEquals("(-inf, 1) U (1, inf)", intervals(f.decreasing))
        assertEquals("x = 1; -inf: y = 0; inf: y = 0", asymptotes(f))
    }

    @Test
    fun anObliqueAsymptote() {
        val f = facts("\\frac{x^{2}+1}{x}")
        assertEquals(Symmetry.ODD, f.symmetry)
        assertEquals("max (-1, -2); min (1, 2)", extrema(f))
        assertEquals("x = 0; -inf: y = x; inf: y = x", asymptotes(f))
        assertNull(f.yIntercept)
    }

    @Test
    fun theLogarithm() {
        val f = facts("\\ln(x)")
        assertEquals("(0, inf)", intervals(f.domain.intervals))
        assertEquals("1", zeros(f))
        assertNull(f.yIntercept)
        assertEquals(Symmetry.NEITHER, f.symmetry)
        assertEquals("(0, inf)", intervals(f.increasing))
        assertEquals("(0, inf)", intervals(f.concave))
        assertEquals("x = 0", asymptotes(f))
        assertEquals(Expr.INF, f.limits.second)
        assertNull(f.limits.first)
    }

    @Test
    fun aGrowthAndDecay() {
        val f = facts("xe^{-x}")
        assertEquals("max (1, 1/e)", extrema(f).replace("e^(-1)", "1/e"))
        assertEquals(1, f.inflections!!.size)
        assertEquals("2", t(f.inflections!!.single().x))
        assertEquals("inf: y = 0", asymptotes(f))
        assertEquals(neg(Expr.INF), f.limits.first)
    }

    @Test
    fun aSemicircle() {
        val f = facts("\\sqrt{4-x^{2}}")
        assertEquals("[-2, 2]", intervals(f.domain.intervals))
        assertEquals("-2; 2", zeros(f))
        assertEquals(Symmetry.EVEN, f.symmetry)
        assertEquals("max (0, 2)", extrema(f))
        assertEquals("(-2, 0)", intervals(f.increasing))
        assertEquals("(0, 2)", intervals(f.decreasing))
    }

    @Test
    fun aHoleIsNoAsymptote() {
        val f = facts("\\frac{x^{2}-4}{x-2}")
        assertEquals("(-inf, 2) U (2, inf)", intervals(f.domain.intervals))
        assertEquals("-2", zeros(f))
        assertTrue(f.asymptotes!!.none { it is Asymptote.Vertical })
    }

    @Test
    fun periodicFunctionsKeepWhatCanBeSettled() {
        val sine = facts("\\sin(x)")
        assertEquals(Symmetry.ODD, sine.symmetry)
        assertTrue(sine.zeros!!.periodic.isNotEmpty())
        // Extrema in families are not listed (yet); never a wrong finite list.
        assertNull(sine.extrema)
        val tangent = facts("\\tan(x)")
        assertTrue(tangent.domainFamilies.isNotEmpty())
        assertEquals(Symmetry.ODD, tangent.symmetry)
        assertNull(tangent.increasing)
    }

    /** What the sketches found wanting in the solver and the limits, as Calculate's own answers. */
    @Test
    fun theSolverAndTheLimitsBehind() {
        // A factor that is never 0 is divided out: the numbers alone saw "zeros" all along e^(−x)'s tail.
        assertEquals("x = 1", answer("-xe^{-x}+e^{-x}=0"))
        assertEquals("x = 0", answer("x^{2}e^{x}=0"))
        // ln u and eᵘ by where u goes; ∞/∞ by L'Hôpital in x itself.
        assertEquals("-∞", answer("\\lim_{x\\to 0^{+}}\\ln(x)"))
        assertEquals("0", answer("\\lim_{x\\to\\infty}xe^{-x}"))
        assertEquals("0", answer("\\lim_{x\\to\\infty}\\frac{x^{3}}{e^{x}}"))
        assertEquals("∞", answer("\\lim_{x\\to\\infty}\\frac{e^{x}}{x^{2}}"))
    }

    @Test
    fun theReportCarriesTheSketch() {
        val r = Reports.of("x^{3}-3x")
        val panel = r.panels.single { it.kind == PanelKind.FUNCTION }
        val f = (Reports.compute(r, panel) as PanelResult.Function).facts
        assertEquals("max (-1, 2); min (1, -2)", extrema(f))
        // An equation has no curve sketch, nor has an expression in two letters.
        assertTrue(Reports.of("x^{2}=4").panels.none { it.kind == PanelKind.FUNCTION })
        assertTrue(Reports.of("x+y").panels.none { it.kind == PanelKind.FUNCTION })
    }

    @Test
    fun anEquationOrTwoLettersHaveNoSketch() {
        val e = Parser(token.findAll("x+y").map { it.value }.toList()).statements().single().sides.single()!!
        assertNull(Analysis.of(e, Expr.Sym("x")))
        assertNotNull(facts("x^{2}").derivative)
    }
}
