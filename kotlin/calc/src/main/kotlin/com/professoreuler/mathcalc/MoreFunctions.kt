package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs

/**
 * The functions the Calculator gained for "every mathematical function which is reasonably used" (owner, 6 November 2026),
 * each in one place: its exact values, its double, its certified ball, its derivative and its integral.
 *
 * - coth, sech, csch, and the inverse hyperbolic functions under their ISO 80000-2 names only (arsinh, arcosh, artanh, arcoth:
 *   one name each; asinh, arcsinh … stay unknown names, refused, never misread). arsinh(3/4) = ln 2, as solving sinh x = 3/4
 *   gives; artanh of a rational is ½ ln((1 + r)/(1 − r)).
 * - arcsec x = arccos(1/x), arccsc x = arcsin(1/x), for |x| ≥ 1, with the exact values of arccos and arcsin (arcsec 2 = π/3).
 * - The exponential, sine and cosine integrals Ei, Si, Ci (by their power series, guard bits for the cancellation, the tail
 *   bounded by its first term once the terms fall by half), Bessel's Y_n (Abramowitz and Stegun 9.1.11) and the complete
 *   elliptic integrals K(k) and E(k) of the modulus k (the arithmetic–geometric mean, its limit enclosed between aₙ and bₙ).
 *
 * In the complex numbers (the Calculator's default) arcosh x is i·arccos x on [−1, 1] and arcosh(−x) + iπ below −1, artanh x
 * is ½ ln|(1 + x)/(1 − x)| + iπ/2 beyond ±1 (the value from above the cut, as C99 and Mathematica give); arcoth on [−1, 1],
 * where the conventions differ, is not defined, and neither are Ei, Si, Ci, Y and K, E off the real line.
 */
internal object MoreFunctions {
    private val R1 = Rational.ONE
    private val PI_OVER_TWO by lazy { mul(Expr.PI, Expr.HALF) }

    private fun undefined(): Nothing = throw CalcException(Failure.UNDEFINED)

    /** The functions of one argument whose sign comes out (odd) or goes (even): sinh(−x) = −sinh x, cosh(−x) = cosh x. */
    private val ODD = setOf(Func.SINH, Func.TANH, Func.COTH, Func.CSCH, Func.ASINH, Func.ATANH, Func.ACOTH, Func.ACSC, Func.SI)
    private val EVEN = setOf(Func.COSH, Func.SECH, Func.ELLIPTICK, Func.ELLIPTICE)

    /** f(−x) as ±f(x) for an odd or even [f] whose argument has a negative coefficient; null otherwise. */
    fun oddOrEven(f: Func, x: Expr): Expr? {
        if (f !in ODD && f !in EVEN) return null
        val (k, rest) = splitCoefficient(x)
        if (k !is Expr.Num || k.value.signum >= 0) return null
        val flipped = fn(f, mul(Expr.Num(-k.value), rest))
        return if (f in EVEN) flipped else neg(flipped)
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Exact values.

    fun known(f: Func, args: List<Expr>): Expr? {
        val x = args.last()
        if (x is Expr.Real && (f != Func.BESSELY || args[0] is Expr.Num)) {
            val v = double(f, args.map { (it as? Expr.Real)?.value ?: Numeric.eval(it, emptyMap()) })
            if (!v.isFinite()) undefined()
            return Expr.Real(v)
        }
        val r = rational(x)
        val complex = Domain.isComplex
        when (f) {
            Func.COTH, Func.SECH, Func.CSCH -> {
                if (isZero(x)) return if (f == Func.SECH) Expr.ONE else undefined()
                oddOrEven(f, x)?.let { return it }
                expOfLog(x)?.let { q -> return num(hyperbolic(f, q)) }
                if (f == Func.COTH && x is Expr.Fn && x.f == Func.ACOTH) return x.args[0]
            }
            Func.ASINH -> {
                if (r != null) {
                    if (r.signum == 0) return Expr.ZERO
                    // arsinh r = ln(r + √(r² + 1)) where the root is rational: arsinh(3/4) = ln 2.
                    (r * r + R1).root(2)?.let { q -> return if (r.signum > 0) ln(num(r + q)) else neg(ln(num(-r + q))) }
                    if (r.signum < 0) return neg(fn(f, num(-r)))
                    return null
                }
                oddOrEven(f, x)?.let { return it }
                if (x is Expr.Fn && x.f == Func.SINH && !x.args[0].hasI()) return x.args[0]
            }
            Func.ACOSH -> {
                if (r != null) {
                    if (r == R1) return Expr.ZERO
                    if (r > R1) return (r * r - R1).root(2)?.let { q -> ln(num(r + q)) }
                    if (!complex) undefined()
                    // i·arccos r on [−1, 1]; below −1, arcosh|r| + iπ.
                    if (r >= -R1) return mul(Expr.I, fn(Func.ACOS, x))
                    return add(fn(Func.ACOSH, num(-r)), mul(Expr.I, Expr.PI))
                }
            }
            Func.ATANH -> {
                if (r != null) {
                    if (r.signum == 0) return Expr.ZERO
                    val a = r.abs()
                    if (a == R1) undefined()
                    // ½ ln((1 + r)/(1 − r)): always the logarithm of a rational number; odd, artanh(−1/3) = −ln(2)/2.
                    if (a < R1) return if (r.signum < 0) neg(fn(f, num(-r))) else mul(Expr.HALF, ln(num((R1 + r) / (R1 - r))))
                    if (!complex) undefined()
                    return add(mul(Expr.HALF, ln(num((r + R1) / (r - R1)))), mul(Expr.I, PI_OVER_TWO))
                }
                oddOrEven(f, x)?.let { return it }
                if (x is Expr.Fn && x.f == Func.TANH && !x.args[0].hasI()) return x.args[0]
            }
            Func.ACOTH -> {
                if (r != null) {
                    if (r.abs() <= R1) undefined()
                    if (r.signum < 0) return neg(fn(f, num(-r)))
                    return mul(Expr.HALF, ln(num((r + R1) / (r - R1))))
                }
                oddOrEven(f, x)?.let { return it }
                if (x is Expr.Fn && x.f == Func.COTH && !x.args[0].hasI()) return x.args[0]
            }
            Func.ASEC, Func.ACSC -> {
                if (isZero(x)) undefined()
                if (r != null && r.abs() < R1) return if (complex) null else undefined()
                if (x.hasI()) return null
                // arcsec x = arccos(1/x), arccsc x = arcsin(1/x): their exact values where 1/x is a known cosine or sine.
                val inverse = pow(x, Expr.MINUS_ONE)
                val known = try { Trig.inverse(if (f == Func.ASEC) Func.ACOS else Func.ASIN, inverse) } catch (e: CalcException) { null }
                if (known != null) return known
                if (f == Func.ACSC) oddOrEven(f, x)?.let { return it }
            }
            Func.EI -> if (isZero(x)) undefined()
            Func.SI -> {
                if (isZero(x)) return Expr.ZERO
                oddOrEven(f, x)?.let { return it }
            }
            Func.CI -> if (r != null && r.signum <= 0) undefined()
            Func.BESSELY -> {
                val order = rational(args[0]) ?: return null
                if (!order.isInteger) undefined()
                if (r != null && r.signum <= 0) undefined()
                // Y₋ₙ = (−1)ⁿ Yₙ
                if (order.signum < 0) {
                    val y = fn(Func.BESSELY, num(-order), x)
                    return if (order.num.testBit(0)) neg(y) else y
                }
            }
            Func.ELLIPTICK, Func.ELLIPTICE -> {
                if (isZero(x)) return PI_OVER_TWO
                if (r != null && r.abs() == R1) return if (f == Func.ELLIPTICE) Expr.ONE else undefined()
                if (r != null && r.abs() > R1) undefined()
                oddOrEven(f, x)?.let { return it }
                // The lemniscatic case k = 1/√2: K = Γ(1/4)²/(4√π), E = π^(3/2)/Γ(1/4)² + Γ(1/4)²/(8√π) (Legendre's relation).
                if (x == mul(Expr.HALF, sqrt(Expr.TWO))) {
                    val g2 = pow(fn(Func.GAMMA, num(Rational.of(1) / Rational.of(4))), Expr.TWO)
                    val k = mul(g2, pow(mul(num(4), sqrt(Expr.PI)), Expr.MINUS_ONE))
                    return if (f == Func.ELLIPTICK) k
                    else add(mul(pow(Expr.PI, num(Rational.of(3) / Rational.of(2))), pow(g2, Expr.MINUS_ONE)), mul(k, Expr.HALF))
                }
            }
            else -> {}
        }
        return null
    }

    /** sinh, cosh, tanh (and coth, sech, csch) at x = c·ln r, exactly, as rational functions of e^x = r^c; null elsewhere. */
    fun atLogarithm(f: Func, x: Expr): Expr? = expOfLog(x)?.let { q -> num(hyperbolic(f, q)) }

    /** e^x where x = c·ln r, c a whole number and r a positive rational: then e^x = r^c. */
    private fun expOfLog(x: Expr): Rational? {
        val (c, l) = when {
            x is Expr.Fn && x.f == Func.LN -> R1 to x
            x is Expr.Mul && x.factors.size == 2 && x.factors[0] is Expr.Num && x.factors[1].let { it is Expr.Fn && it.f == Func.LN } ->
                (x.factors[0] as Expr.Num).value to x.factors[1]
            else -> return null
        }
        val base = rational((l as Expr.Fn).args[0]) ?: return null
        if (!c.isInteger || c.num.abs() > BigInteger.valueOf(64) || base.signum <= 0) return null
        return base.pow(c.num.toInt())
    }

    /** coth, sech or csch at x where e^x = q. */
    private fun hyperbolic(f: Func, q: Rational): Rational {
        val q2 = q * q
        return when (f) {
            Func.SINH -> (q2 - R1) / (Rational.of(2) * q)
            Func.COSH -> (q2 + R1) / (Rational.of(2) * q)
            Func.TANH -> (q2 - R1) / (q2 + R1)
            Func.COTH -> if (q2 == R1) undefined() else (q2 + R1) / (q2 - R1)
            Func.SECH -> Rational.of(2) * q / (q2 + R1)
            else -> if (q2 == R1) undefined() else Rational.of(2) * q / (q2 - R1)
        }
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Doubles: for graphs and the checks, NaN outside the real domain.

    private const val DOUBLE_BITS = 64

    fun double(f: Func, a: List<Double>): Double {
        val x = a.last()
        return when (f) {
            Func.COTH -> 1 / kotlin.math.tanh(x)
            Func.SECH -> 1 / kotlin.math.cosh(x)
            Func.CSCH -> 1 / kotlin.math.sinh(x)
            Func.ASINH -> kotlin.math.asinh(x)
            Func.ACOSH -> if (x >= 1) kotlin.math.acosh(x) else Double.NaN
            Func.ATANH -> if (abs(x) < 1) kotlin.math.atanh(x) else Double.NaN
            Func.ACOTH -> if (abs(x) > 1) kotlin.math.atanh(1 / x) else Double.NaN
            Func.ASEC -> if (abs(x) >= 1) kotlin.math.acos(1 / x) else Double.NaN
            Func.ACSC -> if (abs(x) >= 1) kotlin.math.asin(1 / x) else Double.NaN
            Func.BESSELY -> {
                val n = a[0]
                if (n != kotlin.math.floor(n) || abs(n) > 1000) Double.NaN else fromBall(x) { besselY(n.toLong(), it, DOUBLE_BITS) }
            }
            Func.EI, Func.SI, Func.CI, Func.ELLIPTICK, Func.ELLIPTICE -> fromBall(x) { real(f, it, DOUBLE_BITS) }
            else -> Double.NaN
        }
    }

    private inline fun fromBall(x: Double, f: (Ball) -> Ball): Double {
        if (!x.isFinite()) return Double.NaN
        return try {
            f(Ball(BigFloat.of(x))).toDouble()
        } catch (e: CalcException) {
            if (e.failure == Failure.TOO_LONG) throw e
            Double.NaN
        } catch (e: Imprecise) {
            Double.NaN
        }
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Certified balls.

    /** [f] of the balls [a] (the order first for Y) at [w] bits; complex values only where [complex]. */
    fun ball(f: Func, a: List<CBall>, w: Int, complex: Boolean): CBall {
        val z = a.last()
        return when (f) {
            Func.COTH -> CMath.cosh(z, w).div(CMath.sinh(z, w), w)
            Func.SECH -> CBall.ONE.div(CMath.cosh(z, w), w)
            Func.CSCH -> CBall.ONE.div(CMath.sinh(z, w), w)
            Func.ASINH -> asinh(z, w)
            Func.ACOSH -> acosh(z, w, complex)
            Func.ATANH -> atanh(z, w, complex)
            Func.ACOTH -> acoth(z, w, complex)
            Func.ASEC, Func.ACSC -> {
                if (z.isReal && z.re.isExactZero) undefined()
                val inverse = CBall.ONE.div(z, w)
                if (f == Func.ASEC) CMath.acos(inverse, w, complex) else CMath.asin(inverse, w, complex)
            }
            Func.BESSELY -> {
                val order = a[0]
                if (!order.isReal) undefined()
                val n = Special.smallInteger(order.re) ?: if (order.re.isExact) undefined() else throw Imprecise()
                CBall(besselY(n, realPart(z), w))
            }
            else -> CBall(real(f, realPart(z), w))
        }
    }

    private fun realPart(z: CBall): Ball {
        if (!z.isReal) undefined()
        return z.re
    }

    private fun asinh(z: CBall, w: Int): CBall {
        if (!z.isReal) return CMath.ln(z.add(CMath.sqrt(z.mul(z, w).add(CBall.ONE, w), w), w), w)
        val x = z.re
        if (x.isExactZero) return CBall.ZERO
        // ln(|x| + √(x² + 1)) with the sign of x: no cancellation below 0.
        val negative = x.isNegative
        val ax = if (negative) -x else x
        val v = BallMath.ln(ax.add(BallMath.sqrt(ax.sqr(w).add(Ball.ONE, w), w), w), w)
        return CBall(if (negative) -v else v)
    }

    private fun acosh(z: CBall, w: Int, complex: Boolean): CBall {
        if (!z.isReal) return CMath.ln(z.add(CMath.sqrt(z.add(CBall.ONE, w), w).mul(CMath.sqrt(z.sub(CBall.ONE, w), w), w), w), w)
        val x = z.re
        val minusOne = Ball.of(-1)
        if (x.isExact && x.mid == BigFloat.ONE) return CBall.ZERO
        if (Ball.ONE.below(x)) return CBall(BallMath.ln(x.add(BallMath.sqrt(x.sqr(w).sub(Ball.ONE, w), w), w), w))
        if (!complex) {
            if (x.below(Ball.ONE)) undefined()
            throw Imprecise()
        }
        if (x.isExact && x.mid == -BigFloat.ONE) return CBall(Ball.ZERO, BallMath.pi(w))
        if (minusOne.below(x) && x.below(Ball.ONE)) return CBall(Ball.ZERO, BallMath.acos(x, w))
        if (x.below(minusOne)) {
            val m = -x
            return CBall(BallMath.ln(m.add(BallMath.sqrt(m.sqr(w).sub(Ball.ONE, w), w), w), w), BallMath.pi(w))
        }
        throw Imprecise()
    }

    private fun atanh(z: CBall, w: Int, complex: Boolean): CBall {
        if (!z.isReal) {
            val d = CMath.ln(CBall.ONE.add(z, w), w).sub(CMath.ln(CBall.ONE.sub(z, w), w), w)
            return CBall(d.re.shl(-1), d.im.shl(-1))
        }
        val x = z.re
        if (x.isExactZero) return CBall.ZERO
        val minusOne = Ball.of(-1)
        if (x.isExact && x.mid.abs() == BigFloat.ONE) undefined()
        if (minusOne.below(x) && x.below(Ball.ONE)) {
            return CBall(BallMath.ln(Ball.ONE.add(x, w).div(Ball.ONE.sub(x, w), w), w).shl(-1))
        }
        val outside = x.below(minusOne) || Ball.ONE.below(x)
        if (!outside) throw Imprecise()
        if (!complex) undefined()
        // ½ ln((x + 1)/(x − 1)) + iπ/2 beyond ±1.
        return CBall(BallMath.ln(x.add(Ball.ONE, w).div(x.sub(Ball.ONE, w), w), w).shl(-1), BallMath.pi(w).shl(-1))
    }

    private fun acoth(z: CBall, w: Int, complex: Boolean): CBall {
        if (!z.isReal) return atanh(CBall.ONE.div(z, w), w, complex)
        val x = z.re
        val minusOne = Ball.of(-1)
        if (x.below(minusOne) || Ball.ONE.below(x)) {
            return CBall(BallMath.ln(x.add(Ball.ONE, w).div(x.sub(Ball.ONE, w), w), w).shl(-1))
        }
        if (minusOne.below(x) && x.below(Ball.ONE) || x.isExact && x.mid.abs() == BigFloat.ONE) undefined()
        throw Imprecise()
    }

    /** Ei, Si, Ci, K or E of a real ball. */
    private fun real(f: Func, x: Ball, prec: Int): Ball = when (f) {
        Func.EI -> ei(x, prec)
        Func.SI -> si(x, prec)
        Func.CI -> ci(x, prec)
        Func.ELLIPTICK -> elliptic(x, prec, second = false)
        Func.ELLIPTICE -> elliptic(x, prec, second = true)
        else -> throw IllegalStateException()
    }

    private const val LOG2E = 1.4426950408889634

    /** Euler's constant γ = −ψ(1). */
    private fun eulerGamma(w: Int): Ball {
        if (w <= CACHED_BITS) return cachedGamma
        return -Special.digamma(Ball.ONE, w)
    }

    private const val CACHED_BITS = 1024
    private val cachedGamma: Ball by lazy { -Special.digamma(Ball.ONE, CACHED_BITS + 32) }

    /** An upper bound of |x| as a double, a little above. */
    private fun size(x: Ball): Double = x.magUp().toDouble() * (1 + 1e-12) + 1e-300

    private fun tooLarge(xd: Double) {
        if (xd > 20_000) throw CalcException(Failure.TOO_LARGE)
    }

    /** Si x = Σ (−1)^k x^(2k+1) / ((2k + 1)·(2k + 1)!). */
    private fun si(x: Ball, prec: Int): Ball {
        if (x.isExactZero) return Ball.ZERO
        val xd = size(x)
        tooLarge(xd)
        val w = prec + (xd * LOG2E).toInt() + 32
        val x2 = x.sqr(w)
        var p = x
        var sum = x
        var k = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            p = -p.mul(x2, w).div(Ball.of(2 * k * (2 * k + 1)), w)
            val t = p.div(Ball.of(2 * k + 1), w)
            sum = sum.add(t, w)
            // From here each term is below half the one before: the tail is below this one.
            if ((2.0 * k + 2) * (2 * k + 3) > 2 * xd * xd + 1 && t.magUp() < limit) return sum.widen(t.magUp()).rounded(prec)
            k++
            Budget.check()
        }
    }

    /** Ci x = γ + ln x + Σ_(k≥1) (−1)^k x^(2k) / (2k·(2k)!), for x > 0. */
    private fun ci(x: Ball, prec: Int): Ball {
        if (!x.isPositive) {
            if (x.upper.signum <= 0) undefined()
            throw Imprecise()
        }
        val xd = size(x)
        tooLarge(xd)
        val w = prec + (xd * LOG2E).toInt() + 32
        val x2 = x.sqr(w)
        var p = Ball.ONE
        var sum = eulerGamma(w).add(BallMath.ln(x, w), w)
        var k = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            p = -p.mul(x2, w).div(Ball.of((2 * k - 1) * (2 * k)), w)
            val t = p.div(Ball.of(2 * k), w)
            sum = sum.add(t, w)
            if ((2.0 * k + 1) * (2 * k + 2) > 2 * xd * xd + 1 && t.magUp() < limit) return sum.widen(t.magUp()).rounded(prec)
            k++
            Budget.check()
        }
    }

    /** Ei x = γ + ln|x| + Σ_(k≥1) x^k / (k·k!), for x ≠ 0 (its principal value for x > 0). */
    private fun ei(x: Ball, prec: Int): Ball {
        if (x.isExactZero) undefined()
        if (x.containsZero()) throw Imprecise()
        val xd = size(x)
        tooLarge(xd)
        // Below 0 the terms reach e^|x| while the value is near e^−|x|/|x|.
        val w = prec + ((if (x.isNegative) 2 else 1) * xd * LOG2E).toInt() + 32
        var p = Ball.ONE
        var sum = eulerGamma(w).add(BallMath.ln(if (x.isNegative) -x else x, w), w)
        var k = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            p = p.mul(x, w).div(Ball.of(k), w)
            val t = p.div(Ball.of(k), w)
            sum = sum.add(t, w)
            if (k + 1.0 > 2 * xd + 1 && t.magUp() < limit) return sum.widen(t.magUp()).rounded(prec)
            k++
            Budget.check()
        }
    }

    /**
     * Yₙ(x) for a whole n ≥ 0 and x > 0 (Abramowitz and Stegun 9.1.11): (2/π)(ln(x/2) + γ)Jₙ(x) − (1/π) Σ_(k<n) (n−k−1)!/k! (x/2)^(2k−n)
     * − (1/π) Σ_(k≥0) (H_k + H_(n+k)) (−1)^k (x/2)^(2k+n) / (k!(n+k)!), H the harmonic numbers; Y₋ₙ = (−1)ⁿ Yₙ.
     */
    fun besselY(order: Long, x: Ball, prec: Int): Ball {
        if (order < 0) return besselY(-order, x, prec).let { if (order % 2 != 0L) -it else it }
        if (order > 1000) throw CalcException(Failure.TOO_LARGE)
        if (!x.isPositive) {
            if (x.upper.signum <= 0) undefined()
            throw Imprecise()
        }
        val n = order
        val xd = size(x)
        tooLarge(xd)
        val w = prec + (xd * LOG2E).toInt() + 32 + BallMath.bitLength(n) * 2
        val h = x.shl(-1)
        val h2 = h.sqr(w)
        // The series of Jₙ and its weighted twin, term by term: a_k = (−1)^k (x/2)^(2k+n) / (k!(n+k)!).
        var a = h.pow(BigInteger.valueOf(n), w).div(Ball.of(Special.factorial(n)), w)
        var hk = Ball.ZERO
        var hnk = harmonic(n, w)
        var j = a
        var weighted = a.mul(hk.add(hnk, w), w)
        var k = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            a = -a.mul(h2, w).div(Ball.of(k * (k + n)), w)
            hk = hk.add(Ball.ONE.div(Ball.of(k), w), w)
            hnk = hnk.add(Ball.ONE.div(Ball.of(k + n), w), w)
            j = j.add(a, w)
            weighted = weighted.add(a.mul(hk.add(hnk, w), w), w)
            // From here the terms fall by a quarter or more and the weights grow by less than 2: the tails are below
            // |a_k|·(3 + 2 ln(n + k + 2)).
            if ((k + 1.0) * (k + n + 1) > xd * xd + 1) {
                val factor = kotlin.math.ceil(3 + 2 * kotlin.math.ln(n + k + 2.0)).toLong()
                val tail = mulUp(a.magUp(), BigFloat.of(factor))
                if (tail < limit) {
                    j = j.widen(tail)
                    weighted = weighted.widen(tail)
                    break
                }
            }
            k++
            Budget.check()
        }
        val pi = BallMath.pi(w)
        var finite = Ball.ZERO
        for (i in 0 until n) {
            val c = Ball.of(Rational.of(Special.factorial(n - i - 1), Special.factorial(i)), w)
            finite = finite.add(c.mul(h.pow(BigInteger.valueOf(2 * i - n), w), w), w)
            Budget.check()
        }
        val logPart = BallMath.ln(h, w).add(eulerGamma(w), w).mul(j, w).shl(1)
        return logPart.sub(finite, w).sub(weighted, w).div(pi, prec)
    }

    private fun harmonic(n: Long, w: Int): Ball {
        var s = Ball.ZERO
        for (i in 1..n) s = s.add(Ball.ONE.div(Ball.of(i), w), w)
        return s
    }

    /**
     * K(k) = π/(2·M(1, √(1 − k²))) and E(k) = K(k)·(1 − Σ 2^(n−1) cₙ²) with c₀ = k, by the arithmetic–geometric mean: aₙ₊₁ =
     * (aₙ + bₙ)/2, bₙ₊₁ = √(aₙbₙ), cₙ₊₁ = (aₙ − bₙ)/2. The mean M lies between bₙ and aₙ; once cₙ ≤ M each term of the sum is
     * below an eighth of the one before, so the tail is below the last term taken.
     */
    private fun elliptic(k: Ball, prec: Int, second: Boolean): Ball {
        val w = prec + 32
        if (k.isExact && k.mid.abs() == BigFloat.ONE) return if (second) Ball.ONE else undefined()
        val k2 = k.sqr(w)
        val rest = Ball.ONE.sub(k2, w)
        if (!rest.isPositive) {
            if (rest.upper.signum < 0) undefined()
            throw Imprecise()
        }
        var a = Ball.ONE
        var b = BallMath.sqrt(rest, w)
        var sum = k2.shl(-1)
        var n = 1L
        val close = BigFloat.twoTo(-(prec + 8).toLong())
        while (true) {
            val c = a.sub(b, w).shl(-1)
            val next = a.add(b, w).shl(-1)
            b = BallMath.sqrt(a.mul(b, w), w)
            a = next
            val term = c.sqr(w).shl(n - 1)
            sum = sum.add(term, w)
            // Converged: aₙ − bₙ below the precision or within what the radii (k's own uncertainty) allow; the enclosure
            // [bₙ, aₙ] below holds either way.
            val gap = a.sub(b, w).magUp()
            val converged = gap < close || gap <= mulUp(addUp(a.rad, b.rad), BigFloat.of(4))
            if (converged && term.magUp() < close && c.magUp() <= b.lower) {
                sum = sum.widen(term.magUp())
                break
            }
            n++
            if (n > 10_000) throw CalcException(Failure.TOO_LONG)
            Budget.check()
        }
        if (b.lower.signum <= 0) throw Imprecise()
        val mean = Ball.span(b.lower, a.upper)
        val kk = BallMath.pi(w).div(mean.shl(1), w)
        return if (second) kk.mul(Ball.ONE.sub(sum, w), prec) else kk.rounded(prec)
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Derivatives and integrals.

    /** d/du of f(u), the inner derivative left to the chain rule; [x] the letter differentiated in (an order may not hold it). */
    fun derivative(f: Func, args: List<Expr>, x: Expr.Sym): Expr {
        val u = args.last()
        val minusHalf = num(Rational.of(-1) / Rational.of(2))
        return when (f) {
            Func.COTH -> neg(pow(fn(Func.SINH, u), num(-2)))
            Func.SECH -> neg(mul(fn(Func.SINH, u), pow(fn(Func.COSH, u), num(-2))))
            Func.CSCH -> neg(mul(fn(Func.COSH, u), pow(fn(Func.SINH, u), num(-2))))
            Func.ASINH -> pow(u * u + Expr.ONE, minusHalf)
            Func.ACOSH -> pow(u * u - Expr.ONE, minusHalf)
            Func.ATANH, Func.ACOTH -> pow(Expr.ONE - u * u, Expr.MINUS_ONE)
            Func.ASEC -> pow(mul(fn(Func.ABS, u), sqrt(u * u - Expr.ONE)), Expr.MINUS_ONE)
            Func.ACSC -> neg(pow(mul(fn(Func.ABS, u), sqrt(u * u - Expr.ONE)), Expr.MINUS_ONE))
            Func.EI -> mul(exp(u), pow(u, Expr.MINUS_ONE))
            Func.SI -> mul(fn(Func.SIN, u), pow(u, Expr.MINUS_ONE))
            Func.CI -> mul(fn(Func.COS, u), pow(u, Expr.MINUS_ONE))
            // Yₙ′ = (Yₙ₋₁ − Yₙ₊₁)/2, as Jₙ′.
            Func.BESSELY -> {
                val n = args[0]
                if (n.has(x)) undefined()
                mul(Expr.HALF, add(fn(Func.BESSELY, n - Expr.ONE, u), neg(fn(Func.BESSELY, n + Expr.ONE, u))))
            }
            // K′(k) = E/(k(1 − k²)) − K/k, E′(k) = (E − K)/k.
            Func.ELLIPTICK -> add(mul(fn(Func.ELLIPTICE, u), pow(mul(u, Expr.ONE - u * u), Expr.MINUS_ONE)),
                neg(mul(fn(Func.ELLIPTICK, u), pow(u, Expr.MINUS_ONE))))
            Func.ELLIPTICE -> mul(add(fn(Func.ELLIPTICE, u), neg(fn(Func.ELLIPTICK, u))), pow(u, Expr.MINUS_ONE))
            else -> throw IllegalStateException()
        }
    }

    /**
     * The Taylor coefficients c₀ … c_[terms] of [f] at [y0] where its derivatives there are only limits: Si at 0,
     * Σ (−1)^k y^(2k+1) / ((2k + 1)·(2k + 1)!). Null for every other function and point.
     */
    fun seriesAtZero(f: Func, y0: Expr, terms: Int): List<Expr>? {
        if (f != Func.SI || !isZero(y0)) return null
        return (0..terms).map { j ->
            if (j % 2 == 0) Expr.ZERO
            else num(Rational.of(if ((j / 2) % 2 == 0) 1L else -1L) / Rational.of(Special.factorial(j.toLong()) * BigInteger.valueOf(j.toLong())))
        }
    }

    /** ∫ f(u) du of the table (tanh too), or null; the caller divides by u′ for a linear u. Each is differentiated back. */
    fun tableIntegral(f: Func, u: Expr): Expr? = when (f) {
        Func.TANH -> ln(fn(Func.COSH, u))
        Func.COTH -> ln(fn(Func.ABS, fn(Func.SINH, u)))
        // ∫ sech u du = arctan(sinh u), ∫ csch u du = ln|tanh(u/2)|.
        Func.SECH -> fn(Func.ATAN, fn(Func.SINH, u))
        Func.CSCH -> ln(fn(Func.ABS, fn(Func.TANH, mul(Expr.HALF, u))))
        Func.ASINH -> u * fn(Func.ASINH, u) - sqrt(u * u + Expr.ONE)
        Func.ACOSH -> u * fn(Func.ACOSH, u) - sqrt(u * u - Expr.ONE)
        Func.ATANH -> u * fn(Func.ATANH, u) + mul(Expr.HALF, ln(Expr.ONE - u * u))
        Func.ACOTH -> u * fn(Func.ACOTH, u) + mul(Expr.HALF, ln(u * u - Expr.ONE))
        // ∫ arcsec u du = u·arcsec u − arcosh|u|, ∫ arccsc u du = u·arccsc u + arcosh|u|.
        Func.ASEC -> u * fn(Func.ASEC, u) - fn(Func.ACOSH, fn(Func.ABS, u))
        Func.ACSC -> u * fn(Func.ACSC, u) + fn(Func.ACOSH, fn(Func.ABS, u))
        Func.SI -> u * fn(Func.SI, u) + fn(Func.COS, u)
        Func.CI -> u * fn(Func.CI, u) - fn(Func.SIN, u)
        Func.EI -> u * fn(Func.EI, u) - exp(u)
        else -> null
    }

    /** sech² u, csch² u, cosh⁻² u, sinh⁻² u: (∫ du, u), or null. */
    fun squareIntegral(b: Expr, p: Expr): Pair<Expr, Expr>? {
        if (b !is Expr.Fn || b.args.size != 1) return null
        val u = b.args[0]
        return when {
            (b.f == Func.COSH && p == num(-2)) || (b.f == Func.SECH && p == Expr.TWO) -> fn(Func.TANH, u) to u
            (b.f == Func.SINH && p == num(-2)) || (b.f == Func.CSCH && p == Expr.TWO) -> neg(fn(Func.COTH, u)) to u
            else -> null
        }
    }

    /** e^(ax)/x, sin(ax)/x and cos(ax)/x (times what is free of x): c·Ei(ax), c·Si(ax), c·Ci(|a|x); null for anything else. */
    fun overX(e: Expr, x: Expr.Sym): Expr? {
        if (e !is Expr.Mul) return null
        val at = e.factors.indexOfFirst { it is Expr.Pow && it.base == x && it.exp == Expr.MINUS_ONE }
        if (at < 0) return null
        val (with, without) = e.factors.filterIndexed { i, _ -> i != at }.partition { it.has(x) }
        if (with.size != 1) return null
        val g = with[0]
        val (inner, special) = when {
            g is Expr.Pow && g.base == Expr.E -> g.exp to Func.EI
            g is Expr.Fn && g.f == Func.SIN -> g.args[0] to Func.SI
            g is Expr.Fn && g.f == Func.COS -> g.args[0] to Func.CI
            else -> return null
        }
        val c = Solve.coefficientsIn(Algebra.expand(inner), x) ?: return null
        if (c.size != 2 || !isZero(c[0]) || c[1].has(x)) return null
        // cos is even: Ci of |a|x.
        val u = if (special == Func.CI && (Numeric.value(c[1]) ?: return null) < 0) neg(inner) else inner
        return mul(mul(without), fn(special, u))
    }
}
