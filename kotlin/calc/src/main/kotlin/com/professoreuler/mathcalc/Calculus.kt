package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/**
 * Derivatives, integrals, limits and sums. Every antiderivative is differentiated back and compared with the integrand
 * before it is given; a definite integral without one is worked out numerically; a limit is compared with values near its
 * point.
 */
object Calculus {

    // ---------------------------------------------------------------------------------------------------------------------
    // Derivatives.

    /**
     * d/dx of [e], every rule, simplified; a product the chain rule gives stays one where that is shorter than multiplied
     * out: d/dx (2x + 1)^5 is 10(2x + 1)^4.
     */
    fun derivative(e: Expr, x: Expr.Sym, order: Int = 1): Expr {
        var d = e
        repeat(order) {
            val raw = diff(d, x)
            val tidied = tidy(raw)
            d = if ((raw is Expr.Mul || raw is Expr.Pow) && !hasDenominator(raw) && Algebra.complexity(raw) < Algebra.complexity(tidied)) raw else tidied
        }
        return d
    }

    private fun hasDenominator(e: Expr): Boolean = when (e) {
        is Expr.Pow -> rational(e.exp)?.let { it.signum < 0 } == true || hasDenominator(e.base) || hasDenominator(e.exp)
        is Expr.Add -> e.terms.any(::hasDenominator)
        is Expr.Mul -> e.factors.any(::hasDenominator)
        is Expr.Fn -> e.args.any(::hasDenominator)
        else -> false
    }

    /** d/dx without tidying: the rules applied. */
    fun diff(e: Expr, x: Expr.Sym): Expr = when {
        !e.has(x) -> Expr.ZERO
        e == x -> Expr.ONE
        e is Expr.Add -> add(e.terms.map { diff(it, x) })
        e is Expr.Mul -> add(e.factors.indices.map { i ->
            mul(e.factors.mapIndexed { j, f -> if (i == j) diff(f, x) else f })
        })
        e is Expr.Pow -> {
            val b = e.base
            val p = e.exp
            when {
                !p.has(x) -> mul(p, pow(b, p - Expr.ONE), diff(b, x))
                !b.has(x) -> mul(e, if (b == Expr.E) Expr.ONE else ln(b), diff(p, x))
                // b^p = e^(p ln b).
                else -> mul(e, add(mul(diff(p, x), ln(b)), mul(p, diff(b, x), pow(b, Expr.MINUS_ONE))))
            }
        }
        e is Expr.Fn -> {
            val u = e.args.last()
            val du = diff(u, x)
            val outer: Expr = when (e.f) {
                Func.SIN -> fn(Func.COS, u)
                Func.COS -> neg(fn(Func.SIN, u))
                Func.TAN -> pow(fn(Func.COS, u), num(-2))
                Func.COT -> neg(pow(fn(Func.SIN, u), num(-2)))
                Func.SEC -> mul(fn(Func.SIN, u), pow(fn(Func.COS, u), num(-2)))
                Func.CSC -> neg(mul(fn(Func.COS, u), pow(fn(Func.SIN, u), num(-2))))
                Func.ASIN -> pow(Expr.ONE - u * u, num(Rational.of(-1) / Rational.of(2)))
                Func.ACOS -> neg(pow(Expr.ONE - u * u, num(Rational.of(-1) / Rational.of(2))))
                Func.ATAN -> pow(Expr.ONE + u * u, Expr.MINUS_ONE)
                Func.ACOT -> neg(pow(Expr.ONE + u * u, Expr.MINUS_ONE))
                Func.SINH -> fn(Func.COSH, u)
                Func.COSH -> fn(Func.SINH, u)
                Func.TANH -> pow(fn(Func.COSH, u), num(-2))
                Func.LN -> pow(u, Expr.MINUS_ONE)
                Func.LOG -> {
                    if (e.args[0].has(x)) return diff(ln(u) / ln(e.args[0]), x)
                    pow(u * ln(e.args[0]), Expr.MINUS_ONE)
                }
                Func.ABS -> u / fn(Func.ABS, u)
                Func.SIGN, Func.FLOOR, Func.CEIL -> Expr.ZERO
                Func.FACTORIAL -> throw CalcException(Failure.UNDEFINED)
                // Γ′ = Γ·ψ
                Func.GAMMA -> mul(fn(Func.GAMMA, u), fn(Func.DIGAMMA, u))
                // erf′ = 2/√π · e^(−u²)
                Func.ERF -> mul(Expr.TWO, pow(Expr.PI, num(Rational.of(-1) / Rational.of(2))), exp(neg(u * u)))
                Func.ERFC -> neg(mul(Expr.TWO, pow(Expr.PI, num(Rational.of(-1) / Rational.of(2))), exp(neg(u * u))))
                // J_n′ = (J_(n−1) − J_(n+1)) / 2
                Func.BESSELJ -> {
                    val n = e.args[0]
                    if (n.has(x)) throw CalcException(Failure.UNDEFINED)
                    mul(Expr.HALF, add(fn(Func.BESSELJ, n - Expr.ONE, u), neg(fn(Func.BESSELJ, n + Expr.ONE, u))))
                }
                // W′ = e^(−W) / (1 + W)
                Func.LAMBERTW -> mul(exp(neg(fn(Func.LAMBERTW, u))), pow(Expr.ONE + fn(Func.LAMBERTW, u), Expr.MINUS_ONE))
                // ψ′ and ζ′ have no form with the functions here.
                Func.DIGAMMA, Func.ZETA -> throw CalcException(Failure.UNDEFINED)
            }
            mul(outer, du)
        }
        e is Expr.Op -> diff(evaluate(e), x)
        else -> Expr.ZERO
    }

    /** A result as a learner writes it: the simplest form found, its fractions on one bar where that is shorter. */
    fun tidy(e: Expr): Expr = try {
        Algebra.simplify(e)
    } catch (x: CalcException) {
        if (x.failure == Failure.TOO_LARGE) e else throw x
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Integrals.

    /** An antiderivative of [e] (without + C), checked by differentiating it back; null where none was found. */
    fun antiderivative(e: Expr, x: Expr.Sym): Expr? {
        val f = integrate(e, x, 0) ?: return null
        val back = diff(f, x)
        if (!Verify.same(back, e)) return null
        return tidy(f)
    }

    private fun integrate(e: Expr, x: Expr.Sym, depth: Int): Expr? {
        if (depth > 8) return null
        if (!e.has(x)) return mul(e, x)
        if (e is Expr.Add) return e.terms.map { integrate(it, x, depth + 1) ?: return integrateWhole(e, x, depth) }.let { add(it) }
        // Factors free of x stand before the integral.
        if (e is Expr.Mul) {
            val (with, without) = e.factors.partition { it.has(x) }
            if (without.isNotEmpty()) return integrate(mul(with), x, depth + 1)?.let { mul(mul(without), it) }
        }
        table(e, x)?.let { return it }
        return integrateWhole(e, x, depth)
    }

    /** The methods for a whole integrand: fractions, substitution, parts, trigonometric powers, multiplying out. */
    private fun integrateWhole(e: Expr, x: Expr.Sym, depth: Int): Expr? {
        rationalFunction(e, x)?.let { return it }
        substitution(e, x, depth)?.let { return it }
        parts(e, x, depth)?.let { return it }
        expSinCos(e, x)?.let { return it }
        trigPowers(e, x, depth)?.let { return it }
        val expanded = Algebra.expand(e)
        if (expanded != e && expanded is Expr.Add) return integrate(expanded, x, depth + 1)
        return null
    }

    /** u = a·x + b with a, b free of x: (a, b), else null. */
    private fun linear(u: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        val c = Solve.coefficientsIn(Algebra.expand(u), x) ?: return null
        if (c.size != 2 || isZero(c[1])) return null
        return c[1] to c[0]
    }

    /** The table of integrals, with a linear argument: (ax+b)ⁿ, e^(ax+b), sin, cos, tan, ln, 1/cos², 1/(1+x²), 1/√(1−x²) … */
    private fun table(e: Expr, x: Expr.Sym): Expr? {
        if (e == x) return x * x / Expr.TWO
        if (e is Expr.Pow) {
            val b = e.base
            val p = e.exp
            if (!p.has(x)) {
                linear(b, x)?.let { (a, _) ->
                    return if (p == Expr.MINUS_ONE) ln(fn(Func.ABS, b)) / a else pow(b, p + Expr.ONE) / (a * (p + Expr.ONE))
                }
                // 1/cos²u, 1/sin²u.
                if (p == num(-2) && b is Expr.Fn && b.f == Func.COS) linear(b.args[0], x)?.let { (a, _) -> return fn(Func.TAN, b.args[0]) / a }
                if (p == num(-2) && b is Expr.Fn && b.f == Func.SIN) linear(b.args[0], x)?.let { (a, _) -> return neg(fn(Func.COT, b.args[0])) / a }
                // 1/(1+x²) (and a² + x²), 1/√(1−x²), 1/√(x²±a²).
                quadraticKernel(b, p, x)?.let { return it }
            }
            if (!b.has(x)) linear(p, x)?.let { (a, _) -> return if (b == Expr.E) e / a else e / (a * ln(b)) }
            return null
        }
        if (e is Expr.Fn) {
            val u = e.args.last()
            val (a, _) = linear(u, x) ?: return null
            return when (e.f) {
                Func.SIN -> neg(fn(Func.COS, u)) / a
                Func.COS -> fn(Func.SIN, u) / a
                Func.TAN -> neg(ln(fn(Func.ABS, fn(Func.COS, u)))) / a
                Func.COT -> ln(fn(Func.ABS, fn(Func.SIN, u))) / a
                Func.SINH -> fn(Func.COSH, u) / a
                Func.COSH -> fn(Func.SINH, u) / a
                Func.LN -> (u * ln(u) - u) / a
                Func.LOG -> (u * ln(u) - u) / (a * ln(e.args[0]))
                Func.ATAN -> (u * fn(Func.ATAN, u) - ln(Expr.ONE + u * u) / Expr.TWO) / a
                Func.ASIN -> (u * fn(Func.ASIN, u) + sqrt(Expr.ONE - u * u)) / a
                Func.ACOS -> (u * fn(Func.ACOS, u) - sqrt(Expr.ONE - u * u)) / a
                Func.ABS -> u * fn(Func.ABS, u) / (Expr.TWO * a)
                else -> null
            }
        }
        return null
    }

    /** 1/(c + d x²) as arctan, 1/√(c − d x²) as arcsin, 1/√(d x² ± c) as a logarithm. */
    private fun quadraticKernel(b: Expr, p: Expr, x: Expr.Sym): Expr? {
        val c = Solve.coefficientsIn(Algebra.expand(b), x) ?: return null
        if (c.size != 3 || !isZero(c[1])) return null
        val k = Numeric.value(c[0]) ?: return null
        val d = Numeric.value(c[2]) ?: return null
        if (p == Expr.MINUS_ONE && k > 0 && d > 0) {
            val r = sqrt(c[0] / c[2])
            return fn(Func.ATAN, x / r) / (c[2] * r)
        }
        if (p == num(Rational.of(-1) / Rational.of(2))) {
            if (k > 0 && d < 0) return fn(Func.ASIN, x * sqrt(neg(c[2]) / c[0])) / sqrt(neg(c[2]))
            if (d > 0) return ln(fn(Func.ABS, x * sqrt(c[2]) + sqrt(b))) / sqrt(c[2])
        }
        return null
    }

    /** A fraction of polynomials in x with rational coefficients: polynomial division and partial fractions. */
    private fun rationalFunction(e: Expr, x: Expr.Sym): Expr? {
        val kernels = Algebra.Kernels()
        val (n0, d0) = Algebra.fraction(e, kernels) ?: return null
        if (!kernels.isEmpty || d0.isConstant) return null
        if ((n0.unknowns + d0.unknowns) != setOf(x.name)) return null
        val g = Algebra.gcd(n0, d0)
        val n = n0.divideExactly(g) ?: n0
        val d = d0.divideExactly(g) ?: d0
        val dc = univariate(d, x.name)
        var nc = univariate(n, x.name)
        // Polynomial part.
        val quotient = ArrayList<Rational>()
        if (nc.size >= dc.size) {
            val (q, r) = divide(nc, dc)
            quotient += q
            nc = r
        }
        val polyPart = add(quotient.withIndex().map { (k, c) -> mul(num(c), pow(x, num(k.toLong()))) })
        val polyIntegral = integrate(polyPart, x, 1) ?: return null
        if (nc.all { it.signum == 0 }) return polyIntegral
        // Partial fractions over the denominator's factors: linear ones (any power), quadratic ones (power one).
        val product = Factor.of(d)
        val factors = product.factors.map { (f, k) -> univariate(f, x.name) to k }
        if (factors.any { (f, k) -> f.size - 1 > 2 || (f.size - 1 == 2 && k > 1) }) return null
        // Unknown coefficients: for (x − r)^k, A₁..A_k; for a quadratic, B x + C.
        data class Part(val factor: List<Rational>, val power: Int, val linearNumerator: Boolean)
        val parts = ArrayList<Part>()
        for ((f, k) in factors) for (j in 1..k) parts += Part(f, j, f.size - 1 == 2)
        val unknowns = parts.sumOf { if (it.linearNumerator) 2 else 1 }
        val degree = dc.size - 1
        if (unknowns != degree) return null
        // Each part's numerator times the rest of the denominator; their sum must be the remainder's numerator.
        val columns = ArrayList<List<Rational>>()
        val dMonic = dc.map { it / product.constant }
        for (part in parts) {
            val rest = divide(dMonic, power(part.factor, part.power)).first
            columns += pad(rest, degree)
            if (part.linearNumerator) columns += pad(listOf(Rational.ZERO) + rest, degree)
        }
        val target = pad(nc.map { it / product.constant }, degree)
        val solution = solveLinear(columns, target) ?: return null
        var result = polyIntegral
        var idx = 0
        for (part in parts) {
            val factorExpr = add(part.factor.withIndex().map { (k, c) -> mul(num(c), pow(x, num(k.toLong()))) })
            if (!part.linearNumerator) {
                val a = num(solution[idx++])
                val lead = num(part.factor[1])
                result = result + if (part.power == 1) a * ln(fn(Func.ABS, factorExpr)) / lead
                    else a * pow(factorExpr, num(1L - part.power)) / (lead * num(1L - part.power))
            } else {
                // (C + B x)/(q + p x + x²): B/2 ln(...) + (C − Bp/2) · 2/√(4q − p²) · arctan((2x + p)/√(4q − p²)).
                val c = solution[idx++]
                val bCoef = solution[idx++]
                val lead = part.factor[2]
                val p = part.factor[1] / lead
                val q = part.factor[0] / lead
                val disc = num(Rational.of(4) * q - p * p)
                val s = sqrt(disc)
                result = result + num(bCoef / (Rational.of(2) * lead)) * ln(factorExpr) +
                    num((c - bCoef * p / Rational.of(2)) / lead) * num(2) / s * fn(Func.ATAN, (num(2) * x + num(p)) / s)
            }
        }
        return result
    }

    private fun univariate(p: Polynomial, x: String): List<Rational> =
        List(max(0, p.degreeIn(x)) + 1) { k -> p.terms[if (k == 0) Monomial.ONE else Monomial(listOf(x to k))] ?: Rational.ZERO }

    private fun divide(a: List<Rational>, b: List<Rational>): Pair<List<Rational>, List<Rational>> {
        val d = b.dropLastWhile { it.signum == 0 }
        val r = a.dropLastWhile { it.signum == 0 }.toMutableList()
        if (r.size < d.size) return listOf(Rational.ZERO) to r
        val q = MutableList(r.size - d.size + 1) { Rational.ZERO }
        while (r.size >= d.size && r.isNotEmpty()) {
            val shift = r.size - d.size
            val k = r.last() / d.last()
            q[shift] = k
            for ((i, c) in d.withIndex()) r[i + shift] = r[i + shift] - c * k
            while (r.isNotEmpty() && r.last().signum == 0) r.removeAt(r.size - 1)
        }
        return q to r
    }

    private fun power(p: List<Rational>, k: Int): List<Rational> {
        var out = listOf(Rational.ONE)
        repeat(k) {
            val next = MutableList(out.size + p.size - 1) { Rational.ZERO }
            for ((i, a) in out.withIndex()) for ((j, b) in p.withIndex()) next[i + j] = next[i + j] + a * b
            out = next
        }
        return out
    }

    private fun pad(p: List<Rational>, n: Int) = List(n) { p.getOrElse(it) { Rational.ZERO } }

    /** Columns · u = target, exactly; null where it has no unique solution. */
    private fun solveLinear(columns: List<List<Rational>>, target: List<Rational>): List<Rational>? {
        val n = columns.size
        val m = target.indices.map { r -> (columns.map { it[r] } + target[r]).toMutableList() }.toMutableList()
        for (col in 0 until n) {
            val p = (col until m.size).firstOrNull { m[it][col].signum != 0 } ?: return null
            m[p] = m[col].also { m[col] = m[p] }
            val lead = m[col][col]
            for (j in 0..n) m[col][j] = m[col][j] / lead
            for (r in m.indices) if (r != col && m[r][col].signum != 0) {
                val k = m[r][col]
                for (j in 0..n) m[r][j] = m[r][j] - k * m[col][j]
            }
        }
        return (0 until n).map { m[it][n] }
    }

    /** ∫ f(g(x)) g′(x) dx = F(g(x)): each inner part g tried; what is left after dividing by g′ must be a function of g alone. */
    private fun substitution(e: Expr, x: Expr.Sym, depth: Int): Expr? {
        val u = Expr.Sym("\u0001w")
        for (g in innerParts(e, x)) {
            if (g == x) continue
            val dg = diff(g, x)
            if (isZero(dg)) continue
            val ratio = try { Algebra.simplify(e / dg) } catch (c: CalcException) { continue }
            val inU = Solve.substituteExact(ratio, g, u)
            val clean = if (inU.has(x)) {
                // x itself may be written through g: x² = g where g = x² … only where g is x to a power.
                continue
            } else inU
            val f = integrate(clean, u, depth + 1) ?: continue
            return Solve.substituteExact(f, u, g).substitute(u, g)
        }
        return null
    }

    /** The parts of [e] a substitution may take: arguments of functions, bases and exponents of powers, with x. */
    private fun innerParts(e: Expr, x: Expr.Sym): List<Expr> {
        val out = LinkedHashSet<Expr>()
        fun walk(t: Expr) {
            when (t) {
                is Expr.Fn -> { if (t.args.last().has(x)) out += t.args.last(); out += t; t.args.forEach(::walk) }
                is Expr.Pow -> {
                    if (t.base.has(x)) out += t.base
                    if (t.exp.has(x)) out += t.exp
                    if (t.base.has(x) && rational(t.exp)?.isInteger == false) out += t
                    walk(t.base); walk(t.exp)
                }
                is Expr.Add -> t.terms.forEach(::walk)
                is Expr.Mul -> t.factors.forEach(::walk)
                else -> Unit
            }
        }
        walk(e)
        return out.filter { it.has(x) }.sortedByDescending { it.size() }
    }

    /**
     * ∫ u dv = uv − ∫ v du: a polynomial times eˣ, sin, cos (the polynomial differentiated until it is gone), and a
     * polynomial times ln, arctan, arcsin (the function differentiated).
     */
    private fun parts(e: Expr, x: Expr.Sym, depth: Int): Expr? {
        if (e !is Expr.Mul && !(e is Expr.Fn)) return null
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        val poly = factors.filter { Solve.coefficientsIn(Algebra.expand(it), x) != null }
        val others = factors.filter { it !in poly }
        if (others.size != 1) return null
        val t = others[0]
        val p = mul(poly)
        val degree = (Solve.coefficientsIn(Algebra.expand(p), x)?.size ?: return null) - 1
        if (degree > 8) return null
        return when {
            // LIATE: ln, arctan, arcsin are differentiated.
            t is Expr.Fn && t.f in setOf(Func.LN, Func.LOG, Func.ATAN, Func.ASIN, Func.ACOS) || (t is Expr.Pow && t.base is Expr.Fn && (t.base as Expr.Fn).f == Func.LN) -> {
                val v = integrate(p, x, depth + 1) ?: return null
                val rest = integrate(Algebra.expand(v * diff(t, x)), x, depth + 1) ?: return null
                t * v - rest
            }
            degree >= 1 -> {
                // Tabular: Σ (−1)^k p^(k) · T_(k+1).
                var sign = 1
                var pk: Expr = p
                var integral: Expr = t
                var sum: Expr = Expr.ZERO
                for (k in 0..degree) {
                    integral = integrate(integral, x, depth + 1) ?: return null
                    sum = sum + num(sign.toLong()) * pk * integral
                    pk = diff(pk, x)
                    sign = -sign
                    if (isZero(pk)) break
                }
                sum
            }
            else -> null
        }
    }

    /** e^(ax)·sin(bx) and e^(ax)·cos(bx), which parts turn in a circle: e^(ax)(a sin bx − b cos bx)/(a² + b²). */
    private fun expSinCos(e: Expr, x: Expr.Sym): Expr? {
        if (e !is Expr.Mul) return null
        val (with, without) = e.factors.partition { it.has(x) }
        if (with.size != 2 || without.isNotEmpty()) return null
        val ex = with.firstOrNull { it is Expr.Pow && it.base == Expr.E } as Expr.Pow? ?: return null
        val tr = with.firstOrNull { it is Expr.Fn && (it.f == Func.SIN || it.f == Func.COS) } as Expr.Fn? ?: return null
        val (a, _) = linear(ex.exp, x) ?: return null
        val (b, _) = linear(tr.args[0], x) ?: return null
        val s = fn(Func.SIN, tr.args[0])
        val c = fn(Func.COS, tr.args[0])
        val denominator = a * a + b * b
        return if (tr.f == Func.SIN) ex * (a * s - b * c) / denominator else ex * (a * c + b * s) / denominator
    }

    /** sinᵐ u cosⁿ u: an odd power gives a substitution, even ones are halved (sin²u = (1 − cos 2u)/2). */
    private fun trigPowers(e: Expr, x: Expr.Sym, depth: Int): Expr? {
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        var u: Expr? = null
        var m = 0
        var n = 0
        val rest = ArrayList<Expr>()
        for (f in factors) {
            val (base, k) = if (f is Expr.Pow) f.base to (integer(f.exp)?.toInt() ?: return null) else f to 1
            if (base is Expr.Fn && (base.f == Func.SIN || base.f == Func.COS)) {
                if (u != null && u != base.args[0]) return null
                u = base.args[0]
                if (base.f == Func.SIN) m += k else n += k
            } else rest += f
        }
        val arg = u ?: return null
        if (rest.any { it.has(x) } || m < 0 || n < 0 || m + n < 2) return null
        val s = fn(Func.SIN, arg)
        val c = fn(Func.COS, arg)
        val rewritten = when {
            m % 2 == 1 -> mul(listOf(s) + rest + pow(Expr.ONE - c * c, num(((m - 1) / 2).toLong())) + pow(c, num(n.toLong())))
            n % 2 == 1 -> mul(listOf(c) + rest + pow(Expr.ONE - s * s, num(((n - 1) / 2).toLong())) + pow(s, num(m.toLong())))
            else -> mul(listOf(pow((Expr.ONE - fn(Func.COS, num(2) * arg)) / Expr.TWO, num((m / 2).toLong())),
                pow((Expr.ONE + fn(Func.COS, num(2) * arg)) / Expr.TWO, num((n / 2).toLong()))) + rest)
        }
        val expanded = Algebra.expand(rewritten)
        if (expanded == e) return null
        return integrate(expanded, x, depth + 1)
    }

    /** ∫ₐᵇ: exactly by an antiderivative where the integrand has no singularity between a and b, numerically otherwise. */
    fun definite(e: Expr, x: Expr.Sym, a: Expr, b: Expr): Definite {
        val av = boundValue(a)
        val bv = boundValue(b)
        val f = antiderivative(e, x)
        if (f != null && av.isFinite() && bv.isFinite() && continuous(e, x, av, bv)) {
            val exact = tidy(f.substitute(x, b) - f.substitute(x, a))
            return Definite(exact, Numeric.value(exact), f)
        }
        if (f != null && (av.isInfinite() || bv.isInfinite()) && continuous(e, x, if (av.isFinite()) av else -1e6, if (bv.isFinite()) bv else 1e6)) {
            val upper = if (bv.isInfinite()) limit(f, x, b, null) else f.substitute(x, b)
            val lower = if (av.isInfinite()) limit(f, x, a, null) else f.substitute(x, a)
            if (upper != null && lower != null && !upper.hasInfinity() && !lower.hasInfinity()) {
                val exact = tidy(upper - lower)
                return Definite(exact, Numeric.value(exact), f)
            }
            if (upper != null && lower != null) return Definite(null, null, f, diverges = true)
        }
        val numeric = Quadrature.integrate({ t -> Numeric.eval(e, mapOf(x.name to t)) }, av, bv)
        return Definite(null, numeric, f, diverges = numeric == null)
    }

    /** A definite integral: exact where it could be, its value, the antiderivative used. */
    class Definite(val exact: Expr?, val value: Double?, val antiderivative: Expr?, val diverges: Boolean = false)

    private fun boundValue(b: Expr): Double = when {
        b == Expr.INF -> Double.POSITIVE_INFINITY
        b == neg(Expr.INF) -> Double.NEGATIVE_INFINITY
        else -> Numeric.value(b) ?: Double.NaN
    }

    /** No singularity between a and b: finite at many points, and no jump through infinity. */
    private fun continuous(e: Expr, x: Expr.Sym, a: Double, b: Double): Boolean {
        if (!a.isFinite() || !b.isFinite()) return false
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        val n = 400
        var last = Double.NaN
        for (k in 0..n) {
            val t = lo + (hi - lo) * k / n
            val v = Numeric.eval(e, mapOf(x.name to t))
            if (!v.isFinite()) return false
            if (last.isFinite() && abs(v - last) > 1e6 * max(1.0, abs(last))) return false
            last = v
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Limits.

    /**
     * lim as x → [a] of [e] ([side] −1 from below, 1 from above, null both): exact where substitution, cancelling,
     * L'Hôpital or the leading terms give it; ±∞; null where it does not exist or Calculate cannot find it.
     */
    fun limit(e: Expr, x: Expr.Sym, a: Expr, side: Int?): Expr? {
        val exact = exactLimit(e, x, a, side, 0)
        val numeric = numericLimit(e, x, a, side)
        if (exact != null) {
            // The exact value must agree with values near the point, where those settle.
            val ev = if (exact == Expr.INF) Double.POSITIVE_INFINITY else if (exact == neg(Expr.INF)) Double.NEGATIVE_INFINITY else Numeric.value(exact)
            if (numeric == null || ev == null || (ev.isInfinite() && numeric.isInfinite() && ev.sign == numeric.sign) ||
                (ev.isFinite() && numeric.isFinite() && abs(ev - numeric) <= 1e-4 * max(1.0, abs(ev)))) return exact
            return null
        }
        return numeric?.let { if (it.isInfinite()) (if (it > 0) Expr.INF else neg(Expr.INF)) else Expr.Real(it) }
    }

    private fun exactLimit(e: Expr, x: Expr.Sym, a: Expr, side: Int?, depth: Int): Expr? {
        if (depth > 6) return null
        if (!e.has(x)) return e
        if (a.hasInfinity()) {
            // ∞/∞ by L'Hôpital in x itself, with e^(−x) as 1/eˣ: x·e^(−x) is x/eˣ, then 1/eˣ → 0. (Through t = 1/x it took
            // more rounds than are allowed.)
            val (top, bottom) = quotientAtInfinity(e, x)
            if (!isOne(bottom) && top.has(x) && bottom.has(x)) {
                val tl = exactLimit(top, x, a, side, depth + 1)
                val bl = exactLimit(bottom, x, a, side, depth + 1)
                val infinite = { v: Expr? -> v == Expr.INF || v == neg(Expr.INF) }
                if (infinite(tl) && infinite(bl)) {
                    val dt = tidy(diff(top, x))
                    val db = tidy(diff(bottom, x))
                    if (!isZero(db)) exactLimit(tidy(dt / db), x, a, side, depth + 1)?.let { return it }
                }
            }
            // x → ±∞ as t → 0⁺ with x = ±1/t.
            val sign = if (a == Expr.INF) 1 else -1
            val t = Expr.Sym("\u0001t")
            val inT = try { e.substitute(x, num(sign.toLong()) / t) } catch (c: CalcException) { return null }
            return exactLimit(tidy(inT), t, Expr.ZERO, 1, depth + 1)
        }
        // Plain substitution, where it is defined and not an indeterminate form.
        try {
            val v = tidy(e.substitute(x, a))
            if (!v.hasInfinity() && Numeric.value(v)?.isFinite() == true) return v
        } catch (c: CalcException) {
            // 1/0, 0/0: below.
        }
        // f^g with both depending on x (1^∞, 0^0, ∞^0): e to the limit of g·ln f.
        if (e is Expr.Pow && e.base.has(x) && e.exp.has(x)) {
            val inner = exactLimit(tidy(e.exp * ln(e.base)), x, a, side, depth + 1) ?: return null
            return when {
                inner == Expr.INF -> Expr.INF
                inner == neg(Expr.INF) -> Expr.ZERO
                else -> tidy(exp(inner))
            }
        }
        // ln u and aᵘ (a > 1, e included) by where u goes: ln u → −∞ as u → 0 (from above, where ln is defined), ∞ as u → ∞;
        // aᵘ → ∞ as u → ∞ and 0 as u → −∞.
        if (e is Expr.Fn && e.f == Func.LN) {
            val inner = exactLimit(e.args[0], x, a, side, depth + 1) ?: return null
            return when {
                inner == Expr.INF -> Expr.INF
                isZero(inner) -> neg(Expr.INF)
                inner.hasInfinity() -> null
                else -> tidy(ln(inner)).takeIf { Numeric.value(it)?.isFinite() == true }
            }
        }
        if (e is Expr.Pow && !e.base.has(x) && e.exp.has(x)) {
            val base = Numeric.value(e.base)
            if (base != null && base > 1) {
                val inner = exactLimit(e.exp, x, a, side, depth + 1) ?: return null
                return when {
                    inner == Expr.INF -> Expr.INF
                    inner == neg(Expr.INF) -> Expr.ZERO
                    inner.hasInfinity() -> null
                    else -> tidy(pow(e.base, inner))
                }
            }
        }
        // A fraction: cancelled, then substituted.
        Algebra.cancel(e)?.let { c ->
            if (c != e) try {
                val v = tidy(c.substitute(x, a))
                if (Numeric.value(v)?.isFinite() == true) return v
            } catch (z: CalcException) {
            }
        }
        // Numerator and denominator: L'Hôpital for 0/0 and ∞/∞; a nonzero over 0 gives ±∞ by the sign near a.
        val (top, bottom) = numeratorDenominator(e)
        if (isOne(bottom)) return null
        // What numerator and denominator tend to: by putting a in exactly, else by values ever nearer it.
        val tv = tendsTo(top, x, a, side) ?: return null
        val bv = tendsTo(bottom, x, a, side) ?: return null
        val zeroTop = tv == 0.0
        val zeroBottom = bv == 0.0
        if (zeroTop && zeroBottom || (tv.isInfinite() && bv.isInfinite())) {
            val dt = tidy(diff(top, x))
            val db = tidy(diff(bottom, x))
            if (isZero(db)) return null
            return exactLimit(tidy(dt / db), x, a, side, depth + 1)
        }
        if (zeroBottom && !zeroTop) {
            val below = Numeric.eval(e, mapOf(x.name to (Numeric.value(a) ?: return null) - 1e-7))
            val above = Numeric.eval(e, mapOf(x.name to (Numeric.value(a) ?: return null) + 1e-7))
            val s = when (side) {
                -1 -> below.sign
                1 -> above.sign
                else -> if (below.sign == above.sign) above.sign else return null
            }
            return if (s > 0) Expr.INF else neg(Expr.INF)
        }
        return null
    }

    /** The value [e] tends to as x → a: exactly 0 where a put in gives 0, ±∞ where it divides by 0, else from values near a. */
    private fun tendsTo(e: Expr, x: Expr.Sym, a: Expr, side: Int?): Double? {
        try {
            val v = tidy(e.substitute(x, a))
            if (isZero(v)) return 0.0
            Numeric.value(v)?.let { if (it.isFinite()) return it }
        } catch (c: CalcException) {
            // 1/0 and the like: ±∞, seen from the values.
        }
        val near = listOf(1e-6, 1e-8).map { valueNear(e, x, a, side, it) ?: return null }
        return when {
            abs(near[1]) < abs(near[0]) * 0.05 && abs(near[1]) < 1e-6 -> 0.0
            abs(near[1]) > abs(near[0]) * 20 -> if (near[1] > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
            else -> near[1]
        }
    }

    /** As [numeratorDenominator], with a power whose exponent in x is negative (e^(−x)) put below as its reciprocal. */
    private fun quotientAtInfinity(e: Expr, x: Expr.Sym): Pair<Expr, Expr> {
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        val top = ArrayList<Expr>()
        val bottom = ArrayList<Expr>()
        for (f in factors) {
            val r = if (f is Expr.Pow) rational(f.exp) else null
            val negativeExponent = f is Expr.Pow && r == null && f.exp.has(x) && !f.base.has(x) &&
                (splitCoefficient(f.exp).first as? Expr.Num)?.value?.signum == -1
            when {
                f is Expr.Pow && r != null && r.signum < 0 -> bottom += pow(f.base, num(-r))
                negativeExponent -> bottom += pow((f as Expr.Pow).base, neg(f.exp))
                else -> top += f
            }
        }
        return mul(top) to mul(bottom)
    }

    private fun numeratorDenominator(e: Expr): Pair<Expr, Expr> {
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        val top = ArrayList<Expr>()
        val bottom = ArrayList<Expr>()
        for (f in factors) {
            val r = if (f is Expr.Pow) rational(f.exp) else null
            if (f is Expr.Pow && r != null && r.signum < 0) bottom += pow(f.base, num(-r)) else top += f
        }
        return mul(top) to mul(bottom)
    }

    private fun valueNear(e: Expr, x: Expr.Sym, a: Expr, side: Int?, distance: Double = 1e-9): Double? {
        val av = Numeric.value(a) ?: return null
        val h = distance * max(1.0, abs(av))
        val v = when (side) {
            -1 -> Numeric.eval(e, mapOf(x.name to av - h))
            else -> Numeric.eval(e, mapOf(x.name to av + h))
        }
        return if (v.isNaN()) null else v
    }

    /** Values at points ever nearer a: where they settle, their limit; ±∞ where they grow without bound. */
    private fun numericLimit(e: Expr, x: Expr.Sym, a: Expr, side: Int?): Double? {
        fun seq(sign: Int): Double? {
            val values = ArrayList<Double>()
            for (k in 2..10) {
                val t = if (a.hasInfinity()) {
                    (if (a == Expr.INF) 1.0 else -1.0) * Math.pow(10.0, k.toDouble())
                } else (Numeric.value(a) ?: return null) + sign * Math.pow(10.0, -k.toDouble())
                val v = Numeric.eval(e, mapOf(x.name to t))
                // 0/0 of rounding, nearer a than the digits reach: the values before it are the ones to go by.
                if (v.isNaN()) break
                values += v
            }
            if (values.size < 3) return null
            // Without bound: each of the last four steps at least three times as far from 0, with one sign.
            val tail = values.takeLast(5)
            if (tail.size == 5 && tail.zipWithNext().all { (p, q) -> abs(q) >= 3 * abs(p) && p.sign == q.sign } &&
                (tail.last().isInfinite() || abs(tail.last()) > 1e6)) return tail.last().sign * Double.POSITIVE_INFINITY
            // Where the step between two values suddenly grows tenfold, rounding has taken over (1 − cos x at x = 10⁻⁸ is 0
            // in a double): only the values before it count.
            var trusted = values.size
            for (j in 1 until values.size - 1) {
                val step = abs(values[j + 1] - values[j])
                if (values[j + 1].isInfinite() || (step > 10 * abs(values[j] - values[j - 1]) && step > 1e-10 * max(1.0, abs(values[j])))) {
                    trusted = j + 1
                    break
                }
            }
            if (trusted < 2) return null
            val last = values[trusted - 1]
            val prev = values[trusted - 2]
            return if (abs(last - prev) <= 1e-5 * max(1.0, abs(last))) last else null
        }
        return when (side) {
            -1 -> seq(-1)
            1 -> seq(1)
            else -> {
                if (a.hasInfinity()) return seq(1)
                val l = seq(-1) ?: return null
                val r = seq(1) ?: return null
                if (l.isInfinite() || r.isInfinite()) return if (l == r) l else null
                if (abs(l - r) <= 1e-4 * max(1.0, abs(l))) (l + r) / 2 else null
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Sums and products.

    /** Σ from [a] to [b] of [e] in [k]: exactly term by term, in closed form (polynomials, geometric), known series. */
    fun sum(e: Expr, k: Expr.Sym, a: Expr, b: Expr): Expr? {
        val av = integer(a)?.toLong()
        val bv = integer(b)?.toLong()
        if (av != null && bv != null && bv - av < 20_000) {
            if (bv < av) return Expr.ZERO
            var s: Expr = Expr.ZERO
            for (j in av..bv) s = s + e.substitute(k, num(j))
            return tidy(s)
        }
        // A polynomial in k: its sum is a polynomial in the upper bound, found from enough values.
        val c = Solve.coefficientsIn(Algebra.expand(e), k)
        if (c != null && c.all { it.isConstant() } && av != null) {
            val n = Expr.Sym("\u0001n")
            val degree = c.size
            val points = (0..degree).map { m ->
                val upper = av + m
                var s: Expr = Expr.ZERO
                for (j in av..upper) s = s + e.substitute(k, num(j))
                (upper.toDouble() to s)
            }
            val poly = interpolate(points.map { num(Rational.of(it.first.toLong())) to it.second }, n)
            if (b == Expr.INF) return if (isZero(Algebra.expand(poly - poly.substitute(n, Expr.ZERO)))) Expr.ZERO else null
            return tidy(Algebra.expand(poly).substitute(n, b))
        }
        // Geometric: c·qᵏ.
        geometric(e, k)?.let { (coefficient, q) ->
            val qv = Numeric.value(q) ?: return null
            if (b == Expr.INF) {
                if (abs(qv) >= 1) return null
                return tidy(coefficient * pow(q, a) / (Expr.ONE - q))
            }
            if (qv == 1.0) return tidy(coefficient * (b - a + Expr.ONE))
            return tidy(coefficient * (pow(q, a) - pow(q, b + Expr.ONE)) / (Expr.ONE - q))
        }
        if (b == Expr.INF && av == 1L) known(e, k)?.let { return it }
        return null
    }

    /** e = c·qᵏ with c and q free of k. */
    private fun geometric(e: Expr, k: Expr.Sym): Pair<Expr, Expr>? {
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        val (with, without) = factors.partition { it.has(k) }
        if (with.size != 1) return null
        val p = with[0] as? Expr.Pow ?: return null
        if (p.base.has(k)) return null
        val (a, b) = Solve.coefficientsIn(Algebra.expand(p.exp), k)?.takeIf { it.size == 2 }?.let { it[1] to it[0] } ?: return null
        return mul(mul(without), pow(p.base, b)) to pow(p.base, a)
    }

    /** Series from k = 1 a learner meets: Σ 1/k² = π²/6, Σ 1/k⁴ = π⁴/90, Σ (−1)^(k+1)/k = ln 2, Σ 1/k! = e − 1. */
    private fun known(e: Expr, k: Expr.Sym): Expr? {
        val table = listOf(
            pow(k, num(-2)) to pow(Expr.PI, num(2)) / num(6),
            pow(k, num(-4)) to pow(Expr.PI, num(4)) / num(90),
            mul(pow(Expr.MINUS_ONE, k + Expr.ONE), pow(k, Expr.MINUS_ONE)) to ln(Expr.TWO),
            pow(fn(Func.FACTORIAL, k), Expr.MINUS_ONE) to Expr.E - Expr.ONE,
        )
        for ((term, value) in table) if (Verify.same(term, e)) return value
        return null
    }

    /** The polynomial in [n] through the points (Lagrange). */
    private fun interpolate(points: List<Pair<Expr, Expr>>, n: Expr.Sym): Expr {
        var out: Expr = Expr.ZERO
        for ((i, pi) in points.withIndex()) {
            var term = pi.second
            for ((j, pj) in points.withIndex()) if (i != j) term = term * (n - pj.first) / (pi.first - pj.first)
            out = out + term
        }
        return Algebra.expand(out)
    }

    /** Π from [a] to [b] of [e] in [k]: exactly term by term, or n! for Π k. */
    fun product(e: Expr, k: Expr.Sym, a: Expr, b: Expr): Expr? {
        val av = integer(a)?.toLong()
        val bv = integer(b)?.toLong()
        if (av != null && bv != null && bv - av < 2_000) {
            var p: Expr = Expr.ONE
            for (j in av..bv) p = p * e.substitute(k, num(j))
            return tidy(p)
        }
        if (e == k && av == 1L) return fn(Func.FACTORIAL, b)
        return null
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Working out the calculus in an expression.

    /** [e] with each derivative, integral, limit, sum and product in it worked out (innermost first). */
    fun evaluate(e: Expr): Expr {
        if (!e.hasOp()) return e
        if (e !is Expr.Op) return e.mapChildren { evaluate(it) }
        val body = evaluate(e.body)
        return when (e.kind) {
            OpKind.DERIVATIVE -> derivative(body, e.variable, integer(e.a ?: Expr.ONE)?.toInt() ?: 1)
            OpKind.INTEGRAL -> if (e.a == null || e.b == null) antiderivative(body, e.variable) ?: throw CalcException(Failure.NO_ANTIDERIVATIVE)
                else definite(body, e.variable, evaluate(e.a), evaluate(e.b)).let { d ->
                    d.exact ?: d.value?.let { Expr.Real(it) } ?: throw CalcException(Failure.DIVERGES)
                }
            OpKind.LIMIT -> limit(body, e.variable, evaluate(e.a ?: Expr.ZERO), e.b?.let { rational(it)?.signum })
                ?: throw CalcException(Failure.NO_LIMIT)
            OpKind.SUM -> sum(body, e.variable, evaluate(e.a ?: Expr.ONE), evaluate(e.b ?: Expr.INF)) ?: throw CalcException(Failure.DIVERGES)
            OpKind.PRODUCT -> product(body, e.variable, evaluate(e.a ?: Expr.ONE), evaluate(e.b ?: Expr.INF)) ?: throw CalcException(Failure.UNREADABLE)
        }
    }
}

/** Adaptive Gauss–Kronrod (7, 15) quadrature, with infinite bounds mapped to a finite interval. */
internal object Quadrature {
    private val XGK = doubleArrayOf(0.991455371120813, 0.949107912342759, 0.864864423359769, 0.741531185599394,
        0.586087235467691, 0.405845151377397, 0.207784955007898, 0.0)
    private val WGK = doubleArrayOf(0.022935322010529, 0.063092092629979, 0.104790010322250, 0.140653259715525,
        0.169004726639267, 0.190350578064785, 0.204432940075298, 0.209482141084728)
    private val WG = doubleArrayOf(0.129484966168870, 0.279705391489277, 0.381830050505119, 0.417959183673469)

    fun integrate(f: (Double) -> Double, a: Double, b: Double): Double? {
        if (a.isNaN() || b.isNaN()) return null
        if (a == b) return 0.0
        if (a > b) return integrate(f, b, a)?.let { -it }
        // ∞ bounds: x = t/(1 − t²) on (−1, 1), or a + t/(1 − t) on (0, 1).
        val g: (Double) -> Double
        val lo: Double
        val hi: Double
        when {
            a.isInfinite() && b.isInfinite() -> { g = { t -> val d = 1 - t * t; f(t / d) * (1 + t * t) / (d * d) }; lo = -1.0; hi = 1.0 }
            b.isInfinite() -> { g = { t -> val d = 1 - t; f(a + t / d) / (d * d) }; lo = 0.0; hi = 1.0 }
            a.isInfinite() -> { g = { t -> val d = 1 - t; f(b - t / d) / (d * d) }; lo = 0.0; hi = 1.0 }
            else -> { g = f; lo = a; hi = b }
        }
        var evaluations = 0
        fun piece(x0: Double, x1: Double, depth: Int): Double? {
            Budget.check()
            val c = (x0 + x1) / 2
            val h = (x1 - x0) / 2
            var kronrod = WGK[7] * g(c)
            var gauss = WG[3] * g(c)
            for (k in 0 until 7) {
                val dx = h * XGK[k]
                val s = g(c - dx) + g(c + dx)
                kronrod += WGK[k] * s
                if (k % 2 == 1) gauss += WG[k / 2] * s
            }
            evaluations += 15
            kronrod *= h
            gauss *= h
            if (!kronrod.isFinite()) return null
            if (abs(kronrod - gauss) <= 1e-10 * max(1.0, abs(kronrod)) || depth > 40 || evaluations > 200_000) {
                return if (depth > 40 && abs(kronrod - gauss) > 1e-4 * max(1.0, abs(kronrod))) null else kronrod
            }
            val left = piece(x0, c, depth + 1) ?: return null
            val right = piece(c, x1, depth + 1) ?: return null
            return left + right
        }
        // Avoid the open ends exactly where the mapping has its poles.
        val eps = if (a.isInfinite() || b.isInfinite()) 1e-12 else 0.0
        return piece(lo + eps, hi - eps, 0)
    }
}
