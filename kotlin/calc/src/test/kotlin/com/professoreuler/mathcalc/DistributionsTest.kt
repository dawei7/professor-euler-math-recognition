package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The distributions as number functions, each against a value known independently of the engine: a closed form worked out here in
 * doubles (the t distribution of a whole ν by its finite series, χ² of an even k by its Poisson sum, F(2, 2), the arcsine law, the
 * beta of whole parameters by the binomial), a textbook table value, or an exact value by hand.
 */
class DistributionsTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    private fun answer(text: String): String = calc(text).let { if (it.outcome is Outcome.Failed) "FAILED ${(it.outcome as Outcome.Failed).failure}" else answerOf(it) }

    /** The number an input comes to. */
    private fun value(text: String): Double {
        val o = calc(text).outcome as? Outcome.Value ?: error("$text: ${answer(text)}")
        return o.exact?.let { Numeric.value(it) } ?: o.value.double
    }

    private fun close(expected: Double, text: String, rel: Double = 1e-9) {
        val got = value(text)
        assertTrue("$text: $got, not $expected", abs(got - expected) <= rel * maxOf(abs(expected), 1e-300))
    }

    /** P(|T| ≤ t) for a whole ν (Abramowitz and Stegun 26.7.3 and 26.7.4), with θ = arctan(t/√ν). */
    private fun tCentral(t: Double, nu: Int): Double {
        val theta = atan(t / sqrt(nu.toDouble()))
        val c2 = cos(theta) * cos(theta)
        if (nu % 2 == 1) {
            if (nu == 1) return 2 / Math.PI * theta
            var term = cos(theta)
            var sum = term
            var k = 3
            while (k <= nu - 2) {
                term *= c2 * (k - 1).toDouble() / k
                sum += term
                k += 2
            }
            return 2 / Math.PI * (theta + sin(theta) * sum)
        }
        var term = 1.0
        var sum = 1.0
        var k = 2
        while (k <= nu - 2) {
            term *= c2 * (k - 1).toDouble() / k
            sum += term
            k += 2
        }
        return sin(theta) * sum
    }

    /** P(χ²ₖ > x) for an even k: e^(−x/2)·Σ_{j<k/2} (x/2)ʲ/j!. */
    private fun chiTailEven(x: Double, k: Int): Double {
        // In logarithms, so that e^(−1000) and 1000⁴⁹⁹ do not leave a double's range.
        val logs = DoubleArray(k / 2)
        var factorial = 0.0
        for (j in 0 until k / 2) {
            if (j > 0) factorial += ln(j.toDouble())
            logs[j] = (if (j == 0) 0.0 else j * ln(x / 2)) - factorial - x / 2
        }
        val top = logs.maxOrNull()!!
        return exp(top) * logs.sumOf { exp(it - top) }
    }

    @Test
    fun studentT() {
        close(tCentral(2.0, 5), "tcdf(-2, 2, 5)")
        close(0.5 + tCentral(2.0, 5) / 2, "tcdf(-infinity, 2, 5)")
        close((1 - tCentral(10.0, 3)) / 2, "tcdf(10, infinity, 3)")
        // ν = 2: F(t) = ½ + t/(2√(t² + 2)), and its quantile t = (2p − 1)/√(2p(1 − p)).
        close(1.5 / (2 * sqrt(1.5 * 1.5 + 2)), "tcdf(0, 1.5, 2)")
        close(0.8 / sqrt(2 * 0.9 * 0.1), "invt(0.9, 2)")
        close(0.8 / sqrt(2 * 0.9 * 0.1), "-invt(0.1, 2)")
        // ν = 1, Cauchy: the quantile tan(π(p − ½)).
        close(Math.tan(Math.PI * 0.49), "invt(0.99, 1)")
        // Table values.
        close(2.228138852, "invt(0.975, 10)", 1e-9)
        close(12.70620474, "invt(0.975, 1)", 1e-9)
        // A large ν, quickly (the incomplete beta's shorter series): t → z.
        val start = System.nanoTime()
        close(1.959963984540054 + (1.959963984540054 + Math.pow(1.959963984540054, 3.0)) / (4 * 100000), "invt(0.975, 100000)", 1e-9)
        assertTrue("invt with ν = 100000 took too long", System.nanoTime() - start < 2_000_000_000L)
        assertEquals("0", answer("invt(0.5, 7)"))
        assertEquals("1/2 = 0.5", answer("tcdf(0, infinity, 3)"))
        // The density: 1/π at 0 for ν = 1, exact, and 3√3/(8π) at 1 for ν = 3.
        assertEquals("1/π ≈ 0.3183098862", answer("tpdf(0, 1)"))
        assertEquals("3√3/(8π) ≈ 0.2067483358", answer("tpdf(1, 3)"))
    }

    @Test
    fun chiSquare() {
        close(1 - exp(-1.5), "chisqcdf(0, 3, 2)")
        close(chiTailEven(9.0, 6), "chisqcdf(9, infinity, 6)")
        close(chiTailEven(30.0, 10) - chiTailEven(40.0, 10), "chisqcdf(30, 40, 10)")
        close(-2 * ln(0.05), "invchisq(0.95, 2)")
        close(18.30703805, "invchisq(0.95, 10)", 1e-9)
        close(3.841458821, "invchisq(0.95, 1)", 1e-9)
        // A tail far out keeps its digits: P(χ²₁₀₀₀ ≥ 2000) is no 0.
        val tail = value("chisqcdf(2000, infinity, 1000)")
        assertTrue("$tail", tail > 1e-70 && tail < 1e-68)
        close(chiTailEven(2000.0, 1000), "chisqcdf(2000, infinity, 1000)", 1e-8)
        assertEquals("1/(2e) ≈ 0.1839397206", answer("chisqpdf(2, 4)"))
        assertEquals("1/2 = 0.5", answer("chisqpdf(0, 2)"))
        assertEquals("FAILED UNDEFINED", answer("chisqpdf(0, 1)"))
    }

    @Test
    fun fisherF() {
        // F(2, 2): P(X ≤ x) = x/(1 + x).
        close(0.75, "fcdf(0, 3, 2, 2)")
        close(1.0, "invf(0.5, 2, 2)")
        // F(1, ν) is the square of t with ν: P(X ≤ t²) = P(|T| ≤ t).
        close(tCentral(2.0, 7), "fcdf(0, 4, 1, 7)")
        close(3.098391212, "invf(0.95, 3, 20)", 1e-9)
        // F(1, 1): x = tan²(πp/2).
        close(Math.tan(Math.PI * 0.99 / 2).let { it * it }, "invf(0.99, 1, 1)")
        assertEquals("1/4 = 0.25", answer("fpdf(1, 2, 2)"))
    }

    @Test
    fun gammaBetaLognormal() {
        // Gamma(2, 1): P(X ≤ 2) = 1 − 3e^(−2); Gamma(3, 2) is χ²₆.
        close(1 - 3 * exp(-2.0), "gammacdf(0, 2, 2, 1)")
        close(chiTailEven(7.0, 6), "gammacdf(7, infinity, 3, 2)")
        assertEquals("1/e ≈ 0.3678794412", answer("gammapdf(1, 2, 1)"))
        // Beta(2, 3) at 0.5: P(Bin(4, 0.5) ≥ 2) = 11/16; the arcsine law Beta(½, ½): (2/π)·arcsin √x.
        close(11.0 / 16, "betacdf(0, 0.5, 2, 3)")
        close(1.0 / 3, "betacdf(0, 0.25, 0.5, 0.5)")
        close(sqrt(0.3), "invbeta(0.3, 2, 1)")
        close(1 - sqrt(1 - 0.999), "invbeta(0.999, 1, 2)")
        assertEquals("1.5", answer("betapdf(0.5, 2, 3)"))
        // Lognormal: P(X ≤ e) = Φ(1); its median e^μ.
        close(0.8413447460685429, "lognormalcdf(0, e, 0, 1)")
        close(Math.E, "invlognormal(0.5, 1, 2)")
        assertEquals("√2/(2√π) ≈ 0.3989422804", answer("lognormalpdf(1, 0, 1)"))
    }

    @Test
    fun exactOnes() {
        assertEquals("2/e^2 ≈ 0.2706705665", answer("exppdf(1, 2)"))
        close(1 - exp(-2.0), "expcdf(0, 1, 2)")
        close(ln(2.0) / 2, "invexp(0.5, 2)")
        assertEquals("0", answer("exppdf(-1, 2)"))
        assertEquals("3/10 = 0.3", answer("uniformcdf(2, 5, 0, 10)"))
        assertEquals("1/4 = 0.25", answer("uniformpdf(3, 1, 5)"))
        assertEquals("0", answer("invuniform(0.25, -2, 6)"))
        // Hypergeometric: exactly two aces in a hand of five, C(4, 2)·C(48, 3)/C(52, 5).
        assertEquals("2162/54145 ≈ 0.03992981808", answer("hypergeompdf(52, 4, 5, 2)"))
        assertEquals("51888/54145 ≈ 0.9583156339", answer("hypergeomcdf(52, 4, 5, 1)"))
        assertEquals("1", answer("hypergeomcdf(52, 4, 5, 4)"))
        assertEquals("0", answer("hypergeompdf(10, 3, 5, 4)"))
    }

    @Test
    fun normalKeepsItsTails() {
        // Q(10) = 7.6198530241605·10⁻²⁴, which erf in doubles cancels to 0.
        close(7.6198530241605e-24, "normalcdf(10, infinity)", 1e-9)
        close(0.6826894921370859, "normalcdf(-1, 1)")
        assertEquals("1/2 = 0.5", answer("normalcdf(-infinity, 0)"))
        assertEquals("0", answer("invnorm(0.5)"))
        close(1.959963984540054, "invnorm(0.975)")
        close(100 + 15 * 1.2815515655446004, "invnorm(0.9, 100, 15)")
    }

    @Test
    fun parametersOutsideTheirRangeAreRefused() {
        for (text in listOf("tpdf(1, 0)", "tcdf(0, 1, -2)", "invt(1, 5)", "invt(0, 5)", "invchisq(1.5, 3)", "fpdf(1, 0, 2)",
            "gammacdf(0, 1, 0, 1)", "betapdf(0.5, -1, 2)", "lognormalpdf(1, 0, 0)", "exppdf(1, 0)", "uniformpdf(1, 2, 2)",
            "hypergeompdf(10, 11, 5, 1)", "hypergeompdf(10, 3, 5, 2.5)", "invnorm(0.9, 100, -15)", "normalcdf(0, 1, 0, -1)")) {
            assertEquals(text, "FAILED UNDEFINED", answer(text))
        }
        for (text in listOf("tcdf(1, 2)", "invt(0.5)", "fcdf(0, 1, 2)", "hypergeompdf(10, 3, 5)", "uniformcdf(1, 2, 3)")) {
            assertEquals(text, "FAILED ARGUMENTS", answer(text))
        }
    }

    /** Seeded: t of whole ν and χ² of even k against their closed forms, at random points; a hundred of each. */
    @Test
    fun againstClosedFormsAtRandom() {
        val random = java.util.Random(20261106)
        val wrong = ArrayList<String>()
        repeat(100) {
            val nu = 1 + random.nextInt(40)
            val t = Math.round((0.05 + random.nextDouble() * 6) * 100) / 100.0
            val got = value("tcdf(-$t, $t, $nu)")
            if (abs(got - tCentral(t, nu)) > 1e-9 * tCentral(t, nu)) wrong += "tcdf(-$t, $t, $nu): $got"
            val k = 2 * (1 + random.nextInt(30))
            val x = Math.round(random.nextDouble() * 3 * k * 100) / 100.0
            val tail = value("chisqcdf($x, infinity, $k)")
            if (abs(tail - chiTailEven(x, k)) > 1e-9 * chiTailEven(x, k)) wrong += "chisqcdf($x, infinity, $k): $tail"
            // The quantile put back into the closed form.
            val p = (1 + random.nextInt(98)) / 100.0
            val q = value("invt($p, $nu)")
            val back = 0.5 + Math.signum(q) * tCentral(abs(q), nu) / 2
            if (abs(back - p) > 1e-9) wrong += "invt($p, $nu): $q"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun dataFunctions() {
        assertEquals("1.75", answer("quantile([1, 2, 3, 4], 0.25)"))
        assertEquals("4.6", answer("quantile([3, 1, 4, 1, 5], 0.9)"))
        assertEquals("3", answer("quantile([3, 1, 4, 1, 5], 0.5)"))
        assertEquals("5", answer("quantile([3, 1, 4, 1, 5], 1)"))
        assertEquals("FAILED UNDEFINED", answer("quantile([1, 2], 1.5)"))
        assertEquals("3/2 = 1.5", answer("cov((1, 2), (2, 4), (3, 5))"))
        assertEquals("3√21/14 ≈ 0.9819805061", answer("corr((1, 2), (2, 4), (3, 5))"))
        assertEquals("-1", answer("corr((1, 3), (2, 2), (3, 1))"))
        assertEquals("FAILED UNDEFINED", answer("corr((1, 1), (2, 1), (3, 1))"))
        assertEquals("FAILED ARGUMENTS", answer("cov((1, 2))"))
        assertEquals("{2, 3}", answer("mode(1, 2, 2, 3, 3)"))
        assertEquals("{}", answer("mode(1, 2, 3)"))
        assertEquals("{1/2}", answer("mode(0.5, 2, 1/2)"))
    }
}
