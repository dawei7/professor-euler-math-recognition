package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Statistics of a list of numbers (exact where its entries are), a least-squares line through points with r and R², and the
 * distributions a course meets as a calculator names them: binompdf, binomcdf, poissonpdf, poissoncdf, geometpdf, normalpdf,
 * normalcdf, invnorm (exact where they can be: binomial probabilities with a rational p are fractions).
 */
object Stats {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** A data list's summary: its count, sum, mean, median, modes, both variances and standard deviations, quartiles and range. */
    class Summary(
        val n: Int, val sum: Expr, val mean: Expr, val median: Expr, val modes: List<Expr>, val populationVariance: Expr,
        val sampleVariance: Expr?, val populationSd: Expr, val sampleSd: Expr?, val min: Expr, val max: Expr, val q1: Expr, val q3: Expr,
    )

    fun summary(values: List<Expr>): Summary? {
        if (values.size < 2) return null
        val numbers = values.map { Numeric.value(it) ?: return null }
        val sorted = values.zip(numbers).sortedBy { it.second }.map { it.first }
        val n = values.size
        val sum = tidy(add(values))
        val mean = tidy(sum / num(n.toLong()))
        fun medianOf(list: List<Expr>): Expr = if (list.size % 2 == 1) list[list.size / 2] else tidy((list[list.size / 2 - 1] + list[list.size / 2]) / Expr.TWO)
        val median = medianOf(sorted)
        // The quartiles as the medians of the lower and upper halves (the median left out where n is odd).
        val lower = sorted.subList(0, n / 2)
        val upper = sorted.subList((n + 1) / 2, n)
        val q1 = if (lower.isEmpty()) sorted.first() else medianOf(lower)
        val q3 = if (upper.isEmpty()) sorted.last() else medianOf(upper)
        val squares = tidy(add(values.map { pow(tidy(it - mean), Expr.TWO) }))
        val population = tidy(squares / num(n.toLong()))
        val sample = if (n > 1) tidy(squares / num((n - 1).toLong())) else null
        val counts = values.groupingBy { it }.eachCount()
        val top = counts.values.maxOrNull() ?: 1
        val modes = if (top > 1) counts.filterValues { it == top }.keys.sortedBy { Numeric.value(it) } else emptyList()
        return Summary(n, sum, mean, median, modes, population, sample, tidy(sqrt(population)), sample?.let { tidy(sqrt(it)) },
            sorted.first(), sorted.last(), q1, q3)
    }

    /**
     * y = a·x + b through the points by least squares, with Pearson's r; exact for exact points. [r] is null where the y are
     * all the same: the line is level (a = 0) and r, a quotient by their spread, is not defined.
     */
    class Line(val slope: Expr, val intercept: Expr, val r: Expr?)

    fun regression(xs: List<Expr>, ys: List<Expr>): Line? {
        val n = xs.size
        if (n < 2 || ys.size != n) return null
        val nn = num(n.toLong())
        val sx = tidy(add(xs))
        val sy = tidy(add(ys))
        val sxx = tidy(add(xs.map { pow(it, Expr.TWO) }))
        val syy = tidy(add(ys.map { pow(it, Expr.TWO) }))
        val sxy = tidy(add(xs.indices.map { mul(xs[it], ys[it]) }))
        val denominator = tidy(nn * sxx - sx * sx)
        // All x the same: the points lie on a vertical line x = c, which is no y = a·x + b.
        if (LinAlg.isNull(denominator)) return null
        val a = tidy((nn * sxy - sx * sy) / denominator)
        val b = tidy((sy - a * sx) / nn)
        val rDen = tidy(sqrt(tidy(denominator * (nn * syy - sy * sy))))
        val r = if (LinAlg.isNull(rDen)) null else tidy((nn * sxy - sx * sy) / rDen)
        return Line(a, b, r)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Distributions.

    /** The distribution functions by their calculator names, and how many arguments each takes. */
    val FUNCTIONS = mapOf("binompdf" to 3, "binomcdf" to 3, "poissonpdf" to 2, "poissoncdf" to 2, "geometpdf" to 2,
        "normalpdf" to 3, "normalcdf" to 4, "invnorm" to 3)

    /**
     * One of [FUNCTIONS] at its arguments (μ = 0, σ = 1 may be left out of the normal ones); null (undefined) where a parameter
     * lies outside its range: n and k whole, 0 ≤ p ≤ 1 (0 < p for the geometric distribution), λ ≥ 0, σ > 0, 0 < p < 1 for
     * invnorm, and the lower bound of normalcdf not above the upper (P(a ≤ X ≤ b) is of an interval; with them swapped it would
     * come out negative). In the probabilities 0⁰ is 1: binompdf(3, 0, 0) is (1 − 0)³ = 1.
     */
    fun distribution(name: String, args: List<Expr>): Expr? {
        fun whole(e: Expr) = integer(e)?.takeIf { it.signum() >= 0 && it.bitLength() < 20 }?.toInt()
        // A parameter that is a number must lie in its range; one with letters in it stays as it is written.
        fun inRange(e: Expr, ok: (Double) -> Boolean): Boolean = Numeric.value(e)?.let(ok) ?: e.symbols().isNotEmpty()
        // bᵏ with b⁰ = 1, also for b = 0.
        fun power(b: Expr, k: Int): Expr = if (k == 0) Expr.ONE else pow(b, num(k.toLong()))
        return when (name) {
            "binompdf", "binomcdf" -> {
                if (args.size != 3) return null
                val n = whole(args[0]) ?: return null
                val p = args[1]
                val k = whole(args[2]) ?: return null
                val pv = Numeric.value(p) ?: return null
                if (pv < 0 || pv > 1 || k > n) return null
                val range = if (name == "binompdf") k..k else 0..k
                tidy(add(range.map { j ->
                    mul(num(Rational.of(NumberTheory.binomial(java.math.BigInteger.valueOf(n.toLong()), java.math.BigInteger.valueOf(j.toLong())))),
                        power(p, j), power(tidy(Expr.ONE - p), n - j))
                }))
            }
            "poissonpdf", "poissoncdf" -> {
                if (args.size != 2) return null
                val lambda = args[0]
                if (!inRange(lambda) { it >= 0 }) return null
                val k = whole(args[1]) ?: return null
                val range = if (name == "poissonpdf") k..k else 0..k
                tidy(add(range.map { j -> mul(power(lambda, j), exp(neg(lambda)), pow(fn(Func.FACTORIAL, num(j.toLong())), Expr.MINUS_ONE)) }))
            }
            "geometpdf" -> {
                if (args.size != 2) return null
                val k = whole(args[1])?.takeIf { it >= 1 } ?: return null
                if (!inRange(args[0]) { it > 0 && it <= 1 }) return null
                tidy(mul(power(tidy(Expr.ONE - args[0]), k - 1), args[0]))
            }
            "normalpdf" -> {
                if (args.isEmpty() || args.size > 3) return null
                val mu = args.getOrElse(1) { Expr.ZERO }
                val sigma = args.getOrElse(2) { Expr.ONE }
                if (!inRange(sigma) { it > 0 }) return null
                val z = tidy((args[0] - mu) / sigma)
                tidy(mul(pow(mul(sigma, sqrt(mul(Expr.TWO, Expr.PI))), Expr.MINUS_ONE), exp(neg(pow(z, Expr.TWO) / Expr.TWO))))
            }
            "normalcdf" -> {
                if (args.size < 2) return null
                val mu = args.getOrElse(2) { Expr.ZERO }
                val sigma = args.getOrElse(3) { Expr.ONE }
                if (!inRange(sigma) { it > 0 }) return null
                fun bound(x: Expr): Double? = when (x) { Expr.INF -> Double.POSITIVE_INFINITY; neg(Expr.INF) -> Double.NEGATIVE_INFINITY; else -> Numeric.value(x) }
                val lower = bound(args[0])
                val upper = bound(args[1])
                if (lower != null && upper != null && lower > upper) return null
                fun phi(x: Expr): Expr = if (x == Expr.INF) Expr.ONE else if (x == neg(Expr.INF)) Expr.ZERO else
                    tidy(mul(Expr.HALF, add(Expr.ONE, fn(Func.ERF, tidy((x - mu) / mul(sigma, sqrt(Expr.TWO)))))))
                tidy(phi(args[1]) - phi(args[0]))
            }
            "invnorm" -> {
                if (args.isEmpty()) return null
                val p = Numeric.value(args[0])?.takeIf { it > 0 && it < 1 } ?: return null
                val mu = Numeric.value(args.getOrElse(1) { Expr.ZERO }) ?: return null
                val sigma = Numeric.value(args.getOrElse(2) { Expr.ONE })?.takeIf { it > 0 } ?: return null
                // The median exactly: Φ⁻¹(1/2) = 0.
                if (args[0] == Expr.HALF) return tidy(args.getOrElse(1) { Expr.ZERO })
                // Φ⁻¹ by bisection on erf, to 15 digits.
                var lo = -40.0
                var hi = 40.0
                repeat(200) {
                    val mid = (lo + hi) / 2
                    val v = 0.5 * (1 + Numeric.function(Func.ERF, listOf(mid / sqrt(2.0))))
                    if (v < p) lo = mid else hi = mid
                }
                Expr.Real(mu + sigma * (lo + hi) / 2)
            }
            else -> null
        }
    }

    @Suppress("unused") private fun close(a: Double, b: Double) = abs(a - b) < 1e-12
}
