package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Functions of numbers worked out where they are written, as gcd and binom are ([Discrete]): round, max and min of numbers
 * (letters refused: [Failure.ARGUMENTS]); the real part, imaginary part, conjugate and argument of a complex number (re, im
 * and conj also with letters, which are real); Euler's beta function; and the orthogonal polynomials of Legendre, Chebyshev
 * (first kind), Hermite (physicists') and Laguerre of a whole degree n, at a number or a letter. Every answer is checked:
 * a rounding by its error, an order by certified signs, parts by putting them back together, a beta value by its integral,
 * a polynomial by a second formula for it.
 */
internal object ValueFunctions {
    /** The names (English in every language) and how many arguments each takes. */
    val NAMES: Map<String, IntRange> = mapOf(
        "round" to 1..2, "max" to 1..10_000, "min" to 1..10_000,
        "re" to 1..1, "im" to 1..1, "conj" to 1..1, "arg" to 1..1,
        "beta" to 2..2,
        "legendrep" to 2..2, "chebyshevt" to 2..2, "hermiteh" to 2..2, "laguerrel" to 2..2,
    )

    private fun fail(f: Failure): Nothing = throw CalcException(f)

    /** [name] (one of [NAMES]) of [args]; [flags] says the answer is written in decimals where a rounding asks for them. */
    fun call(name: String, args: List<Expr>, flags: Flags): Expr {
        val arity = NAMES.getValue(name)
        if (args.size !in arity || args.any { it is Expr.Mat }) fail(Failure.ARGUMENTS)
        Budget.check()
        return when (name) {
            "round" -> round(args, flags)
            "max", "min" -> extreme(args, name == "max")
            "re", "im", "conj" -> part(name, args[0])
            "arg" -> argument(args[0], flags)
            "beta" -> beta(args[0], args[1])
            else -> Orthogonal.at(name, args[0], args[1])
        }
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Numbers, their signs and their order.

    /** A real number from [e]: letters are refused, i a number that is not real; 1·(1 + i)(1 − i) is 2. */
    private fun realNumber(e: Expr): Expr {
        if (e.symbols().isNotEmpty()) fail(Failure.ARGUMENTS)
        if (e.hasInfinity()) fail(Failure.UNDEFINED)
        if (!e.hasI()) return e
        val (a, b) = Complex.normal(e) ?: fail(Failure.UNDEFINED)
        if (!isZero(Calculus.tidy(b))) fail(Failure.UNDEFINED)
        return Calculus.tidy(a)
    }

    /** The sign of a real constant, certified: its ball at rising precision lies on one side of 0. */
    internal fun sign(e: Expr): Int {
        if (isZero(e)) return 0
        rational(e)?.let { return it.signum }
        var prec = 64
        while (prec <= 1 shl 14) {
            try {
                val z = Precise.eval(e, prec, false, emptyMap()).re
                if (z.isExactZero) return 0
                if (z.isPositive) return 1
                if (z.isNegative) return -1
            } catch (x: Imprecise) {
                // more bits
            }
            prec *= 2
            Budget.check()
        }
        fail(Failure.TOO_LONG)
    }

    /**
     * round(x) and round(x, n): x to n decimals (n < 0 to tens, hundreds …), a half rounded away from zero (round(2.5) = 3,
     * round(−2.5) = −3): the rule taught at school and set by DIN 1333 and ISO 80000-1 (Annex B, rule B), which calculators
     * and spreadsheets follow; rounding a half to even is the rule of IEEE arithmetic and of statistics' data, not of a
     * learner's answer. Exact for rationals; for any other number the digits are decided in balls.
     */
    private fun round(args: List<Expr>, flags: Flags): Expr {
        val x = realNumber(args[0])
        val n = if (args.size == 2) {
            if (args[1].symbols().isNotEmpty()) fail(Failure.ARGUMENTS)
            val k = integer(args[1]) ?: fail(Failure.ARGUMENTS)
            if (k.abs() > BigInteger.valueOf(1000)) fail(Failure.TOO_LARGE)
            k.toInt()
        } else 0
        val scale = if (n >= 0) Rational.of(BigInteger.TEN.pow(n)) else Rational.of(BigInteger.ONE, BigInteger.TEN.pow(-n))
        val k = halfAwayFromZero(mul(x, num(scale)))
        val result = Rational.of(k) / scale
        // A rounding to decimals is written in decimals: round(π, 2) is 3.14.
        if (n > 0 && !result.isInteger) flags.decimals = true
        return num(result)
    }

    /** The whole number nearest to [y], a half away from zero, checked: |y − k| ≤ ½, and = ½ only away from zero. */
    private fun halfAwayFromZero(y: Expr): BigInteger {
        val half = Rational.ONE / Rational.of(2)
        rational(y)?.let { r ->
            val a = r.abs() + half
            val f = a.num.divide(a.den)
            return if (r.signum < 0) f.negate() else f
        }
        var prec = 64
        while (prec <= 1 shl 14) {
            try {
                val z = Precise.eval(y, prec, false, emptyMap()).re
                if (z.magUp() < BigFloat.HALF) return BigInteger.ZERO
                val negative = when {
                    z.isPositive -> false
                    z.isNegative -> true
                    else -> throw Imprecise()
                }
                val a = if (negative) -z else z
                val lo = (a.lower + BigFloat.HALF).floor()
                // The check: k − ½ ≤ |y| < k + ½ over the whole ball, in exact binary numbers.
                val k = BigFloat.of(lo)
                if (a.lower >= k - BigFloat.HALF && a.upper < k + BigFloat.HALF) return if (negative) lo.negate() else lo
            } catch (x: Imprecise) {
                // more bits
            }
            prec *= 2
            Budget.check()
        }
        fail(Failure.TOO_LONG)
    }

    /** The largest (or smallest) of real numbers, each compared with the best so far by a certified sign. */
    private fun extreme(args: List<Expr>, largest: Boolean): Expr {
        val xs = args.map { realNumber(it) }
        var best = xs[0]
        for (x in xs.drop(1)) {
            val s = sign(x - best)
            if ((largest && s > 0) || (!largest && s < 0)) best = x
        }
        return best
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Complex parts.

    /** re, im or conj of [z], its letters real; refused where its parts cannot be written exactly ([Failure.NOT_POLYNOMIAL]). */
    private fun part(name: String, z: Expr): Expr {
        val (a0, b0) = parts(z) ?: fail(Failure.NOT_POLYNOMIAL)
        val a = Calculus.tidy(a0)
        val b = Calculus.tidy(b0)
        // Checked: re + i·im is z again, at numbers for the letters where they are.
        if (!Verify.same(add(a, mul(Expr.I, b)), z)) fail(Failure.NOT_POLYNOMIAL)
        return when (name) {
            "re" -> a
            "im" -> b
            else -> Calculus.tidy(add(a, neg(mul(Expr.I, b))))
        }
    }

    /** (re, im) of [e] with its letters real, or null where that cannot be said exactly. */
    private fun parts(e: Expr): Pair<Expr, Expr>? {
        if (!e.hasI()) return if (real(e)) e to Expr.ZERO else null
        return when (e) {
            Expr.I -> Expr.ZERO to Expr.ONE
            is Expr.Add -> e.terms.map { parts(it) ?: return null }.let { ps -> add(ps.map { it.first }) to add(ps.map { it.second }) }
            is Expr.Mul -> {
                var a: Expr = Expr.ONE
                var b: Expr = Expr.ZERO
                for (f in e.factors) {
                    val (c, d) = parts(f) ?: return null
                    val re = add(mul(a, c), neg(mul(b, d)))
                    b = add(mul(a, d), mul(b, c))
                    a = re
                }
                a to b
            }
            is Expr.Pow -> {
                // e^(a + bi) = e^a (cos b + i sin b)
                if (e.base == Expr.E) {
                    val (a, b) = parts(e.exp) ?: return null
                    val m = exp(a)
                    return mul(m, fn(Func.COS, b)) to mul(m, fn(Func.SIN, b))
                }
                val n = integer(e.exp)?.takeIf { it.bitLength() < 8 }?.toInt() ?: return null
                val (a, b) = parts(e.base) ?: return null
                // A negative power by the conjugate over a² + b².
                val (p, q) = if (n < 0) {
                    val m = pow(add(pow(a, Expr.TWO), pow(b, Expr.TWO)), Expr.MINUS_ONE)
                    mul(a, m) to neg(mul(b, m))
                } else a to b
                var re: Expr = Expr.ONE
                var im: Expr = Expr.ZERO
                repeat(kotlin.math.abs(n)) {
                    val r2 = add(mul(re, p), neg(mul(im, q)))
                    im = add(mul(re, q), mul(im, p))
                    re = r2
                }
                re to im
            }
            else -> null
        }
    }

    /** Functions real at every real argument. */
    private val REAL_ON_REALS = setOf(Func.SIN, Func.COS, Func.TAN, Func.COT, Func.SEC, Func.CSC, Func.ATAN, Func.ACOT, Func.SINH,
        Func.COSH, Func.TANH, Func.COTH, Func.SECH, Func.CSCH, Func.ASINH, Func.ABS, Func.FLOOR, Func.CEIL, Func.SIGN, Func.ERF,
        Func.ERFC, Func.SI)

    /** Whether [e] (without i) is real: a constant whose real value can be taken, or real at every real value of its letters. */
    private fun real(e: Expr): Boolean {
        if (e.isConstant()) {
            return try {
                Precise.eval(e, 96, false, emptyMap())
                true
            } catch (x: CalcException) {
                if (x.failure == Failure.TOO_LONG) throw x
                false
            } catch (x: Imprecise) {
                false
            }
        }
        return when (e) {
            is Expr.Num, is Expr.Real, is Expr.Sym -> true
            is Expr.Const -> e.kind == Constant.PI || e.kind == Constant.E
            is Expr.Add -> e.terms.all(::real)
            is Expr.Mul -> e.factors.all(::real)
            is Expr.Pow -> real(e.base) && real(e.exp) && (e.base == Expr.E || integer(e.exp) != null ||
                rational(e.exp)?.den?.testBit(0) == true || nonNegative(e.base) ||
                (e.base.isConstant() && try { sign(e.base) > 0 } catch (x: CalcException) { false }))
            is Expr.Fn -> e.f in REAL_ON_REALS && e.args.all(::real)
            else -> false
        }
    }

    /** arg z in (−π, π], of a number: by the signs of its parts, checked by |z|·cos φ and |z|·sin φ. */
    private fun argument(z: Expr, flags: Flags): Expr {
        if (z.symbols().isNotEmpty()) fail(Failure.ARGUMENTS)
        val (a0, b0) = parts(z) ?: fail(Failure.NOT_POLYNOMIAL)
        val a = Calculus.tidy(a0)
        val b = Calculus.tidy(b0)
        val sa = sign(a)
        val sb = sign(b)
        if (sa == 0 && sb == 0) fail(Failure.UNDEFINED)
        val half = mul(Expr.PI, Expr.HALF)
        val phi = Calculus.tidy(when {
            sa > 0 -> fn(Func.ATAN, Calculus.tidy(b / a))
            sa < 0 && sb >= 0 -> add(fn(Func.ATAN, Calculus.tidy(b / a)), Expr.PI)
            sa < 0 -> add(fn(Func.ATAN, Calculus.tidy(b / a)), neg(Expr.PI))
            sb > 0 -> half
            else -> neg(half)
        })
        val r = sqrt(add(pow(a, Expr.TWO), pow(b, Expr.TWO)))
        val back = listOf(add(mul(r, fn(Func.COS, phi)), neg(a)), add(mul(r, fn(Func.SIN, phi)), neg(b)))
        if (back.any { d -> Precise.nearZero(d) != true && !Verify.same(d, Expr.ZERO) }) fail(Failure.NOT_POLYNOMIAL)
        // An angle: in degrees where degrees are assumed, as arctan's.
        flags.met += Ambiguity.ANGLES
        if (flags.assumptions.degrees) {
            flags.degrees = true
            return Calculus.tidy(mul(phi, num(Rational.of(180)), pow(Expr.PI, Expr.MINUS_ONE)))
        }
        return phi
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Euler's beta function.

    /**
     * B(a, b) = Γ(a)Γ(b)/Γ(a + b); for a whole b = n ≥ 1, (n − 1)!/(a(a + 1)…(a + n − 1)), a fraction even with letters; for
     * a + b = 1, π/sin(πa). A value is checked against ∫₀¹ t^(a−1)(1 − t)^(b−1) dt (shifted by B(a, b) = B(a + 1, b)(a + b)/a
     * until the integrand is smooth).
     */
    private fun beta(a: Expr, b: Expr): Expr {
        if (a.hasI() || b.hasI() || a.hasInfinity() || b.hasInfinity()) fail(Failure.UNDEFINED)
        val numbers = a.isConstant() && b.isConstant()
        if (numbers) {
            for (v in listOf(a, b, a + b)) if (sign(v) <= 0 && isWhole(v)) fail(Failure.UNDEFINED)
        }
        val result = whole(b)?.let { finite(a, it) } ?: whole(a)?.let { finite(b, it) } ?: run {
            val ra = rational(a)
            val rb = rational(b)
            if (ra != null && rb != null && ra + rb == Rational.ONE) Calculus.tidy(mul(Expr.PI, pow(fn(Func.SIN, mul(num(ra), Expr.PI)), Expr.MINUS_ONE)))
            else Calculus.tidy(mul(fn(Func.GAMMA, a), fn(Func.GAMMA, b), pow(fn(Func.GAMMA, a + b), Expr.MINUS_ONE)))
        }
        if (numbers) checkBeta(result, a, b)
        else if (!Verify.same(result, mul(fn(Func.GAMMA, a), fn(Func.GAMMA, b), pow(fn(Func.GAMMA, a + b), Expr.MINUS_ONE)))) {
            fail(Failure.UNSOLVED)
        }
        return result
    }

    private fun isWhole(v: Expr): Boolean = rational(v)?.isInteger == true

    /** A whole number 1..100 that [e] is, or null. */
    private fun whole(e: Expr): Int? = integer(e)?.takeIf { it.signum() > 0 && it <= BigInteger.valueOf(100) }?.toInt()

    /** B(x, n) = (n − 1)!/(x(x + 1)…(x + n − 1)). */
    private fun finite(x: Expr, n: Int): Expr {
        if (isWhole(x) && rational(x)!!.signum <= 0) fail(Failure.UNDEFINED)
        val product = mul((0 until n).map { add(x, num(it.toLong())) })
        if (isZero(product)) fail(Failure.UNDEFINED)
        return Calculus.tidy(mul(num(Rational.of(Special.factorial(n - 1L))), pow(product, Expr.MINUS_ONE)))
    }

    /** In logarithms, so that B(300, 400) is checked as well as B(2, 3): ln|B| and its sign against the integral's. */
    private fun checkBeta(result: Expr, a: Expr, b: Expr) {
        var x = Numeric.value(a) ?: fail(Failure.UNDEFINED)
        var y = Numeric.value(b) ?: fail(Failure.UNDEFINED)
        var lnFactor = 0.0
        var negative = false
        fun shift(by: Double) {
            lnFactor += kotlin.math.ln(kotlin.math.abs(by))
            if (by < 0) negative = !negative
        }
        while (x < 1) { shift((x + y) / x); x += 1; Budget.check() }
        while (y < 1) { shift((x + y) / y); y += 1; Budget.check() }
        // The integrand over its largest value, at t* = (x − 1)/(x + y − 2).
        val top = if (x + y > 2) (x - 1) / (x + y - 2) else 0.5
        fun lnIntegrand(t: Double) = (if (x == 1.0) 0.0 else (x - 1) * kotlin.math.ln(t)) + (if (y == 1.0) 0.0 else (y - 1) * kotlin.math.ln(1 - t))
        val lnTop = lnIntegrand(top.coerceIn(1e-300, 1 - 1e-16))
        val integral = Quadrature.integrate({ t -> if (t <= 0.0 || t >= 1.0) 0.0 else kotlin.math.exp(lnIntegrand(t) - lnTop) }, 0.0, 1.0)
            ?: fail(Failure.UNSOLVED)
        if (integral <= 0) fail(Failure.UNSOLVED)
        val expected = lnFactor + lnTop + kotlin.math.ln(integral)
        val ball = Precise.eval(result, 96, false, emptyMap()).re
        if (ball.containsZero() || ball.isNegative != negative) fail(Failure.UNSOLVED)
        val actual = BallMath.ln(if (ball.isNegative) -ball else ball, 64).toDouble()
        if (kotlin.math.abs(actual - expected) > 1e-6 * maxOf(1.0, kotlin.math.abs(expected))) fail(Failure.UNSOLVED)
    }
}

/**
 * The classical orthogonal polynomials of a whole degree n (0 to 200): Legendre's Pₙ, Chebyshev's Tₙ (first kind), Hermite's
 * Hₙ (the physicists') and Laguerre's Lₙ, by their three-term recurrences, each checked coefficient by coefficient against its
 * explicit sum (Abramowitz and Stegun 22.3), then put at the argument: a number gives a number, a letter the polynomial.
 */
internal object Orthogonal {
    private const val MOST = 200

    fun at(name: String, nArg: Expr, xArg: Expr): Expr {
        if (nArg.symbols().isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        val n = integer(nArg) ?: throw CalcException(Failure.UNDEFINED)
        if (n.signum() < 0) throw CalcException(Failure.UNDEFINED)
        if (n > BigInteger.valueOf(MOST.toLong())) throw CalcException(Failure.TOO_LARGE)
        val k = n.toInt()
        val byRecurrence = recurrence(name, k)
        if (byRecurrence != explicit(name, k)) throw CalcException(Failure.UNSOLVED)
        return evaluate(byRecurrence, xArg)
    }

    /** The coefficients, the constant first, by the recurrence. */
    internal fun recurrence(name: String, n: Int): List<Rational> {
        val x = listOf(Rational.ZERO, Rational.ONE)
        var previous: List<Rational> = listOf(Rational.ONE)
        var current: List<Rational> = when (name) {
            "legendrep", "chebyshevt" -> x
            "hermiteh" -> listOf(Rational.ZERO, Rational.of(2))
            else -> listOf(Rational.ONE, Rational.of(-1))
        }
        if (n == 0) return previous
        for (m in 1 until n) {
            Budget.check()
            val mm = Rational.of(m.toLong())
            val next = when (name) {
                // (m + 1)P₍ₘ₊₁₎ = (2m + 1)x Pₘ − m P₍ₘ₋₁₎
                "legendrep" -> Poly.scale(Poly.minus(Poly.scale(Poly.shift(current), Rational.of(2L * m + 1)), Poly.scale(previous, mm)),
                    Rational.ONE / Rational.of(m + 1L))
                // T₍ₘ₊₁₎ = 2x Tₘ − T₍ₘ₋₁₎
                "chebyshevt" -> Poly.minus(Poly.scale(Poly.shift(current), Rational.of(2)), previous)
                // H₍ₘ₊₁₎ = 2x Hₘ − 2m H₍ₘ₋₁₎
                "hermiteh" -> Poly.minus(Poly.scale(Poly.shift(current), Rational.of(2)), Poly.scale(previous, Rational.of(2L * m)))
                // (m + 1)L₍ₘ₊₁₎ = (2m + 1 − x)Lₘ − m L₍ₘ₋₁₎
                else -> Poly.scale(Poly.minus(Poly.minus(Poly.scale(current, Rational.of(2L * m + 1)), Poly.shift(current)),
                    Poly.scale(previous, mm)), Rational.ONE / Rational.of(m + 1L))
            }
            previous = current
            current = next
        }
        return Poly.trim(current)
    }

    /** The coefficients by the explicit sums: a second way to the same polynomial. */
    internal fun explicit(name: String, n: Int): List<Rational> {
        val c = MutableList(n + 1) { Rational.ZERO }
        fun f(k: Int) = Special.factorial(k.toLong())
        fun binom(a: Int, b: Int) = NumberTheory.binomial(BigInteger.valueOf(a.toLong()), BigInteger.valueOf(b.toLong()))
        val two = BigInteger.valueOf(2)
        when (name) {
            // Pₙ(x) = 2⁻ⁿ Σ (−1)^k C(n, k) C(2n − 2k, n) x^(n−2k)
            "legendrep" -> for (k in 0..n / 2) {
                val v = Rational.of(binom(n, k) * binom(2 * n - 2 * k, n), two.pow(n))
                c[n - 2 * k] = if (k % 2 == 0) v else -v
            }
            // Tₙ(x) = (n/2) Σ (−1)^k (n − k − 1)!/(k!(n − 2k)!) (2x)^(n−2k), T₀ = 1
            "chebyshevt" -> if (n == 0) c[0] = Rational.ONE else for (k in 0..n / 2) {
                val v = Rational.of(BigInteger.valueOf(n.toLong()) * f(n - k - 1) * two.pow(n - 2 * k), two * f(k) * f(n - 2 * k))
                c[n - 2 * k] = if (k % 2 == 0) v else -v
            }
            // Hₙ(x) = n! Σ (−1)^m (2x)^(n−2m)/(m!(n − 2m)!)
            "hermiteh" -> for (m in 0..n / 2) {
                val v = Rational.of(f(n) * two.pow(n - 2 * m), f(m) * f(n - 2 * m))
                c[n - 2 * m] = if (m % 2 == 0) v else -v
            }
            // Lₙ(x) = Σ C(n, k) (−1)^k x^k / k!
            else -> for (k in 0..n) {
                val v = Rational.of(binom(n, k), f(k))
                c[k] = if (k % 2 == 0) v else -v
            }
        }
        return Poly.trim(c)
    }

    /** The polynomial at [x]: a number exactly (Horner), a letter as the polynomial, anything else multiplied out. */
    private fun evaluate(c: List<Rational>, x: Expr): Expr {
        if (x.isConstant()) {
            var v: Expr = Expr.ZERO
            for (k in c.indices.reversed()) {
                v = add(mul(v, x), num(c[k]))
                Budget.check()
            }
            return Calculus.tidy(v)
        }
        val p = add(c.indices.map { k -> mul(num(c[k]), pow(x, num(k.toLong()))) })
        return if (x is Expr.Sym) p else Algebra.expand(p)
    }
}

/** Polynomials in one letter with rational coefficients, the constant first. */
internal object Poly {
    fun trim(p: List<Rational>): List<Rational> {
        var n = p.size
        while (n > 0 && p[n - 1].signum == 0) n--
        return p.subList(0, n).toList()
    }

    fun degree(p: List<Rational>): Int = trim(p).size - 1

    fun plus(p: List<Rational>, q: List<Rational>): List<Rational> =
        trim(List(maxOf(p.size, q.size)) { k -> (p.getOrNull(k) ?: Rational.ZERO) + (q.getOrNull(k) ?: Rational.ZERO) })

    fun minus(p: List<Rational>, q: List<Rational>): List<Rational> = plus(p, q.map { -it })

    fun scale(p: List<Rational>, c: Rational): List<Rational> = trim(p.map { it * c })

    /** x·p */
    fun shift(p: List<Rational>): List<Rational> = if (p.isEmpty()) p else listOf(Rational.ZERO) + p

    fun times(p: List<Rational>, q: List<Rational>): List<Rational> {
        if (p.isEmpty() || q.isEmpty()) return emptyList()
        val out = MutableList(p.size + q.size - 1) { Rational.ZERO }
        for (i in p.indices) for (j in q.indices) out[i + j] = out[i + j] + p[i] * q[j]
        return trim(out)
    }

    /** (quotient, remainder) of p by q ≠ 0. */
    fun divide(p0: List<Rational>, q0: List<Rational>): Pair<List<Rational>, List<Rational>> {
        val q = trim(q0)
        require(q.isNotEmpty())
        var r = trim(p0).toMutableList()
        val quotient = MutableList(maxOf(r.size - q.size + 1, 0)) { Rational.ZERO }
        while (r.size >= q.size && r.isNotEmpty()) {
            Budget.check()
            val shift = r.size - q.size
            val c = r.last() / q.last()
            quotient[shift] = c
            for (k in q.indices) r[k + shift] = r[k + shift] - c * q[k]
            r = trim(r).toMutableList()
        }
        return trim(quotient) to r
    }

    fun monic(p: List<Rational>): List<Rational> = trim(p).let { t -> if (t.isEmpty()) t else scale(t, Rational.ONE / t.last()) }

    fun expr(p: List<Rational>, x: Expr.Sym): Expr = add(p.indices.map { k -> mul(num(p[k]), pow(x, num(k.toLong()))) })
}

/**
 * gcd and lcm of polynomials in one letter with rational coefficients, under the names of the numbers' (gcd(x² − 1, x² + 2x + 1)
 * = x + 1): the monic one, by Euclid's algorithm, checked by its Bézout certificate (Σ sᵢ·pᵢ = g with g dividing every pᵢ, so
 * every common divisor divides g); the lcm as p·q/gcd, checked: each pᵢ divides it.
 */
internal object PolynomialGcd {
    /** One division of Euclid's algorithm: [a] = [quotient]·[b] + [remainder]. */
    class Division(val a: List<Rational>, val b: List<Rational>, val quotient: List<Rational>, val remainder: List<Rational>)

    fun call(name: String, args: List<Expr>): Expr {
        if (args.size < 2 || args.any { it is Expr.Mat }) throw CalcException(Failure.ARGUMENTS)
        val letters = args.flatMap { it.symbols() }.toSet()
        if (letters.size != 1) throw CalcException(Failure.NOT_POLYNOMIAL)
        val x = Expr.Sym(letters.first())
        val ps = args.map { coefficients(it, x) ?: throw CalcException(Failure.NOT_POLYNOMIAL) }
        return Poly.expr(if (name == "gcd") gcd(ps) else lcm(ps), x)
    }

    /** The coefficients of [e] as a polynomial in [x] with rational coefficients, or null. */
    internal fun coefficients(e: Expr, x: Expr.Sym): List<Rational>? {
        val c = Solve.coefficientsIn(Algebra.expand(e), x) ?: return null
        return Poly.trim(c.map { rational(it) ?: return null })
    }

    /** The monic gcd of [ps], certified; 0 where they are all 0. */
    fun gcd(ps: List<List<Rational>>): List<Rational> {
        var g = Poly.trim(ps[0])
        // Bézout: Σ s[i]·ps[i] = g.
        var s = MutableList(ps.size) { emptyList<Rational>() }.also { it[0] = listOf(Rational.ONE) }
        for (i in 1 until ps.size) {
            val (h, u, v) = extended(g, Poly.trim(ps[i]))
            s = s.map { Poly.times(it, u) }.toMutableList()
            s[i] = v
            g = h
        }
        val monic = Poly.monic(g)
        val unit = if (g.isEmpty()) Rational.ONE else Rational.ONE / g.last()
        // The certificate: monic g = Σ (s[i]/lead)·ps[i], and g divides every ps[i].
        var combination = emptyList<Rational>()
        for (i in ps.indices) combination = Poly.plus(combination, Poly.times(Poly.scale(s[i], unit), ps[i]))
        if (combination != monic) throw CalcException(Failure.UNSOLVED)
        if (monic.isNotEmpty() && ps.any { Poly.divide(it, monic).second.isNotEmpty() }) throw CalcException(Failure.UNSOLVED)
        return monic
    }

    /** (g, u, v) with u·a + v·b = g, g a gcd of a and b. */
    private fun extended(a: List<Rational>, b: List<Rational>): Triple<List<Rational>, List<Rational>, List<Rational>> {
        var r0 = a
        var r1 = b
        var s0 = listOf(Rational.ONE)
        var s1 = emptyList<Rational>()
        var t0 = emptyList<Rational>()
        var t1 = listOf(Rational.ONE)
        while (r1.isNotEmpty()) {
            Budget.check()
            val (q, r) = Poly.divide(r0, r1)
            r0 = r1.also { r1 = r }
            s0 = s1.also { s1 = Poly.minus(s0, Poly.times(q, s1)) }
            t0 = t1.also { t1 = Poly.minus(t0, Poly.times(q, t1)) }
        }
        return Triple(r0, s0, t0)
    }

    /** Euclid's divisions for the steps: each a = q·b + r, down to the remainder 0. */
    fun divisions(a0: List<Rational>, b0: List<Rational>): List<Division> {
        val out = ArrayList<Division>()
        var a = Poly.trim(a0)
        var b = Poly.trim(b0)
        if (Poly.degree(a) < Poly.degree(b)) a = b.also { b = a }
        while (b.isNotEmpty() && out.size < 64) {
            val (q, r) = Poly.divide(a, b)
            out += Division(a, b, q, r)
            a = b
            b = r
        }
        return out
    }

    /** The monic lcm of [ps]: each step p·q/gcd(p, q), checked: every ps[i] divides it. */
    fun lcm(ps: List<List<Rational>>): List<Rational> {
        if (ps.any { Poly.trim(it).isEmpty() }) return emptyList()
        var l = Poly.monic(ps[0])
        for (i in 1 until ps.size) {
            val g = gcd(listOf(l, ps[i]))
            val (q, r) = Poly.divide(Poly.times(l, ps[i]), g)
            if (r.isNotEmpty()) throw CalcException(Failure.UNSOLVED)
            l = Poly.monic(q)
        }
        if (ps.any { Poly.divide(l, it).second.isNotEmpty() }) throw CalcException(Failure.UNSOLVED)
        return l
    }
}
