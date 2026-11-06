package com.professoreuler.mathcalc

/**
 * Power series solutions of linear differential equations Σ pₖ(x)·y⁽ᵏ⁾ = g(x) around an ordinary point x₀ (pₙ(x₀) ≠ 0 and every
 * pₖ/pₙ and g/pₙ with a Taylor series there): y = Σ aⱼ(x − x₀)ʲ. The coefficient of each power of (x − x₀) gives aⱼ₊ₙ from the
 * earlier ones; with polynomial coefficients and g = 0 that is a recurrence in j, shown as one. The initial values fix
 * a₀ … aₙ₋₁ (aⱼ = y⁽ʲ⁾(x₀)/j!); those not given stay free. The series is checked by putting it into the equation: what is left
 * has no power of (x − x₀) below the order the series reaches, and it has the initial values.
 */
object SeriesOde {
    /**
     * The series of [y] in [x] at [point] up to the power [order]: [terms] (coefficient and power of (x − x₀), rising), its free
     * coefficients [free] (a₀, a₁ where no initial values fix them), and, where the coefficients are polynomials and the equation
     * homogeneous, the [recurrence] aⱼ₊ₙ = … in j (indices below 0 stand for 0). [n] is the equation's order.
     */
    class Solution(val y: String, val x: String, val point: Expr, val n: Int, val terms: List<Pair<Expr, Int>>, val order: Int,
        val free: List<String>, val recurrence: Pair<Expr, Expr>?) {
        /** x − x₀ (x where x₀ is 0). */
        val seriesIn: Expr get() = if (isZero(point)) Expr.Sym(x) else add(Expr.Sym(x), neg(point))
    }

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** The letter of a coefficient: a_0, a_1, …; in a recurrence a_j, a_j+2, a_j-1. */
    private fun a(k: Int) = Expr.Sym("a_$k")

    private fun aj(shift: Int) = Expr.Sym(if (shift == 0) "a_j" else if (shift > 0) "a_j+$shift" else "a_j$shift")

    /**
     * Solves [equation] (left − right = 0, linear in [y] and its derivatives) by a power series at [at] (null: where the initial
     * values are, else 0) up to the power [order].
     */
    fun solve(equation: Expr, y: String, x: String, conditions: List<Ode.Condition>, at: Expr?, order: Int): Solution {
        val n = Ode.order(equation, y)
        if (n == 0) throw CalcException(Failure.UNREADABLE)
        if (order < n || order > 24) throw CalcException(Failure.ARGUMENTS)
        // The point: as asked, or where the initial values stand (all at one), else 0.
        val points = conditions.map { it.at }
        if (points.any { p -> !LinAlg.isNull(tidy(p - points[0])) }) throw CalcException(Failure.ARGUMENTS)
        val x0 = at ?: points.firstOrNull() ?: Expr.ZERO
        if (points.isNotEmpty() && !LinAlg.isNull(tidy(points[0] - x0))) throw CalcException(Failure.ARGUMENTS)
        if (x0.symbols().isNotEmpty() || Numeric.value(x0) == null) throw CalcException(Failure.ARGUMENTS)
        val xs = Expr.Sym(x)
        val markers = (0..n).map { Ode.marker(y, it) }
        val e = tidy(Algebra.expand(equation))
        val p = markers.map { tidy(Calculus.derivative(e, it)) }
        if (p.any { c -> markers.any { c.has(it) } }) throw CalcException(Failure.UNSOLVED)
        if (p.any { c -> (c.symbols() - x).isNotEmpty() }) throw CalcException(Failure.UNSOLVED)
        val g = tidy(neg(markers.fold(e) { acc, m -> acc.substitute(m, Expr.ZERO) }))
        if ((g.symbols() - x).isNotEmpty()) throw CalcException(Failure.UNSOLVED)
        if (!OdeSystems.vanishes(e - add(markers.indices.map { mul(p[it], markers[it]) }) + g)) throw CalcException(Failure.UNSOLVED)
        // An ordinary point: pₙ(x₀) ≠ 0, and the coefficients have Taylor series there.
        val lead = try { tidy(p[n].substitute(xs, x0)) } catch (z: CalcException) { if (z.failure == Failure.TOO_LONG) throw z; throw CalcException(Failure.SINGULAR_POINT) }
        if (LinAlg.isNull(lead) || Numeric.value(lead) == null) throw CalcException(Failure.SINGULAR_POINT)
        val coefficients = p.map { taylor(it, xs, x0, order) ?: throw CalcException(Failure.SINGULAR_POINT) }
        val forcing = taylor(g, xs, x0, order) ?: throw CalcException(Failure.SINGULAR_POINT)
        // a₀ … aₙ₋₁ from the initial values, the rest free.
        val given = HashMap<Int, Expr>()
        for (c in conditions) {
            if (c.k >= n || c.k in given) throw CalcException(Failure.ARGUMENTS)
            given[c.k] = tidy(c.value / num(factorial(c.k)))
        }
        val coef = ArrayList<Expr>()
        val free = ArrayList<String>()
        for (k in 0 until n) {
            coef += given[k] ?: a(k).also { free += it.name }
        }
        // The coefficient of tʲ: Σₖ Σᵢ c[k][i]·(j − i + k)!/(j − i)!·a₍ⱼ₋ᵢ₊ₖ₎ = g[j]; the term k = n, i = 0 holds aⱼ₊ₙ.
        for (j in 0..order - n) {
            Budget.check()
            var rest: Expr = forcing.getOrElse(j) { Expr.ZERO }
            for (k in 0..n) for (i in 0..j) {
                if (k == n && i == 0) continue
                val c = coefficients[k].getOrElse(i) { Expr.ZERO }
                if (isZero(c)) continue
                val m = j - i + k
                rest = rest - mul(c, num(falling(m, k)), coef[m])
            }
            coef += tidy(Algebra.expand(rest / (coefficients[n][0] * num(falling(j + n, n)))))
        }
        val terms = coef.mapIndexed { k, c -> tidy(c) to k }.filter { !LinAlg.isNull(it.first) }
        val solution = Solution(y, x, x0, n, terms, order, free, null)
        if (!checks(equation, solution, conditions, y, x)) throw CalcException(Failure.UNSOLVED)
        val recurrence = recurrence(p, g, xs, x0, n)?.takeIf { (_, rhs) -> reproduces(rhs, coef, n, order) }
        return Solution(y, x, x0, n, terms, order, free, recurrence)
    }

    private fun factorial(k: Int): Rational {
        var r = Rational.ONE
        for (j in 2..k) r = r * Rational.of(j.toLong())
        return r
    }

    /** m(m − 1)…(m − k + 1): the factor y⁽ᵏ⁾ brings to aₘ; 0 where m < k. */
    private fun falling(m: Int, k: Int): Rational {
        var r = Rational.ONE
        for (q in 0 until k) r = r * Rational.of((m - q).toLong())
        return r
    }

    /** The Taylor coefficients of [e] at [x0] up to [order], index by power; null where it has no Taylor series there. */
    private fun taylor(e: Expr, x: Expr.Sym, x0: Expr, order: Int): List<Expr>? {
        if (!e.has(x)) return listOf(e) + List(order) { Expr.ZERO }
        val terms = try { Series.taylorTerms(e, x, x0, order) } catch (z: CalcException) { if (z.failure == Failure.TOO_LONG) throw z; null } ?: return null
        if (terms.any { it.second < 0 }) return null
        val out = MutableList<Expr>(order + 1) { Expr.ZERO }
        for ((c, k) in terms) if (k <= order) out[k] = tidy(c)
        return out
    }

    /**
     * The series put into the equation: L[y] − g has no power of (x − x₀) below order − n + 1 (its Taylor coefficients there are 0),
     * and the series has the initial values.
     */
    private fun checks(equation: Expr, s: Solution, conditions: List<Ode.Condition>, y: String, x: String): Boolean {
        val xs = Expr.Sym(x)
        val h = s.seriesIn
        val series = add(s.terms.map { (c, k) -> mul(c, pow(h, num(k.toLong()))) })
        var residual = equation
        var d = series
        for (k in 0..s.n) {
            residual = residual.substitute(Ode.marker(y, k), d)
            d = Calculus.derivative(d, xs)
        }
        val left = taylor(tidy(residual), xs, s.point, s.order - s.n) ?: return false
        if (!left.all { OdeSystems.vanishes(it) }) return false
        return conditions.all { c ->
            var v = series
            repeat(c.k) { v = Calculus.derivative(v, xs) }
            LinAlg.isNull(tidy(Algebra.expand(v.substitute(xs, c.at) - c.value)))
        }
    }

    /**
     * aⱼ₊ₙ in j for polynomial coefficients and g = 0: −Σ c[k][i]·(j − i + k)⋯(j − i + 1)·aⱼ₊ₖ₋ᵢ / (c[n][0]·(j + n)⋯(j + 1)) over
     * the terms but k = n, i = 0. Null for other equations.
     */
    private fun recurrence(p: List<Expr>, g: Expr, xs: Expr.Sym, x0: Expr, n: Int): Pair<Expr, Expr>? {
        if (!LinAlg.isNull(g)) return null
        val t = Expr.Sym("\u0001t")
        val j = Expr.Sym("j")
        val polys = p.map { c ->
            val shifted = tidy(Algebra.expand(c.substitute(xs, add(t, x0))))
            val k = Algebra.Kernels()
            val poly = Algebra.polynomial(shifted, k) ?: return null
            if (!k.isEmpty) return null
            shifted
        }
        val degree = polys.map { c -> (0..12).lastOrNull { d -> !LinAlg.isNull(tidy(coefficientOf(c, t, d))) } ?: 0 }.maxOrNull() ?: 0
        if (degree > 12) return null
        val lead = tidy(coefficientOf(polys[n], t, 0))
        var sum: Expr = Expr.ZERO
        for (k in 0..n) for (i in 0..degree) {
            if (k == n && i == 0) continue
            val c = tidy(coefficientOf(polys[k], t, i))
            if (LinAlg.isNull(c)) continue
            // (j − i + k)(j − i + k − 1)⋯(j − i + 1)
            val ff = mul((0 until k).map { q -> add(j, num((k - i - q).toLong())) })
            sum = sum + mul(c, ff, aj(k - i))
        }
        val denominator = mul(lead, mul((0 until n).map { q -> add(j, num((n - q).toLong())) }))
        val rhs = tidy(neg(sum) / denominator)
        // Each a with its coefficient in j factored: (j − 2)(j + 3)/((j + 1)(j + 2))·aⱼ.
        val shifts = (-12..n).filter { rhs.has(aj(it)) }
        val grouped = add(shifts.map { sh ->
            val c = tidy(Calculus.derivative(rhs, aj(sh)))
            val factored = try { Algebra.factor(Algebra.simplify(c))?.first } catch (z: CalcException) { if (z.failure == Failure.TOO_LONG) throw z; null }
            mul(factored?.takeIf { Verify.same(it, c) } ?: c, aj(sh))
        })
        return aj(n) to (if (OdeSystems.vanishes(grouped - rhs)) grouped else rhs)
    }

    /** The coefficient of t^d in a polynomial. */
    private fun coefficientOf(e: Expr, t: Expr.Sym, d: Int): Expr {
        var c = e
        repeat(d) { c = Calculus.derivative(c, t) }
        return tidy(c.substitute(t, Expr.ZERO) / num(factorial(d)))
    }

    /** Whether the recurrence gives the coefficients worked out term by term, aₙ … a_order (a with an index below 0 as 0). */
    private fun reproduces(rhs: Expr, coef: List<Expr>, n: Int, order: Int): Boolean {
        for (jv in 0..order - n) {
            var v = rhs.substitute(Expr.Sym("j"), num(jv.toLong()))
            for (s in -12..n) {
                val idx = jv + s
                val value = if (idx < 0) Expr.ZERO else coef.getOrNull(idx) ?: continue
                v = v.substitute(aj(s), value)
            }
            if (v.symbols().any { it.startsWith("a_j") }) return false
            if (!LinAlg.isNull(tidy(Algebra.expand(v - coef[jv + n])))) return false
        }
        return true
    }
}
