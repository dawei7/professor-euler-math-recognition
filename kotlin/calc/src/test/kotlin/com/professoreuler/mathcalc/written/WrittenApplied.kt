package com.professoreuler.mathcalc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The applied functions. Matrix facts in the test's own fractions (rank and kernel by its own elimination, the characteristic
 * polynomial as det(λI − A) at points, the adjugate from cofactors, rank cases of matrices built with known determinants); lines,
 * lengths, areas, volumes and means of polynomials exactly (the test's own antiderivatives in fractions) or by its quadrature;
 * linear programs by enumerating every corner of the feasible region in fractions.
 */
internal object WrittenApplied {
    private const val AREA = "applied"

    private fun Random.matrix(rows: Int, cols: Int, lo: Int = -4, hi: Int = 4) = List(rows) { List(cols) { int(lo, hi).toLong() } }

    private fun matrices(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(70) { i ->
            val rows = 2 + i % 3
            val cols = 2 + (i / 3) % 3
            var m = r.matrix(rows, cols)
            // Some with a row that is a combination of others, so the rank drops.
            if (i % 2 == 0) m = m.dropLast(1) + listOf(m[0].indices.map { j -> 2 * m[0][j] - (if (rows > 2) m[1][j] else 0) })
            val a = QM.of(m)
            val rank = QM.rank(a)
            val t = QM.typed(m)
            out += Task(AREA, "rank($t)", "$rank", judge = isExact(q(rank.toLong())))
            out += Task(AREA, "nullspace($t)", "dim ${cols - rank}", judge = { c ->
                val o = c.outcome as? Outcome.Basis
                val vs = o?.vectors?.map { QM.vector(it) }
                o != null && o.nullSpace && o.dimension == cols - rank && vs != null && vs.all { it != null && it.size == cols } && vs.size == cols - rank &&
                    vs.all { v -> a.all { row -> row.indices.fold(Q.ZERO) { s, j -> s + row[j] * v!![j] }.isZero } } &&
                    (vs.isEmpty() || QM.rank(vs.map { it!! }) == vs.size)
            })
            out += Task(AREA, "columnspace($t)", "dim $rank", judge = { c ->
                val o = c.outcome as? Outcome.Basis
                val vs = o?.vectors?.map { QM.vector(it) }
                o != null && !o.nullSpace && o.dimension == rank && vs != null && vs.size == rank && vs.all { it != null && it.size == rows } &&
                    // Each in the span of the columns, and independent.
                    vs.all { v -> QM.rank(a.indices.map { k -> a[k] + listOf(v!![k]) }) == rank } && (vs.isEmpty() || QM.rank(vs.map { it!! }) == vs.size)
            })
        }
        repeat(60) { i ->
            val n = 2 + i % 3
            val m = r.matrix(n, n)
            val a = QM.of(m)
            val t = QM.typed(m)
            out += Task(AREA, "trace($t)", "", judge = isExact(a.indices.fold(Q.ZERO) { s, k -> s + a[k][k] }))
            // det(λI − A) at λ = −1 … n + 1, the characteristic polynomial's values.
            out += Task(AREA, if (i % 2 == 0) "charpoly($t)" else "charpoly($t, x)", "det(λI − A)", judge = { c ->
                val p = (c.outcome as? Outcome.Expression)?.result
                val letter = if (i % 2 == 0) "λ" else "x"
                p != null && p.symbols().all { it == letter } && (-1..n + 1).all { v ->
                    val shifted = a.indices.map { x -> a.indices.map { y -> (if (x == y) q(v.toLong()) else Q.ZERO) - a[x][y] } }
                    exactAt(p, mapOf(letter to q(v.toLong()))) == QM.det(shifted)
                }
            })
            // adj(A)ᵢⱼ = (−1)^(i+j)·det of the minor without row j and column i.
            val adj = a.indices.map { x -> a.indices.map { y -> QM.det(QM.minor(a, y, x)) * (if ((x + y) % 2 == 0) Q.ONE else -Q.ONE) } }
            out += Task(AREA, "adjugate($t)", "", judge = { c -> ((c.outcome as? Outcome.Expression)?.result as? Expr.Mat)?.let { QM.read(it) } == adj })
        }
        // Rank cases: [[a, k], [k, b]] with ab = m² (det ab − k², rank 1 at k = ±m), and [[k, c, c], [c, k, c], [c, c, k]] with
        // det (k − c)²(k + 2c): rank 1 at k = c, rank 2 at k = −2c.
        for (mm in 1..6) for ((x, y) in listOf(1L to 1L, 1L to 4L, 2L to 2L, 4L to 1L).filter { (mm * mm).toLong() % it.first == 0L }.map { it.first to mm.toLong() * mm / it.first }) {
            val special = mapOf(q(mm.toLong()) to 1, q(-mm.toLong()) to 1)
            out += Task(AREA, "rank([[$x, k], [k, $y]])", "rank 2, k = ±$mm: rank 1", judge = rankCases(2, special) { k -> listOf(listOf(q(x), k), listOf(k, q(y))) })
        }
        for (cc in listOf(1, 2, 3, -1, -2, 5)) {
            val c = q(cc.toLong())
            val special = mapOf(c to 1, q(-2L * cc) to 2)
            out += Task(AREA, "rank([[k, $cc, $cc], [$cc, k, $cc], [$cc, $cc, k]])", "rank 3, k = $cc: 1, k = ${-2 * cc}: 2",
                judge = rankCases(3, special) { k -> listOf(listOf(k, c, c), listOf(c, k, c), listOf(c, c, k)) })
        }
        for ((call, why) in listOf("trace([[1, 2, 3], [4, 5, 6]])" to Failure.SIZES, "charpoly([[1, 2], [3, 4], [5, 6]])" to Failure.SIZES,
                "rank(5)" to Failure.ARGUMENTS, "adjugate([[1, 2, 3]])" to Failure.SIZES)) {
            out += Task(AREA, call, why.name, fails = setOf(why)) { false }
        }
        return out
    }

    /** A rank that depends on k: [generic] elsewhere, the [special] ranks at their values (all of them), each checked here. */
    private fun rankCases(generic: Int, special: Map<Q, Int>, matrix: (Q) -> List<List<Q>>): (Calculation) -> Boolean = { c ->
        val o = c.outcome as? Outcome.RankCases
        o != null && o.generic == generic && o.undefined.isEmpty() &&
            o.special.map { (v, k) -> rationalOf(v) to k }.toMap() == special && special.all { (v, k) -> QM.rank(matrix(v)) == k } &&
            listOf(q(7, 3), q(-5, 2), q(11)).all { QM.rank(matrix(it)) == generic }
    }

    /** f as typed, its value and its derivative (the test's own, by hand). */
    private class Fn(val text: String, val f: (Double) -> Double, val d: (Double) -> Double, val lo: Double = -3.0, val hi: Double = 3.0)

    private val FUNCTIONS = listOf(
        Fn("sin(x)", ::sin, ::cos), Fn("cos(x)", ::cos, { -sin(it) }), Fn("e^x", ::exp, ::exp), Fn("e^(-x)", { exp(-it) }, { -exp(-it) }),
        Fn("ln(x)", ::ln, { 1 / it }, 0.2, 5.0), Fn("sqrt(x)", ::sqrt, { 0.5 / sqrt(it) }, 0.2, 9.0), Fn("1/x", { 1 / it }, { -1 / (it * it) }, 0.3, 4.0),
        Fn("x e^x", { it * exp(it) }, { (1 + it) * exp(it) }), Fn("x^2 sin(x)", { it * it * sin(it) }, { 2 * it * sin(it) + it * it * cos(it) }),
        Fn("arctan(x)", { kotlin.math.atan(it) }, { 1 / (1 + it * it) }), Fn("cosh(x)", { kotlin.math.cosh(it) }, { kotlin.math.sinh(it) }, -2.0, 2.0),
    )

    private fun Random.poly(degree: Int): List<Q> = List(degree + 1) { k -> q(int(-4, 4, nonzero = k == degree).toLong()) }

    private fun calculus(r: Random): List<Task> {
        val out = ArrayList<Task>()
        val values = listOf(q(-2), q(-1), q(0), q(1), q(2), q(3), q(1, 2), q(-3, 2), q(5, 3))
        // Tangent and normal lines of polynomials, exactly: y = f(a) + f′(a)(x − a); the normal y = f(a) − (x − a)/f′(a), or x = a.
        repeat(80) { i ->
            val p = r.poly(r.int(1, 4))
            val a = values.random(r)
            val fa = QP.at(p, a)
            val s = QP.at(QP.derivative(p), a)
            val normal = i % 2 == 1
            val call = (if (normal) "normalline" else "tangentline") + "(${QP.text(p)}, x, ${a.typed()})"
            out += Task(AREA, call, "slope $s at ($a, $fa)", judge = { c ->
                val o = c.outcome as? Outcome.TangentLine
                o != null && rationalOf(o.at) == a && rationalOf(o.value) == fa && rationalOf(o.slope) == s && when {
                    normal && s.isZero -> o.line.first == "x" && rationalOf(o.line.second) == a
                    else -> o.line.first == "y" && QPOINTS.all { x ->
                        val want = if (normal) fa - (x - a) / s else fa + s * (x - a)
                        exactAt(o.line.second, mapOf("x" to x)) == want
                    }
                }
            })
        }
        // Of other functions, at points of their domain: compared in doubles at points.
        for (f in FUNCTIONS) for (k in 0 until 4) {
            val a = f.lo + (f.hi - f.lo) * (k + 0.5) / 4
            val at = Math.round(a * 4) / 4.0
            if (at <= f.lo || at >= f.hi) continue
            val text = fmt(at)
            val s = f.d(at)
            out += Task(AREA, "tangentline(${f.text}, x, $text)", "slope $s", judge = { c ->
                val o = c.outcome as? Outcome.TangentLine
                o != null && close(valueOf(o.slope), s, 1e-10, 1e-12) && close(valueOf(o.value), f.f(at), 1e-10, 1e-12) &&
                    agrees(o.line.second, listOf("x"), points1()) { (x) -> f.f(at) + s * (x - at) }
            })
        }
        // Arc length: ∫ₐᵇ √(1 + f′²) by the test's quadrature.
        repeat(40) {
            val p = r.poly(r.int(1, 3))
            val a = r.int(-2, 1); val b = a + r.int(1, 3)
            val d = QP.derivative(p)
            out += Task(AREA, "arclength(${QP.text(p)}, x, $a, $b)", "", judge = isNumber(integrate(a.toDouble(), b.toDouble()) { x -> sqrt(1 + QP.at(d, x).let { it * it }) }, 1e-9))
        }
        for (f in FUNCTIONS.take(8)) {
            val a = maxOf(f.lo, 0.5); val b = minOf(f.hi, 2.5)
            out += Task(AREA, "arclength(${f.text}, x, ${fmt(a)}, ${fmt(b)})", "", judge = isNumber(integrate(a, b) { x -> sqrt(1 + f.d(x) * f.d(x)) }, 1e-9))
        }
        // The area between two curves: f − g = k(x − p)(x − q)(…) with chosen whole roots, exactly between them.
        repeat(60) { i ->
            val k = r.int(-3, 3, nonzero = true)
            val roots = (if (i % 3 == 0) List(3) { r.int(-4, 4) } else List(2) { r.int(-4, 4) }).distinct().sorted()
            if (roots.size < 2) return@repeat
            var diff = listOf(q(k.toLong()))
            for (root in roots) diff = QP.times(diff, listOf(q(-root.toLong()), Q.ONE))
            val g = r.poly(r.int(0, 2))
            val f = QP.plus(g, diff)
            val area = roots.zipWithNext().fold(Q.ZERO) { s, (lo, hi) -> QP.integral(diff, q(lo.toLong()), q(hi.toLong())).let { s + if (it < Q.ZERO) -it else it } }
            out += Task(AREA, "areabetween(${QP.text(f)}, ${QP.text(g)})", "$area", judge = isExact(area))
        }
        // With bounds: ∫ₐᵇ |f − g|, the test's quadrature between the zeros of f − g.
        repeat(40) {
            val f = r.poly(r.int(1, 3)); val g = r.poly(r.int(0, 1))
            val a = r.int(-3, 0); val b = a + r.int(1, 4)
            val diff = QP.plus(f, QP.scale(g, -Q.ONE))
            val cuts = listOf(a.toDouble()) + zeros(a.toDouble(), b.toDouble()) { QP.at(diff, it) } + b.toDouble()
            val area = cuts.zipWithNext().sumOf { (lo, hi) -> abs(integrate(lo, hi) { QP.at(diff, it) }) }
            out += Task(AREA, "areabetween(${QP.text(f)}, ${QP.text(g)}, $a, $b)", "", judge = isNumber(area, 1e-9))
        }
        // Volumes of revolution and means, exactly: π∫f², 2π∫x·f (f ≥ 0 on [a, b] with 0 ≤ a), (1/(b − a))∫f.
        repeat(50) {
            val p = r.poly(r.int(0, 3))
            val a = r.int(0, 2); val b = a + r.int(1, 3)
            val pi = PI
            out += Task(AREA, "volumex(${QP.text(p)}, x, $a, $b)", "", judge = isNumber(pi * QP.integral(QP.times(p, p), q(a.toLong()), q(b.toLong())).toDouble()))
            // Shells: 2π∫ x|f(x)| dx, the quadrature between the zeros of f.
            val cuts = listOf(a.toDouble()) + zeros(a.toDouble(), b.toDouble()) { QP.at(p, it) } + b.toDouble()
            val shells = 2 * pi * cuts.zipWithNext().sumOf { (lo, hi) -> abs(integrate(lo, hi) { x -> x * QP.at(p, x) }) }
            out += Task(AREA, "volumey(${QP.text(p)}, x, $a, $b)", "", judge = isNumber(shells, 1e-9))
            val c = r.int(-3, 0); val d = c + r.int(1, 4)
            out += Task(AREA, "meanvalue(${QP.text(p)}, x, $c, $d)", "", judge = isExact(QP.integral(p, q(c.toLong()), q(d.toLong())) / q((d - c).toLong())))
        }
        for (f in FUNCTIONS.take(8)) {
            val a = maxOf(f.lo, 0.5); val b = minOf(f.hi, 2.5)
            out += Task(AREA, "meanvalue(${f.text}, x, ${fmt(a)}, ${fmt(b)})", "", judge = isNumber(integrate(a, b, 1e-13, f.f) / (b - a), 1e-9))
            out += Task(AREA, "volumex(${f.text}, x, ${fmt(a)}, ${fmt(b)})", "", judge = isNumber(PI * integrate(a, b) { x -> f.f(x) * f.f(x) }, 1e-9))
        }
        return out
    }

    /** Linear programs: the optimum over every corner of the feasible region (each pair or triple of boundaries meeting), in fractions. */
    private fun programs(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(80) { i ->
            val n = if (i % 3 == 0) 3 else 2
            val letters = listOf("x", "y", "z").take(n)
            val maximize = i % 2 == 0
            // Rows a·v ≤ b with positive a (bounded with v ≥ 0) for a maximum; a·v ≥ b with positive a for a minimum.
            val m = r.int(2, 3)
            val rows = List(m) { List(n) { r.int(1, 5).toLong() } to r.int(4, 30).toLong() }
            val c = List(n) { r.int(if (maximize) -2 else 1, 6).toLong() }
            val rel = if (maximize) "<=" else ">="
            val all = rows.map { (a, b) -> a.map { q(it) } to q(b) } + letters.indices.map { j -> List(n) { k -> if (k == j) -Q.ONE else Q.ZERO } to Q.ZERO }
            // a·v ≤ b for every row of all (the ≥ rows turned around).
            val le = all.mapIndexed { k, (a, b) -> if (!maximize && k < m) a.map { -it } to -b else a to b }
            val corners = ArrayList<List<Q>>()
            fun choose(start: Int, picked: List<Int>) {
                if (picked.size == n) {
                    val sys = picked.map { le[it].first + le[it].second }
                    val (red, rank) = QM.rref(sys)
                    if (rank == n) corners += red.map { it[n] }
                    return
                }
                for (k in start until le.size) choose(k + 1, picked + k)
            }
            choose(0, emptyList())
            val feasible = corners.filter { v -> le.all { (a, b) -> a.indices.fold(Q.ZERO) { s, j -> s + a[j] * v[j] } <= b } }
            if (feasible.isEmpty()) return@repeat
            fun objective(v: List<Q>) = c.indices.fold(Q.ZERO) { s, j -> s + q(c[j]) * v[j] }
            val best = feasible.map(::objective).let { if (maximize) it.max() else it.min() }
            fun lin(a: List<Long>) = a.mapIndexed { j, k -> term(k, letters[j], first = j == 0 || a.take(j).all { it == 0L }) }.joinToString("").ifEmpty { "0" }
            val call = (if (maximize) "maximize(" else "minimize(") + lin(c) + ", " + rows.joinToString(", ") { (a, b) -> "${lin(a)} $rel $b" } + ", " +
                letters.joinToString(", ") { "$it >= 0" } + ")"
            out += Task(AREA, call, "${if (maximize) "max" else "min"} $best", judge = { calc ->
                val p = (calc.outcome as? Outcome.Optimized)?.program
                p != null && p.status == LinearProgram.Status.OPTIMAL && p.value?.let { rationalOf(it) } == best && p.point?.let { pt ->
                    val v = pt.map { rationalOf(it) }
                    v.all { it != null } && le.all { (a, b) -> a.indices.fold(Q.ZERO) { s, j -> s + a[j] * v[j]!! } <= b } && objective(v.map { it!! }) == best
                } == true
            })
        }
        // No feasible point: x + y ≤ b and x + y ≥ b + k; unbounded: a maximum with only ≥ rows.
        for (b in 1..5) {
            out += Task(AREA, "maximize(x + y, x + y <= $b, x + y >= ${b + 2}, x >= 0, y >= 0)", "infeasible", judge = { c ->
                (c.outcome as? Outcome.Optimized)?.program?.status == LinearProgram.Status.INFEASIBLE
            })
            out += Task(AREA, "maximize(${b}x + y, x + y >= $b, x >= 0, y >= 0)", "unbounded", judge = { c ->
                (c.outcome as? Outcome.Optimized)?.program?.status == LinearProgram.Status.UNBOUNDED
            })
        }
        out += Task(AREA, "maximize(x y, x + y <= 4)", "not linear", fails = setOf(Failure.NOT_LINEAR)) { false }
        return out
    }

    fun all(): List<Task> {
        val r = Random(5150)
        return matrices(r) + calculus(r) + programs(r)
    }
}
