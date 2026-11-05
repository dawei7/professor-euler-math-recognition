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
