package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Iterations and norms. The secant method's zero is one of the roots the polynomial was built from; a fixed point satisfies
 * g(x) = x and is the limit of the test's own iteration; Jacobi and Gauss–Seidel reach the solution of the test's own elimination
 * in fractions, and the dominance they report is the rows' own; norms of vectors and matrices exactly (the 2-norm of a 2 × 2 by
 * the eigenvalues of AᵀA in closed form), condition numbers from the test's own inverse; Gram–Schmidt and projections against the
 * test's own process and normal equations; Euler's and the Runge–Kutta method by their recurrences in doubles.
 */
internal object WrittenIterations {
    private const val AREA = "iterations"

    private fun iteration(c: Calculation) = (c.outcome as? Outcome.Iterated)?.iteration

    private fun table(c: Calculation) = (c.outcome as? Outcome.NumericTable)?.table

    private fun roots(r: Random): List<Task> {
        val out = ArrayList<Task>()
        // The secant method on (x − r₁)(x − r₂)(…): its zero is one of them (null: it did not converge, a decline).
        repeat(50) {
            val roots = List(r.int(1, 3)) { r.int(-5, 5) }.distinct()
            var p = listOf(Q.ONE)
            for (root in roots) p = QP.times(p, listOf(q(-root.toLong()), Q.ONE))
            val x0 = r.int(-6, 6).toDouble() + 0.5; val x1 = x0 + r.int(1, 3)
            out += Task(AREA, "secant(${QP.text(p)}, ${fmt(x0)}, ${fmt(x1)})", "one of $roots", judge = { c ->
                val v = table(c)?.result ?: throw Declined()
                roots.any { abs(v - it) <= 1e-9 * maxOf(1.0, abs(it.toDouble())) }
            })
        }
        // Fixed points: g(x) = x, the limit of x ← g(x) from the start computed here.
        class G(val text: String, val g: (Double) -> Double)
        val gs = ArrayList<G>()
        for (a in listOf(2, 3, 5, 7, 10)) gs += G("(x + $a/x)/2") { x -> (x + a / x) / 2 }
        for (a in listOf(1, 2, 3, 6)) gs += G("sqrt(x + $a)") { x -> sqrt(x + a) }
        for (a in listOf(2, 3, 5)) gs += G("cos(x)/$a + 1") { x -> cos(x) / a + 1 }
        for (a in listOf(2, 4)) gs += G("e^(-x)/$a") { x -> exp(-x) / a }
        gs += G("cos(x)") { x -> cos(x) }
        for (g in gs) for (start in listOf(1, 2)) {
            var x = start.toDouble()
            repeat(2000) { x = g.g(x) }
            out += Task(AREA, "fixedpoint(${g.text}, $start)", "$x", judge = { c ->
                val it = iteration(c)
                val v = it?.result?.single() ?: throw Declined()
                abs(v - x) <= 1e-9 * maxOf(1.0, abs(x)) && abs(g.g(v) - v) <= 1e-9 * maxOf(1.0, abs(v)) &&
                    (it.exact == null || abs(valueOf(it.exact!!.single()) - x) <= 1e-12 * maxOf(1.0, abs(x)))
            })
        }
        return out
    }

    private fun Random.dominant(n: Int): List<List<Long>> = List(n) { i ->
        val row = MutableList(n) { int(-3, 3).toLong() }
        row[i] = (row.sumOf { abs(it) } - abs(row[i]) + int(1, 4)) * (if (nextBoolean()) 1 else -1)
        row
    }

    private fun systems(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(50) { i ->
            val n = r.int(2, 4)
            val a = r.dominant(n)
            val b = List(n) { r.int(-9, 9).toLong() }
            val sys = QM.of(a).mapIndexed { k, row -> row + listOf(q(b[k])) }
            val x = QM.rref(sys).first.map { it[n] }
            val seidel = i % 2 == 1
            val start = if (i % 3 == 0) ", ${List(n) { r.int(-2, 2) }.joinToString(", ", "[", "]")}" else ""
            val call = (if (seidel) "gaussseidel" else "jacobimethod") + "(${QM.typed(a)}, ${b.joinToString(", ", "[", "]")}$start)"
            out += Task(AREA, call, "$x", judge = { c ->
                val it = iteration(c)
                val v = it?.result ?: throw Declined()
                v.size == n && v.indices.all { k -> abs(v[k] - x[k].toDouble()) <= 1e-8 * maxOf(1.0, abs(x[k].toDouble())) } &&
                    (it.exact == null || it.exact!!.map { e -> rationalOf(e) } == x) &&
                    it.tests.filterIsInstance<Iterative.Test.Dominance>().all { t -> t.holds }
            })
        }
        // Not dominant: the test says which row.
        out += Task(AREA, "jacobimethod([[1, 3], [2, 1]], [4, 3])", "not dominant in row 1", judge = { c ->
            iteration(c)?.tests?.filterIsInstance<Iterative.Test.Dominance>()?.singleOrNull()?.let { !it.holds && it.row == 0 } ?: throw Declined()
        })
        out += Task(AREA, "jacobimethod([[0, 1], [1, 0]], [1, 1])", "a zero on the diagonal", fails = setOf(Failure.ZERO_DIAGONAL)) { false }
        out += Task(AREA, "gaussseidel([[1, 2], [2, 4]], [1, 2])", "singular", fails = setOf(Failure.SINGULAR)) { false }
        return out
    }

    private fun norms(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) {
            val v = List(r.int(2, 4)) { r.int(-6, 6).toLong() }
            val t = v.joinToString(", ", "(", ")")
            out += Task(AREA, "norm($t)", "", judge = isNumber(sqrt(v.sumOf { it * it }.toDouble())))
            out += Task(AREA, "norm($t, 1)", "", judge = isExact(q(v.sumOf { abs(it) })))
            out += Task(AREA, "norm($t, inf)", "", judge = isExact(q(v.maxOf { abs(it) })))
            val p = r.int(3, 5)
            out += Task(AREA, "norm($t, $p)", "", judge = isNumber(Math.pow(v.sumOf { Math.pow(abs(it).toDouble(), p.toDouble()) }, 1.0 / p)))
        }
        repeat(40) { i ->
            val n = 2 + i % 2
            val m = List(n) { List(n) { r.int(-5, 5).toLong() } }
            val a = QM.of(m)
            val t = QM.typed(m)
            fun one(x: List<List<Q>>) = x[0].indices.maxOf { j -> x.fold(Q.ZERO) { s, row -> s + row[j].let { if (it < Q.ZERO) -it else it } } }
            fun inf(x: List<List<Q>>) = x.maxOf { row -> row.fold(Q.ZERO) { s, v -> s + if (v < Q.ZERO) -v else v } }
            fun frob(x: List<List<Q>>) = x.fold(Q.ZERO) { s, row -> row.fold(s) { u, v -> u + v * v } }
            out += Task(AREA, "norm($t, 1)", "", judge = isExact(one(a)))
            out += Task(AREA, "norm($t, inf)", "", judge = isExact(inf(a)))
            out += Task(AREA, "norm($t, F)", "", judge = isNumber(sqrt(frob(a).toDouble())))
            // The 2-norm of a 2 × 2: √ of the largest eigenvalue of AᵀA, (tr + √(tr² − 4 det))/2.
            val ata = QM.times(QM.transpose(a), a)
            if (n == 2) {
                val tr = (ata[0][0] + ata[1][1]).toDouble(); val det = QM.det(ata).toDouble()
                val big = (tr + sqrt(maxOf(0.0, tr * tr - 4 * det))) / 2
                out += Task(AREA, "norm($t)", "", judge = isNumber(sqrt(big), 1e-9))
                val small = (tr - sqrt(maxOf(0.0, tr * tr - 4 * det))) / 2
                if (!QM.det(a).isZero) out += Task(AREA, "cond($t)", "", judge = isNumber(sqrt(big / small), 1e-8))
            }
            if (QM.det(a).isZero) out += Task(AREA, "cond($t, 1)", "singular", fails = setOf(Failure.SINGULAR)) { false }
            else {
                val inv = QM.inverse(a)
                out += Task(AREA, "cond($t, 1)", "", judge = isExact(one(a) * one(inv)))
                out += Task(AREA, "cond($t, inf)", "", judge = isExact(inf(a) * inf(inv)))
                out += Task(AREA, "cond($t, F)", "", judge = isNumber(sqrt(frob(a).toDouble() * frob(inv).toDouble())))
            }
        }
        return out
    }

    private fun bases(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) {
            val n = r.int(2, 3)
            val k = r.int(1, n)
            val vs = List(k) { List(n) { r.int(-3, 3).toDouble() } }
            // Classical Gram–Schmidt here: each vector less its projections on the earlier ones, then of length 1; a vector that
            // comes to 0 is dependent and left out.
            val basis = ArrayList<DoubleArray>()
            val dependent = ArrayList<Int>()
            for ((j, v) in vs.withIndex()) {
                val w = v.toDoubleArray()
                for (e in basis) { val d = (0 until n).sumOf { w[it] * e[it] }; for (x in 0 until n) w[x] -= d * e[x] }
                val len = sqrt(w.sumOf { it * it })
                if (len < 1e-9) dependent += j else basis += DoubleArray(n) { w[it] / len }
            }
            val text = vs.joinToString(", ") { v -> v.joinToString(", ", "(", ")") { fmt(it) } }
            out += Task(AREA, "gramschmidt($text)", "${basis.size} vectors", judge = { c ->
                val b = (c.outcome as? Outcome.Orthonormalized)?.basis
                b != null && b.dependent == dependent && b.orthonormal.size == basis.size && b.orthonormal.zip(basis).all { (e, w) ->
                    val got = (if (e.rows.size == 1) e.rows[0] else e.rows.map { it[0] }).map { valueOf(it) }
                    got.size == n && got.indices.all { abs(got[it] - w[it]) < 1e-9 }
                }
            })
            // The projection of a vector onto their span: by the normal equations (VᵀV)c = Vᵀu in fractions.
            val u = List(n) { r.int(-4, 4).toLong() }
            val cols = vs.map { v -> v.map { q(it.toLong()) } }
            val gram = cols.map { a -> cols.map { b -> a.indices.fold(Q.ZERO) { s, x -> s + a[x] * b[x] } } }
            val rhs = cols.map { a -> a.indices.fold(Q.ZERO) { s, x -> s + a[x] * q(u[x]) } }
            val (red, rank) = QM.rref(gram.mapIndexed { x, row -> row + listOf(rhs[x]) })
            if (cols.all { col -> col.all { it.isZero } }) return@repeat
            // A dependent set: the projection onto the span of the independent ones.
            val coefficient = if (rank == k) red.map { it[k] } else null
            if (coefficient != null) {
                val proj = (0 until n).map { x -> cols.indices.fold(Q.ZERO) { s, j -> s + coefficient[j] * cols[j][x] } }
                out += Task(AREA, "projection(${u.joinToString(", ", "(", ")")}, $text)", "$proj", judge = { c ->
                    ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { QM.vector(it) } == proj
                })
            }
        }
        return out
    }

    private fun odes(r: Random): List<Task> {
        val out = ArrayList<Task>()
        class F(val text: String, val f: (Double, Double) -> Double)
        val fs = listOf(F("x + y") { x, y -> x + y }, F("x y") { x, y -> x * y }, F("-2y") { _, y -> -2 * y }, F("y - x^2") { x, y -> y - x * x },
            F("x - y") { x, y -> x - y }, F("cos(x) - y") { x, y -> cos(x) - y }, F("y^2/4") { _, y -> y * y / 4 }, F("3x^2") { x, _ -> 3 * x * x })
        repeat(60) { i ->
            val f = fs.random(r)
            val x0 = r.int(-1, 1); val y0 = r.int(-2, 2)
            val (hText, h) = listOf("0.1" to 0.1, "0.25" to 0.25, "0.5" to 0.5, "0.2" to 0.2, "-0.25" to -0.25).random(r)
            val steps = r.int(1, 8)
            val b = x0 + h * steps
            val rk = i % 2 == 1
            var x = x0.toDouble(); var y = y0.toDouble()
            repeat(steps) {
                y = if (!rk) y + h * f.f(x, y) else {
                    val k1 = f.f(x, y); val k2 = f.f(x + h / 2, y + h / 2 * k1); val k3 = f.f(x + h / 2, y + h / 2 * k2); val k4 = f.f(x + h, y + h * k3)
                    y + h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
                }
                x += h
            }
            val call = "${if (rk) "rungekutta" else "euler"}(y' = ${f.text}, y($x0) = $y0, ${fmt(Math.round(b * 1e6) / 1e6)}, $hText)"
            out += Task(AREA, call, "$y", judge = { c -> table(c)?.let { t -> t.rows.size == steps + 1 && t.result?.let { close(it, y, 1e-10, 1e-12) } == true } == true })
        }
        out += Task(AREA, "euler(y' = y, y(0) = 1, 1, 0.3)", "the step does not reach 1", fails = setOf(Failure.ARGUMENTS)) { false }
        return out
    }

    fun all(): List<Task> {
        val r = Random(9090)
        return roots(r) + systems(r) + norms(r) + bases(r) + odes(r)
    }
}
