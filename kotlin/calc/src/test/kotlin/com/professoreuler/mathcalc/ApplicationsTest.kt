package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * tangentline, normalline, arclength, areabetween, volumex, volumey and meanvalue: textbook values (Stewart's and the usual
 * German Abitur tasks), and seeded tasks checked independently of the engine: by this test's own composite Simpson rule on
 * the formula itself (derivatives by central differences), and by closed forms the test knows (the area between a parabola and
 * a line through two of its points is |k|·(b − a)³/6, the disc volume of a line π∫(mx + q)²).
 */
class ApplicationsTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    private fun value(text: String): Double? {
        val o = Reports.of(TextInput.read(text)).result.outcome
        return (o as? Outcome.Value)?.value?.double
    }

    /** ∫ from a to b of f by Simpson's rule with 20 000 panels, the test's own. */
    private fun simpson(f: (Double) -> Double, a: Double, b: Double, n: Int = 20_000): Double {
        val h = (b - a) / n
        var s = f(a) + f(b)
        for (k in 1 until n) s += f(a + k * h) * if (k % 2 == 1) 4 else 2
        return s * h / 3
    }

    private fun near(a: Double?, b: Double, tol: Double = 1e-7) = a != null && abs(a - b) <= tol * maxOf(1.0, abs(b))

    @Test
    fun textbookValues() {
        val cases = listOf(
            "tangentline(x^2, 1)" to "y = 2x - 1 at (1, 1) slope 2",
            "tangentline(x^3 - 3x, x, 2)" to "y = 9x - 16 at (2, 2) slope 9",
            "tangentline(sin(x), x, 0)" to "y = x at (0, 0) slope 1",
            "tangentline(e^x, x, 0)" to "y = x + 1 at (0, 1) slope 1",
            "tangentline(x^2, x, a)" to "y = -a^2 + 2ax at (a, a^2) slope 2a",
            "normalline(x^2, x, 1)" to "y = -(1/2)x + 3/2 at (1, 1) slope 2",
            "normalline(sqrt(x), x, 4)" to "y = -4x + 18 at (4, 2) slope 1/4",
            "normalline(cos(x), x, 0)" to "x = 0 at (0, 1) slope 0",
            // No tangent at a kink, at a vertical tangent, outside the domain.
            "tangentline(abs(x), x, 0)" to "FAILED UNDEFINED",
            "tangentline(x^(1/3), x, 0)" to "FAILED UNDEFINED",
            "tangentline(ln(x), x, -1)" to "FAILED UNDEFINED",
            "tangentline(x^2, x, x)" to "FAILED ARGUMENTS",
            "tangentline(x^2)" to "FAILED ARGUMENTS",
            "tangentline(y = x^2, 1)" to "FAILED ARGUMENTS",
            // Arc lengths: Stewart's x^(3/2), the textbook x³/6 + 1/(2x), ln(cos x), a line.
            "arclength(x^(3/2), x, 0, 4)" to "-8/27 + 80√10/27 ≈ 9.073415289",
            "arclength(x^3/6 + 1/(2x), x, 1, 2)" to "17/12 ≈ 1.416666667",
            "arclength(ln(cos(x)), x, 0, pi/4)" to "ln(1 + √2) ≈ 0.881373587",
            "arclength(2x + 1, x, 0, 3)" to "3√5 ≈ 6.708203932",
            "arclength(x^2, x, 0, 1)" to "√5/2 + ln(2 + √5)/4 ≈ 1.478942858",
            "arclength(1/x, x, -1, 1)" to "FAILED UNDEFINED",
            // Areas.
            "areabetween(x^2, x)" to "1/6 ≈ 0.1666666667",
            "areabetween(x^2, x + 2)" to "9/2 = 4.5",
            "areabetween(x^3, x)" to "1/2 = 0.5",
            "areabetween(sin(x), cos(x), 0, pi)" to "2√2 ≈ 2.828427125",
            "areabetween(sin(x), 0, 0, 2pi)" to "4",
            "areabetween(e^(-x), 0, 0, infinity)" to "1",
            "areabetween(x^4 - 2x^2, 0)" to "16√2/15 ≈ 1.508494467",
            "areabetween(x, x + 1)" to "FAILED UNSOLVED",
            "areabetween(sin(x), cos(x))" to "FAILED MANY_SOLUTIONS",
            "areabetween(1/x, 0, -1, 1)" to "FAILED DIVERGES",
            // Volumes: the cone, the sphere, Gabriel's horn, shells.
            "volumex(sqrt(x), x, 0, 4)" to "8π ≈ 25.13274123",
            "volumex(r/h x, x, 0, h)" to "(1/3)πhr^2",
            "volumex(sqrt(r^2 - x^2), x, -r, r)" to "(4/3)πr^3",
            "volumex(1/x, x, 1, infinity)" to "π ≈ 3.141592654",
            "volumey(x^2, x, 0, 2)" to "8π ≈ 25.13274123",
            "volumey(sin(x), x, 0, pi)" to "2π^2 ≈ 19.7392088",
            "volumey(1 - x^2, x, 0, 1)" to "π/2 ≈ 1.570796327",
            "volumey(x, x, -1, 1)" to "FAILED ARGUMENTS",
            // Mean values.
            "meanvalue(x^2, x, 0, 3)" to "3",
            "meanvalue(sin(x), x, 0, pi)" to "2/π ≈ 0.6366197724",
            "meanvalue(a x + b, x, 0, 1)" to "(1/2)a + b",
            "meanvalue(x, 1, 1)" to "FAILED DIVISION_BY_ZERO",
        )
        val wrong = cases.mapNotNull { (t, want) -> answer(t).takeIf { it != want }?.let { "$t: $it, not $want" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun integralsAgreeWithTheTestsOwnQuadrature() {
        fun d(f: (Double) -> Double, x: Double) = (f(x + 1e-5) - f(x - 1e-5)) / 2e-5
        val cases = listOf<Triple<String, Double, Double>>(
            Triple("arclength(cosh(x), x, 0, 1)", simpson({ x -> sqrt(1 + d(::coshOf, x).let { it * it }) }, 0.0, 1.0), 1e-7),
            Triple("arclength(sin(x), x, 0, pi)", simpson({ x -> sqrt(1 + kotlin.math.cos(x).let { it * it }) }, 0.0, PI), 1e-7),
            Triple("areabetween(sin(x), x/2, -2, 2)", simpson({ x -> abs(kotlin.math.sin(x) - x / 2) }, -2.0, 2.0, 200_000), 1e-6),
            Triple("volumex(e^x, x, 0, 1)", PI * simpson({ x -> kotlin.math.exp(2 * x) }, 0.0, 1.0), 1e-7),
            Triple("volumey(cos(x), x, 0, pi)", 2 * PI * simpson({ x -> abs(x * kotlin.math.cos(x)) }, 0.0, PI, 200_000), 1e-6),
            Triple("meanvalue(e^(-x^2), x, 0, 1)", simpson({ x -> kotlin.math.exp(-x * x) }, 0.0, 1.0), 1e-7),
        )
        val wrong = cases.mapNotNull { (t, want, tol) -> value(t).takeIf { !near(it, want, tol) }?.let { "$t: $it, not $want" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    private fun coshOf(x: Double) = kotlin.math.cosh(x)

    @Test
    fun seededTasksHold() {
        val r = Random(61106)
        val wrong = ArrayList<String>()
        var declined = 0
        repeat(80) {
            // A parabola k(x − a)(x − b) + (cx + d) and the line cx + d: the area between them is |k|·(b − a)³/6.
            val a = r.nextInt(-5, 5)
            val b = a + r.nextInt(1, 6)
            val k = listOf(-3, -2, -1, 1, 2, 3)[r.nextInt(6)]
            val c = r.nextInt(-4, 5)
            val d = r.nextInt(-4, 5)
            val task = "areabetween(($k)(x - ($a))(x - ($b)) + ($c)x + ($d), ($c)x + ($d))"
            val got = value(task)
            if (got == null) declined++ else if (!near(got, abs(k) * Math.pow((b - a).toDouble(), 3.0) / 6, 1e-9)) wrong += "$task: $got"
        }
        repeat(80) {
            // The tangent of a cubic at a whole number, against the power rule worked here.
            val p = List(4) { r.nextInt(-4, 5) }
            val at = r.nextInt(-3, 4)
            val task = "tangentline((${p[3]})x^3 + (${p[2]})x^2 + (${p[1]})x + (${p[0]}), x, $at)"
            val o = Reports.of(TextInput.read(task)).result.outcome as? Outcome.TangentLine
            val fa = p[3] * at * at * at + p[2] * at * at + p[1] * at + p[0]
            val m = 3 * p[3] * at * at + 2 * p[2] * at + p[1]
            if (o == null) { declined++; return@repeat }
            val line = o.line.second
            val ok = o.line.first == "y" && listOf(-2.0, 0.5, 3.0).all { x -> abs(Numeric.eval(line, mapOf("x" to x)) - (fa + m * (x - at))) < 1e-9 }
            if (!ok) wrong += "$task: ${o.line}"
        }
        repeat(60) {
            // The cone of a line turned about the x-axis: π∫₀ʰ (mx + q)² dx = π(m²h³/3 + mqh² + q²h).
            val m = r.nextInt(-3, 4)
            val q = r.nextInt(0, 4)
            val h = r.nextInt(1, 5)
            val task = "volumex(($m)x + ($q), x, 0, $h)"
            val got = value(task)
            val want = PI * (m * m * h * h * h / 3.0 + m * q * h * h + q * q * h)
            if (got == null) declined++ else if (!near(got, want, 1e-9)) wrong += "$task: $got, not $want"
        }
        assertEquals("", wrong.joinToString("\n"))
        assertEquals(0, declined)
    }
}
