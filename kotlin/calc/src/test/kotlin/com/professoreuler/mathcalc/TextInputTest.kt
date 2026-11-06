package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a learner types on the Calculator's keyboard, formulas and English functions, in English and German, read and worked
 * out: each list reports all its differences.
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
            // LaTeX, as the pen pad reads it, stays as it is.
            "\\frac{1}{2}+\\sin x" to "\\frac{1}{2}+\\sin x", "x^{2}-\\sqrt{4}" to "x^{2}-\\sqrt{4}", "a\\,b" to "a\\,b",
        )
        val wrong = cases.mapNotNull { (text, expected) -> TextInput.latex(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun aCallIsShownAsTheCall() {
        assertEquals("\\operatorname{integral}\\left(x\\cdot e^{x}, x\\right)", TextInput.display(TextInput.read("integral(x*e^x, x)")))
        assertEquals("\\operatorname{derivative}\\left(x^{2} y, y\\right)", TextInput.display(TextInput.read("derivative(x^2 y, y)")))
        // d/dx is notation, written as the function it is.
        assertEquals("\\operatorname{derivative}\\left(x^{3}, x\\right)", TextInput.display(TextInput.read("d/dx x^3")))
        assertEquals("\\operatorname{solve}\\left(x^{2}=4, x\\right)", TextInput.display(TextInput.read("solve(x^2=4, x)")))
        // A formula alone is shown as it is.
        assertEquals("x^{2}=4", TextInput.display(TextInput.read("x^2=4")))
    }

    /** No command words in any language (owner, 6 November 2026): the words are letters, the functions are English. */
    @Test
    fun wordsAreNoCommands() {
        assertEquals(null, TextInput.read("solve x^2 = 4").choice)
        assertEquals(null, TextInput.read("löse x^2 = 4", "de").choice)
        assertEquals(null, TextInput.read("5 km in mi").choice)
        assertEquals(null, TextInput.read("ggT(12, 18)", "de").choice)
    }

    @Test
    fun english() = check("en", listOf(
        "2+3*4" to "14",
        "sqrt(8)" to "2√2 ≈ 2.828427125",
        "x^2+3x-4=0" to "x = -4; 1",
        "solve(x^2 = 4, x)" to "x = -2; 2",
        "factor(x^2-1)" to "(x - 1)(x + 1)",
        "expand((x+1)^3)" to "x^3 + 3x^2 + 3x + 1",
        "simplify((x^2-4)/(x+2))" to "x - 2 (x ≠ -2)",
        "d/dx x^3" to "3x^2",
        "integral(x^2, x, 0, 3)" to "9",
        "limit(sin(x)/x, x, 0)" to "1",
        "limit((1-cos(x))/x^2, x, 0)" to "1/2 = 0.5",
        "e^(i pi)" to "-1",
        "gamma(5)" to "24",
        "derivative(x^2 y, y)" to "x^2",
        "5!" to "120",
        "2^10" to "1024",
        "|x-1| = 3" to "x = -2; 4",
        "3x+2=11" to "x = 3",
        "sin(30°)" to "1/2 = 0.5",
        "√2·√8" to "4",
        "sum(k, k, 1, 100)" to "5050",
    ))

    @Test
    fun german() = check("de", listOf(
        "solve(x^2=9, x)" to "x = -3; 3",
        "derivative(x^3, x)" to "3x^2",
        "integral(x^2, x, 0, 3)" to "9",
        "limit(sin(x)/x, x, 0)" to "1",
        "factor(x^2+4x+4)" to "(x + 2)^2",
        "simplify((x^2-1)/(x-1))" to "x + 1 (x ≠ 1)",
        "sum(k, k, 1, 100)" to "5050",
        // Decimal commas: a comma between two digits is in its number.
        "calculate(2,5*4)" to "10",
        "2,5*4" to "10",
    ))

    /** What the Calculator's guide shows, read as it is written. */
    @Test
    fun asTheGuideWritesIt() = check("en", listOf(
        // An initial value after a comma is a line of its own.
        "y' = y, y(0) = 1" to "y = Ce^x [linear] | y = e^x",
        "y'' + y = 0, y(0) = 0, y'(0) = 1" to "y = C_2·sin(x) + C_1·cos(x) [constant_coefficients] | y = sin(x)",
        "truthtable(p → q)" to "1101 dnf ¬p ∨ q cnf ¬p ∨ q",
        "p ∧ ¬q ∨ r" to "01011101 dnf (p ∧ ¬q) ∨ r cnf (p ∨ r) ∧ (¬q ∨ r)",
        "p ⊕ q" to "0110 dnf (¬p ∧ q) ∨ (p ∧ ¬q) cnf (p ∨ q) ∧ (¬p ∨ ¬q)",
        // a_(n+1) is a subscript; × the cross product; a quotient with i as a + bi.
        "a_(n+1) = 2a_n + 1; a_0 = 0" to "a_n = 2^n - 1 | 0, 1, 3, 7, 15, 31, 63, 127",
        "(1, 2, 3) × (4, 5, 6)" to "(-3, 6, -3)",
        "1/(1 + i)" to "1/2 - i/2",
        "trapezoid(x^2, x, 0, 1)" to "trapezoid 6 rows → 0.3333740234",
        "convert(1 stone, kg)" to "317514659/50000000 kg",
        "convert(2 pints, l)" to "473176473/500000000 l",
        "convert(760 torr, atm)" to "1 atm",
        // Infinity by its English name or its sign.
        "integral(1/x^2, x, 1, infinity)" to "1",
        "limit(1/x, x, -∞)" to "0",
        "newton(x^2 - 2, 1)" to "newton 7 rows → 1.4142135624",
        "factor(a^2 + 2 ab + b^2)" to "(a + b)^2",
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
