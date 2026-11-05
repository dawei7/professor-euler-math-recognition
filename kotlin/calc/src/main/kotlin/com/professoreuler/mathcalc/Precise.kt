package com.professoreuler.mathcalc

import java.math.BigInteger

/** A number's decimal digits, each one certified: the whole ball the calculation ends with agrees on them. */
sealed class Decimal {
    /** Exactly zero. */
    object Zero : Decimal() {
        override fun toString() = "0"
    }

    /**
     * ±d₁.d₂d₃… × 10^[exponent], [digits] the significant digits (the first not 0). [exact] where these digits are the
     * whole value (0.125), not a rounding of it.
     */
    data class Digits(val negative: Boolean, val digits: String, val exponent: Int, val exact: Boolean) : Decimal() {
        override fun toString(): String = (if (negative) "-" else "") + digits[0] + (if (digits.length > 1) "." + digits.substring(1) else "") +
            (if (exponent != 0) "e$exponent" else "")
    }

    /** Too small to tell from 0 at the precision allowed: |x| < 10^[below]. */
    data class Small(val below: Int) : Decimal()
}

/** The certified digits of a constant: its real part and, where it is not real, its imaginary part. */
data class PreciseValue(val re: Decimal, val im: Decimal?) {
    val isReal: Boolean get() = im == null
}

/**
 * Numbers to as many certified digits as asked for: an expression is worked out in complex [Ball]s at a precision, and the
 * digits are given only where the whole ball rounds to them; otherwise the precision is doubled and it is worked out again
 * (within [Budget]). Odd roots of negative numbers are real, as everywhere in the calculator; in [complex] mode a value may
 * be complex (ln(−1) is πi), otherwise that is [Failure.UNDEFINED].
 */
object Precise {
    /** At most this many digits may be asked for. */
    const val MAX_DIGITS = 10_000

    /** `BigInteger.TWO` is Java 9: Android has it only from version 13. */
    private val TWO: BigInteger = BigInteger.valueOf(2)

    /** The certified digits of [e] (no letters, no calculus left in it), [digits] significant digits of each part. */
    fun digits(e: Expr, digits: Int, complex: Boolean = true): PreciseValue {
        require(digits in 1..MAX_DIGITS)
        if (e.symbols().isNotEmpty()) throw CalcException(Failure.UNKNOWN_WITHOUT_EQUATION)
        if (e.hasOp()) throw CalcException(Failure.UNREADABLE)
        if (hasReal(e)) throw CalcException(Failure.UNREADABLE)
        var prec = (digits * 3.33).toInt() + 32
        val maxPrec = maxOf(prec * 16, 4096)
        var last: CBall? = null
        while (prec <= maxPrec) {
            try {
                val z = eval(e, prec, complex, emptyMap())
                last = z
                val re = decimal(z.re, digits)
                val im = if (z.isReal) null else decimal(z.im, digits)
                if (re != null && (z.isReal || im != null)) {
                    return PreciseValue(re, im?.takeIf { it != Decimal.Zero })
                }
            } catch (x: Imprecise) {
                // decide again with more bits
            }
            prec *= 2
            Budget.check()
        }
        // As many digits as the last ball allows.
        val z = last ?: throw CalcException(Failure.TOO_LONG)
        return PreciseValue(fallback(z.re, digits), if (z.isReal) null else fallback(z.im, digits))
    }

    /** The value of a constant expression as a complex ball at [prec] bits; [env] gives letters their values. */
    internal fun eval(e: Expr, prec: Int, complex: Boolean, env: Map<String, CBall>): CBall {
        Budget.check()
        val w = prec + 8
        val z = when (e) {
            is Expr.Num -> CBall(Ball.of(e.value, w))
            is Expr.Real -> CBall(Ball(BigFloat.of(e.value)))
            is Expr.Sym -> env[e.name] ?: throw CalcException(Failure.UNKNOWN_WITHOUT_EQUATION)
            is Expr.Const -> when (e.kind) {
                Constant.PI -> CBall(BallMath.pi(w))
                Constant.E -> CBall(BallMath.e(w))
                Constant.I -> CBall.I
                Constant.INF -> throw CalcException(Failure.UNDEFINED)
            }
            is Expr.Add -> e.terms.fold(CBall.ZERO) { acc, t -> acc.add(eval(t, w, complex, env), w) }
            is Expr.Mul -> e.factors.fold(CBall.ONE) { acc, f -> acc.mul(eval(f, w, complex, env), w) }
            is Expr.Pow -> power(e, w, complex, env)
            is Expr.Fn -> function(e.f, e.args, w, complex, env)
            is Expr.Op -> throw CalcException(Failure.UNREADABLE)
        }
        if (!complex && !z.isReal) {
            if (z.im.containsZero()) throw Imprecise()
            throw CalcException(Failure.UNDEFINED)
        }
        return z
    }

    private fun power(e: Expr.Pow, w: Int, complex: Boolean, env: Map<String, CBall>): CBall {
        if (e.base == Expr.E) return CMath.exp(eval(e.exp, w, complex, env), w)
        val base = eval(e.base, w, complex, env)
        val r = rational(e.exp)
        if (r != null) {
            if (base.isReal && base.re.isExactZero) {
                if (r.signum > 0) return CBall.ZERO
                throw CalcException(Failure.DIVISION_BY_ZERO)
            }
            if (r.isInteger) return CMath.powInt(base, r.num, w)
            if (base.isReal) {
                val b = base.re
                if (b.isPositive) {
                    if (r.den == TWO) return CBall(BallMath.sqrt(b, w).pow(r.num, w))
                    return CBall(BallMath.pow(b, Ball.of(r, w), w))
                }
                if (b.isNegative) {
                    // Odd roots of negatives are real: ∛(−8) = −2.
                    if (r.den.testBit(0)) {
                        val m = BallMath.pow(-b, Ball.of(r, w), w)
                        return CBall(if (r.num.testBit(0)) -m else m)
                    }
                    if (!complex) throw CalcException(Failure.UNDEFINED)
                    // (−x)^(p/q) = x^(p/q)·e^(iπp/q)
                    val m = BallMath.pow(-b, Ball.of(r, w), w)
                    val angle = BallMath.pi(w).mul(Ball.of(r, w), w)
                    return CMath.polar(m, angle, w)
                }
                throw Imprecise()
            }
        }
        val exponent = eval(e.exp, w, complex, env)
        if (base.isReal && exponent.isReal && base.re.isPositive) return CBall(BallMath.pow(base.re, exponent.re, w))
        if (base.isReal && base.re.isExactZero) {
            if (exponent.isReal && exponent.re.isPositive) return CBall.ZERO
            throw CalcException(Failure.UNDEFINED)
        }
        return CMath.exp(exponent.mul(CMath.ln(base, w), w), w)
    }

    private fun function(f: Func, args: List<Expr>, w: Int, complex: Boolean, env: Map<String, CBall>): CBall {
        if (f == Func.FACTORIAL) {
            val r = rational(args[0]) ?: run {
                val v = eval(args[0], w, complex, env)
                if (!v.isReal) throw CalcException(Failure.UNDEFINED)
                val n = Special.smallInteger(v.re) ?: throw Imprecise()
                Rational.of(n)
            }
            if (!r.isInteger || r.signum < 0) throw CalcException(Failure.UNDEFINED)
            if (r.num > BigInteger.valueOf(Limits.MAX_FACTORIAL.toLong())) throw CalcException(Failure.TOO_LARGE)
            return CBall(Ball.of(Special.factorial(r.num.toLong())).rounded(w))
        }
        if (f == Func.BESSELJ) {
            val order = rational(args[0])?.takeIf { it.isInteger }?.num ?: throw CalcException(Failure.UNDEFINED)
            if (order.bitLength() > 31) throw CalcException(Failure.TOO_LARGE)
            val x = eval(args[1], w, complex, env)
            if (!x.isReal) throw CalcException(Failure.UNDEFINED)
            return CBall(Special.besselJ(order.toLong(), x.re, w))
        }
        if (f == Func.LOG) {
            val base = eval(args[0], w, complex, env)
            val x = eval(args[1], w, complex, env)
            return CMath.ln(x, w).div(CMath.ln(base, w), w)
        }
        val z = eval(args[0], w, complex, env)
        return when (f) {
            Func.SIN -> CMath.sin(z, w)
            Func.COS -> CMath.cos(z, w)
            Func.TAN -> CMath.sin(z, w).div(CMath.cos(z, w), w)
            Func.COT -> CMath.cos(z, w).div(CMath.sin(z, w), w)
            Func.SEC -> CBall.ONE.div(CMath.cos(z, w), w)
            Func.CSC -> CBall.ONE.div(CMath.sin(z, w), w)
            Func.ASIN -> CMath.asin(z, w, complex)
            Func.ACOS -> CMath.acos(z, w, complex)
            Func.ATAN -> CMath.atan(z, w)
            // arccot in (0, π), as the exact values have it: π/2 − arctan x
            Func.ACOT -> CBall(BallMath.pi(w).shl(-1)).sub(CMath.atan(z, w), w)
            Func.SINH -> CMath.sinh(z, w)
            Func.COSH -> CMath.cosh(z, w)
            Func.TANH -> CMath.sinh(z, w).div(CMath.cosh(z, w), w)
            Func.LN -> CMath.ln(z, w)
            Func.ABS -> CBall(if (z.isReal) abs(z.re) else BallMath.sqrt(z.norm(w), w))
            Func.FLOOR, Func.CEIL -> {
                if (!z.isReal) throw CalcException(Failure.UNDEFINED)
                CBall(Ball.of(integerPart(z.re, ceiling = f == Func.CEIL)))
            }
            Func.SIGN -> {
                if (!z.isReal) throw CalcException(Failure.UNDEFINED)
                when {
                    z.re.isExactZero -> CBall.ZERO
                    z.re.isPositive -> CBall.ONE
                    z.re.isNegative -> CBall(Ball.of(-1))
                    else -> throw Imprecise()
                }
            }
            Func.GAMMA, Func.DIGAMMA, Func.ERF, Func.ERFC, Func.ZETA, Func.LAMBERTW -> {
                if (!z.isReal) throw CalcException(Failure.UNDEFINED)
                val x = z.re
                CBall(
                    when (f) {
                        Func.GAMMA -> Special.gamma(x, w)
                        Func.DIGAMMA -> Special.digamma(x, w)
                        Func.ERF -> Special.erf(x, w)
                        Func.ERFC -> Special.erfc(x, w)
                        Func.ZETA -> Special.zeta(x, w)
                        else -> Special.lambertW(x, w)
                    },
                )
            }
            Func.LOG, Func.FACTORIAL, Func.BESSELJ -> throw IllegalStateException()
        }
    }

    private fun abs(x: Ball): Ball = when {
        x.isPositive || x.isExactZero -> x
        x.isNegative -> -x
        else -> Ball.span(BigFloat.ZERO, x.magUp())
    }

    private fun integerPart(x: Ball, ceiling: Boolean): BigInteger {
        if (x.isExact) return if (ceiling) (-x.mid).floor().negate() else x.mid.floor()
        val lo = x.lower
        val hi = x.upper
        val a = if (ceiling) (-lo).floor().negate() else lo.floor()
        val b = if (ceiling) (-hi).floor().negate() else hi.floor()
        if (a != b) throw Imprecise()
        // A ball reaching an integer from one side cannot tell floor(n) from floor(n − ε).
        if (!ceiling && (hi.isInteger || lo.isInteger)) throw Imprecise()
        if (ceiling && (hi.isInteger || lo.isInteger)) throw Imprecise()
        return a
    }

    private fun hasReal(e: Expr): Boolean = when (e) {
        is Expr.Real -> true
        is Expr.Add -> e.terms.any(::hasReal)
        is Expr.Mul -> e.factors.any(::hasReal)
        is Expr.Pow -> hasReal(e.base) || hasReal(e.exp)
        is Expr.Fn -> e.args.any(::hasReal)
        else -> false
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Digits of a ball.

    /** [n] significant digits that every number in [ball] rounds to (half to even), or null where they differ. */
    internal fun decimal(ball: Ball, n: Int): Decimal? {
        if (ball.isExactZero) return Decimal.Zero
        if (ball.containsZero()) return null
        val negative = ball.mid.signum < 0
        val mag = ball.mid.abs()
        if (ball.isExact) exactDecimal(mag, n)?.let { (digits, exponent) -> return Decimal.Digits(negative, digits, exponent, exact = true) }
        val lo = (mag - ball.rad).toRational()
        val hi = (mag + ball.rad).toRational()
        var exponent = decadeOf(hi, mag)
        val loDigits = roundScaled(lo, n - 1 - exponent)
        val hiDigits = roundScaled(hi, n - 1 - exponent)
        if (loDigits != hiDigits) return null
        var digits = hiDigits.toString()
        if (digits.length > n) {
            // 9.99… rounded up to 10.0…
            exponent += 1
            digits = digits.substring(0, n)
        }
        if (digits.length < n) return null
        return Decimal.Digits(negative, digits, exponent, exact = false)
    }

    /** The digits of an exact binary number (x > 0) where they end within [n] significant digits, and its decade. */
    private fun exactDecimal(x: BigFloat, n: Int): Pair<String, Int>? {
        if (x.exp < -4L * n - 64 || x.top > 4L * n + 64) return null
        // m·2^−k = m·5^k / 10^k
        val k = if (x.exp < 0) (-x.exp).toInt() else 0
        val whole = if (k > 0) x.man.abs() * BigInteger.valueOf(5).pow(k) else x.man.abs().shiftLeft(x.exp.toInt())
        val s = whole.toString()
        val digits = s.trimEnd('0')
        if (digits.length > n) return null
        return digits to s.length - 1 - k
    }

    /** E with 10^E ≤ x < 10^(E+1), for x > 0, using [estimate]'s size as a start. */
    private fun decadeOf(x: Rational, estimate: BigFloat): Int {
        var e = kotlin.math.floor(estimate.top * 0.30102999566398120).toInt()
        if (kotlin.math.abs(e) > 1_000_000) throw CalcException(Failure.TOO_LARGE)
        while (x < tenTo(e)) e--
        while (x >= tenTo(e + 1)) e++
        return e
    }

    private fun tenTo(e: Int): Rational = if (e >= 0) Rational.of(BigInteger.TEN.pow(e)) else Rational.of(BigInteger.ONE, BigInteger.TEN.pow(-e))

    /** x·10^shift rounded to a whole number, ties to even. */
    private fun roundScaled(x: Rational, shift: Int): BigInteger {
        val scaled = x * tenTo(shift)
        val (q, rem) = scaled.num.divideAndRemainder(scaled.den).let { it[0] to it[1] }
        val twice = rem.shiftLeft(1)
        val c = twice.compareTo(scaled.den)
        return if (c > 0 || (c == 0 && q.testBit(0))) q + BigInteger.ONE else q
    }

    /** At the precision's end: as many digits as the ball agrees on, or how small it is. */
    private fun fallback(ball: Ball, n: Int): Decimal {
        for (d in n - 1 downTo 1) decimal(ball, d)?.let { return it }
        val bound = ball.magUp()
        if (bound.isZero) return Decimal.Zero
        return Decimal.Small(decadeOf(bound.toRational(), bound) + 1)
    }
}

/** Complex elementary functions on [CBall]s: the principal values, as the real ones where the value is real. */
internal object CMath {
    fun exp(z: CBall, w: Int): CBall {
        if (z.isReal) return CBall(BallMath.exp(z.re, w))
        return polar(BallMath.exp(z.re, w), z.im, w)
    }

    /** r·(cos θ + i sin θ) */
    fun polar(r: Ball, theta: Ball, w: Int): CBall {
        val (s, c) = sinCos(theta, w)
        return CBall(r.mul(c, w), r.mul(s, w))
    }

    private fun sinCos(x: Ball, w: Int): Pair<Ball, Ball> {
        val (s, c) = BallMath.sinCosPoint(x.mid, w)
        return s.widen(x.rad) to c.widen(x.rad)
    }

    /** ln|z| + i·arg z, arg in (−π, π]. */
    fun ln(z: CBall, w: Int): CBall {
        if (z.isReal) {
            val x = z.re
            if (x.isPositive) return CBall(BallMath.ln(x, w))
            if (x.isExactZero) throw CalcException(Failure.UNDEFINED)
            if (x.isNegative) return CBall(BallMath.ln(-x, w), BallMath.pi(w))
            throw Imprecise()
        }
        val modulus = BallMath.ln(z.norm(w), w).shl(-1)
        return CBall(modulus, BallMath.atan2(z.im, z.re, w))
    }

    fun powInt(z: CBall, n: BigInteger, w: Int): CBall {
        if (z.isReal) return CBall(z.re.pow(n, w))
        if (n.signum() == 0) return CBall.ONE
        if (n.signum() < 0) return CBall.ONE.div(powInt(z, n.negate(), w + 4), w)
        val work = w + n.bitLength() + 4
        var result = CBall.ONE
        for (i in n.bitLength() - 1 downTo 0) {
            result = result.mul(result, work)
            if (n.testBit(i)) result = result.mul(z, work)
            Budget.check()
        }
        return result
    }

    fun sqrt(z: CBall, w: Int): CBall {
        if (z.isReal && !z.re.isNegative) return CBall(BallMath.sqrt(z.re, w))
        if (z.isReal) return CBall(Ball.ZERO, BallMath.sqrt(-z.re, w))
        return exp(ln(z, w + 8).let { CBall(it.re.shl(-1), it.im.shl(-1)) }, w)
    }

    fun sin(z: CBall, w: Int): CBall {
        if (z.isReal) return CBall(BallMath.sin(z.re, w))
        // sin(a + bi) = sin a cosh b + i cos a sinh b
        val (s, c) = sinCos(z.re, w)
        return CBall(s.mul(BallMath.cosh(z.im, w), w), c.mul(BallMath.sinh(z.im, w), w))
    }

    fun cos(z: CBall, w: Int): CBall {
        if (z.isReal) return CBall(BallMath.cos(z.re, w))
        // cos(a + bi) = cos a cosh b − i sin a sinh b
        val (s, c) = sinCos(z.re, w)
        return CBall(c.mul(BallMath.cosh(z.im, w), w), -s.mul(BallMath.sinh(z.im, w), w))
    }

    fun sinh(z: CBall, w: Int): CBall {
        if (z.isReal) return CBall(BallMath.sinh(z.re, w))
        // sinh(a + bi) = sinh a cos b + i cosh a sin b
        val (s, c) = sinCos(z.im, w)
        return CBall(BallMath.sinh(z.re, w).mul(c, w), BallMath.cosh(z.re, w).mul(s, w))
    }

    fun cosh(z: CBall, w: Int): CBall {
        if (z.isReal) return CBall(BallMath.cosh(z.re, w))
        // cosh(a + bi) = cosh a cos b + i sinh a sin b
        val (s, c) = sinCos(z.im, w)
        return CBall(BallMath.cosh(z.re, w).mul(c, w), BallMath.sinh(z.re, w).mul(s, w))
    }

    fun asin(z: CBall, w: Int, complex: Boolean): CBall {
        if (z.isReal && Ball.of(-1).below(z.re) && z.re.below(Ball.ONE)) return CBall(BallMath.asin(z.re, w))
        if (z.isReal && z.re.isExact && z.re.mid.abs() == BigFloat.ONE) return CBall(BallMath.asin(z.re, w))
        if (!complex) {
            if (z.isReal && (z.re.below(Ball.of(-1)) || Ball.ONE.below(z.re))) throw CalcException(Failure.UNDEFINED)
            throw Imprecise()
        }
        // −i·ln(iz + √(1 − z²))
        val iz = CBall(-z.im, z.re)
        val root = sqrt(CBall.ONE.sub(z.mul(z, w), w), w)
        val l = ln(iz.add(root, w), w)
        return CBall(l.im, -l.re)
    }

    fun acos(z: CBall, w: Int, complex: Boolean): CBall = CBall(BallMath.pi(w).shl(-1)).sub(asin(z, w, complex), w)

    fun atan(z: CBall, w: Int): CBall {
        if (z.isReal) return CBall(BallMath.atan(z.re, w))
        // (i/2)·(ln(1 − iz) − ln(1 + iz))
        val iz = CBall(-z.im, z.re)
        val d = ln(CBall.ONE.sub(iz, w), w).sub(ln(CBall.ONE.add(iz, w), w), w)
        return CBall(-d.im.shl(-1), d.re.shl(-1))
    }
}
