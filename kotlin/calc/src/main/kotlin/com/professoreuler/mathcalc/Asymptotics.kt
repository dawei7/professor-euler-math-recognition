package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * How functions grow as n → ∞, for the functions an algorithms course compares: constants, powers and roots of n, logarithms of
 * every base and their powers, ln ln n, exponentials aⁿ and a^f(n), nⁿ, n! and Γ, their sums, products and quotients.
 *
 * Each function is written as c·exp(Σ cₖ·hₖ)·(1 + o(1)) on the scale hₖ = n^e₀·(ln n)^e₁·(ln ln n)^e₂·… of functions that tend to
 * ∞ (the leading monomial of a logarithmico-exponential function, as Hardy compared them); n! by Stirling's formula. The limit of
 * f/g as n → ∞ then follows from the two: the largest scale function where their exponents differ decides between 0 and ∞, and
 * where they do not differ the limit is the quotient of the constants. Every limit is checked before it is shown: against the
 * calculator's own limit wherever that finds an exact one, and against ln f(N) − ln g(N) worked out directly from the formulas in
 * ball arithmetic at N = 2^64, 2^128, …, 2^1024, which must approach what the leading monomials say.
 */
object Asymptotics {
    // ---------------------------------------------------------------------------------------------------------------------
    // What the Calculator shows.

    /** What lim f/g says of f and g: f ∈ o(g), O(g), Θ(g), Ω(g), ω(g); and f ~ g where the limit is 1. */
    enum class Relation { LITTLE_O, BIG_O, THETA, BIG_OMEGA, LITTLE_OMEGA, EQUIVALENT }

    /**
     * Functions of [variable] compared as it tends to ∞. [sorted] false (asymptotic): [functions] f and g, [order] [0, 1] and
     * [limits] the one limit of f/g. [sorted] true (growthorder): [order] the functions' indices from the slowest-growing to the
     * fastest (Θ-equal ones side by side, in the order given), [limits] the limit of each one over the next in that order: 0 where
     * the next grows faster, a positive number where both are Θ of each other.
     */
    data class Growth(val variable: String, val functions: List<Expr>, val order: List<Int>, val limits: List<Expr>, val sorted: Boolean) {
        /** The relations of f to g that the limit of f/g gives. */
        fun relations(k: Int = 0): Set<Relation> = relationsOf(limits[k])
    }

    fun relationsOf(limit: Expr): Set<Relation> = when {
        limit == Expr.INF -> setOf(Relation.LITTLE_OMEGA, Relation.BIG_OMEGA)
        isZero(limit) -> setOf(Relation.LITTLE_O, Relation.BIG_O)
        isOne(limit) -> setOf(Relation.BIG_O, Relation.THETA, Relation.BIG_OMEGA, Relation.EQUIVALENT)
        else -> setOf(Relation.BIG_O, Relation.THETA, Relation.BIG_OMEGA)
    }

    /** f compared with g: lim f/g as [x] → ∞, checked. */
    fun compare(f: Expr, g: Expr, x: String): Growth {
        val l = checkedLimit(f, g, x)
        return Growth(x, listOf(f, g), listOf(0, 1), listOf(l), sorted = false)
    }

    /** [fs] sorted by growth, the slowest first; each limit between neighbours checked. */
    fun sort(fs: List<Expr>, x: String): Growth {
        val monos = fs.map { leading(it, x) }
        val order = try {
            fs.indices.sortedWith { i, j -> compareGrowth(monos[i], monos[j]) }
        } catch (u: Undecided) {
            throw CalcException(Failure.UNSOLVED)
        }
        val limits = order.zipWithNext { i, j -> checkedLimit(fs[i], fs[j], x) }
        // The order agrees with every limit: 0 or a positive number from each to the next, never ∞.
        if (limits.any { it == Expr.INF || it.hasInfinity() }) throw CalcException(Failure.UNSOLVED)
        return Growth(x, fs, order, limits, sorted = true)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The scale.

    private const val LEVELS = 6

    /** n^e₀·(ln n)^e₁·(ln ln n)^e₂·…, compared by its exponents: the first that differs decides which grows faster. */
    data class Scale(val e: List<Rational>) : Comparable<Scale> {
        operator fun plus(o: Scale) = Scale(e.indices.map { e[it] + o.e[it] })
        fun times(k: Rational) = Scale(e.map { it * k })

        override fun compareTo(other: Scale): Int {
            for (k in e.indices) {
                val c = e[k].compareTo(other.e[k])
                if (c != 0) return c
            }
            return 0
        }

        /** As an exponent: this = exp(e₀·ln n + e₁·ln ln n + …), each exponent on the next level; null past the last level. */
        fun asExponent(): Map<Scale, Expr>? {
            if (e.last().signum != 0) return null
            val out = LinkedHashMap<Scale, Expr>()
            for (k in 0 until LEVELS - 1) if (e[k].signum != 0) out[level(k + 1)] = num(e[k])
            return out
        }

        companion object {
            val ONE = Scale(List(LEVELS) { Rational.ZERO })
            fun level(k: Int) = Scale(List(LEVELS) { if (it == k) Rational.ONE else Rational.ZERO })
        }
    }

    /**
     * A function as c·exp(Σ cₖ·hₖ)·(1 + o(1)): [c] its constant, [exponent] the coefficient cₖ of each scale function hₖ → ∞ in
     * its exponent; [exact] where the o(1) is 0.
     */
    class Mono(val c: Expr, val exponent: Map<Scale, Expr>, val exact: Boolean)

    /**
     * A function as Σ cₖ·hₖ + o([error]) (hₖ any scale functions, the constant 1 among them); [error] null where the sum is exact.
     * Terms smaller than the error are left out: they say nothing.
     */
    class Expansion(val terms: Map<Scale, Expr>, val error: Scale?)

    private class Undecided : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** The sign of a constant, certified, or [Failure.UNSOLVED] where it cannot be told. */
    internal fun signOf(e: Expr): Int = try {
        sign(e)
    } catch (u: Undecided) {
        throw CalcException(Failure.UNSOLVED)
    }

    /** The sign of a constant, certified: exactly 0, or the whole ball on one side of 0. */
    private fun sign(e: Expr): Int {
        val t = tidy(e)
        if (isZero(t)) return 0
        if (t is Expr.Num) return t.value.signum
        if (t.symbols().isNotEmpty() || t.hasInfinity()) throw Undecided()
        val z = try { Precise.eval(t, 160, false, emptyMap()) } catch (x: Imprecise) { throw Undecided() } catch (x: CalcException) {
            if (x.failure == Failure.TOO_LONG) throw x
            throw Undecided()
        }
        if (z.re.isPositive) return 1
        if (z.re.isNegative) return -1
        throw Undecided()
    }

    private fun merged(a: Map<Scale, Expr>, b: Map<Scale, Expr>, factor: Expr = Expr.ONE): MutableMap<Scale, Expr> {
        val out = LinkedHashMap(a)
        for ((s, c) in b) {
            val v = tidy(add(out[s] ?: Expr.ZERO, mul(factor, c)))
            if (sign(v) == 0) out.remove(s) else out[s] = v
        }
        return out
    }

    /** The scale function where two exponents differ most (the largest), and the sign of that difference; 0 where they agree. */
    private fun differ(a: Map<Scale, Expr>, b: Map<Scale, Expr>): Pair<Scale?, Int> {
        val d = merged(a, b, Expr.MINUS_ONE)
        val top = d.keys.maxOrNull() ?: return null to 0
        return top to sign(d.getValue(top))
    }

    private fun compareGrowth(a: Mono, b: Mono): Int = differ(a.exponent, b.exponent).second

    // ---------------------------------------------------------------------------------------------------------------------
    // Leading monomials.

    /** The leading monomial of [e] in [x], or [Failure.UNSOLVED] where the calculator cannot tell how it grows. */
    fun leading(e: Expr, x: String): Mono = try {
        mono(tidy(e), Expr.Sym(x))
    } catch (u: Undecided) {
        throw CalcException(Failure.UNSOLVED)
    } catch (u: IllegalArgumentException) {
        throw CalcException(Failure.UNSOLVED)
    }

    private fun mono(e: Expr, x: Expr.Sym): Mono {
        Budget.check()
        if (!e.has(x)) {
            if (e.hasInfinity() || e is Expr.Mat || e.hasOp()) throw Undecided()
            if (sign(e) == 0) throw Undecided()
            return Mono(e, emptyMap(), true)
        }
        return when (e) {
            is Expr.Sym -> Mono(Expr.ONE, mapOf(Scale.level(1) to Expr.ONE), true)
            is Expr.Mul -> e.factors.map { mono(it, x) }.reduce { a, b -> product(a, b) }
            is Expr.Add -> sum(e, x)
            is Expr.Pow -> power(e, x)
            is Expr.Fn -> when (e.f) {
                Func.LN -> fromExpansion(logarithm(mono(e.args[0], x)))
                Func.LOG -> {
                    val base = e.args[0]
                    if (base.has(x)) throw Undecided()
                    val lb = tidy(ln(base))
                    if (sign(lb) == 0) throw Undecided()
                    fromExpansion(scaled(logarithm(mono(e.args[1], x)), pow(lb, Expr.MINUS_ONE)))
                }
                Func.FACTORIAL -> stirling(e.args[0], x, shift = true)
                Func.GAMMA -> stirling(e.args[0], x, shift = false)
                Func.ABS -> mono(e.args[0], x).also { if (sign(it.c) <= 0) throw Undecided() }
                else -> throw Undecided()
            }
            else -> throw Undecided()
        }
    }

    private fun product(a: Mono, b: Mono) = Mono(tidy(mul(a.c, b.c)), merged(a.exponent, b.exponent), a.exact && b.exact)

    /** A sum: its fastest-growing terms decide; where those cancel, the exact sum of the terms' expansions. */
    private fun sum(e: Expr.Add, x: Expr.Sym): Mono {
        val terms = e.terms.map { mono(it, x) }
        var top = terms[0]
        for (t in terms.drop(1)) if (compareGrowth(t, top) > 0) top = t
        val same = terms.filter { compareGrowth(it, top) == 0 }
        val c = tidy(add(same.map { it.c }))
        if (sign(c) != 0) return Mono(c, top.exponent, exact = same.size == terms.size && terms.all { it.exact })
        return fromExpansion(expansion(e, x))
    }

    private fun power(e: Expr.Pow, x: Expr.Sym): Mono {
        if (!e.exp.has(x)) {
            val b = mono(e.base, x)
            val k = e.exp
            val r = rational(k)
            if (sign(b.c) < 0 && (r == null || !r.isInteger)) throw Undecided()
            return Mono(tidy(pow(b.c, k)), b.exponent.mapValues { tidy(mul(it.value, k)) }, b.exact)
        }
        // aᶠ and fᵍ: e to the power of the exponent's expansion.
        val inExponent = if (e.base == Expr.E) e.exp else {
            if (!e.base.has(x) && sign(e.base) <= 0) throw Undecided()
            mul(e.exp, ln(e.base))
        }
        return exponential(expansion(tidy(inExponent), x))
    }

    /** n! = Γ(n + 1) and Γ(u) by Stirling: ln Γ(u) = (u − ½)·ln u − u + ½·ln 2π + o(1), ln u! = (u + ½)·ln u − u + ½·ln 2π + o(1). */
    private fun stirling(u: Expr, x: Expr.Sym, shift: Boolean): Mono {
        val m = mono(u, x)
        // u must tend to ∞.
        val top = m.exponent.keys.maxOrNull() ?: throw Undecided()
        if (sign(m.c) <= 0 || sign(m.exponent.getValue(top)) <= 0) throw Undecided()
        val half = if (shift) Expr.HALF else neg(Expr.HALF)
        val s = add(mul(add(u, half), ln(u)), neg(u), mul(Expr.HALF, ln(mul(Expr.TWO, Expr.PI))))
        val ex = expansion(tidy(s), x)
        return exponential(Expansion(ex.terms, maxOf(ex.error ?: Scale.ONE, Scale.ONE)), exact = false)
    }

    /** exp of an expansion whose error is o(1): its constant term becomes the constant, its growing terms the exponent. */
    private fun exponential(x: Expansion, exact: Boolean = true): Mono {
        val error = x.error
        if (error != null && error > Scale.ONE) throw Undecided()
        val exponent = LinkedHashMap<Scale, Expr>()
        var c: Expr = Expr.ONE
        var dropped = false
        for ((s, k) in x.terms) when {
            s > Scale.ONE -> exponent[s] = k
            s == Scale.ONE -> c = expOf(k)
            else -> dropped = true
        }
        return Mono(c, exponent, exact && error == null && !dropped)
    }

    /** e^k with each r·ln a in k as a^r: e^(ln(2)/2 + ln(2π)/2) is √2·√(2π) = 2√π. */
    private fun expOf(k: Expr): Expr {
        val terms = if (k is Expr.Add) k.terms else listOf(k)
        return tidy(mul(terms.map { t ->
            val (r, rest) = splitCoefficient(t)
            if (rest is Expr.Fn && rest.f == Func.LN) pow(rest.args[0], r) else exp(t)
        }))
    }

    /** ln of a monomial: ln c + Σ cₖ·hₖ + o(1). */
    private fun logarithm(m: Mono): Expansion {
        if (sign(m.c) <= 0) throw Undecided()
        val terms = LinkedHashMap(m.exponent)
        val lc = tidy(ln(m.c))
        if (sign(lc) != 0) terms[Scale.ONE] = lc
        return Expansion(terms, if (m.exact) null else Scale.ONE)
    }

    private fun scaled(x: Expansion, k: Expr) = Expansion(x.terms.mapValues { tidy(mul(it.value, k)) }, x.error)

    /** The leading term of an expansion as a monomial. */
    private fun fromExpansion(x: Expansion): Mono {
        val top = x.terms.keys.maxOrNull() ?: throw Undecided()
        if (x.error != null && top < x.error) throw Undecided()
        val c = x.terms.getValue(top)
        val exponent = top.asExponent() ?: throw Undecided()
        return Mono(c, exponent, x.error == null && x.terms.size == 1)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Expansions.

    private fun expansion(e: Expr, x: Expr.Sym): Expansion {
        Budget.check()
        if (!e.has(x)) return Expansion(if (sign(e) == 0) emptyMap() else mapOf(Scale.ONE to e), null)
        return when (e) {
            is Expr.Sym -> Expansion(mapOf(Scale.level(0) to Expr.ONE), null)
            is Expr.Add -> e.terms.map { expansion(it, x) }.reduce { a, b -> plus(a, b) }
            is Expr.Mul -> e.factors.map { expansion(it, x) }.reduce { a, b -> times(a, b) }
            is Expr.Pow -> {
                val k = rational(e.exp)
                if (k != null && k.isInteger && k.signum > 0 && k.num <= BigInteger.valueOf(16)) {
                    val b = expansion(e.base, x)
                    (1 until k.num.toInt()).fold(b) { acc, _ -> times(acc, b) }
                } else fromMono(mono(e, x))
            }
            is Expr.Fn -> when (e.f) {
                Func.LN -> lnOf(e.args[0], x)
                Func.LOG -> {
                    if (e.args[0].has(x)) throw Undecided()
                    scaled(lnOf(e.args[1], x), pow(ln(e.args[0]), Expr.MINUS_ONE))
                }
                else -> fromMono(mono(e, x))
            }
            else -> throw Undecided()
        }
    }

    /**
     * ln of [arg]: where [arg] is an exact sum c₀·h₀·(1 + u) (n + 1, n² − n), ln c₀ + ln h₀ + u − u²/2 + … − u⁸/8 with an error of
     * u⁹'s size, which a product with n or n² still keeps below 1 (as Stirling's formula for (n + 1)! needs); else from the leading
     * monomial, ln c + Σ cₖ·hₖ + o(1).
     */
    private fun lnOf(arg: Expr, x: Expr.Sym): Expansion {
        val exact = try {
            expansion(arg, x).takeIf { it.error == null && it.terms.size > 1 }
        } catch (u: Undecided) {
            null
        }
        if (exact == null) return logarithm(mono(arg, x))
        val (s0, c0) = exact.terms.entries.maxByOrNull { it.key }!!.toPair()
        if (sign(c0) <= 0) throw Undecided()
        var result = logarithm(Mono(c0, s0.asExponent() ?: throw Undecided(), true))
        val inverse = s0.times(-Rational.ONE)
        val u = Expansion(exact.terms.filterKeys { it != s0 }.entries.associate { (s, c) -> (s + inverse) to tidy(mul(c, pow(c0, Expr.MINUS_ONE))) }, null)
        val top = u.terms.keys.maxOrNull() ?: return result
        var power = u
        for (k in 1..8) {
            val sign = if (k % 2 == 1) Rational.ONE else -Rational.ONE
            result = plus(result, scaled(power, num(sign / Rational.of(k.toLong()))))
            if (k < 8) power = times(power, u)
        }
        return cut(result.terms, top.times(Rational.of(9)))
    }

    /** A monomial c·n^a·(ln n)^b·… (its exponent only of logarithms) as one term c·h + o(h). */
    private fun fromMono(m: Mono): Expansion {
        val e = MutableList(LEVELS) { Rational.ZERO }
        for ((s, k) in m.exponent) {
            val level = s.e.indexOfFirst { it.signum != 0 }
            if (level < 1 || s != Scale.level(level)) throw Undecided()
            e[level - 1] = rational(k) ?: throw Undecided()
        }
        val h = Scale(e)
        return Expansion(mapOf(h to m.c), if (m.exact) null else h)
    }

    private fun cut(terms: Map<Scale, Expr>, error: Scale?) = Expansion(if (error == null) terms else terms.filterKeys { it >= error }, error)

    private fun plus(a: Expansion, b: Expansion): Expansion {
        val error = listOfNotNull(a.error, b.error).maxOrNull()
        return cut(merged(a.terms, b.terms), error)
    }

    private fun times(a: Expansion, b: Expansion): Expansion {
        val terms = LinkedHashMap<Scale, Expr>()
        for ((s, c) in a.terms) for ((t, d) in b.terms) {
            val key = s + t
            val v = tidy(add(terms[key] ?: Expr.ZERO, mul(c, d)))
            if (sign(v) == 0) terms.remove(key) else terms[key] = v
        }
        val errors = ArrayList<Scale>()
        val leadA = a.terms.keys.maxOrNull()
        val leadB = b.terms.keys.maxOrNull()
        if (a.error != null) {
            if (leadB != null) errors += leadB + a.error
            if (b.error != null) errors += a.error + b.error
        }
        if (b.error != null && leadA != null) errors += leadA + b.error
        return cut(terms, errors.maxOrNull())
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The limit and its checks.

    /** lim f/g as [x] → ∞ from the leading monomials: 0, ∞ or a positive constant. */
    fun limit(f: Expr, g: Expr, x: String): Expr {
        val a = leading(f, x)
        val b = leading(g, x)
        try {
            if (sign(a.c) <= 0 || sign(b.c) <= 0) throw CalcException(Failure.UNDEFINED)
            val (_, s) = differ(a.exponent, b.exponent)
            return when {
                s > 0 -> Expr.INF
                s < 0 -> Expr.ZERO
                else -> tidy(mul(a.c, pow(b.c, Expr.MINUS_ONE)))
            }
        } catch (u: Undecided) {
            throw CalcException(Failure.UNSOLVED)
        }
    }

    /** The limit, checked against the calculator's own limit (where it finds an exact one) and against values far out. */
    fun checkedLimit(f: Expr, g: Expr, x: String): Expr {
        val l = limit(f, g, x)
        val v = Expr.Sym(x)
        val engine = try {
            Calculus.limit(tidy(mul(f, pow(g, Expr.MINUS_ONE))), v, Expr.INF, null)
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            null
        } catch (e: ArithmeticException) {
            null
        } catch (e: StackOverflowError) {
            null
        }
        if (engine != null && !hasReal(engine)) {
            val agrees = try {
                when {
                    l == Expr.INF -> engine == Expr.INF
                    isZero(l) -> isZero(tidy(engine))
                    else -> !engine.hasInfinity() && sign(add(engine, neg(l))) == 0
                }
            } catch (u: Undecided) {
                false
            }
            if (!agrees) throw CalcException(Failure.UNSOLVED)
        }
        if (!farOut(f, g, x)) throw CalcException(Failure.UNSOLVED)
        return l
    }

    private fun hasReal(e: Expr): Boolean = when (e) {
        is Expr.Real -> true
        is Expr.Add -> e.terms.any(::hasReal)
        is Expr.Mul -> e.factors.any(::hasReal)
        is Expr.Pow -> hasReal(e.base) || hasReal(e.exp)
        is Expr.Fn -> e.args.any(::hasReal)
        else -> false
    }

    /**
     * ln f(N) − ln g(N), worked out from the formulas in ball arithmetic, against what the leading monomials say it is (ln c_f −
     * ln c_g + the exponents' difference at N): their difference is ln(1 + o(1)), so it must be small at N = 2^1024 and must not
     * grow from N = 2^256 on, or else shrink from N = 2^64 to 2^1024 at every step and by a quarter in all.
     */
    private fun farOut(f: Expr, g: Expr, x: String): Boolean = try {
        val a = leading(f, x)
        val b = leading(g, x)
        val d = merged(a.exponent, b.exponent, Expr.MINUS_ONE)
        val gaps = (6..10).map { j -> gapAt(f, g, a, b, d, x, j) }
        val last = gaps.last()
        // Small, and not growing; or, where the o(1) shrinks as slowly as 1/ln ln N does, shrinking all the way and by a quarter.
        val falling = gaps.zipWithNext().all { (p, q) -> q <= p + 1e-9 }
        (last <= 0.3 && gaps[4] <= gaps[3] + 1e-9 && gaps[3] <= gaps[2] + 1e-9) || (falling && last <= 0.75 * gaps[0] && last <= 1.5)
    } catch (u: Undecided) {
        false
    } catch (u: Imprecise) {
        false
    } catch (e: CalcException) {
        if (e.failure == Failure.TOO_LONG) throw e
        false
    } catch (e: ArithmeticException) {
        false
    }

    /** |true − predicted| at N = 2^(2^j), to the precision that decides it to about 10⁻⁶. */
    private fun gapAt(f: Expr, g: Expr, a: Mono, b: Mono, d: Map<Scale, Expr>, x: String, j: Int): Double {
        var prec = (1 shl j) + 192
        while (prec <= 16_384) {
            Budget.check()
            val big = Ball(BigFloat.ONE.shl(1L shl j))
            val logs = levels(big, prec)
            val truth = LogEval(x, big, prec).ln(f).add(-LogEval(x, big, prec).ln(g), prec)
            var predicted = ball(tidy(ln(a.c)), prec).sub(ball(tidy(ln(b.c)), prec), prec)
            for ((s, k) in d) predicted = predicted.add(ball(k, prec).mul(scaleAt(s, logs, prec), prec), prec)
            val gap = truth.sub(predicted, prec)
            if (gap.rad.toDouble() < 1e-6) return kotlin.math.abs(gap.mid.toDouble())
            prec *= 2
        }
        throw Undecided()
    }

    private fun ball(e: Expr, prec: Int): Ball = Precise.eval(e, prec, false, emptyMap()).re

    /** N, ln N, ln ln N, …: as many levels as are positive. */
    private fun levels(n: Ball, prec: Int): List<Ball> {
        val out = arrayListOf(n)
        while (out.size < LEVELS) {
            val last = out.last()
            if (!last.isPositive) break
            out += BallMath.ln(last, prec)
        }
        return out
    }

    /** A scale function at N: exp(Σ eₖ·ln Lₖ), Lₖ the k-th level. */
    private fun scaleAt(s: Scale, logs: List<Ball>, prec: Int): Ball {
        var sum = Ball.ZERO
        for ((k, e) in s.e.withIndex()) {
            if (e.signum == 0) continue
            val level = logs.getOrNull(k) ?: throw Undecided()
            if (!level.isPositive) throw Undecided()
            sum = sum.add(Ball.of(e, prec).mul(BallMath.ln(level, prec), prec), prec)
        }
        return BallMath.exp(sum, prec)
    }

    /**
     * The logarithm of a formula's value at [x] = N, in balls, worked out without the value itself (which is far too large): ln of
     * a product the sum of the factors' logarithms, ln aᵘ = u·ln a, ln of a sum by its largest term, ln N! by Stirling's series with
     * its bound 0 < R < 1/(360·N³).
     */
    private class LogEval(val x: String, val n: Ball, val prec: Int) {
        private val env = mapOf(x to CBall(n))

        /** The value itself, where it is not too large: an exponent, the argument of a logarithm or of n!. */
        fun value(e: Expr): Ball = Precise.eval(e, prec, false, env).re

        fun ln(e: Expr): Ball {
            val (s, l) = signed(e)
            if (s <= 0) throw Undecided()
            return l
        }

        /** The sign of e(N) and ln |e(N)|. */
        fun signed(e: Expr): Pair<Int, Ball> {
            Budget.check()
            if (!e.has(Expr.Sym(x))) {
                val v = value(e)
                if (v.isPositive) return 1 to BallMath.ln(v, prec)
                if (v.isNegative) return -1 to BallMath.ln(-v, prec)
                throw Undecided()
            }
            return when (e) {
                is Expr.Sym -> 1 to BallMath.ln(n, prec)
                is Expr.Mul -> e.factors.map { signed(it) }.fold(1 to Ball.ZERO) { (s, l), (t, m) -> s * t to l.add(m, prec) }
                is Expr.Pow -> {
                    if (e.base == Expr.E) return 1 to value(e.exp)
                    val r = rational(e.exp)
                    if (r != null) {
                        val (s, l) = signed(e.base)
                        if (s < 0 && !r.isInteger) throw Undecided()
                        val sign = if (s < 0 && r.num.testBit(0)) -1 else 1
                        return sign to l.mul(Ball.of(r, prec), prec)
                    }
                    val (s, l) = signed(e.base)
                    if (s <= 0) throw Undecided()
                    1 to value(e.exp).mul(l, prec)
                }
                is Expr.Add -> {
                    val parts = e.terms.map { signed(it) }
                    val top = parts.maxByOrNull { it.second.mid }!!.second
                    var total = Ball.ZERO
                    for ((s, l) in parts) {
                        val d = l.sub(top, prec)
                        // A term below the precision only widens the sum.
                        val term = if (d.upper < BigFloat.of(-(prec + 64).toLong())) Ball(BigFloat.ZERO, BigFloat.ONE.shl(-(prec + 32).toLong()))
                            else BallMath.exp(d, prec)
                        total = total.add(if (s < 0) -term else term, prec)
                    }
                    when {
                        total.isPositive -> 1 to top.add(BallMath.ln(total, prec), prec)
                        total.isNegative -> -1 to top.add(BallMath.ln(-total, prec), prec)
                        else -> throw Undecided()
                    }
                }
                is Expr.Fn -> when (e.f) {
                    Func.LN -> {
                        val (s, l) = signed(e.args[0])
                        if (s <= 0) throw Undecided()
                        positive(l)
                    }
                    Func.LOG -> {
                        val (s, l) = signed(e.args[1])
                        if (s <= 0) throw Undecided()
                        positive(l.div(BallMath.ln(value(e.args[0]), prec), prec))
                    }
                    Func.FACTORIAL -> 1 to lnGamma(value(e.args[0]).add(Ball.ONE, prec))
                    Func.GAMMA -> 1 to lnGamma(value(e.args[0]))
                    Func.ABS -> signed(e.args[0]).let { (s, l) -> if (s == 0) throw Undecided() else 1 to l }
                    else -> throw Undecided()
                }
                else -> throw Undecided()
            }
        }

        private fun positive(v: Ball): Pair<Int, Ball> = when {
            v.isPositive -> 1 to BallMath.ln(v, prec)
            v.isNegative -> -1 to BallMath.ln(-v, prec)
            else -> throw Undecided()
        }

        /** ln Γ(z) for z ≥ 10: (z − ½)·ln z − z + ½·ln 2π + 1/(12z) − R, 0 < R < 1/(360·z³). */
        private fun lnGamma(z: Ball): Ball {
            if (!z.sub(Ball.of(10), prec).isPositive) throw Undecided()
            val lnz = BallMath.ln(z, prec)
            val twoPi = BallMath.pi(prec).shl(1)
            var s = z.sub(Ball(BigFloat.HALF), prec).mul(lnz, prec).sub(z, prec)
                .add(BallMath.ln(twoPi, prec).shl(-1), prec).add(z.mul(12L, prec).inverse(prec), prec)
            val bound = z.mul(z, prec).mul(z, prec).mul(360L, prec).inverse(prec)
            s = s.sub(bound.shl(-1), prec).widen(bound.upper.shl(-1).abs())
            return s
        }
    }
}
