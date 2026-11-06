package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.ln as lnD
import kotlin.math.exp as expD
import com.professoreuler.mathcalc.StatNumbers.agree
import com.professoreuler.mathcalc.StatNumbers.ball
import com.professoreuler.mathcalc.StatNumbers.certified
import com.professoreuler.mathcalc.StatNumbers.compare
import com.professoreuler.mathcalc.StatNumbers.double
import com.professoreuler.mathcalc.StatNumbers.infinity
import com.professoreuler.mathcalc.StatNumbers.lnBetaD
import com.professoreuler.mathcalc.StatNumbers.lnGammaD
import com.professoreuler.mathcalc.StatNumbers.signOf

/**
 * The distributions beyond the binomial, Poisson, geometric and normal ones ([Stats]), as number functions with the argument
 * orders of those: a density at x then the parameters (`tpdf(x, ν)`, as `normalpdf(x, μ, σ)`), a probability between two bounds
 * then the parameters (`tcdf(a, b, ν)`, as `normalcdf(a, b, μ, σ)`, a bound may be ±∞), a quantile of a share p then the
 * parameters (`invt(p, ν)`, as `invnorm(p, μ, σ)`), and a discrete one's parameters then k (`hypergeompdf(N, K, n, k)`, as
 * `binompdf(n, p, k)`). Student's t, χ² (written chisq: a name holds no digit), F, hypergeometric, uniform on [a, b], exponential
 * with rate λ, gamma with shape k and scale θ (mean kθ: the scale is what spreadsheets and most textbooks take, and makes the
 * exponential with mean θ its k = 1), beta, and lognormal (μ and σ those of ln X).
 *
 * Every value is checked before it is given, by a second computation of its own: a probability, worked out from the regularized
 * incomplete Γ and B in ball arithmetic ([Special]), against the density integrated numerically (adaptive Gauss–Kronrod in
 * doubles, its constant from a Lanczos ln Γ: [StatNumbers]); a density against the derivative of the distribution function; a
 * quantile, bracketed in balls, against the integral up to it; the hypergeometric probabilities, exact fractions, against their
 * recurrence and their sum. A decimal has only certified digits. A parameter outside its range is refused
 * ([Failure.UNDEFINED]), never a number.
 */
internal object Distributions {
    /** The names, as [Discrete.NAMES] takes them, and how many arguments each takes. */
    val NAMES: Map<String, IntRange> = mapOf(
        "tpdf" to 2..2, "tcdf" to 3..3, "invt" to 2..2,
        "chisqpdf" to 2..2, "chisqcdf" to 3..3, "invchisq" to 2..2,
        "fpdf" to 3..3, "fcdf" to 4..4, "invf" to 3..3,
        "hypergeompdf" to 4..4, "hypergeomcdf" to 4..4,
        "uniformpdf" to 3..3, "uniformcdf" to 4..4, "invuniform" to 3..3,
        "exppdf" to 2..2, "expcdf" to 3..3, "invexp" to 2..2,
        "gammapdf" to 3..3, "gammacdf" to 4..4, "invgamma" to 3..3,
        "betapdf" to 3..3, "betacdf" to 4..4, "invbeta" to 3..3,
        "lognormalpdf" to 3..3, "lognormalcdf" to 4..4, "invlognormal" to 3..3,
        "quantile" to 2..2, "cov" to 2..10_000, "corr" to 2..10_000,
    )

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /**
     * [name] at [args]: null where it is none of [NAMES] or an argument holds a letter (then the letters are a product, as for
     * every number function); numbers, but not as many as it takes, fail ([Failure.ARGUMENTS]).
     */
    fun call(name: String, args: List<Expr>): Expr? = Budget.bounded(MathCalc.LIMIT_MS) { work(name, args) }

    private fun work(name: String, args: List<Expr>): Expr? {
        val arity = NAMES[name] ?: return null
        if (args.any { it.symbols().isNotEmpty() }) return null
        if (name == "quantile" || name == "cov" || name == "corr") return Describe.call(name, args)
        if (args.any { it is Expr.Mat }) return null
        if (args.size !in arity) throw CalcException(Failure.ARGUMENTS)
        if (args.any { it.hasI() || it.hasOp() }) throw CalcException(Failure.UNDEFINED)
        Budget.check()
        val a = args
        return when (name) {
            "tpdf" -> StudentT(positive(a[1])).let { d -> pdf(d, finite(a[0])) { exactT(a[0], d.nu) } }
            "tcdf" -> StudentT(positive(a[2])).let { d -> cdf(d, a[0], a[1]) }
            "invt" -> StudentT(positive(a[1])).let { d -> inverse(d, share(a[0])) }
            "chisqpdf" -> chiSquare(positive(a[1])).let { d -> pdf(d, finite(a[0])) { exactGamma(a[0], d.k, d.theta) } }
            "chisqcdf" -> chiSquare(positive(a[2])).let { d -> cdf(d, a[0], a[1]) }
            "invchisq" -> chiSquare(positive(a[1])).let { d -> inverse(d, share(a[0])) }
            "fpdf" -> FDist(positive(a[1]), positive(a[2])).let { d -> pdf(d, finite(a[0])) { exactF(a[0], d.d1, d.d2) } }
            "fcdf" -> FDist(positive(a[2]), positive(a[3])).let { d -> cdf(d, a[0], a[1]) }
            "invf" -> FDist(positive(a[1]), positive(a[2])).let { d -> inverse(d, share(a[0])) }
            "gammapdf" -> GammaDist(positive(a[1]), positive(a[2])).let { d -> pdf(d, finite(a[0])) { exactGamma(a[0], d.k, d.theta) } }
            "gammacdf" -> GammaDist(positive(a[2]), positive(a[3])).let { d -> cdf(d, a[0], a[1]) }
            "invgamma" -> GammaDist(positive(a[1]), positive(a[2])).let { d -> inverse(d, share(a[0])) }
            "betapdf" -> BetaDist(positive(a[1]), positive(a[2])).let { d -> pdf(d, finite(a[0])) { exactBeta(a[0], d.alpha, d.beta) } }
            "betacdf" -> BetaDist(positive(a[2]), positive(a[3])).let { d -> cdf(d, a[0], a[1]) }
            "invbeta" -> BetaDist(positive(a[1]), positive(a[2])).let { d -> betaInverse(d, share(a[0])) }
            "lognormalpdf" -> LogNormal(finite(a[1]), positive(a[2])).let { d -> pdf(d, finite(a[0])) { exactLogNormal(a[0], d.mu, d.sigma) } }
            "lognormalcdf" -> LogNormal(finite(a[2]), positive(a[3])).let { d -> cdf(d, a[0], a[1]) }
            "invlognormal" -> LogNormal(finite(a[1]), positive(a[2])).let { d -> inverse(d, share(a[0])) }
            "exppdf" -> exponentialPdf(finite(a[0]), positive(a[1]))
            "expcdf" -> exponentialCdf(a[0], a[1], positive(a[2]))
            "invexp" -> exponentialInverse(share(a[0]), positive(a[1]))
            "uniformpdf" -> uniform(a[1], a[2]).let { (lo, hi) -> uniformPdf(finite(a[0]), lo, hi) }
            "uniformcdf" -> uniform(a[2], a[3]).let { (lo, hi) -> uniformCdf(a[0], a[1], lo, hi) }
            "invuniform" -> uniform(a[1], a[2]).let { (lo, hi) -> uniformInverse(share(a[0]), lo, hi) }
            "hypergeompdf", "hypergeomcdf" -> hypergeometric(a[0], a[1], a[2], a[3], cumulative = name == "hypergeomcdf")
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Arguments.

    /** A parameter that must be positive and finite (ν, k, σ, λ, θ, α, β, the degrees of freedom). */
    private fun positive(e: Expr): Expr {
        if (infinity(e) != 0 || signOf(e) <= 0) throw CalcException(Failure.UNDEFINED)
        return e
    }

    private fun finite(e: Expr): Expr {
        if (infinity(e) != 0) throw CalcException(Failure.UNDEFINED)
        signOf(e)
        return e
    }

    /** A share p of a quantile: 0 < p < 1, exactly (a decimal is the fraction it writes). */
    private fun share(e: Expr): Rational {
        val r = rational(e) ?: throw CalcException(Failure.UNDEFINED)
        if (r.signum <= 0 || r >= Rational.ONE) throw CalcException(Failure.UNDEFINED)
        return r
    }

    private fun uniform(a: Expr, b: Expr): Pair<Expr, Expr> {
        finite(a)
        finite(b)
        if (compare(a, b) >= 0) throw CalcException(Failure.UNDEFINED)
        return a to b
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The continuous distributions.

    /** A continuous distribution: its two tails in balls (inside its support), its density in balls and in doubles. */
    internal abstract class Continuous {
        /** Where the support starts and ends: −∞ or 0, and 1 or ∞. */
        abstract val start: Double
        abstract val end: Double

        /** A point near the middle: an interval above it is worked out from the upper tail. */
        abstract val middle: Double

        /** Where the density behaves as (x − start)^(s − 1) and as (end − x)^(s − 1): s, for the check's integral. */
        open val startPower: Double get() = 1.0
        open val endPower: Double get() = 1.0

        /** Where the density falls as |x|^(−1 − s) towards ±∞: s (ν for t, d₂/2 for F); infinite where it falls faster. */
        open val tailPower: Double get() = Double.POSITIVE_INFINITY

        /** P(X ≤ x), for x inside the support. */
        abstract fun lower(x: Ball, prec: Int): Ball

        /** P(X > x), for x inside the support. */
        abstract fun upper(x: Ball, prec: Int): Ball

        /** The density at x inside the support, in balls. */
        abstract fun density(x: Ball, prec: Int): Ball

        /** The density in doubles, its constant from Lanczos' ln Γ: the checks' own. */
        abstract fun densityD(x: Double): Double

        /**
         * The density where the support starts (x = 0) or ends (x = 1), where it is a value of its own: exact, or null where it
         * is infinite (then refused).
         */
        open fun densityAtStart(): Expr? = Expr.ZERO
        open fun densityAtEnd(): Expr? = Expr.ZERO

        /** P(X ≤ x) where it is exact without a calculation (the middle of a symmetric distribution), else null. */
        open fun exactLower(x: Expr): Expr? = null

        /** The quantile where it is exact without a calculation, else null. */
        open fun exactQuantile(p: Rational): Expr? = null
    }

    /** Student's t with ν degrees of freedom: P(T > t) = ½·I_(ν/(ν + t²))(ν/2, ½) for t > 0. */
    internal class StudentT(val nu: Expr) : Continuous() {
        private val nuD = valueOf(nu)
        override val start = Double.NEGATIVE_INFINITY
        override val end = Double.POSITIVE_INFINITY
        override val middle = 0.0
        private val lnC = lnGammaD((nuD + 1) / 2) - lnGammaD(nuD / 2) - 0.5 * lnD(nuD * Math.PI)
        override val tailPower get() = nuD

        private fun tail(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val n = ball(nu, w)
            val z = n.div(n.add(x.sqr(w), w), w)
            return Special.betaI(z, n.shl(-1), Ball(BigFloat.HALF), w).shl(-1).rounded(prec)
        }

        override fun upper(x: Ball, prec: Int): Ball = when {
            x.isExactZero -> Ball(BigFloat.HALF)
            x.isPositive -> tail(x, prec)
            x.isNegative -> Ball.ONE.sub(tail(-x, prec + 8), prec)
            else -> throw Imprecise()
        }

        override fun lower(x: Ball, prec: Int): Ball = upper(-x, prec)

        override fun density(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val n = ball(nu, w)
            val half = Ball(BigFloat.HALF)
            val lg = Special.lnGamma(n.add(Ball.ONE, w).shl(-1), w).sub(Special.lnGamma(n.shl(-1), w), w)
                .sub(BallMath.ln(n.mul(BallMath.pi(w), w), w).mul(half, w), w)
            val body = BallMath.ln(Ball.ONE.add(x.sqr(w).div(n, w), w), w).mul(n.add(Ball.ONE, w).shl(-1), w)
            return BallMath.exp(lg.sub(body, w), prec)
        }

        override fun densityD(x: Double): Double = expD(lnC - (nuD + 1) / 2 * kotlin.math.ln1p(x * x / nuD))

        override fun exactLower(x: Expr): Expr? = if (isZero(x)) Expr.HALF else null
        override fun exactQuantile(p: Rational): Expr? = if (p == Rational.of(1) / Rational.of(2)) Expr.ZERO else null
    }

    /** The standard normal distribution: P(Z > z) = ½·erfc(z/√2). For the tests' and intervals' z. */
    internal object Normal : Continuous() {
        override val start = Double.NEGATIVE_INFINITY
        override val end = Double.POSITIVE_INFINITY
        override val middle = 0.0

        private fun scaled(x: Ball, w: Int): Ball = x.div(BallMath.sqrt(Ball.of(2), w), w)

        override fun upper(x: Ball, prec: Int): Ball = Special.erfc(scaled(x, prec + 16), prec + 8).shl(-1).rounded(prec)
        override fun lower(x: Ball, prec: Int): Ball = upper(-x, prec)

        override fun density(x: Ball, prec: Int): Ball {
            val w = prec + 16
            return BallMath.exp(-x.sqr(w).shl(-1), w).div(BallMath.sqrt(BallMath.pi(w).shl(1), w), prec)
        }

        override fun densityD(x: Double): Double = expD(-x * x / 2) / kotlin.math.sqrt(2 * Math.PI)
        override fun exactLower(x: Expr): Expr? = if (isZero(x)) Expr.HALF else null
        override fun exactQuantile(p: Rational): Expr? = if (p == Rational.of(1) / Rational.of(2)) Expr.ZERO else null
    }

    /** The gamma distribution with shape k and scale θ: P(X ≤ x) = P(k, x/θ), the regularized lower incomplete Γ. */
    internal open class GammaDist(val k: Expr, val theta: Expr) : Continuous() {
        private val kD = valueOf(k)
        private val thetaD = valueOf(theta)
        override val start = 0.0
        override val end = Double.POSITIVE_INFINITY
        override val middle = kD * thetaD
        override val startPower get() = kD
        private val lnC = -lnGammaD(kD) - kD * lnD(thetaD)

        override fun lower(x: Ball, prec: Int): Ball {
            val w = prec + 16
            return Special.gammaP(ball(k, w), x.div(ball(theta, w), w), prec)
        }

        override fun upper(x: Ball, prec: Int): Ball {
            val w = prec + 16
            return Special.gammaQ(ball(k, w), x.div(ball(theta, w), w), prec)
        }

        override fun density(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val kb = ball(k, w)
            val t = ball(theta, w)
            val l = kb.sub(Ball.ONE, w).mul(BallMath.ln(x, w), w).sub(x.div(t, w), w)
                .sub(Special.lnGamma(kb, w), w).sub(kb.mul(BallMath.ln(t, w), w), w)
            return BallMath.exp(l, prec)
        }

        override fun densityD(x: Double): Double = if (x <= 0) 0.0 else expD(lnC + (kD - 1) * lnD(x) - x / thetaD)

        override fun densityAtStart(): Expr? = when (compare(k, Expr.ONE)) {
            -1 -> null
            0 -> tidy(Expr.ONE / theta)
            else -> Expr.ZERO
        }
    }

    /** χ² with k degrees of freedom: the gamma distribution with shape k/2 and scale 2. */
    internal fun chiSquare(k: Expr) = GammaDist(tidy(k / Expr.TWO), Expr.TWO)

    /** The beta distribution on [0, 1]: P(X ≤ x) = I_x(α, β). */
    internal class BetaDist(val alpha: Expr, val beta: Expr) : Continuous() {
        private val aD = valueOf(alpha)
        private val bD = valueOf(beta)
        override val start = 0.0
        override val end = 1.0
        override val middle = aD / (aD + bD)
        override val startPower get() = aD
        override val endPower get() = bD
        private val lnC = -lnBetaD(aD, bD)

        override fun lower(x: Ball, prec: Int): Ball = Special.betaI(x, ball(alpha, prec + 16), ball(beta, prec + 16), prec)
        override fun upper(x: Ball, prec: Int): Ball {
            val w = prec + 16
            return Special.betaI(Ball.ONE.sub(x, w), ball(beta, w), ball(alpha, w), prec)
        }

        override fun density(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val a = ball(alpha, w)
            val b = ball(beta, w)
            val lnB = Special.lnGamma(a, w).add(Special.lnGamma(b, w), w).sub(Special.lnGamma(a.add(b, w), w), w)
            val l = a.sub(Ball.ONE, w).mul(BallMath.ln(x, w), w).add(b.sub(Ball.ONE, w).mul(BallMath.ln(Ball.ONE.sub(x, w), w), w), w).sub(lnB, w)
            return BallMath.exp(l, prec)
        }

        override fun densityD(x: Double): Double =
            if (x <= 0 || x >= 1) 0.0 else expD(lnC + (aD - 1) * lnD(x) + (bD - 1) * kotlin.math.ln1p(-x))

        private fun edge(power: Expr, other: Expr): Expr? = when (compare(power, Expr.ONE)) {
            -1 -> null
            // α = 1: the density at 0 is 1/B(1, β) = β.
            0 -> other
            else -> Expr.ZERO
        }

        override fun densityAtStart(): Expr? = edge(alpha, beta)
        override fun densityAtEnd(): Expr? = edge(beta, alpha)
        override fun exactLower(x: Expr): Expr? = if (alpha == beta && x == Expr.HALF) Expr.HALF else null
        override fun exactQuantile(p: Rational): Expr? = if (alpha == beta && p == Rational.of(1) / Rational.of(2)) Expr.HALF else null
    }

    /** F with d₁ and d₂ degrees of freedom: P(X ≤ x) = I_(d₁x/(d₁x + d₂))(d₁/2, d₂/2). */
    internal class FDist(val d1: Expr, val d2: Expr) : Continuous() {
        private val aD = valueOf(d1)
        private val bD = valueOf(d2)
        override val start = 0.0
        override val end = Double.POSITIVE_INFINITY
        override val middle = 1.0
        override val startPower get() = aD / 2
        private val lnC = aD / 2 * lnD(aD) + bD / 2 * lnD(bD) - lnBetaD(aD / 2, bD / 2)
        override val tailPower get() = bD / 2

        override fun lower(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val a = ball(d1, w)
            val b = ball(d2, w)
            val ax = a.mul(x, w)
            return Special.betaI(ax.div(ax.add(b, w), w), a.shl(-1), b.shl(-1), prec)
        }

        override fun upper(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val a = ball(d1, w)
            val b = ball(d2, w)
            return Special.betaI(b.div(a.mul(x, w).add(b, w), w), b.shl(-1), a.shl(-1), prec)
        }

        override fun density(x: Ball, prec: Int): Ball {
            val w = prec + 16
            val a = ball(d1, w)
            val b = ball(d2, w)
            val ha = a.shl(-1)
            val hb = b.shl(-1)
            val lnB = Special.lnGamma(ha, w).add(Special.lnGamma(hb, w), w).sub(Special.lnGamma(ha.add(hb, w), w), w)
            val l = ha.mul(BallMath.ln(a, w), w).add(hb.mul(BallMath.ln(b, w), w), w)
                .add(ha.sub(Ball.ONE, w).mul(BallMath.ln(x, w), w), w)
                .sub(ha.add(hb, w).mul(BallMath.ln(a.mul(x, w).add(b, w), w), w), w).sub(lnB, w)
            return BallMath.exp(l, prec)
        }

        override fun densityD(x: Double): Double =
            if (x <= 0) 0.0 else expD(lnC + (aD / 2 - 1) * lnD(x) - (aD + bD) / 2 * lnD(aD * x + bD))

        override fun densityAtStart(): Expr? = when (compare(d1, Expr.TWO)) {
            -1 -> null
            0 -> Expr.ONE
            else -> Expr.ZERO
        }
    }

    /** The lognormal distribution: ln X is normal with mean μ and standard deviation σ. */
    internal class LogNormal(val mu: Expr, val sigma: Expr) : Continuous() {
        private val muD = valueOf(mu)
        private val sigmaD = valueOf(sigma)
        override val start = 0.0
        override val end = Double.POSITIVE_INFINITY
        override val middle = expD(muD)

        private fun z(x: Ball, w: Int): Ball = BallMath.ln(x, w).sub(ball(mu, w), w).div(ball(sigma, w), w)

        override fun lower(x: Ball, prec: Int): Ball = Normal.lower(z(x, prec + 16), prec)
        override fun upper(x: Ball, prec: Int): Ball = Normal.upper(z(x, prec + 16), prec)

        override fun density(x: Ball, prec: Int): Ball {
            val w = prec + 16
            return Normal.density(z(x, w), w).div(x.mul(ball(sigma, w), w), prec)
        }

        override fun densityD(x: Double): Double {
            if (x <= 0) return 0.0
            val z = (lnD(x) - muD) / sigmaD
            return expD(-z * z / 2) / (x * sigmaD * kotlin.math.sqrt(2 * Math.PI))
        }

        override fun exactLower(x: Expr): Expr? = if (tidy(ln(x) - mu).let { isZero(it) }) Expr.HALF else null
    }

    private fun valueOf(e: Expr): Double = Numeric.value(e) ?: throw CalcException(Failure.UNDEFINED)

    // ---------------------------------------------------------------------------------------------------------------------
    // Density, probability, quantile.

    /**
     * The density of [d] at [x]: exact where [exact] gives a short exact number that the balls confirm, else a certified decimal;
     * checked against the derivative of the distribution function there.
     */
    private fun pdf(d: Continuous, x: Expr, exact: () -> Expr?): Expr {
        // Outside the support the density is 0; at a finite end it has a value of its own, or is infinite (refused).
        if (d.start.isFinite()) {
            val c = compare(x, bound(d.start))
            if (c < 0) return Expr.ZERO
            if (c == 0) return d.densityAtStart() ?: throw CalcException(Failure.UNDEFINED)
        }
        if (d.end.isFinite()) {
            val c = compare(x, bound(d.end))
            if (c > 0) return Expr.ZERO
            if (c == 0) return d.densityAtEnd() ?: throw CalcException(Failure.UNDEFINED)
        }
        val value = certified { prec -> d.density(ball(x, prec + 16), prec) }
        if (!agree(StatNumbers.valueOf(value), slope(d, x))) throw CalcException(Failure.UNVERIFIED)
        val e = try { exact()?.let { tidy(it) } } catch (x: CalcException) { null }
        // An exact form only where it is short enough to read (√2/(2√π), 3√3/(8π)); else the decimal alone.
        if (e != null && (e is Expr.Num || (MathCalc.niceExact(e) && e.size() <= EXACT_SIZE)) &&
            confirms(e) { prec -> d.density(ball(x, prec + 16), prec) }) return e
        return value
    }

    /** The largest exact density shown as its formula. */
    private const val EXACT_SIZE = 24

    /** Whether the exact [e] is the value the balls give, to 2⁻⁸⁰ of it. */
    private fun confirms(e: Expr, compute: (Int) -> Ball): Boolean = try {
        val a = ball(e, 160)
        val b = compute(160)
        val diff = a.sub(b, 160).magUp()
        diff <= b.magUp().shl(-80) || diff.isZero
    } catch (x: CalcException) {
        false
    } catch (x: Imprecise) {
        false
    }

    /** dF/dx at [x] by central differences of the distribution function in balls, extrapolated (Richardson): the density's check. */
    private fun slope(d: Continuous, x: Expr): Double? {
        val xd = double(x)
        var m = maxOf(abs(xd), 1.0)
        if (d.start.isFinite()) m = minOf(m, xd - d.start)
        if (d.end.isFinite()) m = minOf(m, d.end - xd)
        val h = 1e-4 * m
        if (!(h > 0)) return null
        val prec = 192
        val xb = ball(x, prec)
        fun quotient(step: Double): Double {
            val hb = Ball(BigFloat.of(step))
            val up = d.lower(xb.add(hb, prec), prec)
            val down = d.lower(xb.sub(hb, prec), prec)
            return up.sub(down, prec).div(hb.shl(1), prec).mid.toDouble()
        }
        return try {
            val d1 = quotient(h)
            val d2 = quotient(h / 2)
            (4 * d2 - d1) / 3
        } catch (e: Imprecise) {
            null
        }
    }

    /** P(a ≤ X ≤ b) = F(b) − F(a) (negative where a > b, as `normalcdf` has it); a bound may be ±∞. */
    private fun cdf(d: Continuous, a: Expr, b: Expr): Expr {
        val order = compare(a, b)
        if (order == 0) return Expr.ZERO
        if (order > 0) return tidy(neg(cdf(d, b, a)))
        // Clipped to the support: below its start F is 0, beyond its end 1.
        val lo = clip(d, a)
        val hi = clip(d, b)
        if (compare(lo, hi) == 0) return Expr.ZERO
        val exactLo = exactF(d, lo)
        val exactHi = exactF(d, hi)
        if (exactLo != null && exactHi != null) return tidy(exactHi - exactLo)
        val loD = double(lo)
        val hiD = double(hi)
        // Above the middle, the upper tail keeps its digits: Q(a) − Q(b).
        val useUpper = loD >= d.middle
        val value = certified { prec ->
            fun f(e: Expr, exactValue: Expr?): Ball = when {
                exactValue != null -> ball(if (useUpper) tidy(Expr.ONE - exactValue) else exactValue, prec)
                useUpper -> d.upper(ball(e, prec + 16), prec + 8)
                else -> d.lower(ball(e, prec + 16), prec + 8)
            }
            val fa = f(lo, exactLo)
            val fb = f(hi, exactHi)
            if (useUpper) fa.sub(fb, prec) else fb.sub(fa, prec)
        }
        if (!agree(StatNumbers.valueOf(value), integral(d, loD, hiD))) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    /** A bound inside the support: below its start the start, beyond its end the end (or ±∞). */
    private fun clip(d: Continuous, e: Expr): Expr {
        if (d.start.isFinite() && compare(e, bound(d.start)) < 0) return bound(d.start)
        if (d.end.isFinite() && compare(e, bound(d.end)) > 0) return bound(d.end)
        return e
    }

    private fun bound(d: Double): Expr = when {
        d == Double.POSITIVE_INFINITY -> Expr.INF
        d == Double.NEGATIVE_INFINITY -> neg(Expr.INF)
        else -> num(Rational.of(d.toLong()))
    }

    /** F at a bound where it is exact: 0 at the start, 1 at the end, ½ at a symmetric middle. */
    private fun exactF(d: Continuous, e: Expr): Expr? = when {
        infinity(e) < 0 -> Expr.ZERO
        infinity(e) > 0 -> Expr.ONE
        d.start.isFinite() && compare(e, bound(d.start)) <= 0 -> Expr.ZERO
        d.end.isFinite() && compare(e, bound(d.end)) >= 0 -> Expr.ONE
        else -> d.exactLower(e)
    }

    /** ∫ of the density in doubles from [a] to [b] (inside the support), cut at its middle: the probabilities' check. */
    internal fun integral(d: Continuous, a: Double, b: Double): Double? {
        val f = { x: Double -> d.densityD(x) }
        val m = d.middle
        if (a < m && m < b) {
            val left = piece(d, f, a, m) ?: return null
            val right = piece(d, f, m, b) ?: return null
            return left + right
        }
        return piece(d, f, a, b)
    }

    private fun piece(d: Continuous, f: (Double) -> Double, a: Double, b: Double): Double? = when {
        // A heavy tail to ±∞: from beyond ±1 by x = a/t, whose integrand behaves as t^(s − 1) near 0.
        b.isInfinite() && d.tailPower.isFinite() -> if (a >= 1) tail(f, a, d.tailPower) else {
            val inner = piece(d, f, a, 1.0)
            val outer = tail(f, 1.0, d.tailPower)
            if (inner == null || outer == null) null else inner + outer
        }
        a.isInfinite() && d.tailPower.isFinite() -> if (b <= -1) tail(f, b, d.tailPower) else {
            val inner = piece(d, f, -1.0, b)
            val outer = tail(f, -1.0, d.tailPower)
            if (inner == null || outer == null) null else inner + outer
        }
        a == d.start && d.startPower < 1 -> StatNumbers.integrateSingularStart(f, a, b, d.startPower)
        b == d.end && d.end.isFinite() && d.endPower < 1 -> StatNumbers.integrateSingularEnd(f, a, b, d.endPower)
        else -> StatNumbers.integrateTo(f, a, b)
    }

    /** ∫ of f from [a] (|a| ≥ 1) out to ±∞ as a's sign has it: x = a/t, dx = |a|/t² dt, t from 0 to 1. */
    private fun tail(f: (Double) -> Double, a: Double, s: Double): Double? {
        val g = { t: Double -> if (t <= 0.0) 0.0 else f(a / t) * abs(a) / (t * t) }
        return StatNumbers.integrateSingularStart(g, 0.0, 1.0, s)
    }

    /**
     * The quantile of p: the x with P(X ≤ x) = p, certified; checked by the integral up to it. [reflect]: the quantile asked for is
     * 1 − this one (a beta distribution's share near 1 from its mirror image, so its digits near 1 are kept).
     */
    private fun inverse(d: Continuous, p: Rational, reflect: Boolean = false): Expr {
        d.exactQuantile(p)?.let { return if (reflect) tidy(Expr.ONE - it) else it }
        val q = Quantile(d, p)
        val value = certified { prec -> q.enclose(prec).let { if (reflect) Ball.ONE.sub(it, prec) else it } }
        val x = StatNumbers.valueOf(value).let { if (reflect) 1 - it else it }
        val half = Rational.of(1) / Rational.of(2)
        val ok = if (p > half) agree((Rational.ONE - p).toDouble(), integral(d, x, d.end)) else agree(p.toDouble(), integral(d, d.start, x))
        if (!ok) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    /** A beta quantile: near 1 from the mirror image, Beta(β, α) at 1 − p. */
    private fun betaInverse(d: BetaDist, p: Rational): Expr =
        if (p > Rational.of(1) / Rational.of(2)) inverse(BetaDist(d.beta, d.alpha), Rational.ONE - p, reflect = true) else inverse(d, p)

    /**
     * A quantile of [d] at the share [p], enclosed in balls: found in doubles (a bracket, bisection, then Newton's method), then
     * refined at each precision by Newton's steps and enclosed between two points whose tails lie on either side of the share. The
     * upper tail is solved where p > ½, so a share near 1 keeps its digits.
     */
    internal class Quantile(private val d: Continuous, p: Rational) {
        private val upperSide = p > Rational.of(1) / Rational.of(2)
        private val q: Rational = if (upperSide) Rational.ONE - p else p
        private var estimate: BigFloat

        private fun tail(x: Ball, prec: Int): Ball = if (upperSide) d.upper(x, prec) else d.lower(x, prec)

        private fun tailD(x: Double): Double = when {
            x <= d.start -> if (upperSide) 1.0 else 0.0
            x >= d.end -> if (upperSide) 0.0 else 1.0
            else -> tail(StatNumbers.ball(x), 64).mid.toDouble()
        }

        init {
            estimate = BigFloat.of(firstGuess())
        }

        /** g(x) = tail(x) − q rises with x on the lower side and falls on the upper side: its sign says on which side of x the quantile is. */
        private fun below(x: Double, qd: Double): Boolean = if (upperSide) tailD(x) > qd else tailD(x) < qd

        private fun firstGuess(): Double {
            val qd = q.toDouble()
            if (!(qd > 1e-290)) throw CalcException(Failure.TOO_LARGE)
            // A bracket: the quantile is above lo (below(lo)) and not above hi.
            var lo: Double
            var hi: Double
            if (d.start.isInfinite()) {
                lo = d.middle - 1
                hi = d.middle + 1
                var step = 2.0
                while (!below(lo, qd)) { lo -= step; step *= 2; if (!lo.isFinite()) throw CalcException(Failure.TOO_LARGE) }
                step = 2.0
                while (below(hi, qd)) { hi += step; step *= 2; if (!hi.isFinite()) throw CalcException(Failure.TOO_LARGE) }
            } else {
                lo = d.start
                hi = if (d.end.isFinite()) d.end else maxOf(d.middle, 1e-3)
                if (d.end.isInfinite()) while (below(hi, qd)) { hi *= 2; if (!hi.isFinite()) throw CalcException(Failure.TOO_LARGE) }
                // A tiny quantile: halve towards the start until the bracket's low end is below it.
                var k = 0
                while (k < 1100) {
                    val half = d.start + (hi - d.start) / 2
                    if (half == d.start || half == hi) throw CalcException(Failure.TOO_LARGE)
                    if (below(half, qd)) { lo = half; break }
                    hi = half
                    k++
                }
                if (lo == d.start) throw CalcException(Failure.TOO_LARGE)
            }
            // Bisection to a relative 10⁻⁶, then Newton's method within the bracket.
            var k = 0
            while (hi - lo > 1e-6 * maxOf(abs(lo), abs(hi), 1e-300) && k < 400) {
                val mid = 0.5 * (lo + hi)
                if (below(mid, qd)) lo = mid else hi = mid
                k++
                Budget.check()
            }
            var x = 0.5 * (lo + hi)
            for (i in 0 until 60) {
                val g = tailD(x) - qd
                val slope = d.densityD(x) * (if (upperSide) -1 else 1)
                if (slope == 0.0 || !slope.isFinite()) break
                var next = x - g / slope
                if (!(next > lo && next < hi)) next = 0.5 * (lo + hi)
                if (below(next, qd)) lo = maxOf(lo, next) else hi = minOf(hi, next)
                val done = abs(next - x) <= 1e-15 * abs(x)
                x = next
                if (done) break
            }
            return x
        }

        /** The quantile in a ball of relative radius about 2^-(prec − 24), at [prec] bits; [Imprecise] where it does not close. */
        fun enclose(prec: Int): Ball {
            var x = estimate
            val qb = Ball.of(q, prec + 16)
            repeat(3) {
                val xd = x.toDouble()
                val slope = d.densityD(xd) * (if (upperSide) -1 else 1)
                if (slope == 0.0 || !slope.isFinite()) return@repeat
                val g = tail(Ball(x), prec + 16).mid - qb.mid
                val step = g.divide(BigFloat.of(slope), prec + 16).first
                val next = (x - step).round(prec + 32, Round.NEAREST)
                if (d.start.isFinite() && next.toDouble() <= d.start) return@repeat
                if (d.end.isFinite() && next.toDouble() >= d.end) return@repeat
                x = next
            }
            estimate = x
            val size = if (x.isZero) BigFloat.twoTo(-prec.toLong()) else x.abs()
            val h = size.shl(-(prec - 24).toLong()).round(32, Round.CEILING)
            val lo = x - h
            val hi = x + h
            if (d.start.isFinite() && lo.toDouble() <= d.start) throw Imprecise()
            if (d.end.isFinite() && hi.toDouble() >= d.end) throw Imprecise()
            val tLo = tail(Ball(lo), prec + 16)
            val tHi = tail(Ball(hi), prec + 16)
            val bracketed = if (upperSide) tHi.below(qb) && qb.below(tLo) else tLo.below(qb) && qb.below(tHi)
            if (!bracketed) throw Imprecise()
            return Ball.span(lo, hi)
        }
    }

    /** normalcdf(a, b, μ, σ) = Φ((b − μ)/σ) − Φ((a − μ)/σ), certified and checked as every probability here; σ > 0. */
    internal fun normalProbability(a: Expr, b: Expr, mu: Expr, sigma: Expr): Expr {
        positive(sigma)
        fun z(e: Expr): Expr = if (infinity(e) != 0) e else tidy((e - mu) / sigma)
        return cdf(Normal, z(a), z(b))
    }

    /** invnorm(p, μ, σ) = μ + σ·Φ⁻¹(p), certified: 0 < p < 1 and σ > 0, else refused; exactly μ at p = ½. */
    internal fun normalQuantile(p: Expr, mu: Expr, sigma: Expr): Expr {
        val share = share(p)
        finite(mu)
        positive(sigma)
        if (share == Rational.of(1) / Rational.of(2)) return mu
        val z = inverse(Normal, share)
        if (isOne(sigma) && isZero(mu)) return z
        val cache = HashMap<Pair<Continuous, Rational>, Quantile>()
        return certified { prec -> ball(mu, prec + 16).add(ball(sigma, prec + 16).mul(quantileBall(Normal, share, prec + 8, cache), prec + 8), prec) }
    }

    /** A quantile of [d] at [p] as a ball at [prec] bits, for the tests' and intervals' critical values. */
    internal fun quantileBall(d: Continuous, p: Rational, prec: Int, cache: MutableMap<Pair<Continuous, Rational>, Quantile>): Ball {
        d.exactQuantile(p)?.let { return ball(it, prec) }
        val q = cache.getOrPut(d to p) { Quantile(d, p) }
        return q.enclose(prec)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Exact densities, where they are short.

    /** Γ(r) for r a whole number or half an odd one, up to 200: (n − 1)!, or (2n)!·√π/(4ⁿ·n!) at n + ½. */
    private fun gammaExact(e: Expr): Expr? {
        val r = rational(e) ?: return null
        if (r.signum <= 0) return null
        val twice = r * Rational.of(2)
        if (!twice.isInteger || twice.num > BigInteger.valueOf(400)) return null
        val m = twice.num.toInt()
        if (m % 2 == 0) return num(Rational.of(Special.factorial((m / 2 - 1).toLong())))
        val n = (m - 1) / 2
        val c = Rational.of(Special.factorial(2L * n), BigInteger.valueOf(4).pow(n) * Special.factorial(n.toLong()))
        return mul(num(c), sqrt(Expr.PI))
    }

    private fun small(e: Expr) = rational(e)?.let { it.num.bitLength() < 40 && it.den.bitLength() < 40 } == true

    private fun exactT(x: Expr, nu: Expr): Expr? {
        if (!small(x) || !small(nu)) return null
        val top = gammaExact(tidy((nu + Expr.ONE) / Expr.TWO)) ?: return null
        val bottom = gammaExact(tidy(nu / Expr.TWO)) ?: return null
        return mul(top, pow(mul(sqrt(mul(nu, Expr.PI)), bottom), Expr.MINUS_ONE),
            pow(tidy(Expr.ONE + pow(x, Expr.TWO) / nu), tidy(neg((nu + Expr.ONE) / Expr.TWO))))
    }

    private fun exactGamma(x: Expr, k: Expr, theta: Expr): Expr? {
        if (!small(x) || !small(theta)) return null
        val g = gammaExact(k) ?: return null
        return mul(pow(x, tidy(k - Expr.ONE)), exp(tidy(neg(x / theta))), pow(mul(g, pow(theta, k)), Expr.MINUS_ONE))
    }

    private fun exactBeta(x: Expr, a: Expr, b: Expr): Expr? {
        if (!small(x)) return null
        val ga = gammaExact(a) ?: return null
        val gb = gammaExact(b) ?: return null
        val gab = gammaExact(tidy(a + b)) ?: return null
        return mul(pow(x, tidy(a - Expr.ONE)), pow(tidy(Expr.ONE - x), tidy(b - Expr.ONE)), gab, pow(mul(ga, gb), Expr.MINUS_ONE))
    }

    private fun exactF(x: Expr, d1: Expr, d2: Expr): Expr? {
        if (!small(x)) return null
        val ha = tidy(d1 / Expr.TWO)
        val hb = tidy(d2 / Expr.TWO)
        val ga = gammaExact(ha) ?: return null
        val gb = gammaExact(hb) ?: return null
        val gab = gammaExact(tidy(ha + hb)) ?: return null
        val d1x = tidy(d1 * x)
        return mul(pow(d1x, ha), pow(d2, hb), pow(tidy(d1x + d2), tidy(neg(ha + hb))), pow(x, Expr.MINUS_ONE), gab, pow(mul(ga, gb), Expr.MINUS_ONE))
    }

    private fun exactLogNormal(x: Expr, mu: Expr, sigma: Expr): Expr? {
        if (!small(x)) return null
        val z = tidy((ln(x) - mu) / sigma)
        return mul(exp(tidy(neg(pow(z, Expr.TWO) / Expr.TWO))), pow(mul(x, sigma, sqrt(mul(Expr.TWO, Expr.PI))), Expr.MINUS_ONE))
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The exponential and the uniform distribution, exact.

    private fun exponentialPdf(x: Expr, lambda: Expr): Expr {
        if (signOf(x) < 0) return Expr.ZERO
        val value = tidy(mul(lambda, exp(tidy(neg(lambda * x)))))
        // The check: the density is the derivative of 1 − e^(−λx), by differences in balls.
        val check = slope(GammaDist(Expr.ONE, tidy(Expr.ONE / lambda)), x)
        if (signOf(x) > 0 && !agree(StatNumbers.valueOf(value), check)) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    /** P(a ≤ X ≤ b) = e^(−λa) − e^(−λb) for 0 ≤ a ≤ b, exact; checked by the density's integral. */
    private fun exponentialCdf(a: Expr, b: Expr, lambda: Expr): Expr {
        val order = compare(a, b)
        if (order == 0) return Expr.ZERO
        if (order > 0) return tidy(neg(exponentialCdf(b, a, lambda)))
        fun survival(e: Expr): Expr = when {
            infinity(e) > 0 -> Expr.ZERO
            signOf(e) <= 0 -> Expr.ONE
            else -> exp(tidy(neg(lambda * e)))
        }
        val value = tidy(survival(a) - survival(b))
        val d = GammaDist(Expr.ONE, tidy(Expr.ONE / lambda))
        val lo = maxOf(double(a), 0.0)
        val hi = double(b)
        if (hi > 0 && !agree(StatNumbers.valueOf(value), integral(d, lo, hi))) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    /** x = ln(1/(1 − p))/λ, put back: 1 − e^(−λx) = p. */
    private fun exponentialInverse(p: Rational, lambda: Expr): Expr {
        val value = tidy(ln(num(Rational.ONE / (Rational.ONE - p))) / lambda)
        val back = tidy(Expr.ONE - exp(tidy(neg(lambda * value))))
        if (!(back == num(p) || confirms(num(p)) { prec -> ball(back, prec) })) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    private fun uniformPdf(x: Expr, a: Expr, b: Expr): Expr =
        if (compare(x, a) < 0 || compare(x, b) > 0) Expr.ZERO else tidy(Expr.ONE / (b - a))

    /** The share of [a, b] that [c, d] covers: (min(d, b) − max(c, a))/(b − a), checked against F(d) − F(c). */
    private fun uniformCdf(c: Expr, d: Expr, a: Expr, b: Expr): Expr {
        val order = compare(c, d)
        if (order == 0) return Expr.ZERO
        if (order > 0) return tidy(neg(uniformCdf(d, c, a, b)))
        val from = if (compare(c, a) < 0) a else if (compare(c, b) > 0) b else c
        val to = if (compare(d, b) > 0) b else if (compare(d, a) < 0) a else d
        val value = tidy((to - from) / (b - a))
        fun f(x: Expr): Expr = when {
            compare(x, a) <= 0 -> Expr.ZERO
            compare(x, b) >= 0 -> Expr.ONE
            else -> tidy((x - a) / (b - a))
        }
        if (!LinAlg.isNull(tidy(value - (f(d) - f(c))))) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    private fun uniformInverse(p: Rational, a: Expr, b: Expr): Expr {
        val value = tidy(a + num(p) * (b - a))
        if (!LinAlg.isNull(tidy((value - a) / (b - a) - num(p)))) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The hypergeometric distribution, exact.

    /**
     * P(X = k), or P(X ≤ k) where [cumulative], for k successes in n draws without replacement from N things of which K are
     * successes: C(K, k)·C(N − K, n − k)/C(N, n). Checked: the terms by their recurrence from the lowest k, and their sum 1.
     */
    private fun hypergeometric(nTotal: Expr, kTotal: Expr, nDraws: Expr, kWanted: Expr, cumulative: Boolean): Expr {
        fun whole(e: Expr): BigInteger = integer(e) ?: throw CalcException(Failure.UNDEFINED)
        val total = whole(nTotal)
        val successes = whole(kTotal)
        val draws = whole(nDraws)
        val k = whole(kWanted)
        if (total.signum() < 0 || successes.signum() < 0 || draws.signum() < 0 || successes > total || draws > total) throw CalcException(Failure.UNDEFINED)
        if (total > BigInteger.valueOf(1_000_000)) throw CalcException(Failure.TOO_LARGE)
        val low = (draws + successes - total).max(BigInteger.ZERO)
        val high = draws.min(successes)
        val all = NumberTheory.binomial(total, draws)
        fun direct(j: BigInteger): Rational =
            Rational.of(NumberTheory.binomial(successes, j) * NumberTheory.binomial(total - successes, draws - j), all)
        if (!cumulative) {
            if (k < low || k > high) return Expr.ZERO
            val value = direct(k)
            // The check: from P(low) by P(j + 1)/P(j) = (K − j)(n − j)/((j + 1)(N − K − n + j + 1)).
            if (recurrence(low, k, successes, draws, total, direct(low)) != value) throw CalcException(Failure.UNVERIFIED)
            // And the first term itself: the terms of the whole support add up to 1.
            if (high - low <= BigInteger.valueOf(5_000) && sumByRecurrence(low, high, successes, draws, total, direct(low)) != Rational.ONE)
                throw CalcException(Failure.UNVERIFIED)
            return num(value)
        }
        if (k < low) return Expr.ZERO
        if (k >= high) {
            // Every term: their sum is 1, checked by the recurrence too.
            if (sumByRecurrence(low, high, successes, draws, total, direct(low)) != Rational.ONE) throw CalcException(Failure.UNVERIFIED)
            return Expr.ONE
        }
        if (k - low > BigInteger.valueOf(100_000)) throw CalcException(Failure.TOO_LARGE)
        var sum = Rational.ZERO
        var j = low
        while (j <= k) {
            sum += direct(j)
            j += BigInteger.ONE
            Budget.check()
        }
        if (sumByRecurrence(low, k, successes, draws, total, direct(low)) != sum) throw CalcException(Failure.UNVERIFIED)
        return num(sum)
    }

    private fun ratio(j: BigInteger, successes: BigInteger, draws: BigInteger, total: BigInteger): Rational =
        Rational.of((successes - j) * (draws - j), (j + BigInteger.ONE) * (total - successes - draws + j + BigInteger.ONE))

    private fun recurrence(from: BigInteger, to: BigInteger, successes: BigInteger, draws: BigInteger, total: BigInteger, start: Rational): Rational {
        var p = start
        var j = from
        while (j < to) {
            p *= ratio(j, successes, draws, total)
            j += BigInteger.ONE
            Budget.check()
        }
        return p
    }

    private fun sumByRecurrence(from: BigInteger, to: BigInteger, successes: BigInteger, draws: BigInteger, total: BigInteger, start: Rational): Rational {
        var p = start
        var sum = start
        var j = from
        while (j < to) {
            p *= ratio(j, successes, draws, total)
            sum += p
            j += BigInteger.ONE
            Budget.check()
        }
        return sum
    }
}
