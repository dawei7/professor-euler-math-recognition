package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * A matrix's functions of its own beside its reduced row echelon form ([LinAlg.echelon], whose code they use): the rank, the
 * trace, a basis of the null space and of the column space, the characteristic polynomial and the adjugate. Each is checked
 * by a second way before it is shown, and declines ([Failure.UNSOLVED]) where a check fails:
 * - the rank r both ways: a minor of size r is not 0 (on r independent rows and the pivot columns), and n − r independent
 *   vectors have A·v = 0; with one letter, case by case, every special value worked out again;
 * - the null space: each vector A·v = 0, independent, as many as n − rank;
 * - the column space: the columns at the pivots, independent (the same minor), as many as the rank;
 * - the characteristic polynomial (Faddeev–LeVerrier, [LinAlg.characteristic]) against the sums of the principal minors, the
 *   coefficient of λⁿ⁻ᵏ being (−1)ᵏ times the sum of the k × k ones;
 * - the adjugate (cofactors) by A·adj(A) = adj(A)·A = det(A)·I, which decides it where det(A) ≠ 0; where det(A) = 0, against
 *   det(A + tI)·(A + tI)⁻¹ at n values of t, interpolated to t = 0 (the adjugate is a polynomial in t of degree below n).
 */
internal object MatrixFunctions {
    private fun tidy(e: Expr) = Calculus.tidy(e)

    fun of(mode: Mode, m: Expr.Mat, letter: String?): Outcome = when (mode) {
        Mode.RANK -> rank(m)
        Mode.TRACE -> trace(m)
        Mode.NULL_SPACE -> nullSpace(m)
        Mode.COLUMN_SPACE -> columnSpace(m)
        Mode.CHARACTERISTIC_POLYNOMIAL -> Outcome.Expression(characteristic(m, letter))
        else -> Outcome.Expression(adjugate(m))
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Rank.

    private fun rank(m: Expr.Mat): Outcome {
        val letters = m.symbols()
        if (letters.isEmpty()) return Outcome.Value(Num.Exact(Rational.of(certified(m).rank.toLong())))
        if (letters.size > 1) throw CalcException(Failure.SEVERAL_UNKNOWNS)
        return rankCases(m, letters.single())
    }

    /** A rank and what shows it: the pivot columns, r independent rows, and a basis of the null space. */
    class Certified(val rank: Int, val pivots: List<Int>, val rows: List<Int>, val kernel: List<Expr.Mat>)

    /**
     * The rank of [m], shown both ways: at least r, as the r × r minor on r independent rows (the pivots of Aᵀ) and the pivot
     * columns is not 0; at most r, as n − r independent vectors have A·v = 0. Over the letters' rational functions where it has
     * letters (its rank for almost every value).
     */
    fun certified(m: Expr.Mat): Certified {
        val e = LinAlg.echelon(m)
        val r = e.rank
        val rows = LinAlg.echelon(LinAlg.transpose(m)).pivots
        if (rows.size != r) throw CalcException(Failure.UNSOLVED)
        if (r > 0 && LinAlg.isNull(LinAlg.det(Expr.Mat(rows.map { i -> e.pivots.map { j -> m.rows[i][j] } })))) throw CalcException(Failure.UNSOLVED)
        val kernel = LinAlg.nullSpace(m).map { cleared(it) }
        if (kernel.size != m.columns - r || !isKernel(m, kernel, e.pivots)) throw CalcException(Failure.UNSOLVED)
        return Certified(r, e.pivots, rows, kernel)
    }

    /** Each vector solves A·v = 0, and they are independent: on the columns that are no pivots their entries' determinant is not 0. */
    private fun isKernel(m: Expr.Mat, kernel: List<Expr.Mat>, pivots: List<Int>): Boolean {
        for (v in kernel) {
            Budget.check()
            val av = LinAlg.times(m, v) as Expr.Mat
            if (!av.rows.all { LinAlg.isNull(it[0]) }) return false
        }
        if (kernel.isEmpty()) return true
        val free = (0 until m.columns).filter { it !in pivots }
        return !LinAlg.isNull(LinAlg.det(Expr.Mat(free.map { i -> kernel.map { it.rows[i][0] } })))
    }

    /** A vector with whole entries where its entries are fractions of numbers: (1/2, −1, 1/2) as (1, −2, 1). */
    private fun cleared(v: Expr.Mat): Expr.Mat {
        val entries = v.rows.map { it[0] }
        val rationals = entries.map { rational(it) ?: return v }
        val lcm = rationals.fold(BigInteger.ONE) { acc, r -> acc / acc.gcd(r.den) * r.den }
        val scaled = rationals.map { it * Rational.of(lcm) }
        val gcd = scaled.fold(BigInteger.ZERO) { acc, r -> acc.gcd(r.num) }.takeIf { it.signum() > 0 } ?: return v
        return LinAlg.column(scaled.map { num(it / Rational.of(gcd)) })
    }

    /**
     * The rank of a matrix in one letter [k] for every real value of it: the rank r over the rational functions in k, which every
     * value has but those where the certifying minor is 0; each of those worked out on its own (checked as [certified]), and
     * the values where an entry is not defined. Entries must be fractions of polynomials in k; the minor's zeros found exactly.
     */
    private fun rankCases(m: Expr.Mat, k: String): Outcome {
        val kk = Expr.Sym(k)
        val undefined = ArrayList<Expr>()
        for (row in m.rows) for (entry in row) {
            val kernels = Algebra.Kernels()
            val (_, den) = Algebra.fraction(entry, kernels) ?: throw CalcException(Failure.UNSOLVED)
            if (!kernels.isEmpty) throw CalcException(Failure.UNSOLVED)
            val d = Algebra.toExpr(den, kernels)
            if (d.has(kk)) undefined += realRoots(d, kk)
        }
        val c = certified(m)
        val r = c.rank
        val minor = if (r == 0) Expr.ONE else tidy(LinAlg.det(Expr.Mat(c.rows.map { i -> c.pivots.map { j -> m.rows[i][j] } })))
        val candidates = if (minor.has(kk)) realRoots(minor, kk) else emptyList()
        val special = ArrayList<Pair<Expr, Int>>()
        val skip = undefined.distinctBy { key(it) }
        for (v in candidates.distinctBy { key(it) }.sortedBy { Numeric.value(it) ?: 0.0 }) {
            if (skip.any { key(it) == key(v) }) continue
            val at = Expr.Mat(m.rows.map { row -> row.map { tidy(it.substitute(kk, v)) } })
            val rv = certified(at).rank
            if (rv > r) throw CalcException(Failure.UNSOLVED)
            if (rv < r) special += v to rv
        }
        // The rank r at values of k that are none of these, worked out on their own.
        val random = java.util.Random(7L * m.hashCode() + 3)
        var tried = 0
        while (tried < 3) {
            Budget.check()
            val v = num(Rational.of((random.nextInt(2000) - 1000).toLong()) / Rational.of(97))
            if ((candidates + skip).any { key(it) == key(v) }) continue
            val at = Expr.Mat(m.rows.map { row -> row.map { tidy(it.substitute(kk, v)) } })
            if (certified(at).rank != r) throw CalcException(Failure.UNSOLVED)
            tried++
        }
        return Outcome.RankCases(k, r, special, skip.sortedBy { Numeric.value(it) ?: 0.0 })
    }

    private fun key(e: Expr) = Numeric.value(e)?.let { Math.round(it * 1e9) } ?: e.hashCode().toLong()

    /** The real values where [e] (a fraction of polynomials in [x]) is 0: found exactly, or the cases are not said. */
    private fun realRoots(e: Expr, x: Expr.Sym): List<Expr> {
        val kernels = Algebra.Kernels()
        val (numerator, _) = Algebra.fraction(e, kernels) ?: throw CalcException(Failure.UNSOLVED)
        val p = Algebra.toExpr(numerator, kernels)
        if (!p.has(x)) return emptyList()
        val s = Solve.equation(p, Expr.ZERO, x)
        if (s.numeric || s.many || s.always || s.periodic.isNotEmpty() || s.values.any { it is Expr.Real || it.hasDecimal() }) throw CalcException(Failure.UNSOLVED)
        return s.values
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Trace.

    private fun trace(m: Expr.Mat): Outcome {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        val t = LinAlg.trace(m)
        // Again, as the sum of eᵢ·(A·eᵢ): the matrix times each unit vector.
        val again = tidy(add(m.rows.indices.map { i ->
            val unit = LinAlg.column(List(m.size) { j -> if (i == j) Expr.ONE else Expr.ZERO })
            (LinAlg.times(m, unit) as Expr.Mat).rows[i][0]
        }))
        if (!LinAlg.isNull(tidy(t - again))) throw CalcException(Failure.UNSOLVED)
        return Applied.number(t)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Null space and column space.

    private fun nullSpace(m: Expr.Mat): Outcome {
        if (m.symbols().isNotEmpty()) throw CalcException(Failure.UNSOLVED)
        val c = certified(m)
        return Outcome.Basis(c.kernel, nullSpace = true, dimension = c.kernel.size, space = m.columns)
    }

    private fun columnSpace(m: Expr.Mat): Outcome {
        if (m.symbols().isNotEmpty()) throw CalcException(Failure.UNSOLVED)
        // The pivot columns: independent (their minor on the certified rows is not 0) and as many as the rank, so a basis.
        val c = certified(m)
        val columns = c.pivots.map { j -> LinAlg.column(m.rows.map { it[j] }) }
        return Outcome.Basis(columns, nullSpace = false, dimension = c.rank, space = m.size)
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Characteristic polynomial.

    /** det(λI − A) in λ (or in [letter]), checked against the sums of the principal minors. */
    fun characteristic(m: Expr.Mat, letter: String?): Expr {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        val lambda = LinAlg.LAMBDA
        val named = letter?.let { Expr.Sym(it) } ?: lambda
        if (m.symbols().any { it == lambda.name || it == named.name }) throw CalcException(Failure.ARGUMENTS)
        val p = LinAlg.characteristic(m)
        val n = m.size
        // λⁿ − E₁λⁿ⁻¹ + E₂λⁿ⁻² − …: Eₖ the sum of the k × k principal minors.
        val terms = ArrayList<Expr>()
        terms += pow(lambda, num(n.toLong()))
        for (k in 1..n) {
            val sum = add(subsets(n, k).map { s ->
                Budget.check()
                LinAlg.det(Expr.Mat(s.map { i -> s.map { j -> m.rows[i][j] } }))
            })
            val sign = if (k % 2 == 0) Expr.ONE else Expr.MINUS_ONE
            terms += mul(sign, sum, pow(lambda, num((n - k).toLong())))
        }
        val again = tidy(Algebra.expand(add(terms)))
        if (!LinAlg.isNull(tidy(Algebra.expand(p - again)))) throw CalcException(Failure.UNSOLVED)
        return if (named == lambda) p else tidy(p.substitute(lambda, named))
    }

    /** The k-element subsets of 0 until n, each in rising order. */
    private fun subsets(n: Int, k: Int): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        fun walk(start: Int, chosen: List<Int>) {
            if (chosen.size == k) { out += chosen; return }
            for (i in start until n) walk(i + 1, chosen + i)
        }
        walk(0, emptyList())
        return out
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Adjugate.

    /** The cofactor matrix's transpose, checked: A·adj(A) = adj(A)·A = det(A)·I, and where det(A) = 0 by interpolation. */
    fun adjugate(m: Expr.Mat): Expr.Mat {
        if (m.size != m.columns) throw CalcException(Failure.SIZES)
        val adj = cofactors(m)
        val det = tidy(LinAlg.det(m))
        if (!isScalar(LinAlg.times(m, adj) as Expr.Mat, det) || !isScalar(LinAlg.times(adj, m) as Expr.Mat, det)) throw CalcException(Failure.UNSOLVED)
        // det(A) ≠ 0: A·X = det(A)·I has the one solution X = det(A)·A⁻¹, so the products decide it. Else interpolated.
        if (LinAlg.isNull(det) && !same(adj, interpolated(m))) throw CalcException(Failure.UNSOLVED)
        return adj
    }

    private fun cofactors(m: Expr.Mat): Expr.Mat {
        val n = m.size
        if (n == 1) return Expr.Mat(listOf(listOf(Expr.ONE)))
        // adj(A)ᵢⱼ = (−1)ⁱ⁺ʲ · Mⱼᵢ, the minor without row j and column i.
        return Expr.Mat(List(n) { i ->
            List(n) { j ->
                Budget.check()
                val minor = Expr.Mat(m.rows.filterIndexed { r, _ -> r != j }.map { row -> row.filterIndexed { c, _ -> c != i } })
                tidy(mul(if ((i + j) % 2 == 0) Expr.ONE else Expr.MINUS_ONE, LinAlg.det(minor)))
            }
        })
    }

    private fun isScalar(p: Expr.Mat, d: Expr): Boolean = p.rows.withIndex().all { (i, row) ->
        row.withIndex().all { (j, x) -> LinAlg.isNull(tidy(x - if (i == j) d else Expr.ZERO)) }
    }

    private fun same(a: Expr.Mat, b: Expr.Mat): Boolean = a.rows.indices.all { i -> a.rows[i].indices.all { j -> LinAlg.isNull(tidy(a.rows[i][j] - b.rows[i][j])) } }

    /**
     * adj(A) from Gauss–Jordan inverses: det(A + tI)·(A + tI)⁻¹ is adj(A + tI), a polynomial in t of degree below n, so its
     * values at n values of t where A + tI is invertible give its value at t = 0 by Lagrange's formula.
     */
    private fun interpolated(m: Expr.Mat): Expr.Mat {
        val n = m.size
        val ts = ArrayList<Long>()
        val values = ArrayList<Expr.Mat>()
        var t = 1L
        while (ts.size < n) {
            Budget.check()
            if (t > 4L * n + 8) throw CalcException(Failure.UNSOLVED)
            val shifted = Expr.Mat(m.rows.mapIndexed { i, row -> row.mapIndexed { j, x -> if (i == j) tidy(x + num(t)) else x } })
            val d = tidy(LinAlg.det(shifted))
            if (!LinAlg.isNull(d)) {
                ts += t
                values += LinAlg.scale(LinAlg.inverse(shifted), d)
            }
            t++
        }
        return Expr.Mat(List(n) { i ->
            List(n) { j ->
                tidy(add(ts.indices.map { a ->
                    val weight = ts.indices.filter { it != a }.fold(Rational.ONE) { acc, b ->
                        acc * (Rational.of(-ts[b]) / Rational.of(ts[a] - ts[b]))
                    }
                    mul(num(weight), values[a].rows[i][j])
                }))
            }
        })
    }
}
