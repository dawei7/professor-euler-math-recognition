package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a learner types on the Calculator's keyboard, in words of English or German, read and worked out: each list reports
 * all its differences.
 */
class TextInputTest {
    private fun typed(text: String, language: String = "en"): String {
        return answerOf(Reports.of(TextInput.read(text, language)).result)
    }

    private fun check(language: String, cases: List<Pair<String, String>>) {
        val wrong = cases.mapNotNull { (text, expected) ->
            val got = typed(text, language)
            if (got != expected) "$text → ${TextInput.read(text, language)}: $got, not $expected" else null
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun mathInALine() {
        val cases = listOf(
            "x^2+3x" to "x^{2}+3x", "sqrt(x+1)" to "\\sqrt{x+1}", "e^(i pi)" to "e^{i \\pi}", "2πr" to "2 \\pi r",
            "x1+x2" to "x_{1}+x_{2}", "log10(100)" to "\\log_{10}(100)", "a<=b" to "a<=b", "x != 2" to "x \\ne 2",
            "2^-3" to "2^{-3}", "x^(n+1)" to "x^{n+1}", "abs(x-1)" to "\\left|x-1\\right|", "3x²" to "3x^{2}",
            "sin x" to "\\sin x", "gamma(5)" to "\\Gamma(5)", "2^3^2" to "2^{3^{2}}", "2*x" to "2\\cdot x",
        )
        val wrong = cases.mapNotNull { (text, expected) -> TextInput.latex(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun whatALineAsksForIsShownAsALearnerWritesIt() {
        assertEquals("\\int x\\cdot e^{x} \\, dx", TextInput.display(TextInput.read("integrate x*e^x dx")))
        assertEquals("\\frac{d}{dy}\\left(x^{2} y\\right)", TextInput.display(TextInput.read("derivative of x^2 y with respect to y")))
        assertEquals("\\frac{d}{dx}\\left(x^{3}\\right)", TextInput.display(TextInput.read("Ableitung von x^3", "de")))
        assertEquals("x^{2}=4", TextInput.display(TextInput.read("solve x^2=4")))
    }

    @Test
    fun english() = check("en", listOf(
        "2+3*4" to "14",
        "sqrt(8)" to "2√2 ≈ 2.828427125",
        "x^2+3x-4=0" to "x = -4; 1",
        "solve x^2 = 4 for x" to "x = -2; 2",
        "factor x^2-1" to "(x - 1)(x + 1)",
        "expand (x+1)^3" to "x^3 + 3x^2 + 3x + 1",
        "simplify (x^2-4)/(x+2)" to "x - 2 (x ≠ -2)",
        "d/dx x^3" to "3x^2",
        "integrate x^2 from 0 to 3" to "9",
        "limit sin(x)/x as x->0" to "1",
        "lim x->0 (1-cos(x))/x^2" to "1/2 = 0.5",
        "e^(i pi)" to "-1",
        "gamma(5)" to "24",
        "derivative of x^2 y with respect to y" to "x^2",
        "5!" to "120",
        "2^10" to "1024",
        "|x-1| = 3" to "x = -2; 4",
        "3x+2=11" to "x = 3",
        "sin(30°)" to "1/2 = 0.5",
        "√2·√8" to "4",
        "sum k for k=1 to 100" to "5050",
    ))

    @Test
    fun german() = check("de", listOf(
        "löse x^2=9 nach x" to "x = -3; 3",
        "Ableitung von x^3 nach x" to "3x^2",
        "Integral von x^2 von 0 bis 3" to "9",
        "Grenzwert von sin(x)/x für x gegen 0" to "1",
        "faktorisiere x^2+4x+4" to "(x + 2)^2",
        "vereinfache (x^2-1)/(x-1)" to "x + 1 (x ≠ 1)",
        "Summe von k für k=1 bis 100" to "5050",
        "berechne 2,5*4" to "10",
        // English is understood whatever the app's language.
        "solve 2x = 6" to "x = 3",
    ))

    @Test
    fun reportOfANumber() {
        val r = Reports.of("2^{10}")
        assertTrue(r.panels.any { it.kind == PanelKind.DIGITS })
        val d = Reports.compute(r, r.panels.first { it.kind == PanelKind.DIGITS }) as PanelResult.Digits
        assertEquals(Decimal.Digits(false, "1024", 3, exact = true), d.value.re)
        val pi = Reports.of("\\pi")
        val digits = Reports.compute(pi, pi.panels.first { it.kind == PanelKind.DIGITS }, digits = 50) as PanelResult.Digits
        assertEquals("31415926535897932384626433832795028841971693993751", (digits.value.re as Decimal.Digits).digits)
    }

    @Test
    fun reportOfAnExpression() {
        val r = Reports.of("x^{2}-1")
        assertEquals(PanelKind.RESULT, r.panels.first().kind)
        assertTrue(r.panels.none { it.kind == PanelKind.DIGITS })
        val graph = r.panels.first { it.choice?.mode == Mode.GRAPH }
        val g = Reports.compute(r, graph) as PanelResult.Calculated
        assertTrue(g.calculation.outcome is Outcome.Graph)
        // Every panel works out on its own.
        for (p in r.panels) assertTrue(Reports.compute(r, p) !is PanelResult.Failed)
    }
}
