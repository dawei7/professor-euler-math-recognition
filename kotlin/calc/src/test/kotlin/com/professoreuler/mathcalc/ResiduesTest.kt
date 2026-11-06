package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Random

/**
 * multorder, primroot and discretelog: every value compared with one found another way, by stepping through the powers one at
 * a time (all moduli to a few hundred), and with values known from the literature; "none" where stepping finds none.
 */
class ResiduesTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        val o = r.outcome
        return if (o is Outcome.Failed) "FAILED ${o.failure}" else answerOf(r)
    }

    private fun b(n: Int) = BigInteger.valueOf(n.toLong())

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** The order by stepping: a, a², a³, … until 1. */
    private fun steppedOrder(a: Int, n: Int): Int {
        var x = a % n
        var k = 1
        while (x != 1 % n) { x = x * a % n; k++ }
        return k
    }

    private fun phi(n: Int) = (1..n).count { gcd(it, n) == 1 }

    @Test
    fun ordersAgreeWithStepping() {
        val wrong = ArrayList<String>()
        for (n in 2..300) for (a in 1 until n) {
            if (gcd(a, n) != 1) continue
            val got = Residues.order(b(a), b(n)).toInt()
            val stepped = steppedOrder(a, n)
            if (got != stepped) wrong += "ord_$n($a) = $got, not $stepped"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun primitiveRootsAgreeWithStepping() {
        val wrong = ArrayList<String>()
        for (n in 2..600) {
            val f = phi(n)
            val stepped = (1 until n).firstOrNull { gcd(it, n) == 1 && steppedOrder(it, n) == f }
            val got = try { Residues.primitiveRoot(b(n)).toInt() } catch (e: CalcException) { e.failure }
            val expected: Any = stepped ?: Failure.NO_PRIMITIVE_ROOT
            if (got != expected) wrong += "primroot($n) = $got, not $expected"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun discreteLogarithmsAgreeWithStepping() {
        val wrong = ArrayList<String>()
        for (n in 2..90) for (g in 1 until n) {
            if (gcd(g, n) != 1) continue
            // The smallest x with gˣ ≡ a for each a reached, by stepping through one period.
            val first = HashMap<Int, Int>()
            var x = 1 % n
            var k = 0
            while (x !in first) { first[x] = k; x = x * g % n; k++ }
            for (a in 0 until n) {
                val got = try { Residues.discreteLog(b(a), b(g), b(n)).toInt() } catch (e: CalcException) { e.failure }
                val expected: Any = first[a] ?: Failure.NO_DISCRETE_LOG
                if (got != expected) wrong += "discretelog($a, $g, $n) = $got, not $expected"
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** Large moduli: the logarithm put back with BigInteger's own powering, and primitive roots known from the literature. */
    @Test
    fun largeModuli() {
        val p = BigInteger("1000000007")
        val rnd = Random(3)
        repeat(5) {
            val a = BigInteger(29, rnd).mod(p).max(BigInteger.ONE)
            val x = Residues.discreteLog(a, BigInteger.valueOf(5), p)
            assertEquals(a, BigInteger.valueOf(5).modPow(x, p))
            assertTrue(x < p - BigInteger.ONE)
        }
        // The smallest primitive roots of 10⁹ + 7 and of 998244353 = 119·2²³ + 1 are 5 and 3.
        assertEquals("5", answer("primroot(1000000007)"))
        assertEquals("3", answer("primroot(998244353)"))
        // 10 has order 22 modulo 23 and 16 modulo 17: 1/23 and 1/17 have periods of 22 and 16 digits.
        assertEquals("22", answer("multorder(10, 23)"))
        assertEquals("16", answer("multorder(10, 17)"))
        // Modulo 2⁶¹ − 1 (a Mersenne prime), 2 has order 61.
        assertEquals("61", answer("multorder(2, 2305843009213693951)"))
    }

    @Test
    fun typedAsFunctionsAndAsCongruences() {
        val cases = listOf(
            "multorder(3, 7)" to "6",
            "multorder(2, 7)" to "3",
            "multorder(-1, 7)" to "2",
            "multorder(2, 6)" to "FAILED UNDEFINED",
            "multorder(3)" to "FAILED ARGUMENTS",
            "primroot(7)" to "3",
            "primroot(8)" to "FAILED NO_PRIMITIVE_ROOT",
            "primroot(2)" to "1",
            "primroot(1)" to "FAILED UNDEFINED",
            "primroot(2.5)" to "FAILED UNDEFINED",
            "discretelog(13, 3, 17)" to "4",
            "discretelog(3, 2, 7)" to "FAILED NO_DISCRETE_LOG",
            "discretelog(1, 5, 23)" to "0",
            "discretelog(4, 2, 6)" to "FAILED UNDEFINED",
            "discretelog(13, 3)" to "FAILED ARGUMENTS",
            "3^x ≡ 13 mod 17" to "x ≡ 4 (mod 16)",
            "2^x ≡ 4 mod 7" to "x ≡ 2 (mod 3)",
            "2^x ≡ 3 mod 7" to "none",
            "2·3^x + 1 ≡ 0 mod 7" to "x ≡ 1 (mod 6)",
            "discretelog(13, 3, 17) + 1" to "5",
        )
        val wrong = cases.mapNotNull { (text, expected) -> answer(text).takeIf { it != expected }?.let { "$text: $it, not $expected" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun theirSteps() {
        fun keys(text: String) = TextInput.read(text).let { t -> Steps.of(t.latex, t.choice) }?.joinToString(" ") { it.why.key.name } ?: "none"
        assertEquals("START ORDER_PHI ORDER_TEST RESULT", keys("multorder(3, 7)"))
        assertEquals("ORDER_PHI PRIMROOT_REJECT PRIMROOT_REJECT PRIMROOT_REJECT PRIMROOT_REJECT PRIMROOT_ACCEPT RESULT", keys("primroot(41)"))
        assertEquals("START DLOG_ORDER DLOG_PART RESULT", keys("discretelog(13, 3, 17)"))
        assertEquals("START DLOG_ORDER DLOG_PART DLOG_PART CRT RESULT", keys("discretelog(5, 2, 13)"))
        val last = TextInput.read("primroot(41)").let { Steps.of(it.latex, it.choice)!!.last().latex }
        assertEquals("g = 6", last)
    }
}
