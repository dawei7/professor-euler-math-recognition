package com.professoreuler.mathcalc

import kotlin.math.abs

/**
 * Laurent series in a small t > 0: x = a + t or a − t near a point, x = 1/t or −1/t at ±∞. Where e holds roots of x (√x,
 * x^(1/3)), t's power q makes them whole: x = a + t^q. A series is c₀·tᵛ + c₁·tᵛ⁺¹ + … with as many terms as are known; a
 * logarithm of t is kept as the letter [L] in a coefficient (it grows more slowly than any power), so x·ln x at 0⁺ is L·t,
 * which tends to 0, and x^x = e^(x ln x) is 1 + L·t + …. Sums, products, powers, exp and ln by their recurrences, |u| by the
 * sign of its leading term, and every function of one argument analytic at the point by its derivatives there.
 *
 * Limits come from the leading term ([limit]); [taylor] gives a Taylor or Laurent polynomial at a point.
 */
internal object Series {
    /** ln t, as t → 0⁺. */
    val L = Expr.Sym("\u0001L")

    /** Σ c[j]·t^(v + j), the terms known; beyond them O(t^(v + c.size)). */
    class S(val v: Int, val c: List<Expr>) {
        val upto get() = v + c.size
        fun at(exponent: Int): Expr = c.getOrNull(exponent - v) ?: Expr.ZERO
    }

    /** How x is written in t: x = a + σ·t^q near a finite a, x = σ/t^q at infinity. */
    private class Place(val a: Expr?, val sigma: Int, val q: Int)

    /** The limit of [e] as [x] → [a] (finite, ∞ or −∞) from [side] (1 from above, −1 below, null both), or null. */
    fun limit(e: Expr, x: Expr.Sym, a: Expr, side: Int?): Expr? {
        val q = rootDegree(e, x) ?: return null
        if (a.hasInfinity()) return oneSided(e, x, Place(null, if (a == Expr.INF) 1 else -1, q))
        if (side != null) return oneSided(e, x, Place(a, side, q))
        val above = oneSided(e, x, Place(a, 1, q)) ?: return null
        val below = oneSided(e, x, Place(a, -1, q)) ?: return null
        return if (same(above, below)) above else null
    }

    private fun same(p: Expr, q: Expr): Boolean {
        if (p.hasInfinity() || q.hasInfinity()) return p == q
        if (p == q) return true
        val a = Numeric.value(p) ?: return false
        val b = Numeric.value(q) ?: return false
        return abs(a - b) <= 1e-12 * maxOf(1.0, abs(a))
    }

    private fun oneSided(e: Expr, x: Expr.Sym, place: Place): Expr? {
        for (n in listOf(6, 10, 16)) {
            Budget.check()
            val s = build(e, x, place, n) ?: return null
            val (v, c) = leading(s) ?: continue
            if (v > 0) return Expr.ZERO
            if (v == 0 && !c.has(L)) return Calculus.tidy(c)
            val sign = dominantSign(c) ?: return null
            return if (sign > 0) Expr.INF else neg(Expr.INF)
        }
        return null
    }

    /**
     * [e]'s Taylor polynomial (or Laurent, where it has negative powers) at a finite [a] up to (x − a)ⁿ, or null where it has
     * none: the same from both sides, no logarithm of t and no root of it.
     */
    fun taylor(e: Expr, x: Expr.Sym, a: Expr, n: Int): Expr? {
        val terms = taylorTerms(e, x, a, n) ?: return null
        val h = if (isZero(a)) x else add(x, neg(a))
        return if (terms.isEmpty()) Expr.ZERO else add(terms.map { (c, j) -> mul(c, pow(h, num(j.toLong()))) })
    }

    /** The same as coefficients and powers of (x − a), in rising powers. */
    fun taylorTerms(e: Expr, x: Expr.Sym, a: Expr, n: Int): List<Pair<Expr, Int>>? {
        val above = build(e, x, Place(a, 1, 1), n + 6) ?: return null
        val below = build(e, x, Place(a, -1, 1), n + 6) ?: return null
        if (above.c.any { it.has(L) }) return null
        // From below t is −(x − a): each coefficient of the same power with (−1)^j.
        val terms = ArrayList<Pair<Expr, Int>>()
        for (j in minOf(above.v, below.v)..n) {
            val up = above.at(j)
            val down = below.at(j)
            val expected = if (j % 2 == 0) down else neg(down)
            if (!zero(add(up, neg(expected)))) return null
            if (j >= above.upto || j >= below.upto) return null
            if (!zero(up)) terms += Calculus.tidy(up) to j
        }
        return terms
    }

    /**
     * Whether [t] is [e]'s series to the power [n] at [a]: near a, from both sides, e − t shrinks at least as fast as
     * |x − a|^(n+1) does (its ratio at two distances bounded), or is nothing but rounding.
     */
    fun matches(e: Expr, t: Expr, x: Expr.Sym, a: Expr, n: Int): Boolean {
        val av = Numeric.value(a) ?: return false
        // Other letters ((1 + x)^a) take several values each, and the series must match at every one of them; with none, a
        // series in letters would compare nothing (every value NaN) and pass unseen.
        val others = (e.symbols() + t.symbols() - x.name).toList()
        val settings = if (others.isEmpty()) listOf(emptyMap()) else listOf(0.37, 1.61, -0.73, 2.29).map { v ->
            others.withIndex().associate { (k, name) -> name to v + 0.11 * k }
        }
        // At 0.1 and 0.05, else (a nearer singularity: 1/(1 − 10x)) at 0.02 and 0.01.
        return settings.all { env ->
            val compared = intArrayOf(0)
            (closeAt(e, t, x, av, n, 0.1, env, compared) || closeAt(e, t, x, av, n, 0.02, env, compared)) && (others.isEmpty() || compared[0] > 0)
        }
    }

    private fun closeAt(e: Expr, t: Expr, x: Expr.Sym, av: Double, n: Int, h: Double, env: Map<String, Double> = emptyMap(),
        compared: IntArray = IntArray(1)): Boolean {
        for (sign in listOf(1.0, -1.0)) {
            val near = DoubleArray(2)
            val hs = doubleArrayOf(h, h / 2)
            for (i in 0..1) {
                val at = env + (x.name to av + sign * hs[i])
                val f = Numeric.eval(e, at)
                val g = Numeric.eval(t, at)
                // Undefined on that side (√x, ln x below 0): the other side decides.
                if (!f.isFinite()) { near[0] = Double.NaN; break }
                if (!g.isFinite()) return false
                near[i] = abs(f - g)
            }
            if (near[0].isNaN()) continue
            compared[0]++
            val scale = maxOf(1.0, abs(Numeric.eval(e, env + (x.name to av + sign * hs[1]))))
            if (near[1] <= 1e-11 * scale) continue
            // Halving the distance divides the error by about 2^(n+1); allow a generous margin for the next terms.
            val ratio = near[1] / near[0]
            if (!(ratio <= Math.pow(0.5, (n + 1).toDouble()) * 4.0)) return false
        }
        return true
    }

    /** The power of t that makes every root of x whole: the least common multiple of their degrees (at most 12), or null. */
    private fun rootDegree(e: Expr, x: Expr.Sym): Int? {
        var q = 1
        fun visit(u: Expr) {
            if (u is Expr.Pow && u.base.has(x)) rational(u.exp)?.let { r -> if (!r.isInteger) q = lcm(q, r.den.toInt()) }
            when (u) {
                is Expr.Add -> u.terms.forEach(::visit)
                is Expr.Mul -> u.factors.forEach(::visit)
                is Expr.Pow -> { visit(u.base); visit(u.exp) }
                is Expr.Fn -> u.args.forEach(::visit)
                else -> {}
            }
        }
        visit(e)
        return if (q > 12) null else q
    }

    private fun lcm(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) { val t = x % y; x = y; y = t }
        return a / x * b
    }

    /** The first nonzero term: its exponent and coefficient. */
    private fun leading(s: S): Pair<Int, Expr>? {
        for ((j, c) in s.c.withIndex()) if (!zero(c)) return (s.v + j) to c
        return null
    }

    /** Exactly zero, or zero in ball arithmetic (a coefficient of numbers only). */
    private fun zero(c: Expr): Boolean {
        val e = Algebra.expand(c)
        if (isZero(e)) return true
        if (e.has(L) || e.symbols().isNotEmpty()) return false
        val d = Numeric.value(e) ?: return false
        if (abs(d) > 1e-9) return false
        return Precise.nearZero(e, complex = false) == true
    }

    /** The sign of c as t → 0⁺: of its highest power of L (L → −∞), else of c itself; null where it depends on a letter. */
    private fun dominantSign(c: Expr): Int? {
        val e = Algebra.expand(c)
        if (!e.has(L)) return signOf(e)
        val powers = Solve.coefficientsIn(e, L) ?: return null
        val d = powers.indexOfLast { !zero(it) }
        if (d < 0) return null
        val s = signOf(powers[d]) ?: return null
        return if (d % 2 == 0) s else -s
    }

    private fun signOf(e: Expr): Int? {
        if (e.symbols().isNotEmpty()) return null
        val d = Numeric.value(e) ?: return null
        return if (d > 0) 1 else if (d < 0) -1 else null
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Building a series.

    private fun build(e: Expr, x: Expr.Sym, place: Place, n: Int): S? = try {
        Builder(x, place, n * place.q).of(e)
    } catch (z: CalcException) {
        if (z.failure == Failure.TOO_LONG) throw z
        null
    } catch (z: ArithmeticException) {
        null
    }

    private class Builder(val x: Expr.Sym, val place: Place, val n: Int) {
        private fun exact(v: Int, vararg c: Pair<Int, Expr>): S {
            val list = MutableList<Expr>(n) { Expr.ZERO }
            for ((j, value) in c) list[j] = value
            return S(v, list)
        }

        private val xSeries: S = when {
            place.a == null -> exact(-place.q, 0 to num(place.sigma.toLong()))
            isZero(place.a) -> exact(place.q, 0 to num(place.sigma.toLong()))
            else -> exact(0, 0 to place.a, place.q to num(place.sigma.toLong()))
        }

        fun of(e: Expr): S? {
            Budget.check()
            if (!e.has(x)) return exact(0, 0 to e)
            return when (e) {
                is Expr.Sym -> xSeries
                is Expr.Add -> {
                    var sum: S? = null
                    for (t in e.terms) {
                        val s = of(t) ?: return null
                        sum = if (sum == null) s else plus(sum, s)
                    }
                    sum
                }
                is Expr.Mul -> {
                    var product: S? = null
                    for (f in e.factors) {
                        val s = of(f) ?: return null
                        product = if (product == null) s else times(product, s)
                    }
                    product
                }
                is Expr.Pow -> power(e)
                is Expr.Fn -> function(e)
                else -> null
            }
        }

        private fun power(e: Expr.Pow): S? {
            if (e.base == Expr.E) return exp(of(e.exp) ?: return null)
            if (e.exp.has(x)) {
                // bᵘ = e^(u·ln b).
                return exp(of(mul(e.exp, fn(Func.LN, e.base))) ?: return null)
            }
            // A power whose exponent is a letter other than x, (1 + x)^a: the binomial series, where the base starts at a number.
            val r = rational(e.exp) ?: return symbolicPow(of(e.base) ?: return null, e.exp)
            return pow(of(e.base) ?: return null, r)
        }

        /**
         * p^r for an exponent r free of x that is no number (a letter): (c₀(1 + g))^r = c₀^r·Σ binom(r, k)·g^k by Miller's
         * recurrence, where p starts at t⁰ and c₀ is 1 or a positive number (so c₀^r is real).
         */
        private fun symbolicPow(p0: S, rr: Expr): S? {
            val p = normal(p0) ?: return null
            if (p.v != 0 || rr.has(L)) return null
            val a = p.c
            val first = if (isOne(a[0])) Expr.ONE else if (signOf(a[0]) == 1) pow(a[0], rr) else return null
            val b = ArrayList<Expr>()
            b += first
            val inverse = pow(a[0], Expr.MINUS_ONE)
            for (j in 1 until a.size) {
                var s: Expr = Expr.ZERO
                for (k in 1..j) s = add(s, mul(add(mul(add(rr, Expr.ONE), num(k.toLong())), num(-j.toLong())), a[k], b[j - k]))
                b += Algebra.expand(mul(s, num(Rational.ONE / Rational.of(j.toLong())), inverse))
            }
            return S(0, b)
        }

        private fun function(e: Expr.Fn): S? {
            if (e.f == Func.LOG && e.args.size == 2) {
                // log_b u = ln u / ln b.
                return of(mul(fn(Func.LN, e.args[1]), pow(fn(Func.LN, e.args[0]), Expr.MINUS_ONE)))
            }
            if (e.args.size != 1) return null
            val inner = of(e.args[0]) ?: return null
            return when (e.f) {
                Func.LN -> ln(inner)
                Func.ABS -> {
                    val p = normal(inner) ?: return null
                    val s = dominantSign(p.c[0]) ?: return null
                    if (s > 0) p else scale(p, Expr.MINUS_ONE)
                }
                Func.SIGN, Func.FLOOR, Func.CEIL, Func.FACTORIAL, Func.GAMMA, Func.DIGAMMA, Func.ZETA -> null
                else -> analytic(e.f, inner)
            }
        }

        // Arithmetic.

        private fun plus(p: S, q: S): S {
            val v = minOf(p.v, q.v)
            val upto = minOf(p.upto, q.upto)
            return S(v, (v until upto).map { j -> Algebra.expand(add(p.at(j), q.at(j))) })
        }

        private fun times(p0: S, q0: S): S? {
            // A product is known as far as its less precise factor, relative to its first term.
            val p = normal(p0) ?: return lowerBound(p0, q0)
            val q = normal(q0) ?: return lowerBound(q0, p0)
            val len = minOf(p.c.size, q.c.size)
            val out = (0 until len).map { j -> Algebra.expand(add((0..j).map { i -> mul(p.c[i], q.c[j - i]) })) }
            return S(p.v + q.v, out)
        }

        /** A factor known only to vanish to its precision: the product is O(t^(its upto + the other's first exponent)). */
        private fun lowerBound(zeroish: S, other: S): S {
            val first = normal(other)?.v ?: other.upto
            return S(zeroish.upto + first, emptyList())
        }

        private fun scale(p: S, k: Expr) = S(p.v, p.c.map { Algebra.expand(mul(k, it)) })

        /** p with its leading zeros taken off: c₀ ≠ 0; null where every known term is zero. */
        fun normal(p: S): S? {
            val first = p.c.indexOfFirst { !zero(it) }
            if (first < 0) return null
            return S(p.v + first, p.c.drop(first))
        }

        /** pʳ for a rational r: (c₀tᵛ(1 + g))ʳ by Miller's recurrence, where v·r is whole and, for a root, c₀ > 0. */
        private fun pow(p0: S, r: Rational): S? {
            val p = normal(p0) ?: return if (r.signum > 0) S(Math.ceil(p0.upto * r.toDouble()).toInt(), emptyList()) else null
            val vr = Rational.of(p.v.toLong()) * r
            if (!vr.isInteger) return null
            val a = p.c
            if (!r.isInteger && signOf(a[0]) != 1) return null
            val rr = num(r)
            val b = ArrayList<Expr>()
            b += Calculus.tidy(pow(a[0], rr))
            val inverse = pow(a[0], Expr.MINUS_ONE)
            for (j in 1 until a.size) {
                var s: Expr = Expr.ZERO
                for (k in 1..j) s = add(s, mul(add(mul(add(rr, Expr.ONE), num(k.toLong())), num(-j.toLong())), a[k], b[j - k]))
                b += Algebra.expand(mul(s, num(Rational.ONE / Rational.of(j.toLong())), inverse))
            }
            return S(vr.num.toInt(), b)
        }

        /** e^p for p with no negative powers: e^(c₀)·e^g by the recurrence b_j = (1/j)·Σ k·a_k·b_(j−k). */
        private fun exp(p0: S): S? {
            val p = if (p0.v < 0) (normal(p0) ?: return S(0, emptyList())) else p0
            if (p.v < 0) return null
            val a = (0 until p.upto).map { p.at(it) }
            if (a.isEmpty()) return S(0, emptyList())
            val b = ArrayList<Expr>()
            b += Calculus.tidy(pow(Expr.E, a[0]))
            for (j in 1 until a.size) {
                var s: Expr = Expr.ZERO
                for (k in 1..j) s = add(s, mul(num(k.toLong()), a[k], b[j - k]))
                b += Algebra.expand(mul(num(Rational.ONE / Rational.of(j.toLong())), s))
            }
            return S(0, b)
        }

        /** ln p = ln c₀ + v·L + ln(1 + g), where c₀ > 0 (t > 0, so p > 0 near the point). */
        private fun ln(p0: S): S? {
            val p = normal(p0) ?: return null
            val a = p.c
            if (signOf(a[0]) != 1) return null
            val b = ArrayList<Expr>()
            b += Algebra.expand(add(Calculus.tidy(fn(Func.LN, a[0])), mul(num(p.v.toLong()), L)))
            val inverse = pow(a[0], Expr.MINUS_ONE)
            for (j in 1 until a.size) {
                var s: Expr = Expr.ZERO
                for (k in 1 until j) s = add(s, mul(num(k.toLong()), b[k], a[j - k]))
                b += Algebra.expand(mul(inverse, add(a[j], mul(num(Rational.of(-1) / Rational.of(j.toLong())), s))))
            }
            return S(0, b)
        }

        /** f(p) for f analytic at p's constant term y₀: Σ f⁽ᵏ⁾(y₀)/k!·gᵏ with g = p − y₀. */
        private fun analytic(f: Func, p0: S): S? {
            val p = if (p0.v < 0) (normal(p0) ?: return null) else p0
            if (p.v < 0) return null
            if (p.c.isEmpty()) return null
            val y0 = p.at(0)
            if (y0.has(L)) return null
            val g = S(0, (0 until p.upto).map { if (it == 0) Expr.ZERO else p.at(it) })
            val first = g.c.indexOfFirst { !zero(it) }
            // How many powers of g reach the precision: g starts at t^first.
            val terms = if (first <= 0) 1 else (g.c.size + first - 1) / first
            val y = Expr.Sym("\u0001y")
            var d: Expr = fn(f, y)
            val coefficients = ArrayList<Expr>()
            // Si at 0, whose derivatives there are limits (sin y / y): its own series.
            MoreFunctions.seriesAtZero(f, y0, terms)?.let { coefficients += it }
            var factorial = Rational.ONE
            for (k in 0..terms) {
                if (coefficients.size > terms) break
                if (k > 0) {
                    d = Calculus.tidy(Calculus.derivative(d, y))
                    factorial = factorial * Rational.of(k.toLong())
                }
                val value = Calculus.tidy(d.substitute(y, y0))
                // Analytic at y₀: every derivative a finite number there (tan at π/2, asin at 1 are not).
                if (y0.symbols().isEmpty()) {
                    val v = Numeric.value(value)
                    if (v == null || !v.isFinite() || abs(v) > 1e12) return null
                }
                coefficients += mul(num(Rational.ONE / factorial), value)
            }
            var result = S(0, listOf(coefficients[0]) + List(g.c.size - 1) { Expr.ZERO })
            if (first <= 0) return result
            var power: S = S(0, listOf(Expr.ONE) + List(g.c.size - 1) { Expr.ZERO })
            for (k in 1..terms) {
                power = times(power, g) ?: return null
                if (power.v >= g.c.size) break
                result = plus(result, scale(power, coefficients[k]))
            }
            return result
        }
    }
}
