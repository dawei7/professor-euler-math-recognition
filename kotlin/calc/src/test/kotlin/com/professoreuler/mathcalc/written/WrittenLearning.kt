package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Machine learning: activations and information measures by their formulas in doubles (the softmax and log-sum-exp shifted by
 * the largest value, GELU by the test's own erf), gradient descent by its recurrence with the test's own gradient, and Markov
 * decision processes by brute force: every deterministic policy evaluated in fractions, (I − γP_π)v = r_π by the test's own
 * elimination, the optimal value the best of them in every state and the optimal actions those that reach it.
 */
internal object WrittenLearning {
    private const val AREA = "machine learning"

    private val NUMBERS = listOf(q(-3), q(-2), q(-3, 2), q(-1), q(-1, 2), q(-1, 4), q(0), q(1, 4), q(1, 2), q(1), q(3, 2), q(2), q(5, 2), q(3))

    private fun sigma(x: Double) = if (x >= 0) 1 / (1 + exp(-x)) else exp(x) / (1 + exp(x))

    private fun activations(r: Random): List<Task> {
        val out = ArrayList<Task>()
        for (v in NUMBERS) {
            val x = v.toDouble()
            val t = v.typed()
            out += Task(AREA, "sigmoid($t)", "", judge = if (v.isZero) isExact(q(1, 2)) else isNumber(sigma(x)))
            out += Task(AREA, "relu($t)", "", judge = isExact(if (v < Q.ZERO) Q.ZERO else v))
            out += Task(AREA, "leakyrelu($t)", "", judge = isExact(if (v < Q.ZERO) v * q(1, 100) else v))
            out += Task(AREA, "leakyrelu($t, 1/5)", "", judge = isExact(if (v < Q.ZERO) v * q(1, 5) else v))
            out += Task(AREA, "softplus($t)", "", judge = isNumber(if (x > 0) x + Math.log1p(exp(-x)) else Math.log1p(exp(x))))
            out += Task(AREA, "gelu($t)", "", judge = if (v.isZero) isNumber(0.0) else isNumber(x / 2 * (1 + WrittenNumbers.erf(x / sqrt(2.0))), 1e-9))
        }
        // Element by element on a vector.
        repeat(20) {
            val v = List(r.int(2, 4)) { NUMBERS.random(r) }
            out += Task(AREA, "relu([${v.joinToString(", ") { it.typed() }}])", "", judge = { c ->
                ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { QM.vector(it) } == v.map { if (it < Q.ZERO) Q.ZERO else it }
            })
            out += Task(AREA, "sigmoid([${v.joinToString(", ") { it.typed() }}])", "", judge = { c ->
                ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { m ->
                    val got = (if (m.rows.size == 1) m.rows[0] else m.rows.map { it[0] }).map { valueOf(it) }
                    got.size == v.size && got.zip(v).all { (g, w) -> close(g, sigma(w.toDouble())) }
                } == true
            })
        }
        // Softmax and log-sum-exp, shifted by the largest value; large values too.
        repeat(40) { i ->
            val shift = if (i % 4 == 3) r.int(100, 900) else 0
            val z = List(r.int(2, 5)) { r.int(-4, 4) + shift }
            val top = z.max()
            val s = z.sumOf { exp((it - top).toDouble()) }
            val text = z.joinToString(", ", "[", "]")
            out += Task(AREA, "softmax($text)", "", judge = { c ->
                ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { m ->
                    val got = (if (m.rows.size == 1) m.rows[0] else m.rows.map { it[0] }).map { valueOf(it) }
                    got.size == z.size && got.zip(z).all { (g, w) -> close(g, exp((w - top).toDouble()) / s, 1e-10) }
                } == true
            })
            out += Task(AREA, "logsumexp($text)", "", judge = isNumber(top + ln(s)))
        }
        // Softmax of a matrix: row by row.
        repeat(10) {
            val m = List(2) { List(3) { r.int(-3, 3) } }
            out += Task(AREA, "softmax(${m.joinToString(", ", "[", "]") { it.joinToString(", ", "[", "]") }})", "rows", judge = { c ->
                ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { e ->
                    e.rows.size == 2 && e.rows.indices.all { i ->
                        val s = m[i].sumOf { exp(it.toDouble()) }
                        e.rows[i].size == 3 && e.rows[i].indices.all { j -> close(valueOf(e.rows[i][j]), exp(m[i][j].toDouble()) / s) }
                    }
                } == true
            })
        }
        // Cosine similarity of whole vectors: u·v/(|u||v|).
        repeat(30) {
            val n = r.int(2, 4)
            val u = List(n) { r.int(-5, 5) }; val v = List(n) { r.int(-5, 5) }
            val nu = sqrt(u.sumOf { it * it }.toDouble()); val nv = sqrt(v.sumOf { it * it }.toDouble())
            val call = "cosinesimilarity(${u.joinToString(", ", "[", "]")}, ${v.joinToString(", ", "[", "]")})"
            out += if (nu == 0.0 || nv == 0.0) Task(AREA, call, "a zero vector", fails = setOf(Failure.DIVISION_BY_ZERO)) { false }
            else Task(AREA, call, "", judge = isNumber(u.indices.sumOf { u[it] * v[it] } / (nu * nv)))
        }
        return out
    }

    /** A random distribution of [n] fractions (some 0 where [zeros]). */
    private fun Random.distribution(n: Int, zeros: Boolean = false): List<Q> {
        val w = List(n) { if (zeros && nextInt(4) == 0) 0L else int(1, 9).toLong() }.let { if (it.all { v -> v == 0L }) listOf(1L) + it.drop(1) else it }
        val total = w.sum()
        return w.map { q(it, total) }
    }

    private fun text(p: List<Q>) = p.joinToString(", ", "[", "]") { it.typed().removeSurrounding("(", ")") }

    private fun information(r: Random): List<Task> {
        val out = ArrayList<Task>()
        fun h(p: List<Q>) = -p.sumOf { v -> v.toDouble().let { if (it == 0.0) 0.0 else it * ln(it) } }
        repeat(50) { i ->
            val n = r.int(2, 5)
            val p = r.distribution(n, zeros = i % 3 == 0)
            val qd = r.distribution(n)
            val base = listOf(null, 2, 10, 3).random(r)
            val unit = base?.let { ln(it.toDouble()) } ?: 1.0
            val b = base?.let { ", $it" } ?: ""
            out += Task(AREA, "entropy(${text(p)}$b)", "", judge = isNumber(h(p) / unit))
            out += Task(AREA, "crossentropy(${text(p)}, ${text(qd)}$b)", "",
                judge = isNumber(-p.indices.sumOf { k -> if (p[k].isZero) 0.0 else p[k].toDouble() * ln(qd[k].toDouble()) } / unit))
            val kl = p.indices.sumOf { k -> if (p[k].isZero) 0.0 else p[k].toDouble() * ln(p[k].toDouble() / qd[k].toDouble()) } / unit
            out += Task(AREA, "kldivergence(${text(p)}, ${text(qd)}$b)", "", judge = isNumber(kl, 1e-9))
        }
        // Mutual information of a joint table: Σ pᵢⱼ ln(pᵢⱼ/(pᵢ·pⱼ)); a product table has none.
        repeat(30) { i ->
            val rows = r.int(2, 3); val cols = r.int(2, 3)
            val joint = if (i % 5 == 0) {
                val a = r.distribution(rows); val b = r.distribution(cols)
                List(rows) { x -> List(cols) { y -> a[x] * b[y] } }
            } else r.distribution(rows * cols, zeros = i % 3 == 0).chunked(cols)
            val pr = joint.map { row -> row.fold(Q.ZERO) { s, v -> s + v } }
            val pc = (0 until cols).map { y -> joint.fold(Q.ZERO) { s, row -> s + row[y] } }
            val mi = joint.indices.sumOf { x -> (0 until cols).sumOf { y ->
                val v = joint[x][y].toDouble()
                if (v == 0.0) 0.0 else v * ln(v / (pr[x].toDouble() * pc[y].toDouble()))
            } }
            // A product table (independence) has none, exactly.
            out += Task(AREA, "mutualinformation(${joint.joinToString(", ", "[", "]") { text(it) }})", "", judge = if (i % 5 == 0) isExact(Q.ZERO) else isNumber(mi, 1e-9))
        }
        // Not distributions; a zero of q where p is not 0.
        out += Task(AREA, "entropy([0.5, 0.6])", "", fails = setOf(Failure.NOT_DISTRIBUTION)) { false }
        out += Task(AREA, "kldivergence([1/2, 1/2], [1, 0])", "∞", fails = setOf(Failure.DIVERGES)) { false }
        out += Task(AREA, "crossentropy([1/4, 3/4], [0, 1])", "∞", fails = setOf(Failure.DIVERGES)) { false }
        return out
    }

    /** Gradient descent on a quadratic a·x² + b·xy + c·y² + d·x + e·y: its recurrence here, with the gradient by hand. */
    private fun descent(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val two = i % 2 == 0
            val a = r.int(1, 4); val c = if (two) r.int(1, 4) else 0
            val b = if (two) r.int(-1, 1) else 0
            val d = r.int(-6, 6); val e = if (two) r.int(-6, 6) else 0
            val eta = listOf(q(1, 20), q(1, 10), q(1, 8), q(1, 16)).random(r)
            val steps = r.int(5, 60)
            val x0 = r.int(-3, 3).toDouble(); val y0 = r.int(-3, 3).toDouble()
            var x = x0; var y = y0
            val h = eta.toDouble()
            repeat(steps) {
                val gx = 2 * a * x + b * y + d
                val gy = b * x + 2 * c * y + e
                x -= h * gx; y -= h * gy
            }
            val f = if (two) listOf(a.toLong() to "x^2", b.toLong() to "x y", c.toLong() to "y^2", d.toLong() to "x", e.toLong() to "y")
                else listOf(a.toLong() to "x^2", d.toLong() to "x")
            val ft = f.filter { it.first != 0L }.mapIndexed { j, (k, body) -> term(k, body, first = j == 0) }.joinToString("")
            val start = if (two) "[${fmt(x0)}, ${fmt(y0)}]" else fmt(x0)
            val call = "gradientdescent($ft, $start, ${decimalText(eta)}, $steps)"
            // The minimum of a convex quadratic: where the gradient is 0 (2a·x + b·y = −d, b·x + 2c·y = −e).
            val min = if (two) QM.rref(listOf(listOf(q(2L * a), q(b.toLong()), q(-d.toLong())), listOf(q(b.toLong()), q(2L * c), q(-e.toLong())))).first.map { it[2] }
                else listOf(q(-d.toLong(), 2L * a))
            out += Task(AREA, call, "(${fmt(x)}, ${fmt(y)})", judge = { calc ->
                val o = calc.outcome as? Outcome.Descent
                val p = o?.point
                p != null && close(p[0], x, 1e-9, 1e-9) && (!two || close(p[1], y, 1e-9, 1e-9)) &&
                    (o.minimum == null || o.letters.indices.all { j -> rationalOf(o.minimum!!.at.getValue(o.letters[j])) == min[j] })
            })
        }
        return out
    }

    /** v = (I − γP_π)⁻¹ r_π for a deterministic policy, in fractions. */
    private fun evaluate(p: List<List<List<Q>>>, rw: List<List<Q>>, gamma: Q, policy: List<Int>): List<Q> {
        val n = rw.size
        val m = List(n) { s -> List(n) { t -> (if (s == t) Q.ONE else Q.ZERO) - gamma * p[policy[s]][s][t] } + listOf(rw[s][policy[s]]) }
        return QM.rref(m).first.map { it[n] }
    }

    private fun Random.row(n: Int): List<Q> {
        val w = List(n) { int(0, 3).toLong() }.let { if (it.all { v -> v == 0L }) listOf(1L) + it.drop(1) else it }
        return w.map { q(it, w.sum()) }
    }

    private fun decisions(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val n = r.int(2, 3)
            val actions = 2
            val p = List(actions) { List(n) { r.row(n) } }
            val rw = List(n) { List(actions) { q(r.int(-2, 6).toLong()) } }
            val gamma = listOf(q(1, 2), q(9, 10), q(3, 4), q(0), q(4, 5)).random(r)
            // Every deterministic policy: v* is the best value in each state, reached by one of them.
            val policies = (0 until Math.pow(actions.toDouble(), n.toDouble()).toInt()).map { k -> List(n) { s -> (k shr s) and 1 } }
            val values = policies.map { evaluate(p, rw, gamma, it) }
            val best = List(n) { s -> values.maxOf { it[s] } }
            if (values.none { v -> v == best }) return@repeat
            // q*(s, a) = r(s, a) + γ·Σ P_a(s, t)·v*(t): the optimal actions reach v*(s).
            val optimal = List(n) { s -> (0 until actions).filter { a -> rw[s][a] + gamma * (0 until n).fold(Q.ZERO) { acc, t -> acc + p[a][s][t] * best[t] } == best[s] }.map { it + 1 } }
            val call = "valueiteration(${p.joinToString(", ") { pm -> pm.joinToString(", ", "[", "]") { text(it) } }}, ${rw.joinToString(", ", "[", "]") { text(it) }}, ${gamma.typed().removeSurrounding("(", ")")})"
            out += Task(AREA, call, "v* = ${best.joinToString(", ")}", judge = { c ->
                val o = c.outcome as? Outcome.DecisionProcess
                o != null && o.values.map { rationalOf(it) } == best && o.policy.map { it.sorted() } == optimal
            })
            // A random policy evaluated: v = r_π + γP_πv with the probabilities of π.
            val pi = List(n) { r.row(actions) }
            val m = List(n) { s -> List(n) { t -> (if (s == t) Q.ONE else Q.ZERO) - gamma * (0 until actions).fold(Q.ZERO) { acc, a -> acc + pi[s][a] * p[a][s][t] } } +
                listOf((0 until actions).fold(Q.ZERO) { acc, a -> acc + pi[s][a] * rw[s][a] }) }
            val v = QM.rref(m).first.map { it[n] }
            out += Task(AREA, call.replace("valueiteration(", "policyevaluation(").dropLast(1) + ", ${pi.joinToString(", ", "[", "]") { text(it) }})", "$v", judge = { c ->
                ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { QM.vector(it) } == v
            })
            if (i == 0) {
                out += Task(AREA, call.replace(gamma.typed().removeSurrounding("(", ")") + ")", "1)"), "γ = 1", fails = setOf(Failure.UNDEFINED)) { false }
            }
        }
        return out
    }

    fun all(): List<Task> {
        val r = Random(4242)
        return activations(r) + information(r) + descent(r) + decisions(r)
    }
}
