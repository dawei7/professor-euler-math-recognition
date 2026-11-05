package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The calculus of milestone 4: limits by series and by growth, improper integrals piece by piece, the further integration
 * rules, Gosper's sums and the convergence tests. Each list reports all its differences.
 */
class CalculusDeepTest {
    private fun check(vararg cases: Pair<String, String>) {
        val wrong = cases.mapNotNull { (latex, expected) ->
            val got = answerOf(MathCalc.calculate(latex))
            if (got != expected) "$latex: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** Indeterminate forms a learner meets: 0⁰, ∞⁰, 1^∞, ∞ − ∞ and 0·∞, by the leading term of a series. */
    @Test fun limitsBySeries() = check(
        "\\lim_{x\\to 0^{+}}x^{x}" to "1",
        "\\lim_{x\\to\\infty}x^{\\frac{1}{x}}" to "1",
        "\\lim_{x\\to 0}\\left(\\frac{1}{\\sin(x)^{2}}-\\frac{1}{x^{2}}\\right)" to "1/3 ≈ 0.3333333333",
        "\\lim_{x\\to 0^{+}}x\\ln(x)" to "0",
        "\\lim_{x\\to 0^{+}}\\sqrt{x}\\ln(x)" to "0",
        "\\lim_{x\\to 0}\\left(\\cos(x)\\right)^{\\frac{1}{x^{2}}}" to "1/√e ≈ 0.6065306597",
        "\\lim_{x\\to\\infty}\\left(1+\\frac{a}{x}\\right)^{x}" to "e^a",
        "\\lim_{x\\to 0}\\frac{\\arcsin(x)-x}{x^{3}}" to "1/6 ≈ 0.1666666667",
        "\\lim_{x\\to 0}\\frac{\\tan(x)-\\sin(x)}{x^{3}}" to "1/2 = 0.5",
        "\\lim_{x\\to 0^{+}}\\frac{1-\\cos(\\sqrt{x})}{x}" to "1/2 = 0.5",
        "\\lim_{x\\to\\infty}\\left(\\sqrt{x^{2}+x}-x\\right)" to "0.5",
    )

    /** What grows fastest at infinity, and functions with a limit there. */
    @Test fun limitsByGrowth() = check(
        "\\lim_{x\\to\\infty}\\frac{x^{100}}{e^{x}}" to "0",
        "\\lim_{x\\to\\infty}x^{2}e^{-x}" to "0",
        "\\lim_{x\\to\\infty}\\arctan(x)" to "π/2 ≈ 1.570796327",
        "\\lim_{x\\to-\\infty}\\arctan(x)" to "-π/2 ≈ -1.570796327",
        "\\lim_{x\\to 0}\\frac{1}{x}" to "NO_LIMIT",
        "\\lim_{x\\to 0}\\frac{1}{x^{2}}" to "∞",
    )

    /**
     * Improper integrals exactly, each end a limit from inside; an integrand undefined inside is split there; a divergent one
     * is said to diverge only where an end's limit is infinite, and an integral that cannot be worked out shows no number.
     */
    @Test fun improperIntegrals() = check(
        "\\int_{0}^{\\infty}e^{-x^{2}}dx" to "√π/2 ≈ 0.8862269255",
        "\\int_{-\\infty}^{\\infty}e^{-x^{2}}dx" to "√π ≈ 1.772453851",
        "\\int_{0}^{1}\\ln(x)dx" to "-1",
        "\\int_{-\\infty}^{\\infty}\\frac{1}{1+x^{2}}dx" to "π ≈ 3.141592654",
        "\\int_{0}^{1}\\frac{1}{\\sqrt{x}}dx" to "2",
        "\\int_{2}^{\\infty}\\frac{1}{x\\ln(x)^{2}}dx" to "1/ln(2) ≈ 1.442695041",
        "\\int_{0}^{1}\\sqrt{1-x^{2}}dx" to "π/4 ≈ 0.7853981634",
        "\\int_{0}^{\\frac{\\pi}{4}}\\tan(x)dx" to "ln(2)/2 ≈ 0.3465735903",
        "\\int_{-1}^{1}\\frac{1}{x^{2}}dx" to "DIVERGES",
        "\\int_{0}^{2}\\frac{1}{(x-1)^{2}}dx" to "DIVERGES",
        "\\int_{0}^{\\infty}\\frac{1}{(x-1)^{2}}dx" to "DIVERGES",
        "\\int_{0}^{\\pi}\\tan(x)dx" to "DIVERGES",
        "\\int_{1}^{\\infty}\\frac{1}{x}dx" to "DIVERGES",
        "\\int_{0}^{\\infty}\\sin(x)dx" to "UNSOLVED",
    )

    /** The Gaussian, sec and csc and their powers, roots of quadratics, rational functions of eˣ, products of sines and cosines. */
    @Test fun integrationRules() = check(
        "\\int e^{-x^{2}}dx" to "(1/2)√π·erf(x) + C",
        "\\int e^{-x^{2}+2x}dx" to "(1/2)e√π·erf(x - 1) + C",
        "\\int\\sec(x)^{3}dx" to "sin(x)/(2cos^2(x)) + (1/2)ln(|sin(x)/cos(x) + 1/cos(x)|) + C",
        "\\int\\csc(x)dx" to "-ln(|1/sin(x) + cot(x)|) + C",
        "\\int\\sqrt{1-x^{2}}dx" to "(1/2)x√(-x^2 + 1) + (1/2)arcsin(x) + C",
        "\\int\\sqrt{x^{2}+1}dx" to "(1/2)x√(x^2 + 1) + (1/2)ln(|x + √(x^2 + 1)|) + C",
        "\\int\\frac{1}{(1+x^{2})^{\\frac{3}{2}}}dx" to "x/√(x^2 + 1) + C",
        "\\int\\frac{x^{2}}{\\sqrt{1-x^{2}}}dx" to "-(1/2)x√(-x^2 + 1) + (1/2)arcsin(x) + C",
        "\\int\\frac{1}{1+e^{x}}dx" to "x - ln(e^x + 1) + C",
        "\\int\\frac{1}{e^{x}+e^{-x}}dx" to "arctan(e^x) + C",
        "\\int x\\sin(x)\\cos(x)dx" to "-(1/4)x·cos(2x) + (1/8)sin(2x) + C",
        "\\int\\sin(2x)\\cos(3x)dx" to "(1/2)cos(x) - (1/10)cos(5x) + C",
    )

    /** Gosper's algorithm for sums in closed form, the table of series, and divergence only where a test shows it. */
    @Test fun sums() = check(
        "\\sum_{k=0}^{n}k2^{k}" to "n·2^(n + 1) - 2^(n + 1) + 2",
        "\\sum_{k=1}^{n}\\frac{1}{k(k+1)}" to "n/(n + 1)",
        "\\sum_{k=1}^{\\infty}\\frac{1}{k(k+1)}" to "1",
        "\\sum_{k=1}^{\\infty}\\frac{(-1)^{k}}{k}" to "-ln(2) ≈ -0.6931471806",
        "\\sum_{k=0}^{\\infty}\\frac{x^{k}}{k!}" to "e^x",
        "\\sum_{k=1}^{\\infty}\\frac{1}{k}" to "DIVERGES",
    )

    private fun series(latex: String, choice: Choice = Choice(Mode.SERIES, "x")): String {
        val c = MathCalc.calculate(latex, choice)
        val o = c.outcome
        return if (o is Outcome.Expression) c.textResult(o) else answerOf(c)
    }

    /** Taylor and Laurent series, at 0 or at any point, in rising powers with their remainder; checked against the function. */
    @Test fun seriesAtAPoint() {
        val cases = listOf(
            series("\\sin(x)") to "x - (1/6)x^3 + (1/120)x^5 + O(x^7)",
            series("\\frac{1}{\\sin(x)}") to "1/x + (1/6)x + (7/360)x^3 + (31/15120)x^5 + O(x^7)",
            series("\\sqrt{1+x}", Choice(Mode.SERIES, "x", order = 3)) to "1 + (1/2)x - (1/8)x^2 + (1/16)x^3 + O(x^4)",
            series("\\tan(x)") to "x + (1/3)x^3 + (2/15)x^5 + O(x^7)",
            series("\\ln(x)", Choice(Mode.SERIES, "x", point = "1", order = 3)) to "(x - 1) - (1/2)(x - 1)^2 + (1/3)(x - 1)^3 + O((x - 1)^4)",
            series("\\cos(x)", Choice(Mode.SERIES, "x", point = "\\pi", order = 4)) to "-1 + (1/2)(x - π)^2 - (1/24)(x - π)^4 + O((x - π)^5)",
            series("\\frac{1}{1-10x}", Choice(Mode.SERIES, "x", order = 3)) to "1 + 10x + 100x^2 + 1000x^3 + O(x^4)",
        )
        assertEquals("", cases.filter { (got, want) -> got != want }.joinToString("\n") { (got, want) -> "$got, not $want" })
        // Offered for a function that is no polynomial, at 1 where it has none at 0; never for a polynomial.
        assertEquals(true, Mode.SERIES in MathCalc.calculate("\\ln(x)").modes)
        assertEquals(false, Mode.SERIES in MathCalc.calculate("x^{2}+1").modes)
    }

    /** "Taylor series" as each language says it, the point after "at" or "um". */
    @Test fun seriesInWords() {
        val expected = "x - (1/6)x^3 + (1/120)x^5 + O(x^7)"
        val wrong = listOf("taylor series of sin(x)" to "en", "Taylorreihe von sin(x) um 0" to "de", "série de taylor de sin(x)" to "fr",
            "泰勒展开 sin(x)" to "zh", "ряд тейлора sin(x)" to "ru", "sin(x)をテイラー展開" to "ja", "serie di taylor di sin(x)" to "it",
            "maclaurin sin(x)" to "ko").mapNotNull { (text, language) ->
            val c = Reports.of(TextInput.read(text, language)).result
            val o = c.outcome
            val got = if (o is Outcome.Expression) c.textResult(o) else answerOf(c)
            if (got != expected) "$language $text: $got" else null
        }
        assertEquals("", wrong.joinToString("\n"))
        val atOne = TextInput.read("taylor series of e^x at x = 1")
        assertEquals(Choice(Mode.SERIES, "x", point = "1"), atOne.choice)
        // As textbooks put it, where the verb is also the one for multiplying out or factoring.
        val textbook = listOf("desarrolla sin(x) en serie de taylor" to "es", "rozwiń sin(x) w szereg maclaurina" to "pl", "sin(x)を級数展開" to "ja",
            "разложите в ряд тейлора функцию sin(x)" to "ru", "把sin(x)展开成泰勒级数" to "zh-Hans", "sviluppa sin(x) in serie di taylor" to "it",
            "développe sin(x) en série entière" to "fr", "sin(x) 급수 전개" to "ko").mapNotNull { (text, language) ->
            val c = Reports.of(TextInput.read(text, language)).result
            val o = c.outcome
            val got = if (o is Outcome.Expression) c.textResult(o) else answerOf(c)
            if (got != expected) "$language $text: $got" else null
        }
        assertEquals("", textbook.joinToString("\n"))
        // The point in words: "nel punto 1", "в точке 1".
        assertEquals(Choice(Mode.SERIES, "x", point = "1"), TextInput.read("serie di taylor di ln(x) nel punto 1", "it").choice)
        assertEquals(Choice(Mode.SERIES, "x", point = "1"), TextInput.read("ряд тейлора ln(x) в точке 1", "ru").choice)
    }

    private fun critical(latex: String) = answerOf(MathCalc.calculate(latex, Choice(Mode.EXTREMA)))

    /** Critical points of functions of x, y (and z), each checked and classified by the Hessian's leading minors. */
    @Test fun criticalPoints() {
        val cases = listOf(
            critical("x^{3}-3xy+y^{3}") to "(0, 0) saddle 0; (1, 1) minimum -1",
            critical("x^{3}+y^{3}-3x-12y+20") to "(-1, -2) maximum 38; (-1, 2) saddle 6; (1, -2) saddle 34; (1, 2) minimum 2",
            critical("x^{4}+y^{4}") to "(0, 0) undecided 0",
            critical("xye^{-x^{2}-y^{2}}") to "(-√2/2, -√2/2) maximum 1/(2e) ≈ 0.1839397206; (-√2/2, √2/2) minimum -1/(2e) ≈ -0.1839397206; " +
                "(0, 0) saddle 0; (√2/2, -√2/2) minimum -1/(2e) ≈ -0.1839397206; (√2/2, √2/2) maximum 1/(2e) ≈ 0.1839397206",
            critical("x^{2}+y^{2}+z^{2}-2x+4z") to "(1, 0, -2) minimum -5",
            // A line of critical points is not said as if it were some of them.
            critical("(x-y)^{2}") to "UNSOLVED",
        )
        assertEquals("", cases.filter { (got, want) -> got != want }.joinToString("\n") { (got, want) -> "$got, not $want" })
        assertEquals(true, Mode.SURFACE in MathCalc.calculate("x^{2}-y^{2}").modes)
        assertEquals(false, Mode.EXTREMA in MathCalc.calculate("a^{2}+b^{2}").modes)
    }

    /** An iterated integral whose inner bounds are in the outer letter. */
    @Test fun iteratedIntegrals() = check(
        "\\int_{0}^{1}\\int_{0}^{2}xy\\,dx\\,dy" to "1",
        "\\int_{0}^{1}\\int_{0}^{x}xy\\,dy\\,dx" to "0.125",
        "\\int_{0}^{1}\\int_{x^{2}}^{x}(x+y)\\,dy\\,dx" to "0.15",
    )
}
