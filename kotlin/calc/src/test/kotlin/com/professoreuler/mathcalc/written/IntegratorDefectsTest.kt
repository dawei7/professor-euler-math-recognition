package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sinh

/**
 * The defects the app integrator found (7 November 2026), each checked against 6c813ab and fixed where it still occurred, with the
 * right answer worked out here: transforms by their definition (a quadrature of e^(−st)·f(t)), derivatives and logarithms by hand,
 * η(1/2) = (1 − √2)ζ(1/2) from mpmath.
 */
class IntegratorDefectsTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result
    private fun answer(text: String): String = answerOf(calc(text))
    private fun editor(latex: String): String = answerOf(Reports.of(TextInput.read(latex)).result)

    /** ∫₀^∞ e^(−st) f(t) dt by the test's own quadrature (s large enough). */
    private fun transform(s: Double, f: (Double) -> Double) = integrate(0.0, 60.0, 1e-13) { t -> exp(-s * t) * f(t) }

    /** 1, 2: laplace(sinh(t)), laplace(cosh(3t)) and laplace(sinh(a t)) are answered (they were at 6c813ab), written with s first. */
    @Test
    fun laplaceWithSFirst() {
        assertEquals("1/((s - 1)(s + 1))", answer("laplace(sinh(t))"))
        assertEquals("s/((s - 3)(s + 3))", answer("laplace(cosh(3t))"))
        assertEquals("a/((s - a)(a + s))", answer("laplace(sinh(a t))"))
        assertEquals("1/(s - a)", answer("laplace(e^(a t))"))
        assertEquals("2/(s - a)^3", answer("laplace(t^2 e^(a t))"))
        assertEquals("1/(s - a)^2", answer("laplace(t e^(a t))"))
        assertEquals("1/(a + s)", answer("laplace(e^(-a t))"))
        // The values, against the definition at a = 0.7, s = 3.
        for ((text, f) in listOf<Pair<String, (Double) -> Double>>("laplace(sinh(a t))" to { t -> sinh(0.7 * t) }, "laplace(e^(a t))" to { t -> exp(0.7 * t) },
                "laplace(t^2 e^(a t))" to { t -> t * t * exp(0.7 * t) })) {
            val e = (calc(text).outcome as Outcome.Expression).result
            assertEquals(text, transform(3.0, f), Numeric.eval(e, mapOf("a" to 0.7, "s" to 3.0)), 1e-10)
        }
    }

    /** 3: a field's entries in x, y (and z) with constants: in all its coordinates. */
    @Test
    fun fieldsWithConstantsAndAZero() {
        assertEquals("a + b", answer("divergence((a x, b y, 0))"))
        assertEquals("(0, 0, -2a)", answer("curl((a y, -a x, 0))"))
        assertEquals("a + b + c", answer("divergence((a x, b y, c z))"))
        assertEquals("(0, 0, -2)", answer("curl((y, -x, 0))"))
    }

    /** 4: d/dx typed anywhere in a line reads as the editor's \frac{d}{dx}. */
    @Test
    fun derivativesTypedAnywhere() {
        assertEquals("2x + 2", answer("2 + d/dx x^2"))
        assertEquals("2x + 1", answer("(d/dx x^2) + 1"))
        assertEquals("equal: 2x | 2x", answer("d/dx x^2 = 2x"))
        assertEquals("9x^2", answer("3 d/dx x^3"))
        for ((typed, latex) in listOf("2 + d/dx x^2" to "2+\\frac{d}{dx}x^{2}", "(d/dx x^2) + 1" to "\\left(\\frac{d}{dx}x^{2}\\right)+1",
                "d/dx x^2 = 2x" to "\\frac{d}{dx}x^{2}=2x")) {
            assertEquals(typed, answer(typed), answerOf(MathCalc.calculate(latex)))
        }
        // The start of a line as before: d/dx takes all that follows.
        assertEquals("2x", answer("d/dx x^2"))
        assertEquals("2xy", answer("d/dy (x y^2)"))
    }

    /** 5: one limit for the order of a derivative, however it is asked for (100; sin's 25th derivative is cos). */
    @Test
    fun oneLimitForTheOrder() {
        assertEquals("cos(x)", answer("derivative(sin(x), x, 25)"))
        assertEquals("cos(x)", answer("d^25/dx^25 sin(x)"))
        assertEquals("0", answer("derivative(x^3, x, 100)"))
        assertEquals("TOO_LARGE", answer("derivative(sin(x), x, 101)"))
        assertEquals("TOO_LARGE", answer("d^101/dx^101 sin(x)"))
        assertEquals("ARGUMENTS", answer("derivative(sin(x), x, 0)"))
    }

    /** 6: ∫ 0 dx is C. */
    @Test
    fun theIntegralOfZero() {
        assertEquals("C", answer("integral(0, x)"))
        assertEquals("0", answer("integral(0, x, 1, 2)"))
        assertEquals("5x + C", answer("integral(5, x)"))
    }

    /** 7: a typed base is everything after _ up to the bracket: log_1.04(2) = ln 2/ln 1.04 = 17.6729876851…. */
    @Test
    fun typedLogarithmBases() {
        val v = (calc("log_1.04(2)").outcome as Outcome.Value)
        assertTrue(abs(Numeric.value(v.exact!!)!! - kotlin.math.ln(2.0) / kotlin.math.ln(1.04)) < 1e-12)
        assertEquals("log_(ab)(8)", answer("log_ab(8)"))
        assertEquals("log_(2x)(8)", answer("log_2x(8)"))
        assertEquals(answer("log_ab(8)"), answerOf(MathCalc.calculate("\\log_{ab}\\left(8\\right)")))
        assertEquals("3", answer("log_10(1000)"))
        assertEquals("3", answer("log_2(8)"))
    }

    /** 8: scientific notation in a conversion. */
    @Test
    fun scientificNotationInAConversion() {
        assertEquals("3/2 km", answer("convert(1.5e3 m, km)"))
        assertEquals("5/2 m", answer("convert(2.5e-3 km, m)"))
    }

    /** 9: a line through points with roots, collected in x: (2 − √2)x + √2, no "+ −" (regression's equation likewise). */
    @Test
    fun linesCollected() {
        val l = (calc("line((0, sqrt(2)), (1, 2))").outcome as Outcome.LineThrough).line
        assertEquals("x(2 - √2) + √2", Format.expr(l.equation!!.second, Style.PLAIN, latex = false))
        val r = (calc("regression((1, sqrt(2)), (2, 2), (3, 3))").outcome as Outcome.Regression).line
        val text = Format.expr(r.equation, Style.PLAIN, latex = false)
        assertEquals("x(3/2 - √2/2) + 4√2/3 - 4/3", text)
        assertTrue(text, "+ -" !in text)
        // The same line: at x = 0, 1, 2 the least-squares values by hand, slope (3 − √2)/2 and intercept (4√2 − 4)/3.
        for (x in listOf(0.0, 1.0, 2.0)) {
            val want = (3 - Math.sqrt(2.0)) / 2 * x + (4 * Math.sqrt(2.0) - 4) / 3
            assertEquals(want, Numeric.eval(r.equation, mapOf("x" to x)), 1e-12)
        }
    }

    /**
     * 10: Σ(−1)ᵏ/√k from 1 is −(1 − √2)ζ(1/2) = −0.60489864342163 (mpmath), typed and from the editor alike, within milliseconds
     * (it took over three seconds either way, and its ninth digit was wrong).
     */
    @Test
    fun alternatingSeriesQuickly() {
        val editorLatex = "\\operatorname{sum}\\left(\\frac{\\left(-1\\right)^{k}}{\\sqrt{k}}, k, 1, \\infty \\right)"
        for (text in listOf("sum((-1)^k/sqrt(k), k, 1, infinity)", editorLatex)) {
            answer(text)
            val start = System.nanoTime()
            assertEquals(text, "≈ -0.6048986434", if (text.startsWith("sum")) answer(text) else editor(text))
            assertTrue(System.nanoTime() - start < 1_000_000_000L)
        }
        // Others against mpmath's nsum: Σ(−1)ᵏ/ln k from 2, Σ(−1)ᵏ·k/(k² + 1) from 1, Σ(−1)ᵏ/∛k from 1.
        assertEquals("≈ 0.9242998972", answer("sum((-1)^k/ln(k), k, 2, infinity)"))
        assertEquals("≈ -0.2696105027", answer("sum((-1)^k k/(k^2 + 1), k, 1, infinity)"))
        assertEquals("≈ -0.5717528338", answer("sum((-1)^k/k^(1/3), k, 1, infinity)"))
    }

    /** And leastsquares of a consistent system is its solution (as at 6c813ab). */
    @Test
    fun leastSquaresOfAConsistentSystem() = assertEquals("x = 1, y = 1", answer("leastsquares(x + y = 2, x - y = 0, 2x + y = 3)"))

    private fun others(text: String): Set<Mode> =
        Reports.of(TextInput.read(text)).panels.filter { it.kind == PanelKind.CHOICE }.mapNotNull { it.choice?.mode }.toSet()

    /**
     * A command written as the list of its arguments has no other panels (irr's report showed the cash flows' column, their
     * transpose, their statistics and "no rule found" under its answer); points keep the other curves through them, a field its
     * other derivatives, a matrix what else works on it; a formula keeps every other reading (the integrator, 7 November 2026).
     */
    @Test
    fun aCommandsReportHoldsOnlyWhatBelongsToIt() {
        for (text in listOf("irr(-1000, 300, 400, 500)", "amortization(10000, 5%, 4)", "equalprincipal(10000, 5%, 4)",
                "straightline(10000, 1000, 5)", "decliningbalance(10000, 20%, 5)", "stats(1, 2, 3)", "sequence(1, 4, 9, 16)",
                "convert(5 km, mi)")) {
            assertEquals(text, emptySet<Mode>(), others(text))
        }
        for (text in listOf("interpolate((0, 1), (1, 3), (2, 7))", "regression((1, 2), (2, 4), (3, 5))", "spline((0, 0), (1, 1), (2, 0))")) {
            val o = others(text)
            assertTrue("$text $o", o.all { CommandFunctions.shapeOf(it) == CommandFunctions.Shape.POINTS })
            assertTrue("$text $o", o.isNotEmpty())
        }
        assertEquals(setOf(Mode.DIVERGENCE, Mode.JACOBIAN), others("curl((y z, x z, x y))"))
        val svd = others("svd([[1, 2], [3, 4]])")
        assertTrue("$svd", Mode.DETERMINANT in svd && Mode.REGRESSION !in svd)
        // A formula's own readings stay: the derivative of x² sin x beside its graph and integral.
        val d = others("derivative(x^2 sin(x), x)")
        assertTrue("$d", Mode.GRAPH in d && Mode.INTEGRAL in d)
        assertTrue(Mode.EIGEN in others("det([[1, 2], [3, 4]])"))
    }

    /** A fitted coefficient whose certified digits are 1 is left out: y = e^(…), never 1e^(…), which reads as 1·10^(…). */
    @Test
    fun aFittedCoefficientOfOneIsLeftOut() {
        val exp = answer("fitexp((0, 1), (1, 2), (2, 4), (3, 8))")
        assertTrue(exp, exp.endsWith("y = e^(0.6931471806x)"))
        val pow = answer("fitpow((1, 1), (2, 4), (3, 9))")
        assertTrue(pow, pow.endsWith("y = x^(2)"))
        // Other coefficients stay as they are.
        val three = answer("fitexp((0, 3), (1, 6), (2, 12))")
        assertTrue(three, three.endsWith("y = 3e^(0.6931471806x)"))
    }

    private fun digits(text: String): Boolean = Reports.of(TextInput.read(text)).panels.any { it.kind == PanelKind.DIGITS }

    /**
     * The digits of the input's value stand only beside an answer that is that value: frombase(11111111, 2) is 255, and its
     * report showed the digits of 11111111; derivative(5, x), integral(5, x), laplace(1) and trapezoid's approximation had the
     * input's digits under another answer. tobase and frombase also show their answer alone (the integrator, 7 November 2026).
     */
    @Test
    fun theDigitsStandOnlyBesideTheInputsOwnValue() {
        for (text in listOf("frombase(11111111, 2)", "tobase(-255, 16)", "derivative(5, x)", "integral(5, x)", "laplace(1)",
                "trapezoid(x^2, x, 0, 1)", "simpson(x^2, x, 0, 1)", "volumex(2, 0, 1)")) {
            assertTrue(text, !digits(text))
        }
        for (text in listOf("sqrt(2)", "2^(1/3) + 1", "integral(x^2, x, 0, 1)", "factor(360)", "polar(1 + i)", "simplify(sqrt(8))", "ln(-2)")) {
            assertTrue(text, digits(text))
        }
        assertEquals(emptySet<Mode>(), others("tobase(-255, 16)"))
        assertEquals(emptySet<Mode>(), others("frombase(FF, 16)"))
    }
}
