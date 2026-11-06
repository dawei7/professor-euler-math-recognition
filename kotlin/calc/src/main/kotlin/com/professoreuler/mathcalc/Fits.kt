package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.StatCommands.Arg
import com.professoreuler.mathcalc.StatNumbers.certified
import kotlin.math.abs

/**
 * Curves fitted to points by least squares, as GeoGebra names them: `fitpoly(points…, n)` (a polynomial of degree n, exact for
 * exact points: the normal equations solved in fractions), `fitexp` (y = a·e^(kx), also written a·bˣ with b = e^k), `fitpow`
 * (y = a·x^b), `fitlog` (y = a + b·ln x) and `fitlogistic` (y = c/(1 + a·e^(−kx))). The exponential and the power fit are the least
 * squares of ln y, as calculators and textbooks fit them (ln y = ln a + k·x, ln y = ln a + b·ln x); the logarithmic fit is the least
 * squares of y itself; the logistic one is found numerically (Levenberg–Marquardt from several starts) and certified in balls by
 * Krawczyk's test, which proves that the box around it holds exactly one point where the gradient of the squared residuals is 0,
 * and the positive Hessian there that it is a minimum. R² = 1 − SS_res/SS_tot is taken on the y as given, for every fit.
 *
 * Each fit is checked: the polynomial's normal equations hold exactly at its coefficients, the linearized fits' normal equations
 * hold in balls and their coefficients agree with a second computation in doubles by other formulas, and the residuals are the
 * points' y less the curve's values, recomputed.
 */
internal object Fits {
    private fun tidy(e: Expr) = Calculus.tidy(e)
    private val X = Expr.Sym("x")

    fun of(mode: Mode, args: List<Arg>): StatResult = when (mode) {
        Mode.FIT_POLYNOMIAL -> polynomial(args)
        Mode.FIT_EXPONENTIAL, Mode.FIT_POWER, Mode.FIT_LOGARITHMIC -> linearized(mode, points(args, 2))
        Mode.FIT_LOGISTIC -> logistic(points(args, 4))
        else -> throw CalcException(Failure.UNREADABLE)
    }

    /** The points (x, y), each two numbers typed as fractions or decimals, at least [least] of them with two different x. */
    private fun points(args: List<Arg>, least: Int): List<Pair<Rational, Rational>> {
        if (args.size < least) throw CalcException(Failure.ARGUMENTS)
        val points = args.map { a ->
            val e = (a as? Arg.Value)?.e ?: throw CalcException(Failure.ARGUMENTS)
            val p = Describe.entries(e)?.takeIf { it.size == 2 } ?: throw CalcException(Failure.ARGUMENTS)
            (rational(p[0]) ?: throw CalcException(Failure.ARGUMENTS)) to (rational(p[1]) ?: throw CalcException(Failure.ARGUMENTS))
        }
        if (points.map { it.first }.toSet().size < 2) throw CalcException(Failure.SINGULAR)
        return points
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // A polynomial, exact.

    private fun polynomial(args: List<Arg>): StatResult {
        if (args.size < 3) throw CalcException(Failure.ARGUMENTS)
        val degreeArg = (args.last() as? Arg.Value)?.e ?: throw CalcException(Failure.ARGUMENTS)
        if (degreeArg is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val degree = integer(degreeArg)?.takeIf { it.signum() >= 0 && it.bitLength() < 8 }?.toInt() ?: throw CalcException(Failure.UNDEFINED)
        if (degree > 10) throw CalcException(Failure.TOO_LARGE)
        val pts = points(args.dropLast(1), 2)
        // Enough different x for the degree: n + 1 of them.
        if (pts.map { it.first }.toSet().size < degree + 1) throw CalcException(Failure.SINGULAR)
        val m = degree + 1
        val powers = pts.map { (x, _) -> (0 until 2 * m).map { j -> x.pow(j) ?: throw CalcException(Failure.TOO_LARGE) } }
        val a = (0 until m).map { i -> (0 until m).map { j -> powers.fold(Rational.ZERO) { s, p -> s + p[i + j] } } }
        val b = (0 until m).map { i -> pts.indices.fold(Rational.ZERO) { s, k -> s + powers[k][i] * pts[k].second } }
        val beta = solve(a, b) ?: throw CalcException(Failure.SINGULAR)
        val fitted = pts.indices.map { k -> (0 until m).fold(Rational.ZERO) { s, j -> s + beta[j] * powers[k][j] } }
        val residuals = pts.indices.map { pts[it].second - fitted[it] }
        // The check: the residuals are orthogonal to every column of the design, Σ rᵢ·xᵢʲ = 0, exactly.
        for (j in 0 until m) {
            if (pts.indices.fold(Rational.ZERO) { s, k -> s + residuals[k] * powers[k][j] }.signum != 0) throw CalcException(Failure.UNVERIFIED)
        }
        val ssRes = residuals.fold(Rational.ZERO) { s, r -> s + r * r }
        val mean = pts.fold(Rational.ZERO) { s, p -> s + p.second } / Rational.of(pts.size.toLong())
        val ssTot = pts.fold(Rational.ZERO) { s, p -> s + (p.second - mean) * (p.second - mean) }
        val model = tidy(add((0 until m).map { j -> mul(num(beta[j]), pow(X, num(j.toLong()))) }))
        val rows = ArrayList<StatRow>()
        rows += StatRow(Quantity.N, num(pts.size.toLong()))
        for (j in degree downTo 0) rows += StatRow(Quantity.COEFFICIENT, num(beta[j]), symbol = "a_$j")
        if (ssTot.signum != 0) rows += StatRow(Quantity.R_SQUARED, num(Rational.ONE - ssRes / ssTot))
        rows += StatRow(Quantity.RESIDUAL_SUM, num(ssRes))
        return StatResult(Mode.FIT_POLYNOMIAL, rows, model = model, xs = pts.map { num(it.first) }, ys = pts.map { num(it.second) },
            fitted = fitted.map { num(it) }, residuals = residuals.map { num(it) })
    }

    /** A·x = b in fractions by Gaussian elimination; null where A is singular. */
    internal fun solve(a: List<List<Rational>>, b: List<Rational>): List<Rational>? {
        val n = b.size
        val m = Array(n) { i -> Array(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (col in 0 until n) {
            val pivot = (col until n).firstOrNull { m[it][col].signum != 0 } ?: return null
            if (pivot != col) { val t = m[pivot]; m[pivot] = m[col]; m[col] = t }
            val p = m[col][col]
            for (j in col..n) m[col][j] = m[col][j] / p
            for (i in 0 until n) {
                if (i == col || m[i][col].signum == 0) continue
                val f = m[i][col]
                for (j in col..n) m[i][j] = m[i][j] - f * m[col][j]
                Budget.check()
            }
        }
        return (0 until n).map { m[it][n] }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The linearized fits, in balls.

    /** v = intercept + slope·u by least squares, in balls: slope = Σ(u − ū)(v − v̄)/Σ(u − ū)². */
    private fun line(us: List<Ball>, vs: List<Ball>, prec: Int): Pair<Ball, Ball> {
        val n = Ball.of(us.size.toLong())
        val ub = us.fold(Ball.ZERO) { s, u -> s.add(u, prec) }.div(n, prec)
        val vb = vs.fold(Ball.ZERO) { s, v -> s.add(v, prec) }.div(n, prec)
        var sxy = Ball.ZERO
        var sxx = Ball.ZERO
        for (i in us.indices) {
            val du = us[i].sub(ub, prec)
            sxy = sxy.add(du.mul(vs[i].sub(vb, prec), prec), prec)
            sxx = sxx.add(du.sqr(prec), prec)
        }
        val slope = sxy.div(sxx, prec)
        return slope to vb.sub(slope.mul(ub, prec), prec)
    }

    /** The same in doubles by the other formulas, (nΣuv − ΣuΣv)/(nΣu² − (Σu)²): the check. */
    private fun lineD(us: List<Double>, vs: List<Double>): Pair<Double, Double> {
        val n = us.size.toDouble()
        val su = us.sum()
        val sv = vs.sum()
        val suv = us.indices.sumOf { us[it] * vs[it] }
        val suu = us.sumOf { it * it }
        val slope = (n * suv - su * sv) / (n * suu - su * su)
        return slope to (sv - slope * su) / n
    }

    private fun lnBall(r: Rational, prec: Int): Ball = BallMath.ln(Ball.of(r, prec + 8), prec)

    /**
     * fitexp, fitpow, fitlog: the least-squares line of the transformed points (x, ln y), (ln x, ln y), (ln x, y), and the curve
     * it makes, with R² on the y as given.
     */
    private fun linearized(mode: Mode, pts: List<Pair<Rational, Rational>>): StatResult {
        val logX = mode != Mode.FIT_EXPONENTIAL
        val logY = mode != Mode.FIT_LOGARITHMIC
        if (logX && pts.any { it.first.signum <= 0 }) throw CalcException(Failure.UNDEFINED)
        if (logY && pts.any { it.second.signum <= 0 }) throw CalcException(Failure.UNDEFINED)
        fun us(prec: Int) = pts.map { if (logX) lnBall(it.first, prec) else Ball.of(it.first, prec) }
        fun vs(prec: Int) = pts.map { if (logY) lnBall(it.second, prec) else Ball.of(it.second, prec) }
        // The coefficients as the curve writes them: exp a·e^(kx) (and b = e^k), pow a·x^b, log a + b·ln x.
        fun coefficients(prec: Int): List<Ball> {
            val (slope, intercept) = line(us(prec + 16), vs(prec + 16), prec + 16)
            return when (mode) {
                Mode.FIT_EXPONENTIAL -> listOf(BallMath.exp(intercept, prec), slope.rounded(prec), BallMath.exp(slope, prec))
                Mode.FIT_POWER -> listOf(BallMath.exp(intercept, prec), slope.rounded(prec))
                else -> listOf(intercept.rounded(prec), slope.rounded(prec))
            }
        }
        // The check: the normal equations of the transformed points hold, and a second computation in doubles agrees.
        run {
            val prec = 160
            val u = us(prec)
            val v = vs(prec)
            val (slope, intercept) = line(u, v, prec)
            var s0 = Ball.ZERO
            var s1 = Ball.ZERO
            var size = BigFloat.ZERO
            for (i in u.indices) {
                val r = v[i].sub(intercept.add(slope.mul(u[i], prec), prec), prec)
                s0 = s0.add(r, prec)
                s1 = s1.add(r.mul(u[i], prec), prec)
                size = size + v[i].magUp() + u[i].magUp().let { it * it }
            }
            val tiny = size.shl(-100)
            if (s0.magUp() > tiny || s1.magUp() > tiny) throw CalcException(Failure.UNVERIFIED)
            val (slopeD, interceptD) = lineD(u.map { it.toDouble() }, v.map { it.toDouble() })
            val scale = maxOf(abs(slope.toDouble()), abs(intercept.toDouble()), 1e-300)
            if (abs(slopeD - slope.toDouble()) > 1e-8 * scale || abs(interceptD - intercept.toDouble()) > 1e-8 * scale) {
                throw CalcException(Failure.UNVERIFIED)
            }
        }
        val names = when (mode) {
            Mode.FIT_EXPONENTIAL -> listOf("a", "k", "b")
            Mode.FIT_POWER -> listOf("a", "b")
            else -> listOf("a", "b")
        }
        val values = names.indices.map { j -> certified { prec -> coefficients(prec)[j] } }
        // The curve's values at the points, in balls from the coefficients' own balls.
        fun curve(x: Rational, c: List<Ball>, prec: Int): Ball = when (mode) {
            Mode.FIT_EXPONENTIAL -> c[0].mul(BallMath.exp(c[1].mul(Ball.of(x, prec), prec), prec), prec)
            Mode.FIT_POWER -> c[0].mul(BallMath.exp(c[1].mul(lnBall(x, prec), prec), prec), prec)
            else -> c[0].add(c[1].mul(lnBall(x, prec), prec), prec)
        }
        val scale = pts.maxOf { abs(it.second.toDouble()) }
        val fitted = pts.map { (x, _) -> certified { prec -> curve(x, coefficients(prec + 16), prec) } }
        val residuals = pts.map { (x, y) -> certified(scale) { prec -> Ball.of(y, prec + 16).sub(curve(x, coefficients(prec + 16), prec + 16), prec) } }
        val ssTot = run {
            val mean = pts.fold(Rational.ZERO) { s, p -> s + p.second } / Rational.of(pts.size.toLong())
            pts.fold(Rational.ZERO) { s, p -> s + (p.second - mean) * (p.second - mean) }
        }
        fun ssRes(prec: Int): Ball {
            val c = coefficients(prec + 16)
            return pts.fold(Ball.ZERO) { s, (x, y) -> s.add(Ball.of(y, prec + 16).sub(curve(x, c, prec + 16), prec + 16).sqr(prec + 16), prec + 16) }
        }
        val residualSum = certified(scale * scale) { prec -> ssRes(prec) }
        val rows = ArrayList<StatRow>()
        rows += StatRow(Quantity.N, num(pts.size.toLong()))
        for (j in names.indices) rows += StatRow(Quantity.COEFFICIENT, values[j], symbol = names[j])
        if (ssTot.signum != 0) rows += StatRow(Quantity.R_SQUARED, certified(1.0) { prec -> Ball.ONE.sub(ssRes(prec + 8).div(Ball.of(ssTot, prec + 8), prec + 8), prec) })
        rows += StatRow(Quantity.RESIDUAL_SUM, residualSum)
        // The check of the residuals: each is the point's y less the curve's value.
        for (i in pts.indices) {
            val r = StatNumbers.valueOf(residuals[i])
            val again = pts[i].second.toDouble() - StatNumbers.valueOf(fitted[i])
            if (abs(r - again) > 1e-8 * maxOf(scale, 1e-300)) throw CalcException(Failure.UNVERIFIED)
        }
        val model = when (mode) {
            Mode.FIT_EXPONENTIAL -> mul(values[0], exp(mul(values[1], X)))
            Mode.FIT_POWER -> mul(values[0], pow(X, values[1]))
            else -> add(values[0], mul(values[1], ln(X)))
        }
        return StatResult(mode, rows, model = model, xs = pts.map { num(it.first) }, ys = pts.map { num(it.second) },
            fitted = fitted, residuals = residuals)
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The logistic fit, numerical and certified.

    /** y = c/(1 + a·e^(−kx)) by least squares. */
    private fun logistic(pts: List<Pair<Rational, Rational>>): StatResult {
        if (pts.any { it.second.signum <= 0 }) throw CalcException(Failure.UNDEFINED)
        val xs = pts.map { it.first.toDouble() }
        val ys = pts.map { it.second.toDouble() }
        val start = bestStart(xs, ys) ?: throw CalcException(Failure.UNSOLVED)
        var box: List<Ball>? = null
        var prec = 128
        while (prec <= 1024 && box == null) {
            box = certify(pts, start, prec)
            prec *= 2
        }
        val k = box ?: throw CalcException(Failure.UNSOLVED)
        if (!k[1].isPositive) throw CalcException(Failure.UNSOLVED)
        val names = listOf("c", "a", "k")
        val values = (0..2).map { j -> boxValue(k[j]) }
        val scale = ys.maxOf { abs(it) }
        fun curve(x: Rational, w: Int): Ball {
            val u = BallMath.exp(-k[2].mul(Ball.of(x, w), w), w)
            return k[0].div(Ball.ONE.add(k[1].mul(u, w), w), w)
        }
        val w = 256
        val fittedBalls = pts.map { curve(it.first, w) }
        val fitted = fittedBalls.map { boxValue(it) }
        val residualBalls = pts.indices.map { Ball.of(pts[it].second, w).sub(fittedBalls[it], w) }
        val residuals = residualBalls.map { boxValue(it, scale) }
        val ss = residualBalls.fold(Ball.ZERO) { s, r -> s.add(r.sqr(w), w) }
        val mean = pts.fold(Rational.ZERO) { s, p -> s + p.second } / Rational.of(pts.size.toLong())
        val ssTot = pts.fold(Rational.ZERO) { s, p -> s + (p.second - mean) * (p.second - mean) }
        val rows = ArrayList<StatRow>()
        rows += StatRow(Quantity.N, num(pts.size.toLong()))
        for (j in 0..2) rows += StatRow(Quantity.COEFFICIENT, values[j], symbol = names[j])
        if (ssTot.signum != 0) rows += StatRow(Quantity.R_SQUARED, boxValue(Ball.ONE.sub(ss.div(Ball.of(ssTot, w), w), w), 1.0))
        rows += StatRow(Quantity.RESIDUAL_SUM, boxValue(ss, scale * scale))
        val model = mul(values[0], pow(add(Expr.ONE, mul(values[1], exp(mul(neg(values[2]), X)))), Expr.MINUS_ONE))
        return StatResult(Mode.FIT_LOGISTIC, rows, model = model, xs = pts.map { num(it.first) }, ys = pts.map { num(it.second) },
            fitted = fitted, residuals = residuals)
    }

    /** A ball's certified decimal (it holds the true value: the box proves it). */
    private fun boxValue(b: Ball, scale: Double? = null): Expr = certified(scale) { _ -> b }

    /** The model's value and its first and second derivatives in (c, a, k) at x, in balls. */
    private class Terms(val f: Ball, val d: Array<Ball>, val dd: Array<Array<Ball>>)

    private fun terms(t: List<Ball>, x: Ball, w: Int): Terms {
        val c = t[0]
        val a = t[1]
        val k = t[2]
        val u = BallMath.exp(-k.mul(x, w), w)
        val den = Ball.ONE.add(a.mul(u, w), w)
        val d1 = den.inverse(w)
        val d2 = d1.sqr(w)
        val d3 = d2.mul(d1, w)
        val xu = x.mul(u, w)
        val f = c.mul(d1, w)
        val fc = d1
        val fa = -c.mul(u, w).mul(d2, w)
        val fk = c.mul(a, w).mul(xu, w).mul(d2, w)
        val fca = -u.mul(d2, w)
        val fck = a.mul(xu, w).mul(d2, w)
        val faa = c.mul(u.sqr(w), w).mul(d3, w).shl(1)
        val fak = c.mul(xu, w).mul(d2, w).sub(c.mul(a, w).mul(xu, w).mul(u, w).mul(d3, w).shl(1), w)
        val fkk = c.mul(a, w).mul(x, w).mul(xu, w).mul(d2, w).let { first ->
            c.mul(a.sqr(w), w).mul(xu.sqr(w), w).mul(d3, w).shl(1).sub(first, w)
        }
        return Terms(f, arrayOf(fc, fa, fk), arrayOf(arrayOf(Ball.ZERO, fca, fck), arrayOf(fca, faa, fak), arrayOf(fck, fak, fkk)))
    }

    /** The gradient of SS = Σ(yᵢ − f(xᵢ))² and, where [hessian], its Hessian, at the parameters [t] (balls: a box gives enclosures). */
    private fun gradient(pts: List<Pair<Rational, Rational>>, t: List<Ball>, w: Int, hessian: Boolean): Pair<Array<Ball>, Array<Array<Ball>>?> {
        val g = Array(3) { Ball.ZERO }
        val h = if (hessian) Array(3) { Array(3) { Ball.ZERO } } else null
        for ((x, y) in pts) {
            val tm = terms(t, Ball.of(x, w), w)
            val r = Ball.of(y, w).sub(tm.f, w)
            for (j in 0..2) {
                g[j] = g[j].sub(r.mul(tm.d[j], w).shl(1), w)
                if (h != null) for (l in 0..2) h[j][l] = h[j][l].add(tm.d[j].mul(tm.d[l], w).sub(r.mul(tm.dd[j][l], w), w).shl(1), w)
            }
            Budget.check()
        }
        return g to h
    }

    /**
     * Krawczyk's test at [prec] bits from the estimate [start]: Newton's steps in balls, then the box X = θ ± r and
     * K = θ − Y·g(θ) + (I − Y·H(X))·(X − θ) with Y the inverse of H(θ); K inside X proves that X holds exactly one zero of the
     * gradient, which lies in K; a positive definite H(X) makes it a minimum. Null where it does not close.
     */
    private fun certify(pts: List<Pair<Rational, Rational>>, start: DoubleArray, prec: Int): List<Ball>? = try {
        val w = prec + 32
        var theta = start.map { BigFloat.of(it) }
        var y: Array<DoubleArray>? = null
        repeat(4) {
            val (g, h) = gradient(pts, theta.map { Ball(it) }, w, hessian = true)
            val hd = Array(3) { i -> DoubleArray(3) { j -> h!![i][j].toDouble() } }
            val inv = inverse3(hd) ?: return null
            y = inv
            theta = (0..2).map { i ->
                var step = BigFloat.ZERO
                for (j in 0..2) step += BigFloat.of(inv[i][j]) * g[j].mid
                (theta[i] - step).round(w, Round.NEAREST)
            }
        }
        val yy = y ?: return null
        val r = theta.map { (if (it.isZero) BigFloat.ONE else it.abs()).shl(-(prec / 2).toLong()).round(32, Round.CEILING) }
        val box = (0..2).map { Ball(theta[it], r[it]) }
        val (g0, _) = gradient(pts, theta.map { Ball(it) }, w, hessian = false)
        val (_, hx) = gradient(pts, box, w, hessian = true)
        val yb = Array(3) { i -> Array(3) { j -> Ball(BigFloat.of(yy[i][j])) } }
        val k = (0..2).map { i ->
            var v = Ball(theta[i])
            for (j in 0..2) v = v.sub(yb[i][j].mul(g0[j], w), w)
            for (j in 0..2) {
                // (I − Y·H(X))ᵢⱼ times (X − θ)ⱼ = ±rⱼ
                var m = if (i == j) Ball.ONE else Ball.ZERO
                for (l in 0..2) m = m.sub(yb[i][l].mul(hx!![l][j], w), w)
                v = v.add(m.mul(Ball(BigFloat.ZERO, r[j]), w), w)
            }
            v
        }
        val inside = (0..2).all { i -> addUp((k[i].mid - theta[i]).abs(), k[i].rad) < r[i] }
        val h = hx!!
        val m1 = h[0][0]
        val m2 = h[0][0].mul(h[1][1], w).sub(h[0][1].mul(h[1][0], w), w)
        val m3 = det3(h, w)
        if (inside && m1.isPositive && m2.isPositive && m3.isPositive) k else null
    } catch (x: Imprecise) {
        null
    }

    private fun det3(h: Array<Array<Ball>>, w: Int): Ball {
        fun m(a: Ball, b: Ball, c: Ball, d: Ball) = a.mul(d, w).sub(b.mul(c, w), w)
        return h[0][0].mul(m(h[1][1], h[1][2], h[2][1], h[2][2]), w)
            .sub(h[0][1].mul(m(h[1][0], h[1][2], h[2][0], h[2][2]), w), w)
            .add(h[0][2].mul(m(h[1][0], h[1][1], h[2][0], h[2][1]), w), w)
    }

    private fun inverse3(m: Array<DoubleArray>): Array<DoubleArray>? {
        val a = m
        val c00 = a[1][1] * a[2][2] - a[1][2] * a[2][1]
        val c01 = a[1][2] * a[2][0] - a[1][0] * a[2][2]
        val c02 = a[1][0] * a[2][1] - a[1][1] * a[2][0]
        val det = a[0][0] * c00 + a[0][1] * c01 + a[0][2] * c02
        if (det == 0.0 || !det.isFinite()) return null
        val inv = arrayOf(
            doubleArrayOf(c00, a[0][2] * a[2][1] - a[0][1] * a[2][2], a[0][1] * a[1][2] - a[0][2] * a[1][1]),
            doubleArrayOf(c01, a[0][0] * a[2][2] - a[0][2] * a[2][0], a[0][2] * a[1][0] - a[0][0] * a[1][2]),
            doubleArrayOf(c02, a[0][1] * a[2][0] - a[0][0] * a[2][1], a[0][0] * a[1][1] - a[0][1] * a[1][0]),
        )
        return Array(3) { i -> DoubleArray(3) { j -> inv[i][j] / det } }
    }

    /**
     * The best of several starts for (c, a, k): c a little above the largest y (1.01 to 5 times it), ln(c/y − 1) = ln a − kx by
     * least squares for a and k, then Levenberg–Marquardt in doubles; the start whose fit has the least SS.
     */
    private fun bestStart(xs: List<Double>, ys: List<Double>): DoubleArray? {
        val top = ys.maxOf { it }
        var best: DoubleArray? = null
        var bestSs = Double.POSITIVE_INFINITY
        for (factor in doubleArrayOf(1.01, 1.05, 1.2, 1.5, 2.0, 3.0, 5.0)) {
            val c0 = top * factor
            val vs = ys.map { kotlin.math.ln(c0 / it - 1) }
            val (slope, intercept) = lineD(xs, vs)
            if (!slope.isFinite() || !intercept.isFinite()) continue
            val fit = marquardt(xs, ys, doubleArrayOf(c0, kotlin.math.exp(intercept), -slope)) ?: continue
            val ss = ss(xs, ys, fit)
            if (ss < bestSs && fit[1] > 0) {
                bestSs = ss
                best = fit
            }
            Budget.check()
        }
        return best
    }

    private fun model(x: Double, t: DoubleArray) = t[0] / (1 + t[1] * kotlin.math.exp(-t[2] * x))

    private fun ss(xs: List<Double>, ys: List<Double>, t: DoubleArray) = xs.indices.sumOf { val r = ys[it] - model(xs[it], t); r * r }

    /** Levenberg–Marquardt for the logistic curve in doubles; null where it does not converge. */
    private fun marquardt(xs: List<Double>, ys: List<Double>, start: DoubleArray): DoubleArray? {
        var t = start.copyOf()
        var lambda = 1e-3
        var current = ss(xs, ys, t)
        for (iteration in 0 until 500) {
            val jtj = Array(3) { DoubleArray(3) }
            val jtr = DoubleArray(3)
            for (i in xs.indices) {
                val u = kotlin.math.exp(-t[2] * xs[i])
                val den = 1 + t[1] * u
                val j = doubleArrayOf(1 / den, -t[0] * u / (den * den), t[0] * t[1] * xs[i] * u / (den * den))
                val r = ys[i] - t[0] / den
                for (a in 0..2) {
                    jtr[a] += j[a] * r
                    for (b in 0..2) jtj[a][b] += j[a] * j[b]
                }
            }
            var improved = false
            for (attempt in 0 until 30) {
                val m = Array(3) { a -> DoubleArray(3) { b -> jtj[a][b] + if (a == b) lambda * jtj[a][a] else 0.0 } }
                val inv = inverse3(m) ?: break
                val step = DoubleArray(3) { a -> (0..2).sumOf { b -> inv[a][b] * jtr[b] } }
                val next = DoubleArray(3) { t[it] + step[it] }
                val s = ss(xs, ys, next)
                if (s.isFinite() && s <= current) {
                    val small = (0..2).all { abs(step[it]) <= 1e-14 * maxOf(abs(t[it]), 1e-300) }
                    t = next
                    current = s
                    lambda = maxOf(lambda / 10, 1e-15)
                    improved = true
                    if (small) return t
                    break
                }
                lambda *= 10
            }
            if (!improved) return if (t.all { it.isFinite() }) t else null
            Budget.check()
        }
        return t
    }
}
