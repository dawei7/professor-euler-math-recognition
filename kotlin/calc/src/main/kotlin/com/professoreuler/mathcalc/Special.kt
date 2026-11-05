package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.ln as lnD

/**
 * Special functions of real balls, each with a rigorous bound: Γ and ln Γ and ψ (Stirling's series after a shift, its
 * remainder bounded by the first omitted term), erf and erfc (Taylor's series with guard bits, or the asymptotic series),
 * ζ (Borwein's algorithm 2 with its error bound, and the functional equation), Bessel's J_n (its series), Lambert's W
 * (Newton's method, then certified by the sign of w·e^w − x on both sides) and the regularized incomplete Γ and B, on
 * which the distributions rest.
 */
internal object Special {
    // --------------------------------------------------------------------------------------------------------------------
    // Γ, ln Γ, ψ

    fun gamma(x: Ball, prec: Int): Ball {
        nonPositiveInteger(x)
        smallInteger(x)?.let { n -> if (n in 1..2000) return Ball.of(factorial(n - 1)).rounded(prec) }
        val w = prec + 32
        if (x.mid < BigFloat.HALF) {
            // Γ(x) = π / (sin(πx)·Γ(1 − x))
            val pi = BallMath.pi(w)
            val s = BallMath.sin(pi.mul(x, w), w)
            return pi.div(s.mul(gamma(Ball.ONE.sub(x, w), w), w), prec)
        }
        val lg = lnGammaPositive(x, w + magnitudeBits(x))
        return BallMath.exp(lg, prec)
    }

    /** ln Γ(x) for x > 0. */
    fun lnGamma(x: Ball, prec: Int): Ball {
        if (!x.isPositive) {
            if (x.upper.signum <= 0) throw CalcException(Failure.UNDEFINED)
            throw Imprecise()
        }
        return lnGammaPositive(x, prec)
    }

    /** ψ(x) = Γ′(x)/Γ(x). */
    fun digamma(x: Ball, prec: Int): Ball {
        nonPositiveInteger(x)
        val w = prec + 32
        if (x.mid < BigFloat.HALF) {
            // ψ(x) = ψ(1 − x) − π·cot(πx)
            val pi = BallMath.pi(w)
            val px = pi.mul(x, w)
            val cot = BallMath.cos(px, w).div(BallMath.sin(px, w), w)
            return digamma(Ball.ONE.sub(x, w), w).sub(pi.mul(cot, w), prec)
        }
        val (shift, k) = stirlingPlan(x, w)
        var shifted = Ball.ZERO
        for (i in 0 until shift) {
            shifted = shifted.add(x.add(Ball.of(i.toLong()), w).inverse(w), w)
            Budget.check()
        }
        val z = x.add(Ball.of(shift.toLong()), w)
        // ψ(z) = ln z − 1/(2z) − Σ B_2j / (2j·z^2j) − R, |R| ≤ |B_2k+2| / ((2k + 2)·z^(2k+2))
        val inv = z.inverse(w)
        val inv2 = inv.sqr(w)
        var power = inv2
        var sum = BallMath.ln(z, w).sub(inv.shl(-1), w)
        for (j in 1..k) {
            val coeff = Ball.of(Bernoulli.b2k(j) / Rational.of(2L * j), w)
            sum = sum.sub(coeff.mul(power, w), w)
            power = power.mul(inv2, w)
            Budget.check()
        }
        val rest = remainder(Bernoulli.b2k(k + 1).abs() / Rational.of(2L * k + 2), z, 2L * k + 2)
        return sum.widen(rest).sub(shifted, prec)
    }

    private fun lnGammaPositive(x: Ball, prec: Int): Ball {
        val w = prec + 16
        val (shift, k) = stirlingPlan(x, w)
        // ln Γ(x) = ln Γ(x + N) − ln(x (x + 1) … (x + N − 1))
        var product = Ball.ONE
        for (i in 0 until shift) {
            product = product.mul(x.add(Ball.of(i.toLong()), w), w)
            Budget.check()
        }
        val z = x.add(Ball.of(shift.toLong()), w)
        // (z − ½) ln z − z + ½ ln 2π + Σ B_2j / (2j (2j − 1) z^(2j−1)) ± |B_2k+2| / ((2k + 2)(2k + 1) z^(2k+1))
        val lnz = BallMath.ln(z, w)
        var sum = z.sub(Ball(BigFloat.HALF), w).mul(lnz, w).sub(z, w)
            .add(BallMath.ln(BallMath.pi(w).shl(1), w).shl(-1), w)
        val inv = z.inverse(w)
        val inv2 = inv.sqr(w)
        var power = inv
        for (j in 1..k) {
            val coeff = Ball.of(Bernoulli.b2k(j) / Rational.of(2L * j * (2L * j - 1)), w)
            sum = sum.add(coeff.mul(power, w), w)
            power = power.mul(inv2, w)
            Budget.check()
        }
        val rest = remainder(Bernoulli.b2k(k + 1).abs() / Rational.of((2L * k + 2) * (2L * k + 1)), z, 2L * k + 1)
        sum = sum.widen(rest)
        return if (shift == 0) sum.rounded(prec) else sum.sub(BallMath.ln(product, w), prec)
    }

    /** c / z^n with z at its lowest, rounded up: the bound of a remainder. */
    private fun remainder(c: Rational, z: Ball, n: Long): BigFloat {
        val lo = z.lower
        if (lo.signum <= 0) throw Imprecise()
        val bound = Ball.of(c, 64).div(Ball(lo).pow(BigInteger.valueOf(n), 64), 64)
        return bound.magUp()
    }

    /**
     * How far to shift x up (N) and how many terms of Stirling's series to sum (K) for [prec] bits: the remainder
     * |B_2K+2| / ((2K+2)(2K+1) z^(2K+1)) below 2^-prec, with z = x + N at least about prec/2, so K stays near prec/8.
     */
    private fun stirlingPlan(x: Ball, prec: Int): Pair<Int, Int> {
        val xd = x.mid.toDouble()
        val z0 = maxOf(10.0, prec * 0.5)
        val shift = if (xd >= z0) 0 else kotlin.math.ceil(z0 - xd).toInt()
        val z = xd + shift
        val target = -(prec + 8) * lnD(2.0)
        var k = 1
        while (k < 100_000) {
            // ln of the remainder's size: ln(2·(2k+2)!) − (2k+2)·ln 2π − ln((2k+2)(2k+1)) − (2k+1)·ln z
            val m = 2.0 * k + 2
            val size = lnD(2.0) + lnFactorial(m) - m * lnD(2 * Math.PI) - lnD(m * (m - 1)) - (m - 1) * lnD(z)
            if (size < target) return shift to k
            k++
        }
        throw CalcException(Failure.TOO_LONG)
    }

    private fun lnFactorial(n: Double): Double {
        if (n < 2) return 0.0
        // Stirling's formula is ample for choosing a count of terms.
        return n * lnD(n) - n + 0.5 * lnD(2 * Math.PI * n) + 1 / (12 * n)
    }

    // --------------------------------------------------------------------------------------------------------------------
    // erf, erfc

    fun erf(x: Ball, prec: Int): Ball {
        if (x.isExactZero) return Ball.ZERO
        if (x.mid.signum < 0) return -erf(-x, prec)
        val xd = x.mid.toDouble()
        if (xd * xd * LOG2E > prec + 16 && x.isPositive) {
            return Ball.ONE.sub(erfcAsymptotic(x, prec + 8), prec)
        }
        return erfTaylor(x, prec)
    }

    fun erfc(x: Ball, prec: Int): Ball {
        if (x.mid.signum <= 0 || !x.isPositive) return Ball.ONE.sub(erf(x, prec + 8), prec)
        val xd = x.mid.toDouble()
        if (xd * xd * LOG2E > prec + 16) return erfcAsymptotic(x, prec)
        // erfc is about e^(−x²): ask erf for that many bits more.
        val extra = (xd * xd * LOG2E).toInt() + 16
        return Ball.ONE.sub(erfTaylor(x, prec + extra), prec)
    }

    /** erf x = 2/√π · Σ (−1)ⁿ x^(2n+1) / (n!·(2n + 1)), with guard bits for the cancellation (terms reach e^(x²)). */
    private fun erfTaylor(x: Ball, prec: Int): Ball {
        val xd = x.mid.toDouble()
        val w = prec + (xd * xd * LOG2E).toInt() + 24
        val x2 = x.sqr(w)
        var t = x // x^(2n+1)/n!
        var sum = x
        var n = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            t = -t.mul(x2, w).div(n, w)
            val term = t.div(2 * n + 1, w)
            sum = sum.add(term, w)
            // Past n > x², the terms fall in size and alternate: the tail is below the next one, which is below this one.
            if (n > xd * xd + 1 && term.magUp() < limit) {
                sum = sum.widen(term.magUp())
                break
            }
            n++
            Budget.check()
        }
        val factor = Ball.of(2).div(BallMath.sqrt(BallMath.pi(w), w), w)
        return sum.mul(factor, prec)
    }

    /**
     * erfc x for large x: e^(−x²)/(x√π) · Σ (−1)ⁿ (2n − 1)!! / (2x²)ⁿ, the remainder below the first term left out (x real,
     * positive); used only where the terms fall below 2^-prec before they start to grow.
     */
    private fun erfcAsymptotic(x: Ball, prec: Int): Ball {
        val w = prec + 16
        val inv = x.sqr(w).shl(1).inverse(w) // 1/(2x²)
        var t = Ball.ONE
        var sum = Ball.ONE
        var n = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            t = -t.mul(inv, w).mul(2 * n - 1, w)
            if (t.magUp() < limit) {
                sum = sum.widen(t.magUp())
                break
            }
            sum = sum.add(t, w)
            n++
            if (n > 1_000_000) throw Imprecise()
            Budget.check()
        }
        val prefactor = BallMath.exp(-x.sqr(w), w).div(x.mul(BallMath.sqrt(BallMath.pi(w), w), w), w)
        return sum.mul(prefactor, prec)
    }

    // --------------------------------------------------------------------------------------------------------------------
    // ζ

    fun zeta(s: Ball, prec: Int): Ball {
        smallInteger(s)?.let { n ->
            if (n == 1L) throw CalcException(Failure.UNDEFINED)
            if (n == 0L) return Ball(BigFloat.HALF.let { -it })
            if (n < 0) {
                // ζ(−n) = −B_(n+1) / (n + 1): 0 for even n, and a Bernoulli number for odd n
                val m = -n
                if (m % 2 == 0L) return Ball.ZERO
                val b = Bernoulli.b2k(((m + 1) / 2).toInt())
                return Ball.of(-b / Rational.of(m + 1), prec)
            }
        }
        val w = prec + 32
        if (s.mid < BigFloat.HALF) {
            // ζ(s) = 2^s π^(s−1) sin(πs/2) Γ(1 − s) ζ(1 − s)
            val pi = BallMath.pi(w)
            val oneMinus = Ball.ONE.sub(s, w)
            val twoS = BallMath.exp(s.mul(BallMath.ln2(w), w), w)
            val piPow = BallMath.exp(s.sub(Ball.ONE, w).mul(BallMath.ln(pi, w), w), w)
            val sine = BallMath.sin(pi.mul(s, w).shl(-1), w)
            return twoS.mul(piPow, w).mul(sine, w).mul(gamma(oneMinus, w), w).mul(zeta(oneMinus, w), prec)
        }
        // Borwein: ζ(s) = −1/(d_n (1 − 2^(1−s))) Σ_(k<n) (−1)^k (d_k − d_n)/(k + 1)^s + γ_n,
        // |γ_n| ≤ 3 / ((3 + √8)^n · |1 − 2^(1−s)|) for real s ≥ ½ (bounded here by 4).
        val n = ((w + 12) / 2.54).toInt() + 2
        val d = borweinD(n)
        val dn = d[n]
        val integer = smallInteger(s)
        var sum = Ball.ZERO
        for (k in 0 until n) {
            val base = Ball.of((k + 1).toLong())
            val power = if (integer != null) base.pow(BigInteger.valueOf(integer), w) else BallMath.exp(s.mul(BallMath.ln(base, w), w), w)
            val numerator = Ball.of(d[k] - dn)
            val term = numerator.div(power, w)
            sum = if (k % 2 == 0) sum.add(term, w) else sum.sub(term, w)
            Budget.check()
        }
        val factor = Ball.ONE.sub(BallMath.exp(Ball.ONE.sub(s, w).mul(BallMath.ln2(w), w), w), w) // 1 − 2^(1−s)
        val value = -sum.div(Ball.of(dn).mul(factor, w), w)
        val bound = Ball.of(4).div(BallMath.exp(Ball.of(n.toLong()).mul(BallMath.ln(Ball.of(3).add(BallMath.sqrt(Ball.of(8), 64), 64), 64), 64), 64)
            .mul(Ball(factor.magDown().takeIf { !it.isZero } ?: throw Imprecise()), 64), 64)
        return value.widen(bound.magUp()).rounded(prec)
    }

    /** Borwein's d_0 … d_n: d_k = n Σ_(i≤k) (n + i − 1)! 4^i / ((n − i)! (2i)!), all whole numbers. */
    private fun borweinD(n: Int): List<BigInteger> {
        val out = ArrayList<BigInteger>(n + 1)
        var term = Rational.ONE // the i-th summand, times n
        var sum = Rational.ZERO
        for (i in 0..n) {
            if (i > 0) {
                val num = 4L * (n + i - 1) * (n - i + 1)
                term = term * Rational.of(BigInteger.valueOf(num), BigInteger.valueOf(2L * i * (2L * i - 1)))
            }
            sum = sum + term
            out.add(sum.num)
        }
        return out
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Bessel J_n, Lambert W

    /** J_n(x) for a whole order n: Σ (−1)^k (x/2)^(2k+n) / (k!·(k + n)!), with guard bits (terms reach e^|x|). */
    fun besselJ(order: Long, x: Ball, prec: Int): Ball {
        if (order < 0) return besselJ(-order, x, prec).let { if (order % 2 != 0L) -it else it }
        if (x.isExactZero) return if (order == 0L) Ball.ONE else Ball.ZERO
        if (order > 100_000) throw CalcException(Failure.TOO_LARGE)
        val xd = kotlin.math.abs(x.mid.toDouble())
        val w = prec + (xd * LOG2E).toInt() + 24
        val h = x.shl(-1)
        val h2 = h.sqr(w)
        var t = h.pow(BigInteger.valueOf(order), w).div(Ball.of(factorial(order)), w)
        var sum = t
        var k = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            t = -t.mul(h2, w).div(Ball.of(k * (k + order)), w)
            sum = sum.add(t, w)
            // Once (k + 1)(k + n + 1) > 2·(x/2)², each term is below half the one before: the tail is below this term.
            if ((k + 1) * (k + order + 1) > 2 * xd * xd / 4 + 1 && t.magUp() < limit) {
                sum = sum.widen(t.magUp())
                break
            }
            k++
            Budget.check()
        }
        return sum.rounded(prec)
    }

    /** The principal branch W₀(x) for x ≥ −1/e: the w ≥ −1 with w·e^w = x. */
    fun lambertW(x: Ball, prec: Int): Ball {
        if (x.isExactZero) return Ball.ZERO
        val w = prec + 16
        val minusInvE = -BallMath.e(w).inverse(w)
        if (x.below(minusInvE)) throw CalcException(Failure.UNDEFINED)
        if (!minusInvE.below(x)) throw Imprecise()
        // A double first, then Newton at doubling precision on the midpoint.
        val xd = x.mid.toDouble()
        var guess = when {
            xd > 3 -> lnD(xd) - lnD(lnD(xd))
            xd > -0.32 -> xd * (1 - xd)
            else -> -1 + kotlin.math.sqrt(2 * (1 + Math.E * xd))
        }
        repeat(60) {
            val e = kotlin.math.exp(guess)
            val f = guess * e - xd
            val step = f / (e * (guess + 1) - (guess + 2) * f / (2 * guess + 2))
            if (!step.isFinite()) return@repeat
            guess -= step
        }
        var v = BigFloat.of(guess)
        var p = 53
        val xm = Ball(x.mid)
        while (true) {
            p = minOf(2 * p, w)
            val vb = Ball(v)
            val ev = BallMath.exp(vb, p + 16)
            val f = vb.mul(ev, p + 16).sub(xm, p + 16)
            val df = ev.mul(vb.add(Ball.ONE, p + 16), p + 16)
            v = vb.sub(f.div(df, p + 16), p + 16).mid.round(p, Round.NEAREST)
            if (p >= w) break
            Budget.check()
        }
        // Certify: w·e^w − x changes sign across [v − δ, v + δ] for every x in the ball.
        var delta = if (v.isZero) BigFloat.twoTo(-w.toLong()) else BigFloat.twoTo(maxOf(v.top, -w.toLong()) - w + 4)
        delta = addUp(delta, x.rad)
        repeat(8) {
            val lo = Ball(v - delta)
            val hi = Ball(v + delta)
            val fLo = lo.mul(BallMath.exp(lo, w), w).sub(x, w)
            val fHi = hi.mul(BallMath.exp(hi, w), w).sub(x, w)
            if (fLo.isNegative && fHi.isPositive) return Ball(v, up(delta)).rounded(prec)
            delta = delta.shl(4)
        }
        throw Imprecise()
    }

    // --------------------------------------------------------------------------------------------------------------------
    // Regularized incomplete Γ and B

    /** P(s, x) = γ(s, x)/Γ(s) for s > 0, x ≥ 0: x^s e^(−x)/Γ(s + 1) · Σ xⁿ / ((s + 1)…(s + n)), positive terms. */
    fun gammaP(s: Ball, x: Ball, prec: Int): Ball {
        if (!s.isPositive) throw CalcException(Failure.UNDEFINED)
        if (x.isExactZero) return Ball.ZERO
        if (x.isNegative) throw CalcException(Failure.UNDEFINED)
        if (!x.isPositive) throw Imprecise()
        val xd = x.mid.toDouble()
        val w = prec + (xd * LOG2E).toInt() + 24
        var t = Ball.ONE
        var sum = Ball.ONE
        var n = 1L
        val limit = BigFloat.twoTo(-w.toLong())
        while (true) {
            t = t.mul(x, w).div(s.add(Ball.of(n), w), w)
            sum = sum.add(t, w)
            // ratio x/(s + n + 1) < ½ from here on: the tail is below this term
            if (xd < 0.5 * (s.mid.toDouble() + n + 1) - 1 && t.magUp() < limit) {
                sum = sum.widen(t.magUp())
                break
            }
            n++
            if (n > 10_000_000) throw CalcException(Failure.TOO_LONG)
            Budget.check()
        }
        val prefactor = BallMath.exp(s.mul(BallMath.ln(x, w), w).sub(x, w).sub(lnGamma(s.add(Ball.ONE, w), w), w), w)
        return sum.mul(prefactor, prec)
    }

    /** Q(s, x) = 1 − P(s, x). */
    fun gammaQ(s: Ball, x: Ball, prec: Int): Ball {
        val extra = (x.mid.toDouble() * LOG2E).toInt().coerceAtLeast(0)
        return Ball.ONE.sub(gammaP(s, x, prec + extra + 16), prec)
    }

    /** I_x(a, b) for a, b > 0 and 0 ≤ x ≤ 1. */
    fun betaI(x: Ball, a: Ball, b: Ball, prec: Int): Ball {
        if (!a.isPositive || !b.isPositive) throw CalcException(Failure.UNDEFINED)
        if (x.isExactZero) return Ball.ZERO
        if (x.isExact && x.mid == BigFloat.ONE) return Ball.ONE
        if (x.isNegative || Ball.ONE.below(x)) throw CalcException(Failure.UNDEFINED)
        if (!x.isPositive || !x.below(Ball.ONE)) throw Imprecise()
        val w = prec + 32
        // Where the series would be slow, I_x(a, b) = 1 − I_(1−x)(b, a).
        val split = a.add(Ball.ONE, w).div(a.add(b, w).add(Ball.of(2), w), w)
        if (split.below(x)) return Ball.ONE.sub(betaI(Ball.ONE.sub(x, w), b, a, w), prec)
        // x^a (1 − x)^b / (a·B(a, b)) · Σ (a + b)ₙ / (a + 1)ₙ · xⁿ
        var t = Ball.ONE
        var sum = Ball.ONE
        var n = 0L
        val limit = BigFloat.twoTo(-w.toLong())
        val xUp = x.upper
        while (true) {
            val ratio = a.add(b, w).add(Ball.of(n), w).div(a.add(Ball.ONE, w).add(Ball.of(n), w), w).mul(x, w)
            t = t.mul(ratio, w)
            sum = sum.add(t, w)
            n++
            // the ratios beyond are at most r = max(this ratio, x): the tail is below t·r/(1 − r)
            val r = if (ratio.upper > xUp) ratio.upper else xUp
            if (r < BigFloat.ONE && t.magUp() < limit) {
                val rb = Ball(r)
                val tail = Ball(t.magUp()).mul(rb, 64).div(Ball.ONE.sub(rb, 64), 64).magUp()
                if (tail < limit.shl(4)) {
                    sum = sum.widen(tail)
                    break
                }
            }
            if (n > 10_000_000) throw CalcException(Failure.TOO_LONG)
            Budget.check()
        }
        val lnBeta = lnGamma(a, w).add(lnGamma(b, w), w).sub(lnGamma(a.add(b, w), w), w)
        val prefactor = BallMath.exp(
            a.mul(BallMath.ln(x, w), w).add(b.mul(BallMath.ln(Ball.ONE.sub(x, w), w), w), w).sub(lnBeta, w), w,
        ).div(a, w)
        return sum.mul(prefactor, prec)
    }

    // --------------------------------------------------------------------------------------------------------------------

    private const val LOG2E = 1.4426950408889634

    /** A ball that is exactly a whole number of moderate size, as a Long. */
    fun smallInteger(x: Ball): Long? {
        if (!x.isExact || !x.mid.isInteger) return null
        val v = x.mid.floor()
        return if (v.bitLength() < 62) v.toLong() else null
    }

    private fun nonPositiveInteger(x: Ball) {
        val n = smallInteger(x)
        if (n != null && n <= 0) throw CalcException(Failure.UNDEFINED)
    }

    private fun magnitudeBits(x: Ball): Int {
        val top = x.mid.top
        return if (top <= 0) 0 else (top * 2 + 8).coerceAtMost(4096).toInt()
    }

    fun factorial(n: Long): BigInteger {
        var f = BigInteger.ONE
        for (i in 2..n) {
            f = f.multiply(BigInteger.valueOf(i))
            if (i and 255L == 0L) Budget.check()
        }
        return f
    }
}

/** Exact Bernoulli numbers B_2k, from the tangent numbers (Brent and Harvey's algorithm, integers only). */
internal object Bernoulli {
    @Volatile private var table: List<Rational> = emptyList()

    /** B_2k for k ≥ 1. */
    fun b2k(k: Int): Rational {
        require(k >= 1)
        table.let { if (k <= it.size) return it[k - 1] }
        synchronized(this) {
            if (k <= table.size) return table[k - 1]
            val n = maxOf(k, table.size * 2, 32)
            val t = tangentNumbers(n)
            table = (1..n).map { j ->
                val pow = BigInteger.ONE.shiftLeft(2 * j)
                val num = BigInteger.valueOf(2L * j) * t[j - 1]
                Rational.of(if (j % 2 == 1) num else num.negate(), pow * (pow - BigInteger.ONE))
            }
            return table[k - 1]
        }
    }

    /** T_1 … T_n. */
    private fun tangentNumbers(n: Int): Array<BigInteger> {
        val t = Array(n) { BigInteger.ZERO }
        t[0] = BigInteger.ONE
        for (k in 1 until n) t[k] = t[k - 1] * BigInteger.valueOf(k.toLong())
        for (k in 1 until n) {
            for (j in k until n) t[j] = t[j - 1] * BigInteger.valueOf((j - k).toLong()) + t[j] * BigInteger.valueOf((j - k + 2).toLong())
            Budget.check()
        }
        return t
    }
}
