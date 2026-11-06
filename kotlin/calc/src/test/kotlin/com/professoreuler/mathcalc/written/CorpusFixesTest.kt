package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wrong answers the written corpus found (6 November 2026), each with its right answer: by hand, from the textbook, or
 * from mpmath at 30 digits (normalcdf(42, 44, 69, 3) = 3.91858755079·10⁻¹⁷, erfc 10 = 2.08848758376·10⁻⁴⁵,
 * √(10¹⁶ + 1) − 10⁸ = 5.000000000000000005·10⁻⁹, ln(1 + 10⁻¹²) = 9.999999999995·10⁻¹³, 1 − cos 10⁻⁵ = 4.99999999999958·10⁻¹¹).
 */
class CorpusFixesTest {
    private fun answer(text: String): String = answerOf(Reports.of(TextInput.read(text)).result)

    private fun check(cases: List<Pair<String, String>>) {
        val wrong = cases.mapNotNull { (input, expected) -> answer(input).takeIf { it != expected }?.let { "$input: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** A typed limit or sum is of its whole formula, not of its first term (sum(3k + 5, k, 1, 9) was 140). */
    @Test
    fun limitsAndSumsTakeTheWholeFormula() = check(listOf(
        "sum(3k + 5, k, 1, 9)" to "180", "sum(k + 1, k, 1, 3)" to "9", "sum(4k - 2, k, 2, 29)" to "1680",
        "limit(sqrt(x^2 + 2x) - x, x, infinity)" to "1", "limit((x^2 - 1)/(x - 1) + 1, x, 1)" to "3",
    ))

    /** A function asked for what the formula does not offer is never answered by another choice (derivative(5 + 3, x) was 8). */
    @Test
    fun aFunctionIsNeverAnsweredByAnother() = check(listOf(
        "derivative(5 + 3, x)" to "0", "derivative(y^2, x)" to "0", "derivative(sin(x), y)" to "0", "integral(y, x)" to "xy + C",
        "laplace(1)" to "1/s", "laplace(5)" to "5/s",
        // Checked by a quadrature of e^(−s₀t)·sinh t, which overflowed as written: now in exponentials, L{sinh t} = 1/(s² − 1).
        "laplace(sinh(t))" to "1/((s - 1)(s + 1))", "laplace(cosh(2t))" to "s/((s - 2)(s + 2))",
        "inverse(x)" to "ARGUMENTS", "det([[1, 2, 3], [4, 5, 6]])" to "SIZES", "solve(x^2 = 4, y)" to "ARGUMENTS",
        "extrema(x^2)" to "ARGUMENTS",
        // A series asked for by name is worked out as asked, whether its chip was found in time or not (under load,
        // taylor(tan(x), x, 0, 7) was refused): ln(x − 1) about 2 is (x − 2) − (x − 2)²/2 + (x − 2)³/3.
        "taylor(ln(x - 1), x, 2, 3)" to "x + (1/3)(x - 2)^3 - (1/2)(x - 2)^2 - 2",
        // A consistent system's least-squares solution is its solution.
        "leastsquares(x + y = 2, x - y = 0, 2x + y = 3)" to "x = 1, y = 1",
    ))

    /** A decimal near a simple fraction is no proof of it: tanh 15 = 1 − 1.9·10⁻¹³ was shown as 1, normalcdf as 0. */
    @Test
    fun nearFractionsAreNotShownExact() = check(listOf(
        "tanh(15)" to "≈ 1", "erf(6)" to "≈ 1", "1/2 + e^(-100)" to "1/2 + 1/e^100 ≈ 0.5", "erfc(10)" to "≈ 2.088487584 · 10^-45",
        "normalcdf(42, 44, 69, 3)" to "≈ 3.918587551 · 10^-17",
    ))

    /** The decimal beside a result is certified, not a double's: these cancel in doubles (e^(10⁻¹⁰) − 1 was 1.000000083·10⁻¹⁰). */
    @Test
    fun decimalsAreCertified() = check(listOf(
        "e^(10^(-10)) - 1" to "-1 + e^(1/10000000000) ≈ 1 · 10^-10", "sqrt(10^16 + 1) - 10^8" to "-100000000 + √10000000000000001 ≈ 5 · 10^-9",
        "ln(1 + 10^(-12))" to "ln(1000000000001/1000000000000) ≈ 1 · 10^-12", "1 - cos(10^(-5))" to "≈ 5 · 10^-11",
        "calculate(355/113 - pi)" to "355/113 - π ≈ 2.667641891 · 10^-7",
        // Its smaller root is 1/(10⁸ + √(10¹⁶ − 1)) ≈ 5·10⁻⁹, which doubles cannot put back (and once rejected).
        "solve(x^2 - 200000000x + 1 = 0, x)" to "x = 100000000 - 3√1111111111111111 ≈ 5 · 10^-9; 100000000 + 3√1111111111111111 ≈ 200000000",
    ))

    /** Limits at ∞ were values at 10ᵏ, all even: (−1)ᵏ had the limit 1, and alternating series were called divergent. */
    @Test
    fun oscillationAndAlternatingSeries() = check(listOf(
        "limit((-1)^k, k, infinity)" to "NO_LIMIT", "limit(cos(pi k), k, infinity)" to "NO_LIMIT", "limit((-1)^k/k, k, infinity)" to "0",
        "limit(sin(x)/x, x, infinity)" to "0", "limit(x sin(1/x), x, 0)" to "0", "limit(sin(k)/k, k, infinity)" to "0",
        "sum((-1)^k/k^2, k, 1, infinity)" to "-π^2/12 ≈ -0.8224670334", "sum((-1)^k/(k + 1), k, 0, infinity)" to "≈ 0.6931471806",
        "sum((-1)^k/(2k + 1), k, 0, infinity)" to "π/4 ≈ 0.7853981634",
    ))

    /** Exact where the mathematics is: log₁₀ typed with its index, hyperbolic functions of logarithms, floor of π, the median. */
    @Test
    fun exactValues() = check(listOf(
        "log_10(1000)" to "3", "log_10(1/100)" to "-2", "sinh(ln(2))" to "3/4 = 0.75", "tanh(ln(3))" to "4/5 = 0.8", "cosh(3 ln(2))" to "65/16 = 4.0625",
        "floor(pi)" to "3", "ceil(sqrt(10))" to "4", "sgn(e - pi)" to "-1", "invnorm(0.5)" to "0", "invnorm(0.5, 100, 15)" to "100",
    ))

    /** normalcdf checked its erf terms at 168 identical points in balls: seconds; a constant is checked once. */
    @Test
    fun normalcdfIsQuick() {
        answer("normalcdf(1, 2)")
        val start = System.nanoTime()
        answer("normalcdf(42, 44, 69, 3)")
        assertTrue("normalcdf took ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 1_500_000_000L)
    }
}
