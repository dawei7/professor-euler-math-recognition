package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.util.Random

/**
 * Whole numbers up to 40 digits taken apart (SQUFOF, Pollard–Brent, ECM) and their primes certified: each factorization
 * compared with one known independently (a historical one, or the primes a number was made of), the Montgomery arithmetic with
 * BigInteger's, and the certificates with primes and composites known as such.
 */
class BigFactorTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        val o = r.outcome
        return if (o is Outcome.Failed) "FAILED ${o.failure}" else answerOf(r)
    }

    private fun big(s: String) = BigInteger(s)

    private fun factored(n: BigInteger): List<Pair<BigInteger, Int>>? = Budget.within(60_000) { NumberTheory.factor(n) }

    /** Factorizations of the history books. */
    @Test
    fun knownFactorizations() {
        // 2⁶⁴ + 1 (Landry, 1880), 2⁶⁷ − 1 (Cole, 1903), 2¹²⁸ + 1 (Morrison and Brillhart, 1970).
        assertEquals(listOf(big("274177") to 1, big("67280421310721") to 1), factored(BigInteger.ONE.shiftLeft(64) + BigInteger.ONE))
        assertEquals(listOf(big("193707721") to 1, big("761838257287") to 1), factored(BigInteger.ONE.shiftLeft(67) - BigInteger.ONE))
        assertEquals(listOf(big("59649589127497217") to 1, big("5704689200685129054721") to 1),
            factored(BigInteger.ONE.shiftLeft(128) + BigInteger.ONE))
        // 600851475143 = 71 · 839 · 1471 · 6857.
        assertEquals(listOf(71, 839, 1471, 6857).map { BigInteger.valueOf(it.toLong()) to 1 }, factored(big("600851475143")))
        // 10³⁸ − 1 = 3² · 11 · 909090909090909091 · 1111111111111111111 (repunits).
        assertEquals(listOf(big("3") to 2, big("11") to 1, big("909090909090909091") to 1, big("1111111111111111111") to 1),
            factored(BigInteger.TEN.pow(38) - BigInteger.ONE))
        // A prime power: 1000003⁵.
        assertEquals(listOf(big("1000003") to 5), factored(big("1000003").pow(5)))
    }

    /** Products of primes made at random (seeded): each comes apart into exactly the primes it was made of. */
    @Test
    fun madeOfKnownPrimes() {
        val rnd = Random(2026)
        val wrong = ArrayList<String>()
        for ((bits, count) in listOf(listOf(20, 30) to 4, listOf(31, 31) to 4, listOf(45, 50) to 4, listOf(25, 40, 60) to 3, listOf(62, 64) to 3,
            listOf(40, 40, 40) to 2)) {
            repeat(count) {
                val primes = bits.map { BigInteger.probablePrime(it, rnd) }
                val n = primes.fold(BigInteger.ONE) { acc, p -> acc * p }
                val expected = primes.groupingBy { it }.eachCount().entries.sortedBy { it.key }.map { it.key to it.value }
                val got = factored(n)
                if (got != expected) wrong += "$n: $got, not $expected"
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    /** The hard case of 40 digits, two primes of 20 digits: ECM finds them (given time enough here; the Calculator has 3 s). */
    @Test
    fun fortyDigitsOfTwoPrimes() {
        val rnd = Random(66)
        repeat(2) {
            val p = BigInteger.probablePrime(66, rnd)
            val q = BigInteger.probablePrime(66, rnd)
            val n = p * q
            assertEquals(listOf(p.min(q) to 1, p.max(q) to 1), factored(n))
        }
    }

    @Test
    fun squfofAndRhoAndEcmEachFindAFactor() {
        val rnd = Random(5)
        repeat(20) {
            val p = BigInteger.probablePrime(28, rnd).toLong()
            val q = BigInteger.probablePrime(30, rnd).toLong()
            val f = BigFactor.squfof(p * q)
            assertTrue("squfof(${p * q}) = $f", f == p || f == q)
        }
        repeat(5) {
            val p = BigInteger.probablePrime(30, rnd)
            val q = BigInteger.probablePrime(90, rnd)
            val f = Budget.within(30_000) { BigFactor.rho(p * q, 1 shl 18) }
            assertEquals(p, f)
        }
        repeat(4) {
            val p = BigInteger.probablePrime(48, rnd)
            val q = BigInteger.probablePrime(80, rnd)
            val f = Budget.within(30_000) { BigFactor.ecm(p * q) }
            assertTrue("ecm: $f", f == p || f == q)
        }
    }

    /** Montgomery's product against BigInteger's (a·b mod n), for moduli of 2 to 160 bits. */
    @Test
    fun montgomeryArithmetic() {
        val rnd = Random(9)
        for (bits in listOf(2, 31, 60, 61, 90, 120, 133, 160)) repeat(50) {
            val n = BigInteger(bits, rnd).setBit(0).setBit(maxOf(1, bits - 1))
            val m = BigFactor.Montgomery(n)
            val a = BigInteger(bits + 3, rnd).mod(n)
            val b = BigInteger(bits + 3, rnd).mod(n)
            val am = m.toMont(a)
            val bm = m.toMont(b)
            val out = LongArray(m.k)
            m.mul(am, bm, out)
            assertEquals("$a·$b mod $n", (a * b).mod(n), m.fromMont(out))
            m.add(am, bm, out)
            assertEquals((a + b).mod(n), m.fromMont(out))
            m.sub(am, bm, out)
            assertEquals((a - b).mod(n), m.fromMont(out))
        }
    }

    /** Certificates: primes known as such are proved prime, beyond the bound of Miller–Rabin's fixed bases. */
    @Test
    fun primesAreCertified() {
        // Mersenne primes 2⁸⁹ − 1 (Powers, 1911), 2¹⁰⁷ − 1 (Powers, 1914) and 2¹²⁷ − 1 (Lucas, 1876).
        for (k in listOf(89, 107, 127)) {
            val p = BigInteger.ONE.shiftLeft(k) - BigInteger.ONE
            assertTrue("2^$k − 1", Budget.within(30_000) { BigFactor.certify(p) })
            assertTrue(Budget.within(30_000) { NumberTheory.isPrime(p) })
        }
        // Random probable primes of 90 to 133 bits, and composites made of two of them.
        val rnd = Random(77)
        repeat(6) {
            val p = BigInteger.probablePrime(90 + 8 * it, rnd)
            assertTrue("$p", Budget.within(30_000) { NumberTheory.isPrime(p) })
            assertTrue(!NumberTheory.isPrime(p * BigInteger.probablePrime(40, rnd)))
        }
        // 2¹⁰¹ − 1 = 7432339208719 · 341117531003194129 is no prime.
        assertTrue(!NumberTheory.isPrime(BigInteger.ONE.shiftLeft(101) - BigInteger.ONE))
    }

    /** facts and factor of numbers of up to 40 digits, typed as their functions. */
    @Test
    fun factsAndFactorOfLargeNumbers() {
        val p = big("59649589127497217")
        val q = big("5704689200685129054721")
        val n = p * q
        val sigma = (p + BigInteger.ONE) * (q + BigInteger.ONE)
        val phi = (p - BigInteger.ONE) * (q - BigInteger.ONE)
        val got = answer("facts($n)")
        assertTrue(got, got.startsWith("59649589127497217 · 5704689200685129054721 d=4 σ=$sigma φ=$phi μ=1 next "))
        assertEquals("274177 · 67280421310721", answer("factor(18446744073709551617)"))
        // The Mersenne prime 2¹²⁷ − 1: prime, certified.
        assertTrue(answer("facts(170141183460469231731687303715884105727)").startsWith("prime d=2"))
        // 41 digits and more: too large to take apart, never shown as the number itself.
        assertEquals("FAILED TOO_LARGE", answer("facts(10000000000000000000000000000000000000001)"))
        assertEquals("FAILED TOO_LARGE", answer("factor(10000000000000000000000000000000000000001)"))
        assertEquals("FAILED ARGUMENTS", answer("facts(97, 98)"))
    }
}
