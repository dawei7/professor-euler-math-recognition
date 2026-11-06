package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A corpus of limits from calculus textbooks (Stewart, Spivak, Gruntz's thesis), each with its value known independently
 * (written here as a number): at a point from both sides and from one, at ±∞, the indeterminate forms 0/0, ∞/∞, 0·∞, ∞ − ∞,
 * 1^∞, 0⁰, ∞⁰, and nested exponentials and logarithms. NaN marks a limit that does not exist, which the engine must decline. An
 * answer is exact (no decimal from values) or declined; a value that differs from the known one is wrong.
 */
class LimitCorpusTest {
    private val inf = Double.POSITIVE_INFINITY
    private val none = Double.NaN

    private val atPoints = listOf(
        "\\lim_{x\\to 0}\\frac{\\sin(x)}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{1-\\cos(x)}{x^{2}}" to 0.5,
        "\\lim_{x\\to 0}\\frac{\\tan(x)}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{e^{x}-1}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{\\ln(1+x)}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{2^{x}-1}{x}" to ln(2.0),
        "\\lim_{x\\to 0}\\frac{\\sin(3x)}{x}" to 3.0,
        "\\lim_{x\\to 0}\\frac{\\sin(2x)}{\\sin(5x)}" to 0.4,
        "\\lim_{x\\to 0}(1+x)^{\\frac{1}{x}}" to E,
        "\\lim_{x\\to 0}(1+2x)^{\\frac{1}{x}}" to E * E,
        "\\lim_{x\\to 0^{+}}x\\ln(x)" to 0.0,
        "\\lim_{x\\to 0^{+}}x^{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{x-\\sin(x)}{x^{3}}" to 1.0 / 6,
        "\\lim_{x\\to 0}\\frac{e^{x}-1-x}{x^{2}}" to 0.5,
        "\\lim_{x\\to 0}\\frac{\\tan(x)-x}{x^{3}}" to 1.0 / 3,
        "\\lim_{x\\to 0}\\frac{\\sqrt{1+x}-1}{x}" to 0.5,
        "\\lim_{x\\to 0}\\frac{\\sqrt[3]{1+x}-1}{x}" to 1.0 / 3,
        "\\lim_{x\\to 0}\\left(\\cot(x)-\\frac{1}{x}\\right)" to 0.0,
        "\\lim_{x\\to 0}\\left(\\frac{1}{x}-\\frac{1}{e^{x}-1}\\right)" to 0.5,
        "\\lim_{x\\to 0}\\left(\\frac{1}{x^{2}}-\\cot(x)^{2}\\right)" to 2.0 / 3,
        "\\lim_{x\\to 0}\\frac{\\ln(\\cos(x))}{x^{2}}" to -0.5,
        "\\lim_{x\\to 0}\\cos(x)^{\\frac{1}{x^{2}}}" to exp(-0.5),
        "\\lim_{x\\to 0}\\left(\\frac{\\sin(x)}{x}\\right)^{\\frac{1}{x^{2}}}" to exp(-1.0 / 6),
        "\\lim_{x\\to 0^{+}}x^{\\sin(x)}" to 1.0,
        "\\lim_{x\\to 0}\\frac{e^{x}-e^{-x}}{x}" to 2.0,
        "\\lim_{x\\to 0}\\frac{\\sinh(x)}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{\\arcsin(x)}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{\\arctan(x)}{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{1-\\cos(2x)}{x^{2}}" to 2.0,
        "\\lim_{x\\to 0}x^{2}\\sin\\left(\\frac{1}{x}\\right)" to 0.0,
        "\\lim_{x\\to 0}\\frac{e^{-\\frac{1}{x^{2}}}}{x^{10}}" to 0.0,
        "\\lim_{x\\to 0^{+}}\\frac{e^{-\\frac{1}{x}}}{x}" to 0.0,
        "\\lim_{x\\to 0^{+}}x^{\\frac{1}{x}}" to 0.0,
        "\\lim_{x\\to 0^{+}}\\left(\\frac{1}{x}\\right)^{x}" to 1.0,
        "\\lim_{x\\to 0^{+}}\\ln(x)\\ln(1-x)" to 0.0,
        "\\lim_{x\\to 0^{+}}x^{x^{x}}" to 0.0,
        "\\lim_{x\\to 0^{+}}\\sin(x)^{x}" to 1.0,
        "\\lim_{x\\to 0}\\frac{2^{x}-3^{x}}{x}" to ln(2.0 / 3),
        "\\lim_{x\\to 0^{+}}x^{2}\\ln(x)^{3}" to 0.0,
        "\\lim_{x\\to 0^{+}}\\frac{\\ln(x)}{\\cot(x)}" to 0.0,
        "\\lim_{x\\to 0}\\frac{x^{2}}{1-\\cos(x)}" to 2.0,
        "\\lim_{x\\to 0}\\frac{\\ln(1+x)-x}{x^{2}}" to -0.5,
        "\\lim_{x\\to 0}\\frac{1}{x}" to none,
        "\\lim_{x\\to 0}\\frac{1}{x^{2}}" to inf,
        "\\lim_{x\\to 0^{+}}\\frac{1}{x}" to inf,
        "\\lim_{x\\to 0^{-}}\\frac{1}{x}" to -inf,
        "\\lim_{x\\to 0}\\frac{|x|}{x}" to none,
        "\\lim_{x\\to 0}\\sin\\left(\\frac{1}{x}\\right)" to none,
        "\\lim_{x\\to 0^{+}}\\ln(x)" to -inf,
        "\\lim_{x\\to 0^{+}}e^{\\frac{1}{x}}" to inf,
        "\\lim_{x\\to 0^{-}}e^{\\frac{1}{x}}" to 0.0,
        "\\lim_{x\\to 1}\\frac{x^{2}-1}{x-1}" to 2.0,
        "\\lim_{x\\to 2}\\frac{x^{3}-8}{x-2}" to 12.0,
        "\\lim_{x\\to 4}\\frac{\\sqrt{x}-2}{x-4}" to 0.25,
        "\\lim_{x\\to\\pi}\\frac{\\sin(x)}{x-\\pi}" to -1.0,
        "\\lim_{x\\to 1}\\frac{x-1}{\\ln(x)}" to 1.0,
        "\\lim_{x\\to 1}x^{\\frac{1}{1-x}}" to 1 / E,
        "\\lim_{x\\to 1}\\frac{2^{x}-2}{x-1}" to 2 * ln(2.0),
        "\\lim_{x\\to 1}\\frac{e^{x}-e}{x-1}" to E,
        "\\lim_{x\\to 1}\\left(\\frac{1}{x-1}-\\frac{1}{\\ln(x)}\\right)" to -0.5,
        "\\lim_{x\\to 1}(1-x)\\tan\\left(\\frac{\\pi x}{2}\\right)" to 2 / PI,
        "\\lim_{x\\to 1^{+}}(x-1)^{x-1}" to 1.0,
        "\\lim_{x\\to 1^{-}}e^{\\frac{1}{x-1}}" to 0.0,
        "\\lim_{x\\to 1^{+}}e^{\\frac{1}{x-1}}" to inf,
        "\\lim_{x\\to 3}\\frac{x^{2}-9}{x^{2}-2x-3}" to 1.5,
        "\\lim_{x\\to 0}\\frac{\\sqrt{x+4}-2}{x}" to 0.25,
    )

    private val atInfinity = listOf(
        "\\lim_{x\\to\\infty}\\left(1+\\frac{1}{x}\\right)^{x}" to E,
        "\\lim_{x\\to\\infty}\\left(1+\\frac{2}{x}\\right)^{x}" to E * E,
        "\\lim_{x\\to\\infty}\\left(1-\\frac{1}{x}\\right)^{x}" to 1 / E,
        "\\lim_{x\\to\\infty}x^{\\frac{1}{x}}" to 1.0,
        "\\lim_{x\\to\\infty}\\frac{e^{x}}{x^{100}}" to inf,
        "\\lim_{x\\to\\infty}\\frac{x^{100}}{e^{x}}" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{\\ln(x)^{2}}{x}" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{\\ln(x)}{x^{\\frac{1}{10}}}" to 0.0,
        "\\lim_{x\\to\\infty}x\\left(e^{\\frac{1}{x}}-1\\right)" to 1.0,
        "\\lim_{x\\to\\infty}x\\sin\\left(\\frac{1}{x}\\right)" to 1.0,
        "\\lim_{x\\to\\infty}x\\left(\\ln(x+1)-\\ln(x)\\right)" to 1.0,
        "\\lim_{x\\to\\infty}\\left(\\sqrt{x^{2}+x}-x\\right)" to 0.5,
        "\\lim_{x\\to\\infty}\\left(\\sqrt{x^{2}+1}-x\\right)" to 0.0,
        "\\lim_{x\\to\\infty}\\left(\\frac{x+1}{x-1}\\right)^{x}" to E * E,
        "\\lim_{x\\to\\infty}x^{2}\\left(1-\\cos\\left(\\frac{1}{x}\\right)\\right)" to 0.5,
        "\\lim_{x\\to\\infty}\\frac{3x^{2}+1}{2x^{2}-x}" to 1.5,
        "\\lim_{x\\to\\infty}\\frac{\\ln(\\ln(x))}{\\ln(x)}" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{x}{\\ln(x)}" to inf,
        "\\lim_{x\\to\\infty}\\frac{x^{\\ln(x)}}{e^{x}}" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{e^{\\sqrt{x}}}{x^{10}}" to inf,
        "\\lim_{x\\to\\infty}\\left(e^{x}+x\\right)^{\\frac{1}{x}}" to E,
        "\\lim_{x\\to\\infty}\\left(2^{x}+3^{x}\\right)^{\\frac{1}{x}}" to 3.0,
        "\\lim_{x\\to\\infty}\\frac{\\ln(1+e^{x})}{x}" to 1.0,
        "\\lim_{x\\to\\infty}\\left(\\ln(e^{x}+x)-x\\right)" to 0.0,
        "\\lim_{x\\to\\infty}x\\left(\\frac{\\pi}{2}-\\arctan(x)\\right)" to 1.0,
        "\\lim_{x\\to\\infty}\\left(x-\\ln(\\cosh(x))\\right)" to ln(2.0),
        "\\lim_{x\\to\\infty}\\frac{e^{\\frac{1}{x}-e^{-x}}-e^{\\frac{1}{x}}}{e^{-x}}" to -1.0,
        "\\lim_{x\\to\\infty}e^{x}\\left(e^{\\frac{1}{x}+e^{-x}}-e^{\\frac{1}{x}}\\right)" to 1.0,
        "\\lim_{x\\to\\infty}\\frac{x^{x}}{e^{x}}" to inf,
        "\\lim_{x\\to\\infty}xe^{-x}" to 0.0,
        "\\lim_{x\\to\\infty}e^{xe^{-x}}" to 1.0,
        "\\lim_{x\\to\\infty}\\ln(x)^{\\frac{1}{x}}" to 1.0,
        "\\lim_{x\\to\\infty}x^{\\frac{1}{\\ln(x)}}" to E,
        "\\lim_{x\\to\\infty}\\left(1+\\frac{1}{x^{2}}\\right)^{x}" to 1.0,
        "\\lim_{x\\to\\infty}\\left(1+\\frac{1}{x}\\right)^{x^{2}}" to inf,
        "\\lim_{x\\to\\infty}\\left(1+\\frac{1}{x}\\right)^{x^{2}}e^{-x}" to exp(-0.5),
        "\\lim_{x\\to\\infty}x^{2}\\left(e^{\\frac{1}{x}}-1-\\frac{1}{x}\\right)" to 0.5,
        "\\lim_{x\\to\\infty}\\frac{x+\\ln(x)}{x-\\ln(x)}" to 1.0,
        "\\lim_{x\\to\\infty}\\frac{\\ln(x^{2}+1)}{\\ln(x)}" to 2.0,
        "\\lim_{x\\to\\infty}e^{-x}\\ln(x)" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{e^{x}}{e^{x+\\frac{1}{x}}}" to 1.0,
        "\\lim_{x\\to\\infty}\\frac{\\sin(x)}{x}" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{x\\arctan(x)}{x+1}" to PI / 2,
        "\\lim_{x\\to\\infty}\\tanh(x)" to 1.0,
        "\\lim_{x\\to\\infty}e^{2x}(\\tanh(x)-1)" to -2.0,
        "\\lim_{x\\to\\infty}\\left(\\frac{x}{x+1}\\right)^{x}" to 1 / E,
        "\\lim_{x\\to\\infty}\\frac{\\ln(x)^{\\ln(x)}}{x}" to inf,
        "\\lim_{x\\to\\infty}\\frac{e^{e^{x}}}{e^{e^{x}-x}}" to inf,
        "\\lim_{x\\to\\infty}\\frac{e^{e^{x}}}{e^{e^{x}+e^{-x}}}" to 1.0,
        "\\lim_{x\\to\\infty}\\frac{\\ln(\\ln(x+e^{\\ln(x)\\ln(\\ln(x))}))}{\\ln(\\ln(\\ln(e^{x}+x+\\ln(x))))}" to 1.0,
        "\\lim_{x\\to\\infty}\\left(x^{\\frac{1}{x}}-1\\right)\\frac{x}{\\ln(x)}" to 1.0,
        "\\lim_{x\\to\\infty}\\sqrt{x}\\left(\\sqrt{x+1}-\\sqrt{x}\\right)" to 0.5,
        "\\lim_{x\\to\\infty}\\frac{2^{x}}{3^{x}}" to 0.0,
        "\\lim_{x\\to\\infty}\\frac{x^{3}+2x}{x^{3}-x^{2}}" to 1.0,
        "\\lim_{x\\to\\infty}\\left(\\frac{1}{x}\\right)^{\\frac{1}{x}}" to 1.0,
        "\\lim_{x\\to\\infty}x\\ln\\left(1+\\frac{3}{x}\\right)" to 3.0,
        "\\lim_{x\\to\\infty}\\sin(x)" to none,
        "\\lim_{x\\to\\infty}\\frac{e^{x}-e^{-x}}{e^{x}+e^{-x}}" to 1.0,
        "\\lim_{x\\to\\infty}\\left(\\ln(2x)-\\ln(x+1)\\right)" to ln(2.0),
        "\\lim_{x\\to\\infty}\\frac{\\sqrt{x^{2}+1}}{x}" to 1.0,
        "\\lim_{x\\to\\infty}\\frac{e^{x^{2}}}{e^{x}}" to inf,
        "\\lim_{x\\to\\infty}x\\left(\\left(1+\\frac{1}{x}\\right)^{x}-e\\right)" to -E / 2,
        "\\lim_{x\\to\\infty}\\left(\\frac{\\ln(x)}{x}\\right)^{\\frac{1}{x}}" to 1.0,
    )

    private val atMinusInfinity = listOf(
        "\\lim_{x\\to-\\infty}x^{2}e^{x}" to 0.0,
        "\\lim_{x\\to-\\infty}xe^{x}" to 0.0,
        "\\lim_{x\\to-\\infty}\\left(1+\\frac{1}{x}\\right)^{x}" to E,
        "\\lim_{x\\to-\\infty}\\left(\\sqrt{x^{2}+x}+x\\right)" to -0.5,
        "\\lim_{x\\to-\\infty}\\arctan(x)" to -PI / 2,
        "\\lim_{x\\to-\\infty}\\frac{x}{\\sqrt{x^{2}+1}}" to -1.0,
        "\\lim_{x\\to-\\infty}e^{x}" to 0.0,
        "\\lim_{x\\to-\\infty}\\frac{e^{x}}{x}" to 0.0,
        "\\lim_{x\\to-\\infty}x^{3}" to -inf,
        "\\lim_{x\\to-\\infty}\\tanh(x)" to -1.0,
    )

    private val sections = listOf("at a point" to atPoints, "at ∞" to atInfinity, "at −∞" to atMinusInfinity)

    /** The limit as the engine gives it: its value and whether it is exact; null where it was declined. */
    private fun valueOf(c: Calculation): Pair<Double, Boolean>? {
        fun of(e: Expr): Double? = when {
            e == Expr.INF -> Double.POSITIVE_INFINITY
            e == neg(Expr.INF) -> Double.NEGATIVE_INFINITY
            else -> Numeric.value(e)
        }
        return when (val o = c.outcome) {
            is Outcome.Value -> o.exact?.let { ex -> of(ex)?.let { it to !ex.hasDecimal() } } ?: (o.value.double to (o.value is Num.Exact))
            is Outcome.Expression -> of(o.result)?.let { it to !o.result.hasDecimal() }
            else -> null
        }
    }

    private fun same(a: Double, b: Double): Boolean = when {
        a.isInfinite() || b.isInfinite() -> a == b
        else -> abs(a - b) <= 1e-9 * max(1.0, abs(b))
    }

    @Test
    fun corpus() {
        val wrong = ArrayList<String>()
        val report = StringBuilder()
        var all = 0
        var exactAll = 0
        val exactBySection = LinkedHashMap<String, Int>()
        for ((name, entries) in sections) {
            var exact = 0
            var numeric = 0
            val declined = ArrayList<String>()
            for ((latex, expected) in entries) {
                all++
                val c = MathCalc.calculate(latex)
                val got = valueOf(c)
                when {
                    got == null && expected.isNaN() -> exact++
                    got == null -> declined += "$latex [" + ((c.outcome as? Outcome.Failed)?.failure ?: c.outcome.javaClass.simpleName) + "]"
                    expected.isNaN() -> wrong += "$latex: ${answerOf(c)}, but there is no limit"
                    !same(got.first, expected) -> wrong += "$latex: ${answerOf(c)}, not $expected"
                    got.second -> exact++
                    else -> { numeric++; declined += "$latex [only a decimal: ${answerOf(c)}]" }
                }
            }
            exactBySection[name] = exact
            exactAll += exact
            report.append("$name: exact $exact of ${entries.size}" + (if (numeric > 0) ", only a decimal $numeric" else "") + "\n")
            declined.forEach { report.append("  declined: $it\n") }
        }
        report.append("all: exact $exactAll of $all (a limit that does not exist, declined, counts as exact)\n")
        println(report)
        assertEquals("", wrong.joinToString("\n"))
        for ((name, least) in LEAST) assert((exactBySection[name] ?: 0) >= least) { "$name: ${exactBySection[name]} < $least\n$report" }
    }

    private companion object {
        /**
         * What the engine finds exactly today in each section: it may only get better. Before Gruntz's algorithm (6 November
         * 2026) it was 58, 46 and 10, with 9 answers wrong (decimals from values, and ∞ for (eˣ + x)^(1/x) and four more where
         * values overflowed); the one still declined is Gruntz's own ln ln example, which tends to 1 too slowly for values to
         * confirm it.
         */
        val LEAST = mapOf("at a point" to 66, "at ∞" to 62, "at −∞" to 10)
    }
}
