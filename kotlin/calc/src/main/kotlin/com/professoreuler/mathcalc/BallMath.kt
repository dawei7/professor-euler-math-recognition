package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Constants and elementary functions on [Ball]s, each to a precision with a rigorous bound: the series are summed in fixed
 * point (integers scaled by 2^w) with every truncation counted, their tails bounded, and the error of the argument carried
 * by a bound of the derivative. The tests hold them against reference digits to 1000 places.
 */
internal object BallMath {
    private class Cached(val prec: Int, val ball: Ball)

    @Volatile private var piCache: Cached? = null
    @Volatile private var ln2Cache: Cached? = null

    fun pi(prec: Int): Ball {
        piCache?.let { if (it.prec >= prec) return it.ball.rounded(prec) }
        val p = prec + 32
        val (a, ea) = arctanOfInverse(5, p + 32, alternating = true)
        val (b, eb) = arctanOfInverse(239, p + 32, alternating = true)
        val ball = fixedBall(a.multiply(BigInteger.valueOf(16)) - b.multiply(BigInteger.valueOf(4)), 16 * ea + 4 * eb, p + 32).rounded(p)
        piCache = Cached(p, ball)
        return ball.rounded(prec)
    }

    /** ln 2 = 18 atanh(1/26) − 2 atanh(1/4801) + 8 atanh(1/8749). */
    fun ln2(prec: Int): Ball {
        ln2Cache?.let { if (it.prec >= prec) return it.ball.rounded(prec) }
        val p = prec + 32
        val w = p + 32
        val (a, ea) = arctanOfInverse(26, w, alternating = false)
        val (b, eb) = arctanOfInverse(4801, w, alternating = false)
        val (c, ec) = arctanOfInverse(8749, w, alternating = false)
        val v = a.multiply(BigInteger.valueOf(18)) - b.multiply(BigInteger.valueOf(2)) + c.multiply(BigInteger.valueOf(8))
        val ball = fixedBall(v, 18 * ea + 2 * eb + 8 * ec, w).rounded(p)
        ln2Cache = Cached(p, ball)
        return ball.rounded(prec)
    }

    fun e(prec: Int): Ball = expPoint(BigFloat.ONE, prec)

    // --------------------------------------------------------------------------------------------------------------------
    // Functions of a ball.

    fun exp(x: Ball, prec: Int): Ball {
        val y = expPoint(x.mid, prec + 4)
        if (x.isExact) return y.rounded(prec)
        // |e^a − e^b| ≤ |a − b| · e^upper
        val bound = expPoint(x.upper.round(64, Round.CEILING), 64).magUp()
        return y.widen(mulUp(x.rad, bound)).rounded(prec)
    }

    fun ln(x: Ball, prec: Int): Ball {
        if (!x.isPositive) {
            if (x.upper.signum <= 0) throw CalcException(Failure.UNDEFINED)
            throw Imprecise()
        }
        val y = lnPoint(x.mid, prec + 4)
        if (x.isExact) return y.rounded(prec)
        return y.widen(divUp(x.rad, x.lower.round(RAD_BITS, Round.FLOOR))).rounded(prec)
    }

    fun sqrt(x: Ball, prec: Int): Ball {
        if (x.isExactZero) return Ball.ZERO
        if (x.isNegative) throw CalcException(Failure.UNDEFINED)
        if (!x.isPositive) throw Imprecise()
        val (s, err) = x.mid.sqrt(prec + 2)
        var r = err
        if (!x.rad.isZero) {
            // |√a − √b| ≤ |a − b| / (2·√lower)
            val lowerRoot = x.lower.sqrt(RAD_BITS).first
            if (lowerRoot.isZero) throw Imprecise()
            r = addUp(r, divUp(x.rad, lowerRoot.shl(1)))
        }
        return Ball(s, up(r)).rounded(prec)
    }

    fun sin(x: Ball, prec: Int): Ball = sinCosPoint(x.mid, prec + 4).first.widen(x.rad).rounded(prec)
    fun cos(x: Ball, prec: Int): Ball = sinCosPoint(x.mid, prec + 4).second.widen(x.rad).rounded(prec)

    fun tan(x: Ball, prec: Int): Ball {
        val (s, c) = sinCosPoint(x.mid, prec + 8)
        return s.widen(x.rad).div(c.widen(x.rad), prec)
    }

    fun atan(x: Ball, prec: Int): Ball = atanPoint(x.mid, prec + 4).widen(x.rad).rounded(prec)

    /** arcsin on [−1, 1], as atan(x / √(1 − x²)). */
    fun asin(x: Ball, prec: Int): Ball {
        val w = prec + 16
        if (x.isExact && x.mid.abs() == BigFloat.ONE) return pi(prec).shl(-1).let { if (x.mid.signum < 0) -it else it }
        val oneMinus = Ball.ONE.sub(x.sqr(w), w)
        if (oneMinus.isNegative) throw CalcException(Failure.UNDEFINED)
        return atan(x.div(sqrt(oneMinus, w), w), w).rounded(prec)
    }

    fun acos(x: Ball, prec: Int): Ball = pi(prec + 8).shl(-1).sub(asin(x, prec + 8), prec + 8).rounded(prec)

    fun sinh(x: Ball, prec: Int): Ball {
        val w = prec + 8
        val e = exp(x, w)
        return e.sub(e.inverse(w), w).shl(-1).rounded(prec)
    }

    fun cosh(x: Ball, prec: Int): Ball {
        val w = prec + 8
        val e = exp(x, w)
        return e.add(e.inverse(w), w).shl(-1).rounded(prec)
    }

    fun tanh(x: Ball, prec: Int): Ball {
        val w = prec + 8
        val e2 = exp(x.shl(1), w)
        return e2.sub(Ball.ONE, w).div(e2.add(Ball.ONE, w), prec)
    }

    /** The angle of (x, y) in (−π, π]: [Imprecise] where the ball reaches the cut or the origin. */
    fun atan2(y: Ball, x: Ball, prec: Int): Ball {
        val w = prec + 8
        return when {
            x.isPositive -> atan(y.div(x, w), prec)
            y.isPositive -> pi(w).shl(-1).sub(atan(x.div(y, w), w), w).rounded(prec)
            y.isNegative -> (-pi(w).shl(-1)).sub(atan(x.div(y, w), w), w).rounded(prec)
            x.isNegative && y.isExactZero -> pi(prec)
            x.isNegative -> throw Imprecise()
            x.isExactZero && y.isExactZero -> throw CalcException(Failure.UNDEFINED)
            else -> throw Imprecise()
        }
    }

    /** x^y for x > 0. */
    fun pow(x: Ball, y: Ball, prec: Int): Ball = exp(y.mul(ln(x, prec + 16), prec + 16), prec)

    // --------------------------------------------------------------------------------------------------------------------
    // Functions of a point, with their own error.

    fun expPoint(x: BigFloat, prec: Int): Ball {
        if (x.isZero) return Ball.ONE
        if (x.top > 30) throw CalcException(Failure.TOO_LARGE)
        val s = maxOf(0L, x.top + 9).toInt() // |r| < 2^-8
        val w = prec + 2 * s + 24 + bitLength(prec)
        val r = x.shl(-s.toLong())
        val one = BigInteger.ONE.shiftLeft(w)
        val big = fixed(r, w)
        var sum = one
        var t = one
        var k = 1L
        var terms = 0L
        while (true) {
            t = t.multiply(big).shiftRight(w).divide(BigInteger.valueOf(k))
            if (t.signum() == 0) break
            sum += t
            terms++
            k++
            Budget.check()
        }
        var y = fixedBall(sum, 4 * terms + 9, w)
        repeat(s) {
            y = y.sqr(w)
            Budget.check()
        }
        return y.rounded(prec)
    }

    fun lnPoint(x: BigFloat, prec: Int): Ball {
        if (x.signum <= 0) throw CalcException(Failure.UNDEFINED)
        if (x == BigFloat.ONE) return Ball.ZERO
        var k = x.top + 1 // x / 2^k in [1/2, 1)
        var y = x.shl(-k)
        if (y < SQRT_HALF) {
            y = y.shl(1)
            k -= 1
        }
        val w = prec + bitLength(kotlin.math.abs(k)) + 24 + bitLength(prec)
        val one = BigInteger.ONE.shiftLeft(w)
        val big = fixed(y, w)
        // z = (y − 1)/(y + 1), |z| < 0.18; ln y = 2·atanh z = 2(z + z³/3 + z⁵/5 + …), summed for |z| (atanh is odd), so
        // the truncated powers fall to 0
        val signed = (big - one).shiftLeft(w).divide(big + one)
        val z = signed.abs()
        val z2 = z.multiply(z).shiftRight(w)
        var p = z
        var sum = z
        var n = 1L
        var terms = 1L
        while (true) {
            p = p.multiply(z2).shiftRight(w)
            if (p.signum() == 0) break
            sum += p.divide(BigInteger.valueOf(2 * n + 1))
            n++
            terms++
            Budget.check()
        }
        val lny = fixedBall(sum.shiftLeft(1).let { if (signed.signum() < 0) it.negate() else it }, 2 * (3 * terms + 8), w)
        if (k == 0L) return lny.rounded(prec)
        return lny.add(ln2(w).mul(Ball.of(k), w), w).rounded(prec)
    }

    /** (sin x, cos x). */
    fun sinCosPoint(x: BigFloat, prec: Int): Pair<Ball, Ball> {
        if (x.isZero) return Ball.ZERO to Ball.ONE
        if (x.top > 1 shl 16) throw CalcException(Failure.TOO_LARGE)
        val extra = maxOf(0L, x.top + 2).toInt()
        val w = prec + extra + 24 + bitLength(prec)
        var r = Ball(x)
        var quadrant = 0
        if (x.top >= 0) {
            // x = n·π/2 + r, |r| ≤ π/4
            val halfPi = pi(w + extra).shl(-1)
            val n = nearestInteger(Ball(x).div(halfPi, w + 16).mid)
            r = Ball(x).sub(halfPi.mul(Ball.of(n), w + extra), w)
            quadrant = n.mod(BigInteger.valueOf(4)).toInt()
        }
        val one = BigInteger.ONE.shiftLeft(w)
        val big = fixed(r.mid, w)
        var t = one
        var sin = BigInteger.ZERO
        var cos = one
        var k = 1L
        var terms = 0L
        while (true) {
            t = t.multiply(big).shiftRight(w).divide(BigInteger.valueOf(k))
            if (t.signum() == 0) break
            when ((k % 4).toInt()) {
                1 -> sin += t
                2 -> cos -= t
                3 -> sin -= t
                else -> cos += t
            }
            k++
            terms++
            Budget.check()
        }
        val err = 4 * terms + 9
        val s = fixedBall(sin, err, w).widen(r.rad)
        val c = fixedBall(cos, err, w).widen(r.rad)
        val (a, b) = when (quadrant) {
            0 -> s to c
            1 -> c to -s
            2 -> -s to -c
            else -> -c to s
        }
        return a.rounded(prec) to b.rounded(prec)
    }

    fun atanPoint(x: BigFloat, prec: Int): Ball {
        if (x.isZero) return Ball.ZERO
        if (x.signum < 0) return -atanPoint(-x, prec)
        if (x == BigFloat.ONE) return pi(prec).shl(-2)
        val w = prec + 40 + bitLength(prec)
        if (x > BigFloat.ONE) {
            // π/2 − atan(1/x)
            val inv = Ball.ONE.div(Ball(x), w)
            return pi(w).shl(-1).sub(atanPoint(inv.mid, w).widen(inv.rad), w).rounded(prec)
        }
        // atan y = 2·atan(y / (1 + √(1 + y²))), until y < 2^-10
        var y = Ball(x)
        var doublings = 0
        while (y.mid.top > -10) {
            val root = sqrt(Ball.ONE.add(y.sqr(w), w), w)
            y = y.div(Ball.ONE.add(root, w), w)
            doublings++
        }
        val big = fixed(y.mid, w)
        val y2 = big.multiply(big).shiftRight(w)
        var p = big
        var sum = big
        var n = 1L
        var terms = 1L
        while (true) {
            p = p.multiply(y2).shiftRight(w)
            if (p.signum() == 0) break
            val t = p.divide(BigInteger.valueOf(2 * n + 1))
            sum = if (n % 2 == 1L) sum - t else sum + t
            n++
            terms++
            Budget.check()
        }
        return fixedBall(sum, 3 * terms + 6, w).widen(y.rad).shl(doublings.toLong()).rounded(prec)
    }

    // --------------------------------------------------------------------------------------------------------------------

    private val SQRT_HALF = BigFloat.of(0.7071067811865476)

    /**
     * Σ (±1)ⁿ / ((2n + 1)·k^(2n+1)) in fixed point with [w] bits (atan or atanh of 1/k), and a bound on its error in units
     * of 2^-w: each division truncates by less than 1, and the tail is below 3 once the powers reach 0.
     */
    private fun arctanOfInverse(k: Long, w: Int, alternating: Boolean): Pair<BigInteger, Long> {
        val kk = BigInteger.valueOf(k).pow(2)
        var p = BigInteger.ONE.shiftLeft(w).divide(BigInteger.valueOf(k))
        var sum = p
        var n = 1L
        var terms = 1L
        while (p.signum() != 0) {
            p = p.divide(kk)
            val t = p.divide(BigInteger.valueOf(2 * n + 1))
            sum = if (alternating && n % 2 == 1L) sum - t else sum + t
            n++
            terms++
            Budget.check()
        }
        return sum to 3 * terms + 3
    }

    /** x·2^w, rounded down. */
    fun fixed(x: BigFloat, w: Int): BigInteger {
        val sh = x.exp + w
        return when {
            sh >= 0 -> x.man.shiftLeft(sh.toInt())
            -sh > x.bits + 2 -> if (x.signum < 0) BigInteger.ONE.negate() else BigInteger.ZERO
            else -> x.man.shiftRight((-sh).toInt())
        }
    }

    /** v·2^-w with an error of [errUnits]·2^-w. */
    fun fixedBall(v: BigInteger, errUnits: Long, w: Int): Ball =
        Ball(BigFloat.of(v, -w.toLong()), up(BigFloat.of(BigInteger.valueOf(errUnits), -w.toLong())))

    fun nearestInteger(x: BigFloat): BigInteger = (x + BigFloat.HALF).floor()

    fun bitLength(n: Int): Int = 32 - Integer.numberOfLeadingZeros(maxOf(n, 1))
    fun bitLength(n: Long): Int = 64 - java.lang.Long.numberOfLeadingZeros(maxOf(n, 1L))
}
