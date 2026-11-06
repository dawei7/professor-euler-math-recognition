package com.professoreuler.mathcalc

/**
 * Descriptive statistics of data typed as numbers, beyond [Stats.summary]: a quantile of a list (`quantile([x₁, …], p)`), the
 * sample covariance and Pearson's correlation of points (`cov((x₁, y₁), …)`, `corr(…)`), and the modes of a list. Exact for exact
 * data, and each checked by a second computation.
 *
 * The quantile is the one spreadsheets (PERCENTILE.INC, QUANTILE.INC of Excel, LibreOffice and Google Sheets), R's default (type 7)
 * and NumPy's default take, and many textbooks: of the sorted values x₍₁₎ ≤ … ≤ x₍ₙ₎, at h = (n − 1)·p + 1 the value
 * x₍⌊h⌋₎ + (h − ⌊h⌋)·(x₍⌊h⌋+1₎ − x₍⌊h⌋₎), the straight line between neighbouring order statistics; quantile(…, 0) is the
 * minimum, ½ the median, 1 the maximum.
 */
internal object Describe {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    fun call(name: String, args: List<Expr>): Expr? = when (name) {
        "quantile" -> quantile(args)
        "cov" -> covariance(points(args))
        "corr" -> correlation(points(args))
        else -> null
    }

    /** The entries of a list typed in brackets ([1, 2, 3], a column or a row), or null. */
    fun entries(e: Expr): List<Expr>? {
        val m = e as? Expr.Mat ?: return null
        return when {
            m.columns == 1 -> m.rows.map { it[0] }
            m.size == 1 -> m.rows[0]
            else -> null
        }
    }

    private fun numbers(values: List<Expr>): List<Expr> {
        for (v in values) if (v is Expr.Mat || v.hasI() || infinite(v) || Numeric.value(v) == null) throw CalcException(Failure.ARGUMENTS)
        return values
    }

    private fun infinite(e: Expr) = StatNumbers.infinity(e) != 0

    /** The values sorted, exactly: a tie that doubles cannot split is decided in balls. */
    fun sorted(values: List<Expr>): List<Expr> = values.sortedWith { a, b -> StatNumbers.compare(a, b) }

    private fun quantile(args: List<Expr>): Expr {
        if (args.size != 2) throw CalcException(Failure.ARGUMENTS)
        val list = entries(args[0]) ?: throw CalcException(Failure.ARGUMENTS)
        val p = args[1]
        if (p is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val values = sorted(numbers(list))
        val pr = rational(p) ?: throw CalcException(Failure.UNDEFINED)
        if (pr.signum < 0 || pr > Rational.ONE) throw CalcException(Failure.UNDEFINED)
        val n = values.size
        // 0-based: h = (n − 1)·p, between the values at ⌊h⌋ and ⌊h⌋ + 1.
        val h = Rational.of((n - 1).toLong()) * pr
        val i = h.num.divide(h.den).toInt()
        val fraction = h - Rational.of(i.toLong())
        val value = if (fraction.signum == 0) values[i] else tidy(values[i] + num(fraction) * (values[i + 1] - values[i]))
        // The check, the other way it is written (1-based, h = (n − 1)·p + 1), and between its neighbours; the median at ½.
        val h1 = Rational.of((n - 1).toLong()) * pr + Rational.ONE
        val j = h1.num.divide(h1.den).toInt()
        val g = h1 - Rational.of(j.toLong())
        val again = if (j >= n) values[n - 1] else tidy(mul(num(Rational.ONE - g), values[j - 1]) + mul(num(g), values[j]))
        if (!LinAlg.isNull(tidy(value - again))) throw CalcException(Failure.UNVERIFIED)
        if (StatNumbers.compare(value, values[i]) < 0 || StatNumbers.compare(value, values[minOf(i + 1, n - 1)]) > 0) throw CalcException(Failure.UNVERIFIED)
        if (pr == Rational.of(1) / Rational.of(2) && n >= 2) {
            val median = Stats.summary(values)?.median ?: throw CalcException(Failure.UNVERIFIED)
            if (!LinAlg.isNull(tidy(median - value))) throw CalcException(Failure.UNVERIFIED)
        }
        return value
    }

    /** Points typed as (x, y): each a vector of two numbers; at least two of them. */
    fun points(args: List<Expr>): List<Pair<Expr, Expr>> {
        if (args.size < 2) throw CalcException(Failure.ARGUMENTS)
        return args.map { a ->
            val e = entries(a)?.takeIf { it.size == 2 } ?: throw CalcException(Failure.ARGUMENTS)
            numbers(e)
            e[0] to e[1]
        }
    }

    private class Sums(val n: Int, val sxx: Expr, val syy: Expr, val sxy: Expr)

    /** Σ(x − x̄)², Σ(y − ȳ)², Σ(x − x̄)(y − ȳ), exactly. */
    private fun sums(points: List<Pair<Expr, Expr>>): Sums {
        val n = points.size
        val nn = num(n.toLong())
        val mx = tidy(add(points.map { it.first }) / nn)
        val my = tidy(add(points.map { it.second }) / nn)
        val sxx = tidy(add(points.map { pow(tidy(it.first - mx), Expr.TWO) }))
        val syy = tidy(add(points.map { pow(tidy(it.second - my), Expr.TWO) }))
        val sxy = tidy(add(points.map { mul(tidy(it.first - mx), tidy(it.second - my)) }))
        return Sums(n, sxx, syy, sxy)
    }

    /** The sample covariance s_xy = Σ(x − x̄)(y − ȳ)/(n − 1), checked against (Σxy − n·x̄·ȳ)/(n − 1). */
    private fun covariance(points: List<Pair<Expr, Expr>>): Expr {
        val s = sums(points)
        val n1 = num((s.n - 1).toLong())
        val value = tidy(s.sxy / n1)
        val n = num(s.n.toLong())
        val sx = tidy(add(points.map { it.first }))
        val sy = tidy(add(points.map { it.second }))
        val again = tidy((add(points.map { mul(it.first, it.second) }) - sx * sy / n) / n1)
        if (!LinAlg.isNull(tidy(value - again))) throw CalcException(Failure.UNVERIFIED)
        return value
    }

    /**
     * Pearson's r = s_xy/(s_x·s_y) = Σ(x − x̄)(y − ȳ)/√(Σ(x − x̄)²·Σ(y − ȳ)²): undefined where the x or the y are all equal; checked
     * against the regression line's r ([Stats.regression]), which works it out from Σx, Σy, Σxy, Σx², Σy².
     */
    private fun correlation(points: List<Pair<Expr, Expr>>): Expr {
        val s = sums(points)
        if (LinAlg.isNull(s.sxx) || LinAlg.isNull(s.syy)) throw CalcException(Failure.UNDEFINED)
        val value = tidy(s.sxy / sqrt(tidy(s.sxx * s.syy)))
        val line = Stats.regression(points.map { it.first }, points.map { it.second }) ?: throw CalcException(Failure.UNVERIFIED)
        val r = line.r ?: throw CalcException(Failure.UNVERIFIED)
        if (!LinAlg.isNull(tidy(value - r))) {
            val a = Numeric.value(value)
            val b = Numeric.value(r)
            if (a == null || b == null || !StatNumbers.agree(a, b, 1e-12)) throw CalcException(Failure.UNVERIFIED)
        }
        return value
    }

    /**
     * The modes of a list: every value that occurs most often, where some value occurs more than once; none (an empty set)
     * where every value occurs once. Checked by counting again.
     */
    fun modes(values: List<Expr>): List<Expr> {
        numbers(values)
        val groups = ArrayList<Pair<Expr, Int>>()
        for (v in sorted(values)) {
            val last = groups.lastOrNull()
            if (last != null && StatNumbers.compare(last.first, v) == 0) groups[groups.size - 1] = last.first to last.second + 1 else groups += v to 1
        }
        val top = groups.maxOf { it.second }
        if (top == 1) return emptyList()
        val modes = groups.filter { it.second == top }.map { it.first }
        // Counted again, each against the whole list.
        for (m in modes) if (values.count { StatNumbers.compare(it, m) == 0 } != top) throw CalcException(Failure.UNVERIFIED)
        if (values.any { v -> modes.none { StatNumbers.compare(it, v) == 0 } && values.count { StatNumbers.compare(it, v) == 0 } >= top }) throw CalcException(Failure.UNVERIFIED)
        return modes
    }
}
