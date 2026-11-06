package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Whole numbers: primality (Miller–Rabin with the bases that make it certain below 3.3·10²⁴, a strong probable-prime test
 * beyond), factors by trial division and Pollard–Brent, divisors, φ, σ and μ, other bases; the extended Euclidean algorithm,
 * linear congruences, the Chinese remainder theorem, linear Diophantine equations and Pell's equation. Every answer is checked
 * by multiplying back or putting it in.
 */
object NumberTheory {
    /** `BigInteger.TWO` and `BigInteger.sqrt` are Java 9: Android has them only from version 13. */
    private val TWO = BigInteger.valueOf(2)
    private val BASES = listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41).map { BigInteger.valueOf(it.toLong()) }
    private val CERTAIN_BELOW = BigInteger("3317044064679887385961981")

    /** Prime: certain below 3.3·10²⁴ (Miller–Rabin with the first 13 primes as bases), almost certain beyond. */
    fun isPrime(n: BigInteger): Boolean {
        if (n < TWO) return false
        for (p in BASES) { if (n == p) return true; if (n.mod(p).signum() == 0) return false }
        if (n >= CERTAIN_BELOW) return n.isProbablePrime(100)
        var d = n - BigInteger.ONE
        var r = 0
        while (!d.testBit(0)) { d = d.shiftRight(1); r++ }
        loop@ for (a in BASES) {
            var x = a.modPow(d, n)
            if (x == BigInteger.ONE || x == n - BigInteger.ONE) continue
            for (k in 1 until r) {
                x = x.multiply(x).mod(n)
                if (x == n - BigInteger.ONE) continue@loop
            }
            return false
        }
        return true
    }

    /** The prime factors with their powers, smallest first; null where a factor would not come out in time (beyond about 10³⁰). */
    fun factor(n0: BigInteger): List<Pair<BigInteger, Int>>? {
        if (n0 < TWO) return null
        if (n0.bitLength() > 110) return null
        val found = HashMap<BigInteger, Int>()
        var n = n0
        var p = TWO
        while (p.toLong() < 10_000 && p * p <= n) {
            while (n.mod(p).signum() == 0) { found[p] = (found[p] ?: 0) + 1; n /= p }
            p = if (p == TWO) BigInteger.valueOf(3) else p + TWO
        }
        val stack = ArrayDeque<BigInteger>()
        if (n > BigInteger.ONE) stack += n
        while (stack.isNotEmpty()) {
            Budget.check()
            val m = stack.removeLast()
            if (m == BigInteger.ONE) continue
            if (isPrime(m)) { found[m] = (found[m] ?: 0) + 1; continue }
            val d = brent(m) ?: return null
            stack += d
            stack += m / d
        }
        val result = found.entries.sortedBy { it.key }.map { it.key to it.value }
        // Multiplied back.
        return if (result.fold(BigInteger.ONE) { acc, (q, k) -> acc * q.pow(k) } == n0) result else null
    }

    /** A factor of a composite m by Pollard's rho in Brent's form. */
    private fun brent(m: BigInteger): BigInteger? {
        if (!m.testBit(0)) return TWO
        for (c0 in 1L..40L) {
            val c = BigInteger.valueOf(c0)
            var y = BigInteger.valueOf(2 + c0)
            var r = 1
            var q = BigInteger.ONE
            var g = BigInteger.ONE
            var x = y
            var ys = y
            val step = 128
            fun f(v: BigInteger) = (v * v + c).mod(m)
            while (g == BigInteger.ONE) {
                x = y
                repeat(r) { y = f(y) }
                var k = 0
                while (k < r && g == BigInteger.ONE) {
                    Budget.check()
                    ys = y
                    repeat(minOf(step, r - k)) {
                        y = f(y)
                        q = (q * (x - y).abs()).mod(m)
                    }
                    g = q.gcd(m)
                    k += step
                }
                r *= 2
                if (r > 1 shl 22) break
            }
            if (g == m) {
                do { ys = f(ys); g = (x - ys).abs().gcd(m) } while (g == BigInteger.ONE)
            }
            if (g != m && g != BigInteger.ONE) return g
        }
        return null
    }

    /** All divisors in order, where there are at most [most]. */
    fun divisors(factors: List<Pair<BigInteger, Int>>, most: Int = 240): List<BigInteger>? {
        val count = factors.fold(1L) { acc, (_, k) -> acc * (k + 1) }
        if (count > most) return null
        var out = listOf(BigInteger.ONE)
        for ((p, k) in factors) out = out.flatMap { d -> (0..k).map { d * p.pow(it) } }
        return out.sorted()
    }

    fun divisorCount(factors: List<Pair<BigInteger, Int>>): BigInteger = factors.fold(BigInteger.ONE) { acc, (_, k) -> acc * BigInteger.valueOf(k + 1L) }

    /** Euler's φ: how many of 1 … n are coprime to n. */
    fun phi(factors: List<Pair<BigInteger, Int>>): BigInteger = factors.fold(BigInteger.ONE) { acc, (p, k) -> acc * (p - BigInteger.ONE) * p.pow(k - 1) }

    /** σ: the sum of the divisors. */
    fun sigma(factors: List<Pair<BigInteger, Int>>): BigInteger =
        factors.fold(BigInteger.ONE) { acc, (p, k) -> acc * ((p.pow(k + 1) - BigInteger.ONE) / (p - BigInteger.ONE)) }

    /** Möbius μ: 0 with a square factor, else (−1)^(number of primes). */
    fun mu(factors: List<Pair<BigInteger, Int>>): Int = if (factors.any { it.second > 1 }) 0 else if (factors.size % 2 == 0) 1 else -1

    /** g = gcd(a, b) and x, y with a·x + b·y = g (Bézout). */
    fun extendedGcd(a: BigInteger, b: BigInteger): Triple<BigInteger, BigInteger, BigInteger> {
        var (r0, r1) = a to b
        var (s0, s1) = BigInteger.ONE to BigInteger.ZERO
        var (t0, t1) = BigInteger.ZERO to BigInteger.ONE
        while (r1.signum() != 0) {
            val q = r0.divide(r1)
            r0 = r1.also { r1 = r0 - q * r1 }
            s0 = s1.also { s1 = s0 - q * s1 }
            t0 = t1.also { t1 = t0 - q * t1 }
        }
        return if (r0.signum() < 0) Triple(r0.negate(), s0.negate(), t0.negate()) else Triple(r0, s0, t0)
    }

    /** a·x ≡ b (mod m): x ≡ x₀ (mod m/g), or null where g = gcd(a, m) does not divide b. */
    fun linearCongruence(a: BigInteger, b: BigInteger, m: BigInteger): Pair<BigInteger, BigInteger>? {
        if (m.signum() <= 0) return null
        val (g, x, _) = extendedGcd(a.mod(m), m)
        if (b.mod(g).signum() != 0) return null
        val step = m / g
        val x0 = (x * (b / g)).mod(step)
        return if ((a * x0 - b).mod(m).signum() == 0) x0 to step else null
    }

    /** x ≡ rᵢ (mod mᵢ) for all i: x ≡ r (mod lcm), or null where two of them contradict each other. */
    fun crt(residues: List<BigInteger>, moduli: List<BigInteger>): Pair<BigInteger, BigInteger>? {
        var r = BigInteger.ZERO
        var m = BigInteger.ONE
        for ((ri, mi) in residues.zip(moduli)) {
            val (g, p, _) = extendedGcd(m, mi)
            if ((ri - r).mod(g).signum() != 0) return null
            val l = m / g * mi
            r = (r + m * (((ri - r) / g) * p).mod(mi / g)).mod(l)
            m = l
        }
        return if (residues.zip(moduli).all { (ri, mi) -> (r - ri).mod(mi).signum() == 0 }) r to m else null
    }

    /** a·x + b·y = c in whole numbers: (x₀, y₀) and the steps (b/g, −a/g) of the family, or null where g ∤ c. */
    fun diophantine(a: BigInteger, b: BigInteger, c: BigInteger): List<BigInteger>? {
        if (a.signum() == 0 && b.signum() == 0) return null
        val (g, x, y) = extendedGcd(a, b)
        if (c.mod(g).signum() != 0) return null
        val k = c / g
        val x0 = x * k
        val y0 = y * k
        if (a * x0 + b * y0 != c) return null
        return listOf(x0, y0, b / g, (a / g).negate())
    }

    /** The smallest x, y > 0 with x² − d·y² = 1, by the continued fraction of √d; null for a square d or one too large. */
    fun pell(d: BigInteger): Pair<BigInteger, BigInteger>? {
        if (d.signum() <= 0 || d.bitLength() > 40) return null
        val a0 = isqrt(d)
        if (a0 * a0 == d) return null
        var m = BigInteger.ZERO
        var dd = BigInteger.ONE
        var a = a0
        var (p0, p1) = BigInteger.ONE to a0
        var (q0, q1) = BigInteger.ZERO to BigInteger.ONE
        for (k in 0 until 10_000) {
            Budget.check()
            if (p1 * p1 - d * q1 * q1 == BigInteger.ONE) return p1 to q1
            m = dd * a - m
            dd = (d - m * m) / dd
            a = (a0 + m) / dd
            p0 = p1.also { p1 = a * p1 + p0 }
            q0 = q1.also { q1 = a * q1 + q0 }
        }
        return null
    }

    /** ⌊√n⌋ of n ≥ 0, by Newton's method in whole numbers (`BigInteger.sqrt` is not on Android before 13). */
    fun isqrt(n: BigInteger): BigInteger {
        if (n.signum() < 0) throw ArithmeticException("negative")
        if (n.bitLength() < 52) return BigInteger.valueOf(kotlin.math.sqrt(n.toDouble()).toLong()).let { r ->
            var x = r
            while (x * x > n) x -= BigInteger.ONE
            while ((x + BigInteger.ONE) * (x + BigInteger.ONE) <= n) x += BigInteger.ONE
            x
        }
        var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
        while (true) {
            val y = (x + n / x).shiftRight(1)
            if (y >= x) return x
            x = y
        }
    }

    /** n in base [base] (2 to 36), with letters for digits beyond 9. */
    fun inBase(n: BigInteger, base: Int): String = n.toString(base).uppercase()

    /** n choose k for whole n ≥ k ≥ 0. */
    fun binomial(n: BigInteger, k: BigInteger): BigInteger {
        if (k.signum() < 0 || k > n) return BigInteger.ZERO
        val kk = minOf(k, n - k).toInt()
        var r = BigInteger.ONE
        for (i in 1..kk) r = r * (n - BigInteger.valueOf((kk - i).toLong())) / BigInteger.valueOf(i.toLong())
        return r
    }
}
