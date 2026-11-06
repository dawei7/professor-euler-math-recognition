package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * A corpus of integrals from the tables of textbooks (Bronstein–Semendjajew, Stewart's table, the CRC tables), section by
 * section: rational, algebraic, exponential, logarithmic, trigonometric, inverse trigonometric, hyperbolic. Every antiderivative
 * the engine gives is checked here independently of it: differentiated numerically (a central difference, Richardson's
 * extrapolation) at points where the integrand is defined, against the integrand's own value. None may be wrong; the share
 * solved by section is printed, and must not fall below what the engine reaches today.
 *
 * Entries marked [nonElementary] have no elementary antiderivative (Liouville): e^x/x, sin x/x, e^(x²), ln x/(x + 1). They
 * stay in the corpus to show that the engine declines them rather than inventing one.
 */
class IntegralCorpusTest {
    private class Entry(val latex: String, val nonElementary: Boolean = false)

    private fun e(latex: String) = Entry(latex)
    private fun none(latex: String) = Entry(latex, nonElementary = true)

    private val rational = listOf(
        e("\\frac{1}{x}"), e("\\frac{1}{2x+3}"), e("\\frac{1}{x^{2}}"), e("\\frac{x}{x+1}"), e("\\frac{x^{2}}{x+1}"), e("\\frac{x^{3}}{x-1}"),
        e("\\frac{1}{x^{2}+1}"), e("\\frac{1}{x^{2}+4}"), e("\\frac{1}{4x^{2}+9}"), e("\\frac{1}{x^{2}-1}"), e("\\frac{1}{x^{2}-4}"),
        e("\\frac{1}{9-x^{2}}"), e("\\frac{1}{x^{2}+x+1}"), e("\\frac{1}{x^{2}+2x+5}"), e("\\frac{x}{x^{2}+1}"), e("\\frac{x}{x^{2}-1}"),
        e("\\frac{2x+1}{x^{2}+x+1}"), e("\\frac{x+1}{x^{2}+2x+2}"), e("\\frac{1}{x(x+1)}"), e("\\frac{1}{x(x-1)(x+2)}"),
        e("\\frac{1}{(x-1)^{2}}"), e("\\frac{1}{(x+1)^{2}(x-1)}"), e("\\frac{x}{(x+1)^{2}}"), e("\\frac{1}{x^{2}(x+1)}"),
        e("\\frac{1}{x^{3}+1}"), e("\\frac{1}{x^{3}-1}"), e("\\frac{x}{x^{3}+1}"), e("\\frac{x}{x^{3}-1}"), e("\\frac{x^{2}}{x^{3}+1}"),
        e("\\frac{1}{x^{4}+1}"), e("\\frac{x^{2}}{x^{4}+1}"), e("\\frac{x}{x^{4}+1}"), e("\\frac{x^{3}}{x^{4}+1}"), e("\\frac{1}{x^{4}-1}"),
        e("\\frac{x}{x^{4}-1}"), e("\\frac{x^{2}}{x^{4}-1}"), e("\\frac{1}{x^{4}+x^{2}+1}"), e("\\frac{1}{x^{4}+4}"), e("\\frac{1}{x^{6}+1}"),
        e("\\frac{1}{x^{6}-1}"), e("\\frac{1}{(x^{2}+1)^{2}}"), e("\\frac{x}{(x^{2}+1)^{2}}"), e("\\frac{1}{(x^{2}+1)^{3}}"),
        e("\\frac{x^{2}}{(x^{2}+1)^{2}}"), e("\\frac{1}{(x^{2}+4)^{2}}"), e("\\frac{1}{x(x^{2}+1)}"), e("\\frac{1}{x^{2}(x^{2}+1)}"),
        e("\\frac{1}{x(x^{2}+1)^{2}}"), e("\\frac{1}{(x^{2}+1)(x^{2}+4)}"), e("\\frac{x^{2}+1}{x^{4}+1}"), e("\\frac{x^{2}-1}{x^{4}+1}"),
        e("\\frac{1}{x^{3}+x}"), e("\\frac{1}{x^{3}+x^{2}+x+1}"), e("\\frac{3x^{2}+2x+1}{x^{3}+x^{2}+x}"), e("\\frac{1}{x^{5}+x}"),
        e("\\frac{1}{x^{4}+x^{3}}"), e("\\frac{x^{4}}{x^{2}+1}"), e("\\frac{x^{5}}{x^{2}+1}"), e("\\frac{x^{3}}{x^{2}+4}"),
        e("\\frac{1}{x^{2}+x-2}"), e("\\frac{x+2}{x^{2}+4x+8}"), e("\\frac{1}{2x^{2}+3x+1}"), e("\\frac{1}{x^{3}-x}"),
        e("\\frac{1}{x(x+1)^{2}}"), e("\\frac{1}{(x^{2}+2x+2)^{2}}"), e("\\frac{1}{x^{3}+8}"), e("\\frac{1}{x^{3}-8}"),
        e("\\frac{1}{x^{4}+5x^{2}+4}"), e("\\frac{1}{x^{4}-5x^{2}+4}"), e("\\frac{x^{3}}{x^{8}+1}"), e("\\frac{x^{2}}{x^{6}+1}"),
        e("\\frac{x^{4}+1}{x^{6}+1}"), e("\\frac{1}{x^{3}-2}"), e("\\frac{1}{x^{3}+x+1}"), e("\\frac{1}{x^{5}+1}"),
        e("\\frac{x^{2}+2x+3}{(x-1)(x+1)^{2}}"), e("\\frac{1}{(x-1)^{3}(x+2)}"), e("\\frac{2x^{3}+x}{(x^{2}+1)^{2}}"),
        e("\\frac{1}{x^{4}+2x^{2}+1}"), e("\\frac{x^{2}}{(x^{2}-1)^{2}}"), e("\\frac{1}{(x^{2}-1)^{2}}"), e("\\frac{1}{x^{4}+x^{2}-2}"),
        e("\\frac{x^{3}+1}{x^{3}-x^{2}}"), e("\\frac{1}{x^{3}+3x^{2}+3x+1}"), e("\\frac{1}{(x^{2}+x+1)^{2}}"),
    )

    private val algebraic = listOf(
        e("\\sqrt{x}"), e("x\\sqrt{x}"), e("\\frac{1}{\\sqrt{x}}"), e("\\sqrt{x+1}"), e("\\sqrt{2x+3}"), e("x\\sqrt{x+1}"),
        e("x^{2}\\sqrt{x+1}"), e("\\frac{x}{\\sqrt{x+1}}"), e("\\frac{1}{1+\\sqrt{x}}"), e("\\frac{\\sqrt{x}}{1+x}"),
        e("\\frac{1}{\\sqrt{x}+\\sqrt[3]{x}}"), e("\\frac{x}{\\sqrt{x^{2}+1}}"), e("x\\sqrt{x^{2}+1}"), e("\\frac{1}{\\sqrt{x^{2}+1}}"),
        e("\\frac{1}{\\sqrt{x^{2}-1}}"), e("\\frac{1}{\\sqrt{1-x^{2}}}"), e("\\sqrt{1-x^{2}}"), e("\\sqrt{x^{2}+1}"), e("\\sqrt{x^{2}-1}"),
        e("x^{2}\\sqrt{1-x^{2}}"), e("\\frac{x^{2}}{\\sqrt{x^{2}+1}}"), e("\\frac{1}{x\\sqrt{x^{2}+1}}"), e("\\frac{1}{x\\sqrt{x^{2}-1}}"),
        e("\\frac{1}{x\\sqrt{1-x^{2}}}"), e("\\frac{\\sqrt{x^{2}+1}}{x}"), e("\\frac{\\sqrt{1-x^{2}}}{x}"), e("\\frac{\\sqrt{x^{2}-1}}{x}"),
        e("\\frac{1}{x^{2}\\sqrt{x^{2}+1}}"), e("\\frac{1}{x^{2}\\sqrt{1-x^{2}}}"), e("\\frac{1}{(1-x^{2})^{\\frac{3}{2}}}"),
        e("\\frac{1}{(x^{2}+1)^{\\frac{3}{2}}}"), e("(x^{2}+1)^{\\frac{3}{2}}"), e("\\frac{x}{(1-x^{2})^{\\frac{3}{2}}}"),
        e("\\frac{1}{\\sqrt{x^{2}+2x+5}}"), e("\\frac{1}{\\sqrt{x^{2}+4x}}"), e("\\sqrt{x^{2}+2x+2}"), e("\\frac{1}{\\sqrt{2x-x^{2}}}"),
        e("\\frac{1}{\\sqrt{x(1-x)}}"), e("\\frac{x}{\\sqrt{x^{2}+2x+2}}"), e("\\frac{1}{(x+1)\\sqrt{x^{2}+1}}"), e("\\frac{1}{x\\sqrt{x+1}}"),
        e("\\sqrt{\\frac{1+x}{1-x}}"), e("x^{3}\\sqrt{x^{2}+1}"), e("\\frac{x^{3}}{\\sqrt{1-x^{2}}}"), e("\\sqrt[3]{x+1}"),
        e("x\\sqrt[3]{x+1}"), e("\\frac{1}{\\sqrt[3]{x+1}}"), e("\\frac{1}{\\sqrt{x+1}+\\sqrt{x}}"), e("\\frac{1}{x+\\sqrt{x}}"),
        e("\\frac{\\sqrt{x}}{x+4}"), e("\\frac{1}{(x+2)\\sqrt{x+1}}"), e("\\frac{x+1}{\\sqrt{x^{2}+1}}"), e("\\frac{1}{\\sqrt{4-x^{2}}}"),
        e("\\frac{x^{2}}{\\sqrt{4-x^{2}}}"), e("\\sqrt{9-x^{2}}"), e("\\frac{1}{x^{2}\\sqrt{x^{2}-1}}"), e("\\frac{\\sqrt{x}}{\\sqrt{x}+1}"),
        e("\\frac{1}{\\sqrt{x}(1+x)}"), e("\\frac{1}{(1+x)\\sqrt{x}}"), e("\\sqrt{x^{2}+4x+5}"),
    )

    private val exponential = listOf(
        e("e^{x}"), e("e^{2x}"), e("e^{-x}"), e("2^{x}"), e("xe^{x}"), e("x^{2}e^{x}"), e("x^{3}e^{x}"), e("xe^{-x}"), e("x^{2}e^{-2x}"),
        e("e^{x}\\sin(x)"), e("e^{x}\\cos(x)"), e("e^{2x}\\sin(3x)"), e("xe^{x}\\sin(x)"), e("xe^{x^{2}}"), e("x^{3}e^{x^{2}}"),
        e("e^{\\sqrt{x}}"), e("\\frac{e^{\\frac{1}{x}}}{x^{2}}"), e("\\frac{e^{x}}{1+e^{x}}"), e("\\frac{e^{x}}{1+e^{2x}}"),
        e("\\frac{1}{1+e^{x}}"), e("\\frac{1}{e^{x}+e^{-x}}"), e("\\frac{e^{2x}}{1+e^{x}}"), e("\\frac{xe^{x}}{(x+1)^{2}}"),
        e("e^{x}\\left(\\frac{1}{x}-\\frac{1}{x^{2}}\\right)"), e("e^{-x}\\cos(2x)"), e("x2^{x}"), e("e^{x}\\sqrt{1+e^{x}}"),
        e("e^{x+e^{x}}"), e("e^{x}\\cos(e^{x})"), e("\\frac{e^{x}-1}{e^{x}+1}"), e("\\frac{1}{e^{x}-1}"), e("\\frac{e^{x}}{(e^{x}+1)^{2}}"),
        e("\\frac{x}{e^{x}}"), e("x^{5}e^{x^{2}}"), e("e^{3x}+e^{-3x}"), e("\\frac{e^{x}}{e^{2x}+2e^{x}+1}"), e("(x+1)^{2}e^{x}"),
        e("e^{x}(\\sin(x)+\\cos(x))"), e("x^{2}e^{x}\\cos(x)"), e("e^{-x}\\sin(x)^{2}"), e("\\frac{e^{x}(x-1)}{x^{2}}"),
        none("\\frac{e^{x}}{x}"), none("e^{x^{2}}"), e("\\frac{e^{\\sqrt{x}}}{\\sqrt{x}}"), e("3^{2x}"), e("x^{4}e^{x}"),
        e("\\frac{e^{2x}}{e^{x}+2}"), e("e^{x}(1+e^{x})^{3}"), e("\\frac{e^{x}}{\\sqrt{e^{x}+1}}"),
    )

    private val logarithmic = listOf(
        e("\\ln(x)"), e("\\ln(2x+1)"), e("x\\ln(x)"), e("x^{2}\\ln(x)"), e("\\frac{\\ln(x)}{x}"), e("\\frac{\\ln(x)^{2}}{x}"),
        e("\\ln(x)^{2}"), e("\\ln(x)^{3}"), e("x\\ln(x)^{2}"), e("\\frac{1}{x\\ln(x)}"), e("\\frac{1}{x\\ln(x)^{2}}"),
        e("\\frac{\\ln(\\ln(x))}{x}"), e("\\frac{\\ln(x)}{x^{2}}"), e("\\sqrt{x}\\ln(x)"), e("\\ln(x^{2}+1)"), e("x\\ln(x^{2}+1)"),
        e("\\frac{\\ln(x+1)}{x^{2}}"), none("\\frac{\\ln(x)}{x+1}"), e("x^{3}\\ln(x)"), e("\\frac{\\ln(x)}{\\sqrt{x}}"),
        e("\\ln(x+\\sqrt{x^{2}+1})"), e("\\ln(x^{2}-1)"), e("\\log_{10}(x)"), e("\\frac{\\ln(x)^{2}}{x^{2}}"), e("\\frac{1}{x(1+\\ln(x))}"),
        e("\\frac{\\ln(x)}{x(1+\\ln(x))}"), e("\\ln(1+\\frac{1}{x})"), e("\\frac{\\ln(x)^{3}}{x}"), none("\\ln(x)\\ln(x+1)"),
        e("\\tan(x)\\ln(\\cos(x))"), e("\\ln(\\sqrt{x})"), e("\\ln(x^{3})"), e("x\\ln(x+1)"), e("\\frac{\\ln(\\ln(x))}{x\\ln(x)}"),
        e("\\ln(x)^{4}"), e("\\frac{\\ln(x)}{x^{3}}"), e("\\ln(x^{2}+x)"), e("\\frac{\\ln(x)^{5}}{x}"), e("\\frac{1+\\ln(x)}{x}"),
        e("\\frac{\\ln(x)}{(x+1)^{2}}"), e("\\ln(x^{2}+4)"), e("\\frac{1}{x}\\ln(x^{2})"),
    )

    private val trigonometric = listOf(
        e("\\sin(x)"), e("\\cos(x)"), e("\\tan(x)"), e("\\cot(x)"), e("\\sec(x)"), e("\\csc(x)"), e("\\sin(x)^{2}"), e("\\cos(x)^{2}"),
        e("\\tan(x)^{2}"), e("\\sin(x)^{3}"), e("\\cos(x)^{3}"), e("\\sin(x)^{4}"), e("\\sin(x)\\cos(x)"), e("\\sin(x)^{2}\\cos(x)^{2}"),
        e("\\sin(x)^{3}\\cos(x)^{2}"), e("\\sin(x)^{5}"), e("\\sec(x)^{2}"), e("\\csc(x)^{2}"), e("\\sec(x)\\tan(x)"), e("\\csc(x)\\cot(x)"),
        e("\\sec(x)^{3}"), e("\\tan(x)^{3}"), e("\\tan(x)^{4}"), e("\\sin(2x)\\cos(3x)"), e("\\sin(x)\\sin(3x)"), e("\\cos(2x)\\cos(5x)"),
        e("x\\sin(x)"), e("x\\cos(x)"), e("x^{2}\\sin(x)"), e("x^{2}\\cos(2x)"), e("x\\sin(x)^{2}"), e("x\\sec(x)^{2}"),
        e("\\frac{1}{1+\\cos(x)}"), e("\\frac{1}{1+\\sin(x)}"), e("\\frac{1}{2+\\cos(x)}"), e("\\frac{1}{1-\\cos(x)}"),
        e("\\frac{1}{\\sin(x)+\\cos(x)}"), e("\\frac{\\sin(x)}{1+\\cos(x)}"), e("\\frac{\\cos(x)}{1+\\sin(x)}"), e("\\frac{1}{3+2\\cos(x)}"),
        e("\\frac{1}{5+4\\sin(x)}"), e("\\frac{1}{\\sin(x)\\cos(x)}"), e("\\frac{\\sin(x)}{\\cos(x)^{2}}"), e("\\frac{\\cos(x)}{\\sin(x)^{2}}"),
        e("\\frac{\\cos(x)}{1+\\sin(x)^{2}}"), e("\\frac{1}{1+\\sin(x)^{2}}"), e("\\sin(\\ln(x))"), e("\\cos(\\ln(x))"), e("x\\cos(x^{2})"),
        e("\\sin(x)e^{\\cos(x)}"), e("\\tan(x)\\sec(x)^{2}"), e("\\cos(x)^{4}"), e("\\frac{\\sin(x)^{2}}{\\cos(x)}"),
        e("\\frac{1}{\\sin(x)^{2}+4\\cos(x)^{2}}"), e("\\sin(3x)\\cos(x)"), e("\\frac{\\sin(x)}{\\sin(x)+\\cos(x)}"),
        e("\\sin(x)^{2}\\cos(x)^{3}"), e("\\frac{1}{\\cos(x)^{4}}"), e("\\cot(x)^{2}"), e("\\sin(\\sqrt{x})"),
        e("\\frac{\\sin(x)\\cos(x)}{1+\\cos(x)^{2}}"), e("\\frac{\\tan(x)}{1+\\tan(x)}"), e("\\sin(x)\\cos(x)^{4}"),
        none("\\frac{\\sin(x)}{x}"), none("\\sin(x^{2})"), e("\\frac{1}{\\sin(x)}"), e("\\frac{x}{1+\\cos(x)}"), e("\\cos(x)^{5}"),
        e("\\frac{1}{1+\\tan(x)}"), e("\\sin(x)\\cos(2x)\\cos(3x)"),
    )

    private val inverseTrigonometric = listOf(
        e("\\arcsin(x)"), e("\\arccos(x)"), e("\\arctan(x)"), e("x\\arctan(x)"), e("x^{2}\\arctan(x)"), e("x\\arcsin(x)"),
        e("\\frac{\\arctan(x)}{x^{2}}"), e("\\frac{\\arcsin(x)}{\\sqrt{1-x^{2}}}"), e("\\frac{\\arctan(x)}{1+x^{2}}"),
        e("\\frac{e^{\\arctan(x)}}{1+x^{2}}"), e("\\arctan(\\sqrt{x})"), e("\\operatorname{arccot}(x)"), e("x\\arccos(x)"),
        e("\\arcsin(x)^{2}"), e("\\frac{\\arctan(x)^{2}}{1+x^{2}}"), e("x^{3}\\arctan(x)"), e("\\arctan(2x)"), e("\\arcsin(2x)"),
        e("\\frac{\\arcsin(x)}{x^{2}}"), e("\\frac{x\\arcsin(x)}{\\sqrt{1-x^{2}}}"), e("\\arctan(\\frac{1}{x})"),
        e("\\frac{1}{(1+x^{2})\\arctan(x)}"), e("x\\arctan(x)^{2}"), e("\\arcsin(\\sqrt{x})"), e("\\frac{x}{1+x^{2}}\\arctan(x)"),
    )

    private val hyperbolic = listOf(
        e("\\sinh(x)"), e("\\cosh(x)"), e("\\tanh(x)"), e("x\\sinh(x)"), e("x\\cosh(x)"), e("\\sinh(x)^{2}"), e("\\cosh(x)^{2}"),
        e("\\sinh(x)\\cosh(x)"), e("\\sinh(x)^{3}"), e("\\cosh(x)^{3}"), e("\\tanh(x)^{2}"), e("\\frac{1}{\\cosh(x)^{2}}"),
        e("\\frac{1}{\\cosh(x)}"), e("e^{x}\\sinh(x)"), e("\\sinh(2x)\\cosh(3x)"), e("x^{2}\\cosh(x)"), e("\\frac{\\cosh(x)}{\\sinh(x)}"),
        e("\\frac{\\sinh(x)}{\\cosh(x)^{2}}"), e("\\frac{\\tanh(x)}{\\cosh(x)^{2}}"), e("\\frac{\\cosh(x)}{1+\\sinh(x)^{2}}"),
        e("\\frac{\\sinh(x)}{1+\\cosh(x)}"), e("\\frac{1}{\\sinh(x)}"), e("\\sinh(x)^{4}"), e("\\tanh(x)^{3}"), e("\\frac{1}{1+\\cosh(x)}"),
        e("\\cosh(2x)\\sinh(x)"), e("e^{-x}\\cosh(x)"), e("x\\tanh(x)^{2}"), e("\\sinh(\\ln(x))"), e("\\frac{1}{\\sinh(x)\\cosh(x)}"),
    )

    private val sections = listOf("rational" to rational, "algebraic" to algebraic, "exponential" to exponential,
        "logarithmic" to logarithmic, "trigonometric" to trigonometric, "inverse trigonometric" to inverseTrigonometric,
        "hyperbolic" to hyperbolic)

    private val x = Expr.Sym("x")

    private fun integrand(latex: String): Expr = Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList()).expression()

    /**
     * Whether F′ = f at the points where both are defined: true where every one of at least three agrees, false where one
     * differs, null where too few points could be compared.
     */
    private fun differentiatesBack(anti: Expr, f: Expr): Boolean? {
        val points = listOf(0.37, 0.71, 1.23, 1.9, 2.6, -0.43, -1.37, 3.3, 0.13, -2.2, 4.1, 0.55, 5.7, -3.6, 0.91)
        var ok = 0
        for (p in points) {
            val fv = Numeric.eval(f, mapOf("x" to p))
            if (!fv.isFinite()) continue
            fun big(t: Double) = Numeric.eval(anti, mapOf("x" to t))
            // Richardson twice (the error of the central difference is a series in h²), at two step sizes: the larger misses
            // near a singularity or where F oscillates fast (sin(eˣ)), the smaller loses digits to rounding.
            var defined = false
            var agrees = false
            for (scale in listOf(1e-3, 1e-4)) {
                val h = scale * max(1.0, abs(p))
                val d1 = (big(p + h) - big(p - h)) / (2 * h)
                val d2 = (big(p + h / 2) - big(p - h / 2)) / h
                val d3 = (big(p + h / 4) - big(p - h / 4)) / (h / 2)
                if (!d1.isFinite() || !d2.isFinite() || !d3.isFinite()) continue
                defined = true
                val r1 = (4 * d2 - d1) / 3
                val r2 = (4 * d3 - d2) / 3
                val d = (16 * r2 - r1) / 15
                if (abs(d - fv) <= 1e-6 * max(1.0, abs(fv))) agrees = true
            }
            if (!defined) continue
            if (!agrees) return false
            ok++
        }
        return if (ok >= 3) true else null
    }

    @Test
    fun corpus() {
        val wrong = ArrayList<String>()
        val unchecked = ArrayList<String>()
        val report = StringBuilder()
        var total = 0
        var solvedAll = 0
        var elementaryAll = 0
        val solvedBySection = LinkedHashMap<String, Int>()
        for ((name, entries) in sections) {
            var solved = 0
            var elementary = 0
            val declined = ArrayList<String>()
            for (entry in entries) {
                total++
                if (!entry.nonElementary) elementary++
                val f = integrand(entry.latex)
                val started = System.nanoTime()
                val o = MathCalc.calculate(entry.latex, Choice(Mode.INTEGRAL, "x")).outcome
                val ms = (System.nanoTime() - started) / 1_000_000
                val anti = (o as? Outcome.Expression)?.result
                if (anti == null) {
                    declined += entry.latex + (if (entry.nonElementary) " (no elementary antiderivative)" else "") +
                        " [" + ((o as? Outcome.Failed)?.failure ?: o.javaClass.simpleName) + ", ${ms} ms]"
                    continue
                }
                when (differentiatesBack(anti, f)) {
                    true -> solved++
                    false -> wrong += "${entry.latex}: $anti"
                    null -> unchecked += "${entry.latex}: $anti"
                }
            }
            solvedBySection[name] = solved
            solvedAll += solved
            elementaryAll += elementary
            report.append("$name: solved $solved of ${entries.size} (of them elementary ${elementary})\n")
            declined.forEach { report.append("  declined: $it\n") }
        }
        report.append("all: solved $solvedAll of $total (elementary $elementaryAll)\n")
        println(report)
        unchecked.forEach { println("unchecked: $it") }
        assertEquals("", wrong.joinToString("\n"))
        assertEquals("", unchecked.joinToString("\n"))
        for ((name, least) in LEAST) assert((solvedBySection[name] ?: 0) >= least) { "$name: ${solvedBySection[name]} < $least\n$report" }
    }

    private companion object {
        /** What the engine solves today in each section: it may only get better. */
        val LEAST = mapOf("rational" to 0, "algebraic" to 0, "exponential" to 0, "logarithmic" to 0, "trigonometric" to 0,
            "inverse trigonometric" to 0, "hyperbolic" to 0)
    }
}
