package com.professoreuler.mathcalc

/**
 * Ordinary differential equations in y(x), written with y′, y″, … (or dy/dx): first order separable, linear, Bernoulli,
 * homogeneous and exact ones; linear ones with constant coefficients of any order (a right-hand side by variation of
 * parameters for the second order) and Cauchy–Euler ones; initial values for the constants. Every solution is put back into
 * the equation before it is shown (an implicit one by implicit differentiation).
 */
object Ode {
    enum class Method { INTEGRATION, SEPARABLE, LINEAR, BERNOULLI, HOMOGENEOUS, EXACT, CONSTANT_COEFFICIENTS, CAUCHY_EULER }

    /**
     * A solution: [explicit] y = …, or [implicit] F(x, y) = 0 where y cannot be put on its own; in the [constants] C, C₁, C₂, …
     * (none left after initial values).
     */
    class Solution(val explicit: Expr?, val implicit: Expr?, val constants: List<String>, val method: Method)

    /** The marker of y's k-th derivative as the parser writes it: y′ is "y'". */
    fun marker(y: String, k: Int): Expr.Sym = Expr.Sym(y + "'".repeat(k))

    /** The highest derivative of [y] in [e]: 0 where none. */
    fun order(e: Expr, y: String): Int = e.symbols().filter { it.startsWith(y) && it.drop(y.length).all { c -> c == '\'' } }
        .maxOfOrNull { it.length - y.length } ?: 0

    private fun tidy(e: Expr) = Calculus.tidy(e)

    private fun c(k: Int?) = Expr.Sym(if (k == null) "C" else "C_$k")

    /** Solves [equation] (left − right = 0) for y(x); null where none of the methods applies. */
    fun solve(equation: Expr, y: String, x: String): Solution? {
        val n = order(equation, y)
        if (n == 0) return null
        val solution = when (n) {
            1 -> firstOrder(equation, y, x)
            else -> linearConstant(equation, y, x, n) ?: cauchyEuler(equation, y, x, n)
        } ?: return null
        return solution.takeIf { holds(equation, it, y, x) }
    }

    /** The solution put back: y and its derivatives into the equation, or y′ = −Fx/Fy of F(x, y) = 0. */
    fun holds(equation: Expr, s: Solution, y: String, x: String): Boolean {
        val xs = Expr.Sym(x)
        val n = order(equation, y)
        s.explicit?.let { f ->
            var e = equation
            var d = f
            for (k in 0..n) {
                e = e.substitute(marker(y, k), d)
                d = Calculus.derivative(d, xs)
            }
            // Exactly 0 once simplified, or at random values (large exponentials leave rounding, not a residual).
            val simplified = tidy(Algebra.expand(e))
            return LinAlg.isNull(simplified) || Verify.same(simplified, Expr.ZERO)
        }
        val implicit = s.implicit ?: return false
        if (n != 1) return false
        val ys = Expr.Sym(y)
        val slope = tidy(neg(Calculus.derivative(implicit, xs)) / Calculus.derivative(implicit, ys))
        // On the curve F = 0 the equation holds: checked where it is, with y found from F = 0 at sample x.
        val e = tidy(equation.substitute(marker(y, 1), slope))
        return sampledOnCurve(e, implicit, x, y, s.constants)
    }

    /** e = 0 at points of F(x, y) = 0: y found by Newton's method from a few starts, at a few x and constants. */
    private fun sampledOnCurve(e: Expr, f: Expr, x: String, y: String, constants: List<String>): Boolean {
        var checked = 0
        for (xv in listOf(0.37, 0.71, 1.13, 1.61)) for (cv in listOf(0.5, 1.3)) {
            val env = HashMap<String, Double>()
            env[x] = xv
            for (k in constants) env[k] = cv
            var yv = 0.6
            var found = false
            for (it in 0 until 60) {
                env[y] = yv
                val fv = Numeric.eval(f, env)
                val h = 1e-6
                env[y] = yv + h
                val fd = (Numeric.eval(f, env) - fv) / h
                if (!fv.isFinite() || !fd.isFinite() || fd == 0.0) break
                val next = yv - fv / fd
                if (kotlin.math.abs(next - yv) < 1e-12) { yv = next; found = true; break }
                yv = next
            }
            if (!found) continue
            env[y] = yv
            val ev = Numeric.eval(e, env)
            if (!ev.isFinite()) continue
            if (kotlin.math.abs(ev) > 1e-6) return false
            checked++
        }
        return checked >= 2
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // First order.

    private fun firstOrder(equation: Expr, y: String, x: String): Solution? {
        val xs = Expr.Sym(x)
        val ys = Expr.Sym(y)
        val y1 = marker(y, 1)
        // A method's solution only where it holds; else the next method (a separable integral may not, Bernoulli may).
        fun ok(s: Solution?) = s?.takeIf { holds(equation, it, y, x) }
        // Exact first: M + N·y′ = 0 with ∂M/∂y = ∂N/∂x (and the equation linear in y′).
        ok(exact(equation, ys, xs, y1))?.let { return it }
        val slopes = Solve.equation(equation, Expr.ZERO, y1)
        val f = slopes.values.singleOrNull()?.let { tidy(it) } ?: return null
        if (f.has(y1)) return null
        if (!f.has(ys)) {
            val g = Calculus.antiderivative(f, xs) ?: return null
            return Solution(tidy(g + c(null)), null, listOf("C"), Method.INTEGRATION)
        }
        // Linear in y first (y′ = −2y + 4 is y = 2 + Ce^(−2x), not an implicit logarithm), then separable.
        if (!tidy(Calculus.derivative(f, ys)).has(ys)) ok(linear(f, ys, xs))?.let { return it }
        ok(separable(f, ys, xs))?.let { return it }
        ok(linear(f, ys, xs))?.let { return it }
        ok(bernoulli(f, ys, xs))?.let { return it }
        ok(homogeneous(f, ys, xs))?.let { return it }
        return null
    }

    /** f(x, y) as g(x)·h(y), where it is one: (g, h). */
    private fun splitProduct(f: Expr, ys: Expr.Sym, xs: Expr.Sym): Pair<Expr, Expr>? {
        val factored = Algebra.factor(f)?.first ?: f
        for (form in listOf(f, factored, tidy(Algebra.expand(f)))) {
            val factors = if (form is Expr.Mul) form.factors else listOf(form)
            if (factors.any { it.has(xs) && it.has(ys) }) {
                // e^(x + y) is e^x·e^y.
                val split = factors.flatMap { u ->
                    if (u is Expr.Pow && u.base == Expr.E && u.exp is Expr.Add) (u.exp as Expr.Add).terms.map { exp(it) } else listOf(u)
                }
                if (split.any { it.has(xs) && it.has(ys) }) continue
                return mul(split.filter { !it.has(ys) }) to mul(split.filter { it.has(ys) })
            }
            return mul(factors.filter { !it.has(ys) }) to mul(factors.filter { it.has(ys) })
        }
        return null
    }

    private fun separable(f: Expr, ys: Expr.Sym, xs: Expr.Sym): Solution? {
        val (g, h) = splitProduct(f, ys, xs) ?: return null
        // ∫ dy/h(y) = ∫ g(x) dx + C.
        val hInverse = tidy(pow(h, Expr.MINUS_ONE))
        val left = Calculus.antiderivative(hInverse, ys) ?: return null
        val right = Calculus.antiderivative(g, xs) ?: return null
        // a·ln|y| = G + C: y = C·e^(G/a).
        val k = Calculus.tidy(left / ln(fn(Func.ABS, ys)))
        if (!k.has(ys) && !isZero(k)) {
            return Solution(tidy(mul(c(null), exp(tidy(right / k)))), null, listOf("C"), Method.SEPARABLE)
        }
        val implicit = tidy(left - right - c(null))
        val solved = Solve.equation(implicit, Expr.ZERO, ys).values
        if (solved.size == 1) return Solution(tidy(solved[0]), null, listOf("C"), Method.SEPARABLE)
        return Solution(null, implicit, listOf("C"), Method.SEPARABLE)
    }

    /** y′ = −p(x)·y + q(x): y = (∫ μ·q dx + C)/μ with μ = e^(∫p dx). */
    private fun linear(f: Expr, ys: Expr.Sym, xs: Expr.Sym): Solution? {
        val slope = tidy(Calculus.derivative(f, ys))
        if (slope.has(ys)) return null
        val p = tidy(neg(slope))
        val q = tidy(f.substitute(ys, Expr.ZERO))
        val integral = Calculus.antiderivative(p, xs) ?: return null
        // Any multiple of the integrating factor will do: e^(ln|x|) as x.
        val mu = tidy(withoutAbs(tidy(exp(integral))))
        val inner = if (isZero(q)) Expr.ZERO else (Calculus.antiderivative(tidy(mu * q), xs) ?: return null)
        return Solution(tidy((inner + c(null)) / mu), null, listOf("C"), Method.LINEAR)
    }

    private fun withoutAbs(e: Expr): Expr = if (e is Expr.Fn && e.f == Func.ABS) withoutAbs(e.args[0]) else e.mapChildren(::withoutAbs)

    /** A sum of products of sines and cosines as the shortest of itself and its sum form: (1/3)sin x, not four products. */
    private fun shortTrig(e: Expr): Expr {
        if (!TrigForms.has(e)) return e
        val sums = tidy(Algebra.expand(TrigForms.productsToSums(Algebra.expand(e))))
        return if (sums.size() < e.size()) sums else e
    }

    /** y′ = −p·y + q·yⁿ: v = y^(1−n) makes it linear. */
    private fun bernoulli(f: Expr, ys: Expr.Sym, xs: Expr.Sym): Solution? {
        // Its terms, also where they stand on one bar: (x²y² − y)/x is x·y² − y/x.
        val expanded = tidy(Algebra.expand(f))
        val sum = (expanded as? Expr.Mul)?.factors?.firstOrNull { it is Expr.Add }
        val terms = when {
            expanded is Expr.Add -> expanded.terms
            sum != null -> {
                val rest = mul((expanded as Expr.Mul).factors.filter { it !== sum })
                (sum as Expr.Add).terms.map { tidy(Algebra.expand(mul(rest, it))) }
            }
            else -> listOf(expanded)
        }
        var p: Expr = Expr.ZERO
        var q: Expr = Expr.ZERO
        var n: Rational? = null
        for (t in terms) {
            val factors = if (t is Expr.Mul) t.factors else listOf(t)
            val power = factors.firstOrNull { it.has(ys) } ?: return null
            if (factors.count { it.has(ys) } != 1) return null
            val k = when {
                power == ys -> Rational.ONE
                power is Expr.Pow && power.base == ys -> rational(power.exp) ?: return null
                else -> return null
            }
            val rest = mul(factors.filter { it !== power })
            if (rest.has(ys)) return null
            if (k == Rational.ONE) p = p - rest else {
                if (n != null && n != k) return null
                n = k
                q = q + rest
            }
        }
        val power = n ?: return null
        if (power == Rational.ZERO) return null
        val m = Rational.ONE - power
        val vs = Expr.Sym("\u0001v")
        // v′ = (1 − n)(−p v + q)
        val linear = linear(tidy(mul(num(m), add(neg(mul(p, vs)), q))), vs, xs) ?: return null
        val v = linear.explicit ?: return null
        return Solution(tidy(pow(v, num(Rational.ONE / m))), null, listOf("C"), Method.BERNOULLI)
    }

    /** y′ = F(y/x): with v = y/x, x·v′ = F(v) − v, separable. */
    private fun homogeneous(f: Expr, ys: Expr.Sym, xs: Expr.Sym): Solution? {
        val t = Expr.Sym("\u0001t")
        if (!Verify.same(f.substitute(xs, mul(t, xs)).substitute(ys, mul(t, ys)), f)) return null
        val vs = Expr.Sym("\u0001v")
        val fv = tidy(f.substitute(ys, mul(vs, xs)))
        if (fv.has(xs)) return null
        val denominator = tidy(fv - vs)
        if (isZero(denominator)) return null
        val left = Calculus.antiderivative(tidy(pow(denominator, Expr.MINUS_ONE)), vs) ?: return null
        // ∫ dv/(F(v) − v) = ln|x| + C, with v = y/x.
        val implicit = tidy((left - ln(fn(Func.ABS, xs)) - c(null)).substitute(vs, ys / xs))
        val solved = Solve.equation(implicit, Expr.ZERO, ys).values
        if (solved.size == 1) return Solution(tidy(solved[0]), null, listOf("C"), Method.HOMOGENEOUS)
        return Solution(null, implicit, listOf("C"), Method.HOMOGENEOUS)
    }

    /** M(x, y) + N(x, y)·y′ = 0 with ∂M/∂y = ∂N/∂x: Φ(x, y) = C with Φ_x = M and Φ_y = N. */
    private fun exact(equation: Expr, ys: Expr.Sym, xs: Expr.Sym, y1: Expr.Sym): Solution? {
        val expanded = tidy(Algebra.expand(equation))
        val nCoef = tidy(Calculus.derivative(expanded, y1))
        if (nCoef.has(y1) || isZero(nCoef) || !nCoef.has(ys) && !nCoef.has(xs)) return null
        val m = tidy(expanded.substitute(y1, Expr.ZERO))
        if (!m.has(ys) || !nCoef.has(xs)) return null
        if (!Verify.same(Calculus.derivative(m, ys), Calculus.derivative(nCoef, xs))) return null
        val phiX = Calculus.antiderivative(m, xs) ?: return null
        val rest = tidy(nCoef - Calculus.derivative(phiX, ys))
        if (rest.has(xs)) return null
        val h = if (isZero(rest)) Expr.ZERO else (Calculus.antiderivative(rest, ys) ?: return null)
        val phi = tidy(phiX + h)
        return Solution(null, tidy(phi - c(null)), listOf("C"), Method.EXACT)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Linear, of higher order.

    /** The coefficients a₀ … aₙ of y, y′, …, y⁽ⁿ⁾ and the rest g, where the equation is linear in them: Σ aₖ·y⁽ᵏ⁾ = g. */
    private fun linearCoefficients(equation: Expr, y: String, n: Int): Pair<List<Expr>, Expr>? {
        val e = tidy(Algebra.expand(equation))
        val markers = (0..n).map { marker(y, it) }
        val coefficients = markers.map { tidy(Calculus.derivative(e, it)) }
        if (coefficients.any { cf -> markers.any { cf.has(it) } }) return null
        val g = tidy(neg(markers.fold(e) { acc, m -> acc.substitute(m, Expr.ZERO) }))
        return coefficients to g
    }

    /** Σ aₖ·y⁽ᵏ⁾ = g with constant aₖ: the characteristic polynomial's roots, then a particular solution for g. */
    private fun linearConstant(equation: Expr, y: String, x: String, n: Int): Solution? {
        val (a, g) = linearCoefficients(equation, y, n) ?: return null
        if (a.any { it.symbols().isNotEmpty() }) return null
        val r = Expr.Sym("\u0001r")
        val characteristic = tidy(add(a.indices.map { k -> mul(a[k], pow(r, num(k.toLong()))) }))
        val basis = basisFromRoots(characteristic, r, n, Expr.Sym(x)) { root, power, xs ->
            mul(pow(xs, num(power.toLong())), exp(mul(root, xs)))
        } ?: return null
        val homogeneous = add(basis.mapIndexed { k, b -> mul(c(k + 1), b) })
        val particular = if (isZero(g)) Expr.ZERO else shortTrig(variationOfParameters(basis, a[n], g, Expr.Sym(x)) ?: return null)
        return Solution(tidy(homogeneous + particular), null, basis.indices.map { "C_${it + 1}" }, Method.CONSTANT_COEFFICIENTS)
    }

    /**
     * The real basis from the roots of [p] in [r]: for a real root ρ of multiplicity m the functions of [real] with powers 0 … m − 1;
     * for α ± βi, xʲ·e^(αx)·cos βx and xʲ·e^(αx)·sin βx. Null where the roots are not all found exactly.
     */
    private fun basisFromRoots(p: Expr, r: Expr.Sym, n: Int, xs: Expr.Sym, real: (Expr, Int, Expr.Sym) -> Expr): List<Expr>? {
        val roots = Solve.equation(p, Expr.ZERO, r)
        if (roots.numeric) return null
        val out = ArrayList<Expr>()
        fun multiplicity(root: Expr): Int {
            var m = 0
            var d = p
            while (m < n && LinAlg.isNull(tidy(d.substitute(r, root)))) { m++; d = Calculus.derivative(d, r) }
            return m
        }
        for (root in roots.values) {
            val m = multiplicity(root)
            for (j in 0 until m) out += real(root, j, xs)
        }
        // Complex roots in pairs: one of each (the positive imaginary part).
        for (root in roots.complex) {
            val (alpha, beta) = Complex.parts(root) ?: return null
            if (beta == null) return null
            val bv = Numeric.value(beta) ?: return null
            if (bv <= 0) continue
            val m = multiplicity(root)
            for (j in 0 until m) {
                val front = mul(pow(xs, num(j.toLong())), exp(mul(alpha, xs)))
                out += tidy(mul(front, fn(Func.COS, mul(beta, xs))))
                out += tidy(mul(front, fn(Func.SIN, mul(beta, xs))))
            }
        }
        return if (out.size == n) out.map { tidy(it) } else null
    }

    /** y_p = −y₁∫ y₂g/(aW) + y₂∫ y₁g/(aW) for a second-order equation with leading coefficient a and Wronskian W. */
    private fun variationOfParameters(basis: List<Expr>, lead: Expr, g: Expr, xs: Expr.Sym): Expr? {
        if (basis.size != 2) return null
        val (y1, y2) = basis
        val w = tidy(y1 * Calculus.derivative(y2, xs) - Calculus.derivative(y1, xs) * y2)
        if (isZero(w)) return null
        val u1 = Calculus.antiderivative(tidy(neg(y2 * g) / (lead * w)), xs) ?: return null
        val u2 = Calculus.antiderivative(tidy(y1 * g / (lead * w)), xs) ?: return null
        return tidy(Algebra.expand(u1 * y1 + u2 * y2))
    }

    /** a·x²y″ + b·x·y′ + c·y = 0: x^r with a·r(r − 1) + b·r + c = 0 (x^r ln x for a double root, x^α cos(β ln x) …). */
    private fun cauchyEuler(equation: Expr, y: String, x: String, n: Int): Solution? {
        if (n != 2) return null
        val (a, g) = linearCoefficients(equation, y, n) ?: return null
        val xs = Expr.Sym(x)
        val lead = tidy(a[2] / pow(xs, Expr.TWO))
        val mid = tidy(a[1] / xs)
        val last = a[0]
        if (listOf(lead, mid, last).any { it.symbols().isNotEmpty() } || !isZero(g)) return null
        val r = Expr.Sym("\u0001r")
        val characteristic = tidy(lead * r * (r - Expr.ONE) + mid * r + last)
        val roots = Solve.equation(characteristic, Expr.ZERO, r)
        if (roots.numeric) return null
        val basis = ArrayList<Expr>()
        val lnx = ln(xs)
        if (roots.values.size == 1) {
            basis += pow(xs, roots.values[0])
            basis += mul(pow(xs, roots.values[0]), lnx)
        } else if (roots.values.size == 2) {
            roots.values.forEach { basis += pow(xs, it) }
        } else {
            val root = roots.complex.firstOrNull { (Complex.parts(it)?.second?.let { b -> Numeric.value(b) } ?: 0.0) > 0 } ?: return null
            val (alpha, beta) = Complex.parts(root) ?: return null
            basis += mul(pow(xs, alpha), fn(Func.COS, mul(beta!!, lnx)))
            basis += mul(pow(xs, alpha), fn(Func.SIN, mul(beta, lnx)))
        }
        if (basis.size != 2) return null
        return Solution(tidy(add(basis.mapIndexed { k, b -> mul(c(k + 1), tidy(b)) })), null, listOf("C_1", "C_2"), Method.CAUCHY_EULER)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Initial values.

    /** An initial value: y⁽ᵏ⁾(x₀) = v. */
    class Condition(val k: Int, val at: Expr, val value: Expr)

    /** The constants that the [conditions] fix: the particular solution, checked in the equation and the conditions. */
    fun withConditions(equation: Expr, s: Solution, conditions: List<Condition>, y: String, x: String): Solution? {
        val f = s.explicit ?: return null
        if (conditions.size != s.constants.size) return null
        val xs = Expr.Sym(x)
        val equations = conditions.map { cond ->
            var d = f
            repeat(cond.k) { d = Calculus.derivative(d, xs) }
            tidy(d.substitute(xs, cond.at) - cond.value)
        }
        val values = Solve.system(equations, s.constants)?.singleOrNull() ?: return null
        var particular = f
        for ((name, v) in values) particular = particular.substitute(Expr.Sym(name), v)
        val result = Solution(tidy(particular), null, emptyList(), s.method)
        if (!holds(equation, result, y, x)) return null
        val satisfied = conditions.all { cond ->
            var d = result.explicit!!
            repeat(cond.k) { d = Calculus.derivative(d, xs) }
            LinAlg.isNull(tidy(d.substitute(xs, cond.at) - cond.value))
        }
        return if (satisfied) result else null
    }
}
