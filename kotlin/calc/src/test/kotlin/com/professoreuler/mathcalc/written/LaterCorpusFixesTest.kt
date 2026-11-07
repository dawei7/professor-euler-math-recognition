package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * The wrong answers the corpus of the later areas found (7 November 2026), each with its right answer worked out here without the
 * engine.
 */
class LaterCorpusFixesTest {
    private fun calc(text: String): Calculation = Reports.of(TextInput.read(text)).result

    /**
     * A root of a fraction beyond a double's range has a value: interestrate(6256, 8227, 36, 4) is 4((8227/6256)^(1/144) − 1),
     * which the canonical form keeps as the 144th root of a number of over 400 digits, and was refused as undefined.
     */
    @Test
    fun rootsOfLargeNumbersHaveAValue() {
        for ((p, f, n, m) in listOf(listOf(6256, 8227, 36, 4), listOf(3302, 14379, 38, 12), listOf(4092, 13513, 17, 12))) {
            val want = m * ((f.toDouble() / p).pow(1.0 / (m * n)) - 1)
            val c = calc("interestrate($p, $f, $n, $m)")
            val o = c.outcome as? Outcome.Value ?: error("interestrate($p, $f, $n, $m): ${answerOf(c)}")
            val got = Numeric.value(o.exact!!)!!
            assertTrue("$got, not $want", abs(got - want) <= 1e-12 * want)
        }
        // A whole number of 1,000 digits to the power 1/1000 is 10 (Numeric's value of it was ∞).
        assertEquals(10.0, Numeric.eval(Expr.Pow(Expr.Num(Rational.of(java.math.BigInteger.TEN.pow(1000))), Expr.Num(Rational.of(1) / Rational.of(1000))), emptyMap()), 1e-12)
    }
}
