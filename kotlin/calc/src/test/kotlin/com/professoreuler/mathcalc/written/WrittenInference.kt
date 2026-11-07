package com.professoreuler.mathcalc

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The statistics functions. Densities, tails and quantiles of the t, χ², F, gamma, beta, lognormal and exponential distributions
 * and hypothesis tests and intervals on random data against mpmath's values at 40 digits ([WrittenTables], from
 * tools/corpus_tables.py); the uniform and hypergeometric distributions, fits, Markov chains, quantiles, covariances and modes in
 * the test's own fractions (binomial coefficients, normal equations, the stationary equations by elimination, powers of the
 * transition matrix) and doubles (regressions of ln y).
 */
internal object WrittenInference {
    private const val AREA = "statistics"

    private fun tables(): List<Task> {
        val out = ArrayList<Task>()
        for ((call, v) in WrittenTables.DISTRIBUTIONS.distinctBy { it.first }) {
            val ref = v.toDouble()
            out += Task(AREA, call, v, judge = if (ref == 0.0) isNumber(0.0) else isNumber(ref, 1e-9))
        }
        for ((call, quantities, reject) in WrittenTables.TESTS.distinctBy { it.first }) {
            out += Task(AREA, call, quantities.joinToString(", ") { "${it.first} ${it.second}" }, judge = { c ->
                val res = (c.outcome as? Outcome.Statistical)?.result
                res != null && (reject == null || res.reject == reject) && quantities.all { (name, value) ->
                    val q = Quantity.valueOf(name)
                    val got = res.value(q)?.let { valueOf(it) }
                    got != null && close(got, value.toDouble(), 1e-8, 1e-12)
                }
            })
        }
        return out
    }

    private val PASCAL: Array<Array<BigInteger>> = Array(61) { arrayOf(BigInteger.ONE) }.also { p ->
        for (n in 1..60) p[n] = Array(n + 1) { k -> if (k == 0 || k == n) BigInteger.ONE else p[n - 1][k - 1] + p[n - 1][k] }
    }

    private fun binom(n: Int, k: Int): BigInteger = if (k < 0 || k > n) BigInteger.ZERO else PASCAL[n][k]

    private fun discrete(r: Random): List<Task> {
        val out = ArrayList<Task>()
        // Hypergeometric: C(K, k)·C(N − K, n − k)/C(N, n), exactly.
        repeat(40) {
            val big = r.int(5, 60); val k = r.int(1, big); val n = r.int(1, big); val x = r.int(0, minOf(k, n))
            fun pdf(j: Int) = Q.of(binom(k, j) * binom(big - k, n - j), binom(big, n))
            out += Task(AREA, "hypergeompdf($big, $k, $n, $x)", "", judge = isExact(pdf(x)))
            out += Task(AREA, "hypergeomcdf($big, $k, $n, $x)", "", judge = isExact((0..x).fold(Q.ZERO) { s, j -> s + pdf(j) }))
        }
        // Uniform: 1/(b − a) inside, the share of [lo, hi] inside, a + p(b − a).
        repeat(30) {
            val a = r.int(-5, 5); val b = a + r.int(1, 8)
            val x = q(r.int(2 * a - 2, 2 * b + 2).toLong(), 2)
            val lo = q(r.int(2 * a - 2, 2 * b).toLong(), 2); val hi = lo + q(r.int(1, 8).toLong(), 2)
            val inside = { v: Q -> if (v < q(a.toLong())) q(a.toLong()) else if (v > q(b.toLong())) q(b.toLong()) else v }
            out += Task(AREA, "uniformpdf(${x.typed()}, $a, $b)", "", judge = isExact(if (x < q(a.toLong()) || x > q(b.toLong())) Q.ZERO else q(1, (b - a).toLong())))
            out += Task(AREA, "uniformcdf(${lo.typed()}, ${hi.typed()}, $a, $b)", "", judge = isExact((inside(hi) - inside(lo)) / q((b - a).toLong())))
            val p = q(r.int(1, 19).toLong(), 20)
            out += Task(AREA, "invuniform(${decimalText(p)}, $a, $b)", "", judge = isExact(q(a.toLong()) + p * q((b - a).toLong())))
        }
        return out
    }

    private fun Random.points(n: Int, positive: Boolean): List<Pair<Q, Q>> =
        (1..n).map { k -> q(if (positive) k.toLong() else (k - 3).toLong()) to q(if (positive) int(1, 30).toLong() else int(-10, 20).toLong()) }

    private fun text(points: List<Pair<Q, Q>>) = points.joinToString(", ") { "(${decimalText(it.first)}, ${decimalText(it.second)})" }

    private fun coefficient(res: StatResult, symbol: String): Expr? = res.rows.firstOrNull { it.symbol == symbol }?.value

    private fun fits(r: Random): List<Task> {
        val out = ArrayList<Task>()
        // Polynomial fits: the normal equations (XᵀX)a = Xᵀy in fractions.
        repeat(40) {
            val n = r.int(3, 8)
            val pts = r.points(n, positive = false)
            val d = r.int(1, minOf(3, n - 1))
            val x = pts.map { p -> (0..d).map { k -> p.first.pow(k) } }
            val xt = QM.transpose(x)
            val normal = QM.times(xt, x).mapIndexed { i, row -> row + listOf(xt[i].indices.fold(Q.ZERO) { s, k -> s + xt[i][k] * pts[k].second }) }
            val a = QM.rref(normal).first.map { it[d + 1] }
            out += Task(AREA, "fitpoly(${text(pts)}, $d)", a.joinToString(", "), judge = { c ->
                val res = (c.outcome as? Outcome.Statistical)?.result
                res != null && (0..d).all { k -> coefficient(res, "a_$k")?.let { rationalOf(it) } == a[k] }
            })
        }
        // Exponential, power and logarithmic fits: least squares of the transformed data, in doubles.
        fun line(xs: List<Double>, ys: List<Double>): Pair<Double, Double> {
            val mx = xs.average(); val my = ys.average()
            val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.sumOf { (it - mx) * (it - mx) }
            return slope to my - slope * mx
        }
        repeat(30) {
            val pts = r.points(r.int(3, 7), positive = true)
            val xs = pts.map { it.first.toDouble() }; val ys = pts.map { it.second.toDouble() }
            val (k, la) = line(xs, ys.map { ln(it) })
            out += Task(AREA, "fitexp(${text(pts)})", "a = ${exp(la)}, k = $k", judge = { c ->
                val res = (c.outcome as? Outcome.Statistical)?.result
                res != null && coefficient(res, "a")?.let { close(valueOf(it), exp(la), 1e-9) } == true && coefficient(res, "k")?.let { close(valueOf(it), k, 1e-9, 1e-12) } == true
            })
            val (b, lp) = line(xs.map { ln(it) }, ys.map { ln(it) })
            out += Task(AREA, "fitpow(${text(pts)})", "a = ${exp(lp)}, b = $b", judge = { c ->
                val res = (c.outcome as? Outcome.Statistical)?.result
                res != null && coefficient(res, "a")?.let { close(valueOf(it), exp(lp), 1e-9) } == true && coefficient(res, "b")?.let { close(valueOf(it), b, 1e-9, 1e-12) } == true
            })
            val (bl, al) = line(xs.map { ln(it) }, ys)
            out += Task(AREA, "fitlog(${text(pts)})", "a = $al, b = $bl", judge = { c ->
                val res = (c.outcome as? Outcome.Statistical)?.result
                res != null && coefficient(res, "a")?.let { close(valueOf(it), al, 1e-9, 1e-9) } == true && coefficient(res, "b")?.let { close(valueOf(it), bl, 1e-9, 1e-12) } == true
            })
        }
        // Logistic fits: the sum of squared residuals is least there (its gradient by differences is 0, nearby points are worse).
        repeat(12) {
            val cap = r.int(50, 200).toDouble(); val k = r.int(5, 15) / 10.0; val mid = r.int(2, 4).toDouble()
            // Positive values, as a logistic curve has (data with values ≤ 0 the engine declines).
            val pts = (0..6).map { x -> x.toDouble() to maxOf(1L, Math.round(cap / (1 + exp(-k * (x - mid))) + r.int(-2, 2))).toDouble() }
            fun ss(v: DoubleArray) = pts.sumOf { (x, y) -> val d = y - v[0] / (1 + v[1] * exp(-v[2] * x)); d * d }
            out += Task(AREA, "fitlogistic(${pts.joinToString(", ") { "(${fmt(it.first)}, ${fmt(it.second)})" }})", "a minimum", judge = { c ->
                val res = (c.outcome as? Outcome.Statistical)?.result
                val t = res?.let { listOf("c", "a", "k").map { s -> coefficient(it, s)?.let(::valueOf) } }
                t != null && t.all { it != null && it.isFinite() } && t.map { it!! }.toDoubleArray().let { v ->
                    (0..2).all { j ->
                        val h = 1e-6 * maxOf(1e-3, abs(v[j]))
                        val up = v.copyOf().also { it[j] += h }; val down = v.copyOf().also { it[j] -= h }
                        abs((ss(up) - ss(down)) / (2 * h)) < 1e-3 * maxOf(1.0, ss(v)) && ss(v.copyOf().also { it[j] += 1e-3 * maxOf(1e-3, abs(v[j])) }) >= ss(v)
                    }
                }
            })
        }
        return out
    }

    private fun Random.chain(n: Int): List<List<Q>> = List(n) {
        val w = List(n) { int(0, 4).toLong() }.let { if (it.sum() == 0L) listOf(1L) + it.drop(1) else it }
        w.map { q(it, w.sum()) }
    }

    private fun chains(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val n = r.int(2, 4)
            // All entries positive now and then, so the chain has one stationary distribution.
            val p = if (i % 2 == 0) List(n) { val w = List(n) { r.int(1, 5).toLong() }; w.map { q(it, w.sum()) } } else r.chain(n)
            val t = QM.typedQ(p).replace("(", "").replace(")", "")
            // πP = π with Σπ = 1: (Pᵀ − I)π = 0 and the row of ones.
            val sys = (0 until n).map { j -> (0 until n).map { k -> p[k][j] - (if (j == k) Q.ONE else Q.ZERO) } + listOf(Q.ZERO) } + listOf(List(n) { Q.ONE } + listOf(Q.ONE))
            val (red, rank) = QM.rref(sys)
            val pi = if (rank == n && red.drop(n).all { row -> row.all { it.isZero } }) red.take(n).map { it[n] } else null
            out += if (pi == null) Task(AREA, "stationary($t)", "several", fails = setOf(Failure.MANY_SOLUTIONS)) { false }
            else Task(AREA, "stationary($t)", "$pi", judge = { c -> ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { QM.vector(it) } == pi })
            // v₀Pⁿ, exactly.
            val v0 = r.chain(n)[0]
            val steps = r.int(0, 8)
            var v = listOf(v0)
            repeat(steps) { v = QM.times(v, p) }
            out += Task(AREA, "markov($t, ${v0.joinToString(", ", "[", "]") { it.typed().removeSurrounding("(", ")") }}, $steps)", "${v[0]}", judge = { c ->
                ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { QM.vector(it) } == v[0]
            })
        }
        return out
    }

    private fun describe(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) {
            val xs = List(r.int(2, 12)) { q(r.int(-20, 40).toLong(), listOf(1L, 1L, 2L, 4L).random(r)) }
            val p = q(r.int(0, 20).toLong(), 20)
            // Hyndman and Fan's seventh: h = (n − 1)p, between the ⌊h⌋-th and the next of the sorted values.
            val s = xs.sorted()
            val h = q((s.size - 1).toLong()) * p
            val lo = (h.n / h.d).toInt()
            val want = if (lo + 1 >= s.size) s.last() else s[lo] + (h - q(lo.toLong())) * (s[lo + 1] - s[lo])
            out += Task(AREA, "quantile([${xs.joinToString(", ") { decimalText(it) }}], ${decimalText(p)})", "$want", judge = isExact(want))
            // Modes: the values most often there, where one is there more than once.
            val ints = List(r.int(3, 12)) { r.int(0, 6).toLong() }
            val counts = ints.groupingBy { it }.eachCount()
            val most = counts.values.max()
            val modes = if (most == 1) emptyList() else counts.filter { it.value == most }.keys.sorted()
            out += Task(AREA, "mode(${ints.joinToString(", ")})", "$modes", judge = { c ->
                (c.outcome as? Outcome.SetValue)?.elements?.map { rationalOf(it) }?.toSet() == modes.map { q(it) }.toSet()
            })
        }
        // Sample covariance and correlation of points.
        repeat(40) {
            val pts = List(r.int(3, 9)) { q(r.int(-9, 9).toLong()) to q(r.int(-9, 9).toLong()) }
            val n = q(pts.size.toLong())
            val mx = pts.fold(Q.ZERO) { a, p -> a + p.first } / n; val my = pts.fold(Q.ZERO) { a, p -> a + p.second } / n
            val sxy = pts.fold(Q.ZERO) { a, p -> a + (p.first - mx) * (p.second - my) }
            val sxx = pts.fold(Q.ZERO) { a, p -> a + (p.first - mx) * (p.first - mx) }
            val syy = pts.fold(Q.ZERO) { a, p -> a + (p.second - my) * (p.second - my) }
            val text = pts.joinToString(", ") { "(${it.first}, ${it.second})" }
            out += Task(AREA, "cov($text)", "", judge = isExact(sxy / (n - Q.ONE)))
            out += if (sxx.isZero || syy.isZero) Task(AREA, "corr($text)", "constant", fails = setOf(Failure.UNDEFINED)) { false }
            else Task(AREA, "corr($text)", "", judge = isNumber(sxy.toDouble() / sqrt(sxx.toDouble() * syy.toDouble())))
        }
        return out
    }

    fun all(): List<Task> {
        val r = Random(2718)
        return tables() + discrete(r) + fits(r) + chains(r) + describe(r)
    }
}
