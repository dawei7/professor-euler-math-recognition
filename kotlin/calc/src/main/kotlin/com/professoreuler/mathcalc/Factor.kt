package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.ceil

/**
 * A polynomial as a product: [constant] times each factor to its power, as 3x(x − 2)². [complete] says no factor has
 * factors of its own over the rational numbers; where it is false Calculate found no more, but some may exist (a factor of
 * degree six or more, or one in several unknowns it cannot take apart).
 */
class Product internal constructor(val constant: Rational, internal val factors: List<Pair<Polynomial, Int>>, val complete: Boolean) {
    /** Nothing came out: the polynomial is its own only factor, as x² + 1 is. */
    val trivial: Boolean get() = constant == Rational.ONE && factors.size == 1 && factors[0].second == 1

    internal fun expanded(): Polynomial = factors.fold(Polynomial.constant(constant)) { p, (f, k) -> p * f.pow(k) }
}

/**
 * Factors polynomials over the rational numbers, as school algebra does: the number and the unknowns in common first, then
 * rational roots in one unknown (x² − 5x + 6 = (x − 2)(x − 3)), quadratic factors without them (x⁴ + 4), forms in two
 * unknowns of one degree (4x² − 9y² = (2x − 3y)(2x + 3y)), factors in common by grouping (ax + ay + bx + by), and
 * polynomials in one product of unknowns (x²y² − 1). Every product is multiplied out again and compared before it is shown.
 */
internal object Factor {
    /** The largest whole number taken apart into primes: trial division up to its square root stays quick. */
    val MOST_PRIME: BigInteger = BigInteger.TEN.pow(12)

    /** The most candidate roots or quadratic factors tried before Calculate stops looking. */
    private const val MOST_TRIALS = 200_000L

    private val TWO = BigInteger.valueOf(2)

    /** Where one unknown stands for a product of others while a polynomial in it is factored; never a name a formula has. */
    private const val T = "\u0000t"

    fun of(p: Polynomial): Product {
        if (p.isConstant) return Product(p.constant, emptyList(), true)
        // The number in common, with the sign that leaves the first term positive.
        val positive = content(p)
        val constant = if (p.leading.second.signum < 0) -positive else positive
        var q = p * (Rational.ONE / constant)
        val factors = mutableListOf<Pair<Polynomial, Int>>()
        // The unknowns in common.
        val common = q.terms.keys.reduce { a, b -> a.gcd(b) }
        for ((v, k) in common.powers) factors += Polynomial.variable(v) to k
        if (common != Monomial.ONE) q = q.divideExactly(Polynomial(mapOf(common to Rational.ONE))) ?: return unsplit(p)
        val (rest, complete) = split(q)
        factors += merge(rest).sortedWith(compareBy<Pair<Polynomial, Int>>({ it.first.degree }, { rootSize(it.first) },
            { rootNegative(it.first) }, { it.first.size }, { it.first.toString() }))
        val product = Product(constant, factors, complete)
        return if (product.expanded() == p) product else unsplit(p)
    }

    /** What is shown if a product did not multiply out to its polynomial: the polynomial itself, nothing claimed. */
    private fun unsplit(p: Polynomial) = Product(Rational.ONE, listOf(p to 1), complete = false)

    /** The primes of [n] (2 or more) with their powers, smallest first; null beyond [MOST_PRIME]. */
    fun primes(n: BigInteger): List<Pair<BigInteger, Int>>? {
        if (n < TWO || n > MOST_PRIME) return null
        var m = n.toLong()
        val out = mutableListOf<Pair<BigInteger, Int>>()
        var p = 2L
        while (p * p <= m) {
            var k = 0
            while (m % p == 0L) {
                m /= p
                k++
            }
            if (k > 0) out += BigInteger.valueOf(p) to k
            p += if (p == 2L) 1 else 2
        }
        if (m > 1) out += BigInteger.valueOf(m) to 1
        return out
    }

    /** The positive divisors of [n] (1 or more), or null where it is too large to take apart. */
    private fun divisors(n: BigInteger): List<BigInteger>? {
        if (n == BigInteger.ONE) return listOf(BigInteger.ONE)
        val primes = primes(n) ?: return null
        var out = listOf(BigInteger.ONE)
        for ((p, k) in primes) out = out.flatMap { d -> (0..k).map { d * p.pow(it) } }
        return out.sorted()
    }

    /** The positive number in common: the coefficients' numerators' gcd over their denominators' lcm. */
    private fun content(p: Polynomial): Rational {
        var num = BigInteger.ZERO
        var den = BigInteger.ONE
        for (c in p.terms.values) {
            num = num.gcd(c.num)
            den = den / den.gcd(c.den) * c.den
        }
        return Rational.of(num, den)
    }

    /**
     * Takes apart [q]: integer coefficients without a number or an unknown in common, its first term positive. The factors
     * have positive first terms, so their product is [q] itself; true where none can be taken apart further.
     */
    private fun split(q: Polynomial): Pair<List<Pair<Polynomial, Int>>, Boolean> {
        if (q.isConstant) return emptyList<Pair<Polynomial, Int>>() to true
        if (q.degree == 1) return listOf(q to 1) to true
        val unknowns = q.unknowns.toList()
        if (unknowns.size == 1) return univariate(q, unknowns[0])
        if (unknowns.size == 2 && q.homogeneous) return binaryForm(q, unknowns[0], unknowns[1])
        grouped(q)?.let { return it }
        collinear(q)?.let { return it }
        return listOf(q to 1) to linearAndPrime(q)
    }

    /** Equal factors as one, their powers added. */
    private fun merge(parts: List<Pair<Polynomial, Int>>): List<Pair<Polynomial, Int>> {
        val out = LinkedHashMap<Polynomial, Int>()
        for ((f, k) in parts) out[f] = (out[f] ?: 0) + k
        return out.map { it.key to it.value }
    }

    /** For a factor of two terms of degree one, as x − 3 or 2x + 3y, the size of the number that makes it 0: smaller first. */
    private fun rootSize(f: Polynomial): Double = linearRoot(f)?.let { abs(it.toDouble()) } ?: Double.MAX_VALUE

    /** x − 1 before x + 1. */
    private fun rootNegative(f: Polynomial): Boolean = (linearRoot(f)?.signum ?: 0) < 0

    private fun linearRoot(f: Polynomial): Rational? {
        if (f.degree != 1 || f.size != 2) return null
        val (lead, other) = f.sorted
        return -other.second / lead.second
    }

    /** Linear in some unknown, with nothing in common between that unknown's coefficient and the rest: it has no factors. */
    private fun linearAndPrime(q: Polynomial): Boolean = q.unknowns.any { v ->
        val c = q.coefficientsIn(v)
        c.keys == setOf(0, 1) && of(c.values.minBy { it.size }).complete
    }

    /** A factor every coefficient of [q] has in common as a polynomial in one of its unknowns: ax + ay + bx + by = (a + b)(x + y). */
    private fun grouped(q: Polynomial): Pair<List<Pair<Polynomial, Int>>, Boolean>? {
        for (v in q.unknowns) {
            val coefficients = q.coefficientsIn(v).values
            if (coefficients.size < 2) continue
            // A factor of the shortest coefficient (in fewer unknowns, so this ends) divides all of them where it divides q.
            val candidates = of(coefficients.minBy { it.size })
            val found = mutableListOf<Pair<Polynomial, Int>>()
            var rest = q
            for ((f, k) in candidates.factors) {
                if (f.isConstant) continue
                repeat(k) {
                    val divided = rest.divideExactly(f) ?: return@repeat
                    rest = divided
                    found += f to 1
                }
            }
            if (found.isEmpty()) continue
            val (more, complete) = split(normalized(rest))
            return found + more to (candidates.complete && complete)
        }
        return null
    }

    /** [q] with integer coefficients and its first term positive, as [split] takes it. */
    private fun normalized(q: Polynomial): Polynomial {
        val c = content(q)
        return q * (Rational.ONE / if (q.leading.second.signum < 0) -c else c)
    }

    /** A polynomial in one product of unknowns, as x²y² − 1 is t² − 1 with t = xy: factored in t, then t put back. */
    private fun collinear(q: Polynomial): Pair<List<Pair<Polynomial, Int>>, Boolean>? {
        val names = q.unknowns.toList()
        val vectors = q.terms.keys.map { m -> names.map { m.power(it) } }
        val first = vectors.first { v -> v.any { it > 0 } }
        val g = first.fold(0) { a, b -> BigInteger.valueOf(a.toLong()).gcd(BigInteger.valueOf(b.toLong())).toInt() }
        val u = first.map { it / g }
        val at = u.indexOfFirst { it > 0 }
        val inT = HashMap<Monomial, Rational>()
        for ((m, c) in q.terms) {
            val v = names.map { m.power(it) }
            if (v[at] % u[at] != 0) return null
            val k = v[at] / u[at]
            if (v.indices.any { v[it] != k * u[it] }) return null
            inT[if (k == 0) Monomial.ONE else Monomial(listOf(T to k))] = c
        }
        val t = Monomial(names.zip(u).filter { it.second > 0 })
        val (parts, complete) = univariate(Polynomial(inT), T)
        val back = parts.map { (h, k) ->
            Polynomial(h.terms.mapKeys { (m, _) -> Monomial(t.powers.map { (v, e) -> v to e * m.power(T) }.filter { it.second > 0 }) }) to k
        }
        return back to (complete && back.all { (f, _) -> f.degree == 1 || linearAndPrime(f) })
    }

    /** A form in [x] and [y] of one degree: factored as a polynomial in [x] with y = 1, then each factor made whole again. */
    private fun binaryForm(q: Polynomial, x: String, y: String): Pair<List<Pair<Polynomial, Int>>, Boolean> {
        val inX = Polynomial(q.terms.mapKeys { (m, _) -> Monomial(m.powers.filter { it.first == x }) })
        val (parts, complete) = univariate(inX, x)
        return parts.map { (h, k) ->
            val d = h.degree
            Polynomial(h.terms.mapKeys { (m, _) ->
                Monomial(listOfNotNull(m.power(x).takeIf { it > 0 }?.let { x to it }, (d - m.power(x)).takeIf { it > 0 }?.let { y to it }))
            }) to k
        } to complete
    }

    /** [q] in the one unknown [x]: its square-free parts, then rational roots, then quadratic factors. */
    private fun univariate(q: Polynomial, x: String): Pair<List<Pair<Polynomial, Int>>, Boolean> {
        val n = q.degreeIn(x)
        val coefficients = List(n + 1) { k -> q.terms[if (k == 0) Monomial.ONE else Monomial(listOf(x to k))] ?: Rational.ZERO }
        val out = mutableListOf<Pair<Polynomial, Int>>()
        var complete = true
        for ((part, times) in squareFree(coefficients)) {
            val (factors, done) = splitSquareFree(primitive(part))
            complete = complete && done
            for (f in factors) out += Polynomial(f.withIndex().filter { it.value.signum() != 0 }.associate { (k, c) ->
                (if (k == 0) Monomial.ONE else Monomial(listOf(x to k))) to Rational.of(c)
            }) to times
        }
        return out to complete
    }

    /** Integer coefficients without a common factor, the leading one positive: the same polynomial up to a number. */
    private fun primitive(p: List<Rational>): List<BigInteger> {
        var den = BigInteger.ONE
        for (c in p) den = den / den.gcd(c.den) * c.den
        val whole = p.map { (it * Rational.of(den)).num }
        var g = BigInteger.ZERO
        for (c in whole) g = g.gcd(c)
        val sign = if (whole.last().signum() < 0) BigInteger.ONE.negate() else BigInteger.ONE
        return whole.map { it / g * sign }
    }

    /** A square-free polynomial with integer coefficients and f(0) ≠ 0 as factors that have none; true where that is certain. */
    private fun splitSquareFree(f0: List<BigInteger>): Pair<List<List<BigInteger>>, Boolean> {
        if (f0.size <= 2) return listOf(f0) to true
        val out = mutableListOf<List<BigInteger>>()
        var f = f0
        val roots = rationalRootCandidates(f) ?: return listOf(f) to false
        for ((p, q) in roots) {
            if (f.size <= 2) break
            if (!isRoot(f, p, q)) continue
            val linear = listOf(p.negate(), q)
            out += linear
            f = divideInteger(f, linear) ?: return out + listOf(f) to false
        }
        var complete = true
        while (f.size - 1 >= 4) {
            val (g, searched) = quadraticFactor(f)
            if (g == null) {
                // Without a factor of degree one or two, one of degree four or five has none.
                complete = searched && f.size - 1 <= 5
                break
            }
            out += g
            f = divideInteger(f, g) ?: return out + listOf(f) to false
        }
        if (f.size > 1) out += listOf(f)
        return out to complete
    }

    /** Every p/q with p dividing the constant term and q the leading one, in lowest terms; null where there are too many. */
    private fun rationalRootCandidates(f: List<BigInteger>): List<Pair<BigInteger, BigInteger>>? {
        val ps = divisors(f.first().abs()) ?: return null
        val qs = divisors(f.last().abs()) ?: return null
        if (ps.size.toLong() * qs.size * 2 > MOST_TRIALS) return null
        val out = mutableListOf<Pair<BigInteger, BigInteger>>()
        for (q in qs) for (p in ps) if (p.gcd(q) == BigInteger.ONE) {
            out += p to q
            out += p.negate() to q
        }
        return out.sortedWith(compareBy({ it.first.abs().toDouble() / it.second.toDouble() }, { it.first.signum() < 0 }))
    }

    /** Whether f(p/q) = 0: Σ aₖ pᵏ qⁿ⁻ᵏ = 0. */
    private fun isRoot(f: List<BigInteger>, p: BigInteger, q: BigInteger): Boolean {
        val n = f.size - 1
        var sum = BigInteger.ZERO
        for ((k, a) in f.withIndex()) sum += a * p.pow(k) * q.pow(n - k)
        return sum.signum() == 0
    }

    private fun valueAt(f: List<BigInteger>, x: Long): BigInteger {
        var sum = BigInteger.ZERO
        val b = BigInteger.valueOf(x)
        for (a in f.asReversed()) sum = sum * b + a
        return sum
    }

    /**
     * A factor a x² + b x + c of [f] (which has no rational root), searched among a dividing its leading coefficient, c its
     * constant and |b| ≤ 2aR for R the bound on its roots; and whether the search went through all of them.
     */
    private fun quadraticFactor(f: List<BigInteger>): Pair<List<BigInteger>?, Boolean> {
        val leads = divisors(f.last().abs()) ?: return null to false
        val constants = divisors(f.first().abs()) ?: return null to false
        val lead = f.last().abs().toDouble()
        val bound = 1 + f.dropLast(1).maxOf { it.abs().toDouble() } / lead
        if (!bound.isFinite()) return null to false
        // g(k) divides f(k) for every whole k; f has no rational root, so neither is 0.
        val at = listOf(1L, -1L, 2L).map { it to valueAt(f, it) }
        var trials = 0.0
        for (a in leads) {
            val span = ceil(2 * a.toDouble() * bound)
            for (cAbs in constants) for (c in listOf(cAbs, cAbs.negate())) {
                trials += 2 * span + 1
                if (trials > MOST_TRIALS) return null to false
                val bMax = span.toLong()
                for (bl in -bMax..bMax) {
                    val b = BigInteger.valueOf(bl)
                    val g = listOf(c, b, a)
                    if (at.any { (k, fk) -> valueAt(g, k).let { gk -> gk.signum() == 0 || fk.mod(gk.abs()).signum() != 0 } }) continue
                    if (c.gcd(b).gcd(a) != BigInteger.ONE) continue
                    if (divideInteger(f, g) != null) return g to true
                }
            }
        }
        return null to true
    }

    /** [f] divided by [g] where the quotient has integer coefficients and nothing is left over, otherwise null. */
    private fun divideInteger(f: List<BigInteger>, g: List<BigInteger>): List<BigInteger>? {
        val (q, r) = divide(f.map { Rational.of(it) }, g.map { Rational.of(it) })
        if (r.isNotEmpty() || q.any { !it.isInteger }) return null
        return q.map { it.num }
    }

    // Polynomials in one unknown over the rationals, as coefficient lists lowest degree first, without trailing zeros.

    private fun trim(p: List<Rational>) = p.dropLastWhile { it.signum == 0 }

    private fun sub(a: List<Rational>, b: List<Rational>) =
        trim(List(maxOf(a.size, b.size)) { (a.getOrElse(it) { Rational.ZERO }) - (b.getOrElse(it) { Rational.ZERO }) })

    private fun derivative(p: List<Rational>) = trim(p.drop(1).mapIndexed { i, c -> c * Rational.of(i + 1L) })

    private fun divide(a: List<Rational>, b: List<Rational>): Pair<List<Rational>, List<Rational>> {
        val d = trim(b)
        var r = trim(a).toMutableList()
        if (r.size < d.size) return emptyList<Rational>() to r
        val q = MutableList(r.size - d.size + 1) { Rational.ZERO }
        while (r.size >= d.size && r.isNotEmpty()) {
            val shift = r.size - d.size
            val k = r.last() / d.last()
            q[shift] = k
            for ((i, c) in d.withIndex()) r[i + shift] = r[i + shift] - c * k
            r = trim(r).toMutableList()
        }
        return trim(q) to r
    }

    private fun gcd(a: List<Rational>, b: List<Rational>): List<Rational> {
        var x = trim(a)
        var y = trim(b)
        while (y.isNotEmpty()) {
            val r = divide(x, y).second
            x = y
            y = r
        }
        return if (x.isEmpty()) x else x.map { it / x.last() }
    }

    /** Yun's square-free decomposition: f = a₁ a₂² a₃³ …, each aᵢ without repeated factors, returned with its power i. */
    private fun squareFree(f: List<Rational>): List<Pair<List<Rational>, Int>> {
        val out = mutableListOf<Pair<List<Rational>, Int>>()
        val d = derivative(f)
        val a0 = gcd(f, d)
        var b = divide(f, a0).first
        var c = divide(d, a0).first
        var e = sub(c, derivative(b))
        var i = 1
        while (b.size > 1) {
            val a = gcd(b, e)
            if (a.size > 1) out += a to i
            b = divide(b, a).first
            c = divide(e, a).first
            e = sub(c, derivative(b))
            i++
        }
        return out
    }
}
