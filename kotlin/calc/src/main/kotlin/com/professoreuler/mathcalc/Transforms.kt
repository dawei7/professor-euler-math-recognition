package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * The Laplace transform of sums of c·tⁿ·e^(at)·sin(bt), cos(bt) (sinh and cosh as exponentials), checked against ∫₀^∞ e^(−st)f(t) dt
 * at a value of s; the inverse transform by partial fractions in s, checked by transforming back; the Fourier series of a
 * function on [−L, L], its coefficients in n checked by quadrature for n = 1, 2, 3.
 */
object Transforms {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** L{f}(s) of f(t); null where f is not a sum of the forms above or the check fails. */
    fun laplace(f: Expr, t: Expr.Sym, s: Expr.Sym): Expr? {
        val expanded = tidy(Algebra.expand(asExponentials(f)))
        val terms = if (expanded is Expr.Add) expanded.terms else listOf(expanded)
        var growth = 0.0
        val parts = ArrayList<Expr>()
        for (term in terms) {
            val (transform, rate) = laplaceTerm(term, t, s) ?: return null
            parts += transform
            growth = max(growth, rate)
        }
        val result = tidy(add(parts))
        // Checked at s₀ beyond every growth rate: ∫₀^∞ e^(−s₀t) f(t) dt by quadrature.
        val s0 = growth + 2.0
        val numeric = Quadrature.integrate({ x -> kotlin.math.exp(-s0 * x) * Numeric.eval(f, mapOf(t.name to x)) }, 0.0, Double.POSITIVE_INFINITY)
        val exact = Numeric.eval(result, mapOf(s.name to s0))
        if (numeric != null && exact.isFinite() && abs(numeric - exact) > 1e-6 * max(1.0, abs(exact))) return null
        return result
    }

    /** sinh u and cosh u as (e^u ∓ e^(−u))/2. */
    private fun asExponentials(e: Expr): Expr {
        val inner = e.mapChildren(::asExponentials)
        if (inner is Expr.Fn && inner.args.size == 1) {
            val u = inner.args[0]
            when (inner.f) {
                Func.SINH -> return mul(Expr.HALF, add(exp(u), neg(exp(neg(u)))))
                Func.COSH -> return mul(Expr.HALF, add(exp(u), exp(neg(u))))
                else -> {}
            }
        }
        return inner
    }

    /** One term c·tⁿ·e^(at)·trig(bt): its transform and the growth rate a. */
    private fun laplaceTerm(term: Expr, t: Expr.Sym, s: Expr.Sym): Pair<Expr, Double>? {
        val factors = if (term is Expr.Mul) term.factors else listOf(term)
        var constant: Expr = Expr.ONE
        var n = 0
        var a: Expr = Expr.ZERO
        var trig: Expr.Fn? = null
        for (f in factors) {
            when {
                !f.has(t) -> constant = mul(constant, f)
                f == t -> n += 1
                f is Expr.Pow && f.base == t && integer(f.exp)?.let { it.signum() >= 0 && it.bitLength() < 8 } == true -> n += integer(f.exp)!!.toInt()
                f is Expr.Pow && f.base == Expr.E -> {
                    val c = Solve.coefficientsIn(Algebra.expand(f.exp), t) ?: return null
                    if (c.size > 2) return null
                    if (c.isNotEmpty()) constant = mul(constant, exp(c[0]))
                    if (c.size == 2) a = add(a, c[1])
                }
                f is Expr.Fn && (f.f == Func.SIN || f.f == Func.COS) && trig == null -> trig = f
                else -> return null
            }
        }
        val shifted = add(s, neg(a))
        val base: Expr = if (trig == null) pow(shifted, Expr.MINUS_ONE) else {
            val c = Solve.coefficientsIn(Algebra.expand(trig.args[0]), t) ?: return null
            if (c.size != 2) return null
            val b = c[1]
            val phase = c[0]
            val denominator = add(pow(shifted, Expr.TWO), pow(b, Expr.TWO))
            // sin(bt + φ) = sin bt cos φ + cos bt sin φ, and the same for cos.
            val sinPart = mul(b, pow(denominator, Expr.MINUS_ONE))
            val cosPart = mul(shifted, pow(denominator, Expr.MINUS_ONE))
            if (trig.f == Func.SIN) add(mul(fn(Func.COS, phase), sinPart), mul(fn(Func.SIN, phase), cosPart))
            else add(mul(fn(Func.COS, phase), cosPart), neg(mul(fn(Func.SIN, phase), sinPart)))
        }
        // tⁿ·g(t) ↦ (−1)ⁿ dⁿ/dsⁿ G(s).
        var g = base
        repeat(n) { g = neg(Calculus.derivative(g, s)) }
        val rate = Numeric.value(a) ?: return null
        return tidy(mul(constant, g)) to rate
    }

    /**
     * f(t) with L{f} = [big]: partial fractions in s, each A/(s − a)ᵏ as A·t^(k−1)·e^(at)/(k − 1)! and (Bs + C)/((s − α)² + β²) as
     * e^(αt)·(B cos βt + (C + Bα)/β·sin βt); checked by transforming back.
     */
    fun inverseLaplace(big: Expr, s: Expr.Sym, t: Expr.Sym): Expr? {
        // The partial fractions as they are: simplified, they would go back onto one bar.
        val expanded = Calculus.partialFractions(big, s) ?: tidy(big)
        val terms = if (expanded is Expr.Add) expanded.terms else listOf(expanded)
        val out = ArrayList<Expr>()
        for (term in terms) out += inverseTerm(term, s, t) ?: return null
        val f = tidy(add(out))
        val back = laplace(f, t, s) ?: return null
        return if (Verify.same(back, big)) f else null
    }

    private fun inverseTerm(term: Expr, s: Expr.Sym, t: Expr.Sym): Expr? {
        val factors = if (term is Expr.Mul) term.factors else listOf(term)
        val below = factors.filter { it is Expr.Pow && it.base.has(s) && (rational(it.exp)?.signum ?: 0) < 0 }
        if (below.size != 1) return null
        val den = below[0] as Expr.Pow
        val k = integer(den.exp)?.negate()?.toInt() ?: return null
        val top = tidy(mul(factors.filter { it !== den }))
        val dc = Solve.coefficientsIn(Algebra.expand(den.base), s) ?: return null
        val nc = Solve.coefficientsIn(Algebra.expand(top), s) ?: return null
        when (dc.size - 1) {
            1 -> {
                if (nc.size > 1) return null
                // A/(c·(s − a))ᵏ
                val lead = dc[1]
                val a = tidy(neg(dc[0]) / lead)
                val coefficient = tidy(nc.getOrElse(0) { Expr.ZERO } / pow(lead, num(k.toLong())))
                var factorial = Rational.ONE
                for (j in 2 until k) factorial = factorial * Rational.of(j.toLong())
                return tidy(mul(coefficient, pow(t, num((k - 1).toLong())), exp(mul(a, t)), num(Rational.ONE / factorial)))
            }
            2 -> {
                if (k != 1 || nc.size > 2) return null
                val lead = dc[2]
                val p = tidy(dc[1] / lead)
                val q = tidy(dc[0] / lead)
                val alpha = tidy(neg(p) / Expr.TWO)
                val beta2 = tidy(q - pow(alpha, Expr.TWO))
                val bv = Numeric.value(beta2) ?: return null
                if (bv <= 0) return null
                val beta = tidy(sqrt(beta2))
                val bCoef = tidy(nc.getOrElse(1) { Expr.ZERO } / lead)
                val cCoef = tidy(nc.getOrElse(0) { Expr.ZERO } / lead)
                return tidy(mul(exp(mul(alpha, t)), add(mul(bCoef, fn(Func.COS, mul(beta, t))),
                    mul(tidy((cCoef + bCoef * alpha) / beta), fn(Func.SIN, mul(beta, t))))))
            }
            else -> return null
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Fourier series.

    /** The Fourier series of f on [−L, L]: a₀, aₙ and bₙ in n (sin nπ = 0, cos nπ = (−1)ⁿ for whole n), checked by quadrature. */
    class Fourier(val a0: Expr, val an: Expr, val bn: Expr, val half: Expr)

    val N = Expr.Sym("n")

    fun fourier(f: Expr, x: Expr.Sym, half: Expr): Fourier? {
        val lv = Numeric.value(half) ?: return null
        if (lv <= 0) return null
        val k = tidy(mul(N, Expr.PI, pow(half, Expr.MINUS_ONE)))
        fun coefficient(weight: Expr?): Expr? {
            val integrand = if (weight == null) f else mul(f, weight)
            // |x| as −x and x on the two halves of the interval.
            val exact = if (hasAbsOf(integrand, x)) {
                val left = Calculus.definite(withAbs(integrand, x, -1), x, neg(half), Expr.ZERO).exact ?: return null
                val right = Calculus.definite(withAbs(integrand, x, 1), x, Expr.ZERO, half).exact ?: return null
                tidy(left + right)
            } else Calculus.definite(integrand, x, neg(half), half).exact ?: return null
            return tidy(wholeN(tidy(exact / half)))
        }
        val a0 = coefficient(null) ?: return null
        val an = coefficient(fn(Func.COS, mul(k, x))) ?: return null
        val bn = coefficient(fn(Func.SIN, mul(k, x))) ?: return null
        if (an.symbols() - N.name - x.name != f.symbols() - x.name || bn.has(x)) return null
        // Each checked at n = 1, 2, 3 against the integral by quadrature.
        for (nv in 1..3) {
            val kn = nv * Math.PI / lv
            for ((formula, weight) in listOf(an to { v: Double -> kotlin.math.cos(kn * v) }, bn to { v: Double -> kotlin.math.sin(kn * v) })) {
                val q = Quadrature.integrate({ v -> Numeric.eval(f, mapOf(x.name to v)) * weight(v) }, -lv, lv) ?: continue
                val value = Numeric.eval(formula, mapOf(N.name to nv.toDouble()))
                if (!value.isFinite() || abs(value - q / lv) > 1e-6 * max(1.0, abs(value))) return null
            }
        }
        return Fourier(a0, an, bn, half)
    }

    private fun hasAbsOf(e: Expr, x: Expr.Sym): Boolean = when (e) {
        is Expr.Fn -> (e.f == Func.ABS && e.args[0] == x) || e.args.any { hasAbsOf(it, x) }
        is Expr.Add -> e.terms.any { hasAbsOf(it, x) }
        is Expr.Mul -> e.factors.any { hasAbsOf(it, x) }
        is Expr.Pow -> hasAbsOf(e.base, x) || hasAbsOf(e.exp, x)
        else -> false
    }

    /** |x| as [sign]·x. */
    private fun withAbs(e: Expr, x: Expr.Sym, sign: Int): Expr {
        val inner = e.mapChildren { withAbs(it, x, sign) }
        return if (inner is Expr.Fn && inner.f == Func.ABS && inner.args[0] == x) mul(num(sign.toLong()), x) else inner
    }

    /** sin(k·n·π) = 0 and cos(k·n·π) = (−1)^(kn) for a whole k: n is a whole number in a Fourier coefficient. */
    private fun wholeN(e: Expr): Expr {
        val inner = e.mapChildren(::wholeN)
        if (inner is Expr.Fn && (inner.f == Func.SIN || inner.f == Func.COS)) {
            val k = rational(tidy(inner.args[0] / mul(N, Expr.PI)))
            if (k != null && k.isInteger) {
                return if (inner.f == Func.SIN) Expr.ZERO else if (!k.num.testBit(0)) Expr.ONE else pow(Expr.MINUS_ONE, N)
            }
        }
        return inner
    }
}

/** A complex number's polar form, and a function's poles with their residues (from its Laurent series there). */
object ComplexAnalysis {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** z = r·e^(iφ): the modulus r and the argument φ in (−π, π]; checked: r cos φ and r sin φ are z's parts. */
    class Polar(val modulus: Expr, val argument: Expr)

    fun polar(z: Expr): Polar? {
        val (re, im0) = Complex.parts(z) ?: return null
        val im = im0 ?: Expr.ZERO
        val rv = Numeric.value(re) ?: return null
        val iv = Numeric.value(im) ?: return null
        if (rv == 0.0 && iv == 0.0) return null
        val r = tidy(sqrt(add(pow(re, Expr.TWO), pow(im, Expr.TWO))))
        val phi = tidy(when {
            rv > 0 -> fn(Func.ATAN, tidy(im / re))
            rv < 0 && iv >= 0 -> add(fn(Func.ATAN, tidy(im / re)), Expr.PI)
            rv < 0 -> add(fn(Func.ATAN, tidy(im / re)), neg(Expr.PI))
            iv > 0 -> Expr.PI / Expr.TWO
            else -> neg(Expr.PI / Expr.TWO)
        })
        val r0 = Numeric.value(r) ?: return null
        val p0 = Numeric.value(phi) ?: return null
        if (abs(r0 * kotlin.math.cos(p0) - rv) > 1e-9 * max(1.0, r0) || abs(r0 * kotlin.math.sin(p0) - iv) > 1e-9 * max(1.0, r0)) return null
        return Polar(r, phi)
    }

    /** A pole: where, its order, its residue. */
    class Pole(val at: Expr, val order: Int, val residue: Expr)

    /** The poles of f(z) (the zeros of the bases of its negative powers, complex ones too) and the residue at each. */
    fun residues(f0: Expr, z: Expr.Sym): List<Pole>? {
        val f = tidy(f0)
        val factors = if (f is Expr.Mul) f.factors else listOf(f)
        val bases = factors.filter { it is Expr.Pow && it.base.has(z) && (rational(it.exp)?.signum ?: 0) < 0 }.map { (it as Expr.Pow).base }
        if (bases.isEmpty()) return null
        val poles = ArrayList<Pole>()
        for (b in bases) {
            val roots = Domain.complex { Solve.equation(b, Expr.ZERO, z) }
            if (roots.numeric) return null
            for (a in (roots.values + roots.complex).distinct()) {
                if (poles.any { LinAlg.isNull(tidy(it.at - a)) }) continue
                val terms = Domain.complex { Series.taylorTerms(f, z, a, 0) } ?: return null
                val lowest = terms.minOfOrNull { it.second } ?: continue
                if (lowest >= 0) continue
                val residue = terms.firstOrNull { it.second == -1 }?.first ?: Expr.ZERO
                poles += Pole(a, -lowest, tidy(residue))
            }
        }
        return poles
    }
}
