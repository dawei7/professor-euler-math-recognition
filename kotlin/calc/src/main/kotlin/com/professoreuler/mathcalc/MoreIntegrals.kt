package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * The methods [Calculus.antiderivative] tries where its rules find nothing, each a published substitution or algorithm written
 * from its description; what they give is differentiated back by the caller:
 *
 * - hyperbolic functions as exponentials (1/cosh x = 2eˣ/(e^(2x) + 1), then a rational function of eˣ);
 * - t = ⁿ√((ax + b)/(cx + d)) for a rational function of x and roots of one such fraction (√x/(1 + x), 1/(√x + ∛x),
 *   √((1 + x)/(1 − x))): x and dx rational in t;
 * - Euler's substitutions for a rational function of x and √(Ax² + Bx + C): √ = t − x√A where A > 0, √ = (x − r₁)·t
 *   between two rational roots, √ = x·t + √C where C > 0;
 * - Weierstrass's t = tan(u/2) for a rational function of sin u, cos u, tan u … of one linear u (multiples of u by their
 *   formulas): sin u = 2t/(1 + t²), cos u = (1 − t²)/(1 + t²), du = 2 dt/(1 + t²);
 * - the Risch–Norman (parallel Risch) heuristic for products and quotients of x, eᵘ, ln u, tan u, arctan u, sin u, cos u
 *   ([RischNorman]).
 */
internal object MoreIntegrals {
    private val T = Expr.Sym("\u0001s")

    fun of(e: Expr, x: Expr.Sym): Expr? {
        for (method in listOf(::hyperbolic, ::fractionalRoots, ::conjugate, ::euler, ::bioche, RischNorman::of, ::inverseTrig, ::weierstrass)) {
            Budget.check()
            val f = try {
                method(e, x)
            } catch (c: CalcException) {
                if (c.failure == Failure.TOO_LONG) throw c
                null
            } catch (z: ArithmeticException) {
                null
            } ?: continue
            if (Verify.same(Calculus.diff(f, x), e)) return f
        }
        return null
    }

    /**
     * A denominator √a + √b (or √a + b) made rational by its conjugate: 1/(√(x + 1) + √x) = (√(x + 1) − √x)/((x + 1) − x).
     */
    private fun conjugate(e: Expr, x: Expr.Sym): Expr? {
        val factors = if (e is Expr.Mul) e.factors else listOf(e)
        fun root(t: Expr) = t.has(x) && (t is Expr.Pow && t.exp == Expr.HALF || t is Expr.Mul && t.factors.any { it is Expr.Pow && it.exp == Expr.HALF && it.base.has(x) })
        val i = factors.indexOfFirst { f -> f is Expr.Pow && f.exp == Expr.MINUS_ONE && f.base is Expr.Add && (f.base as Expr.Add).terms.size == 2 &&
            (f.base as Expr.Add).terms.any(::root) }
        if (i < 0) return null
        val (a, b) = ((factors[i] as Expr.Pow).base as Expr.Add).terms
        val norm = Algebra.expand(add(mul(a, a), neg(mul(b, b))))
        if (isZero(norm) || root(norm)) return null
        val rewritten = Algebra.expand(mul(factors.filterIndexed { j, _ -> j != i } + add(a, neg(b)) + pow(norm, Expr.MINUS_ONE)))
        return Calculus.integrateByRules(rewritten, x)
    }

    /** ∫ in [t] by the rules, for an integrand a substitution made. */
    private fun inT(e: Expr, t: Expr.Sym): Expr? {
        val clean = try { Calculus.tidy(e) } catch (c: CalcException) { if (c.failure == Failure.TOO_LONG) throw c; e }
        return Calculus.integrateByRules(clean, t) ?: RationalIntegral.of(clean, t)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Hyperbolic functions.

    private fun hyperbolic(e: Expr, x: Expr.Sym): Expr? {
        var changed = false
        fun walk(t: Expr): Expr {
            val inner = t.mapChildren(::walk)
            if (inner is Expr.Fn && inner.args.size == 1 && inner.args[0].has(x)) {
                val u = inner.args[0]
                when (inner.f) {
                    Func.SINH -> { changed = true; return mul(Expr.HALF, add(exp(u), neg(exp(neg(u))))) }
                    Func.COSH -> { changed = true; return mul(Expr.HALF, add(exp(u), exp(neg(u)))) }
                    Func.TANH -> { changed = true; return mul(add(exp(mul(Expr.TWO, u)), Expr.MINUS_ONE), pow(add(exp(mul(Expr.TWO, u)), Expr.ONE), Expr.MINUS_ONE)) }
                    else -> {}
                }
            }
            return inner
        }
        val rewritten = walk(e)
        if (!changed) return null
        return Calculus.integrateByRules(rewritten, x) ?: RischNorman.of(rewritten, x)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Roots of one fraction (ax + b)/(cx + d).

    private fun fractionalRoots(e: Expr, x: Expr.Sym): Expr? {
        val roots = ArrayList<Expr.Pow>()
        fun collect(t: Expr) {
            if (t is Expr.Pow && t.base.has(x) && rational(t.exp)?.isInteger == false) roots += t
            when (t) {
                is Expr.Add -> t.terms.forEach(::collect)
                is Expr.Mul -> t.factors.forEach(::collect)
                is Expr.Pow -> { collect(t.base); collect(t.exp) }
                is Expr.Fn -> t.args.forEach(::collect)
                else -> {}
            }
        }
        collect(e)
        if (roots.isEmpty()) return null
        val u = roots[0].base
        if (roots.any { it.base != u }) return null
        // u = (a x + b)/(c x + d), ad − bc ≠ 0.
        val kernels = Algebra.Kernels()
        val (top, bottom) = Algebra.fraction(u, kernels) ?: return null
        if (!kernels.isEmpty || (top.unknowns + bottom.unknowns) != setOf(x.name)) return null
        if (top.degreeIn(x.name) > 1 || bottom.degreeIn(x.name) > 1) return null
        val a = top.terms[Monomial.of(x.name)] ?: Rational.ZERO
        val b = top.constant
        val c = bottom.terms[Monomial.of(x.name)] ?: Rational.ZERO
        val d = bottom.constant
        if ((a * d - b * c).signum == 0) return null
        var n = BigInteger.ONE
        for (r in roots) n = lcm(n, rational(r.exp)!!.den)
        if (n > BigInteger.valueOf(12)) return null
        val nn = n.toLong()
        // u = tⁿ: a x + b = tⁿ (c x + d), x = (d tⁿ − b)/(a − c tⁿ).
        val tn = pow(T, num(nn))
        val xt = mul(add(mul(num(d), tn), num(-b)), pow(add(num(a), mul(num(-c), tn)), Expr.MINUS_ONE))
        fun replace(t: Expr): Expr = when {
            t is Expr.Pow && t.base == u && rational(t.exp) != null -> pow(T, num(rational(t.exp)!! * Rational.of(nn)))
            t == x -> xt
            !t.has(x) -> t
            else -> t.mapChildren(::replace)
        }
        val inT = mul(replace(e), Calculus.diff(xt, T))
        if (inT.has(x)) return null
        val f = inT(inT, T) ?: return null
        // u as one fraction of its two polynomials, (x + 1)/(1 − x), not as the parser's sum.
        val written = if (bottom.isConstant) Algebra.toExpr(top * (Rational.ONE / bottom.constant), kernels)
            else mul(Algebra.toExpr(top, kernels), pow(Algebra.toExpr(bottom, kernels), Expr.MINUS_ONE))
        return f.substitute(T, pow(written, num(Rational.ONE / Rational.of(nn))))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // x = sin u and x = tan u, where arcsin x or arctan x stands in the integrand.

    private fun inverseTrig(e: Expr, x: Expr.Sym): Expr? {
        val inverse = HashSet<Func>()
        fun collect(t: Expr) {
            if (t is Expr.Fn && t.f in setOf(Func.ASIN, Func.ACOS, Func.ATAN, Func.ACOT) && t.args[0].has(x)) {
                if (t.args[0] != x) inverse += Func.SIGN // an argument other than x: not this method
                inverse += t.f
            }
            when (t) {
                is Expr.Add -> t.terms.forEach(::collect)
                is Expr.Mul -> t.factors.forEach(::collect)
                is Expr.Pow -> { collect(t.base); collect(t.exp) }
                is Expr.Fn -> t.args.forEach(::collect)
                else -> {}
            }
        }
        collect(e)
        if (inverse.isEmpty() || Func.SIGN in inverse) return null
        val sine = inverse.all { it == Func.ASIN || it == Func.ACOS }
        val tangent = inverse.all { it == Func.ATAN || it == Func.ACOT }
        if (!sine && !tangent) return null
        val u = T
        val oneMinus = Algebra.expand(add(Expr.ONE, neg(pow(x, Expr.TWO))))
        val onePlus = Algebra.expand(add(Expr.ONE, pow(x, Expr.TWO)))
        fun replace(t: Expr): Expr = when {
            !t.has(x) -> t
            t == x -> if (sine) fn(Func.SIN, u) else fn(Func.TAN, u)
            t is Expr.Fn && t.f == Func.ASIN || t is Expr.Fn && t.f == Func.ATAN -> u
            t is Expr.Fn && (t.f == Func.ACOS || t.f == Func.ACOT) -> add(mul(Expr.HALF, Expr.PI), neg(u))
            // √(1 − x²) = cos u where x = sin u (|u| ≤ π/2); √(1 + x²) = 1/cos u where x = tan u.
            t is Expr.Pow && rational(t.exp)?.let { !it.isInteger } == true -> {
                val r = rational(t.exp)!!
                val base = Algebra.expand(t.base)
                when {
                    sine && base == oneMinus && r.den == BigInteger.valueOf(2) -> pow(fn(Func.COS, u), num(r * Rational.of(2)))
                    tangent && base == onePlus && r.den == BigInteger.valueOf(2) -> pow(fn(Func.COS, u), num(-r * Rational.of(2)))
                    else -> t.mapChildren(::replace)
                }
            }
            else -> t.mapChildren(::replace)
        }
        val du = if (sine) fn(Func.COS, u) else pow(fn(Func.COS, u), num(-2))
        val inU = mul(replace(e), du)
        // Anything else of x left (a root other than √(1 − x²) or √(1 + x²)): not for this substitution.
        if (inU.has(x)) return null
        val clean = try { Calculus.tidy(inU) } catch (z: CalcException) { if (z.failure == Failure.TOO_LONG) throw z; inU }
        val f = Calculus.integrateByRules(clean, u) ?: RischNorman.of(clean, u) ?: return null
        // u back as arcsin x or arctan x, and the functions of it written in x.
        val back = if (sine) fn(Func.ASIN, x) else fn(Func.ATAN, x)
        val root = if (sine) sqrt(oneMinus) else sqrt(onePlus)
        fun written(t: Expr): Expr {
            val inner = t.mapChildren(::written)
            if (inner is Expr.Fn && inner.args.size == 1 && inner.args[0] == back) {
                // sin u = x, cos u = √(1 − x²) where u = arcsin x; tan u = x, cos u = 1/√(1 + x²) where u = arctan x.
                val (s, c) = if (sine) x to root else mul(x, pow(root, Expr.MINUS_ONE)) to pow(root, Expr.MINUS_ONE)
                return when (inner.f) {
                    Func.SIN -> s
                    Func.COS -> c
                    Func.TAN -> mul(s, pow(c, Expr.MINUS_ONE))
                    Func.COT -> mul(c, pow(s, Expr.MINUS_ONE))
                    Func.SEC -> pow(c, Expr.MINUS_ONE)
                    Func.CSC -> pow(s, Expr.MINUS_ONE)
                    else -> inner
                }
            }
            return inner
        }
        return written(f.substitute(u, back))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Bioche: t = tan u for an integrand unchanged where sin u and cos u both change sign.

    private fun bioche(e: Expr, x: Expr.Sym): Expr? {
        val setup = trigSetup(e, x) ?: return null
        val (base, u, replaceWith) = setup
        val c = Expr.Sym("\u0001c")
        // sin u = t·C, cos u = C, with C² = 1/(1 + t²).
        val rewritten = replaceWith(mul(T, c), c)
        val kernels = Algebra.Kernels()
        val (top, bottom) = Algebra.fraction(rewritten, kernels) ?: return null
        if (!kernels.isEmpty) return null
        fun parity(p: Polynomial): Int? = p.terms.keys.map { it.power(c.name) % 2 }.distinct().singleOrNull()
        val pt = parity(top) ?: return null
        val pb = parity(bottom) ?: return null
        if (pt != pb) return null
        fun inTan(p: Polynomial): Expr = add(p.terms.map { (m, k) ->
            val cp = m.power(c.name) - pt
            mul(listOf(num(k), pow(add(Expr.ONE, pow(T, Expr.TWO)), num(-(cp / 2).toLong()))) +
                m.powers.filter { it.first != c.name }.map { (v, n) -> pow(Expr.Sym(v), num(n.toLong())) })
        })
        // dx = du/base = dt/(base(1 + t²))
        val integrand = mul(inTan(top), pow(inTan(bottom), Expr.MINUS_ONE), num(Rational.ONE / base), pow(add(Expr.ONE, pow(T, Expr.TWO)), Expr.MINUS_ONE))
        if (integrand.has(x)) return null
        val f = inT(integrand, T) ?: return null
        return f.substitute(T, fn(Func.TAN, u))
    }

    /**
     * A rational function of sin, cos, tan … of whole multiples of one u = k·x + c and nothing else of x: (k, u, and a way to
     * write it with given sin u and cos u).
     */
    private fun trigSetup(e: Expr, x: Expr.Sym): Triple<Rational, Expr, (Expr, Expr) -> Expr>? {
        val args = ArrayList<Expr>()
        var other = false
        fun collect(t: Expr) {
            if (t is Expr.Fn && t.f in TRIG && t.args.size == 1 && t.args[0].has(x)) { args += t.args[0]; return }
            if (t is Expr.Fn && t.args.any { it.has(x) }) { other = true; return }
            if (t is Expr.Pow && t.exp.has(x)) { other = true; return }
            if (t is Expr.Pow && t.base.has(x) && rational(t.exp)?.isInteger != true) { other = true; return }
            if (t == x) { other = true; return }
            when (t) {
                is Expr.Add -> t.terms.forEach(::collect)
                is Expr.Mul -> t.factors.forEach(::collect)
                is Expr.Pow -> collect(t.base)
                else -> {}
            }
        }
        collect(e)
        if (args.isEmpty() || other) return null
        val linear = args.map { Solve.coefficientsIn(Algebra.expand(it), x)?.takeIf { c -> c.size == 2 && c.all { k -> rational(k) != null } } ?: return null }
        var base = rational(linear[0][1])!!.abs()
        for (l in linear) base = gcd(base, rational(l[1])!!.abs())
        if (base.signum == 0) return null
        val shift = rational(linear[0][0])!! / (rational(linear[0][1])!! / base)
        for (l in linear) {
            val m = rational(l[1])!! / base
            if (!m.isInteger || m.num.abs() > BigInteger.valueOf(6) || rational(l[0])!! != shift * m) return null
        }
        val u = add(mul(num(base), x), num(shift))
        val write = { s: Expr, c: Expr ->
            val sins = HashMap<Int, Expr>()
            val coss = HashMap<Int, Expr>()
            sins[0] = Expr.ZERO; coss[0] = Expr.ONE; sins[1] = s; coss[1] = c
            for (m in 2..6) {
                sins[m] = add(mul(sins[m - 1]!!, c), mul(coss[m - 1]!!, s))
                coss[m] = add(mul(coss[m - 1]!!, c), neg(mul(sins[m - 1]!!, s)))
            }
            fun replace(t: Expr): Expr {
                if (t is Expr.Fn && t.f in TRIG && t.args.size == 1 && t.args[0].has(x)) {
                    val cf = Solve.coefficientsIn(Algebra.expand(t.args[0]), x)!!
                    val m = (rational(cf[1])!! / base).num.toInt()
                    val sn = if (m >= 0) sins[m]!! else neg(sins[-m]!!)
                    val cs = coss[kotlin.math.abs(m)]!!
                    return when (t.f) {
                        Func.SIN -> sn
                        Func.COS -> cs
                        Func.TAN -> mul(sn, pow(cs, Expr.MINUS_ONE))
                        Func.COT -> mul(cs, pow(sn, Expr.MINUS_ONE))
                        Func.SEC -> pow(cs, Expr.MINUS_ONE)
                        else -> pow(sn, Expr.MINUS_ONE)
                    }
                }
                if (!t.has(x)) return t
                return t.mapChildren(::replace)
            }
            replace(e)
        }
        return Triple(base, u, write)
    }

    private fun lcm(a: BigInteger, b: BigInteger) = a / a.gcd(b) * b

    // ---------------------------------------------------------------------------------------------------------------------
    // Euler's substitutions.

    private fun euler(e: Expr, x: Expr.Sym): Expr? {
        val roots = ArrayList<Expr.Pow>()
        fun collect(t: Expr) {
            if (t is Expr.Pow && t.base.has(x) && rational(t.exp)?.isInteger == false) roots += t
            when (t) {
                is Expr.Add -> t.terms.forEach(::collect)
                is Expr.Mul -> t.factors.forEach(::collect)
                is Expr.Pow -> { collect(t.base); collect(t.exp) }
                is Expr.Fn -> t.args.forEach(::collect)
                else -> {}
            }
        }
        collect(e)
        if (roots.isEmpty()) return null
        val q = roots[0].base
        if (roots.any { it.base != q || rational(it.exp)!!.den != BigInteger.valueOf(2) }) return null
        val c = Solve.coefficientsIn(Algebra.expand(q), x) ?: return null
        if (c.size != 3 || c.any { rational(it) == null }) return null
        val cc = rational(c[0])!!
        val bb = rational(c[1])!!
        val aa = rational(c[2])!!
        if (aa.signum == 0) return null
        // x(t), y(t) = √q in t, the constant k with √q = k·y, and t back in x.
        val sub: Substitution = when {
            aa.signum > 0 -> {
                // y = √(x² + βx + γ) = t − x
                val beta = bb / aa
                val gamma = cc / aa
                val den = pow(add(num(beta), mul(Expr.TWO, T)), Expr.MINUS_ONE)
                val xt = mul(add(pow(T, Expr.TWO), num(-gamma)), den)
                val yt = mul(add(pow(T, Expr.TWO), mul(num(beta), T), num(gamma)), den)
                val k = sqrt(num(aa))
                Substitution(xt, yt, k, add(x, mul(sqrt(q), pow(k, Expr.MINUS_ONE))))
            }
            else -> {
                val disc = bb * bb - Rational.of(4) * aa * cc
                val r = disc.root(2)
                if (r != null && disc.signum > 0) {
                    // q = A(x − r₁)(x − r₂) = |A|(x − r₁)(r₂ − x), y = √((x − r₁)(r₂ − x)) = (x − r₁)·t
                    val two = Rational.of(2)
                    val x1 = (-bb - r) / (two * aa)
                    val x2 = (-bb + r) / (two * aa)
                    val r1 = if (x1 < x2) x1 else x2
                    val r2 = if (x1 < x2) x2 else x1
                    val den = pow(add(Expr.ONE, pow(T, Expr.TWO)), Expr.MINUS_ONE)
                    val xt = mul(add(num(r2), mul(num(r1), pow(T, Expr.TWO))), den)
                    val yt = mul(num(r2 - r1), T, den)
                    val k = sqrt(num(-aa))
                    Substitution(xt, yt, k, mul(sqrt(q), pow(mul(k, add(x, num(-r1))), Expr.MINUS_ONE)))
                } else if (cc.signum > 0) {
                    // y = √(a x² + b x + 1) = x·t + 1
                    val a = aa / cc
                    val b = bb / cc
                    val den = pow(add(num(a), neg(pow(T, Expr.TWO))), Expr.MINUS_ONE)
                    val xt = mul(add(mul(Expr.TWO, T), num(-b)), den)
                    val yt = add(mul(xt, T), Expr.ONE)
                    val k = sqrt(num(cc))
                    Substitution(xt, yt, k, mul(add(mul(sqrt(q), pow(k, Expr.MINUS_ONE)), Expr.MINUS_ONE), pow(x, Expr.MINUS_ONE)))
                } else return null
            }
        }
        fun replace(t: Expr): Expr = when {
            t is Expr.Pow && t.base == q && rational(t.exp) != null -> {
                val m = rational(t.exp)!! * Rational.of(2)
                mul(pow(sub.k, num(m)), pow(sub.y, num(m)))
            }
            t == x -> sub.x
            !t.has(x) -> t
            else -> t.mapChildren(::replace)
        }
        val inT = mul(replace(e), Calculus.diff(sub.x, T))
        if (inT.has(x)) return null
        val f = inT(inT, T) ?: return null
        return f.substitute(T, sub.back)
    }

    /** x and √(…)/k as functions of t, k, and t as a function of x. */
    private class Substitution(val x: Expr, val y: Expr, val k: Expr, val back: Expr)

    // ---------------------------------------------------------------------------------------------------------------------
    // Weierstrass.

    private val TRIG = setOf(Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC)

    private fun weierstrass(e: Expr, x: Expr.Sym): Expr? {
        // The arguments: whole multiples of one u = k·x + c.
        val args = ArrayList<Expr>()
        var other = false
        fun collect(t: Expr) {
            if (t is Expr.Fn && t.f in TRIG && t.args.size == 1 && t.args[0].has(x)) { args += t.args[0]; return }
            if (t is Expr.Fn && t.args.any { it.has(x) }) { other = true; return }
            if (t is Expr.Pow && t.exp.has(x)) { other = true; return }
            if (t is Expr.Pow && t.base.has(x) && rational(t.exp)?.isInteger != true) { other = true; return }
            when (t) {
                is Expr.Add -> t.terms.forEach(::collect)
                is Expr.Mul -> t.factors.forEach(::collect)
                is Expr.Pow -> collect(t.base)
                else -> {}
            }
        }
        collect(e)
        if (args.isEmpty() || other) return null
        val linear = args.map { Solve.coefficientsIn(Algebra.expand(it), x)?.takeIf { c -> c.size == 2 && c.all { k -> rational(k) != null } } ?: return null }
        val slopes = linear.map { rational(it[1])!! }
        var base = slopes[0].abs()
        for (s in slopes) base = gcd(base, s.abs())
        if (base.signum == 0) return null
        // Every argument m·(base·x) + c with one c (for the multiples) and whole m.
        val shift = rational(linear[0][0])!!
        val multiples = linear.map { c ->
            val m = rational(c[1])!! / base
            if (!m.isInteger || m.num.abs() > BigInteger.valueOf(6)) return null
            if (rational(c[0])!! != shift * m) return null
            m.num.toInt()
        }
        if (multiples.isEmpty()) return null
        val u = add(mul(num(base), x), num(shift))
        val tt = T
        val one = Expr.ONE
        val denominator = pow(add(one, pow(tt, Expr.TWO)), Expr.MINUS_ONE)
        val s = mul(Expr.TWO, tt, denominator)
        val c = mul(add(one, neg(pow(tt, Expr.TWO))), denominator)
        // sin(m u), cos(m u) by the addition formulas, from sin u and cos u.
        val sins = HashMap<Int, Expr>()
        val coss = HashMap<Int, Expr>()
        sins[0] = Expr.ZERO; coss[0] = Expr.ONE; sins[1] = s; coss[1] = c
        for (m in 2..6) {
            sins[m] = add(mul(sins[m - 1]!!, c), mul(coss[m - 1]!!, s))
            coss[m] = add(mul(coss[m - 1]!!, c), neg(mul(sins[m - 1]!!, s)))
        }
        fun sinOf(m: Int): Expr = if (m >= 0) sins[m]!! else neg(sins[-m]!!)
        fun cosOf(m: Int): Expr = coss[kotlin.math.abs(m)]!!
        fun replace(t: Expr): Expr {
            if (t is Expr.Fn && t.f in TRIG && t.args.size == 1 && t.args[0].has(x)) {
                val cf = Solve.coefficientsIn(Algebra.expand(t.args[0]), x)!!
                val m = (rational(cf[1])!! / base).num.toInt()
                val sn = sinOf(m)
                val cs = cosOf(m)
                return when (t.f) {
                    Func.SIN -> sn
                    Func.COS -> cs
                    Func.TAN -> mul(sn, pow(cs, Expr.MINUS_ONE))
                    Func.COT -> mul(cs, pow(sn, Expr.MINUS_ONE))
                    Func.SEC -> pow(cs, Expr.MINUS_ONE)
                    else -> pow(sn, Expr.MINUS_ONE)
                }
            }
            if (!t.has(x)) return t
            return t.mapChildren(::replace)
        }
        // dx = du/base = 2 dt/(base(1 + t²))
        val inT = mul(replace(e), num(Rational.of(2) / base), denominator)
        if (inT.has(x)) return null
        val f = inT(inT, tt) ?: return null
        return f.substitute(tt, fn(Func.TAN, mul(Expr.HALF, u)))
    }

    private fun gcd(p: Rational, q: Rational): Rational {
        if (p.signum == 0) return q
        if (q.signum == 0) return p
        return Rational.of((p.num * q.den).gcd(q.num * p.den), p.den * q.den)
    }
}
