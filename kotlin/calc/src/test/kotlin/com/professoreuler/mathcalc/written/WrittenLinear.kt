package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

private typealias M = List<List<Q>>

/**
 * Linear algebra, against the test's own exact fractions: determinants and inverses by its own elimination, ranks and reduced
 * forms the same way, eigenvalues of matrices built as P·D·P⁻¹ (so they are known), decompositions multiplied back.
 */
internal object WrittenLinear {
    private fun typed(m: List<List<Long>>) = m.joinToString(", ", "[", "]") { row -> row.joinToString(", ", "[", "]") }

    private fun qm(m: List<List<Long>>): M = m.map { row -> row.map { q(it) } }

    private fun times(a: M, b: M): M = a.map { row -> b[0].indices.map { j -> row.indices.fold(Q.ZERO) { s, k -> s + row[k] * b[k][j] } } }

    private fun identity(n: Int): M = List(n) { i -> List(n) { j -> if (i == j) Q.ONE else Q.ZERO } }

    /** Reduced row echelon form, with its rank. */
    private fun rref(m0: M): Pair<M, Int> {
        val m = m0.map { it.toMutableList() }.toMutableList()
        var row = 0
        for (col in m[0].indices) {
            val p = (row until m.size).firstOrNull { !m[it][col].isZero } ?: continue
            m[row] = m[p].also { m[p] = m[row] }
            val pivot = m[row][col]
            m[row] = m[row].map { it / pivot }.toMutableList()
            for (i in m.indices) if (i != row && !m[i][col].isZero) {
                val f = m[i][col]
                m[i] = m[i].indices.map { j -> m[i][j] - f * m[row][j] }.toMutableList()
            }
            row++
            if (row == m.size) break
        }
        return m to row
    }

    private fun det(m: M): Q {
        val a = m.map { it.toMutableList() }.toMutableList()
        var d = Q.ONE
        for (c in a.indices) {
            val p = (c until a.size).firstOrNull { !a[it][c].isZero } ?: return Q.ZERO
            if (p != c) { a[c] = a[p].also { a[p] = a[c] }; d = -d }
            d *= a[c][c]
            for (i in c + 1 until a.size) {
                val f = a[i][c] / a[c][c]
                a[i] = a[i].indices.map { j -> a[i][j] - f * a[c][j] }.toMutableList()
            }
        }
        return d
    }

    private fun inverse(m: M): M {
        val n = m.size
        val (r, _) = rref(m.indices.map { i -> m[i] + identity(n)[i] })
        return r.map { it.drop(n) }
    }

    private fun mat(c: Calculation): Expr.Mat? = resultOf(c) as? Expr.Mat

    private fun sameMatrix(e: Expr.Mat, m: M): Boolean = e.rows.size == m.size && e.rows.zip(m).all { (a, b) ->
        a.size == b.size && a.zip(b).all { (x, y) -> rational(x)?.let { Q.of(it.num, it.den) == y } ?: close(at(x, emptyMap()), y.toDouble(), 1e-12) }
    }

    private fun values(e: Expr.Mat): List<List<Double>> = e.rows.map { row -> row.map { at(it, emptyMap()) } }

    fun linear(): List<Task> {
        val r = Random(3141)
        val out = ArrayList<Task>()
        fun Random.matrix(rows: Int, cols: Int, lo: Int = -5, hi: Int = 5) = List(rows) { List(cols) { int(lo, hi).toLong() } }
        // Determinants.
        repeat(120) { i ->
            val n = 2 + i % 4
            val m = r.matrix(n, n, -6, 6)
            val want = det(qm(m))
            out += Task("linear algebra", if (i % 3 == 0) typed(m) else "det(${typed(m)})", "$want", judge = { c ->
                // A matrix on its own shows its determinant too.
                rationalOf(c) == want || (c.outcome as? Outcome.Expression)?.result?.let { rational(it)?.let { v -> Q.of(v.num, v.den) == want } } == true
            })
        }
        // Inverses, or singular.
        repeat(90) { i ->
            val n = 2 + i % 3
            val m = r.matrix(n, n, -4, 4)
            val d = det(qm(m))
            out += if (d.isZero) Task("linear algebra", "inverse(${typed(m)})", "singular", fails = setOf(Failure.SINGULAR)) { false }
            else Task("linear algebra", "inverse(${typed(m)})", "", judge = { c -> mat(c)?.let { sameMatrix(it, inverse(qm(m))) } == true })
        }
        // Reduced row echelon forms and ranks.
        repeat(60) { i ->
            val rows = 2 + i % 3; val cols = 2 + (i / 3) % 3
            var m = r.matrix(rows, cols, -3, 3)
            // Some with a row that is a sum of others, so the rank drops.
            if (i % 2 == 0 && rows >= 3) m = m.take(rows - 1) + listOf(m[0].indices.map { m[0][it] + m[1][it] })
            val (form, rank) = rref(qm(m))
            out += Task("linear algebra", "rref(${typed(m)})", "rank $rank", judge = { c ->
                val o = c.outcome as? Outcome.RowReduced
                o != null && o.rank == rank && sameMatrix(o.form, form) && o.nullSpace.all { v ->
                    // Each vector of the null space is one: A·v = 0.
                    m.all { row -> abs(row.indices.sumOf { k -> row[k] * at(v.rows[k][0], emptyMap()) }) < 1e-9 }
                } && o.nullSpace.size == cols - rank
            })
        }
        // Eigenvalues of P·D·P⁻¹, P with determinant ±1.
        repeat(60) { i ->
            val n = 2 + i % 2
            val lambdas = List(n) { r.int(-5, 5).toLong() }
            val p: List<List<Long>> = if (n == 2) listOf(listOf(1L, r.int(-2, 2).toLong()), listOf(0L, 1L)).let { u ->
                val l = listOf(listOf(1L, 0L), listOf(r.int(-2, 2).toLong(), 1L)); (times(qm(u), qm(l))).map { row -> row.map { it.n.toLong() } }
            } else {
                val u = listOf(listOf(1L, r.int(-1, 1).toLong(), r.int(-1, 1).toLong()), listOf(0L, 1L, r.int(-1, 1).toLong()), listOf(0L, 0L, 1L))
                val l = listOf(listOf(1L, 0L, 0L), listOf(r.int(-1, 1).toLong(), 1L, 0L), listOf(r.int(-1, 1).toLong(), r.int(-1, 1).toLong(), 1L))
                times(qm(u), qm(l)).map { row -> row.map { it.n.toLong() } }
            }
            val d = List(n) { a -> List(n) { b -> if (a == b) q(lambdas[a]) else Q.ZERO } }
            val a = times(times(qm(p), d), inverse(qm(p)))
            val am = a.map { row -> row.map { it.n.toLong() } }
            out += Task("linear algebra", "eigenvalues(${typed(am)})", "${lambdas.sorted()}", judge = { c ->
                val o = c.outcome as? Outcome.Eigenvalues
                if (o == null) false else {
                    val got = o.values.flatMap { e -> List(e.multiplicity) { at(e.value, emptyMap()) } }.sorted()
                    got.size == n && got.zip(lambdas.sorted()).all { (x, y) -> close(x, y.toDouble()) } && o.values.all { e ->
                        val lam = at(e.value, emptyMap())
                        e.vectors.isNotEmpty() && e.vectors.all { v ->
                            val vv = v.rows.map { at(it[0], emptyMap()) }
                            vv.any { abs(it) > 1e-12 } && am.indices.all { row -> abs(am[row].indices.sumOf { k -> am[row][k] * vv[k] } - lam * vv[row]) < 1e-9 }
                        }
                    }
                }
            })
        }
        // Products, powers, sums, transposes.
        repeat(40) {
            val n = r.int(2, 3); val k = r.int(2, 3); val m = r.int(2, 3)
            val a = r.matrix(n, k); val b = r.matrix(k, m)
            out += Task("linear algebra", "${typed(a)} * ${typed(b)}", "", judge = { c -> mat(c)?.let { sameMatrix(it, times(qm(a), qm(b))) } == true })
        }
        repeat(20) {
            val a = r.matrix(2, 2, -3, 3); val e = r.int(2, 5)
            val want = (1 until e).fold(qm(a)) { acc, _ -> times(acc, qm(a)) }
            out += Task("linear algebra", "${typed(a)}^$e", "", judge = { c -> mat(c)?.let { sameMatrix(it, want) } == true })
        }
        repeat(20) {
            val rows = r.int(1, 3); val cols = r.int(2, 4)
            val a = r.matrix(rows, cols)
            out += Task("linear algebra", "transpose(${typed(a)})", "", judge = { c -> mat(c)?.let { sameMatrix(it, qm(a[0].indices.map { j -> a.map { it[j] } })) } == true })
        }
        repeat(20) {
            val a = r.matrix(2, 3); val b = r.matrix(2, 3); val s = r.int(-3, 3, nonzero = true)
            out += Task("linear algebra", "${typed(a)} + $s${typed(b)}", "", judge = { c ->
                mat(c)?.let { sameMatrix(it, a.indices.map { i -> a[i].indices.map { j -> q(a[i][j] + s * b[i][j]) } }) } == true
            })
        }
        // Vectors: dot and cross products, lengths.
        repeat(60) { i ->
            val u = List(3) { r.int(-6, 6).toLong() }; val v = List(3) { r.int(-6, 6).toLong() }
            when (i % 3) {
                0 -> out += Task("linear algebra", "${u.joinToString(", ", "[", "]")} * ${v.joinToString(", ", "[", "]")}", "", judge = isExact(q(u.indices.sumOf { u[it] * v[it] })))
                1 -> {
                    val cross = listOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
                    out += Task("linear algebra", "${u.joinToString(", ", "(", ")")} × ${v.joinToString(", ", "(", ")")}", "$cross", judge = { c ->
                        mat(c)?.let { values(it).flatten().zip(cross).all { (x, y) -> close(x, y.toDouble()) } && values(it).flatten().size == 3 } == true
                    })
                }
                else -> out += Task("linear algebra", "|${u.joinToString(", ", "[", "]")}|", "", judge = isNumber(kotlin.math.sqrt(u.sumOf { it * it }.toDouble())))
            }
        }
        // Decompositions multiplied back: P·A = L·U, A = Q·R, A = L·Lᵀ.
        repeat(30) { i ->
            val n = 2 + i % 2
            val a = if (i % 3 == 0) {
                // Symmetric positive definite: Bᵀ·B + I.
                val b = r.matrix(n, n, -2, 2)
                List(n) { x -> List(n) { y -> (0 until n).sumOf { k -> b[k][x] * b[k][y] } + if (x == y) 1L else 0L } }
            } else r.matrix(n, n, -4, 4)
            if (det(qm(a)).isZero) return@repeat
            out += Task("linear algebra", "decompose(${typed(a)})", "", judge = { c ->
                val o = c.outcome as? Outcome.Decompositions
                fun num(m: Expr.Mat) = values(m)
                fun mul(x: List<List<Double>>, y: List<List<Double>>) = x.map { row -> y[0].indices.map { j -> row.indices.sumOf { k -> row[k] * y[k][j] } } }
                fun same(x: List<List<Double>>, y: List<List<Double>>) = x.indices.all { p -> x[p].indices.all { q -> abs(x[p][q] - y[p][q]) < 1e-9 } }
                val ad = a.map { row -> row.map { it.toDouble() } }
                o != null && o.lu != null && o.lu!!.let { (p, l, u) ->
                    same(mul(num(p), ad), mul(num(l), num(u))) && num(l).indices.all { x -> num(l)[x].indices.all { y -> y <= x || abs(num(l)[x][y]) < 1e-12 } } &&
                        num(u).indices.all { x -> num(u)[x].indices.all { y -> y >= x || abs(num(u)[x][y]) < 1e-12 } }
                } && (o.qr == null || o.qr!!.let { (qq, rr) -> same(mul(num(qq), num(rr)), ad) && same(mul(num(qq)[0].indices.map { x -> num(qq).map { it[x] } }, num(qq)), List(n) { x -> List(n) { y -> if (x == y) 1.0 else 0.0 } }) }) &&
                    (o.cholesky == null || o.cholesky!!.let { l -> same(mul(num(l), num(l)[0].indices.map { x -> num(l).map { it[x] } }), ad) }) &&
                    (i % 3 != 0 || o.cholesky != null)
            })
        }
        // e^A of diagonal and nilpotent matrices.
        repeat(15) {
            val d = List(2) { r.int(-2, 2) }
            out += Task("linear algebra", "expm([[${d[0]}, 0], [0, ${d[1]}]])", "", judge = { c ->
                mat(c)?.let { m -> values(m).let { v -> close(v[0][0], exp(d[0].toDouble())) && close(v[1][1], exp(d[1].toDouble())) && abs(v[0][1]) < 1e-12 && abs(v[1][0]) < 1e-12 } } == true
            })
            val k = r.int(-4, 4, nonzero = true)
            out += Task("linear algebra", "expm([[0, $k], [0, 0]])", "", judge = { c -> mat(c)?.let { sameMatrix(it, qm(listOf(listOf(1L, k.toLong()), listOf(0L, 1L)))) } == true })
            // [[a, b], [0, a]]: e^a·[[1, b], [0, 1]].
            val a = r.int(-2, 2)
            out += Task("linear algebra", "expm([[$a, $k], [0, $a]])", "", judge = { c ->
                mat(c)?.let { m -> values(m).let { v -> close(v[0][0], exp(a.toDouble())) && close(v[0][1], k * exp(a.toDouble())) && abs(v[1][0]) < 1e-12 && close(v[1][1], exp(a.toDouble())) } } == true
            })
        }
        // Sizes that do not fit are refused.
        for ((x, y) in listOf("[[1, 2, 3], [4, 5, 6]]" to "[[1, 2], [3, 4]]", "[[1, 2]]" to "[[1, 2]]")) {
            out += Task("linear algebra", "$x * $y", "sizes", fails = setOf(Failure.SIZES)) { false }
        }
        out += Task("linear algebra", "det([[1, 2, 3], [4, 5, 6]])", "not square", fails = setOf(Failure.SIZES)) { false }
        return out
    }
}
