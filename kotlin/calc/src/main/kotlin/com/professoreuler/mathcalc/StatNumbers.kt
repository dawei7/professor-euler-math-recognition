package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.PriorityQueue
import kotlin.math.abs

/**
 * The numbers of the statistics functions ([Distributions], [Inference], [Fits]): values in ball arithmetic made into decimals
 * whose every digit is certain, exact signs and comparisons, and the checks' own tools in doubles: an adaptive Gauss–Kronrod
 * quadrature and a Lanczos ln Γ, which share nothing with the balls' series, so that a value and its check are two computations.
 */
internal object StatNumbers {
    /** A number (no letters, not ∞) as a ball at [prec] bits. */
    fun ball(e: Expr, prec: Int): Ball {
        if (e is Expr.Num) return Ball.of(e.value, prec)
        if (e is Expr.Real) return Ball(BigFloat.of(e.value))
        val z = Precise.eval(e, prec, complex = false, env = emptyMap())
        if (!z.isReal) throw CalcException(Failure.UNDEFINED)
        return z.re
    }

    fun ball(r: Rational, prec: Int): Ball = Ball.of(r, prec)

    fun ball(d: Double): Ball = Ball(BigFloat.of(d))

    /** 1 for +∞, −1 for −∞, 0 for a number. */
    fun infinity(e: Expr): Int = when (e) {
        Expr.INF -> 1
        neg(Expr.INF) -> -1
        else -> 0
    }

    /** The sign of a number (±∞ too), decided exactly: a rational's own, or a ball's that keeps away from 0. */
    fun signOf(e: Expr): Int {
        infinity(e).let { if (it != 0) return it }
        rational(e)?.let { return it.signum }
        if (e is Expr.Real) return if (e.value > 0) 1 else if (e.value < 0) -1 else 0
        if (e.hasI() || e.symbols().isNotEmpty()) throw CalcException(Failure.UNDEFINED)
        if (LinAlg.isNull(e)) return 0
        var prec = 128
        repeat(5) {
            try {
                val b = ball(e, prec)
                if (b.isExactZero) return 0
                if (b.isPositive) return 1
                if (b.isNegative) return -1
            } catch (x: Imprecise) {
                // more bits
            }
            prec *= 4
            Budget.check()
        }
        throw CalcException(Failure.UNDEFINED)
    }

    /** −1, 0 or 1 as [a] is below, equal to or above [b] (either may be ±∞). */
    fun compare(a: Expr, b: Expr): Int {
        val ia = infinity(a)
        val ib = infinity(b)
        if (ia != 0 || ib != 0) return ia.compareTo(ib).coerceIn(-1, 1)
        val ra = rational(a)
        val rb = rational(b)
        if (ra != null && rb != null) return ra.compareTo(rb).coerceIn(-1, 1)
        return signOf(Calculus.tidy(a - b))
    }

    /** A double of a number, ±∞ for the infinities. */
    fun double(e: Expr): Double = when (infinity(e)) {
        1 -> Double.POSITIVE_INFINITY
        -1 -> Double.NEGATIVE_INFINITY
        else -> Numeric.value(e) ?: throw CalcException(Failure.UNDEFINED)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Certified decimals.

    /**
     * The value [compute] works out in balls, as a decimal of ten certified digits ([Format.DIGITS], as a value is shown): the
     * precision doubles until the whole ball agrees on them, and at its end as many as it agrees on (at least four). A ball that
     * keeps holding 0 is 0 to within [zeroBelow] (relative to [scale]) where that is given: then ≈ 0.
     */
    fun certified(scale: Double? = null, compute: (Int) -> Ball): Expr {
        var prec = 96
        var last: Ball? = null
        while (prec <= 4096) {
            try {
                val b = compute(prec)
                last = b
                if (b.isExactZero) return Expr.ZERO
                Precise.decimal(b, Format.DIGITS)?.let { return real(b, it) }
                if (scale != null && b.containsZero() && b.magUp().toDouble() <= ZERO_BELOW * maxOf(scale, Double.MIN_VALUE)) return Expr.Real(0.0)
            } catch (x: Imprecise) {
                // more bits
            }
            Budget.check()
            prec *= 2
        }
        val b = last ?: throw CalcException(Failure.TOO_LONG)
        for (n in Format.DIGITS - 1 downTo 4) Precise.decimal(b, n)?.let { return real(b, it) }
        throw CalcException(Failure.TOO_LONG)
    }

    /** How small a value that keeps holding 0 must be, against its scale, to be shown as ≈ 0. */
    private const val ZERO_BELOW = 1e-25

    /** A ball's certified decimal as the number it shows: the ball's own double where it rounds to the same digits. */
    private fun real(b: Ball, d: Decimal): Expr {
        val digits = when (d) {
            Decimal.Zero -> return Expr.ZERO
            is Decimal.Small -> return Expr.Real(0.0)
            is Decimal.Digits -> d
        }
        if (abs(digits.exponent) > 300) throw CalcException(Failure.TOO_LARGE)
        var decimal = BigDecimal(BigInteger(digits.digits), digits.digits.length - 1 - digits.exponent)
        if (digits.negative) decimal = decimal.negate()
        val mid = b.mid.toDouble()
        if (digits.exact && mid.isFinite() && BigDecimal(mid).compareTo(decimal) == 0) return Expr.Real(mid)
        if (digits.digits.length == Format.DIGITS && mid.isFinite() && mid != 0.0 &&
            BigDecimal(mid).round(MathContext(Format.DIGITS)).compareTo(decimal) == 0) return Expr.Real(mid)
        return Expr.Real(decimal.toDouble())
    }

    /**
     * The sign of the value [compute] gives against [threshold], decided in balls: −1 below, 1 above; null where the balls keep
     * holding it at every precision tried.
     */
    fun against(threshold: Rational, compute: (Int) -> Ball): Int? {
        var prec = 96
        while (prec <= 2048) {
            try {
                val b = compute(prec)
                val t = Ball.of(threshold, prec)
                if (b.below(t)) return -1
                if (t.below(b)) return 1
            } catch (x: Imprecise) {
                // more bits
            }
            Budget.check()
            prec *= 2
        }
        return null
    }

    /** Whether a value and its check agree to [rel] (relative). */
    fun agree(value: Double, check: Double?, rel: Double = 1e-8): Boolean {
        if (check == null || !check.isFinite() || !value.isFinite()) return false
        return abs(value - check) <= rel * maxOf(abs(value), abs(check)) || (value == 0.0 && check == 0.0)
    }

    /** A value's double: the Real it is, or its rational's. */
    fun valueOf(e: Expr): Double = Numeric.value(e) ?: throw CalcException(Failure.UNDEFINED)

    // ---------------------------------------------------------------------------------------------------------------------
    // The checks' own tools, in doubles.

    /** ln Γ(x) for x > 0 by Lanczos' approximation (g = 7, nine coefficients, as Godfrey published them), about 15 digits. */
    fun lnGammaD(x: Double): Double {
        if (x < 0.5) return kotlin.math.ln(Math.PI / abs(kotlin.math.sin(Math.PI * x))) - lnGammaD(1 - x)
        val z = x - 1
        var a = LANCZOS[0]
        for (i in 1 until LANCZOS.size) a += LANCZOS[i] / (z + i)
        val t = z + 7.5
        return 0.5 * kotlin.math.ln(2 * Math.PI) + (z + 0.5) * kotlin.math.ln(t) - t + kotlin.math.ln(a)
    }

    /** ln B(a, b). */
    fun lnBetaD(a: Double, b: Double): Double = lnGammaD(a) + lnGammaD(b) - lnGammaD(a + b)

    private val LANCZOS = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61502916214059,
        12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
    )

    /** The Kronrod nodes and weights of 15 points and the Gauss weights of 7 (QUADPACK's qk15, public domain). */
    private val XGK = doubleArrayOf(
        0.991455371120812639206854697526329, 0.949107912342758524526189684047851, 0.864864423359769072789712788640926,
        0.741531185599394439863864773280788, 0.586087235467691130294144845693013, 0.405845151377397166906606412076961,
        0.207784955007898467600689403773245, 0.0,
    )
    private val WGK = doubleArrayOf(
        0.022935322010529224963732008058970, 0.063092092629978553290700663189204, 0.104790010322250183839876322541518,
        0.140653259715525918745189590510238, 0.169004726639267902826583426598550, 0.190350578064785409913256402421014,
        0.204432940075298892414161999234649, 0.209482141084727828012999174891714,
    )
    private val WG = doubleArrayOf(
        0.129484966168869693270611432679082, 0.279705391489276667901467771423780, 0.381830050505118944950369775488975,
        0.417959183673469387755102040816327,
    )

    private class Piece(val a: Double, val b: Double, val value: Double, val error: Double)

    private fun kronrod(f: (Double) -> Double, a: Double, b: Double): Piece {
        val c = 0.5 * (a + b)
        val h = 0.5 * (b - a)
        val fc = f(c)
        var k = fc * WGK[7]
        var g = fc * WG[3]
        for (j in 0 until 7) {
            val x = h * XGK[j]
            val s = f(c - x) + f(c + x)
            k += WGK[j] * s
            if (j % 2 == 1) g += WG[j / 2] * s
        }
        return Piece(a, b, k * h, abs((k - g) * h))
    }

    /** ∫ₐᵇ f by adaptive Gauss–Kronrod to the relative [rel]; null where it does not get there. */
    fun integrate(f: (Double) -> Double, a: Double, b: Double, rel: Double = 1e-12): Double? {
        if (a == b) return 0.0
        if (a > b) return integrate(f, b, a, rel)?.let { -it }
        val pieces = PriorityQueue<Piece>(compareByDescending { it.error })
        pieces += kronrod(f, a, b)
        var splits = 0
        while (true) {
            val total = pieces.sumOf { it.value }
            val error = pieces.sumOf { it.error }
            if (!total.isFinite() || !error.isFinite()) return null
            if (error <= rel * abs(total) || error == 0.0) return total
            if (splits >= 4000) return if (error <= 1e-9 * abs(total)) total else null
            val p = pieces.poll()
            val m = 0.5 * (p.a + p.b)
            if (m <= p.a || m >= p.b) return if (error <= 1e-9 * abs(total)) total else null
            pieces += kronrod(f, p.a, m)
            pieces += kronrod(f, m, p.b)
            splits++
            if (splits % 32 == 0) Budget.check()
        }
    }

    /**
     * ∫ f from [a] to [b], either of which may be infinite (mapped to [0, 1) by x = a + u/(1 − u)); [singularAt] a finite end
     * where f behaves as |x − end|^(s − 1) with s < 1 ([power]), taken away by x = end ± (b − a)·t^(1/s).
     */
    fun integrateTo(f: (Double) -> Double, a: Double, b: Double, rel: Double = 1e-12): Double? {
        if (a == b) return 0.0
        if (a > b) return integrateTo(f, b, a, rel)?.let { -it }
        return when {
            a.isInfinite() && b.isInfinite() -> {
                val left = integrateTo(f, a, 0.0, rel) ?: return null
                val right = integrateTo(f, 0.0, b, rel) ?: return null
                left + right
            }
            b.isInfinite() -> integrate({ u -> val w = 1 - u; val x = a + u / w; val v = f(x) / (w * w); if (v.isFinite()) v else 0.0 }, 0.0, 1.0, rel)
            a.isInfinite() -> integrate({ u -> val w = 1 - u; val x = b - u / w; val v = f(x) / (w * w); if (v.isFinite()) v else 0.0 }, 0.0, 1.0, rel)
            else -> integrate(f, a, b, rel)
        }
    }

    /** ∫ from [a] to [b] of f, which near [a] behaves as (x − a)^(s − 1): for s < 1 by x = a + (b − a)·t^(1/s). */
    fun integrateSingularStart(f: (Double) -> Double, a: Double, b: Double, s: Double, rel: Double = 1e-12): Double? {
        if (s >= 1 || b.isInfinite()) return integrateTo(f, a, b, rel)
        val q = 1 / s
        val width = b - a
        // dx = width·q·t^(q − 1) dt, and (x − a)^(s − 1) = (width·t^q)^(s − 1): their product is smooth.
        return integrate({ t -> if (t <= 0.0) 0.0 else f(a + width * Math.pow(t, q)) * width * q * Math.pow(t, q - 1) }, 0.0, 1.0, rel)
    }

    /** The same at the upper end [b], where f behaves as (b − x)^(s − 1). */
    fun integrateSingularEnd(f: (Double) -> Double, a: Double, b: Double, s: Double, rel: Double = 1e-12): Double? {
        if (s >= 1) return integrateTo(f, a, b, rel)
        val q = 1 / s
        val width = b - a
        return integrate({ t -> if (t <= 0.0) 0.0 else f(b - width * Math.pow(t, q)) * width * q * Math.pow(t, q - 1) }, 0.0, 1.0, rel)
    }
}
