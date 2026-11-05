package com.professoreuler.mathcalc

import java.math.BigInteger

/** How [BigFloat.round] cuts a number to a precision: down, up, or to the nearer (ties to even). */
enum class Round { FLOOR, CEILING, NEAREST }

/**
 * A binary floating-point number of any size and precision: [man] · 2^[exp], exactly. [man] is odd, or zero with [exp] 0, so
 * equal numbers are equal objects. Sums, differences and products are exact; [round] cuts a number to a count of bits, and
 * [divide] and [sqrt] give a result of a precision together with a bound on its error. The certified digits of [Ball] rest
 * on it. Pure Kotlin on [BigInteger], with nothing Android 10 lacks.
 */
class BigFloat private constructor(val man: BigInteger, val exp: Long) : Comparable<BigFloat> {
    val signum: Int get() = man.signum()
    val isZero: Boolean get() = man.signum() == 0

    /** The number of significant bits. */
    val bits: Int = man.abs().bitLength()

    /** The exponent of the leading bit, 2^top ≤ |x| < 2^(top+1); [Long.MIN_VALUE] for zero. */
    val top: Long get() = if (isZero) Long.MIN_VALUE else exp + bits - 1

    val isInteger: Boolean get() = isZero || exp >= 0

    operator fun unaryMinus(): BigFloat = if (isZero) this else BigFloat(man.negate(), exp)
    fun abs(): BigFloat = if (signum < 0) -this else this

    /** The exact sum. Two numbers more than [MAX_SHIFT] bits apart are [Failure.TOO_LARGE]; [Ball] never asks for that. */
    operator fun plus(o: BigFloat): BigFloat {
        if (isZero) return o
        if (o.isZero) return this
        val e = minOf(exp, o.exp)
        val sa = exp - e
        val sb = o.exp - e
        if (sa > MAX_SHIFT || sb > MAX_SHIFT) throw CalcException(Failure.TOO_LARGE)
        return of(man.shiftLeft(sa.toInt()) + o.man.shiftLeft(sb.toInt()), e)
    }

    operator fun minus(o: BigFloat): BigFloat = this + (-o)
    operator fun times(o: BigFloat): BigFloat = if (isZero || o.isZero) ZERO else BigFloat(man * o.man, exp + o.exp)
    operator fun times(n: Long): BigFloat = this * of(n)

    /** This times 2^[n], exactly. */
    fun shl(n: Long): BigFloat = if (isZero) this else BigFloat(man, exp + n)

    /** Cut to [prec] bits in the direction of [mode]. A number of [prec] bits or fewer is returned as it is. */
    fun round(prec: Int, mode: Round): BigFloat {
        require(prec >= 2)
        if (bits <= prec) return this
        val drop = bits - prec
        var q = man.shiftRight(drop) // the floor, for negatives too
        val rem = man - q.shiftLeft(drop) // 0 ≤ rem < 2^drop
        if (rem.signum() != 0) when (mode) {
            Round.FLOOR -> {}
            Round.CEILING -> q += BigInteger.ONE
            Round.NEAREST -> {
                val c = rem.compareTo(BigInteger.ONE.shiftLeft(drop - 1))
                if (c > 0 || (c == 0 && q.testBit(0))) q += BigInteger.ONE
            }
        }
        return of(q, exp + drop)
    }

    /** A bound on how far [round] to [prec] bits moves this number: one unit in the last of those bits. */
    fun ulp(prec: Int): BigFloat = if (isZero) ZERO else BigFloat(BigInteger.ONE, top - prec + 1)

    /**
     * This divided by [o] to at least [prec] bits, truncated, and a bound on the error (zero where the quotient is exact).
     * Division by zero is [Failure.DIVISION_BY_ZERO].
     */
    fun divide(o: BigFloat, prec: Int): Pair<BigFloat, BigFloat> {
        if (o.isZero) throw CalcException(Failure.DIVISION_BY_ZERO)
        if (isZero) return ZERO to ZERO
        val shift = maxOf(0, prec + o.bits - bits + 2)
        val qr = man.shiftLeft(shift).divideAndRemainder(o.man)
        val e = exp - o.exp - shift
        val err = if (qr[1].signum() == 0) ZERO else BigFloat(BigInteger.ONE, e)
        return of(qr[0], e) to err
    }

    /** The square root of this (not negative) to at least [prec] bits, truncated, and a bound on its error. */
    fun sqrt(prec: Int): Pair<BigFloat, BigFloat> {
        if (signum < 0) throw CalcException(Failure.UNDEFINED)
        if (isZero) return ZERO to ZERO
        var shift = maxOf(0L, 2L * prec + 2 - bits)
        if ((exp - shift) % 2 != 0L) shift += 1
        val m = man.shiftLeft(shift.toInt())
        val r = isqrt(m)
        val e = (exp - shift) / 2
        val err = if (r * r == m) ZERO else BigFloat(BigInteger.ONE, e)
        return of(r, e) to err
    }

    /** The integer part, rounded toward minus infinity. */
    fun floor(): BigInteger = when {
        isZero -> BigInteger.ZERO
        exp >= 0 -> man.shiftLeft(checkedShift(exp))
        -exp > bits + 1 -> if (signum < 0) BigInteger.ONE.negate() else BigInteger.ZERO
        else -> man.shiftRight((-exp).toInt())
    }

    override fun compareTo(other: BigFloat): Int {
        if (signum != other.signum) return signum.compareTo(other.signum)
        if (isZero) return 0
        if (top != other.top) return if (signum > 0) top.compareTo(other.top) else other.top.compareTo(top)
        return (this - other).signum
    }

    override fun equals(other: Any?) = other is BigFloat && man == other.man && exp == other.exp
    override fun hashCode() = man.hashCode() * 31 + exp.hashCode()

    /** The nearest double, or near enough for graphs and checks; ±∞ and 0 beyond a double's range. */
    fun toDouble(): Double {
        if (isZero) return 0.0
        val b = bits
        val (m, e) = if (b <= 62) man.toLong() to exp else man.shiftRight(b - 62).toLong() to exp + b - 62
        return when {
            e > 2000 -> if (m > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
            e < -2200 -> 0.0
            else -> Math.scalb(m.toDouble(), e.toInt())
        }
    }

    /** The exact fraction. */
    fun toRational(): Rational = when {
        isZero -> Rational.ZERO
        exp >= 0 -> Rational.of(man.shiftLeft(checkedShift(exp)))
        else -> Rational.of(man, BigInteger.ONE.shiftLeft(checkedShift(-exp)))
    }

    override fun toString(): String = "${man}p$exp"

    companion object {
        /** Beyond this many bits apart, two numbers are not added exactly. */
        const val MAX_SHIFT = 1L shl 24

        val ZERO = BigFloat(BigInteger.ZERO, 0)
        val ONE = BigFloat(BigInteger.ONE, 0)
        val HALF = BigFloat(BigInteger.ONE, -1)

        fun of(man: BigInteger, exp: Long = 0): BigFloat {
            if (man.signum() == 0) return ZERO
            val tz = man.lowestSetBit
            return BigFloat(if (tz == 0) man else man.shiftRight(tz), exp + tz)
        }

        fun of(n: Long): BigFloat = of(BigInteger.valueOf(n))

        fun twoTo(n: Long): BigFloat = BigFloat(BigInteger.ONE, n)

        /** A double's exact value. */
        fun of(d: Double): BigFloat {
            require(d.isFinite())
            if (d == 0.0) return ZERO
            val raw = java.lang.Double.doubleToRawLongBits(d)
            val sign = if (raw < 0) -1L else 1L
            val biased = ((raw ushr 52) and 0x7FF).toInt()
            val fraction = raw and 0xFFFFFFFFFFFFFL
            val (m, e) = if (biased == 0) fraction to -1074L else (fraction or (1L shl 52)) to (biased - 1075).toLong()
            return of(BigInteger.valueOf(sign * m), e)
        }

        /** A fraction to at least [prec] bits, with a bound on the error. */
        fun of(r: Rational, prec: Int): Pair<BigFloat, BigFloat> = of(r.num).divide(of(r.den), prec)

        private fun checkedShift(n: Long): Int {
            if (n > MAX_SHIFT) throw CalcException(Failure.TOO_LARGE)
            return n.toInt()
        }
    }
}

/** The whole part of √[x] for x ≥ 0, by Newton's method: Android has `BigInteger.sqrt` only from version 13. */
internal fun isqrt(x: BigInteger): BigInteger {
    if (x.signum() <= 0) return BigInteger.ZERO
    var r = BigInteger.ONE.shiftLeft((x.bitLength() + 1) / 2)
    while (true) {
        val next = (r + x / r).shiftRight(1)
        if (next >= r) return r
        r = next
    }
}

// ------------------------------------------------------------------------------------------------------------------------
// Radii: bounds that are never negative, kept to a few bits and always rounded up, so they stay cheap and safe.

internal const val RAD_BITS = 30

internal fun up(x: BigFloat): BigFloat = x.round(RAD_BITS, Round.CEILING)

/** An upper bound of a + b, for a, b ≥ 0. */
internal fun addUp(a: BigFloat, b: BigFloat): BigFloat {
    if (a.isZero) return up(b)
    if (b.isZero) return up(a)
    val gap = a.top - b.top
    if (gap > RAD_BITS + 8) return up(a) + a.ulp(RAD_BITS)
    if (-gap > RAD_BITS + 8) return up(b) + b.ulp(RAD_BITS)
    return up(a + b)
}

internal fun mulUp(a: BigFloat, b: BigFloat): BigFloat = up(a * b)

/** An upper bound of a / b, for a ≥ 0, b > 0. */
internal fun divUp(a: BigFloat, b: BigFloat): BigFloat {
    if (a.isZero) return BigFloat.ZERO
    val (q, err) = a.divide(b, RAD_BITS + 2)
    return up(q + err)
}

/** A lower bound of a − b, never below 0, for a, b ≥ 0. */
internal fun subDown(a: BigFloat, b: BigFloat): BigFloat {
    if (b.isZero) return a.round(RAD_BITS, Round.FLOOR)
    if (b >= a) return BigFloat.ZERO
    val gap = a.top - b.top
    val d = if (gap > RAD_BITS + 8) a.round(RAD_BITS, Round.FLOOR) - a.ulp(RAD_BITS) else (a - b).round(RAD_BITS, Round.FLOOR)
    return if (d.signum < 0) BigFloat.ZERO else d
}
