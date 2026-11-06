package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max

/**
 * The mathematics of machine learning a learner works out by hand. Functions of a number, worked out where they are written as
 * the formulas that define them, so derivatives, graphs and exact values come as for any formula: the logistic `sigmoid`
 * 1/(1 + e^(−x)), `relu` max(0, x) = (x + |x|)/2, `leakyrelu`, `softplus` ln(1 + eˣ) and `gelu` x·Φ(x); applied to a vector or
 * matrix, entry by entry. Functions of vectors (typed [1, 2, 3]): `softmax`, `logsumexp` (written stably, the largest entry taken
 * out), `cosinesimilarity`. The information measures of probability vectors (entries ≥ 0 that add up to 1; a matrix is a joint
 * distribution): `entropy`, `crossentropy`, `kldivergence` and `mutualinformation`, in nats (the natural logarithm), or to the base
 * given last (2: bits). Gradient descent as an iteration table ([descent]), and finite Markov decision processes ([Decisions]).
 *
 * Every value is exact where its entries are and checked by a second computation before it is shown: softmax's entries add up to
 * 1, logsumexp is ln Σ eˣ in ball arithmetic, the cosine similarity is the law of cosines, D(p‖q) = H(p, q) − H(p) and
 * I(X; Y) = H(X) + H(Y) − H(X, Y), each to 130 bits ([Precise.nearZero]); what fails its check is not shown.
 */
object Learning {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** The functions of numbers and vectors, by their English names, and how many arguments each takes. */
    val FUNCTIONS: Map<String, IntRange> = mapOf(
        "sigmoid" to 1..1, "relu" to 1..1, "leakyrelu" to 1..2, "softplus" to 1..1, "gelu" to 1..1,
        "softmax" to 1..1, "logsumexp" to 1..1, "cosinesimilarity" to 2..2,
        "entropy" to 1..2, "crossentropy" to 2..3, "kldivergence" to 2..3, "mutualinformation" to 1..2,
    )

    /** The commands, a line of their own each ([CommandFunctions]): their names and modes. */
    internal val COMMANDS: Map<String, Mode> = mapOf(
        "gradientdescent" to Mode.GRADIENT_DESCENT, "valueiteration" to Mode.VALUE_ITERATION, "policyevaluation" to Mode.POLICY_EVALUATION,
    )

    /** Whether a side as the parser wrote it ([Statement.texts]) is a call of one of [FUNCTIONS]: softmax(…) is a result, not data. */
    internal fun isCall(text: String): Boolean = text.trim().removePrefix("\\").let { t -> FUNCTIONS.keys.any { t.startsWith(it + "(") } }

    /** Leaky ReLU's slope for x < 0 where none is given, as PyTorch and Keras have it: 0.01. */
    private val LEAK: Expr = num(Rational.of(1) / Rational.of(100))

    /** Beyond this the exponential of a number is written the other way round, so no double overflows: e^(−x) for x > 30. */
    private const val LARGE = 30.0

    // ---------------------------------------------------------------------------------------------------------------------
    // Functions of numbers and vectors, worked out where they are written.

    /**
     * [name] at [args] (each already worked out): its value as an exact expression, or a failure: [Failure.ARGUMENTS] for a count
     * or a kind of argument it does not take, [Failure.NOT_DISTRIBUTION] for a probability vector that is none,
     * [Failure.DIVERGES] for a cross-entropy or divergence that is +∞ (q is 0 where p is not).
     */
    fun call(name: String, args: List<Expr>): Expr {
        val arity = FUNCTIONS[name] ?: throw CalcException(Failure.UNKNOWN_FUNCTION)
        if (args.size !in arity) throw CalcException(Failure.ARGUMENTS)
        Budget.check()
        return when (name) {
            "sigmoid" -> elementwise(args[0], ::sigmoid)
            "relu" -> elementwise(args[0], ::relu)
            "leakyrelu" -> {
                val a = args.getOrElse(1) { LEAK }
                if (a is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
                elementwise(args[0]) { leaky(it, a) }
            }
            "softplus" -> elementwise(args[0], ::softplus)
            "gelu" -> elementwise(args[0], ::gelu)
            "softmax" -> softmax(args[0])
            "logsumexp" -> logSumExp(entries(args[0]))
            "cosinesimilarity" -> cosine(entries(args[0]), entries(args[1]))
            "entropy" -> entropy(distribution(args[0]), base(args.getOrNull(1)))
            "crossentropy" -> crossEntropy(distribution(args[0]), distribution(args[1]), base(args.getOrNull(2)))
            "kldivergence" -> divergence(distribution(args[0]), distribution(args[1]), base(args.getOrNull(2)))
            "mutualinformation" -> mutualInformation(args[0], base(args.getOrNull(1)))
            else -> throw CalcException(Failure.UNKNOWN_FUNCTION)
        }
    }

    /** f of a number, or of each entry of a vector or matrix (as the activation functions of a network are applied). */
    private fun elementwise(x: Expr, f: (Expr) -> Expr): Expr =
        if (x is Expr.Mat) LinAlg.matrix(x.rows.map { r -> r.map { f(it) } }) else f(x)

    /** A real number's value, where [e] is one (no letters, no i). */
    private fun real(e: Expr): Double? = if (e.symbols().isEmpty() && !e.hasI()) Numeric.value(e) else null

    /**
     * The sign of a real constant, certified: exactly for a rational, otherwise where its ball of 130 bits lies on one side of 0
     * (0 where it is 0 to that precision); null where it has letters or cannot be told.
     */
    internal fun sign(e: Expr): Int? {
        rational(e)?.let { return it.signum }
        if (e.symbols().isNotEmpty() || e.hasI() || e.hasInfinity() || e is Expr.Mat) return null
        val v = Numeric.value(e) ?: return null
        if (abs(v) > 1e-6) return if (v > 0) 1 else -1
        return when (Precise.nearZero(e, complex = false)) {
            true -> 0
            else -> try {
                val ball = Precise.eval(e, 256, false, emptyMap()).re
                when {
                    ball.isPositive -> 1
                    ball.isNegative -> -1
                    else -> null
                }
            } catch (x: CalcException) {
                null
            } catch (x: Imprecise) {
                null
            }
        }
    }

    /** σ(x) = 1/(1 + e^(−x)), written eˣ/(1 + eˣ), whose derivative comes out as σ(1 − σ) = eˣ/(1 + eˣ)². */
    private fun sigmoid(x: Expr): Expr {
        val v = real(x)
        return if (v != null && v > LARGE) tidy(pow(add(Expr.ONE, exp(neg(x))), Expr.MINUS_ONE))
        else tidy(mul(exp(x), pow(add(Expr.ONE, exp(x)), Expr.MINUS_ONE)))
    }

    /** max(0, x) = (x + |x|)/2: of a number its value, of a formula the formula (its derivative 1 for x > 0, 0 for x < 0). */
    private fun relu(x: Expr): Expr = when (sign(x)) {
        1, 0 -> x
        -1 -> Expr.ZERO
        else -> tidy(mul(Expr.HALF, add(x, fn(Func.ABS, x))))
    }

    /** x for x ≥ 0, a·x for x < 0: ((1 + a)x + (1 − a)|x|)/2. */
    private fun leaky(x: Expr, a: Expr): Expr = when (sign(x)) {
        1, 0 -> x
        -1 -> tidy(mul(a, x))
        else -> tidy(mul(Expr.HALF, add(mul(add(Expr.ONE, a), x), mul(add(Expr.ONE, neg(a)), fn(Func.ABS, x)))))
    }

    /** ln(1 + eˣ), for a large number x as x + ln(1 + e^(−x)), the same value without a huge eˣ. */
    private fun softplus(x: Expr): Expr {
        val v = real(x)
        return if (v != null && v > LARGE) tidy(add(x, ln(add(Expr.ONE, exp(neg(x))))))
        else tidy(ln(add(Expr.ONE, exp(x))))
    }

    /** x·Φ(x) = (x/2)(1 + erf(x/√2)), Φ the standard normal distribution function. */
    private fun gelu(x: Expr): Expr = tidy(mul(Expr.HALF, x, add(Expr.ONE, fn(Func.ERF, mul(x, pow(Expr.TWO, neg(Expr.HALF)))))))

    /** The entries of a vector, a column or a row of at least two. */
    private fun entries(v: Expr): List<Expr> {
        if (v !is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        return when {
            v.columns == 1 && v.size >= 2 -> v.rows.map { it[0] }
            v.size == 1 && v.columns >= 2 -> v.rows[0]
            else -> throw CalcException(Failure.ARGUMENTS)
        }
    }

    /**
     * softmax(z)ᵢ = e^(zᵢ)/Σⱼ e^(zⱼ), written 1/Σⱼ e^(zⱼ − zᵢ) (no exponential grows past the largest ratio); of a vector as a
     * whole, of a matrix row by row (as attention's weights). Checked: each row adds up to 1, and every entry of numbers is > 0.
     */
    private fun softmax(z: Expr): Expr {
        if (z !is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        fun of(row: List<Expr>): List<Expr> {
            val out = row.map { zi -> tidy(pow(add(row.map { zj -> exp(tidy(add(zj, neg(zi)))) }), Expr.MINUS_ONE)) }
            if (!isOne(add(out))) throw CalcException(Failure.UNSOLVED)
            if (out.any { e -> e.symbols().isEmpty() && !e.hasI() && sign(e) != 1 }) throw CalcException(Failure.UNSOLVED)
            return out
        }
        return when {
            z.columns == 1 && z.size >= 2 -> LinAlg.column(of(z.rows.map { it[0] }))
            z.size == 1 && z.columns >= 2 -> LinAlg.matrix(listOf(of(z.rows[0])))
            z.size >= 2 && z.columns >= 2 -> LinAlg.matrix(z.rows.map { of(it) })
            else -> throw CalcException(Failure.ARGUMENTS)
        }
    }

    /** Whether [e] is 1: exactly, to 130 bits for numbers, or at every value of its letters. */
    private fun isOne(e: Expr): Boolean {
        val d = tidy(add(e, Expr.MINUS_ONE))
        if (isZero(d)) return true
        if (d.symbols().isNotEmpty()) return Verify.same(e, Expr.ONE)
        return Precise.nearZero(d, complex = d.hasI()) == true
    }

    /** Whether the constants [a] and [b] are equal to 130 bits (or, with letters, at every value of them). */
    private fun same(a: Expr, b: Expr): Boolean {
        val d = tidy(add(a, neg(b)))
        if (isZero(d)) return true
        if (d.symbols().isNotEmpty()) return Verify.same(a, b)
        return Precise.nearZero(d, complex = d.hasI()) == true
    }

    /**
     * ln Σ e^(zᵢ), written m + ln Σ e^(zᵢ − m) with m the largest entry: the same number, and no exponential larger than 1 to
     * work out. Checked against ln Σ e^(zᵢ) in ball arithmetic, and m ≤ LSE ≤ m + ln n.
     */
    private fun logSumExp(z: List<Expr>): Expr {
        val values = z.map { real(it) }
        if (values.any { it == null }) return tidy(ln(add(z.map { exp(it) })))
        val m = z[values.indices.maxByOrNull { values[it]!! }!!]
        val lse = tidy(add(m, ln(add(z.map { exp(tidy(add(it, neg(m)))) }))))
        if (!same(lse, ln(add(z.map { exp(it) })))) throw CalcException(Failure.UNSOLVED)
        val v = Numeric.value(lse) ?: throw CalcException(Failure.UNSOLVED)
        val top = values.maxOf { it!! }
        if (v < top - 1e-9 * max(1.0, abs(top)) || v > top + kotlin.math.ln(z.size.toDouble()) + 1e-9 * max(1.0, abs(top))) throw CalcException(Failure.UNSOLVED)
        return lse
    }

    /**
     * cos θ = u·v/(|u||v|), checked against the law of cosines (|u|² + |v|² − |u − v|²)/(2|u||v|); a zero vector has no
     * direction ([Failure.DIVISION_BY_ZERO]).
     */
    private fun cosine(u: List<Expr>, v: List<Expr>): Expr {
        if (u.size != v.size) throw CalcException(Failure.SIZES)
        fun dot(a: List<Expr>, b: List<Expr>) = tidy(add(a.indices.map { mul(a[it], b[it]) }))
        val uu = dot(u, u)
        val vv = dot(v, v)
        if (sign(uu) == 0 || sign(vv) == 0) throw CalcException(Failure.DIVISION_BY_ZERO)
        val c = tidy(mul(dot(u, v), pow(mul(uu, vv), neg(Expr.HALF))))
        val d = u.indices.map { tidy(add(u[it], neg(v[it]))) }
        val law = mul(add(uu, vv, neg(dot(d, d))), pow(mul(Expr.TWO, sqrt(uu), sqrt(vv)), Expr.MINUS_ONE))
        if (!same(c, law)) throw CalcException(Failure.UNSOLVED)
        real(c)?.let { if (abs(it) > 1 + 1e-12) throw CalcException(Failure.UNSOLVED) }
        return c
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Information measures.

    /**
     * The entries of a probability vector or of a joint distribution (a matrix), row by row: numbers ≥ 0 adding up to 1, each
     * checked exactly (or to 130 bits); entries in letters only where they add up to 1 at every value of them ([p, 1 − p]).
     */
    private fun distribution(p: Expr): List<Expr> {
        if (p !is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val all = p.rows.flatten()
        if (all.any { it is Expr.Mat || it.hasInfinity() || it.hasI() }) throw CalcException(Failure.ARGUMENTS)
        if (all.all { it.symbols().isEmpty() }) {
            if (all.any { (sign(it) ?: -1) < 0 }) throw CalcException(Failure.NOT_DISTRIBUTION)
        }
        if (!isOne(add(all))) throw CalcException(Failure.NOT_DISTRIBUTION)
        return all
    }

    /** A logarithm's base: e where none is given, else a number > 0 other than 1. */
    private fun base(b: Expr?): Expr? {
        if (b == null || b == Expr.E) return null
        if (b is Expr.Mat || b.symbols().isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        if (sign(b) != 1 || same(b, Expr.ONE)) throw CalcException(Failure.UNDEFINED)
        return b
    }

    /** log to [b] (ln where it is null) of a constant. */
    private fun log(y: Expr, b: Expr?): Expr = if (b == null) ln(y) else fn(Func.LOG, b, y)

    /**
     * log_b of a positive constant, simplified as far as the engine goes: of a fraction, the logarithms of its primes collected
     * (log₂(3/2) = log₂ 3 − 1), and of anything else as it is.
     */
    private fun logOf(y: Expr, b: Expr?): Expr {
        val r = rational(y) ?: return tidy(log(y, b))
        fun of(n: BigInteger): Expr {
            if (n == BigInteger.ONE) return Expr.ZERO
            val primes = if (n.bitLength() <= 40) Factor.primes(n) else null
            return primes?.let { ps -> add(ps.map { (q, k) -> mul(num(k.toLong()), log(num(Rational.of(q)), b)) }) } ?: log(num(Rational.of(n)), b)
        }
        return tidy(add(of(r.num), neg(of(r.den))))
    }

    /**
     * Σ cᵢ·log_b(yᵢ) (cᵢ ≠ 0) written two ways, the shorter shown: with the terms of equal yᵢ collected and each log as it is
     * (log₁₀ 10 = 1), or with every logarithm of a fraction split into its primes (log₂ 3 − 2/3).
     */
    private fun weightedLogs(terms: List<Pair<Expr, Expr>>, b: Expr?): Expr {
        val grouped = terms.groupBy({ it.second }, { it.first }).map { (y, cs) -> mul(tidy(add(cs)), tidy(log(y, b))) }
        val collected = tidy(add(grouped))
        val split = try { tidy(add(terms.map { (c, y) -> mul(c, logOf(y, b)) })) } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            collected
        }
        return if (split.size() < collected.size()) split else collected
    }

    /**
     * Whether [a] and [b] in letters agree at 24 random values of them, in complex arithmetic with the principal branches (so
     * log₂ p = ln p / ln 2 for every p, also where a real logarithm is not defined); at least 12 values must be defined on both.
     */
    private fun everywhere(a: Expr, b: Expr): Boolean {
        if (a == b) return true
        val letters = (a.symbols() + b.symbols()).toList()
        val random = java.util.Random(7919L + letters.hashCode())
        var compared = 0
        repeat(24) {
            val env = letters.associateWith { (random.nextDouble() * 2 - 1) * 3 + 0.0137 }
            val va = Verify.value(a, env)
            val vb = Verify.value(b, env)
            if (!va.finite || !vb.finite) return@repeat
            if (!Verify.close(va, vb)) return false
            compared++
        }
        return compared >= 12
    }

    /** Whether a distribution has letters in it: its measure is then a formula, its logarithms as they are. */
    private fun inLetters(vararg ds: List<Expr>) = ds.any { d -> d.any { it.symbols().isNotEmpty() } }

    /** The entries that are not 0 (letters kept: they are not known to be 0). */
    private fun isNought(e: Expr) = e.symbols().isEmpty() && sign(e) == 0

    /** −Σ pᵢ ln pᵢ / ln b, built on its own: the second computation every measure is checked with. */
    private fun plainEntropy(p: List<Expr>, b: Expr?): Expr =
        mul(neg(add(p.filterNot(::isNought).map { mul(it, ln(it)) })), b?.let { pow(ln(it), Expr.MINUS_ONE) } ?: Expr.ONE)

    /** −Σ pᵢ ln qᵢ / ln b, on its own. */
    private fun plainCross(p: List<Expr>, q: List<Expr>, b: Expr?): Expr =
        mul(neg(add(p.indices.filterNot { isNought(p[it]) }.map { mul(p[it], ln(q[it])) })), b?.let { pow(ln(it), Expr.MINUS_ONE) } ?: Expr.ONE)

    /** The base's logarithm as a number, to bound a measure (ln b; 1 for nats). */
    private fun lnBase(b: Expr?): Double = b?.let { Numeric.value(it)?.let { v -> kotlin.math.ln(v) } } ?: 1.0

    /** H(p) = −Σ pᵢ log pᵢ = Σ pᵢ log(1/pᵢ) (0·log 0 = 0), checked against −Σ pᵢ ln pᵢ / ln b and 0 ≤ H ≤ log n. */
    private fun entropy(p: List<Expr>, b: Expr?): Expr {
        // In letters ([p, 1 − p]): −Σ pᵢ log pᵢ as it is, on the domain where its entries are probabilities.
        if (inLetters(p)) return tidy(neg(add(p.map { mul(it, log(it, b)) }))).also { h ->
            if (!everywhere(h, plainEntropy(p, b))) throw CalcException(Failure.UNSOLVED)
        }
        val terms = p.filterNot(::isNought).map { it to tidy(pow(it, Expr.MINUS_ONE)) }
        val h = weightedLogs(terms, b)
        if (!same(h, plainEntropy(p, b))) throw CalcException(Failure.UNSOLVED)
        real(h)?.let { v ->
            val most = kotlin.math.ln(terms.size.toDouble()) / lnBase(b)
            if (v < -1e-12 || v > most + 1e-9 * max(1.0, most)) throw CalcException(Failure.UNSOLVED)
        }
        return h
    }

    /** q must not be 0 where p is not: there the measure is +∞. */
    private fun covers(p: List<Expr>, q: List<Expr>) {
        if (p.size != q.size) throw CalcException(Failure.SIZES)
        for (k in p.indices) if (!isNought(p[k]) && isNought(q[k])) throw CalcException(Failure.DIVERGES)
    }

    /** H(p, q) = −Σ pᵢ log qᵢ, checked: H(p, q) = H(p) + D(p‖q), each computed on its own, and H(p, q) ≥ H(p). */
    private fun crossEntropy(p: List<Expr>, q: List<Expr>, b: Expr?): Expr {
        covers(p, q)
        if (inLetters(p, q)) return tidy(neg(add(p.indices.map { mul(p[it], log(q[it], b)) }))).also { h ->
            if (!everywhere(h, plainCross(p, q, b))) throw CalcException(Failure.UNSOLVED)
        }
        val terms = p.indices.filterNot { isNought(p[it]) }.map { p[it] to tidy(pow(q[it], Expr.MINUS_ONE)) }
        val h = weightedLogs(terms, b)
        if (!same(h, plainCross(p, q, b))) throw CalcException(Failure.UNSOLVED)
        val d = add(p.indices.filterNot { isNought(p[it]) }.map { mul(p[it], ln(mul(p[it], pow(q[it], Expr.MINUS_ONE)))) })
        val db = mul(d, b?.let { pow(ln(it), Expr.MINUS_ONE) } ?: Expr.ONE)
        if (!same(h, add(plainEntropy(p, b), db))) throw CalcException(Failure.UNSOLVED)
        val hv = real(h)
        val pv = real(plainEntropy(p, b))
        if (hv != null && pv != null && hv < pv - 1e-9 * max(1.0, abs(pv))) throw CalcException(Failure.UNSOLVED)
        return h
    }

    /**
     * D(p‖q) = Σ pᵢ log(pᵢ/qᵢ), +∞ where q is 0 and p is not ([Failure.DIVERGES]). Checked: D = H(p, q) − H(p), each on its own;
     * D ≥ 0 (Gibbs' inequality), and D = 0 exactly where p = q.
     */
    private fun divergence(p: List<Expr>, q: List<Expr>, b: Expr?): Expr {
        covers(p, q)
        if (inLetters(p, q)) return tidy(add(p.indices.map { mul(p[it], add(log(p[it], b), neg(log(q[it], b)))) })).also { d ->
            if (!everywhere(d, add(plainCross(p, q, b), neg(plainEntropy(p, b))))) throw CalcException(Failure.UNSOLVED)
        }
        val terms = p.indices.filterNot { isNought(p[it]) }.map { p[it] to tidy(mul(p[it], pow(q[it], Expr.MINUS_ONE))) }
        val d = weightedLogs(terms, b)
        if (!same(d, add(plainCross(p, q, b), neg(plainEntropy(p, b))))) throw CalcException(Failure.UNSOLVED)
        val equal = p.indices.all { same(p[it], q[it]) }
        if (equal && !isZero(d) && !same(d, Expr.ZERO)) throw CalcException(Failure.UNSOLVED)
        real(d)?.let { if (it < -1e-12) throw CalcException(Failure.UNSOLVED) }
        return if (equal) Expr.ZERO else d
    }

    /**
     * I(X; Y) = Σ p(x, y) log(p(x, y)/(p(x)p(y))) of a joint distribution, a matrix with a row for each value of X and a column
     * for each value of Y. Checked: I = H(X) + H(Y) − H(X, Y), each on its own; I ≥ 0, and I = 0 exactly where X and Y are
     * independent (p(x, y) = p(x)p(y)).
     */
    private fun mutualInformation(joint: Expr, b: Expr?): Expr {
        if (joint !is Expr.Mat || joint.size < 2 || joint.columns < 2) throw CalcException(Failure.SIZES)
        if (joint.symbols().isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        val all = distribution(joint)
        val rows = joint.rows.map { r -> tidy(add(r)) }
        val columns = (0 until joint.columns).map { j -> tidy(add(joint.rows.map { it[j] })) }
        val terms = ArrayList<Pair<Expr, Expr>>()
        var independent = true
        for (i in joint.rows.indices) for (j in 0 until joint.columns) {
            val pij = joint.rows[i][j]
            val product = tidy(mul(rows[i], columns[j]))
            if (!same(pij, product)) independent = false
            if (isNought(pij)) continue
            terms += pij to tidy(mul(pij, pow(product, Expr.MINUS_ONE)))
        }
        val information = if (independent) Expr.ZERO else weightedLogs(terms, b)
        val check = add(plainEntropy(rows, b), plainEntropy(columns, b), neg(plainEntropy(all, b)))
        if (!same(information, check)) throw CalcException(Failure.UNSOLVED)
        real(information)?.let { if (it < -1e-12) throw CalcException(Failure.UNSOLVED) }
        return information
    }

    /**
     * The p-norm of a vector's entries, exact: Σ|xᵢ| for p = 1, √(Σ xᵢ²) for p = 2, max |xᵢ| for p = ∞, (Σ|xᵢ|ᵖ)^(1/p) for any
     * other p ≥ 1. Not a function of its own name: the Calculator's `norm` is linear algebra's (it may take this for its p).
     */
    fun pNorm(x: List<Expr>, p: Expr): Expr {
        if (x.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        if (p == Expr.INF) {
            if (x.any { it.symbols().isNotEmpty() }) throw CalcException(Failure.ARGUMENTS)
            val sizes = x.map { tidy(fn(Func.ABS, it)) }
            return sizes.maxByOrNull { Numeric.value(it) ?: throw CalcException(Failure.UNDEFINED) }!!
        }
        val pv = real(p) ?: throw CalcException(Failure.ARGUMENTS)
        if (pv < 1) throw CalcException(Failure.UNDEFINED)
        if (p == Expr.ONE) return tidy(add(x.map { fn(Func.ABS, it) }))
        if (p == Expr.TWO) return tidy(sqrt(add(x.map { pow(it, Expr.TWO) })))
        return tidy(pow(add(x.map { pow(fn(Func.ABS, it), p) }), pow(p, Expr.MINUS_ONE)))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The commands.

    /** A command's call as written: its [name], the [mode] it is, and the tokens of each argument. */
    class Command internal constructor(val name: String, val mode: Mode, internal val args: List<List<String>>)

    /**
     * The call of a command, `\operatorname{gradientdescent}\left(…\right)` as [CommandFunctions] writes it, or null where the
     * tokens are none. Its arguments are split at the commas outside every bracket and matrix.
     */
    internal fun command(tokens: List<String>): Command? {
        val t = tokens.filter { it != "\\left" && it != "\\right" }
        if (t.getOrNull(0) != "\\operatorname" || t.getOrNull(1) != "{") return null
        val close = t.indexOf("}")
        if (close < 3) return null
        val name = t.subList(2, close).joinToString("")
        val mode = COMMANDS[name] ?: return null
        if (t.getOrNull(close + 1) != "(" || t.last() != ")") return null
        val args = ArrayList<List<String>>()
        var depth = 0
        var start = close + 2
        for (k in close + 1 until t.size) {
            val s = t[k]
            when (s) {
                "(", "[", "{", "\\{", "\\begin" -> depth++
                ")", "]", "}", "\\}", "\\end" -> depth--
            }
            // \begin{pmatrix} … \end{pmatrix}: the braces of its name open and close at once.
            if (depth == 0 && k < t.size - 1) return null
            if (depth == 1 && s[0] == ',' && s != "\\,") {
                args += t.subList(start, k)
                start = k + 1
            }
        }
        args += t.subList(start, t.size - 1)
        return Command(name, mode, args)
    }

    /** A command worked out, its arguments read in the formula's style ([flags]). */
    internal fun work(c: Command, flags: Flags): Outcome {
        if (c.args.any { it.isEmpty() }) throw CalcException(Failure.ARGUMENTS)
        return when (c.name) {
            "gradientdescent" -> descent(c.args, flags)
            "valueiteration" -> Decisions.valueIteration(c.args.map { argument(it, flags) })
            "policyevaluation" -> Decisions.policyEvaluation(c.args.map { argument(it, flags) })
            else -> throw CalcException(Failure.UNREADABLE)
        }
    }

    /** One argument: one formula without a relation, worked out. */
    private fun argument(tokens: List<String>, flags: Flags): Expr {
        val s = Parser(tokens, flags).statements().singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        if (s.relations.isNotEmpty() || s.definition != null) throw CalcException(Failure.ARGUMENTS)
        val side = s.sides.filterNotNull().singleOrNull() ?: throw CalcException(Failure.ARGUMENTS)
        return Calculus.evaluate(side)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Gradient descent.

    /** At most this many steps may be asked for. */
    private const val MOST_STEPS = 10_000

    /** The method stops where the gradient's length is below this: it has settled at a critical point. */
    private const val SETTLED = 1e-10

    /**
     * gradientdescent(f, x₀, η, n): from x₀ (a number for f of one letter, else a vector, its entries for f's letters in
     * alphabetical order), n steps of x ← x − η·∇f(x) with the gradient differentiated exactly and worked out in doubles; it stops
     * early where |∇f| < 10⁻¹⁰ (settled) or the point grows past 10¹⁵⁰ (diverged). The table holds k, the point, f there and
     * |∇f| there: the method's own approximation. Beside it the exact local minimum nearest to where it ended, where Calculate finds
     * one (the gradient's zeros, each put back, classified by the Hessian's leading minors).
     */
    private fun descent(args: List<List<String>>, flags: Flags): Outcome {
        if (args.size != 4) throw CalcException(Failure.ARGUMENTS)
        val f = argument(args[0], flags)
        if (f is Expr.Mat || f.hasI()) throw CalcException(Failure.ARGUMENTS)
        val letters = f.symbols().toSortedSet().toList()
        if (letters.isEmpty() || letters.size > 6) throw CalcException(Failure.ARGUMENTS)
        val start = argument(args[1], flags)
        val x0 = if (letters.size == 1) listOf(start.takeIf { it !is Expr.Mat } ?: throw CalcException(Failure.SIZES))
            else (start as? Expr.Mat)?.let { entries(it) }?.takeIf { it.size == letters.size } ?: throw CalcException(Failure.SIZES)
        val point0 = x0.map { real(it) ?: throw CalcException(Failure.ARGUMENTS) }
        val eta = real(argument(args[2], flags))?.takeIf { it > 0 } ?: throw CalcException(Failure.ARGUMENTS)
        val n = integer(argument(args[3], flags))?.takeIf { it.signum() > 0 && it <= BigInteger.valueOf(MOST_STEPS.toLong()) }?.toInt()
            ?: throw CalcException(Failure.ARGUMENTS)
        val gradient = letters.map { tidy(Calculus.derivative(f, Expr.Sym(it))) }
        fun env(x: List<Double>) = letters.indices.associate { letters[it] to x[it] }
        val all = ArrayList<List<Double>>()
        var x = point0
        var settled = false
        var diverged = false
        for (k in 0..n) {
            Budget.check()
            val e = env(x)
            val value = Numeric.eval(f, e)
            val g = gradient.map { Numeric.eval(it, e) }
            val length = kotlin.math.sqrt(g.sumOf { it * it })
            all += listOf(k.toDouble()) + x + listOf(value, length)
            if (!value.isFinite() || !length.isFinite() || x.any { !it.isFinite() || abs(it) > 1e150 }) { diverged = true; break }
            if (length < SETTLED) { settled = true; break }
            if (k == n) break
            x = x.indices.map { x[it] - eta * g[it] }
        }
        val rows = if (all.size <= 60) all else all.take(50) + all.takeLast(10)
        val columns = listOf("k") + letters.map { Format.name(it, latex = true) + "_k" } +
            listOf("f", "|\\nabla f|")
        val table = Numerics.Table(Numerics.Method.GRADIENT_DESCENT, columns, rows, null)
        val end = if (diverged) null else x
        val minimum = end?.let { p ->
            try {
                Budget.within(1000) { nearestMinimum(f, letters, gradient, p) }
            } catch (e: CalcException) {
                null
            } catch (e: ArithmeticException) {
                null
            }
        }
        return Outcome.Descent(letters, table, end, settled, minimum)
    }

    /**
     * The local minimum of [f] nearest to [p]: the zeros of the [gradient] (solved exactly, or numerically where the solver does),
     * each put back into every component, a minimum where the Hessian's leading minors are all > 0; null where there is none.
     */
    private fun nearestMinimum(f: Expr, letters: List<String>, gradient: List<Expr>, p: List<Double>): CriticalPoint? {
        val xs = letters.map { Expr.Sym(it) }
        val candidates: List<Map<String, Expr>> = if (letters.size == 1) {
            val s = Solve.equation(gradient[0], Expr.ZERO, xs[0])
            s.values.map { mapOf(letters[0] to it) }
        } else {
            val equations = gradient.filter { !isZero(it) }
            if (equations.isEmpty()) return null
            (Solve.system(equations, letters) ?: return null).filter { s -> letters.all { s[it]?.symbols()?.isEmpty() == true } }
        }
        val hessian = xs.map { a -> xs.map { b -> tidy(Calculus.derivative(Calculus.derivative(f, a), b)) } }
        var best: CriticalPoint? = null
        var bestDistance = Double.POSITIVE_INFINITY
        for (s in candidates) {
            Budget.check()
            if (s.values.any { it.hasI() || it.hasInfinity() }) continue
            fun at(e: Expr): Expr = tidy(letters.fold(e) { acc, n -> acc.substitute(Expr.Sym(n), s.getValue(n)) })
            if (!gradient.all { g -> at(g).let { v -> isZero(v) || Precise.nearZero(v, complex = false) == true } }) continue
            val h = hessian.map { row -> row.map { at(it) } }
            if (!positiveDefinite(h)) continue
            val coordinates = letters.map { Numeric.value(s.getValue(it)) ?: Double.NaN }
            val distance = kotlin.math.sqrt(coordinates.indices.sumOf { (coordinates[it] - p[it]).let { d -> d * d } })
            if (distance.isFinite() && distance < bestDistance) {
                bestDistance = distance
                best = CriticalPoint(letters.associateWith { s.getValue(it) }, CriticalKind.MINIMUM, at(f))
            }
        }
        return best
    }

    /** Every leading minor of [h] certified > 0 (Sylvester's criterion): a strict local minimum. */
    private fun positiveDefinite(h: List<List<Expr>>): Boolean {
        for (k in 1..h.size) {
            val minor = LinAlg.det(LinAlg.matrix(h.take(k).map { it.take(k) }))
            if (sign(tidy(minor)) != 1) return false
        }
        return true
    }
}
