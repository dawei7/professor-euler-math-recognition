package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * The continuous Fourier transform in its angular form, as physics and engineering write it and as the Laplace transform is at
 * s = iω: F(ω) = ∫ f(t)·e^(−iωt) dt over ℝ, and back f(t) = (1/(2π))·∫ F(ω)·e^(iωt) dω. (The other convention, with 2π in the
 * exponent, F(ξ) = ∫ f(t)·e^(−2πiξt) dt, is this F at ω = 2πξ.)
 *
 * f is taken apart by linearity into terms, each worked out by a rule or a table pair:
 * - pieces of exponential polynomials, c·tⁿ·e^(μt) between the points where a sgn or an |…| of f changes (e^(−a|t|), a one-sided
 *   e^(−at)·(1 + sgn t)/2, a rectangle (sgn(t + 1) − sgn(t − 1))/2, a triangle), each integrated exactly over its piece;
 * - the Gaussian tⁿ·e^(−at² + bt), ∫ e^(−at² + st) dt = √(π/a)·e^(s²/(4a)) and its derivatives in s;
 * - the Lorentzian 1/((t − t₀)² + κ²) ↔ (π/κ)·e^(−κ|ω|)·e^(−iωt₀), and t times it by the derivative rule;
 * - e^(iλt)/t ↔ −iπ·sgn(ω − λ) (principal values, whose sum is a true integral where their coefficients add up to 0, as for
 *   sin(at)/t ↔ (π/2)(sgn(ω + a) − sgn(ω − a)));
 * - 1/cosh(at) ↔ (π/a)/cosh(πω/(2a));
 * cos and sin of f as exponentials, so a factor cos(bt) shifts ω by ±b (modulation). The inverse transform is the transform with
 * ω as its letter, read at −t and divided by 2π. Every result is checked numerically at points: the integral of f·e^(−iωt) where
 * f falls off fast, else the inverse integral of F where F does.
 */
internal object FourierTransform {
    val OMEGA = Expr.Sym("\\omega")
    private val T = Expr.Sym("t")
    /** The transform's letter while the inverse is worked out. */
    private val S = Expr.Sym("\u0000s")
    private val SIGMA = Expr.Sym("\u0000σ")

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** fouriertransform(f), fouriertransform(f, t), invfouriertransform(F), invfouriertransform(F, ω). */
    fun call(r: MultiCalls.Reader, inverse: Boolean): Outcome {
        val given = if (r.pieces.size == 2) r.letter(1) else null
        val f = r.expr(0, setOfNotNull(given?.name))
        if (f is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val letters = f.symbols()
        val wanted = if (inverse) T else OMEGA
        val usual = if (inverse) OMEGA else T
        val x = given ?: when {
            usual.name in letters -> usual
            letters.size == 1 -> Expr.Sym(letters.single())
            letters.isEmpty() -> usual
            else -> throw CalcException(Failure.ARGUMENTS)
        }
        if (x == wanted || wanted.name in letters) throw CalcException(Failure.ARGUMENTS)
        return Outcome.Expression(if (inverse) inverse(f, x, wanted) else transform(f, x, wanted))
    }

    /** F(ω) of f(t), checked. */
    fun transform(f: Expr, t: Expr.Sym, w: Expr.Sym): Expr {
        val raw = forward(f, t, w)
        val result = polished(raw)
        if (!checked(f, t, result, w, inverse = false)) throw CalcException(Failure.UNSOLVED)
        return result
    }

    /** f(t) of F(ω), checked. */
    fun inverse(big: Expr, w: Expr.Sym, t: Expr.Sym): Expr {
        val raw = forward(big, w, S)
        val result = polished(tidy(raw.substitute(S, neg(t)) / (Expr.TWO * Expr.PI)))
        if (!checked(big, w, result, t, inverse = true)) throw CalcException(Failure.UNSOLVED)
        return result
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The transform term by term.

    /** ∫ f(t)·e^(−iwt) dt by the rules; fails where a term fits none of them. */
    fun forward(f: Expr, t: Expr.Sym, w: Expr.Sym): Expr {
        val g = Algebra.expand(exponentials(Calculus.tidy(f), t, false))
        if (isZero(g)) return Expr.ZERO
        val terms = if (g is Expr.Add) g.terms else listOf(g)
        val pieces = ArrayList<Expr>()
        val reciprocal = ArrayList<Pair<Expr, Expr>>()
        val out = ArrayList<Expr>()
        for (term in terms) {
            Budget.check()
            val k = Kind.of(term, t) ?: throw CalcException(Failure.UNSOLVED)
            when (k.kind) {
                Kind.PIECES -> pieces += term
                Kind.GAUSS -> out += gaussian(k, w)
                Kind.LORENTZ -> out += lorentzian(k, t, w)
                Kind.RECIPROCAL -> reciprocal += k.coefficient to k.lambda
                Kind.SECH -> out += sech(k, t, w)
                Kind.POLE -> out += pole(k, t, w)
            }
        }
        if (pieces.isNotEmpty()) out += piecewise(add(pieces), t, w)
        if (reciprocal.isNotEmpty()) {
            // Σ c·e^(iλt)/t is integrable at 0 only where Σ c = 0.
            if (!LinAlg.isNull(tidy(add(reciprocal.map { it.first })))) throw CalcException(Failure.UNSOLVED)
            for ((c, lambda) in reciprocal) out += mul(c, num(-1), Expr.I, Expr.PI, fn(Func.SIGN, w - lambda))
        }
        return tidy(add(out))
    }

    /** cos, sin, cosh and sinh of t as exponentials, where they are no denominator (1/cosh t is a table function). */
    private fun exponentials(e: Expr, t: Expr.Sym, below: Boolean): Expr {
        if (!e.has(t)) return e
        if (e is Expr.Pow) {
            val n = rational(e.exp)
            val under = n != null && n.signum < 0
            return pow(exponentials(e.base, t, under), e.exp)
        }
        val inner = e.mapChildren { exponentials(it, t, below) }
        if (inner !is Expr.Fn || inner.args.size != 1 || below) return inner
        val u = inner.args[0]
        val iu = mul(Expr.I, u)
        return when (inner.f) {
            Func.COS -> mul(Expr.HALF, add(exp(iu), exp(neg(iu))))
            Func.SIN -> mul(Expr.HALF, neg(Expr.I), add(exp(iu), neg(exp(neg(iu)))))
            Func.COSH -> mul(Expr.HALF, add(exp(u), exp(neg(u))))
            Func.SINH -> mul(Expr.HALF, add(exp(u), neg(exp(neg(u)))))
            else -> inner
        }
    }

    /** What a term is: its coefficient, its power of t, its exponent, and the table function it holds. */
    private class Kind(val kind: Int, val coefficient: Expr, val n: Int, val exponent: List<Expr>, val lambda: Expr, val kernel: Expr?,
        /** The order m of a pole 1/(t − p)ᵐ. */
        val order: Int = 1) {
        companion object {
            const val PIECES = 0
            const val GAUSS = 1
            const val LORENTZ = 2
            const val RECIPROCAL = 3
            const val SECH = 4
            const val POLE = 5

            fun of(term: Expr, t: Expr.Sym): Kind? {
                val factors = if (term is Expr.Mul) term.factors else listOf(term)
                var c: Expr = Expr.ONE
                var n = 0
                var exponent: Expr = Expr.ZERO
                var piecewise = false
                var reciprocal = false
                var kernel: Expr? = null
                var sech = false
                var order = 0
                for (f in factors) {
                    when {
                        !f.has(t) -> c = mul(c, f)
                        f == t -> n += 1
                        f is Expr.Pow && f.base == t && integer(f.exp) != null -> {
                            val k = integer(f.exp)!!.toInt()
                            if (k == -1 && !reciprocal) reciprocal = true else if (k >= 0) n += k else return null
                        }
                        f is Expr.Pow && f.base == Expr.E -> {
                            if (hasPieces(f.exp, t)) piecewise = true
                            exponent = add(exponent, f.exp)
                        }
                        f is Expr.Fn && (f.f == Func.SIGN || f.f == Func.ABS) -> piecewise = true
                        f is Expr.Pow && f.base is Expr.Fn && (f.base as Expr.Fn).f == Func.ABS && integer(f.exp)?.signum() == 1 -> piecewise = true
                        f is Expr.Pow && f.exp == Expr.MINUS_ONE && kernel == null && f.base is Expr.Fn && (f.base as Expr.Fn).f == Func.COSH -> {
                            kernel = (f.base as Expr.Fn).args[0]
                            sech = true
                        }
                        f is Expr.Pow && f.exp == Expr.MINUS_ONE && kernel == null && quadratic(f.base, t) != null -> kernel = f.base
                        f is Expr.Pow && kernel == null && integer(f.exp)?.let { it.signum() < 0 && it.bitLength() < 5 } == true && pole(f.base, t) != null -> {
                            val (_, m, lead) = pole(f.base, t)!!
                            val k = -integer(f.exp)!!.toInt()
                            kernel = f.base
                            order = m * k
                            c = mul(c, pow(lead, num(-k.toLong())))
                        }
                        else -> return null
                    }
                }
                if (piecewise) {
                    if (kernel != null || reciprocal) return null
                    return Kind(PIECES, c, n, emptyList(), Expr.ZERO, null)
                }
                val coefficients = Solve.coefficientsIn(Algebra.expand(exponent), t) ?: return null
                if (coefficients.size > 3) return null
                val c0 = coefficients.getOrElse(0) { Expr.ZERO }
                val c1 = coefficients.getOrElse(1) { Expr.ZERO }
                val c2 = coefficients.getOrElse(2) { Expr.ZERO }
                val whole = mul(c, exp(c0))
                if (!isZero(c2)) {
                    if (kernel != null || reciprocal) return null
                    return Kind(GAUSS, whole, n, listOf(c0, c1, c2), Expr.ZERO, null)
                }
                // e^(iλt) with λ real: a shift of ω.
                val lambda = imaginary(c1)
                if (reciprocal || kernel != null) {
                    lambda ?: return null
                    return when {
                        reciprocal && kernel == null && n == 0 -> Kind(RECIPROCAL, whole, 0, emptyList(), lambda, null)
                        order > 0 && !reciprocal && n == 0 -> Kind(POLE, whole, 0, emptyList(), lambda, kernel, order)
                        sech && !reciprocal -> Kind(SECH, whole, n, emptyList(), lambda, kernel)
                        kernel != null && !reciprocal -> Kind(LORENTZ, whole, n, emptyList(), lambda, kernel)
                        else -> null
                    }
                }
                return Kind(PIECES, c, n, emptyList(), Expr.ZERO, null)
            }

            private fun hasPieces(e: Expr, t: Expr.Sym): Boolean = when (e) {
                is Expr.Fn -> ((e.f == Func.ABS || e.f == Func.SIGN) && e.has(t)) || e.args.any { hasPieces(it, t) }
                is Expr.Add -> e.terms.any { hasPieces(it, t) }
                is Expr.Mul -> e.factors.any { hasPieces(it, t) }
                is Expr.Pow -> hasPieces(e.base, t) || hasPieces(e.exp, t)
                else -> false
            }
        }
    }

    /** b where [e] is i·b with b a real number (0 for 0); null otherwise. */
    private fun imaginary(e: Expr): Expr? {
        if (isZero(e)) return Expr.ZERO
        val (re, im) = Complex.parts(e) ?: return null
        if (!LinAlg.isNull(re) || im == null || Numeric.value(im) == null) return null
        return tidy(im)
    }

    /** q₂t² + q₁t + q₀ with real numbers, q₂ ≠ 0 and no real zero: (q₂, q₁, q₀). */
    private fun quadratic(e: Expr, t: Expr.Sym): List<Double>? {
        val c = Solve.coefficientsIn(Algebra.expand(e), t) ?: return null
        if (c.size != 3) return null
        val (q0, q1, q2) = c.map { Numeric.value(it) ?: return null }
        if (q2 == 0.0 || q1 * q1 - 4 * q2 * q0 >= 0) return null
        return listOf(q2, q1, q0)
    }

    /** (p, m, b) where [e] is b·(t − p)ᵐ with numbers and p not real; null otherwise. */
    private fun pole(e: Expr, t: Expr.Sym): Triple<Expr, Int, Expr>? {
        val c = Solve.coefficientsIn(Algebra.expand(e), t) ?: return null
        val m = c.size - 1
        if (m < 1 || m > 8 || c.any { it.symbols().isNotEmpty() }) return null
        val lead = c[m]
        val p = tidy(neg(c[m - 1]) / (num(m.toLong()) * lead))
        if (m > 1 && !LinAlg.isNull(tidy(Algebra.expand(lead * pow(t - p, num(m.toLong())) - e)))) return null
        val im = Complex.parts(p)?.second?.let { Numeric.value(it) } ?: return null
        return if (im != 0.0) Triple(p, m, lead) else null
    }

    /**
     * c·e^(iλt)/(t − p)ᵐ (b of b·(t − p)ᵐ in c) with p not real: 1/(t − p) ↔ 2πi·e^(−iΩp)·(1 − sgn Ω)/2 where Im p > 0 and −2πi·e^(−iΩp)·(1 + sgn Ω)/2
     * where Im p < 0 (by residues), each further power a factor −iΩ/k (the derivative in p), at Ω = ω − λ.
     */
    private fun pole(k: Kind, t: Expr.Sym, w: Expr.Sym): Expr {
        val base = k.kernel!!
        val p = pole(base, t)?.first ?: throw CalcException(Failure.UNSOLVED)
        val above = Numeric.value(Complex.parts(p)!!.second!!)!! > 0
        val omega = tidy(w - k.lambda)
        val step = if (above) mul(Expr.HALF, add(Expr.ONE, neg(fn(Func.SIGN, omega)))) else mul(Expr.HALF, add(Expr.ONE, fn(Func.SIGN, omega)))
        var g: Expr = mul(if (above) Expr.ONE else Expr.MINUS_ONE, Expr.TWO, Expr.PI, Expr.I, exp(neg(Expr.I) * omega * p), step)
        for (j in 1 until k.order) g = mul(g, neg(Expr.I) * omega, num(Rational.ONE / Rational.of(j.toLong())))
        return tidy(mul(k.coefficient, g))
    }

    /** c·tⁿ·e^(−at² + βt + γ): c·e^γ·dⁿ/dsⁿ [√(π/a)·e^(s²/(4a))] at s = β − iω, a > 0. */
    private fun gaussian(k: Kind, w: Expr.Sym): Expr {
        val (_, beta, c2) = k.exponent
        val a = tidy(neg(c2))
        val av = Numeric.value(a) ?: throw CalcException(Failure.UNSOLVED)
        if (av <= 0) throw CalcException(if (av < 0) Failure.DIVERGES else Failure.UNSOLVED)
        if (Complex.parts(beta)?.first?.let { Numeric.value(it) } == null) throw CalcException(Failure.UNSOLVED)
        var g: Expr = mul(sqrt(Expr.PI / a), exp(pow(SIGMA, Expr.TWO) / (num(4) * a)))
        repeat(k.n) { g = Calculus.derivative(g, SIGMA) }
        return tidy(mul(k.coefficient, g.substitute(SIGMA, tidy(beta - mul(Expr.I, w)))))
    }

    /** c·tⁿ·e^(iλt)/((t − t₀)² + κ²) for n = 0, 1: (π/κ)·e^(−κ|Ω|)·e^(−iΩt₀), times (t₀ − iκ·sgn Ω) for n = 1, at Ω = ω − λ. */
    private fun lorentzian(k: Kind, t: Expr.Sym, w: Expr.Sym): Expr {
        if (k.n > 1) throw CalcException(Failure.UNSOLVED)
        val c = Solve.coefficientsIn(Algebra.expand(k.kernel!!), t) ?: throw CalcException(Failure.UNSOLVED)
        val (q0, q1, q2) = c
        val t0 = tidy(neg(q1) / (Expr.TWO * q2))
        val kappa = tidy(sqrt(tidy(q0 / q2 - pow(t0, Expr.TWO))))
        val omega = tidy(w - k.lambda)
        val base = mul(Expr.PI / kappa, exp(neg(kappa) * fn(Func.ABS, omega)), exp(neg(Expr.I) * omega * t0))
        val shaped = if (k.n == 0) base else mul(base, add(t0, neg(mul(Expr.I, kappa, fn(Func.SIGN, omega)))))
        return tidy(mul(k.coefficient, pow(q2, Expr.MINUS_ONE), shaped))
    }

    /** c·e^(iλt)/cosh(at): c·(π/|a|)/cosh(π(ω − λ)/(2|a|)). */
    private fun sech(k: Kind, t: Expr.Sym, w: Expr.Sym): Expr {
        if (k.n != 0) throw CalcException(Failure.UNSOLVED)
        val c = Solve.coefficientsIn(Algebra.expand(k.kernel!!), t) ?: throw CalcException(Failure.UNSOLVED)
        if (c.size != 2 || !isZero(c[0])) throw CalcException(Failure.UNSOLVED)
        val av = Numeric.value(c[1]) ?: throw CalcException(Failure.UNSOLVED)
        val a = if (av < 0) tidy(neg(c[1])) else c[1]
        return tidy(mul(k.coefficient, Expr.PI / a, pow(fn(Func.COSH, Expr.PI * (w - k.lambda) / (Expr.TWO * a)), Expr.MINUS_ONE)))
    }

    /**
     * A sum of terms with sgn and |…|: on each piece between the points where one of them changes, a sum of c·tⁿ·e^(μt), each
     * ∫ c·tⁿ·e^(κt) dt = c·e^(κt)·Σⱼ (−1)ʲ·n!/(n − j)!·t^(n−j)/κ^(j+1) with κ = μ − iω, between the piece's ends; an infinite end
     * only where the term falls off there.
     */
    private fun piecewise(f: Expr, t: Expr.Sym, w: Expr.Sym): Expr {
        val points = breakpoints(f, t) ?: throw CalcException(Failure.UNSOLVED)
        val ends: List<Expr?> = listOf<Expr?>(null) + points + listOf<Expr?>(null)
        var total: Expr = Expr.ZERO
        for (k in 0 until ends.size - 1) {
            Budget.check()
            val lo = ends[k]
            val hi = ends[k + 1]
            val lv = lo?.let { Numeric.value(it)!! }
            val hv = hi?.let { Numeric.value(it)!! }
            val s = when {
                lv == null && hv == null -> 0.37
                lv == null -> hv!! - 1
                hv == null -> lv + 1
                else -> (lv + hv) / 2
            }
            val q = Algebra.expand(tidy(resolved(f, t, s)))
            if (isZero(q)) continue
            for (term in if (q is Expr.Add) q.terms else listOf(q)) {
                val (c, n, mu) = expPolynomial(term, t) ?: throw CalcException(Failure.UNSOLVED)
                val reMu = Complex.parts(mu)?.first?.let { Numeric.value(it) } ?: throw CalcException(Failure.UNSOLVED)
                // It must fall off at an infinite end of its piece.
                if (hi == null && reMu >= 0) throw CalcException(if (reMu > 0) Failure.DIVERGES else Failure.UNSOLVED)
                if (lo == null && reMu <= 0) throw CalcException(if (reMu < 0) Failure.DIVERGES else Failure.UNSOLVED)
                val kappa = tidy(mu - mul(Expr.I, w))
                fun antiderivative(x: Expr): Expr {
                    val parts = ArrayList<Expr>()
                    var falling = Rational.ONE
                    for (j in 0..n) {
                        val sign = if (j % 2 == 0) Expr.ONE else Expr.MINUS_ONE
                        parts += mul(sign, num(falling), (if (n == j) Expr.ONE else pow(x, num((n - j).toLong()))), pow(kappa, num(-(j + 1).toLong())))
                        falling = falling * Rational.of((n - j).toLong())
                    }
                    return mul(exp(mul(kappa, x)), add(parts))
                }
                val upper = hi?.let { antiderivative(it) } ?: Expr.ZERO
                val lower = lo?.let { antiderivative(it) } ?: Expr.ZERO
                total = add(total, mul(c, add(upper, neg(lower))))
            }
        }
        return tidy(total)
    }

    /** The points where an argument of a sgn or an |…| of [f] is 0, each linear in t with numbers, in order; null otherwise. */
    private fun breakpoints(f: Expr, t: Expr.Sym): List<Expr>? {
        val out = ArrayList<Pair<Double, Expr>>()
        var ok = true
        fun walk(e: Expr) {
            if (e is Expr.Fn && (e.f == Func.SIGN || e.f == Func.ABS) && e.has(t)) {
                val c = Solve.coefficientsIn(Algebra.expand(e.args[0]), t)
                if (c == null || c.size != 2) { ok = false; return }
                val p = tidy(neg(c[0]) / c[1])
                val v = Numeric.value(p)
                if (v == null) { ok = false; return }
                if (out.none { abs(it.first - v) < 1e-12 }) out += v to p
            }
            when (e) {
                is Expr.Fn -> e.args.forEach(::walk)
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                is Expr.Pow -> { walk(e.base); walk(e.exp) }
                else -> {}
            }
        }
        walk(f)
        return if (ok) out.sortedBy { it.first }.map { it.second } else null
    }

    /** [f] on the piece around [s]: each sgn u as its sign there, each |u| as ±u. */
    private fun resolved(f: Expr, t: Expr.Sym, s: Double): Expr {
        val inner = f.mapChildren { resolved(it, t, s) }
        if (inner is Expr.Fn && (inner.f == Func.SIGN || inner.f == Func.ABS) && inner.has(t)) {
            val u = inner.args[0]
            val positive = Numeric.eval(u, mapOf(t.name to s)) > 0
            return if (inner.f == Func.SIGN) (if (positive) Expr.ONE else Expr.MINUS_ONE) else if (positive) u else neg(u)
        }
        return inner
    }

    /** c·tⁿ·e^(μt) as (c, n, μ) with c and μ free of t; null otherwise. */
    private fun expPolynomial(term: Expr, t: Expr.Sym): Triple<Expr, Int, Expr>? {
        val factors = if (term is Expr.Mul) term.factors else listOf(term)
        var c: Expr = Expr.ONE
        var n = 0
        var mu: Expr = Expr.ZERO
        for (f in factors) {
            when {
                !f.has(t) -> c = mul(c, f)
                f == t -> n += 1
                f is Expr.Pow && f.base == t && integer(f.exp)?.signum()?.let { it >= 0 } == true -> n += integer(f.exp)!!.toInt()
                f is Expr.Pow && f.base == Expr.E -> {
                    val k = Solve.coefficientsIn(Algebra.expand(f.exp), t) ?: return null
                    if (k.size > 2) return null
                    c = mul(c, exp(k.getOrElse(0) { Expr.ZERO }))
                    mu = add(mu, k.getOrElse(1) { Expr.ZERO })
                }
                else -> return null
            }
        }
        if (n > 12) return null
        return Triple(c, n, tidy(mu))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The result as it is written.

    /** e^(iu + v) as e^v·(cos u + i sin u), then simplified: the shorter of the simplified and the multiplied-out forms. */
    fun polished(e: Expr): Expr {
        val euler = euler(signs(e))
        val simple = tidy(euler)
        val expanded = try { tidy(Algebra.expand(euler)) } catch (x: CalcException) {
            if (x.failure == Failure.TOO_LONG) throw x
            simple
        }
        val shorter = if (expanded.size() < simple.size()) expanded else simple
        // i in a denominator, (iω − 1)(iω + 1): the denominator made real, as 1/(1 + i) is 1/2 − i/2.
        if (!iBelow(shorter)) return shorter
        val split = try { parts(shorter) } catch (x: CalcException) {
            if (x.failure == Failure.TOO_LONG) throw x
            null
        } ?: return shorter
        // On one bar where that is no longer: (1 − iω)/(ω² + 1).
        val joined = tidy(split)
        return if (!iBelow(joined) && joined.size() <= split.size()) joined else split
    }

    /** sgn(−u) as −sgn(u). */
    private fun signs(e: Expr): Expr {
        val inner = e.mapChildren(::signs)
        if (inner is Expr.Fn && inner.f == Func.SIGN && Format.expr(inner.args[0], Style.PLAIN, false).startsWith("-")) {
            return neg(fn(Func.SIGN, Algebra.expand(neg(inner.args[0]))))
        }
        return inner
    }

    private fun iBelow(e: Expr): Boolean = when (e) {
        is Expr.Pow -> (rational(e.exp)?.signum ?: 0) < 0 && e.base.hasI() || iBelow(e.base)
        is Expr.Add -> e.terms.any(::iBelow)
        is Expr.Mul -> e.factors.any(::iBelow)
        is Expr.Fn -> e.args.any(::iBelow)
        else -> false
    }

    private fun conjugate(e: Expr): Expr = if (e == Expr.I) neg(Expr.I) else e.mapChildren(::conjugate)

    /** a + i·b with a and b real fractions (the letters real): numerator and denominator times the denominator's conjugate. */
    private fun parts(e: Expr): Expr? {
        val kernels = Algebra.Kernels()
        val (n, d) = Algebra.fraction(e, kernels) ?: return null
        var top = Algebra.expand(Algebra.toExpr(n, kernels))
        var bottom = Algebra.expand(Algebra.toExpr(d, kernels))
        if (bottom.hasI()) {
            val c = conjugate(bottom)
            top = Algebra.expand(top * c)
            bottom = Algebra.expand(bottom * c)
        }
        if (bottom.hasI() || isZero(bottom)) return null
        val terms = if (top is Expr.Add) top.terms else listOf(top)
        val (imaginary, real) = terms.partition { it.hasI() }
        val im = Algebra.expand(add(imaginary) / Expr.I)
        if (im.hasI()) return null
        val result = add(tidy(add(real) / bottom), mul(Expr.I, tidy(im / bottom)))
        // The same function: checked at points.
        return if (Verify.same(result, e)) result else null
    }

    private fun euler(e: Expr): Expr {
        val inner = e.mapChildren(::euler)
        if (inner is Expr.Pow && inner.base == Expr.E && inner.exp.hasI()) {
            val terms = Algebra.expand(inner.exp).let { if (it is Expr.Add) it.terms else listOf(it) }
            val (imaginary, real) = terms.partition { it.hasI() }
            val u = tidy(add(imaginary) / Expr.I)
            if (u.hasI()) return inner
            return mul(exp(add(real)), add(fn(Func.COS, u), mul(Expr.I, fn(Func.SIN, u))))
        }
        return inner
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The check.

    /**
     * Whether [result] (in [w]) is the transform of [f] (in [x]): numerically at points, ∫ f(x)·e^(∓iwx) dx where f falls off fast,
     * or the other way, ∫ result·e^(±iwx) dw where the result does. Other letters are put in as numbers.
     */
    private fun checked(f: Expr, x: Expr.Sym, result: Expr, w: Expr.Sym, inverse: Boolean): Boolean {
        val others = ((f.symbols() + result.symbols()) - x.name - w.name).sorted()
        val env = others.withIndex().associate { (k, p) -> p to 1.37 + 0.11 * k }
        val sign = if (inverse) 1.0 else -1.0
        val forth = agree(f, x, result, w, sign, if (inverse) 1 / (2 * Math.PI) else 1.0, env)
        if (forth == false) return false
        val back = agree(result, w, f, x, -sign, if (inverse) 1.0 else 1 / (2 * Math.PI), env)
        if (back == false) return false
        return forth == true || back == true
    }

    /**
     * Whether h(y) = scale·∫ g(x)·e^(i·sign·y·x) dx at three values of y: true, false, or null where g does not fall off fast enough
     * to integrate it (or too few values compare).
     */
    private fun agree(g: Expr, x: Expr.Sym, h: Expr, y: Expr.Sym, sign: Double, scale: Double, env: Map<String, Double>): Boolean? {
        fun gv(v: Double): Cx = Verify.value(g, env + (x.name to v))
        // How far out g matters: beyond L it is below 10⁻¹⁴ of its size.
        var size = 0.0
        for (k in -80..80) gv(k / 10.0 + 0.0013).takeIf { it.finite }?.let { size = max(size, it.abs) }
        if (size == 0.0) return null
        var reach: Double? = null
        var l = 1.0
        while (l <= 256.0) {
            val small = (0..40).all { k ->
                val v = l * (1 + k / 10.0)
                val a = gv(v)
                val b = gv(-v)
                a.finite && b.finite && a.abs <= 1e-14 * size && b.abs <= 1e-14 * size
            }
            if (small) { reach = l; break }
            l *= 2
        }
        val end = reach ?: return null
        val cuts = (breakpoints(g, x).orEmpty().mapNotNull { Numeric.value(it) } + 0.0).filter { it > -end && it < end }
        val marks = (listOf(-end, end) + cuts).distinct().sorted()
        var compared = 0
        for (yv in listOf(0.37, 1.43, -2.21)) {
            Budget.check()
            val expected = Verify.value(h, env + (y.name to yv))
            if (!expected.finite) continue
            var re = 0.0
            var im = 0.0
            var error = 0.0
            for (j in 0 until marks.size - 1) {
                val a = marks[j]
                val b = marks[j + 1]
                val parts = maxOf(1, kotlin.math.ceil((b - a) / 0.5).toInt())
                for (p in 0 until parts) {
                    val lo = a + (b - a) * p / parts
                    val hi = a + (b - a) * (p + 1) / parts
                    fun at(v: Double): Cx = gv(v) * Cx(kotlin.math.cos(sign * yv * v), kotlin.math.sin(sign * yv * v))
                    val r = Cubature.integrate({ at(it).re }, lo, hi) ?: return null
                    val i = Cubature.integrate({ at(it).im }, lo, hi) ?: return null
                    re += r.value
                    im += i.value
                    error += r.error + i.error
                }
            }
            val got = Cx(re * scale, im * scale)
            if ((got - expected).abs > 1e-6 * max(1.0, expected.abs) + 10 * error) return false
            compared++
        }
        return if (compared >= 2) true else null
    }
}
