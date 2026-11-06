package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.Distributions.Continuous
import com.professoreuler.mathcalc.StatCommands.Arg
import com.professoreuler.mathcalc.StatNumbers.agree
import com.professoreuler.mathcalc.StatNumbers.ball
import com.professoreuler.mathcalc.StatNumbers.certified
import com.professoreuler.mathcalc.StatNumbers.signOf

/**
 * Hypothesis tests and confidence intervals on data typed as numbers: a list in brackets, or the summary a textbook gives
 * (x̄, s, n). A test gives its statistic (exact where the data are), its degrees of freedom, the p-value (two-sided unless the last
 * argument is `less` or `greater`, the alternative μ < μ₀ or μ > μ₀), the critical value and the decision at the level α (0.05
 * unless an argument gives another): H₀ is rejected where p < α. An interval gives its critical value, margin and ends at the
 * confidence level (0.95 unless given).
 *
 * Each result is checked by a second computation: a p-value against the statistic's density integrated over its tail
 * ([Distributions.integral]), the decision against the critical value (p < α exactly where the statistic lies beyond it), a χ²
 * statistic against Σo²/e − n, Welch's degrees of freedom against their second form, an interval's ends put back (the statistic at
 * an end is the critical value, whose tail is the level's), Wilson's ends put back into the equation they solve.
 */
internal object Inference {
    private fun tidy(e: Expr) = Calculus.tidy(e)
    private val HALF = Rational.of(1) / Rational.of(2)
    private val DEFAULT_ALPHA = Rational.of(1) / Rational.of(20)
    private val DEFAULT_LEVEL = Rational.of(19) / Rational.of(20)

    fun of(mode: Mode, args: List<Arg>): StatResult = when (mode) {
        Mode.Z_TEST -> zTest(args)
        Mode.T_TEST -> tTest(args)
        Mode.WELCH_TEST -> welchTest(args)
        Mode.CHI_SQUARE_FIT -> goodnessOfFit(args)
        Mode.CHI_SQUARE_INDEPENDENCE -> independence(args)
        Mode.Z_INTERVAL -> zInterval(args)
        Mode.T_INTERVAL -> tInterval(args)
        Mode.PROPORTION_INTERVAL -> proportionInterval(args)
        else -> throw CalcException(Failure.UNREADABLE)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Reading the arguments.

    private fun value(a: Arg): Expr = (a as? Arg.Value)?.e ?: throw CalcException(Failure.ARGUMENTS)

    private fun number(a: Arg): Expr {
        val e = value(a)
        if (e is Expr.Mat || e.hasI() || StatNumbers.infinity(e) != 0 || Numeric.value(e) == null) throw CalcException(Failure.ARGUMENTS)
        return e
    }

    private fun isList(a: Arg) = a is Arg.Value && a.e is Expr.Mat

    private fun list(a: Arg): List<Expr> {
        val values = Describe.entries(value(a)) ?: throw CalcException(Failure.ARGUMENTS)
        for (v in values) if (v is Expr.Mat || v.hasI() || Numeric.value(v) == null) throw CalcException(Failure.ARGUMENTS)
        return values
    }

    private fun positive(e: Expr): Expr {
        if (signOf(e) <= 0) throw CalcException(Failure.UNDEFINED)
        return e
    }

    private fun count(e: Expr, least: Int): Int {
        val n = integer(e) ?: throw CalcException(Failure.UNDEFINED)
        if (n < java.math.BigInteger.valueOf(least.toLong()) || n.bitLength() > 30) throw CalcException(Failure.UNDEFINED)
        return n.toInt()
    }

    /** A share strictly between 0 and 1 (α, a level), exactly. */
    private fun share(e: Expr): Rational {
        val r = rational(e) ?: throw CalcException(Failure.UNDEFINED)
        if (r.signum <= 0 || r >= Rational.ONE) throw CalcException(Failure.UNDEFINED)
        return r
    }

    /** After the arguments a test needs: α, then `less` or `greater`, each where it is given, in this order. */
    private class Options(val alpha: Rational, val alternative: Alternative)

    private fun options(rest: List<Arg>, alternatives: Boolean): Options {
        var i = 0
        var alpha = DEFAULT_ALPHA
        if (i < rest.size && rest[i] is Arg.Value) alpha = share(number(rest[i++]))
        var alternative = Alternative.TWO_SIDED
        if (alternatives && i < rest.size && rest[i] is Arg.Word) {
            alternative = if ((rest[i++] as Arg.Word).word == "less") Alternative.LESS else Alternative.GREATER
        }
        if (i != rest.size) throw CalcException(Failure.ARGUMENTS)
        return Options(alpha, alternative)
    }

    private fun level(rest: List<Arg>): Rational {
        if (rest.size > 1) throw CalcException(Failure.ARGUMENTS)
        return if (rest.isEmpty()) DEFAULT_LEVEL else share(number(rest[0]))
    }

    /** A sample: its size, mean and (sample) standard deviation, from its values or as a textbook gives them. */
    private class Sample(val n: Int, val mean: Expr, val sd: Expr?)

    private fun sampleOf(values: List<Expr>, needSd: Boolean): Sample {
        if (values.isEmpty() || (needSd && values.size < 2)) throw CalcException(Failure.ARGUMENTS)
        if (values.size == 1) return Sample(1, values[0], null)
        val s = Stats.summary(values) ?: throw CalcException(Failure.UNDEFINED)
        return Sample(s.n, s.mean, s.sampleSd)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Probabilities of a statistic.

    /** The p-value of [statistic] under [d] for the alternative: the tail beyond it (both tails, twice the one beyond |t|). */
    private fun pBall(d: Continuous, statistic: Expr, alternative: Alternative, prec: Int): Ball {
        val t = ball(statistic, prec + 16)
        return when (alternative) {
            Alternative.GREATER -> d.upper(t, prec)
            Alternative.LESS -> d.lower(t, prec)
            Alternative.TWO_SIDED -> {
                val a = if (t.isNegative) -t else if (t.isPositive || t.isExactZero) t else throw Imprecise()
                if (a.isExactZero) Ball.ONE else d.upper(a, prec + 1).shl(1).rounded(prec)
            }
        }
    }

    /** The same by the density's integral, in doubles: the check. */
    private fun pCheck(d: Continuous, statistic: Double, alternative: Alternative): Double? = when (alternative) {
        Alternative.GREATER -> Distributions.integral(d, statistic, d.end)
        Alternative.LESS -> Distributions.integral(d, d.start, statistic)
        Alternative.TWO_SIDED -> if (statistic == 0.0) 1.0 else Distributions.integral(d, kotlin.math.abs(statistic), d.end)?.let { 2 * it }
    }

    /**
     * A test's p-value, critical value and decision, each checked: the p-value by the integral, the decision both ways (p < α
     * and the statistic beyond the critical value). [symmetric]: the critical value of a two-sided test is ±c (z, t).
     */
    private class Decision(val p: Expr, val critical: Expr, val reject: Boolean?)

    private fun decide(d: Continuous, statistic: Expr, alternative: Alternative, alpha: Rational): Decision {
        val p = certified { prec -> pBall(d, statistic, alternative, prec) }
        val tD = StatNumbers.valueOf(statistic)
        val pD = StatNumbers.valueOf(p)
        if (!(p is Expr.Num && p == Expr.ONE) && !agree(pD, pCheck(d, tD, alternative))) throw CalcException(Failure.UNVERIFIED)
        // The critical value: the quantile beyond which the share α lies (α/2 on each side of a two-sided test).
        val upperShare = if (alternative == Alternative.TWO_SIDED) Rational.ONE - alpha / Rational.of(2) else Rational.ONE - alpha
        val cache = HashMap<Pair<Continuous, Rational>, Distributions.Quantile>()
        val critical = certified { prec -> Distributions.quantileBall(d, upperShare, prec, cache) }
        val cD = StatNumbers.valueOf(critical)
        if (!agree((Rational.ONE - upperShare).toDouble(), Distributions.integral(d, cD, d.end))) throw CalcException(Failure.UNVERIFIED)
        val byP = StatNumbers.against(alpha) { prec -> pBall(d, statistic, alternative, prec) }
        // Beyond the critical value: |t| > c, t > c, or t < −c.
        val beyond = StatNumbers.against(Rational.ZERO) { prec ->
            val t = ball(statistic, prec + 16)
            val c = Distributions.quantileBall(d, upperShare, prec, cache)
            when (alternative) {
                Alternative.GREATER -> t.sub(c, prec)
                Alternative.LESS -> (-t).sub(c, prec)
                Alternative.TWO_SIDED -> (if (t.isNegative) -t else t).sub(c, prec)
            }
        }
        val reject = when {
            byP == null || beyond == null -> null
            (byP < 0) != (beyond > 0) -> throw CalcException(Failure.UNVERIFIED)
            else -> byP < 0
        }
        val shownCritical = if (alternative == Alternative.LESS) tidy(neg(critical)) else critical
        return Decision(p, shownCritical, reject)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The tests.

    /** ztest(data, μ₀, σ) or ztest(x̄, n, μ₀, σ), then α and the alternative: z = (x̄ − μ₀)/(σ/√n), σ known. */
    private fun zTest(args: List<Arg>): StatResult {
        if (args.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val sample: Sample
        val rest: List<Arg>
        val mu0: Expr
        val sigma: Expr
        if (isList(args[0])) {
            if (args.size < 3) throw CalcException(Failure.ARGUMENTS)
            sample = sampleOf(list(args[0]), needSd = false)
            mu0 = number(args[1])
            sigma = positive(number(args[2]))
            rest = args.drop(3)
        } else {
            if (args.size < 4) throw CalcException(Failure.ARGUMENTS)
            sample = Sample(count(number(args[1]), 1), number(args[0]), null)
            mu0 = number(args[2])
            sigma = positive(number(args[3]))
            rest = args.drop(4)
        }
        val o = options(rest, alternatives = true)
        val se = tidy(sigma / sqrt(num(sample.n.toLong())))
        val z = tidy((sample.mean - mu0) / se)
        val d = decide(Distributions.Normal, z, o.alternative, o.alpha)
        return StatResult(Mode.Z_TEST, listOf(
            StatRow(Quantity.N, num(sample.n.toLong())), StatRow(Quantity.MEAN, sample.mean), StatRow(Quantity.SIGMA, sigma),
            StatRow(Quantity.STANDARD_ERROR, se), StatRow(Quantity.STATISTIC, z), StatRow(Quantity.P_VALUE, d.p),
            StatRow(Quantity.ALPHA, num(o.alpha)), StatRow(Quantity.CRITICAL, d.critical),
        ), o.alternative, mu0, d.reject)
    }

    /** ttest(data, μ₀) or ttest(x̄, s, n, μ₀), then α and the alternative: t = (x̄ − μ₀)/(s/√n), n − 1 degrees of freedom. */
    private fun tTest(args: List<Arg>): StatResult {
        if (args.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val sample: Sample
        val rest: List<Arg>
        val mu0: Expr
        if (isList(args[0])) {
            if (args.size < 2) throw CalcException(Failure.ARGUMENTS)
            sample = sampleOf(list(args[0]), needSd = true)
            mu0 = number(args[1])
            rest = args.drop(2)
        } else {
            if (args.size < 4) throw CalcException(Failure.ARGUMENTS)
            sample = Sample(count(number(args[2]), 2), number(args[0]), positive(number(args[1])))
            mu0 = number(args[3])
            rest = args.drop(4)
        }
        val o = options(rest, alternatives = true)
        val s = sample.sd ?: throw CalcException(Failure.UNDEFINED)
        if (signOf(s) <= 0) throw CalcException(Failure.UNDEFINED)
        val se = tidy(s / sqrt(num(sample.n.toLong())))
        val t = tidy((sample.mean - mu0) / se)
        val df = num((sample.n - 1).toLong())
        val d = decide(Distributions.StudentT(df), t, o.alternative, o.alpha)
        return StatResult(Mode.T_TEST, listOf(
            StatRow(Quantity.N, num(sample.n.toLong())), StatRow(Quantity.MEAN, sample.mean), StatRow(Quantity.SD, s),
            StatRow(Quantity.STANDARD_ERROR, se), StatRow(Quantity.STATISTIC, t), StatRow(Quantity.DF, df), StatRow(Quantity.P_VALUE, d.p),
            StatRow(Quantity.ALPHA, num(o.alpha)), StatRow(Quantity.CRITICAL, d.critical),
        ), o.alternative, mu0, d.reject)
    }

    /**
     * welchtest(data₁, data₂) or welchtest(x̄₁, s₁, n₁, x̄₂, s₂, n₂), then α and the alternative (μ₁ − μ₂ < 0 or > 0): Welch's t
     * = (x̄₁ − x̄₂)/√(s₁²/n₁ + s₂²/n₂), with the Welch–Satterthwaite degrees of freedom; the variances need not be equal.
     */
    private fun welchTest(args: List<Arg>): StatResult {
        if (args.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val a: Sample
        val b: Sample
        val rest: List<Arg>
        if (isList(args[0])) {
            if (args.size < 2 || !isList(args[1])) throw CalcException(Failure.ARGUMENTS)
            a = sampleOf(list(args[0]), needSd = true)
            b = sampleOf(list(args[1]), needSd = true)
            rest = args.drop(2)
        } else {
            if (args.size < 6) throw CalcException(Failure.ARGUMENTS)
            a = Sample(count(number(args[2]), 2), number(args[0]), nonNegative(number(args[1])))
            b = Sample(count(number(args[5]), 2), number(args[3]), nonNegative(number(args[4])))
            rest = args.drop(6)
        }
        val o = options(rest, alternatives = true)
        val v1 = tidy(pow(a.sd!!, Expr.TWO) / num(a.n.toLong()))
        val v2 = tidy(pow(b.sd!!, Expr.TWO) / num(b.n.toLong()))
        val variance = tidy(v1 + v2)
        if (signOf(variance) <= 0) throw CalcException(Failure.UNDEFINED)
        val se = tidy(sqrt(variance))
        val t = tidy((a.mean - b.mean) / se)
        val n1 = num((a.n - 1).toLong())
        val n2 = num((b.n - 1).toLong())
        val df = tidy(pow(variance, Expr.TWO) / (pow(v1, Expr.TWO) / n1 + pow(v2, Expr.TWO) / n2))
        // The check: 1/df = c²/(n₁ − 1) + (1 − c)²/(n₂ − 1) with c = v₁/(v₁ + v₂).
        val c = tidy(v1 / variance)
        val again = tidy(Expr.ONE / (pow(c, Expr.TWO) / n1 + pow(tidy(Expr.ONE - c), Expr.TWO) / n2))
        if (!LinAlg.isNull(tidy(df - again)) && !agree(StatNumbers.valueOf(df), Numeric.value(again), 1e-12)) throw CalcException(Failure.UNVERIFIED)
        val d = decide(Distributions.StudentT(df), t, o.alternative, o.alpha)
        return StatResult(Mode.WELCH_TEST, listOf(
            StatRow(Quantity.N, num(a.n.toLong()), 1), StatRow(Quantity.MEAN, a.mean, 1), StatRow(Quantity.SD, a.sd, 1),
            StatRow(Quantity.N, num(b.n.toLong()), 2), StatRow(Quantity.MEAN, b.mean, 2), StatRow(Quantity.SD, b.sd, 2),
            StatRow(Quantity.STANDARD_ERROR, se), StatRow(Quantity.STATISTIC, t), StatRow(Quantity.DF, df), StatRow(Quantity.P_VALUE, d.p),
            StatRow(Quantity.ALPHA, num(o.alpha)), StatRow(Quantity.CRITICAL, d.critical),
        ), o.alternative, Expr.ZERO, d.reject)
    }

    private fun nonNegative(e: Expr): Expr {
        if (signOf(e) < 0) throw CalcException(Failure.UNDEFINED)
        return e
    }

    private fun counts(values: List<Expr>): List<Expr> {
        for (v in values) {
            val n = integer(v) ?: throw CalcException(Failure.UNDEFINED)
            if (n.signum() < 0) throw CalcException(Failure.UNDEFINED)
        }
        return values
    }

    /** χ² = Σ(o − e)²/e, checked against Σo²/e − n (which it equals where Σe = Σo = n). */
    private fun chiSquare(observed: List<Expr>, expected: List<Expr>, n: Expr): Expr {
        val value = tidy(add(observed.indices.map { pow(tidy(observed[it] - expected[it]), Expr.TWO) / expected[it] }))
        val again = tidy(add(observed.indices.map { pow(observed[it], Expr.TWO) / expected[it] }) - n)
        if (!LinAlg.isNull(tidy(value - again))) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    /**
     * chisqgof(observed, expected), then α: Pearson's χ² test of goodness of fit with k − 1 degrees of freedom. The expected values
     * are counts that add up to the observed total, or probabilities that add up to 1 (then times the total).
     */
    private fun goodnessOfFit(args: List<Arg>): StatResult {
        if (args.size < 2 || !isList(args[0]) || !isList(args[1])) throw CalcException(Failure.ARGUMENTS)
        val observed = counts(list(args[0]))
        val given = list(args[1])
        if (observed.size != given.size) throw CalcException(Failure.SIZES)
        if (observed.size < 2) throw CalcException(Failure.ARGUMENTS)
        val o = options(args.drop(2), alternatives = false)
        for (e in given) positive(e)
        val n = tidy(add(observed))
        if (signOf(n) <= 0) throw CalcException(Failure.UNDEFINED)
        val total = tidy(add(given))
        val expected = when {
            LinAlg.isNull(tidy(total - n)) -> given
            LinAlg.isNull(tidy(total - Expr.ONE)) -> given.map { tidy(it * n) }
            else -> throw CalcException(Failure.UNDEFINED)
        }
        val chi = chiSquare(observed, expected, n)
        val df = num((observed.size - 1).toLong())
        val d = decide(Distributions.chiSquare(df), chi, Alternative.GREATER, o.alpha)
        val warnings = if (expected.any { StatNumbers.compare(it, num(5)) < 0 }) listOf(StatWarning.SMALL_EXPECTED) else emptyList()
        return StatResult(Mode.CHI_SQUARE_FIT, listOf(
            StatRow(Quantity.N, n), StatRow(Quantity.CATEGORIES, num(observed.size.toLong())), StatRow(Quantity.STATISTIC, chi),
            StatRow(Quantity.DF, df), StatRow(Quantity.P_VALUE, d.p), StatRow(Quantity.ALPHA, num(o.alpha)), StatRow(Quantity.CRITICAL, d.critical),
        ), null, null, d.reject, expected = LinAlg.column(expected), warnings = warnings)
    }

    /** chisqindep(table), then α: Pearson's χ² test of independence of a contingency table, (r − 1)(c − 1) degrees of freedom. */
    private fun independence(args: List<Arg>): StatResult {
        if (args.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val table = value(args[0]) as? Expr.Mat ?: throw CalcException(Failure.ARGUMENTS)
        if (table.size < 2 || table.columns < 2) throw CalcException(Failure.SIZES)
        val o = options(args.drop(1), alternatives = false)
        for (row in table.rows) counts(row)
        val rowSums = table.rows.map { tidy(add(it)) }
        val columnSums = (0 until table.columns).map { j -> tidy(add(table.rows.map { it[j] })) }
        if ((rowSums + columnSums).any { signOf(it) <= 0 }) throw CalcException(Failure.UNDEFINED)
        val n = tidy(add(rowSums))
        val expected = table.rows.indices.map { i -> columnSums.map { c -> tidy(rowSums[i] * c / n) } }
        val chi = chiSquare(table.rows.flatten(), expected.flatten(), n)
        val df = num(((table.size - 1) * (table.columns - 1)).toLong())
        val d = decide(Distributions.chiSquare(df), chi, Alternative.GREATER, o.alpha)
        val warnings = if (expected.flatten().any { StatNumbers.compare(it, num(5)) < 0 }) listOf(StatWarning.SMALL_EXPECTED) else emptyList()
        return StatResult(Mode.CHI_SQUARE_INDEPENDENCE, listOf(
            StatRow(Quantity.N, n), StatRow(Quantity.ROWS, num(table.size.toLong())), StatRow(Quantity.COLUMNS, num(table.columns.toLong())),
            StatRow(Quantity.STATISTIC, chi), StatRow(Quantity.DF, df), StatRow(Quantity.P_VALUE, d.p), StatRow(Quantity.ALPHA, num(o.alpha)),
            StatRow(Quantity.CRITICAL, d.critical),
        ), null, null, d.reject, expected = LinAlg.matrix(expected), warnings = warnings)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The intervals.

    /**
     * centre ± c·se at the level, with c the quantile of (1 + level)/2 of [d]; checked: at each end the statistic (end − centre)/se
     * is ±c, and the tail beyond c is (1 − level)/2.
     */
    private class Ends(val critical: Expr, val margin: Expr, val lower: Expr, val upper: Expr)

    private fun ends(d: Continuous, centre: Expr, se: Expr, level: Rational): Ends {
        val share = (Rational.ONE + level) / Rational.of(2)
        val cache = HashMap<Pair<Continuous, Rational>, Distributions.Quantile>()
        fun c(prec: Int) = Distributions.quantileBall(d, share, prec, cache)
        val critical = certified { prec -> c(prec) }
        val scale = StatNumbers.valueOf(se)
        val margin = certified(scale) { prec -> c(prec).mul(ball(se, prec + 16), prec) }
        val lower = certified(scale) { prec -> ball(centre, prec + 16).sub(c(prec + 8).mul(ball(se, prec + 16), prec + 8), prec) }
        val upper = certified(scale) { prec -> ball(centre, prec + 16).add(c(prec + 8).mul(ball(se, prec + 16), prec + 8), prec) }
        // The checks: the tail beyond the critical value is the level's, by the density's integral; the ends lie the margin
        // on either side of the centre.
        val tail = (Rational.ONE - share).toDouble()
        if (!agree(tail, Distributions.integral(d, StatNumbers.valueOf(critical), d.end))) throw CalcException(Failure.UNVERIFIED)
        if (!symmetricAbout(centre, margin, lower, upper)) throw CalcException(Failure.UNVERIFIED)
        return Ends(critical, margin, lower, upper)
    }

    /** Whether [lower] and [upper] lie [margin] below and above [centre], to their digits. */
    private fun symmetricAbout(centre: Expr, margin: Expr, lower: Expr, upper: Expr): Boolean {
        val c = StatNumbers.valueOf(centre)
        val m = StatNumbers.valueOf(margin)
        val l = StatNumbers.valueOf(lower)
        val u = StatNumbers.valueOf(upper)
        val size = kotlin.math.abs(l) + kotlin.math.abs(u) + kotlin.math.abs(m)
        return kotlin.math.abs(u - l - 2 * m) <= 1e-8 * size && kotlin.math.abs(u + l - 2 * c) <= 1e-8 * size
    }

    /** zinterval(data, σ) or zinterval(x̄, n, σ), then the level: x̄ ± z·σ/√n. */
    private fun zInterval(args: List<Arg>): StatResult {
        if (args.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val sample: Sample
        val sigma: Expr
        val rest: List<Arg>
        if (isList(args[0])) {
            if (args.size < 2) throw CalcException(Failure.ARGUMENTS)
            sample = sampleOf(list(args[0]), needSd = false)
            sigma = positive(number(args[1]))
            rest = args.drop(2)
        } else {
            if (args.size < 3) throw CalcException(Failure.ARGUMENTS)
            sample = Sample(count(number(args[1]), 1), number(args[0]), null)
            sigma = positive(number(args[2]))
            rest = args.drop(3)
        }
        val level = level(rest)
        val se = tidy(sigma / sqrt(num(sample.n.toLong())))
        val e = ends(Distributions.Normal, sample.mean, se, level)
        return StatResult(Mode.Z_INTERVAL, listOf(
            StatRow(Quantity.N, num(sample.n.toLong())), StatRow(Quantity.MEAN, sample.mean), StatRow(Quantity.SIGMA, sigma),
            StatRow(Quantity.LEVEL, num(level)), StatRow(Quantity.CRITICAL, e.critical), StatRow(Quantity.STANDARD_ERROR, se),
            StatRow(Quantity.MARGIN, e.margin), StatRow(Quantity.LOWER, e.lower), StatRow(Quantity.UPPER, e.upper),
        ), interval = e.lower to e.upper)
    }

    /** tinterval(data) or tinterval(x̄, s, n), then the level: x̄ ± t·s/√n, n − 1 degrees of freedom. */
    private fun tInterval(args: List<Arg>): StatResult {
        if (args.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val sample: Sample
        val rest: List<Arg>
        if (isList(args[0])) {
            sample = sampleOf(list(args[0]), needSd = true)
            rest = args.drop(1)
        } else {
            if (args.size < 3) throw CalcException(Failure.ARGUMENTS)
            sample = Sample(count(number(args[2]), 2), number(args[0]), positive(number(args[1])))
            rest = args.drop(3)
        }
        val level = level(rest)
        val s = sample.sd ?: throw CalcException(Failure.UNDEFINED)
        if (signOf(s) <= 0) throw CalcException(Failure.UNDEFINED)
        val se = tidy(s / sqrt(num(sample.n.toLong())))
        val df = num((sample.n - 1).toLong())
        val e = ends(Distributions.StudentT(df), sample.mean, se, level)
        return StatResult(Mode.T_INTERVAL, listOf(
            StatRow(Quantity.N, num(sample.n.toLong())), StatRow(Quantity.MEAN, sample.mean), StatRow(Quantity.SD, s),
            StatRow(Quantity.DF, df), StatRow(Quantity.LEVEL, num(level)), StatRow(Quantity.CRITICAL, e.critical),
            StatRow(Quantity.STANDARD_ERROR, se), StatRow(Quantity.MARGIN, e.margin), StatRow(Quantity.LOWER, e.lower), StatRow(Quantity.UPPER, e.upper),
        ), interval = e.lower to e.upper)
    }

    /**
     * propinterval(k, n), then the level: for k successes in n trials p̂ = k/n, the Wald interval p̂ ± z·√(p̂(1 − p̂)/n) that most
     * textbooks give, and Wilson's score interval, the p with |p̂ − p| ≤ z·√(p(1 − p)/n), which holds its level better for a small n
     * or a p̂ near 0 or 1 (and is what solving that inequality, as some schools teach, gives).
     */
    private fun proportionInterval(args: List<Arg>): StatResult {
        if (args.size < 2) throw CalcException(Failure.ARGUMENTS)
        val k = count(number(args[0]), 0)
        val n = count(number(args[1]), 1)
        if (k > n) throw CalcException(Failure.UNDEFINED)
        val level = level(args.drop(2))
        val nn = num(n.toLong())
        val pHat = num(Rational.of(k.toLong()) / Rational.of(n.toLong()))
        val se = tidy(sqrt(tidy(pHat * (Expr.ONE - pHat) / nn)))
        val share = (Rational.ONE + level) / Rational.of(2)
        val cache = HashMap<Pair<Continuous, Rational>, Distributions.Quantile>()
        fun z(prec: Int) = Distributions.quantileBall(Distributions.Normal, share, prec, cache)
        val critical = certified { prec -> z(prec) }
        val zD = StatNumbers.valueOf(critical)
        if (!agree((Rational.ONE - share).toDouble(), Distributions.integral(Distributions.Normal, zD, Double.POSITIVE_INFINITY))) {
            throw CalcException(Failure.UNVERIFIED)
        }
        val margin = certified(1.0) { prec -> z(prec).mul(ball(se, prec + 16), prec) }
        val lower = certified(1.0) { prec -> ball(pHat, prec + 16).sub(z(prec + 8).mul(ball(se, prec + 16), prec + 8), prec) }
        val upper = certified(1.0) { prec -> ball(pHat, prec + 16).add(z(prec + 8).mul(ball(se, prec + 16), prec + 8), prec) }
        // Wilson: (p̂ + z²/(2n) ± z·√(p̂(1 − p̂)/n + z²/(4n²)))/(1 + z²/n).
        fun wilson(sign: Int, prec: Int): Ball {
            val w = prec + 16
            val zz = z(w).sqr(w)
            val nb = Ball.of(n.toLong())
            val p = ball(pHat, w)
            val centre = p.add(zz.div(nb.shl(1), w), w)
            val root = BallMath.sqrt(p.mul(Ball.ONE.sub(p, w), w).div(nb, w).add(zz.div(nb.sqr(w).shl(2), w), w), w).mul(z(w), w)
            val top = if (sign < 0) centre.sub(root, w) else centre.add(root, w)
            return top.div(Ball.ONE.add(zz.div(nb, w), w), prec)
        }
        val wLower = certified(1.0) { prec -> wilson(-1, prec) }
        val wUpper = certified(1.0) { prec -> wilson(1, prec) }
        // Put back: Wilson's ends solve (p̂ − p)² = z²·p(1 − p)/n.
        for (end in listOf(-1, 1)) {
            val residual = wilsonResidual(end, n, pHat) { prec -> wilson(end, prec) to z(prec) }
            if (!residual) throw CalcException(Failure.UNVERIFIED)
        }
        // And the Wald ends: the margin on either side of p̂.
        if (!symmetricAbout(pHat, margin, lower, upper)) throw CalcException(Failure.UNVERIFIED)
        val few = minOf(k, n - k) < 5
        return StatResult(Mode.PROPORTION_INTERVAL, listOf(
            StatRow(Quantity.SUCCESSES, num(k.toLong())), StatRow(Quantity.N, nn), StatRow(Quantity.PROPORTION, pHat),
            StatRow(Quantity.LEVEL, num(level)), StatRow(Quantity.CRITICAL, critical), StatRow(Quantity.STANDARD_ERROR, se),
            StatRow(Quantity.MARGIN, margin), StatRow(Quantity.LOWER, lower), StatRow(Quantity.UPPER, upper),
            StatRow(Quantity.WILSON_LOWER, wLower), StatRow(Quantity.WILSON_UPPER, wUpper),
        ), interval = lower to upper, warnings = if (few) listOf(StatWarning.FEW_SUCCESSES) else emptyList())
    }

    /** Whether Wilson's end, put back, leaves (p̂ − p)² − z²·p(1 − p)/n at 0 to within 2⁻¹⁰⁰ of its terms. */
    private fun wilsonResidual(end: Int, n: Int, pHat: Expr, compute: (Int) -> Pair<Ball, Ball>): Boolean = try {
        val prec = 256
        val (p, z) = compute(prec)
        val ph = ball(pHat, prec)
        val left = ph.sub(p, prec).sqr(prec)
        val right = z.sqr(prec).mul(p, prec).mul(Ball.ONE.sub(p, prec), prec).div(Ball.of(n.toLong()), prec)
        val diff = left.sub(right, prec).magUp()
        val size = left.magUp() + right.magUp()
        diff <= size.shl(-100) || diff.isZero
    } catch (x: Imprecise) {
        false
    }
}
