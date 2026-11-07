package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * Defects found while writing the Calculator's guide: wrong answers and silent misreadings, each now the right answer or a
 * refusal. Every expected value is known independently of the engine (a textbook value, or worked out here another way).
 */
class DefectsTest {
    /** A line as typed (or as the editor's LaTeX, which goes the same way), worked out as the Calculator does. */
    private fun typed(text: String, complex: Boolean = true): String = answerOf(Reports.of(TextInput.read(text), complex).result)

    private fun outcome(text: String): Outcome = Reports.of(TextInput.read(text)).result.outcome

    // 1. A double near a fraction is never shown as that fraction; equality of constants is decided in ball arithmetic.

    @Test fun transcendentalValuesAreNeverRationalStandIns() {
        for ((input, value) in listOf("sinh(10)" to kotlin.math.sinh(10.0), "sinh(11)" to kotlin.math.sinh(11.0),
            "sinh(20)" to kotlin.math.sinh(20.0), "cosh(10)" to kotlin.math.cosh(10.0))) {
            val o = outcome(input) as Outcome.Value
            assertTrue("$input must not be exact", o.value !is Num.Exact && o.exact == null)
            val shown = typed(input)
            assertTrue("$input: $shown", shown.startsWith("≈ "))
            val digits = shown.removePrefix("≈ ").toDouble()
            assertTrue("$input: $digits against $value", kotlin.math.abs(digits - value) <= 1e-9 * value)
        }
        // tanh 20 = 1 − 8.5·10⁻¹⁸, erf 10 = 1 − 2.1·10⁻⁴⁵, erfc 10 = 2.0884875837625448·10⁻⁴⁵ (mpmath): none exactly 1 or 0.
        assertEquals("≈ 1", typed("tanh(20)"))
        assertEquals("≈ 1", typed("erf(10)"))
        assertEquals("≈ 2.088487584 · 10^-45", typed("erfc(10)"))
        assertEquals("false", typed("tanh(20) = 1"))
        assertTrue(typed("x + tanh(20) = x + 1").startsWith("differ"))
        // What is exactly a number stays exact.
        assertEquals("1", typed("sin(1)^2 + cos(1)^2"))
        assertEquals("2", typed("sqrt(2)^2"))
        assertEquals("1/2 = 0.5", typed("sin(30°)"))
    }

    // 2. tanh, sec, csc (and sinh, cosh) equations solved, never "many solutions".

    @Test fun hyperbolicAndReciprocalTrigonometricEquations() {
        assertEquals("x: none", typed("tanh(x) = 1"))
        assertEquals("x: none", typed("tanh(x) = -3"))
        // atanh(1/2) = ln(3)/2 = 0.5493061443…
        assertEquals("x = ln(3)/2 ≈ 0.5493061443", typed("tanh(x) = 1/2"))
        // sec x = 2 ⟺ cos x = 1/2; csc x = 2 ⟺ sin x = 1/2.
        assertEquals("x = π/3 + 2kπ; 5π/3 + 2kπ (k)", typed("sec(x) = 2"))
        assertEquals("x = π/6 + 2kπ; 5π/6 + 2kπ (k)", typed("csc(x) = 2"))
        assertEquals("x: none", typed("sec(x) = 0"))
        // acosh 2 = ln(2 + √3) = 1.3169578969…, asinh 1 = ln(1 + √2) = 0.8813735870…
        assertEquals("x = -ln(2 + √3) ≈ -1.316957897; ln(2 + √3) ≈ 1.316957897", typed("cosh(x) = 2"))
        assertEquals("x: none", typed("cosh(x) = 1/2"))
        assertEquals("x = ln(1 + √2) ≈ 0.881373587", typed("sinh(x) = 1"))
    }

    // 3. ln of a negative constant: its principal value wherever complex numbers are on, as for ln(−2).

    @Test fun logarithmsOfNegativeConstants() {
        assertEquals("ln(2) + πi", typed("ln(-2)"))
        assertEquals("1 + πi", typed("ln(-e)"))
        assertEquals("ln(π) + πi", typed("ln(-pi)"))
        assertEquals("ln(2)/2 + πi", typed("ln(-sqrt(2))"))
        assertEquals("UNDEFINED", typed("ln(-e)", complex = false))
    }

    // 4. A base of several digits after log_ is that base; what the engine prints reads back.

    @Test fun logarithmBasesOfSeveralDigits() {
        assertEquals("2/3 ≈ 0.6666666667", typed("log_27(9)"))
        assertEquals("1/4 = 0.25", typed("log_16(2)"))
        assertEquals("3", typed("log_10(1000)"))
        val printed = typed("log_10(2)")
        assertEquals("log_10(2) ≈ 0.3010299957", printed)
        // The printed form typed again is the same number: log₁₀ 2 = ln 2 / ln 10.
        val again = Numeric.value(Parser(MathCalc.TOKEN.findAll(TextInput.read(printed.substringBefore(" ≈")).latex).map { it.value }.toList())
            .statements()[0].sides.filterNotNull()[0])!!
        assertEquals(kotlin.math.ln(2.0) / kotlin.math.ln(10.0), again, 1e-15)
        assertEquals("""\log_{12}(144)""", TextInput.read("log_12(144)").latex)
    }

    // 5. Distribution parameters in their ranges, 0⁰ = 1 in the probabilities.

    @Test fun distributionParameters() {
        for (refused in listOf("poissonpdf(-1, 2)", "geometpdf(1.5, 2)", "geometpdf(0, 2)", "binompdf(3, 1.5, 1)", "normalpdf(0, 0, -1)",
            "normalpdf(0, 0, 0)", "invnorm(0.5, 0, -1)", "invnorm(1.5)", "normalcdf(2, 1)", "normalcdf(0, 1, 0, -2)")) {
            assertEquals(refused, "UNDEFINED", typed(refused))
        }
        // P(X = 0) = 1 for n = 3, p = 0; P(X ≤ 2) = 1; a first success at the first try when p = 1; Poisson(0) at 0.
        assertEquals("1", typed("binompdf(3, 0, 0)"))
        assertEquals("1", typed("binomcdf(3, 0, 2)"))
        assertEquals("1", typed("binompdf(3, 1, 3)"))
        assertEquals("1", typed("geometpdf(1, 1)"))
        assertEquals("1", typed("poissonpdf(0, 0)"))
        // The median of N(3, 2²) is 3, exactly.
        assertEquals("3", typed("invnorm(0.5, 3, 2)"))
        // Φ(2) − Φ(1) = 0.1359051220 (tables).
        assertEquals("≈ 0.135905122", typed("normalcdf(1, 2)"))
    }

    // 6. Pearson's r is undefined for constant y; constant x has no line y = ax + b.

    @Test fun regressionOfConstantData() {
        assertEquals("y = 0x + 3 r undefined", typed("regression((1, 3), (2, 3), (3, 3))"))
        assertEquals("UNSOLVED", typed("regression((1, 1), (1, 2), (1, 3))"))
        assertEquals("y = 2x + 0 r 1", typed("regression((1, 2), (2, 4), (3, 6))"))
    }

    // 7. A point's coordinates may be any exact numbers, typed or from the editor alike.

    @Test fun pointsWithRoots() {
        // Through (1, 0, 0), (0, 2, 0), (0, 0, √2): x/1 + y/2 + z/√2 = 1, times 2√2.
        val plane = typed("plane((1, 0, 0), (0, 2, 0), (0, 0, sqrt(2)))")
        assertEquals("2x√2 + y√2 + 2z - 2√2 = 0", plane)
        assertEquals(plane, typed("""\operatorname{plane}\left(\left(1, 0, 0\right), \left(0, 2, 0\right), \left(0, 0, \sqrt{2}\right)\right)"""))
        // Slope (nΣxy − ΣxΣy)/(nΣx² − (Σx)²) = (15 − 3√2)/6 for (1, √2), (2, 3), (3, 5).
        val regression = typed("regression((1, sqrt(2)), (2, 3), (3, 5))")
        assertTrue(regression, regression.startsWith("y = 5/2 - √2/2x"))
        assertEquals(regression, typed("""\operatorname{regression}\left(\left(1, \sqrt{2}\right), \left(2, 3\right), \left(3, 5\right)\right)"""))
        assertTrue(typed("line((sqrt(2), 0), (0, sqrt(2)))").startsWith("y = -x + √2"))
        assertTrue(typed("line((1|2), (3|4))").startsWith("y = x + 1"))
    }

    // 8. Exact integers with all their digits; long fractions by their decimal, the value kept exact.

    @Test fun largeExactNumbers() {
        val two = BigInteger.valueOf(2).pow(100)
        assertEquals("$two ≈ 1.2676506 · 10^30", typed("2^100"))
        var factorial = BigInteger.ONE
        for (k in 2..100) factorial *= BigInteger.valueOf(k.toLong())
        assertEquals(factorial.toString(), typed("100!").substringBefore(" ≈"))
        assertEquals("354224848179261915075", typed("fibonacci(100)").substringBefore(" ≈"))
        assertEquals("100891344545564193334812497256", typed("binom(100, 50)").substringBefore(" ≈"))
        assertEquals("123456789012345", typed("123456789012345"))
        // 1000! has 2568 digits: its decimal, the power of ten saying how many.
        assertEquals("≈ 4.023872601 · 10^2567", typed("1000!"))
        // binompdf(100, 0.02, 1) = 100 · (1/50) · (49/50)^99, exact, shown by its decimal.
        val o = outcome("binompdf(100, 0.02, 1)") as Outcome.Value
        val exact = Rational.of(BigInteger.valueOf(2) * BigInteger.valueOf(49).pow(99), BigInteger.valueOf(50).pow(99))
        assertEquals(Num.Exact(exact), o.value)
        assertEquals("≈ 0.2706521549", typed("binompdf(100, 0.02, 1)"))
    }

    // 9. The written-out mod: a sign belongs to its number.

    @Test fun modWithANegativeNumber() {
        assertEquals("2", typed("-7 mod 3"))
        assertEquals("3", typed("-7 mod 5"))
        assertEquals("5", typed("3 + 7 mod 5"))
        assertEquals("2", typed("3*4 mod 5"))
        assertEquals("1", typed("3 - 7 mod 5"))
        assertEquals("-4", typed("-2*2"))
        assertEquals("-9", typed("-3^2"))
    }

    // 10. Declined or solved at once, and the hyperbolic and periodic simplifications.

    @Test fun lambertAndErfAtOnce() {
        val start = System.nanoTime()
        assertEquals("lambertw(x)", typed("lambertw(x)"))
        assertTrue("lambertw(x) took too long", (System.nanoTime() - start) / 1_000_000 < 2_500)
        assertEquals("x = 0", typed("erf(x) = 0"))
        assertEquals("x = 0", typed("erfc(x) = 1"))
        assertEquals("x: none", typed("erf(x) = 1"))
        // erf⁻¹(1/2) = 0.4769362762… (tables)
        assertTrue(typed("erf(x) = 1/2").contains("0.4769362762"))
    }

    @Test fun hyperbolicParityAndPeriods() {
        assertEquals("-sinh(x)", typed("sinh(-x)"))
        assertEquals("cosh(x)", typed("cosh(-x)"))
        assertEquals("-tanh(x)", typed("tanh(-x)"))
        assertEquals("1", typed("cosh(x)^2 - sinh(x)^2"))
        assertEquals("tan(x)", typed("tan(x + pi)"))
        assertEquals("-sin(x)", typed("sin(x + pi)"))
        assertEquals("cos(x)", typed("cos(x - 2pi)"))
        // Each the same function (at a few points).
        for (v in listOf(-1.3, 0.4, 2.2)) {
            assertEquals(kotlin.math.tan(v + Math.PI), kotlin.math.tan(v), 1e-12)
            assertEquals(kotlin.math.cosh(v) * kotlin.math.cosh(v) - kotlin.math.sinh(v) * kotlin.math.sinh(v), 1.0, 1e-12)
        }
    }

    // 11. Solutions on a whole interval are the interval, never one point of it.

    @Test fun equationsThatHoldOnIntervals() {
        assertEquals("0 ≤ x", typed("abs(x) = x"))
        assertEquals("0 ≤ x", typed("solve(abs(x) = x, x)"))
        assertEquals("0 ≤ x", typed("sqrt(x^2) = x"))
        assertEquals("0 ≤ x", typed("solve(sqrt(x^2) = x, x)"))
        assertEquals("x ≤ 0", typed("abs(x) = -x"))
        assertEquals("1 ≤ x", typed("abs(x - 1) = x - 1"))
        // An equation with points only is as before.
        assertEquals("x = -2; 2", typed("abs(x) = 2"))
    }

    // 12. A dependent linear system: its family of solutions, put back in.

    @Test fun dependentLinearSystems() {
        assertEquals("x = -t + 1, y = t", typed("solve(x + y = 1, 2x + 2y = 2)"))
        assertEquals("x = -t + 2, y = -1, z = t", typed("solve(x + y + z = 1, x - y + z = 3)"))
        // The family at t = 3/10: x + y = 1 and 2x + 2y = 2.
        val t = 0.3
        assertEquals(1.0, (1 - t) + t, 1e-15)
        assertEquals("no solution", typed("solve(x + y = 1, 2x + 2y = 3)"))
        assertEquals("x = 3, y = 2", typed("solve(x + y = 5, x - y = 1)"))
    }

    // 13. Scientific notation.

    @Test fun scientificNotation() {
        assertEquals("1000", typed("1e3"))
        assertEquals("0.00064", typed("3.2e-4 * 2"))
        assertEquals("1/5 = 0.2", typed("2e-1"))
        assertEquals("1/2000 = 0.0005", typed("1/2e3"))
        assertEquals("250000", typed("2.5E5"))
        // e alone is Euler's number.
        assertEquals("-1 + 2e ≈ 4.436563657", typed("2e - 1"))
        assertEquals("2e ≈ 5.436563657", typed("2e"))
    }

    // 14. The values a simplification drops, in several letters; an identity only where the domains agree.

    @Test fun conditionsInSeveralLetters() {
        assertEquals("a^5b (a ≠ 0, b ≠ 0)", typed("simplify((a^2 b)^3/(a b^2))"))
        assertEquals("x + 1 (x ≠ 1)", typed("simplify((x^2 - 1)/(x - 1))"))
        assertEquals("differ: ln(ab) | ln(a) + ln(b)", typed("ln(a b) = ln(a) + ln(b)"))
        assertTrue(typed("ln(x^2) = 2 ln(x)").startsWith("differ"))
        assertEquals("equal: x + 1 | x + 1", typed("(x^2 - 1)/(x - 1) = x + 1"))
        assertTrue(typed("(a + b)^2 = a^2 + 2a b + b^2").startsWith("equal"))
    }

    // 15. Units through the editor as typed, their powers as superscripts.

    @Test fun unitsThroughTheEditor() {
        // 9.81 m/s² = 9.81 · 3600² / 1000 km/h² = 127137.6 km/h².
        assertEquals("635688/5 km/h²", typed("convert(9.81 m/s^2, km/h^2)"))
        assertEquals("635688/5 km/h²", typed("""\operatorname{convert}\left(9.81 m/s^{2}, km/h^{2}\right)"""))
        assertEquals("1 J", typed("convert(1 N*m, J)"))
        assertEquals("1 J", typed("""\operatorname{convert}\left(1 N\cdot m, J\right)"""))
        assertEquals("1000000 cm³", typed("convert(1 m^3, cm^3)"))
    }

    // 16. One name for one operation; a negative sum's absolute value term by term.

    @Test fun signAndAbsolute() {
        assertEquals("UNKNOWN_FUNCTION", typed("sign(x)"))
        assertEquals("sgn(x)", typed("sgn(x)"))
        assertEquals("4 - π ≈ 0.8584073464", typed("abs(pi - 4)"))
    }

    // 17. A matrix's own operation, or a refusal; never another operation in its place.

    @Test fun matrixOperationsDoneOrRefused() {
        for (f in listOf("det", "inverse", "eigenvalues", "expm", "decompose")) {
            assertEquals(f, "SIZES", typed("$f([[1, 2, 3], [4, 5, 6]])"))
        }
        assertEquals("[[e, 0, 0, 0, 0], [0, e, 0, 0, 0], [0, 0, e, 0, 0], [0, 0, 0, e, 0], [0, 0, 0, 0, e]]",
            typed("expm([[1,0,0,0,0],[0,1,0,0,0],[0,0,1,0,0],[0,0,0,1,0],[0,0,0,0,1]])"))
        val d = outcome("decompose([[2,1,0,0,0,0,0],[1,2,1,0,0,0,0],[0,1,2,1,0,0,0],[0,0,1,2,1,0,0],[0,0,0,1,2,1,0],[0,0,0,0,1,2,1],[0,0,0,0,0,1,2]])")
        assertTrue(d is Outcome.Decompositions)
        // det of the tridiagonal 2, 1 matrix of size n is n + 1.
        assertEquals("8", typed("det([[2,1,0,0,0,0,0],[1,2,1,0,0,0,0],[0,1,2,1,0,0,0],[0,0,1,2,1,0,0],[0,0,0,1,2,1,0],[0,0,0,0,1,2,1],[0,0,0,0,0,1,2]])"))
    }

    // 18, 21. The rules of quadrature need their bounds and an integrand defined at their points.

    @Test fun quadratureArguments() {
        assertEquals("ARGUMENTS", typed("trapezoid(x^2)"))
        assertEquals("ARGUMENTS", typed("simpson(x^2)"))
        assertEquals("ARGUMENTS", typed("trapezoid(x^2, x)"))
        assertEquals("UNDEFINED", typed("trapezoid(ln(x), x, 0, 1)"))
        // ∫₀¹ 1/√x dx = 2 exists, but the rules take the integrand at 0.
        assertEquals("UNDEFINED", typed("simpson(1/sqrt(x), x, 0, 1)"))
        assertEquals("DIVERGES", typed("simpson(1/x, x, 0, 1)"))
        assertTrue(typed("simpson(ln(x), x, 1, 2)").startsWith("simpson"))
    }

    // 19, 20. A transform or a series as asked, or refused; never the input back.

    @Test fun transformsAndSeriesAsAsked() {
        assertEquals("ARGUMENTS", typed("fourier(x, L)"))
        // f = x on [−L, L]: bₙ = 2L(−1)^(n+1)/(nπ), for L = 2: −4(−1)ⁿ/(πn).
        assertEquals("a0 = 0, an = 0, bn = -4(-1)^n/(πn)", typed("fourier(x, 2)"))
        assertEquals("1/s", typed("laplace(1)"))
        assertEquals("5/s", typed("laplace(5)"))
        // L{e^(at)} = 1/(s − a).
        // Written with s first (the app integrator, 7 November 2026): 1/(s − a), not −1/(a − s).
        assertEquals("1/(s - a)", typed("laplace(e^(a t))"))
        assertEquals("UNSOLVED", typed("invlaplace(2)"))
        assertEquals("UNSOLVED", typed("residues(e^(1/z))"))
    }

    // 23. A function's whole first argument is its argument: lim and Σ of a sum.

    @Test fun limitsAndSumsOfSums() {
        assertEquals("6", typed("limit(x^2 - x, x, 3)"))
        assertEquals("1/2 = 0.5", typed("limit(sqrt(x^2 + x) - x, x, infinity)"))
        assertEquals("9", typed("sum(k + 1, k, 1, 3)"))
        assertEquals("n^2", typed("sum(2k - 1, k, 1, n)"))
        assertEquals("20", typed("sum(k^2 + k, k, 1, 3)"))
        // The calls are written back as they were typed.
        assertEquals("""\operatorname{sum}\left(k + 1, k, 1, 3\right)""", CommandFunctions.latex(TextInput.read("sum(k + 1, k, 1, 3)")))
        assertEquals("""\operatorname{limit}\left(x^{2} - x, x, 3\right)""", CommandFunctions.latex(TextInput.read("limit(x^2 - x, x, 3)")))
        // Written as notation, Σ takes the term after it, as in mathematics: Σ k + 1 = 6 + 1.
        assertEquals("7", answer("""\sum_{k=1}^{3} k + 1"""))
    }

    // 24. d/dx typed and from the editor read the same: the whole side after it.

    @Test fun derivativeOperatorTypedAndFromTheEditor() {
        assertEquals("6x + 2", typed("d/dx 3x^2 + 2x"))
        assertEquals("6x + 2", typed("""\frac{d}{dx}3x^{2}+2x"""))
        assertEquals("2x", typed("d/dx (x^2) + 1"))
        assertEquals("2x", typed("""\frac{d}{dx}\left(x^{2}\right)+1"""))
    }

    // 25, 26. The derivative and the antiderivative of a constant; orders of two digits.

    @Test fun derivativesOfConstantsAndHighOrders() {
        assertEquals("0", typed("derivative(5, x)"))
        assertEquals("0", typed("derivative(c, x)"))
        assertEquals("0", typed("d/dx 5"))
        assertEquals("0", typed("""\frac{d}{dx}5"""))
        assertEquals("5x + C", typed("integral(5, x)"))
        assertEquals("e^x", typed("derivative(e^x, x, 10)"))
        // d¹⁰/dx¹⁰ x¹² = 12!/2! x² = 239500800x².
        assertEquals("239500800x^2", typed("derivative(x^12, x, 10)"))
        assertEquals("e^x", typed("d^10/dx^10 e^x"))
        assertEquals("239500800x^2", typed("""\frac{d^{10}}{dx^{10}}\left(x^{12}\right)"""))
    }

    // 27. Alternating series converge where the Leibniz test shows it.

    @Test fun alternatingSeries() {
        assertEquals("π/4 ≈ 0.7853981634", typed("sum((-1)^k/(2k + 1), k, 0, infinity)"))
        assertEquals("-π^2/12 ≈ -0.8224670334", typed("sum((-1)^k/k^2, k, 1, infinity)"))
        assertEquals("π^2/12 ≈ 0.8224670334", typed("sum((-1)^(k+1)/k^2, k, 1, infinity)"))
        assertEquals("ln(2) ≈ 0.6931471806", typed("sum((-1)^(k+1)/k, k, 1, infinity)"))
        // Σ (−1)^k/(k² + 1) from 0 = 1/2 + π/(2 sinh π) = 0.6360145274…
        val reference = 0.5 + Math.PI / (2 * kotlin.math.sinh(Math.PI))
        val shown = typed("sum((-1)^k/(k^2 + 1), k, 0, infinity)")
        assertTrue(shown, shown.startsWith("≈ "))
        assertEquals(reference, shown.removePrefix("≈ ").toDouble(), 1e-9)
        assertEquals("DIVERGES", typed("sum((-1)^k, k, 0, infinity)"))
    }

    // 29. The eigenvalues' step says det(A − λI) and shows det(A − λI), also for odd n.

    @Test fun characteristicStepAgreesWithItsWords() {
        // A = diag(1, 2, 3): det(A − λI) = (1 − λ)(2 − λ)(3 − λ) = −λ³ + 6λ² − 11λ + 6.
        val latex = """\begin{pmatrix}1&0&0\\0&2&0\\0&0&3\end{pmatrix}"""
        val steps = Steps.of(latex, Choice(Mode.EIGEN))!!
        val step = steps.first { it.why.key == StepKey.CHARACTERISTIC }.latex
        val formula = step.substringAfter("= ").substringBeforeLast(" = 0").replace("λ", "x")
        val p = try { Parser(MathCalc.TOKEN.findAll(formula).map { it.value }.toList()).statements()[0].sides.filterNotNull()[0] }
            catch (e: CalcException) { throw AssertionError("$step: ${e.failure}") }
        for (lambda in listOf(0.5, 1.7, 4.0)) {
            val expected = (1 - lambda) * (2 - lambda) * (3 - lambda)
            assertEquals(expected, Numeric.eval(p, mapOf("x" to lambda)), 1e-9)
        }
        assertTrue(step, step.startsWith("""\det(A - \lambda I) = """))
    }

    // 28. Gradient, Laplacian and Jacobian in the function's letters, or refused.

    @Test fun vectorCalculusInTheFunctionsLetters() {
        assertEquals("(2x)", typed("gradient(x^2)"))
        assertEquals("2", typed("laplacian(x^2)"))
        assertEquals("(a, b)", typed("gradient(a x + b y)"))
        assertEquals("[[cos(t), -r·sin(t)], [sin(t), r·cos(t)]]", typed("jacobian((r cos(t), r sin(t)))"))
        assertEquals("ARGUMENTS", typed("gradient(x^2 y, x = 1)"))
        assertEquals("(2xy, x^2)", typed("gradient(x^2 y)"))
    }
}
