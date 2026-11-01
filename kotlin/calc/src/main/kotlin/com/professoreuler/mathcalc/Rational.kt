package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** A fraction in lowest terms, with a positive denominator. */
class Rational private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rational> {

    operator fun plus(o: Rational) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Rational) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Rational) = of(num * o.num, den * o.den)
    operator fun div(o: Rational): Rational {
        if (o.num.signum() == 0) throw CalcException(Failure.DIVISION_BY_ZERO)
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = Rational(num.negate(), den)

    fun abs() = if (num.signum() < 0) -this else this

    val isInteger get() = den == BigInteger.ONE
    val signum get() = num.signum()

    /** This to an integer power, or null where the result would be too large to keep exactly. */
    fun pow(n: Int): Rational? {
        if (n < 0) return if (num.signum() == 0) throw CalcException(Failure.DIVISION_BY_ZERO) else of(den, num).pow(-n)
        if (n.toLong() * maxOf(num.bitLength(), den.bitLength()) > MAX_BITS) return null
        return of(num.pow(n), den.pow(n))
    }

    /** The exact [n]th root, if there is a rational one: odd roots of negatives are negative, even ones have none. */
    fun root(n: Int): Rational? {
        if (n < 1) return null
        if (num.signum() < 0) return if (n % 2 == 1) (-this).root(n)?.let { -it } else null
        val a = integerRoot(num, n) ?: return null
        val b = integerRoot(den, n) ?: return null
        return of(a, b)
    }

    fun toDouble(): Double = BigDecimal(num).divide(BigDecimal(den), MathContext.DECIMAL64).toDouble()

    /** Whether it is a decimal that ends: its denominator has no prime factors but 2 and 5. */
    val terminates: Boolean
        get() {
            var d = den
            for (p in listOf(2L, 5L).map(BigInteger::valueOf)) while (d.mod(p).signum() == 0) d /= p
            return d == BigInteger.ONE
        }

    override fun compareTo(other: Rational) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is Rational && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) "$num" else "$num/$den"

    companion object {
        /** Beyond this many bits a power is no longer kept exactly. */
        const val MAX_BITS = 20_000

        fun of(num: BigInteger, den: BigInteger = BigInteger.ONE): Rational {
            if (den.signum() == 0) throw CalcException(Failure.DIVISION_BY_ZERO)
            val g = num.gcd(den).let { if (it.signum() == 0) BigInteger.ONE else it }
            val sign = den.signum()
            return Rational(num / g * BigInteger.valueOf(sign.toLong()), den / g * BigInteger.valueOf(sign.toLong()))
        }

        fun of(n: Long) = of(BigInteger.valueOf(n))

        val ZERO = of(0)
        val ONE = of(1)

        /** A decimal as written: [whole] digits, then [fraction] digits after the separator. */
        fun decimal(whole: String, fraction: String) = of(BigInteger(whole + fraction), BigInteger.TEN.pow(fraction.length))

        /** The nearest fraction with a denominator up to [maxDen] (continued fractions), if [x] lies within [tolerance] of it. */
        fun near(x: Double, maxDen: Long = 10_000, tolerance: Double = 1e-12 * maxOf(1.0, kotlin.math.abs(x))): Rational? {
            if (!x.isFinite() || kotlin.math.abs(x) > 1e15) return null
            var h0 = 0L; var h1 = 1L; var k0 = 1L; var k1 = 0L
            var y = x
            repeat(40) {
                val a = kotlin.math.floor(y)
                if (kotlin.math.abs(a) > 1e15) return null
                val h2 = a.toLong() * h1 + h0
                val k2 = a.toLong() * k1 + k0
                if (k2 > maxDen) return null
                if (kotlin.math.abs(x - h2.toDouble() / k2) <= tolerance) return of(BigInteger.valueOf(h2), BigInteger.valueOf(k2))
                h0 = h1; h1 = h2; k0 = k1; k1 = k2
                val rest = y - a
                if (rest == 0.0) return null
                y = 1 / rest
            }
            return null
        }

        private fun integerRoot(x: BigInteger, n: Int): BigInteger? {
            if (x.signum() == 0 || x == BigInteger.ONE || n == 1) return x
            if (n == 2) return squareRoot(x).takeIf { it * it == x }
            val guess = Math.round(Math.pow(x.toDouble(), 1.0 / n))
            if (guess <= 0 || guess == Long.MAX_VALUE) return null
            for (r in (guess - 1)..(guess + 1)) {
                val b = BigInteger.valueOf(r)
                if (r > 0 && b.pow(n) == x) return b
            }
            return null
        }

        /** The whole part of √[x], by Newton's method: Android has `BigInteger.sqrt` only from version 13. */
        private fun squareRoot(x: BigInteger): BigInteger {
            var r = BigInteger.ONE.shiftLeft((x.bitLength() + 1) / 2)
            while (true) {
                val next = (r + x / r).shiftRight(1)
                if (next >= r) return r
                r = next
            }
        }
    }
}
