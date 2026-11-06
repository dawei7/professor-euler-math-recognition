package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * The multiplicative group modulo n: the order of a residue (`multorder(a, n)`), the smallest primitive root
 * (`primroot(n)`), and the discrete logarithm (`discretelog(a, g, n)`: the smallest x ≥ 0 with gˣ ≡ a (mod n)), by
 * Pohlig–Hellman's reduction to the primes of the order and Shanks' baby-step giant-step in each. Every answer is checked by
 * powering: an order k by a^k ≡ 1 and a^(k/q) ≢ 1 for each prime q of k, a primitive root by its order φ(n), a logarithm by
 * gˣ ≡ a; and "no primitive root" by Carmichael's λ(n) < φ(n), the exponent every residue's order divides.
 */
internal object Residues {
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger.valueOf(2)

    /** The largest baby-step table: the primes of an order up to about 1.7·10¹⁰ are searched. */
    private const val MOST_STEPS = 1 shl 17

    /** The primes of [n] (n ≥ 2), certified; beyond 40 digits too large. */
    private fun primes(n: BigInteger): List<Pair<BigInteger, Int>> =
        NumberTheory.factor(n) ?: throw CalcException(Failure.TOO_LARGE)

    /** φ(n) of n ≥ 2 with its primes: from n's primes p^k as p^(k−1)(p − 1), each p − 1 taken apart. */
    private fun phiFactored(n: BigInteger): Pair<BigInteger, Map<BigInteger, Int>> {
        val out = HashMap<BigInteger, Int>()
        var phi = ONE
        for ((p, k) in primes(n)) {
            phi = phi * p.pow(k - 1) * (p - ONE)
            if (k > 1) out[p] = (out[p] ?: 0) + k - 1
            if (p > TWO) for ((q, e) in primes(p - ONE)) out[q] = (out[q] ?: 0) + e
        }
        // Multiplied back.
        if (out.entries.fold(ONE) { acc, (q, e) -> acc * q.pow(e) } != phi) throw CalcException(Failure.UNSOLVED)
        return phi to out
    }

    /** Whether k is the order of [a] modulo [n], from the primes [qs] of k: a^k ≡ 1 and no a^(k/q) is. */
    private fun isOrder(a: BigInteger, n: BigInteger, k: BigInteger, qs: Collection<BigInteger>): Boolean =
        a.modPow(k, n) == ONE.mod(n) && qs.filter { k.mod(it).signum() == 0 }.none { a.modPow(k / it, n) == ONE.mod(n) }

    /** The order of [a] modulo [n] with its primes: the least k ≥ 1 with a^k ≡ 1 (mod n). */
    private fun orderFactored(a0: BigInteger, n: BigInteger): Pair<BigInteger, Map<BigInteger, Int>> {
        if (n.signum() <= 0) throw CalcException(Failure.UNDEFINED)
        if (n == ONE) return ONE to emptyMap()
        val a = a0.mod(n)
        if (a.gcd(n) != ONE) throw CalcException(Failure.UNDEFINED)
        val (phi, qs) = phiFactored(n)
        var k = phi
        val left = HashMap<BigInteger, Int>()
        for ((q, e) in qs) {
            var times = e
            while (times > 0 && a.modPow(k / q, n) == ONE) {
                Budget.check()
                k /= q
                times--
            }
            if (times > 0) left[q] = times
        }
        if (!isOrder(a, n, k, left.keys)) throw CalcException(Failure.UNSOLVED)
        return k to left
    }

    /** ord_n(a): the least k ≥ 1 with a^k ≡ 1 (mod n), for a coprime to n ≥ 1. */
    fun order(a: BigInteger, n: BigInteger): BigInteger = orderFactored(a, n).first

    /** λ(n), Carmichael's function: the least k with a^k ≡ 1 for every a coprime to n. */
    private fun carmichael(n: BigInteger): BigInteger = primes(n).fold(ONE) { acc, (p, k) ->
        val l = when {
            p == TWO && k <= 2 -> TWO.pow(k - 1)
            p == TWO -> TWO.pow(k - 2)
            else -> p.pow(k - 1) * (p - ONE)
        }
        acc / acc.gcd(l) * l
    }

    /**
     * The smallest primitive root of [n] ≥ 2: the least g whose order is φ(n). Where n has none (n is not 2, 4, pᵏ or 2pᵏ)
     * [Failure.NO_PRIMITIVE_ROOT], shown by λ(n) < φ(n).
     */
    fun primitiveRoot(n: BigInteger): BigInteger {
        if (n < TWO) throw CalcException(Failure.UNDEFINED)
        val (phi, qs) = phiFactored(n)
        if (!hasPrimitiveRoot(n)) {
            if (carmichael(n) < phi) throw CalcException(Failure.NO_PRIMITIVE_ROOT)
            throw CalcException(Failure.UNSOLVED)
        }
        // 1 is a primitive root only of 2.
        var g = if (n == TWO) ONE else TWO
        while (true) {
            Budget.check()
            if (g >= n) throw CalcException(Failure.UNSOLVED)
            if (g.gcd(n) == ONE && isOrder(g, n, phi, qs.keys)) return g
            g += ONE
        }
    }

    /** n is 2, 4, pᵏ or 2pᵏ for an odd prime p. */
    private fun hasPrimitiveRoot(n: BigInteger): Boolean {
        if (n == TWO || n == BigInteger.valueOf(4)) return true
        val odd = if (n.testBit(0)) n else n.shiftRight(1)
        if (!odd.testBit(0)) return false
        return primes(odd).size == 1
    }

    /**
     * The discrete logarithm: the smallest x ≥ 0 with [g]ˣ ≡ [a] (mod [n]), for g coprime to n ≥ 2; every solution is x plus a
     * multiple of ord_n(g). [Failure.NO_DISCRETE_LOG] where no power of g is a.
     */
    fun discreteLog(a0: BigInteger, g0: BigInteger, n: BigInteger): BigInteger = solve(a0, g0, n)?.first
        ?: throw CalcException(Failure.NO_DISCRETE_LOG)

    /** x and ord_n(g) with gˣ ≡ a (mod n), x the smallest; null where there is none. */
    fun solve(a0: BigInteger, g0: BigInteger, n: BigInteger): Pair<BigInteger, BigInteger>? = logarithm(a0, g0, n)?.let { it.x to it.order }

    /** A discrete logarithm: [x] modulo the [order] of g (with its [primes]), from x modulo each prime power ([parts]: residue, q^e). */
    private class Log(val x: BigInteger, val order: BigInteger, val primes: Map<BigInteger, Int>, val parts: List<Pair<BigInteger, BigInteger>>)

    private fun logarithm(a0: BigInteger, g0: BigInteger, n: BigInteger): Log? {
        if (n < TWO) throw CalcException(Failure.UNDEFINED)
        val g = g0.mod(n)
        if (g.gcd(n) != ONE) throw CalcException(Failure.UNDEFINED)
        val a = a0.mod(n)
        // A power of a unit is a unit.
        if (a.gcd(n) != ONE) return null
        val (ord, qs) = orderFactored(g, n)
        // Pohlig–Hellman: x modulo each prime power of the order, then the Chinese remainder theorem.
        val residues = ArrayList<BigInteger>()
        val moduli = ArrayList<BigInteger>()
        for ((q, e) in qs) {
            val gamma = g.modPow(ord / q, n)
            var x = ZERO
            var qk = ONE
            for (k in 0 until e) {
                Budget.check()
                // (a·g⁻ˣ)^(ord/q^(k+1)) is γ^d for the digit d of x in base q.
                val h = (a * g.modPow(x, n).modInverse(n)).mod(n).modPow(ord / (qk * q), n)
                val d = babyGiant(gamma, h, q, n) ?: return null
                x += d * qk
                qk *= q
            }
            residues += x
            moduli += qk
        }
        val x = if (residues.isEmpty()) ZERO else (NumberTheory.crt(residues, moduli) ?: throw CalcException(Failure.UNSOLVED)).first.mod(ord)
        // Put back. For a in the group g generates every digit comes out right, so where gˣ is not a, a is not in it (its
        // order does not divide g's, or it lies outside g's group modulo a composite n): no power of g is a.
        if (g.modPow(x, n) != a) return null
        return Log(x, ord, qs, residues.zip(moduli))
    }

    /** d in [0, q) with γ^d ≡ h (mod n), γ of prime order q, by baby steps γʲ and giant steps h·γ^(−m·i); null where none. */
    private fun babyGiant(gamma: BigInteger, h: BigInteger, q: BigInteger, n: BigInteger): BigInteger? {
        if (h == ONE) return ZERO
        val root = NumberTheory.isqrt(q - ONE) + ONE
        if (root > BigInteger.valueOf(MOST_STEPS.toLong())) throw CalcException(Failure.TOO_LARGE)
        val m = root.toInt()
        // The baby steps in an open table by the low 64 bits of γʲ; a match is checked by powering.
        val size = Integer.highestOneBit(maxOf(4, m) * 2) * 2
        val keys = LongArray(size)
        val values = IntArray(size) { -1 }
        var v = ONE
        for (j in 0 until m) {
            if (j % 1024 == 0) Budget.check()
            var slot = (mix(v.toLong()) and (size - 1).toLong()).toInt()
            while (values[slot] >= 0) slot = (slot + 1) and (size - 1)
            keys[slot] = v.toLong()
            values[slot] = j
            v = (v * gamma).mod(n)
        }
        val step = gamma.modPow(root, n).modInverse(n)
        var y = h
        for (i in 0..m) {
            if (i % 1024 == 0) Budget.check()
            val key = y.toLong()
            var slot = (mix(key) and (size - 1).toLong()).toInt()
            while (values[slot] >= 0) {
                if (keys[slot] == key) {
                    val d = BigInteger.valueOf(i.toLong()) * root + BigInteger.valueOf(values[slot].toLong())
                    if (gamma.modPow(d, n) == h) return d.mod(q)
                }
                slot = (slot + 1) and (size - 1)
            }
            y = (y * step).mod(n)
        }
        return null
    }

    private fun mix(x: Long): Long {
        var z = x * -0x61c8864680b583ebL
        z = z xor (z ushr 29)
        return z
    }

    /**
     * gˣ ≡ a (mod m) written as a congruence in [letter] (c·gˣ + b ≡ 0 with whole c, g, b): x ≡ x₀ (mod ord_m(g)), or none; null
     * where [f] is no such form.
     */
    fun exponential(f: Expr, m: BigInteger, letter: String): Discrete.Congruences? {
        val x = Expr.Sym(letter)
        val terms = if (f is Expr.Add) f.terms else listOf(f)
        var c: BigInteger? = null
        var g: BigInteger? = null
        var b = ZERO
        for (t in terms) {
            val factors = if (t is Expr.Mul) t.factors else listOf(t)
            val power = factors.filter { it.has(x) }
            if (power.isEmpty()) {
                b += integer(t) ?: return null
                continue
            }
            if (c != null || power.size != 1) return null
            val p = power[0] as? Expr.Pow ?: return null
            if (p.exp != x) return null
            g = integer(p.base) ?: return null
            c = (factors - p).fold(ONE) { acc, k -> acc * (integer(k) ?: return null) }
        }
        val base = g ?: return null
        val coefficient = c ?: return null
        if (m < TWO || base.gcd(m) != ONE) return null
        val inverse = try { coefficient.modInverse(m) } catch (e: ArithmeticException) { return null }
        val a = (b.negate() * inverse).mod(m)
        val found = solve(a, base, m)
        // No power of g is a: a is no unit, or not in the subgroup of a prime of g's order (Pohlig–Hellman).
        if (found == null) return Discrete.Congruences(letter, emptyList(), m)
        val (x0, ord) = found
        // Put back: c·g^x₀ + b ≡ 0.
        if ((coefficient * base.modPow(x0, m) + b).mod(m).signum() != 0) throw CalcException(Failure.UNSOLVED)
        return Discrete.Congruences(letter, listOf(x0), ord)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Steps.

    private val CALL = Regex("""^(multorder|primroot|discretelog)\((.*)\)$""")
    private val WHOLE = Regex("""^-?[0-9]+$""")

    /** The steps of multorder, primroot or discretelog called with whole numbers as written, or null. */
    internal fun steps(c: Calculation, latex: String): List<Step>? {
        val plain = latex.replace(Regex("""\\operatorname\{([a-z]+)\}"""), "$1").replace("\\left(", "(").replace("\\right)", ")")
            .replace("\\,", "").replace(" ", "")
        val m = CALL.matchEntire(plain) ?: return null
        val args = m.groupValues[2].split(',')
        if (args.any { !WHOLE.matches(it) }) return null
        val v = args.map { BigInteger(it) }
        val out = ArrayList<Step>()
        fun step(shown: String, key: StepKey, vararg a: String) { out += Step(shown, StepText(key, a.toList())) }
        fun primes(f: Map<BigInteger, Int>) = f.entries.sortedBy { it.key }.joinToString(" \\cdot ") { (q, e) -> if (e == 1) "$q" else "$q^{$e}" }
        when (m.groupValues[1]) {
            "multorder" -> {
                if (v.size != 2) return null
                val (a0, n) = v
                if (n < TWO) return null
                val a = a0.mod(n)
                val (phi, qs) = phiFactored(n)
                val k = order(a, n)
                step("$a^{k} \\equiv 1 \\pmod{$n}", StepKey.START)
                step("\\varphi($n) = $phi" + if (qs.isEmpty()) "" else " = " + primes(qs), StepKey.ORDER_PHI)
                val kPrimes = qs.keys.filter { k.mod(it).signum() == 0 }.sorted()
                step((listOf("$a^{$k} \\equiv 1") + kPrimes.map { q -> "$a^{${k / q}} \\equiv ${a.modPow(k / q, n)}" })
                    .joinToString(",\\ ") + " \\pmod{$n}", StepKey.ORDER_TEST)
                step("\\operatorname{ord}_{$n}($a) = $k", StepKey.RESULT)
            }
            "primroot" -> {
                if (v.size != 1) return null
                val n = v[0]
                if (n < TWO) return null
                val g = primitiveRoot(n)
                val (phi, qs) = phiFactored(n)
                step("\\varphi($n) = $phi" + if (qs.isEmpty()) "" else " = " + primes(qs), StepKey.ORDER_PHI)
                val ps = qs.keys.sorted()
                if (ps.isEmpty()) return null
                var k = TWO
                while (k < g) {
                    Budget.check()
                    val d = k.gcd(n)
                    if (d != ONE) step("\\gcd($k, $n) = $d", StepKey.PRIMROOT_REJECT, "$k")
                    else {
                        val q = ps.firstOrNull { k.modPow(phi / it, n) == ONE } ?: if (k.modPow(phi, n) == ONE) null else return null
                        step(if (q == null) "$k^{$phi} \\equiv 1 \\pmod{$n}" else "$k^{${phi / q}} \\equiv 1 \\pmod{$n}", StepKey.PRIMROOT_REJECT, "$k")
                    }
                    k += ONE
                }
                step(ps.joinToString(",\\ ") { q -> "$g^{${phi / q}} \\equiv ${g.modPow(phi / q, n)}" } + " \\pmod{$n}", StepKey.PRIMROOT_ACCEPT, "$g")
                step("g = $g", StepKey.RESULT)
            }
            "discretelog" -> {
                if (v.size != 3) return null
                val (a0, g0, n) = v
                if (n < TWO) return null
                val log = logarithm(a0, g0, n) ?: return null
                val a = a0.mod(n)
                val g = g0.mod(n)
                step("$g^{x} \\equiv $a \\pmod{$n}", StepKey.START)
                step("\\operatorname{ord}_{$n}($g) = ${log.order}" + if (log.primes.isEmpty()) "" else " = " + primes(log.primes), StepKey.DLOG_ORDER)
                for ((r, q) in log.parts) step("x \\equiv $r \\pmod{$q}", StepKey.DLOG_PART, "$q")
                if (log.parts.size > 1) step("x \\equiv ${log.x} \\pmod{${log.order}}", StepKey.CRT)
                step("x = ${log.x}", StepKey.RESULT)
            }
        }
        return out.takeIf { it.size >= 2 }
    }
}
