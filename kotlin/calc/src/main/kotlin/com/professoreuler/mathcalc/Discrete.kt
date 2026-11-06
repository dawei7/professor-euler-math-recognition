package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Whole numbers and sequences: the number functions a course writes (gcd, lcm, mod, binomial coefficients, Catalan, Bell and
 * Stirling numbers, partitions, Fibonacci, φ, σ, τ, μ, the Jacobi symbol, the n-th prime, the mean and median of a list),
 * congruences and their systems (the Chinese remainder theorem), whole-number solutions of linear and Pell equations, linear
 * recurrences with constant coefficients solved in closed form, and the rule of a list of numbers. Every answer is checked:
 * put back, multiplied back, or its terms recomputed.
 */
object Discrete {
    private fun tidy(e: Expr) = Calculus.tidy(e)
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE

    /** `TWO` is Java 9: Android has it only from version 13. */
    private val TWO = BigInteger.valueOf(2)

    // ---------------------------------------------------------------------------------------------------------------------
    // Number functions, worked out where they are written.

    private val MANY = 2..10_000
    private val LIST = 1..10_000

    /** The names, as typed or as `\operatorname{…}` (English in every language), and how many arguments each takes. */
    val NAMES: Map<String, IntRange> = mapOf(
        "gcd" to MANY, "lcm" to MANY,
        "mod" to 2..2, "modinv" to 2..2, "powmod" to 3..3,
        "binom" to 2..2, "ncr" to 2..2, "choose" to 2..2, "npr" to 2..2, "perm" to 2..2,
        "catalan" to 1..1, "bell" to 1..1, "stirling" to 2..2, "stirling1" to 2..2, "partitions" to 1..1,
        "fibonacci" to 1..1, "fib" to 1..1, "lucas" to 1..1,
        "totient" to 1..1, "phi" to 1..1, "sigma" to 1..1, "tau" to 1..1, "mu" to 1..1, "moebius" to 1..1,
        "jacobi" to 2..2, "legendre" to 2..2, "prime" to 1..1, "nextprime" to 1..1, "prevprime" to 1..1,
        "mean" to LIST, "median" to LIST, "var" to MANY, "variance" to MANY, "sd" to MANY, "stdev" to MANY,
        "binompdf" to 3..3, "binomcdf" to 3..3, "poissonpdf" to 2..2, "poissoncdf" to 2..2, "geometpdf" to 2..2,
        "normalpdf" to 1..3, "normalcdf" to 2..4, "invnorm" to 1..3,
    )

    /** Longest first, so `stirling1` is not `stirling` and a 1. */
    val ORDERED: List<String> = NAMES.keys.sortedByDescending { it.length }

    private fun whole(e: Expr): BigInteger = integer(e) ?: throw CalcException(Failure.UNDEFINED)

    private fun small(e: Expr, most: Int): Int {
        val n = whole(e)
        if (n.signum() < 0) throw CalcException(Failure.UNDEFINED)
        if (n > BigInteger.valueOf(most.toLong())) throw CalcException(Failure.TOO_LARGE)
        return n.toInt()
    }

    private fun factors(n: BigInteger): List<Pair<BigInteger, Int>> {
        if (n.signum() <= 0) throw CalcException(Failure.UNDEFINED)
        if (n == ONE) return emptyList()
        return NumberTheory.factor(n) ?: throw CalcException(Failure.TOO_LARGE)
    }

    private fun big(n: BigInteger) = num(Rational.of(n))

    /**
     * [name] at [args], or null where it is no number function or an argument is no number (then the letters are a product:
     * mu(x+1) is m·u·(x + 1)); numbers, but not as many as it takes, fail ([Failure.ARGUMENTS]): gcd(12) is no g·c·d·12.
     */
    fun call(name: String, args: List<Expr>): Expr? {
        val key = name.lowercase()
        val arity = NAMES[key] ?: return null
        if (args.any { it.symbols().isNotEmpty() || it is Expr.Mat }) return null
        if (args.size !in arity) throw CalcException(Failure.ARGUMENTS)
        Budget.check()
        return when (key) {
            "gcd" -> big(args.map { whole(it).abs() }.reduce { a, b -> a.gcd(b) })
            "lcm" -> big(args.map { whole(it).abs() }.reduce { a, b -> if (a.signum() == 0 || b.signum() == 0) ZERO else a / a.gcd(b) * b })
            "mod" -> {
                val m = whole(args[1]).abs()
                if (m.signum() == 0) throw CalcException(Failure.DIVISION_BY_ZERO)
                big(whole(args[0]).mod(m))
            }
            "modinv" -> {
                val m = whole(args[1]).abs()
                try { big(whole(args[0]).modInverse(m)) } catch (e: ArithmeticException) { throw CalcException(Failure.UNDEFINED) }
            }
            "powmod" -> {
                val m = whole(args[2]).abs()
                if (m.signum() == 0) throw CalcException(Failure.DIVISION_BY_ZERO)
                try { big(whole(args[0]).modPow(whole(args[1]), m)) } catch (e: ArithmeticException) { throw CalcException(Failure.UNDEFINED) }
            }
            "binom", "ncr", "choose" -> binomial(args[0], args[1])
            "npr", "perm" -> {
                val n = whole(args[0])
                val k = small(args[1], 100_000)
                if (n.signum() < 0 || BigInteger.valueOf(k.toLong()) > n) Expr.ZERO
                else big((0 until k).fold(ONE) { acc, i -> Budget.check(); acc * (n - BigInteger.valueOf(i.toLong())) })
            }
            "catalan" -> {
                val n = small(args[0], 20_000)
                big(NumberTheory.binomial(BigInteger.valueOf(2L * n), BigInteger.valueOf(n.toLong())) / BigInteger.valueOf(n + 1L))
            }
            "bell" -> big(bell(small(args[0], 1_000)))
            "stirling" -> big(stirling2(small(args[0], 1_000), small(args[1], 1_000)))
            "stirling1" -> big(stirling1(small(args[0], 1_000), small(args[1], 1_000)))
            "partitions" -> big(partitions(small(args[0], 20_000)))
            "fibonacci", "fib" -> big(fibonacci(whole(args[0])))
            "lucas" -> big(lucas(whole(args[0])))
            "totient", "phi" -> whole(args[0]).let { n -> big(if (n == ONE) ONE else NumberTheory.phi(factors(n))) }
            "sigma" -> whole(args[0]).let { n -> big(if (n == ONE) ONE else NumberTheory.sigma(factors(n))) }
            "tau" -> whole(args[0]).let { n -> big(if (n == ONE) ONE else NumberTheory.divisorCount(factors(n))) }
            "mu", "moebius" -> whole(args[0]).let { n -> num(if (n == ONE) 1L else NumberTheory.mu(factors(n)).toLong()) }
            "jacobi" -> num(jacobi(whole(args[0]), whole(args[1])).toLong())
            "legendre" -> {
                val p = whole(args[1])
                if (p < BigInteger.valueOf(3) || !NumberTheory.isPrime(p)) throw CalcException(Failure.UNDEFINED)
                num(jacobi(whole(args[0]), p).toLong())
            }
            "prime" -> big(nthPrime(small(args[0], 300_000).also { if (it < 1) throw CalcException(Failure.UNDEFINED) }))
            "nextprime" -> {
                var n = whole(args[0]).max(ONE) + ONE
                while (!NumberTheory.isPrime(n)) { Budget.check(); n += ONE }
                big(n)
            }
            "prevprime" -> {
                var n = whole(args[0]) - ONE
                if (n < TWO) throw CalcException(Failure.UNDEFINED)
                while (!NumberTheory.isPrime(n)) { Budget.check(); n -= ONE }
                big(n)
            }
            "mean" -> tidy(add(args) / num(args.size.toLong()))
            "median" -> if (args.size == 1) args[0] else (Stats.summary(args) ?: throw CalcException(Failure.UNDEFINED)).median
            "var", "variance" -> (Stats.summary(args) ?: throw CalcException(Failure.UNDEFINED)).sampleVariance ?: throw CalcException(Failure.UNDEFINED)
            "sd", "stdev" -> (Stats.summary(args) ?: throw CalcException(Failure.UNDEFINED)).sampleSd ?: throw CalcException(Failure.UNDEFINED)
            else -> Stats.distribution(key, args) ?: throw CalcException(Failure.UNDEFINED)
        }
    }

    /** n choose k: for a whole k ≥ 0 and any rational n, n(n − 1)…(n − k + 1)/k!; 0 for k < 0. */
    fun binomial(n: Expr, k: Expr): Expr {
        val kk = whole(k)
        if (kk.signum() < 0) return Expr.ZERO
        val r = rational(n) ?: throw CalcException(Failure.UNDEFINED)
        if (r.isInteger && r.num.signum() >= 0) return big(NumberTheory.binomial(r.num, kk))
        if (kk > BigInteger.valueOf(10_000)) throw CalcException(Failure.TOO_LARGE)
        var out = Rational.ONE
        for (i in 0 until kk.toInt()) {
            Budget.check()
            out = out * (r - Rational.of(i.toLong())) / Rational.of(i + 1L)
        }
        return num(out)
    }

    /** Bell numbers by the Bell triangle. */
    private fun bell(n: Int): BigInteger {
        var row = listOf(ONE)
        repeat(n) {
            Budget.check()
            val next = ArrayList<BigInteger>(row.size + 1)
            next += row.last()
            for (v in row) next += next.last() + v
            row = next
        }
        return row.first()
    }

    /** Stirling numbers of the second kind S(n, k): ways to split n things into k nonempty groups. */
    private fun stirling2(n: Int, k: Int): BigInteger {
        if (k > n) return ZERO
        var row = Array(k + 1) { if (it == 0) ONE else ZERO }
        for (i in 1..n) {
            Budget.check()
            val next = Array(k + 1) { ZERO }
            for (j in 1..minOf(i, k)) next[j] = BigInteger.valueOf(j.toLong()) * row[j] + row[j - 1]
            row = next
        }
        return row[k]
    }

    /** Unsigned Stirling numbers of the first kind c(n, k): permutations of n things with k cycles. */
    private fun stirling1(n: Int, k: Int): BigInteger {
        if (k > n) return ZERO
        var row = Array(k + 1) { if (it == 0) ONE else ZERO }
        for (i in 1..n) {
            Budget.check()
            val next = Array(k + 1) { ZERO }
            for (j in 1..minOf(i, k)) next[j] = BigInteger.valueOf(i - 1L) * row[j] + row[j - 1]
            row = next
        }
        return row[k]
    }

    /** p(n), the partitions of n, by Euler's pentagonal number theorem. */
    private fun partitions(n: Int): BigInteger {
        val p = Array(n + 1) { ZERO }
        p[0] = ONE
        for (m in 1..n) {
            Budget.check()
            var sum = ZERO
            var k = 1
            while (true) {
                val g1 = k * (3 * k - 1) / 2
                if (g1 > m) break
                val sign = if (k % 2 == 1) 1 else -1
                sum = if (sign > 0) sum + p[m - g1] else sum - p[m - g1]
                val g2 = k * (3 * k + 1) / 2
                if (g2 <= m) sum = if (sign > 0) sum + p[m - g2] else sum - p[m - g2]
                k++
            }
            p[m] = sum
        }
        return p[n]
    }

    /** F(n) by fast doubling; F(−n) = (−1)^(n+1)·F(n). */
    fun fibonacci(n: BigInteger): BigInteger {
        if (n.abs() > BigInteger.valueOf(1_000_000)) throw CalcException(Failure.TOO_LARGE)
        val m = n.abs()
        var a = ZERO
        var b = ONE
        for (bit in m.bitLength() - 1 downTo 0) {
            Budget.check()
            val c = a * (b.shiftLeft(1) - a)
            val d = a * a + b * b
            if (m.testBit(bit)) { a = d; b = c + d } else { a = c; b = d }
        }
        return if (n.signum() < 0 && !m.testBit(0)) a.negate() else a
    }

    private fun lucas(n: BigInteger): BigInteger = fibonacci(n - ONE) + fibonacci(n + ONE)

    /** The Jacobi symbol (a/n) for an odd n > 0. */
    private fun jacobi(a0: BigInteger, n0: BigInteger): Int {
        if (n0.signum() <= 0 || !n0.testBit(0)) throw CalcException(Failure.UNDEFINED)
        var a = a0.mod(n0)
        var n = n0
        var t = 1
        val three = BigInteger.valueOf(3)
        val five = BigInteger.valueOf(5)
        val eight = BigInteger.valueOf(8)
        val four = BigInteger.valueOf(4)
        while (a.signum() != 0) {
            while (!a.testBit(0)) {
                a = a.shiftRight(1)
                val r = n.mod(eight)
                if (r == three || r == five) t = -t
            }
            val swap = a
            a = n
            n = swap
            if (a.mod(four) == three && n.mod(four) == three) t = -t
            a = a.mod(n)
        }
        return if (n == ONE) t else 0
    }

    /** The k-th prime, by a sieve up to Rosser's bound k(ln k + ln ln k). */
    private fun nthPrime(k: Int): BigInteger {
        val limit = if (k < 6) 15 else (k * (kotlin.math.ln(k.toDouble()) + kotlin.math.ln(kotlin.math.ln(k.toDouble())))).toInt() + 10
        val composite = BooleanArray(limit + 1)
        var count = 0
        for (i in 2..limit) {
            if (composite[i]) continue
            count++
            if (count == k) return BigInteger.valueOf(i.toLong())
            var j = i.toLong() * i
            while (j <= limit) { composite[j.toInt()] = true; j += i }
            if (i % 4096 == 0) Budget.check()
        }
        throw CalcException(Failure.TOO_LARGE)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // A whole number's facts.

    /** What there is to say about a whole number n ≥ 2. */
    class Facts(
        val n: BigInteger, val prime: Boolean, val factors: List<Pair<BigInteger, Int>>, val divisorCount: BigInteger,
        val divisors: List<BigInteger>?, val sigma: BigInteger, val phi: BigInteger, val mu: Int, val previousPrime: BigInteger?,
        val nextPrime: BigInteger, val square: Boolean,
    ) {
        /** Perfect (σ = 2n), abundant (σ > 2n) or deficient. */
        val kind: Int get() = (sigma - n).compareTo(n)
        val binary: String get() = n.toString(2)
        val hexadecimal: String get() = n.toString(16).uppercase()
    }

    fun facts(n: BigInteger): Facts? {
        if (n < TWO || n.bitLength() > 110) return null
        val f = NumberTheory.factor(n) ?: return null
        val prime = f.size == 1 && f[0].second == 1
        var next = n + ONE
        while (!NumberTheory.isPrime(next)) { Budget.check(); next += ONE }
        var previous: BigInteger? = n - ONE
        while (previous != null && previous >= TWO && !NumberTheory.isPrime(previous)) { Budget.check(); previous -= ONE }
        if (previous != null && previous < TWO) previous = null
        val root = NumberTheory.isqrt(n)
        return Facts(n, prime, f, NumberTheory.divisorCount(f), NumberTheory.divisors(f, 64), NumberTheory.sigma(f), NumberTheory.phi(f),
            NumberTheory.mu(f), previous, next, root * root == n)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Congruences.

    /**
     * x ≡ r (mod [modulus]) for each of [residues]; none where there is no solution. [parts] are the lines on their own: each
     * one's coefficients (lowest first) and modulus, and its solutions as residues of [Part.step].
     */
    class Congruences(val letter: String, val residues: List<BigInteger>, val modulus: BigInteger, val parts: List<Part> = emptyList()) {
        class Part(val coefficients: List<BigInteger>, val m: BigInteger, val residues: List<BigInteger>, val step: BigInteger)
    }

    /** The whole-number coefficients of f in x, lowest first, after multiplying by a denominator that is a unit mod m. */
    private fun integerCoefficients(f: Expr, x: Expr.Sym, m: BigInteger?): List<BigInteger>? {
        val c = Solve.coefficientsIn(Algebra.expand(f), x)?.map { rational(tidy(it)) ?: return null } ?: return null
        val d = c.fold(ONE) { acc, r -> acc / acc.gcd(r.den) * r.den }
        if (m != null && d.gcd(m) != ONE) return null
        return c.map { (it * Rational.of(d)).num }
    }

    private fun valueMod(c: List<BigInteger>, x: BigInteger, m: BigInteger): BigInteger = c.foldRight(ZERO) { a, acc -> (acc * x + a).mod(m) }

    /** One congruence f(x) ≡ 0 (mod m): its solutions as residues of a modulus (a linear one as x ≡ x₀ (mod m/g)). */
    private fun one(c0: List<BigInteger>, m: BigInteger): Pair<List<BigInteger>, BigInteger>? {
        val c = c0.map { it.mod(m) }
        val degree = c.indexOfLast { it.signum() != 0 }
        if (degree <= 0) return if (degree < 0) listOf(ZERO) to ONE else emptyList<BigInteger>() to m
        if (degree == 1) {
            val (x0, step) = NumberTheory.linearCongruence(c[1], (-c[0]).mod(m), m) ?: return emptyList<BigInteger>() to m
            return listOf(x0) to step
        }
        if (m > BigInteger.valueOf(200_000)) return null
        val out = ArrayList<BigInteger>()
        var r = ZERO
        while (r < m) {
            if (r.toInt() % 4096 == 0) Budget.check()
            if (valueMod(c, r, m).signum() == 0) out += r
            r += ONE
        }
        return out to m
    }

    /** Every [lines] congruence f(x) ≡ 0 (mod m) at once, by the Chinese remainder theorem; each solution put back into each. */
    fun congruences(lines: List<Pair<Expr, BigInteger>>, letter: String): Congruences? {
        val x = Expr.Sym(letter)
        val coefficients = lines.map { (f, m) -> (integerCoefficients(f, x, m) ?: return null) to m }
        var residues = listOf(ZERO)
        var modulus = ONE
        val parts = ArrayList<Congruences.Part>()
        for ((c, m) in coefficients) {
            if (m < TWO) return null
            val (own, step) = one(c, m) ?: return null
            parts += Congruences.Part(c, m, own, step)
            val combined = LinkedHashSet<BigInteger>()
            for (r in residues) for (s in own) {
                Budget.check()
                NumberTheory.crt(listOf(r, s), listOf(modulus, step))?.let { combined += it.first }
                if (combined.size > 5_000) return null
            }
            modulus = modulus / modulus.gcd(step) * step
            residues = combined.sorted()
            if (residues.isEmpty()) break
        }
        if (residues.any { r -> coefficients.any { (c, m) -> valueMod(c, r, m).signum() != 0 } }) return null
        return Congruences(letter, residues, modulus, parts)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Whole-number equations.

    sealed class Integers {
        /** x = x₀ + sx·t, y = y₀ + sy·t for every whole t. */
        class Linear(val x: String, val y: String, val x0: BigInteger, val y0: BigInteger, val sx: BigInteger, val sy: BigInteger,
            val a: BigInteger = ZERO, val b: BigInteger = ZERO, val c: BigInteger = ZERO) : Integers()

        /** x² − d·y² = 1: the smallest solution in whole numbers above 0; the others are (x₁ + y₁√d)ᵏ. */
        class Pell(val x: String, val y: String, val d: BigInteger, val x1: BigInteger, val y1: BigInteger) : Integers()

        /** No solution in whole numbers. */
        object None : Integers()
    }

    /** f(x, y) = 0 in whole numbers, for a linear f or a Pell equation; null for other kinds. */
    fun integers(f: Expr, letters: List<String>): Integers? {
        if (letters.size != 2) return null
        val (xn, yn) = letters
        val k = Algebra.Kernels()
        val p = Algebra.polynomial(Algebra.expand(f), k) ?: return null
        if (!k.isEmpty) return null
        val d = p.terms.values.fold(ONE) { acc, r -> acc / acc.gcd(r.den) * r.den }
        val c = p.terms.mapValues { (_, r) -> (r * Rational.of(d)).num }
        fun coefficient(vararg powers: Pair<String, Int>): BigInteger =
            c[if (powers.isEmpty()) Monomial.ONE else Monomial(powers.filter { it.second > 0 }.sortedBy { it.first })] ?: ZERO
        val degree = c.keys.maxOfOrNull { m -> m.powers.sumOf { it.second } } ?: return null
        if (degree == 1) {
            val a = coefficient(xn to 1)
            val b = coefficient(yn to 1)
            val rhs = coefficient().negate()
            if (a.signum() == 0 && b.signum() == 0) return null
            val g = a.gcd(b)
            if (rhs.mod(g).signum() != 0) return Integers.None
            if (a.signum() == 0) return Integers.Linear(xn, yn, ZERO, rhs / b, ONE, ZERO, a, b, rhs)
            if (b.signum() == 0) return Integers.Linear(xn, yn, rhs / a, ZERO, ZERO, ONE, a, b, rhs)
            val (x0, y0, sx, sy) = NumberTheory.diophantine(a, b, rhs) ?: return Integers.None
            // The smallest x₀ ≥ 0 of the family.
            val t = x0.negate().let { n -> if (sx.signum() > 0) floorDiv(n, sx) else floorDiv(n.negate(), sx.negate()) }
            var nx = x0 + sx * t
            var ny = y0 + sy * t
            if (nx.signum() < 0) { nx += sx.abs(); ny += if (sx.signum() > 0) sy else sy.negate() }
            val step = if (sx.signum() < 0) sx.negate() to sy.negate() else sx to sy
            if (a * nx + b * ny != rhs || a * step.first + b * step.second != ZERO) return null
            return Integers.Linear(xn, yn, nx, ny, step.first, step.second, a, b, rhs)
        }
        if (degree == 2) {
            // x² − d·y² = 1 (or a multiple of it, either sign).
            val xx = coefficient(xn to 2)
            val yy = coefficient(yn to 2)
            val one = coefficient()
            val others = c.keys.count { it != Monomial.ONE && it != Monomial(listOf(xn to 2)) && it != Monomial(listOf(yn to 2)) && c[it]!!.signum() != 0 }
            if (others != 0 || xx.signum() == 0 || one.signum() == 0 || one.negate() != xx) return null
            if ((-yy).mod(xx).signum() != 0) return null
            val dd = (-yy) / xx
            if (dd.signum() <= 0) return null
            val (x1, y1) = NumberTheory.pell(dd) ?: return null
            if (x1 * x1 - dd * y1 * y1 != ONE) return null
            return Integers.Pell(xn, yn, dd, x1, y1)
        }
        return null
    }

    private fun floorDiv(a: BigInteger, b: BigInteger): BigInteger {
        val (q, r) = a.divideAndRemainder(b)
        return if (r.signum() != 0 && (r.signum() < 0) != (b.signum() < 0)) q - ONE else q
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Linear recurrences with constant coefficients.

    /** uₙ in closed form ([general] where no initial values fixed its constants C₁, C₂, …), and its first [terms] from the start. */
    class Recurrence(val name: String, val closed: Expr, val general: Boolean, val start: Int, val terms: List<Expr>, val order: Int,
        /** The characteristic polynomial in r, its roots with their multiplicities, the general solution in C₁, C₂, …, the particular one. */
        val characteristic: Expr? = null, val roots: List<Pair<Expr, Int>> = emptyList(), val generalForm: Expr? = null,
        val particular: Expr = Expr.ZERO, val constants: Map<String, Expr> = emptyMap())

    private val SHIFT = Regex("^([A-Za-z])_n([+-][0-9]+)?$")
    private val INITIAL = Regex("^([A-Za-z])_([0-9]+)$")
    private val N = Expr.Sym("n")

    /** Whether [names] hold a term of a recurrence (u_n, u_{n+1}). */
    fun isRecurrence(names: Set<String>): Boolean = names.any { SHIFT.matches(it) && it != "n" }

    /** The lines `[equations]` (each a difference lhs − rhs): one recurrence in uₙ₊ⱼ, and values u₀ = … as its initial values. */
    fun recurrence(equations: List<Expr>): Recurrence? {
        val main = equations.filter { e -> e.symbols().any { SHIFT.matches(it) } }.singleOrNull() ?: return null
        val shifted = main.symbols().filter { SHIFT.matches(it) }
        val name = SHIFT.find(shifted.first())!!.groupValues[1]
        if (shifted.any { SHIFT.find(it)!!.groupValues[1] != name }) return null
        if (main.symbols().any { !SHIFT.matches(it) && it != "n" }) return null
        val offsets = shifted.associateWith { SHIFT.find(it)!!.groupValues[2].ifEmpty { "0" }.toInt() }
        val lo = offsets.values.min()
        val order = offsets.values.max() - lo
        if (order !in 1..6) return null
        // Σ cⱼ uₘ₊ⱼ = g(m) with m = n + lo.
        val expanded = Algebra.expand(main)
        val c = ArrayList<Expr>()
        var rest = expanded
        for (j in 0..order) {
            val sym = offsets.entries.firstOrNull { it.value - lo == j }?.key?.let { Expr.Sym(it) }
            if (sym == null) { c += Expr.ZERO; continue }
            val k = Solve.coefficientsIn(expanded, sym) ?: return null
            if (k.size > 2) return null
            val cj = tidy(k.getOrElse(1) { Expr.ZERO })
            if (cj.symbols().isNotEmpty()) return null
            c += cj
            rest = rest.substitute(sym, Expr.ZERO)
        }
        if (isZero(c[0]) || isZero(c[order])) return null
        val g = tidy(neg(tidy(rest)).substitute(N, add(N, num(-lo.toLong()))))
        if (g.symbols().any { it != "n" }) return null
        val initial = LinkedHashMap<Int, Expr>()
        for (e in equations) {
            if (e === main) continue
            val names = e.symbols()
            val s = names.singleOrNull()?.let { INITIAL.find(it) } ?: return null
            if (s.groupValues[1] != name) return null
            val sym = Expr.Sym(names.single())
            val k = Solve.coefficientsIn(Algebra.expand(e), sym) ?: return null
            if (k.size != 2 || isZero(k[1])) return null
            initial[s.groupValues[2].toInt()] = tidy(neg(k[0]) / k[1])
        }
        if (initial.isNotEmpty() && initial.size != order) return null
        return solve(name, c, g, initial)
    }

    /** Σ_{j ≤ k} cⱼ·u(n + j) = g(n), with [initial] values or none. */
    private fun solve(name: String, c: List<Expr>, g: Expr, initial: Map<Int, Expr>): Recurrence? {
        val order = c.size - 1
        val r = Expr.Sym("\u0001r")
        val characteristic = tidy(add(c.mapIndexed { j, cj -> mul(cj, pow(r, num(j.toLong()))) }))
        val roots = rootsWithMultiplicity(characteristic, r) ?: return null
        if (roots.sumOf { it.second } != order) return null
        val basis = ArrayList<Expr>()
        val done = HashSet<Expr>()
        for ((rho, m) in roots) {
            if (rho in done) continue
            val parts = Complex.parts(rho)
            val polar = if (parts?.second != null && !isZero(parts.second!!)) ComplexAnalysis.polar(rho) else null
            // A pair of complex roots is taken from the one above the real axis.
            if (polar != null && (rational(tidy(polar.argument / Expr.PI))?.signum ?: 0) < 0) continue
            for (s in 0 until m) {
                val ns = if (s == 0) Expr.ONE else pow(N, num(s.toLong()))
                if (polar != null && rational(tidy(polar.argument / Expr.PI))?.signum ?: 0 > 0) {
                    // A complex pair as rⁿ·cos(nθ) and rⁿ·sin(nθ).
                    val rn = pow(polar.modulus, N)
                    basis += tidy(mul(ns, rn, fn(Func.COS, mul(N, polar.argument))))
                    basis += tidy(mul(ns, rn, fn(Func.SIN, mul(N, polar.argument))))
                } else if (polar == null) {
                    basis += tidy(mul(ns, pow(rho, N)))
                }
            }
            done += rho
            // The conjugate is the pair's other half.
            if (polar != null) done += tidy(add(parts!!.first, neg(mul(Expr.I, parts.second!!))))
        }
        if (basis.size != order) return null
        val particular = if (isZero(g)) Expr.ZERO else particular(c, g, roots) ?: return null
        val constants = (1..order).map { Expr.Sym("C_$it") }
        val general = tidy(add(basis.mapIndexed { k, b -> mul(constants[k], b) } + particular))
        fun at(e: Expr, n: Int) = tidy(e.substitute(N, num(n.toLong())))
        if (initial.isEmpty()) {
            // Put back with constants of no special kind.
            val sample = listOf(2L to 3L, -5L to 7L, 3L to 11L, 7L to 5L, -1L to 2L, 4L to 9L).map { (p, q) -> Rational.of(BigInteger.valueOf(p), BigInteger.valueOf(q)) }
            var e = general
            constants.forEachIndexed { k, s -> e = e.substitute(s, num(sample[k])) }
            for (n in 0..8) {
                Budget.check()
                val residual = tidy(add(c.mapIndexed { j, cj -> mul(cj, at(e, n + j)) }) - at(g, n))
                if (!LinAlg.isNull(residual)) return null
            }
            return Recurrence(name, general, true, 0, emptyList(), order, shown(characteristic, r), roots, general, particular)
        }
        val equations = initial.map { (n, v) -> tidy(at(general, n) - v) }
        val solved = Solve.system(equations, constants.map { it.name })?.singleOrNull() ?: return null
        var closed = general
        for (s in constants) closed = closed.substitute(s, solved[s.name] ?: return null)
        closed = tidy(closed)
        // The terms by the recurrence itself, from consecutive initial values, against the closed form.
        val start = initial.keys.min()
        val consecutive = initial.keys.sorted() == (start until start + order).toList()
        val terms = ArrayList<Expr>()
        if (consecutive) {
            val u = ArrayList<Expr>()
            for (j in 0 until order) u += initial.getValue(start + j)
            while (u.size < order + 12) {
                Budget.check()
                val m = start + u.size - order
                val known = add((0 until order).map { j -> mul(c[j], u[u.size - order + j]) })
                u += tidy((at(g, m) - known) / c[order])
            }
            for ((k, v) in u.withIndex()) if (!LinAlg.isNull(tidy(at(closed, start + k) - v))) return null
            terms += u.take(8)
        } else {
            for ((n, v) in initial) if (!LinAlg.isNull(tidy(at(closed, n) - v))) return null
            for (n in 0..8) {
                val residual = tidy(add(c.mapIndexed { j, cj -> mul(cj, at(closed, n + j)) }) - at(g, n))
                if (!LinAlg.isNull(residual)) return null
            }
            for (n in start until start + 8) terms += at(closed, n)
        }
        return Recurrence(name, closed, false, start, terms, order, shown(characteristic, r), roots, general, particular,
            constants.associate { it.name to solved.getValue(it.name) })
    }

    /** The characteristic polynomial in the letter r, as the steps write it. */
    private fun shown(p: Expr, r: Expr.Sym) = tidy(p.substitute(r, Expr.Sym("r")))

    /** The roots of p in r, each with its multiplicity (how many derivatives vanish there). */
    private fun rootsWithMultiplicity(p: Expr, r: Expr.Sym): List<Pair<Expr, Int>>? {
        val found = Solve.equation(p, Expr.ZERO, r)
        if (found.numeric) return null
        val roots = (found.values + found.complex).distinct()
        return roots.map { rho ->
            var m = 1
            var d = Calculus.derivative(p, r)
            while (m < 8 && LinAlg.isNull(tidy(d.substitute(r, rho)))) { m++; d = Calculus.derivative(d, r) }
            rho to m
        }
    }

    /**
     * A particular solution for g(n) as a sum of polynomials times bᵐ: the guess nˢ·bⁿ·(q₀ + q₁n + …), s the multiplicity of
     * b as a root; its coefficients from the recurrence at enough values of n, then checked at others.
     */
    private fun particular(c: List<Expr>, g: Expr, roots: List<Pair<Expr, Int>>): Expr? {
        val terms = tidy(Algebra.expand(g)).let { if (it is Expr.Add) it.terms else listOf(it) }
        val byBase = LinkedHashMap<Expr, Int>()
        for (t in terms) {
            val factors = if (t is Expr.Mul) t.factors else listOf(t)
            var base: Expr = Expr.ONE
            val rest = ArrayList<Expr>()
            for (f in factors) {
                val linear = if (f is Expr.Pow && f.base.symbols().isEmpty() && f.exp != N) Solve.coefficientsIn(Algebra.expand(f.exp), N)?.takeIf {
                    it.size == 2 && it.all { k -> k.symbols().isEmpty() } && !isZero(it[1])
                } else null
                if (f is Expr.Pow && f.exp == N && f.base.symbols().isEmpty()) base = mul(base, f.base)
                else if (linear != null) {
                    base = mul(base, pow((f as Expr.Pow).base, linear[1]))
                    rest += pow(f.base, linear[0])
                }
                else if (f.has(N) && !(f == N || (f is Expr.Pow && f.base == N && integer(f.exp)?.signum() == 1))) return null
                else rest += f
            }
            val degree = (Solve.coefficientsIn(mul(rest), N) ?: return null).size - 1
            val b = tidy(base)
            byBase[b] = maxOf(byBase[b] ?: 0, degree)
        }
        val unknowns = ArrayList<Expr.Sym>()
        val guess = ArrayList<Expr>()
        for ((b, degree) in byBase) {
            val s = roots.firstOrNull { LinAlg.isNull(tidy(it.first - b)) }?.second ?: 0
            for (i in 0..degree) {
                val q = Expr.Sym("\u0001q${unknowns.size}")
                unknowns += q
                guess += mul(q, pow(N, num((s + i).toLong())), if (b == Expr.ONE) Expr.ONE else pow(b, N))
            }
        }
        val p = add(guess)
        fun at(e: Expr, n: Int) = tidy(e.substitute(N, num(n.toLong())))
        fun residual(e: Expr, n: Int) = tidy(add(c.mapIndexed { j, cj -> mul(cj, at(e, n + j)) }) - at(g, n))
        val equations = (0 until unknowns.size + 2).map { residual(p, it) }
        val solved = Solve.system(equations, unknowns.map { it.name })?.singleOrNull() ?: return null
        var out: Expr = p
        for (q in unknowns) out = out.substitute(q, solved[q.name] ?: return null)
        out = tidy(out)
        for (n in 20..25) if (!LinAlg.isNull(residual(out, n))) return null
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The rule of a list of numbers.

    /** aₙ (n from 1) as a polynomial or by a recurrence aₙ = Σ cⱼ·aₙ₋ⱼ, and the next three terms. */
    class Rule(val formula: Expr?, val recurrence: List<Expr>?, val next: List<Expr>)

    fun sequence(values: List<Expr>): Rule? {
        if (values.size < 4) return null
        val a = values.map { rational(it) ?: return null }
        // Finite differences: constant at some level, with at least two terms there.
        var level = a
        var degree = 0
        val firsts = ArrayList<Rational>()
        while (true) {
            firsts += level[0]
            if (level.all { it == level[0] }) break
            if (level.size <= 2) { degree = -1; break }
            level = level.zipWithNext { x, y -> y - x }
            degree++
        }
        if (degree >= 0 && level.size >= 2 && degree <= a.size - 2) {
            // Newton's forward form: Σ Δᵏa₁·C(n − 1, k).
            var p: Expr = Expr.ZERO
            for (k in 0..degree) {
                var term: Expr = num(firsts[k])
                for (i in 0 until k) term = mul(term, add(N, num(-1L - i)))
                term = mul(term, num(Rational.ONE / Rational.of(Algebra.factorial(k))))
                p = add(p, term)
            }
            val formula = tidy(Algebra.expand(p))
            fun at(n: Int) = tidy(formula.substitute(N, num(n.toLong())))
            if ((1..a.size).any { at(it) != num(a[it - 1]) }) return null
            return Rule(formula, null, (a.size + 1..a.size + 3).map { at(it) })
        }
        // The shortest linear recurrence (Berlekamp–Massey), with a term beyond those that fix it.
        val c = berlekampMassey(a)
        if (c.isEmpty() || a.size < 2 * c.size + 2) return null
        for (n in c.size until a.size) {
            val v = c.indices.fold(Rational.ZERO) { acc, j -> acc + c[j] * a[n - 1 - j] }
            if (v != a[n]) return null
        }
        val next = ArrayList(a)
        repeat(3) { next += c.indices.fold(Rational.ZERO) { acc, j -> acc + c[j] * next[next.size - 1 - j] } }
        // In closed form where it has one: Σ_{j} cⱼ … as Σ coefficients of u(n + j), from a₁ … a_L.
        val order = c.size
        val coefficients = (0..order).map { j -> if (j == order) Expr.ONE else num(-c[order - 1 - j]) }
        val closed = try {
            solve("a", coefficients, Expr.ZERO, (1..order).associateWith { num(a[it - 1]) })?.closed
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        }
        return Rule(closed, c.map { num(it) }, next.drop(a.size).map { num(it) })
    }

    /** cⱼ with sₙ = Σ cⱼ·sₙ₋ⱼ₋₁ (j from 0), the shortest such, exactly over the rationals. */
    private fun berlekampMassey(s: List<Rational>): List<Rational> {
        var cc = mutableListOf(Rational.ONE)
        var bb = mutableListOf(Rational.ONE)
        var l = 0
        var m = 1
        var b = Rational.ONE
        for (n in s.indices) {
            Budget.check()
            var d = s[n]
            for (i in 1..l) d += cc.getOrElse(i) { Rational.ZERO } * s[n - i]
            if (d.signum == 0) { m++; continue }
            val t = cc.toMutableList()
            val coef = d / b
            while (cc.size < bb.size + m) cc.add(Rational.ZERO)
            for (i in bb.indices) cc[i + m] = cc[i + m] - coef * bb[i]
            if (2 * l <= n) { l = n + 1 - l; bb = t; b = d; m = 1 } else m++
        }
        return (1..l).map { -cc.getOrElse(it) { Rational.ZERO } }
    }
}
