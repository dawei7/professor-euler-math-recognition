package com.professoreuler.mathcalc

import kotlin.math.abs

/**
 * Matrices and vectors with exact entries ([Expr.Mat]): sums, products, powers, the transpose, the determinant, the inverse
 * (checked: A·A⁻¹ = I), reduced row echelon form with its row operations, rank, null space, the characteristic polynomial
 * (Faddeev–LeVerrier), eigenvalues with their multiplicities and eigenvectors (each checked: A·v = λ·v), dot and cross
 * products and the norm. Entries are simplified after every step; a zero is decided exactly, or in ball arithmetic where it
 * is a number written with roots.
 */
object LinAlg {
    /** At most this many rows and columns: beyond, exact arithmetic takes too long to be worth waiting for. */
    const val MOST = 8

    /** A row operation of an elimination, for its steps: swap rows i and j, multiply row i by c, add c times row j to row i. */
    sealed class RowOp {
        data class Swap(val i: Int, val j: Int) : RowOp()
        data class Scale(val i: Int, val c: Expr) : RowOp()
        data class AddMultiple(val i: Int, val j: Int, val c: Expr) : RowOp()
    }

    /** A matrix brought to reduced row echelon form: the form, the columns of its pivots and the operations that made it. */
    class Echelon(val form: Expr.Mat, val pivots: List<Int>, val ops: List<RowOp>) {
        val rank get() = pivots.size
    }

    /** An eigenvalue, how often it is a root of the characteristic polynomial, and a basis of its eigenvectors. */
    class Eigen(val value: Expr, val multiplicity: Int, val vectors: List<Expr.Mat>)

    // ---------------------------------------------------------------------------------------------------------------------
    // Building and arithmetic.

    fun matrix(rows: List<List<Expr>>): Expr.Mat {
        if (rows.isEmpty() || rows[0].isEmpty() || rows.any { it.size != rows[0].size }) throw CalcException(Failure.SIZES)
        if (rows.size > MOST || rows[0].size > MOST) throw CalcException(Failure.TOO_LARGE)
        return Expr.Mat(rows)
    }

    fun column(entries: List<Expr>): Expr.Mat = matrix(entries.map { listOf(it) })

    fun identity(n: Int): Expr.Mat = Expr.Mat(List(n) { i -> List(n) { j -> if (i == j) Expr.ONE else Expr.ZERO } })

    private fun tidy(e: Expr): Expr = Calculus.tidy(e)

    private fun Expr.Mat.map(f: (Expr) -> Expr) = Expr.Mat(rows.map { r -> r.map(f) })

    fun isVector(m: Expr.Mat) = m.columns == 1 && m.size > 1

    fun plus(a: Expr, b: Expr): Expr {
        if (a !is Expr.Mat || b !is Expr.Mat) throw CalcException(Failure.SIZES)
        if (a.size != b.size || a.columns != b.columns) throw CalcException(Failure.SIZES)
        return Expr.Mat(a.rows.indices.map { i -> a.rows[i].indices.map { j -> tidy(a.rows[i][j] + b.rows[i][j]) } })
    }

    fun scale(m: Expr.Mat, c: Expr): Expr.Mat = m.map { tidy(mul(c, it)) }

    /** a·b: a number times a matrix, a matrix times a matrix (rows of a against columns of b), or two vectors' dot product. */
    fun times(a: Expr, b: Expr): Expr {
        if (a !is Expr.Mat && b is Expr.Mat) return scale(b, a)
        if (a is Expr.Mat && b !is Expr.Mat) return scale(a, b)
        a as Expr.Mat
        b as Expr.Mat
        if (isVector(a) && isVector(b) && a.size == b.size) return dot(a, b)
        if (a.columns != b.size) throw CalcException(Failure.SIZES)
        return Expr.Mat(List(a.size) { i -> List(b.columns) { j -> tidy(add((0 until a.columns).map { k -> mul(a.rows[i][k], b.rows[k][j]) })) } })
    }

    fun dot(a: Expr.Mat, b: Expr.Mat): Expr {
        if (!isVector(a) || !isVector(b) || a.size != b.size) throw CalcException(Failure.SIZES)
        return tidy(add(a.rows.indices.map { mul(a.rows[it][0], b.rows[it][0]) }))
    }

    fun cross(a: Expr.Mat, b: Expr.Mat): Expr.Mat {
        if (!isVector(a) || !isVector(b) || a.size != 3 || b.size != 3) throw CalcException(Failure.SIZES)
        val (a1, a2, a3) = a.rows.map { it[0] }
        val (b1, b2, b3) = b.rows.map { it[0] }
        return column(listOf(tidy(a2 * b3 - a3 * b2), tidy(a3 * b1 - a1 * b3), tidy(a1 * b2 - a2 * b1)))
    }

    /** The length of a vector: √(v·v). */
    fun norm(v: Expr.Mat): Expr = tidy(sqrt(dot(v, v)))

    fun transpose(m: Expr.Mat): Expr.Mat = Expr.Mat(List(m.columns) { j -> List(m.size) { i -> m.rows[i][j] } })

    /** Aⁿ for a whole n: repeated squaring; A⁻ⁿ is (A⁻¹)ⁿ. */
    fun power(m: Expr.Mat, n: Int): Expr.Mat {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        if (n < 0) return power(inverse(m), -n)
        if (n > 64) throw CalcException(Failure.TOO_LARGE)
        var result = identity(m.size)
        var base = m
        var k = n
        while (k > 0) {
            Budget.check()
            if (k and 1 == 1) result = times(result, base) as Expr.Mat
            base = times(base, base) as Expr.Mat
            k = k shr 1
        }
        return result
    }

    fun trace(m: Expr.Mat): Expr = tidy(add(m.rows.indices.map { m.rows[it][it] }))

    // ---------------------------------------------------------------------------------------------------------------------
    // Zero, and elimination.

    /** Exactly zero, or a number zero in ball arithmetic (√2·√2 − 2). */
    internal fun isNull(e: Expr): Boolean {
        if (isZero(e)) return true
        if (e.symbols().isNotEmpty()) return isZero(tidy(Algebra.expand(e)))
        val v = Numeric.value(e)
        if (v != null && abs(v) > 1e-9) return false
        return Precise.nearZero(e, complex = e.hasI()) == true
    }

    /**
     * The reduced row echelon form by Gauss–Jordan elimination, exactly: in each column the first row from the current one with
     * an entry that is not zero is the pivot (a number before an expression in letters, the simpler first).
     */
    fun echelon(m0: Expr.Mat): Echelon {
        val rows = m0.rows.map { it.toMutableList() }.toMutableList()
        val ops = ArrayList<RowOp>()
        val pivots = ArrayList<Int>()
        var r = 0
        for (c in 0 until m0.columns) {
            if (r >= rows.size) break
            Budget.check()
            val candidates = (r until rows.size).filter { !isNull(rows[it][c]) }
            if (candidates.isEmpty()) continue
            val p = candidates.minWith(compareBy<Int> { if (rows[it][c].symbols().isEmpty()) 0 else 1 }.thenBy { rows[it][c].size() })
            if (p != r) {
                val t = rows[p]; rows[p] = rows[r]; rows[r] = t
                ops += RowOp.Swap(r, p)
            }
            val pivot = rows[r][c]
            if (!isOne(pivot)) {
                val inverse = tidy(pow(pivot, Expr.MINUS_ONE))
                rows[r] = rows[r].map { tidy(mul(inverse, it)) }.toMutableList()
                ops += RowOp.Scale(r, inverse)
            }
            for (k in rows.indices) {
                if (k == r || isNull(rows[k][c])) continue
                val factor = tidy(neg(rows[k][c]))
                rows[k] = rows[k].indices.map { j -> tidy(rows[k][j] + mul(factor, rows[r][j])) }.toMutableList()
                ops += RowOp.AddMultiple(k, r, factor)
            }
            // Entries that are zero only by their numbers are written as 0.
            for (k in rows.indices) rows[k] = rows[k].map { if (isNull(it)) Expr.ZERO else it }.toMutableList()
            pivots += c
            r++
        }
        return Echelon(Expr.Mat(rows), pivots, ops)
    }

    fun rank(m: Expr.Mat): Int = echelon(m).rank

    /** A basis of the vectors x with A·x = 0, from the free columns of the echelon form. */
    fun nullSpace(m: Expr.Mat): List<Expr.Mat> {
        val e = echelon(m)
        val free = (0 until m.columns).filter { it !in e.pivots }
        return free.map { f ->
            val v = MutableList<Expr>(m.columns) { Expr.ZERO }
            v[f] = Expr.ONE
            for ((row, pc) in e.pivots.withIndex()) v[pc] = tidy(neg(e.form.rows[row][f]))
            column(v)
        }
    }

    /** The determinant: by cofactors up to 3 × 3 (and for letters up to 4 × 4), else as the product of elimination's pivots. */
    fun det(m: Expr.Mat): Expr {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        val n = m.size
        val letters = m.rows.any { r -> r.any { it.symbols().isNotEmpty() } }
        if (n <= 3 || (letters && n <= 4)) return tidy(Algebra.expand(cofactor(m.rows)))
        // Elimination: the determinant is the product of the pivots, with a sign for each swap.
        val rows = m.rows.map { it.toMutableList() }.toMutableList()
        var result: Expr = Expr.ONE
        for (c in 0 until n) {
            Budget.check()
            val p = (c until n).firstOrNull { !isNull(rows[it][c]) } ?: return Expr.ZERO
            if (p != c) { val t = rows[p]; rows[p] = rows[c]; rows[c] = t; result = neg(result) }
            val pivot = rows[c][c]
            result = tidy(mul(result, pivot))
            for (k in c + 1 until n) {
                if (isNull(rows[k][c])) continue
                val f = tidy(neg(rows[k][c]) / pivot)
                rows[k] = rows[k].indices.map { j -> tidy(rows[k][j] + mul(f, rows[c][j])) }.toMutableList()
            }
        }
        return tidy(result)
    }

    private fun cofactor(rows: List<List<Expr>>): Expr {
        val n = rows.size
        if (n == 1) return rows[0][0]
        if (n == 2) return rows[0][0] * rows[1][1] - rows[0][1] * rows[1][0]
        return add((0 until n).map { j ->
            val minor = rows.drop(1).map { r -> r.filterIndexed { k, _ -> k != j } }
            val sign = if (j % 2 == 0) Expr.ONE else Expr.MINUS_ONE
            mul(sign, rows[0][j], cofactor(minor))
        })
    }

    /** The inverse by Gauss–Jordan on [A | I], checked: A·A⁻¹ = I. A singular matrix has none. */
    fun inverse(m: Expr.Mat): Expr.Mat {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        val n = m.size
        val augmented = Expr.Mat(m.rows.mapIndexed { i, r -> r + List(n) { j -> if (i == j) Expr.ONE else Expr.ZERO } })
        val e = echelon(augmented)
        if (e.pivots.take(n) != (0 until n).toList()) throw CalcException(Failure.SINGULAR)
        val inv = Expr.Mat(e.form.rows.map { it.drop(n) })
        val check = times(m, inv) as Expr.Mat
        for (i in 0 until n) for (j in 0 until n) {
            val want = if (i == j) Expr.ONE else Expr.ZERO
            if (!isNull(tidy(check.rows[i][j] - want))) throw CalcException(Failure.UNSOLVED)
        }
        return inv
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Eigenvalues.

    /** The letter of the characteristic polynomial. */
    val LAMBDA = Expr.Sym("λ")

    /** det(λI − A), by Faddeev–LeVerrier (traces of powers, exact). */
    fun characteristic(m: Expr.Mat): Expr {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        val n = m.size
        val c = MutableList<Expr>(n + 1) { Expr.ZERO }
        c[n] = Expr.ONE
        var mk = Expr.Mat(List(n) { List(n) { Expr.ZERO } })
        for (k in 1..n) {
            Budget.check()
            // M_k = A·M_(k−1) + c_(n−k+1)·I, c_(n−k) = −tr(A·M_k)/k.
            val am = times(m, mk) as Expr.Mat
            mk = Expr.Mat(am.rows.mapIndexed { i, r -> r.mapIndexed { j, v -> if (i == j) tidy(v + c[n - k + 1]) else v } })
            c[n - k] = tidy(neg(trace(times(m, mk) as Expr.Mat)) / num(k.toLong()))
        }
        return tidy(add((0..n).map { k -> mul(c[k], pow(LAMBDA, num(k.toLong()))) }))
    }

    /**
     * The eigenvalues (real first, then complex), each with its algebraic multiplicity and a basis of its eigenvectors; every
     * vector is checked in A·v = λ·v. Null where the characteristic polynomial's roots cannot be found.
     */
    fun eigen(m: Expr.Mat): List<Eigen>? {
        val p = characteristic(m)
        val solutions = Solve.equation(p, Expr.ZERO, LAMBDA)
        val roots = (solutions.values + solutions.complex).distinct()
        if (roots.isEmpty() || solutions.numeric) return null
        val n = m.size
        val out = ArrayList<Eigen>()
        var counted = 0
        for (lambda in roots) {
            Budget.check()
            // How often λ is a root: the first derivative of p that does not vanish there.
            var multiplicity = 0
            var d = p
            while (multiplicity < n && isNull(tidy(d.substitute(LAMBDA, lambda)))) {
                multiplicity++
                d = Calculus.derivative(d, LAMBDA)
            }
            if (multiplicity == 0) return null
            counted += multiplicity
            val shifted = Expr.Mat(m.rows.mapIndexed { i, r -> r.mapIndexed { j, v -> if (i == j) tidy(v - lambda) else v } })
            val vectors = nullSpace(shifted).map { v -> cleared(v) }.filter { v ->
                val av = times(m, v) as Expr.Mat
                v.rows.indices.all { i -> isNull(tidy(av.rows[i][0] - mul(lambda, v.rows[i][0]))) }
            }
            out += Eigen(lambda, multiplicity, vectors)
        }
        return if (counted == n) out else null
    }

    /**
     * A = P·D·P⁻¹ where A has as many independent eigenvectors as rows: P their columns, D the eigenvalues on its diagonal;
     * checked: A·P = P·D. Null where A cannot be diagonalized ([[1, 1], [0, 1]]).
     */
    fun diagonalization(m: Expr.Mat, eigen: List<Eigen>): Pair<Expr.Mat, Expr.Mat>? {
        val n = m.size
        val columns = eigen.flatMap { e -> e.vectors.map { v -> e.value to v } }
        if (columns.size != n) return null
        val p = Expr.Mat(List(n) { i -> columns.map { it.second.rows[i][0] } })
        val d = Expr.Mat(List(n) { i -> List(n) { j -> if (i == j) columns[i].first else Expr.ZERO } })
        val ap = times(m, p) as Expr.Mat
        val pd = times(p, d) as Expr.Mat
        for (i in 0 until n) for (j in 0 until n) if (!isNull(tidy(ap.rows[i][j] - pd.rows[i][j]))) return null
        if (isNull(det(p))) return null
        return p to d
    }

    /** One value of a system's parameter: the system there has [solution] (in [free] letters, as many as it has), or none. */
    class Case(val value: Expr, val solution: Map<String, Expr>?, val free: List<String>)

    /** A linear system in [unknowns] with one [parameter]: for every value but [excluded] the [generic] solution; at those, [special]. */
    class Cases(val parameter: String, val unknowns: List<String>, val excluded: List<Expr>, val generic: Map<String, Expr>?, val special: List<Case>)

    /**
     * A square linear system with a letter in its coefficients, case by case: its determinant in that letter is 0 at a few
     * values; for every other value one solution (by elimination, in the letter); at each of those values the system solved
     * again, with none, one, or a family in free letters. Every solution put back into every equation. Null where the system
     * is not linear in its unknowns, not square, or the determinant's zeros cannot be found.
     */
    fun cases(equations: List<Expr>, unknowns: List<String>, parameter: String): Cases? {
        val n = unknowns.size
        if (equations.size != n || n !in 2..4) return null
        val xs = unknowns.map { Expr.Sym(it) }
        // Coefficients: each equation's derivative in each unknown, free of every unknown; the rest is its constant.
        val rows = ArrayList<List<Expr>>()
        val rhs = ArrayList<Expr>()
        for (e in equations) {
            val row = xs.map { x -> tidy(Calculus.derivative(e, x)) }
            if (row.any { c -> xs.any { c.has(it) } }) return null
            rows += row
            rhs += tidy(neg(xs.fold(e) { acc, x -> acc.substitute(x, Expr.ZERO) }))
        }
        val a = Expr.Sym(parameter)
        val m = Expr.Mat(rows)
        val d = det(m)
        if (!d.has(a)) return null
        val zeros = Solve.equation(d, Expr.ZERO, a)
        if (zeros.always || zeros.numeric || zeros.periodic.isNotEmpty()) return null
        val excluded = zeros.values
        fun holds(solution: Map<String, Expr>, at: Expr?): Boolean = equations.all { e ->
            var v = e
            if (at != null) v = v.substitute(a, at)
            for ((name, value) in solution) v = v.substitute(Expr.Sym(name), value)
            isNull(tidy(v))
        }
        // The generic solution, by Cramer's rule with the determinant in the letter.
        val generic = unknowns.indices.associate { k ->
            val replaced = Expr.Mat(rows.mapIndexed { i, r -> r.mapIndexed { j, c -> if (j == k) rhs[i] else c } })
            unknowns[k] to tidy(det(replaced) / d)
        }
        if (!holds(generic, null)) return null
        val special = excluded.map { value -> case(rows, rhs, unknowns, a, value, equations) ?: return null }
        return Cases(parameter, unknowns, excluded, generic, special)
    }

    /** The system at a = [value]: its echelon form decides none, one, or a family in the free unknowns. */
    private fun case(rows: List<List<Expr>>, rhs: List<Expr>, unknowns: List<String>, a: Expr.Sym, value: Expr, equations: List<Expr>): Case? {
        val n = unknowns.size
        val augmented = Expr.Mat(rows.mapIndexed { i, r -> r.map { tidy(it.substitute(a, value)) } + tidy(rhs[i].substitute(a, value)) })
        val e = echelon(augmented)
        // A pivot in the last column: 0 = 1, no solution.
        if (n in e.pivots) return Case(value, null, emptyList())
        val freeColumns = (0 until n).filter { it !in e.pivots }
        val names = freeNames(unknowns, a.name, freeColumns.size)
        val solution = HashMap<String, Expr>()
        for ((k, column) in freeColumns.withIndex()) solution[unknowns[column]] = Expr.Sym(names[k])
        for ((row, pc) in e.pivots.withIndex()) {
            var v: Expr = e.form.rows[row][n]
            for ((k, column) in freeColumns.withIndex()) v = v - mul(e.form.rows[row][column], Expr.Sym(names[k]))
            solution[unknowns[pc]] = tidy(v)
        }
        val ordered = unknowns.associateWith { solution.getValue(it) }
        val ok = equations.all { eq ->
            var v = eq.substitute(a, value)
            for ((name, x) in ordered) v = v.substitute(Expr.Sym(name), x)
            isNull(tidy(v))
        }
        return if (ok) Case(value, ordered, names) else null
    }

    /** Letters for a family's free unknowns: t, s, r, … that the system does not use. */
    private fun freeNames(unknowns: List<String>, parameter: String, count: Int): List<String> =
        listOf("t", "s", "r", "u", "v", "w").filter { it !in unknowns && it != parameter }.take(count)

    // ---------------------------------------------------------------------------------------------------------------------
    // Decompositions, the column space, least squares, the exponential.

    private fun same(a: Expr.Mat, b: Expr.Mat): Boolean = a.size == b.size && a.columns == b.columns &&
        a.rows.indices.all { i -> a.rows[i].indices.all { j -> isNull(tidy(a.rows[i][j] - b.rows[i][j])) } }

    /**
     * P·A = L·U by elimination, a row swapped in where a pivot is 0: P a permutation, L with ones on its diagonal, U upper
     * triangular; checked P·A = L·U. Null where a column has no pivot left (U would not be what elimination gives).
     */
    fun lu(m: Expr.Mat): Triple<Expr.Mat, Expr.Mat, Expr.Mat>? {
        if (m.size != m.columns) return null
        val n = m.size
        val u = m.rows.map { it.toMutableList() }.toMutableList()
        val l = MutableList(n) { MutableList<Expr>(n) { Expr.ZERO } }
        val order = (0 until n).toMutableList()
        for (c in 0 until n) {
            Budget.check()
            val p = (c until n).firstOrNull { !isNull(u[it][c]) } ?: return null
            if (p != c) {
                val t = u[p]; u[p] = u[c]; u[c] = t
                val o = order[p]; order[p] = order[c]; order[c] = o
                for (k in 0 until c) { val v = l[p][k]; l[p][k] = l[c][k]; l[c][k] = v }
            }
            for (r in c + 1 until n) {
                val f = tidy(u[r][c] / u[c][c])
                l[r][c] = f
                u[r] = u[r].indices.map { j -> tidy(u[r][j] - mul(f, u[c][j])) }.toMutableList()
            }
        }
        for (i in 0 until n) l[i][i] = Expr.ONE
        val pm = Expr.Mat(List(n) { i -> List(n) { j -> if (order[i] == j) Expr.ONE else Expr.ZERO } })
        val lm = Expr.Mat(l)
        val um = Expr.Mat(u.map { r -> r.map { if (isNull(it)) Expr.ZERO else it } })
        return if (same(times(pm, m) as Expr.Mat, times(lm, um) as Expr.Mat)) Triple(pm, lm, um) else null
    }

    /**
     * A = Q·R by Gram–Schmidt on the columns: Q's columns orthonormal, R upper triangular with a positive diagonal; checked
     * A = Q·R. Null where the columns are not independent.
     */
    fun qr(m: Expr.Mat): Pair<Expr.Mat, Expr.Mat>? {
        if (m.columns > m.size) return null
        val k = m.columns
        val columns = (0 until k).map { j -> column(m.rows.map { it[j] }) }
        val q = ArrayList<Expr.Mat>()
        val r = MutableList(k) { MutableList<Expr>(k) { Expr.ZERO } }
        for (j in 0 until k) {
            Budget.check()
            var v = columns[j]
            for (i in 0 until j) {
                val rij = dot(q[i], columns[j])
                r[i][j] = rij
                v = plus(v, scale(q[i], neg(rij))) as Expr.Mat
            }
            val length = tidy(sqrt(dot(v, v)))
            if (isNull(length)) return null
            r[j][j] = length
            q += scale(v, tidy(pow(length, Expr.MINUS_ONE)))
        }
        val qm = Expr.Mat(List(m.size) { i -> q.map { it.rows[i][0] } })
        val rm = Expr.Mat(r)
        return if (same(times(qm, rm) as Expr.Mat, m)) qm to rm else null
    }

    /** A = L·Lᵀ for a symmetric positive definite A, L lower triangular with a positive diagonal; checked. Null otherwise. */
    fun cholesky(m: Expr.Mat): Expr.Mat? {
        if (m.size != m.columns || !same(m, transpose(m))) return null
        val n = m.size
        val l = MutableList(n) { MutableList<Expr>(n) { Expr.ZERO } }
        for (j in 0 until n) {
            Budget.check()
            val s = tidy(m.rows[j][j] - add((0 until j).map { k -> pow(l[j][k], Expr.TWO) }))
            val v = Numeric.value(s) ?: return null
            if (v <= 1e-12) return null
            l[j][j] = tidy(sqrt(s))
            for (i in j + 1 until n) {
                l[i][j] = tidy((m.rows[i][j] - add((0 until j).map { k -> mul(l[i][k], l[j][k]) })) / l[j][j])
            }
        }
        val lm = Expr.Mat(l)
        return if (same(times(lm, transpose(lm)) as Expr.Mat, m)) lm else null
    }

    /** The columns of A that the echelon form's pivots point at: a basis of A's column space. */
    fun columnSpace(m: Expr.Mat): List<Expr.Mat> = echelon(m).pivots.map { j -> column(m.rows.map { it[j] }) }

    /** The x that makes |A·x − b| least, from AᵀA·x = Aᵀb; null where AᵀA is singular. */
    fun leastSquares(a: Expr.Mat, b: Expr.Mat): Expr.Mat? {
        val at = transpose(a)
        val normal = times(at, a) as Expr.Mat
        val inverse = try { inverse(normal) } catch (e: CalcException) { if (e.failure == Failure.TOO_LONG) throw e; return null }
        return times(times(inverse, at), b) as Expr.Mat
    }

    /**
     * e^A: P·e^D·P⁻¹ where A can be diagonalized, e^λ·(I + N) where A = λI + N with N² = 0; checked against the series
     * Σ Aᵏ/k! in numbers. Null otherwise.
     */
    fun exp(m: Expr.Mat): Expr.Mat? {
        if (m.size != m.columns) return null
        val n = m.size
        val result: Expr.Mat = run {
            // 2 × 2 in closed form, real even where the eigenvalues are not: with τ = tr A/2 and δ = det A − τ²,
            // e^A = e^τ·(cos √δ·I + sin √δ/√δ·(A − τI)), cosh and sinh where δ < 0, e^τ·(I + A − τI) where δ = 0.
            val tau2 = if (n == 2) tidy(trace(m) / Expr.TWO) else Expr.ZERO
            val delta2 = if (n == 2) tidy(det(m) - pow(tau2, Expr.TWO)) else Expr.ZERO
            // Two rational eigenvalues: e^λ₁ and e^λ₂ read better than cosh and sinh (diag(0, 1) is diag(1, e)).
            val rationalRoots = n == 2 && (Numeric.value(delta2) ?: 1.0) < 0 && rational(tidy(sqrt(neg(delta2)))) != null
            if (n == 2 && !rationalRoots && m.symbols().isEmpty() && !m.hasI()) {
                val tau = tau2
                val delta = delta2
                val shifted = Expr.Mat(m.rows.mapIndexed { i, r -> r.mapIndexed { j, v -> if (i == j) tidy(v - tau) else v } })
                val dv = Numeric.value(delta)
                val (c, s) = when {
                    isNull(delta) -> Expr.ONE to Expr.ONE
                    dv != null && dv > 0 -> sqrt(delta).let { r -> fn(Func.COS, r) to tidy(fn(Func.SIN, r) / r) }
                    dv != null -> sqrt(neg(delta)).let { r -> fn(Func.COSH, r) to tidy(fn(Func.SINH, r) / r) }
                    else -> return null
                }
                return@run scale(plus(scale(identity(2), c), scale(shifted, s)) as Expr.Mat, tidy(exp(tau)))
            }
            val eigen = eigen(m)
            val diagonal = eigen?.let { diagonalization(m, it) }
            if (diagonal != null) {
                val (p, d) = diagonal
                val ed = Expr.Mat(List(n) { i -> List(n) { j -> if (i == j) tidy(exp(d.rows[i][i])) else Expr.ZERO } })
                return@run times(times(p, ed), inverse(p)) as Expr.Mat
            }
            val single = eigen?.singleOrNull() ?: return null
            val nilpotent = Expr.Mat(m.rows.mapIndexed { i, r -> r.mapIndexed { j, v -> if (i == j) tidy(v - single.value) else v } })
            val square = times(nilpotent, nilpotent) as Expr.Mat
            if (!square.rows.all { r -> r.all { isNull(it) } }) return null
            scale(plus(identity(n), nilpotent) as Expr.Mat, tidy(exp(single.value)))
        }
        // Σ Aᵏ/k! in doubles, to 40 terms.
        if (m.rows.any { r -> r.any { it.symbols().isNotEmpty() || it.hasI() } }) return result
        val a = Array(n) { i -> DoubleArray(n) { j -> Numeric.value(m.rows[i][j]) ?: return null } }
        var term = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
        val sum = Array(n) { i -> term[i].copyOf() }
        for (k in 1..40) {
            val next = Array(n) { i -> DoubleArray(n) { j -> (0 until n).sumOf { t -> term[i][t] * a[t][j] } / k } }
            term = next
            for (i in 0 until n) for (j in 0 until n) sum[i][j] += next[i][j]
        }
        for (i in 0 until n) for (j in 0 until n) {
            val v = Numeric.value(result.rows[i][j]) ?: return null
            if (abs(v - sum[i][j]) > 1e-8 * maxOf(1.0, abs(sum[i][j]))) return null
        }
        return result
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Vector calculus.

    /** ∇f = (∂f/∂x, ∂f/∂y, …). */
    fun gradient(f: Expr, names: List<String>): Expr.Mat = column(names.map { tidy(Calculus.derivative(f, Expr.Sym(it))) })

    /** div F = ∂P/∂x + ∂Q/∂y (+ ∂R/∂z). */
    fun divergence(field: Expr.Mat, names: List<String>): Expr =
        tidy(add(names.indices.map { k -> Calculus.derivative(field.rows[k][0], Expr.Sym(names[k])) }))

    /** curl F of a field in x, y, z; in the plane its one component ∂Q/∂x − ∂P/∂y. */
    fun curl(field: Expr.Mat, names: List<String>): Expr {
        fun d(k: Int, by: Int) = Calculus.derivative(field.rows[k][0], Expr.Sym(names[by]))
        if (names.size == 2) return tidy(d(1, 0) - d(0, 1))
        return column(listOf(tidy(d(2, 1) - d(1, 2)), tidy(d(0, 2) - d(2, 0)), tidy(d(1, 0) - d(0, 1))))
    }

    /** The Jacobian matrix: ∂F_i/∂x_j. */
    fun jacobian(field: Expr.Mat, names: List<String>): Expr.Mat =
        Expr.Mat(field.rows.map { r -> names.map { tidy(Calculus.derivative(r[0], Expr.Sym(it))) } })

    /** Δf = ∂²f/∂x² + ∂²f/∂y² (+ ∂²f/∂z²). */
    fun laplacian(f: Expr, names: List<String>): Expr =
        tidy(add(names.map { Calculus.derivative(f, Expr.Sym(it), 2) }))

    // ---------------------------------------------------------------------------------------------------------------------
    // Extrema under a constraint.

    /** A candidate of a constrained extremum: where, the value there, and the multipliers. */
    class Candidate(val at: Map<String, Expr>, val value: Expr, val multipliers: List<Expr>)

    /**
     * The points where f can have an extremum under the constraints g = 0, by Lagrange's multipliers: ∇f = Σ λᵢ·∇gᵢ with every
     * gᵢ = 0, solved together (a system of polynomials by its Gröbner basis); each real point put back into every equation.
     * Null where the system cannot be solved.
     */
    fun lagrange(f: Expr, constraints: List<Expr>, names: List<String>): List<Candidate>? {
        val lambdas = constraints.indices.map { Expr.Sym(if (constraints.size == 1) "λ" else "λ_${it + 1}") }
        val equations = ArrayList<Expr>()
        for (x in names) {
            val v = Expr.Sym(x)
            equations += tidy(Calculus.derivative(f, v) - add(constraints.indices.map { i -> mul(lambdas[i], Calculus.derivative(constraints[i], v)) }))
        }
        equations += constraints
        val unknowns = names + lambdas.map { it.name }
        val solutions = Solve.system(equations, unknowns) ?: return null
        val out = ArrayList<Candidate>()
        for (s in solutions) {
            if (unknowns.any { s[it] == null || s.getValue(it).symbols().isNotEmpty() || s.getValue(it).hasI() }) continue
            fun at(e: Expr) = tidy(unknowns.fold(e) { acc, n -> acc.substitute(Expr.Sym(n), s.getValue(n)) })
            if (!equations.all { isNull(at(it)) }) continue
            out += Candidate(names.associateWith { s.getValue(it) }, at(f), lambdas.map { s.getValue(it.name) })
        }
        return out
    }

    /** A vector with its fractions cleared where its entries are rational: (1/2, 1) as (1, 2). */
    private fun cleared(v: Expr.Mat): Expr.Mat {
        val entries = v.rows.map { it[0] }
        val rationals = entries.map { rational(it) }
        if (rationals.any { it == null }) return v
        var lcm = java.math.BigInteger.ONE
        for (r in rationals) lcm = lcm.multiply(r!!.den).divide(lcm.gcd(r.den))
        var gcd = java.math.BigInteger.ZERO
        for (r in rationals) gcd = gcd.gcd(r!!.num.multiply(lcm).divide(r.den))
        if (gcd.signum() == 0) return v
        return column(rationals.map { num(Rational.of(it!!.num.multiply(lcm).divide(it.den).divide(gcd))) })
    }
}
