package com.professoreuler.mathcalc

/** A product of unknowns and their powers, by name in alphabetical order: x²y is [(x, 2), (y, 1)]; none is the number 1. */
internal data class Monomial(val powers: List<Pair<String, Int>>) {
    val degree: Int get() = powers.sumOf { it.second }

    fun power(v: String): Int = powers.firstOrNull { it.first == v }?.second ?: 0

    operator fun times(o: Monomial): Monomial {
        val sum = sortedMapOf<String, Int>()
        for ((v, k) in powers + o.powers) sum[v] = (sum[v] ?: 0) + k
        return Monomial(sum.map { it.key to it.value })
    }

    /** This divided by [o], or null where [o] does not divide it. */
    fun div(o: Monomial): Monomial? {
        if (o.powers.any { (v, k) -> power(v) < k }) return null
        return Monomial(powers.mapNotNull { (v, k) -> (k - o.power(v)).takeIf { it > 0 }?.let { v to it } })
    }

    /** What this and [o] have in common: each unknown to the lower of its two powers. */
    fun gcd(o: Monomial): Monomial = Monomial(powers.mapNotNull { (v, k) -> minOf(k, o.power(v)).takeIf { it > 0 }?.let { v to it } })

    companion object {
        val ONE = Monomial(emptyList())

        fun of(v: String) = Monomial(listOf(v to 1))

        /** Higher degree first; within a degree, the higher power of the alphabetically first unknown: x² + xy + y² + x + 1. */
        val ORDER: Comparator<Monomial> = Comparator { a, b ->
            if (a.degree != b.degree) return@Comparator b.degree.compareTo(a.degree)
            for (v in (a.powers.map { it.first } + b.powers.map { it.first }).toSortedSet()) {
                val d = b.power(v).compareTo(a.power(v))
                if (d != 0) return@Comparator d
            }
            0
        }
    }
}

/**
 * A polynomial in the unknowns with exact coefficients, multiplied out and its like terms collected: (x + 2)² is
 * x² + 4x + 4, 5x + 8x + 7y + 2y is 13x + 9y. The apps write it with [Calculation.latex] and [Calculation.text].
 */
class Polynomial internal constructor(terms: Map<Monomial, Rational>) {
    internal val terms: Map<Monomial, Rational> = terms.filterValues { it.signum != 0 }

    /** The terms in the order they are written: highest degree first. */
    internal val sorted: List<Pair<Monomial, Rational>> by lazy {
        this.terms.entries.sortedWith(compareBy(Monomial.ORDER) { it.key }).map { it.key to it.value }
    }

    val isZero: Boolean get() = terms.isEmpty()

    /** The unknowns it has, in alphabetical order. */
    val unknowns: Set<String> get() = terms.keys.flatMapTo(sortedSetOf()) { m -> m.powers.map { it.first } }

    internal val isConstant: Boolean get() = terms.keys.all { it == Monomial.ONE }
    internal val constant: Rational get() = terms[Monomial.ONE] ?: Rational.ZERO
    internal val size: Int get() = terms.size

    /** The highest degree of a term; -1 for 0. */
    internal val degree: Int get() = terms.keys.maxOfOrNull { it.degree } ?: -1

    internal val leading: Pair<Monomial, Rational> get() = sorted.first()

    internal fun degreeIn(v: String): Int = terms.keys.maxOfOrNull { it.power(v) } ?: -1

    /** Every term of the same degree. */
    internal val homogeneous: Boolean get() = terms.keys.map { it.degree }.distinct().size == 1

    /** This as a polynomial in [v]: the coefficient of each power of [v], a polynomial in the other unknowns. */
    internal fun coefficientsIn(v: String): Map<Int, Polynomial> = terms.entries
        .groupBy({ it.key.power(v) }, { (m, c) -> Monomial(m.powers.filter { it.first != v }) to c })
        .mapValues { (_, list) -> Polynomial(list.toMap()) }

    internal operator fun plus(o: Polynomial): Polynomial {
        val out = HashMap(terms)
        for ((m, c) in o.terms) out[m] = (out[m] ?: Rational.ZERO) + c
        return Polynomial(out)
    }

    internal operator fun unaryMinus() = Polynomial(terms.mapValues { -it.value })

    internal operator fun minus(o: Polynomial) = this + -o

    internal operator fun times(r: Rational) = Polynomial(terms.mapValues { it.value * r })

    internal operator fun times(o: Polynomial): Polynomial {
        Budget.check()
        if (terms.size.toLong() * o.terms.size > MOST_PRODUCTS) throw CalcException(Failure.TOO_LARGE)
        val out = HashMap<Monomial, Rational>()
        for ((a, x) in terms) {
            // The time looked at once a term: one product of two long polynomials is much work, and the clock is read only
            // every 256 checks (a line integral ran 0.9 s past its 3 s in products and exact divisions).
            Budget.check()
            for ((b, y) in o.terms) {
                val m = a * b
                out[m] = (out[m] ?: Rational.ZERO) + x * y
            }
        }
        return Polynomial(out).also { if (it.terms.size > MOST_TERMS) throw CalcException(Failure.TOO_LARGE) }
    }

    internal fun pow(n: Int): Polynomial {
        if (terms.size <= 1) {
            val (m, c) = terms.entries.firstOrNull()?.toPair() ?: return if (n == 0) ONE else this
            val power = c.pow(n) ?: throw CalcException(Failure.TOO_LARGE)
            return Polynomial(mapOf(Monomial(m.powers.map { (v, k) -> v to k * n }) to power))
        }
        if (n > MOST_POWER) throw CalcException(Failure.TOO_LARGE)
        var out = ONE
        repeat(n) { out *= this }
        return out
    }

    /** This divided by [d] where [d] divides it exactly (x² − 1 by x − 1 is x + 1), otherwise null. */
    internal fun divideExactly(d: Polynomial): Polynomial? {
        if (d.isZero) throw CalcException(Failure.DIVISION_BY_ZERO)
        if (d.isConstant) return this * (Rational.ONE / d.constant)
        val (lm, lc) = d.leading
        var rest = this
        val quotient = HashMap<Monomial, Rational>()
        var steps = 0
        // The leading term of what is left must be a multiple of the divisor's, or the divisor does not divide it.
        while (!rest.isZero) {
            Budget.check()
            if (++steps > MOST_TERMS) return null
            val (m, c) = rest.leading
            val q = m.div(lm) ?: return null
            val k = c / lc
            quotient[q] = k
            rest -= d * Polynomial(mapOf(q to k))
        }
        return Polynomial(quotient)
    }

    override fun equals(other: Any?) = other is Polynomial && terms == other.terms
    override fun hashCode() = terms.hashCode()
    override fun toString() = sorted.joinToString(" + ") { (m, c) -> "$c" + m.powers.joinToString("") { (v, k) -> if (k == 1) v else "$v^$k" } }

    internal companion object {
        /** Beyond these a polynomial is too large to show, and Calculate says so. */
        const val MOST_TERMS = 1000
        const val MOST_PRODUCTS = 250_000L
        const val MOST_POWER = 64

        val ONE = constant(Rational.ONE)

        fun constant(r: Rational) = Polynomial(mapOf(Monomial.ONE to r))

        fun variable(v: String) = Polynomial(mapOf(Monomial.of(v) to Rational.ONE))
    }
}
