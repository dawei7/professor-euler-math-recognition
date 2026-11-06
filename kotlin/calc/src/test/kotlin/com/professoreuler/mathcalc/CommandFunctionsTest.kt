package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every command as a function: each worked out, written as its call by [CommandFunctions.latex] and read back to the same
 * answer, with or without spaces after its commas, and the names are one operation each, apart from every value function, unit and letter.
 */
class CommandFunctionsTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    /** At least one call of every function, as the guide writes them. */
    private val calls = listOf(
        "solve(x^2 - 5x + 6 = 0, x)",
        "solve(v = s/t, t)",
        "solve(x + y = 5, x - y = 1)",
        "solve(x^2 - 4 > 0, x)",
        "factor(x^2 - 9)",
        "expand((x + 2)^3)",
        "simplify((x^2 - 1)/(x - 1))",
        "calculate(2^10)",
        "partialfractions(1/(x^2 - 1))",
        "derivative(x^2 sin(x), x)",
        "derivative(x^2 y, y)",
        "derivative(x^4, x, 2)",
        "integral(1/x, x)",
        "integral(x^2, x, 0, 3)",
        "integral(e^(-x), x, 0, infinity)",
        "limit(sin(x)/x, x, 0)",
        "limit(1/x, x, 0+)",
        "sum(1/k^2, k, 1, infinity)",
        "sum(k, k, 1, n)",
        "taylor(e^x, x, 1)",
        "taylor(tan(x), x, 0, 7)",
        "plot(x^2 - 2x)",
        "det([[1, 2], [3, 4]])",
        "inverse([[1, 2], [3, 4]])",
        "eigenvalues([[2, 1], [1, 2]])",
        "rref([[1, 2, 3], [4, 5, 6]])",
        "transpose([[1, 2], [3, 4]])",
        "decompose([[4, 3], [6, 3]])",
        "expm([[0, 1], [0, 0]])",
        "gradient(x^2 y)",
        "divergence((x^2, y^2, z^2))",
        "curl((y, -x, 0))",
        "jacobian((x y, x + y))",
        "laplacian(x^2 + y^2)",
        "leastsquares(x + y = 1, x - y = 0, x + 2y = 3)",
        "extrema(x y, x + y = 10)",
        "laplace(e^t cos(t))",
        "invlaplace(1/(s^2 + 1))",
        "fourier(x)",
        "fourier(x^2, 2)",
        "newton(x^2 - 2, 1)",
        "bisection(x^3 - x - 1, 1, 2)",
        "dsolve(y' = y, y(0) = 1)",
        "euler(y' = y, y(0) = 1)",
        "rungekutta(y' = y, y(0) = 1)",
        "trapezoid(x^2, x, 0, 1)",
        "simpson(x^2, x, 0, 1)",
        "polar(1 + i)",
        "residues(e^z/z^2)",
        "facts(97)",
        "stats(12, 15, 11, 18, 15, 14)",
        "sequence(3, 7, 13, 21, 31)",
        "regression((1, 2), (2, 4), (3, 5))",
        "truthtable(p ∧ q → r)",
        "triangle(a = 3, b = 4, c = 5)",
        "triangle(a = 5, b = 7, gamma = 60°)",
        "conic(x^2/4 + y^2 = 1)",
        "line((1, 2), (3, 6))",
        "plane((1, 0, 0), (0, 1, 0), (0, 0, 1))",
        "diophantine(3x + 5y = 1)",
        "convert(5 km, mi)",
        "convert(300 K, °C)",
        "complexplot(z^2 - 1)",
        "complexplot(1/z, 1 + i, 2)",
        "complexplot(w^3 - 1, w, 0, 2)",
        "venn((A ∪ B) ∖ C)",
        "svd([[3, 0], [4, 5]])",
        "svd([[1, 2], [3, 4]])",
        "jordan([[2, 2, 3], [1, 3, 3], [-1, -2, -2]])",
        "dsolve(x' = 3x + y, y' = x + 3y, x(0) = 2, y(0) = 0)",
        "dsolve(y'' + y = 0, y(0) = 0, y(pi) = 0)",
        "odeseries(y'' + y = 0, y(0) = 1, y'(0) = 0)",
        "odeseries(y'' - x y = 0, 0, 8)",
        "dormandprince(y' = y, y(0) = 1)",
        "dormandprince(y' = x + y, y(0) = 1, 2)",
        "slopefield(y' = x - y)",
        "phaseportrait(x' = y, y' = -x)",
        "interpolate((1, 2), (2, 3), (3, 5))",
        "spline((0, 0), (1, 1), (2, 0), (3, 1))",
        "tobase(255, 2)",
        "tobase(2^10 + 1, 16)",
        "frombase(FF, 16)",
        "frombase(11111111, 2)",
    )

    @Test
    fun everyFunctionIsWorkedOutAndWrittenAsItsCall() {
        val wrong = ArrayList<String>()
        val used = HashSet<String>()
        for (call in calls) {
            val typed = TextInput.read(call)
            val answer = answer(call)
            if (answer.startsWith("FAILED")) wrong += "$call: $answer"
            val written = CommandFunctions.latex(typed)
            if (written == null) {
                wrong += "$call: written as no call"
                continue
            }
            used += Regex("""\\operatorname\{(\w+)\}""").find(written)!!.groupValues[1]
            // The call written out is read back to the same answer and written again the same.
            val again = answer(written)
            if (again != answer) wrong += "$call → $written: $again, not $answer"
            val twice = CommandFunctions.latex(TextInput.read(written))
            if (twice != written) wrong += "$call: $written written again as $twice"
        }
        // Every function has its call here.
        val missing = CommandFunctions.ALL.map { it.name } - used
        if (missing.isNotEmpty()) wrong += "no call of $missing"
        assertEquals("", wrong.joinToString("\n"))
    }

    /** Typed on the keys, a call has no spaces: its commas separate its arguments all the same. */
    @Test
    fun aCallWithoutSpacesReadsTheSame() {
        val wrong = ArrayList<String>()
        for (call in calls) {
            val tight = call.replace(", ", ",")
            if (tight == call) continue
            val got = answer(tight)
            if (got != answer(call)) wrong += "$tight: $got, not ${answer(call)}"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun aFunctionTypedAsSuch() {
        val cases = listOf(
            "solve(x^2 = 4, x)" to "x = -2; 2",
            "integral(x^2, x, 0, 3)" to "9",
            "integral(x^2, 0, 3)" to "9",
            "limit(sin(x)/x, x, 0)" to "1",
            "sum(k, k, 1, 100)" to "5050",
            "derivative(x^3, x)" to "3x^2",
            "factor(x^2 - 1)" to "(x - 1)(x + 1)",
            "solve(x + y = 5, x - y = 1)" to "x = 3, y = 2",
            "convert(5 km, mi)" to "78125/25146 mi",
            "stats(12, 15, 11, 18, 15, 14)" to "n=6 mean 85/6 median 29/2 sd √222/6 q1 12 q3 15",
            "triangle(a = 3, b = 4, c = 5)" to "sides 3, 4, 5 angles 36.86989765, 53.13010235, 90 area 6",
        )
        val wrong = cases.mapNotNull { (text, expected) -> answer(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun aNameIsAFunctionOnlyBeforeItsBracket() {
        assertNull(CommandFunctions.read("solve"))
        assertNull(CommandFunctions.read("solve(x) + (y)"))
        assertNull(CommandFunctions.read("mean(1, 2, 3)"))
        // A value function stays one: the mean of three numbers is a number.
        assertEquals("2", answer("mean(1, 2, 3)"))
    }

    @Test
    fun everyNameIsOneOperation() {
        val names = CommandFunctions.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
        for (n in names) {
            assertTrue("$n is a value function", n !in Discrete.NAMES)
            assertTrue("$n is a unit", !Units.known(n))
            assertTrue("$n is too short to be a name", n.length >= 3)
        }
        // Each mode has one function.
        val modes = CommandFunctions.ALL.mapNotNull { it.mode }
        assertEquals(modes.size, modes.toSet().size)
    }
}
