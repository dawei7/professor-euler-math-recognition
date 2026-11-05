package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Thrown where a calculation at the present precision cannot decide (a divisor that might be zero, a ball on both sides of
 * a branch cut): the caller works it out again with more bits. Never shown to the learner.
 */
internal class Imprecise : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * A real number known to lie within [rad] of [mid] (ball arithmetic, after Arb): every operation rounds its midpoint to a
 * precision and adds what the rounding and the operands' radii can change to the radius, so the true value is always
 * inside. A ball with radius 0 is exact. Digits are shown only where the whole ball agrees on them ([Precise]).
 */
class Ball(val mid: BigFloat, val rad: BigFloat = BigFloat.ZERO) {
    init {
        require(rad.signum >= 0)
    }

    val isExact: Boolean get() = rad.isZero
    val lower: BigFloat get() = mid - rad
    val upper: BigFloat get() = mid + rad

    fun containsZero(): Boolean = mid.abs() <= rad
    val isPositive: Boolean get() = mid > rad
    val isNegative: Boolean get() = -mid > rad
    val isExactZero: Boolean get() = isExact && mid.isZero

    /** An upper bound of |x| over the ball. */
    fun magUp(): BigFloat = addUp(mid.abs(), rad)

    /** A lower bound of |x| over the ball (0 where it holds zero). */
    fun magDown(): BigFloat = subDown(mid.abs(), rad)

    operator fun unaryMinus(): Ball = Ball(-mid, rad)

    fun add(o: Ball, prec: Int): Ball {
        val (s, err) = sumRounded(mid, o.mid, prec)
        return Ball(s, addUp(addUp(rad, o.rad), err))
    }

    fun sub(o: Ball, prec: Int): Ball = add(-o, prec)

    fun mul(o: Ball, prec: Int): Ball {
        val exact = mid * o.mid
        val m = exact.round(prec, Round.NEAREST)
        var r = if (m == exact) BigFloat.ZERO else exact.ulp(prec)
        if (!o.rad.isZero) r = addUp(r, mulUp(mid.abs(), o.rad))
        if (!rad.isZero) r = addUp(r, mulUp(o.mid.abs(), rad))
        if (!rad.isZero && !o.rad.isZero) r = addUp(r, mulUp(rad, o.rad))
        return Ball(m, r)
    }

    fun mul(n: Long, prec: Int): Ball = mul(of(n), prec)

    /** This times 2^[n], exactly. */
    fun shl(n: Long): Ball = Ball(mid.shl(n), rad.shl(n))

    fun sqr(prec: Int): Ball = mul(this, prec)

    /**
     * This divided by [o]. A divisor that is exactly zero is [Failure.DIVISION_BY_ZERO]; one whose ball holds zero is
     * [Imprecise].
     */
    fun div(o: Ball, prec: Int): Ball {
        if (o.isExactZero) throw CalcException(Failure.DIVISION_BY_ZERO)
        if (o.containsZero()) throw Imprecise()
        val (q, qErr) = mid.divide(o.mid, prec + 2)
        val m = q.round(prec, Round.NEAREST)
        var r = addUp(qErr, if (m == q) BigFloat.ZERO else q.ulp(prec))
        if (!rad.isZero || !o.rad.isZero) {
            // |a/b − ã/b̃| ≤ (|ã|·r_b + |b̃|·r_a) / (|b̃|·(|b̃| − r_b))
            val bAbs = o.mid.abs()
            val num = addUp(mulUp(mid.abs(), o.rad), mulUp(bAbs, rad))
            val den = (bAbs.round(RAD_BITS, Round.FLOOR) * subDown(bAbs, o.rad)).round(RAD_BITS, Round.FLOOR)
            if (den.isZero) throw Imprecise()
            r = addUp(r, divUp(num, den))
        }
        return Ball(m, r)
    }

    fun div(n: Long, prec: Int): Ball = div(of(n), prec)

    fun inverse(prec: Int): Ball = ONE.div(this, prec)

    /** This to a whole power, by repeated squaring; a negative power is 1 over the positive one. */
    fun pow(n: BigInteger, prec: Int): Ball {
        if (n.signum() == 0) return ONE
        if (n.signum() < 0) return pow(n.negate(), prec + 4).inverse(prec)
        val work = prec + n.bitLength() + 4
        var result = ONE
        var base = this
        for (i in n.bitLength() - 1 downTo 0) {
            result = result.sqr(work)
            if (n.testBit(i)) result = result.mul(base, work)
            Budget.check()
        }
        return result.rounded(prec)
    }

    /** The midpoint cut to [prec] bits, the cut added to the radius. */
    fun rounded(prec: Int): Ball {
        val m = mid.round(prec, Round.NEAREST)
        return if (m == mid) this else Ball(m, addUp(rad, mid.ulp(prec)))
    }

    /** This ball, widened by [r]. */
    fun widen(r: BigFloat): Ball = if (r.isZero) this else Ball(mid, addUp(rad, r))

    /** Whether every number in this ball is below every number in [o]. */
    fun below(o: Ball): Boolean = upper < o.lower

    fun toDouble(): Double = mid.toDouble()

    override fun toString(): String = "[${mid.toDouble()} ± ${rad.toDouble()}]"

    companion object {
        val ZERO = Ball(BigFloat.ZERO)
        val ONE = Ball(BigFloat.ONE)

        fun of(n: Long): Ball = Ball(BigFloat.of(n))
        fun of(n: BigInteger): Ball = Ball(BigFloat.of(n))

        fun of(r: Rational, prec: Int): Ball {
            val (m, err) = BigFloat.of(r, prec + 2)
            val rounded = m.round(prec, Round.NEAREST)
            return Ball(rounded, addUp(err, if (rounded == m) BigFloat.ZERO else m.ulp(prec)))
        }

        /** The ball from [lo] to [hi]. */
        fun span(lo: BigFloat, hi: BigFloat): Ball {
            val m = (lo + hi).shl(-1)
            return Ball(m, up(hi - m))
        }

        /** a + b cut to [prec] bits, and the error; numbers too far apart are not added exactly. */
        private fun sumRounded(a: BigFloat, b: BigFloat, prec: Int): Pair<BigFloat, BigFloat> {
            if (a.isZero) return b.round(prec, Round.NEAREST).let { it to if (it == b) BigFloat.ZERO else b.ulp(prec) }
            if (b.isZero) return a.round(prec, Round.NEAREST).let { it to if (it == a) BigFloat.ZERO else a.ulp(prec) }
            val gap = a.top - b.top
            if (gap > prec + 40 || -gap > prec + 40) {
                val (big, small) = if (gap > 0) a to b else b to a
                val m = big.round(prec, Round.NEAREST)
                return m to addUp(small.abs(), if (m == big) BigFloat.ZERO else big.ulp(prec))
            }
            val exact = a + b
            val m = exact.round(prec, Round.NEAREST)
            return m to if (m == exact) BigFloat.ZERO else exact.ulp(prec)
        }
    }
}

/** A complex number as a pair of balls: the real part and the imaginary part. */
class CBall(val re: Ball, val im: Ball = Ball.ZERO) {
    /** Exactly real: the imaginary part is exactly 0. */
    val isReal: Boolean get() = im.isExactZero

    operator fun unaryMinus() = CBall(-re, -im)

    fun add(o: CBall, prec: Int) = CBall(re.add(o.re, prec), if (isReal && o.isReal) Ball.ZERO else im.add(o.im, prec))
    fun sub(o: CBall, prec: Int) = add(-o, prec)

    fun mul(o: CBall, prec: Int): CBall {
        if (isReal && o.isReal) return CBall(re.mul(o.re, prec))
        if (o.isReal) return CBall(re.mul(o.re, prec), im.mul(o.re, prec))
        if (isReal) return CBall(re.mul(o.re, prec), re.mul(o.im, prec))
        return CBall(re.mul(o.re, prec).sub(im.mul(o.im, prec), prec), re.mul(o.im, prec).add(im.mul(o.re, prec), prec))
    }

    fun div(o: CBall, prec: Int): CBall {
        if (o.isReal) return CBall(re.div(o.re, prec), if (isReal) Ball.ZERO else im.div(o.re, prec))
        val w = prec + 8
        val den = o.re.sqr(w).add(o.im.sqr(w), w)
        val n = mul(CBall(o.re, -o.im), w)
        return CBall(n.re.div(den, prec), n.im.div(den, prec))
    }

    /** |z|² */
    fun norm(prec: Int): Ball = if (isReal) re.sqr(prec) else re.sqr(prec).add(im.sqr(prec), prec)

    override fun toString() = if (isReal) re.toString() else "$re + ${im}i"

    companion object {
        val ZERO = CBall(Ball.ZERO)
        val ONE = CBall(Ball.ONE)
        val I = CBall(Ball.ZERO, Ball.ONE)
    }
}
