package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * Sums in closed form by Gosper's algorithm, and what can be said of a series that has none. A hypergeometric term t(k) (its
 * ratio t(k + 1)/t(k) a rational function of k: k·2ᵏ, 1/(k(k + 1)), binomials) has an antidifference T with T(k + 1) − T(k) =
 * t(k) where Gosper finds one, so Σ_{a}^{b} t = T(b + 1) − T(a), and Σ_{a}^{∞} t = lim T − T(a). Every T is checked at whole
 * numbers before it is used. A series is called divergent only where a test shows it (its terms do not go to 0; terms like
 * 1/k by comparison; a ratio above 1), and given a value without a closed form only where a test bounds the error.
 */
internal object Sums {
    /** What can be said of Σ_{a}^{∞} t(k) without a closed form. */
    sealed class Verdict {
        /** Shown to grow without bound, or to have no limit. */
        object Diverges : Verdict()

        /** Shown to converge, its value to within the error the test bounds. */
        class Converges(val value: Double) : Verdict()

        object Unknown : Verdict()
    }

    /** An antidifference T of [t] in [k], checked, or null where Gosper's algorithm finds none. */
    fun antidifference(t: Expr, k: Expr.Sym): Expr? {
        if (!t.has(k)) return null
        val ratio = try { Algebra.simplify(Calculus.tidy(t.substitute(k, add(k, Expr.ONE)) / t)) } catch (e: CalcException) { return null }
        val kernels = Algebra.Kernels()
        val (n, d) = Algebra.fraction(ratio, kernels) ?: return null
        if (!kernels.isEmpty || !(n.unknowns + d.unknowns).all { it == k.name }) return null
        var a = coefficients(n, k.name)
        var b = coefficients(d, k.name)
        var c = listOf(Rational.ONE)
        // Gosper's form: no root of a and root of b a whole number apart.
        for (h in 0..60) {
            while (true) {
                Budget.check()
                val g = gcd(a, shift(b, h.toLong()))
                if (g.size <= 1) break
                a = quotient(a, g)
                b = quotient(b, shift(g, -h.toLong()))
                // c(k)·g(k − 1)···g(k − h): then a/b · c(k + 1)/c(k) is the ratio still.
                for (i in 1..h) c = times(c, shift(g, -i.toLong()))
            }
        }
        // a(k)·x(k + 1) − b(k − 1)·x(k) = c(k) for a polynomial x.
        val bm = shift(b, -1)
        val x = solvePolynomial(a, bm, c) ?: return null
        // T(k) = b(k − 1)·x(k)/c(k)·t(k).
        val T = Calculus.tidy(mul(poly(bm, k), poly(x, k), pow(poly(c, k), Expr.MINUS_ONE), t))
        // T(k + 1) − T(k) = t(k) at whole numbers where both are defined.
        var checked = 0
        for (j in 1..12) {
            val at = num(j.toLong() + 3)
            val lhs = Numeric.value(add(T.substitute(k, add(at, Expr.ONE)), neg(T.substitute(k, at)))) ?: continue
            val rhs = Numeric.value(t.substitute(k, at)) ?: continue
            if (abs(lhs - rhs) > 1e-9 * max(1.0, abs(rhs))) return null
            checked++
        }
        return T.takeIf { checked >= 6 }
    }

    /** Σ_{a}^{∞} [t] judged by the tests where it has no closed form. */
    fun verdict(t: Expr, k: Expr.Sym, a: Expr): Verdict {
        val start = integer(a)?.toLong() ?: return Verdict.Unknown
        // Alternating, (−1)^k·u(k): first, as the limit of its terms has no value of its own to take ((−1)^k has none). The
        // Leibniz test where u falls to 0 (the error at most the next term); the terms not going to 0 where u tends to c ≠ 0.
        alternating(t, k)?.let { u ->
            val limit = try { Calculus.limit(u, k, Expr.INF, null) } catch (e: CalcException) { null }
            if (limit != null && isZero(limit) && decreasing(u, k, start)) return Verdict.Converges(alternatingSum(t, k, start) ?: return Verdict.Unknown)
            if (limit != null && !limit.hasInfinity() && Numeric.value(limit)?.let { it != 0.0 } == true) return Verdict.Diverges
            if (limit == Expr.INF) return Verdict.Diverges
            return Verdict.Unknown
        }
        // A test that fails to take a limit shows nothing: no verdict from it.
        val terms = try { Calculus.limit(t, k, Expr.INF, null) } catch (e: CalcException) { null }
        // The terms do not go to 0: no sum.
        if (terms != null && !isZero(terms) && Numeric.value(terms)?.let { it != 0.0 } != false) return Verdict.Diverges
        if (terms == null && Numeric.value(t.substitute(k, num(100000))).let { it != null && abs(it) > 1e-3 }) return Verdict.Unknown
        // A rational function of k: like 1/k^(degree difference).
        rationalDegrees(t, k)?.let { (difference, sign) ->
            if (sign != 0) return if (difference >= 2) Verdict.Converges(numeric(t, k, start, polynomialDecay = difference) ?: return Verdict.Unknown)
                else Verdict.Diverges
        }
        // The ratio test.
        val ratio = try {
            Calculus.limit(fn(Func.ABS, Calculus.tidy(t.substitute(k, add(k, Expr.ONE)) / t)), k, Expr.INF, null)
        } catch (e: CalcException) { null }
        val q = ratio?.let { Numeric.value(it) }
        if (q != null) {
            if (q < 1 - 1e-12) return Verdict.Converges(ratioSum(t, k, start, q) ?: return Verdict.Unknown)
            if (q > 1 + 1e-12 || ratio == Expr.INF) return Verdict.Diverges
        }
        return Verdict.Unknown
    }

    /** For a rational function of k: deg(den) − deg(num), and the sign of its leading coefficient (0 where not rational). */
    private fun rationalDegrees(t: Expr, k: Expr.Sym): Pair<Int, Int>? {
        val kernels = Algebra.Kernels()
        val (n, d) = Algebra.fraction(t, kernels) ?: return null
        if (!kernels.isEmpty || !(n.unknowns + d.unknowns).all { it == k.name }) return null
        val nc = coefficients(n, k.name)
        val dc = coefficients(d, k.name)
        if (dc.size <= 1) return null
        val sign = nc.last().signum * dc.last().signum
        return (dc.size - nc.size) to sign
    }

    private fun alternating(t: Expr, k: Expr.Sym): Expr? {
        val factors = if (t is Expr.Mul) t.factors else listOf(t)
        val sign = factors.firstOrNull { it is Expr.Pow && it.base == Expr.MINUS_ONE && it.exp.has(k) } ?: return null
        val rest = mul(factors.filter { it !== sign })
        return rest.takeIf { !it.hasSign(k) }
    }

    private fun Expr.hasSign(k: Expr.Sym): Boolean = when (this) {
        is Expr.Pow -> (base == Expr.MINUS_ONE && exp.has(k)) || base.hasSign(k) || exp.hasSign(k)
        is Expr.Mul -> factors.any { it.hasSign(k) }
        is Expr.Add -> terms.any { it.hasSign(k) }
        is Expr.Fn -> args.any { it.hasSign(k) }
        else -> false
    }

    private fun decreasing(u: Expr, k: Expr.Sym, start: Long): Boolean {
        var last = Double.POSITIVE_INFINITY
        for (j in start..start + 200) {
            val v = Numeric.value(u.substitute(k, num(j))) ?: return false
            if (v < 0 || v > last + 1e-15) return false
            last = v
        }
        return true
    }

    private fun term(t: Expr, k: Expr.Sym, j: Long): Double? = Numeric.value(t.substitute(k, num(j)))

    /** Σ of an alternating series to within 1e-12 by its partial sums (the error at most the next term), averaged at the end. */
    private fun alternatingSum(t: Expr, k: Expr.Sym, start: Long): Double? {
        var s = 0.0
        var j = start
        var prev = 0.0
        while (j < start + 2_000_000) {
            Budget.check()
            val v = term(t, k, j) ?: return null
            if (abs(v) < 1e-13) return s + v / 2
            prev = s
            s += v
            j++
            // Slowly falling terms (1/k): the mean of two partial sums, Euler's way, after many.
            if (j - start > 200_000) return (s + prev) / 2
        }
        return (s + prev) / 2
    }

    /** Σ where the ratio of terms tends to q < 1: until the tail, at most t·q/(1 − q), is below 1e-14. */
    private fun ratioSum(t: Expr, k: Expr.Sym, start: Long, q: Double): Double? {
        var s = 0.0
        var j = start
        while (j < start + 100_000) {
            Budget.check()
            val v = term(t, k, j) ?: return null
            s += v
            if (j > start + 20 && abs(v) * max(q, 0.5) / (1 - max(q, 0.5)) < 1e-14 * max(1.0, abs(s))) return s
            j++
        }
        return null
    }

    /** Σ of positive terms falling like 1/k^p (p ≥ 2): partial sums and a tail by the integral, with Richardson's step. */
    private fun numeric(t: Expr, k: Expr.Sym, start: Long, polynomialDecay: Int): Double? {
        fun partial(n: Long): Double? {
            var s = 0.0
            for (j in start..n) s += term(t, k, j) ?: return null
            return s
        }
        val n = 20_000L
        val s1 = partial(n) ?: return null
        val s2 = s1 + (n + 1..2 * n).sumOf { term(t, k, it) ?: return null }
        // The tail falls like 1/n^(p−1): S ≈ s2 + (s2 − s1)/(2^(p−1) − 1).
        val factor = Math.pow(2.0, (polynomialDecay - 1).toDouble()) - 1
        return s2 + (s2 - s1) / factor
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Polynomials in one letter with rational coefficients, lowest power first.

    private fun coefficients(p: Polynomial, x: String): List<Rational> =
        List(max(0, p.degreeIn(x)) + 1) { j -> p.terms[if (j == 0) Monomial.ONE else Monomial(listOf(x to j))] ?: Rational.ZERO }
            .let { trim(it) }

    private fun trim(p: List<Rational>): List<Rational> = p.dropLastWhile { it.signum == 0 }.ifEmpty { listOf(Rational.ZERO) }

    private fun poly(p: List<Rational>, k: Expr.Sym): Expr = add(p.withIndex().map { (j, c) -> mul(num(c), pow(k, num(j.toLong()))) })

    /** p(k + h). */
    private fun shift(p: List<Rational>, h: Long): List<Rational> {
        var out = listOf(Rational.ZERO)
        // Horner: p(k + h) = (((c_n)(k + h) + c_{n−1})(k + h) + …).
        val kh = listOf(Rational.of(h), Rational.ONE)
        for (c in p.asReversed()) out = plus(times(out, kh), listOf(c))
        return trim(out)
    }

    private fun plus(a: List<Rational>, b: List<Rational>): List<Rational> =
        trim(List(max(a.size, b.size)) { j -> (a.getOrNull(j) ?: Rational.ZERO) + (b.getOrNull(j) ?: Rational.ZERO) })

    private fun times(a: List<Rational>, b: List<Rational>): List<Rational> {
        val out = MutableList(a.size + b.size - 1) { Rational.ZERO }
        for ((i, x) in a.withIndex()) for ((j, y) in b.withIndex()) out[i + j] = out[i + j] + x * y
        return trim(out)
    }

    private fun isZeroPoly(p: List<Rational>) = p.all { it.signum == 0 }

    private fun divide(a: List<Rational>, b: List<Rational>): Pair<List<Rational>, List<Rational>> {
        val d = trim(b)
        val r = trim(a).toMutableList()
        if (isZeroPoly(r) || r.size < d.size) return listOf(Rational.ZERO) to r
        val q = MutableList(r.size - d.size + 1) { Rational.ZERO }
        while (r.size >= d.size && !isZeroPoly(r)) {
            val s = r.size - d.size
            val f = r.last() / d.last()
            q[s] = f
            for ((i, c) in d.withIndex()) r[i + s] = r[i + s] - c * f
            while (r.size > 1 && r.last().signum == 0) r.removeAt(r.size - 1)
            if (r.size == 1 && r[0].signum == 0) break
        }
        return trim(q) to trim(r)
    }

    private fun quotient(a: List<Rational>, b: List<Rational>) = divide(a, b).first

    /** The monic gcd. */
    private fun gcd(a0: List<Rational>, b0: List<Rational>): List<Rational> {
        var a = trim(a0)
        var b = trim(b0)
        while (!isZeroPoly(b)) {
            val r = divide(a, b).second
            a = b
            b = r
        }
        if (isZeroPoly(a)) return listOf(Rational.ONE)
        val lead = a.last()
        return a.map { it / lead }
    }

    /** A polynomial x with a(k)·x(k + 1) − b(k)·x(k) = c(k), by its degree's bound and a linear system; null where none. */
    private fun solvePolynomial(a: List<Rational>, b: List<Rational>, c: List<Rational>): List<Rational>? {
        val da = a.size - 1
        val db = b.size - 1
        val dc = c.size - 1
        val candidates = ArrayList<Int>()
        if (da != db || a.last() != b.last()) {
            candidates += dc - max(da, db)
        } else {
            candidates += dc - da + 1
            // The other bound: (b_{n−1} − a_{n−1})/lc where a and b agree in their leading term.
            if (da >= 1) {
                val r = (b[da - 1] - a[da - 1]) / a.last()
                if (r.isInteger) candidates += r.num.toInt()
            }
        }
        val degree = candidates.max()
        if (degree < 0 || degree > 40) return null
        // Unknown coefficients x_0..x_d: each column is a(k)·k'(k+1)^j − b(k)·k^j.
        val columns = (0..degree).map { j ->
            val kj = List(j + 1) { if (it == j) Rational.ONE else Rational.ZERO }
            plus(times(a, shift(kj, 1)), times(b, kj).map { -it })
        }
        val rows = (columns.maxOf { it.size }).coerceAtLeast(c.size)
        val m = Array(rows) { r -> Array(degree + 2) { col -> if (col <= degree) columns[col].getOrNull(r) ?: Rational.ZERO else c.getOrNull(r) ?: Rational.ZERO } }
        // Gauss–Jordan with exact fractions.
        var rank = 0
        val pivots = IntArray(degree + 1) { -1 }
        for (col in 0..degree) {
            val p = (rank until rows).firstOrNull { m[it][col].signum != 0 } ?: continue
            val tmp = m[p]; m[p] = m[rank]; m[rank] = tmp
            val lead = m[rank][col]
            for (j in 0..degree + 1) m[rank][j] = m[rank][j] / lead
            for (r in 0 until rows) if (r != rank && m[r][col].signum != 0) {
                val f = m[r][col]
                for (j in 0..degree + 1) m[r][j] = m[r][j] - f * m[rank][j]
            }
            pivots[col] = rank
            rank++
        }
        for (r in rank until rows) if (m[r][degree + 1].signum != 0) return null
        return trim((0..degree).map { col -> if (pivots[col] >= 0) m[pivots[col]][degree + 1] else Rational.ZERO })
    }
}
