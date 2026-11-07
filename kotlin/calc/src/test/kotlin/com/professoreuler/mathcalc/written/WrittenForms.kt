package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Matrix forms and further differential equations. An SVD multiplied back, its factors checked orthogonal, its singular values
 * the square roots of AᵀA's eigenvalues by the test's own Jacobi rotations; a Jordan form of a matrix built as S·J·S⁻¹ (so its
 * blocks are known) multiplied back in fractions; systems' solutions put back by the test's own difference quotients; boundary
 * value problems by the textbook's case analysis (sin kL = 0 or not); series solutions by the test's own recurrence for the
 * Taylor coefficients in fractions; Dormand–Prince against closed-form solutions; slope fields' constant solutions the roots the
 * polynomial was built from; the kind of a linear system's equilibrium by its trace and determinant.
 */
internal object WrittenForms {
    private const val AREA = "forms and odes"

    private fun values(e: Expr.Mat) = e.rows.map { r -> r.map { valueOf(it) } }

    private fun mul(a: List<List<Double>>, b: List<List<Double>>) = a.map { row -> b[0].indices.map { j -> row.indices.sumOf { k -> row[k] * b[k][j] } } }

    private fun tr(a: List<List<Double>>) = a[0].indices.map { j -> a.map { it[j] } }

    private fun near(a: List<List<Double>>, b: List<List<Double>>, tol: Double = 1e-8) =
        a.size == b.size && a.indices.all { i -> a[i].size == b[i].size && a[i].indices.all { j -> abs(a[i][j] - b[i][j]) <= tol * maxOf(1.0, abs(b[i][j])) } }

    private fun identity(n: Int) = List(n) { i -> List(n) { j -> if (i == j) 1.0 else 0.0 } }

    /** The eigenvalues of a symmetric matrix by Jacobi's rotations, in doubles. */
    private fun symmetricEigenvalues(m: List<List<Double>>): List<Double> {
        val a = m.map { it.toDoubleArray() }.toTypedArray()
        val n = a.size
        repeat(100) {
            for (p in 0 until n) for (q in p + 1 until n) {
                if (abs(a[p][q]) < 1e-300) continue
                val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                val t = Math.signum(theta).let { if (it == 0.0) 1.0 else it } / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1); val s = t * c
                for (k in 0 until n) { val akp = a[k][p]; val akq = a[k][q]; a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq }
                for (k in 0 until n) { val apk = a[p][k]; val aqk = a[q][k]; a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk }
            }
        }
        return (0 until n).map { a[it][it] }
    }

    private fun svds(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) {
            val m = r.int(1, 3); val n = r.int(1, 3)
            if (m * n == 1) return@repeat
            val a = List(m) { List(n) { r.int(-4, 4).toDouble() } }
            val ata = mul(tr(a), a)
            // min(m, n) of them, the largest; a zero one (rank below that) is the square root of rounding, about 10⁻⁸, taken as 0.
            val sigma = symmetricEigenvalues(ata).sortedDescending().take(minOf(m, n)).map { if (it < 1e-12) 0.0 else sqrt(it) }
            out += Task(AREA, "svd(${a.joinToString(", ", "[", "]") { row -> row.joinToString(", ", "[", "]") { fmt(it) } }})", "σ $sigma", judge = { c ->
                val s = (c.outcome as? Outcome.SingularValues)?.svd
                if (s == null) false else {
                    val u = values(s.u); val sg = values(s.sigma); val v = values(s.v)
                    val got = s.values.map { valueOf(it) }
                    near(mul(mul(u, sg), tr(v)), a) && near(mul(tr(u), u), identity(u[0].size)) && near(mul(tr(v), v), identity(v[0].size)) &&
                        got.size == sigma.size && got.zip(sigma).all { (x, y) -> abs(x - y) <= 1e-8 * maxOf(1.0, y) }
                }
            })
        }
        return out
    }

    private fun jordans(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val n = 2 + i % 2
            // J from chosen blocks: one eigenvalue with a block of size 2 (or 3), or several of size 1.
            val blocks: List<Pair<Long, Int>> = when (i % 4) {
                0 -> listOf(r.int(-3, 3).toLong() to n)
                1 -> listOf(r.int(-3, 3).toLong() to 2) + List(n - 2) { r.int(-3, 3).toLong() to 1 }
                2 -> List(n) { r.int(-3, 3).toLong() to 1 }
                else -> listOf(r.int(-3, 3).toLong() to 1) + (if (n == 3) listOf(r.int(-3, 3).toLong() to 2) else listOf(r.int(-3, 3).toLong() to 1))
            }
            val j = Array(n) { Array(n) { Q.ZERO } }
            var at = 0
            for ((lambda, size) in blocks) {
                for (k in 0 until size) { j[at + k][at + k] = q(lambda); if (k > 0) j[at + k - 1][at + k] = Q.ONE }
                at += size
            }
            // S unimodular (upper times lower triangular with ones on the diagonal).
            val upper = List(n) { x -> List(n) { y -> if (x == y) Q.ONE else if (y > x) q(r.int(-1, 1).toLong()) else Q.ZERO } }
            val lower = List(n) { x -> List(n) { y -> if (x == y) Q.ONE else if (y < x) q(r.int(-1, 1).toLong()) else Q.ZERO } }
            val s = QM.times(upper, lower)
            val a = QM.times(QM.times(s, j.map { it.toList() }), QM.inverse(s))
            val am = a.map { row -> row.map { it.n.toLong() } }
            // Blocks as a multiset: equal eigenvalues' blocks of size 1 merge only where the eigenvectors allow; the sizes of each
            // eigenvalue's blocks are the textbook's.
            val want = blocks.groupBy({ it.first }, { it.second }).mapValues { it.value.sorted() }
            out += Task(AREA, "jordan(${QM.typed(am)})", "$want", judge = { c ->
                val f = (c.outcome as? Outcome.JordanForm)?.jordan
                val jj = f?.j?.let { QM.read(it) }; val p = f?.p?.let { QM.read(it) }
                f != null && jj != null && p != null && !QM.det(p).isZero && QM.times(a, p) == QM.times(p, jj) &&
                    f.blocks.groupBy({ rationalOf(it.value)!! }, { it.size }).mapValues { it.value.sorted() } == want.mapKeys { q(it.key) }
            })
        }
        return out
    }

    private fun systems(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val a = r.int(-3, 3); val b = r.int(-3, 3); val c = r.int(-3, 3); val d = r.int(-3, 3)
            val p = r.int(-2, 2); val q0 = r.int(-2, 2)
            val withValues = i % 2 == 0
            val call = "dsolve(x' = ${listOf(a.toLong() to "x", b.toLong() to "y").filter { it.first != 0L }.mapIndexed { k, (v, s) -> term(v, s, first = k == 0) }.joinToString("").ifEmpty { "0" }}, " +
                "y' = ${listOf(c.toLong() to "x", d.toLong() to "y").filter { it.first != 0L }.mapIndexed { k, (v, s) -> term(v, s, first = k == 0) }.joinToString("").ifEmpty { "0" }}" +
                (if (withValues) ", x(0) = $p, y(0) = $q0)" else ")")
            out += Task(AREA, call, "put back", judge = { cc ->
                val s = (cc.outcome as? Outcome.OdeSystemSolved)?.system
                val sol = if (withValues) s?.particular else s?.general
                if (s == null || sol == null || sol.size != 2) false else {
                    val rr = Random(3)
                    (0 until (if (withValues) 1 else 3)).all {
                        val env = s.constants.associateWith { rr.nextDouble(-2.0, 2.0) }
                        val x = { t: Double -> at(sol[0], env + (s.t to t)) }
                        val y = { t: Double -> at(sol[1], env + (s.t to t)) }
                        listOf(0.2, 0.7, 1.1).all { t ->
                            val scale = 1 + abs(x(t)) + abs(y(t))
                            abs(slope(t, x) - (a * x(t) + b * y(t))) <= 1e-6 * scale && abs(slope(t, y) - (c * x(t) + d * y(t))) <= 1e-6 * scale
                        } && (!withValues || close(x(0.0), p.toDouble(), 1e-10, 1e-10) && close(y(0.0), q0.toDouble(), 1e-10, 1e-10))
                    }
                }
            })
        }
        return out
    }

    private fun boundaries(r: Random): List<Task> {
        val out = ArrayList<Task>()
        // y″ + k²y = 0, y(0) = a, y(L) = b: y = a·cos kx + B·sin kx; unique where sin kL ≠ 0; at kL = mπ none unless b = a·(−1)ᵐ, then
        // infinitely many.
        for (k in 1..3) for ((lt, l) in listOf("pi" to Math.PI, "1" to 1.0, "pi/2" to Math.PI / 2, "2" to 2.0)) repeat(2) {
            val a = r.int(-3, 3); val b = r.int(-3, 3)
            val s = sin(k * l)
            val call = "dsolve(y'' + ${k * k}y = 0, y(0) = $a, y($lt) = $b)"
            if (abs(s) > 1e-9) {
                val bb = (b - a * cos(k * l)) / s
                out += Task(AREA, call, "unique", judge = { c ->
                    val p = (c.outcome as? Outcome.BoundaryValueSolved)?.problem
                    p != null && p.kind == BoundaryValue.Kind.UNIQUE && agrees(p.solution!!, listOf(p.x), points1(0.0, 3.0)) { (x) -> a * cos(k * x) + bb * sin(k * x) }
                })
            } else {
                val m = Math.round(k * l / Math.PI).toInt()
                val consistent = b == a * (if (m % 2 == 0) 1 else -1)
                out += Task(AREA, call, if (consistent) "infinitely many" else "none", judge = { c ->
                    val p = (c.outcome as? Outcome.BoundaryValueSolved)?.problem
                    p != null && p.kind == (if (consistent) BoundaryValue.Kind.INFINITE else BoundaryValue.Kind.NONE)
                })
            }
        }
        // y″ − k²y = 0 always has one: y = a·cosh kx + B·sinh kx.
        for (k in 1..3) repeat(3) {
            val a = r.int(-3, 3); val b = r.int(-3, 3); val l = r.int(1, 2).toDouble()
            val bb = (b - a * kotlin.math.cosh(k * l)) / kotlin.math.sinh(k * l)
            out += Task(AREA, "dsolve(y'' - ${k * k}y = 0, y(0) = $a, y(${fmt(l)}) = $b)", "unique", judge = { c ->
                val p = (c.outcome as? Outcome.BoundaryValueSolved)?.problem
                p != null && p.kind == BoundaryValue.Kind.UNIQUE &&
                    agrees(p.solution!!, listOf(p.x), points1(0.0, 2.0)) { (x) -> a * kotlin.math.cosh(k * x) + bb * kotlin.math.sinh(k * x) }
            })
        }
        return out
    }

    private fun series(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) {
            // y″ + p(x)y′ + q(x)y = 0 with polynomials p and q, at 0: (k + 2)(k + 1)c₍k+2₎ = −Σ pⱼ(k − j + 1)c₍k−j+1₎ − Σ qⱼc₍k−j₎.
            val p = List(r.int(1, 2)) { r.int(-2, 2).toLong() }
            val qq = List(r.int(1, 2)) { r.int(-3, 3).toLong() }
            val y0 = r.int(-2, 2).toLong(); val y1 = r.int(-2, 2).toLong()
            val n = r.int(4, 8)
            val c = MutableList(n + 1) { Q.ZERO }
            c[0] = q(y0); c[1] = q(y1)
            for (k in 0..n - 2) {
                var s = Q.ZERO
                for (j in p.indices) if (k - j + 1 >= 0) s += q(p[j]) * q(k - j + 1L) * c[k - j + 1]
                for (j in qq.indices) if (k - j >= 0) s += q(qq[j]) * c[k - j]
                c[k + 2] = -s / q((k + 2L) * (k + 1))
            }
            fun poly(cs: List<Long>, factor: String) = QP.text(QP.ofLongs(cs)).let { t -> if (t == "0") "" else if (t == "1") factor else "($t)$factor" }
            val pt = poly(p, "y'"); val qt = poly(qq, "y")
            val eq = listOf("y''", pt, qt).filter { it.isNotEmpty() }.joinToString(" + ")
            out += Task(AREA, "odeseries($eq = 0, y(0) = $y0, y'(0) = $y1, 0, $n)", QP.text(c), judge = { cc ->
                val s = (cc.outcome as? Outcome.SeriesSolved)?.solution
                s != null && (0..n).all { k -> (s.terms.firstOrNull { it.second == k }?.first?.let { rationalOf(it) } ?: Q.ZERO) == c[k] } &&
                    s.terms.all { it.second <= n }
            })
        }
        return out
    }

    private fun numerics(r: Random): List<Task> {
        val out = ArrayList<Task>()
        repeat(40) { i ->
            val x0 = r.int(-1, 1); val y0 = r.int(1, 3); val b = x0 + r.int(1, 3)
            val (f, exact) = when (i % 4) {
                0 -> { val k = r.int(-2, 2); "${k}y" to { x: Double -> y0 * exp(k * (x - x0)) } }
                1 -> "x + y" to { x: Double -> (y0 + x0 + 1) * exp(x - x0) - x - 1 }
                2 -> "-2x y" to { x: Double -> y0 * exp(-(x * x - x0 * x0.toDouble())) }
                else -> "cos(x)" to { x: Double -> y0 + sin(x) - sin(x0.toDouble()) }
            }
            val want = exact(b.toDouble())
            out += Task(AREA, "dormandprince(y' = $f, y($x0) = $y0, $b)", "$want", judge = { c ->
                (c.outcome as? Outcome.NumericTable)?.table?.result?.let { abs(it - want) <= 1e-6 * maxOf(1.0, abs(want)) } == true
            })
        }
        // Slope fields: the constant solutions of y′ = c(y − r₁)(y − r₂)… are the roots.
        repeat(30) {
            val roots = List(r.int(1, 3)) { r.int(-4, 4) }.distinct().sorted()
            var p = listOf(q(r.int(-2, 2, nonzero = true).toLong()))
            for (root in roots) p = QP.times(p, listOf(q(-root.toLong()), Q.ONE))
            out += Task(AREA, "slopefield(y' = ${QP.text(p, "y")})", "constant $roots", judge = { c ->
                (c.outcome as? Outcome.DirectionField)?.field?.constants?.map { rationalOf(it) }?.toSet() == roots.map { q(it.toLong()) }.toSet()
            })
        }
        // A linear system's equilibrium by trace and determinant.
        repeat(60) {
            val a = r.int(-3, 3); val b = r.int(-3, 3); val c = r.int(-3, 3); val d = r.int(-3, 3)
            val t = a + d; val det = a * d - b * c; val disc = t * t - 4 * det
            val (kind, stable) = when {
                det == 0 -> Fields.Kind.NON_ISOLATED to null
                det < 0 -> Fields.Kind.SADDLE to false
                t == 0 -> Fields.Kind.CENTRE to null
                disc < 0 -> Fields.Kind.SPIRAL to (t < 0)
                disc > 0 -> Fields.Kind.NODE to (t < 0)
                b == 0 && c == 0 -> Fields.Kind.STAR to (t < 0)
                else -> Fields.Kind.DEGENERATE_NODE to (t < 0)
            }
            fun side(u: Int, v: Int) = listOf(u.toLong() to "x", v.toLong() to "y").filter { it.first != 0L }.mapIndexed { k, (w, s) -> term(w, s, first = k == 0) }.joinToString("").ifEmpty { "0" }
            out += Task(AREA, "phaseportrait(x' = ${side(a, b)}, y' = ${side(c, d)})", "$kind", judge = { cc ->
                val e = (cc.outcome as? Outcome.DirectionField)?.field?.equilibria
                if (det == 0) e != null && e.all { it.kind == Fields.Kind.NON_ISOLATED } else
                    e != null && e.size == 1 && e[0].kind == kind && (stable == null || e[0].stable == stable) && e[0].at.all { isZero(it) }
            })
        }
        return out
    }

    fun all(): List<Task> {
        val r = Random(31415)
        return svds(r) + jordans(r) + systems(r) + boundaries(r) + series(r) + numerics(r)
    }
}
