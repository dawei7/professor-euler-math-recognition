package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max

/**
 * The singular value decomposition A = U·Σ·Vᵀ and the Jordan normal form A = P·J·P⁻¹, each multiplied back before it is shown.
 *
 * The SVD is exact where the eigenvalues of AᵀA are found exactly and its factors stay short; otherwise it is worked out in
 * 60 decimal digits by Jacobi's method (the cyclic Jacobi method for the symmetric eigenvalue problem, as Golub and Van Loan
 * describe it), its singular values certified by the residual of the computed factors (Weyl's inequality for the residual,
 * Ostrowski's for factors that are orthogonal only to rounding) and shown to ten digits; U and V are shown as they multiply back.
 * The Jordan form needs exact eigenvalues: its blocks come from the ranks of (A − λI)ᵏ, its chains from their kernels.
 */
object MatrixForms {
    /**
     * A = [u]·[sigma]·[v]ᵀ: U (m × m) and V (n × n) orthogonal, Σ (m × n) with the singular values [values] σ₁ ≥ σ₂ ≥ … ≥ 0 on its
     * diagonal, [rank] of them not 0. [exact] where every entry is exact; otherwise the entries are decimals, the singular values
     * certified to the ten digits shown and U, V as they multiply back to A within those digits. [valuesExact] where the singular
     * values are exact all the same (√(15 + √221)), found from the exact eigenvalues of AᵀA and agreeing with the certified decimals.
     */
    class Svd(val u: Expr.Mat, val sigma: Expr.Mat, val v: Expr.Mat, val values: List<Expr>, val rank: Int, val exact: Boolean,
        val valuesExact: Boolean = exact)

    /** One Jordan block: its eigenvalue and its size. */
    data class Block(val value: Expr, val size: Int)

    /**
     * A = [p]·[j]·P⁻¹ with J in Jordan normal form: its [blocks] down its diagonal, each an eigenvalue with ones above it; P's
     * columns the Jordan chains, an eigenvector first. [inverse] is P⁻¹. [diagonalizable] where every block has size 1.
     */
    class Jordan(val j: Expr.Mat, val p: Expr.Mat, val inverse: Expr.Mat, val blocks: List<Block>) {
        val diagonalizable: Boolean get() = blocks.all { it.size == 1 }
    }

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** Entries longer than this make an exact SVD unreadable: the decimals are shown instead. */
    private const val LONGEST = 40

    /** How long the exact SVD may take before the decimals are worked out. */
    private const val EXACT_MS = 1200L

    // ---------------------------------------------------------------------------------------------------------------------
    // The singular value decomposition.

    fun svd(a: Expr.Mat): Svd {
        if (a.symbols().isNotEmpty()) throw CalcException(Failure.NOT_POLYNOMIAL)
        if (a.hasI() || a.rows.any { r -> r.any { Numeric.value(it) == null } }) throw CalcException(Failure.UNSOLVED)
        // A wide matrix through its transpose: Aᵀ = U'Σ'V'ᵀ is A = V'Σ'ᵀU'ᵀ.
        if (a.size < a.columns) {
            val t = svd(LinAlg.transpose(a))
            return Svd(t.v, LinAlg.transpose(t.sigma), t.u, t.values, t.rank, t.exact)
        }
        // The exact attempt has a part of the time; past it, or where it fails, the decimals.
        val exact = try {
            Budget.within(EXACT_MS) { exactSvd(a) }
        } catch (e: CalcException) {
            null
        }
        if (exact != null) return exact
        val numeric = numericSvd(a) ?: throw CalcException(Failure.UNSOLVED)
        val values = try {
            Budget.within(EXACT_MS / 2) { exactValues(a, numeric.values) }
        } catch (e: CalcException) {
            null
        } ?: return numeric
        val m = a.size
        val n = a.columns
        val sigma = Expr.Mat(List(m) { i -> List(n) { j -> if (i == j) values[i] else Expr.ZERO } })
        return Svd(numeric.u, sigma, numeric.v, values, numeric.rank, exact = false, valuesExact = true)
    }

    /**
     * The singular values exactly, √λ of the exact eigenvalues λ of AᵀA, where each is short and agrees with its certified
     * decimal; null otherwise.
     */
    private fun exactValues(a: Expr.Mat, decimals: List<Expr>): List<Expr>? {
        if (a.columns > 4) return null
        val g = LinAlg.times(LinAlg.transpose(a), a) as Expr.Mat
        val eigen = LinAlg.eigen(g) ?: return null
        if (eigen.any { it.value.hasI() }) return null
        val lambdas = eigen.flatMap { e -> List(e.multiplicity) { e.value } }.sortedByDescending { Numeric.value(it) ?: 0.0 }
        if (lambdas.size != decimals.size) return null
        val values = lambdas.map { l -> if (LinAlg.isNull(l)) Expr.ZERO else tidy(Algebra.rationalize(tidy(sqrt(l)))) }
        for ((k, v) in values.withIndex()) {
            if (v.size() > 36) return null
            val d = Numeric.value(decimals[k]) ?: return null
            val e = Numeric.value(v) ?: return null
            if (isZero(decimals[k]) != isZero(v)) return null
            if (abs(e - d) > 1e-9 * max(1.0, abs(d))) return null
        }
        return values
    }

    /** Orthonormal vectors spanning the same space as [vs], exactly (orthogonal first, in rational arithmetic, then each divided by its length). */
    internal fun orthonormal(vs: List<Expr.Mat>, against: List<Expr.Mat> = emptyList()): List<Expr.Mat>? {
        val done = ArrayList<Expr.Mat>()
        val basis = ArrayList(against)
        for (w in vs) {
            Budget.check()
            var v: Expr.Mat = w
            for (q in basis + done) {
                val qq = dot(q, q)
                if (LinAlg.isNull(qq)) return null
                v = LinAlg.plus(v, LinAlg.scale(q, tidy(neg(dot(q, w)) / qq))) as Expr.Mat
            }
            if (v.rows.all { LinAlg.isNull(it[0]) }) continue
            done += v
        }
        return done.map { v -> LinAlg.scale(v, tidy(pow(tidy(sqrt(dot(v, v))), Expr.MINUS_ONE))).let { u -> Expr.Mat(u.rows.map { r -> r.map { tidy(Algebra.rationalize(it)) } }) } }
    }

    /** The dot product of two columns of one length, one entry long too. */
    private fun dot(a: Expr.Mat, b: Expr.Mat): Expr = tidy(add(a.rows.indices.map { mul(a.rows[it][0], b.rows[it][0]) }))

    private fun columns(vs: List<Expr.Mat>, rows: Int): Expr.Mat = Expr.Mat(List(rows) { i -> vs.map { it.rows[i][0] } })

    private fun isIdentity(m: Expr.Mat): Boolean = m.rows.withIndex().all { (i, r) -> r.withIndex().all { (j, x) -> LinAlg.isNull(tidy(x - if (i == j) Expr.ONE else Expr.ZERO)) } }

    private fun same(a: Expr.Mat, b: Expr.Mat): Boolean = a.size == b.size && a.columns == b.columns &&
        a.rows.indices.all { i -> a.rows[i].indices.all { j -> LinAlg.isNull(tidy(a.rows[i][j] - b.rows[i][j])) } }

    /** U, Σ, V from the exact eigenvalues and eigenvectors of AᵀA (m ≥ n), checked: U·Σ·Vᵀ = A, UᵀU = I, VᵀV = I. */
    private fun exactSvd(a: Expr.Mat): Svd? {
        val m = a.size
        val n = a.columns
        if (n > 4) return null
        val g = LinAlg.times(LinAlg.transpose(a), a) as Expr.Mat
        val eigen = LinAlg.eigen(g) ?: return null
        val pairs = ArrayList<Pair<Expr, Expr.Mat>>()
        for (e in eigen) {
            if (e.value.hasI()) return null
            val lv = Numeric.value(e.value) ?: return null
            if (lv < -1e-9) return null
            if (e.vectors.size != e.multiplicity) return null
            for (q in orthonormal(e.vectors) ?: return null) pairs += e.value to q
        }
        if (pairs.size != n) return null
        pairs.sortByDescending { Numeric.value(it.first) ?: 0.0 }
        val sigmas = pairs.map { (lambda, _) -> if (LinAlg.isNull(lambda)) Expr.ZERO else tidy(Algebra.rationalize(tidy(sqrt(lambda)))) }
        val rank = sigmas.count { !isZero(it) }
        val vs = pairs.map { positiveFirst(it.second) }
        // u = A·v/σ for the singular values that are not 0; the rest of U an orthonormal basis of the null space of Aᵀ.
        val us = ArrayList<Expr.Mat>()
        for (k in 0 until rank) {
            val av = LinAlg.times(a, vs[k]) as Expr.Mat
            us += Expr.Mat(av.rows.map { r -> listOf(tidy(Algebra.rationalize(tidy(r[0] / sigmas[k])))) })
        }
        if (us.size < m) {
            val rest = orthonormal(LinAlg.nullSpace(LinAlg.transpose(a)), us) ?: return null
            us += rest.map { positiveFirst(it) }
        }
        if (us.size != m) return null
        val u = columns(us, m)
        val v = columns(vs, n)
        val s = Expr.Mat(List(m) { i -> List(n) { j -> if (i == j) sigmas[i] else Expr.ZERO } })
        if ((u.rows + v.rows + s.rows).any { r -> r.any { it.size() > LONGEST } }) return null
        val product = LinAlg.times(LinAlg.times(u, s), LinAlg.transpose(v)) as Expr.Mat
        if (!same(product, a)) return null
        if (!isIdentity(LinAlg.times(LinAlg.transpose(u), u) as Expr.Mat) || !isIdentity(LinAlg.times(LinAlg.transpose(v), v) as Expr.Mat)) return null
        return Svd(u, s, v, sigmas, rank, exact = true)
    }

    /** The vector with its first entry that is not 0 made positive. */
    private fun positiveFirst(v: Expr.Mat): Expr.Mat {
        val first = v.rows.map { it[0] }.firstOrNull { !LinAlg.isNull(it) } ?: return v
        return if ((Numeric.value(first) ?: 0.0) < 0) LinAlg.scale(v, Expr.MINUS_ONE) else v
    }

    // In 60 digits.

    private val MC = MathContext(60, RoundingMode.HALF_EVEN)
    private val TINY = BigDecimal("1e-55")

    private fun big(e: Expr): Pair<BigDecimal, Double>? {
        val r = rational(e)
        if (r != null) return BigDecimal(r.num).divide(BigDecimal(r.den), MC) to 0.0
        val d = Numeric.value(e) ?: return null
        // Not rational (√2): its double, within a few ulps of the value (64 of them allowed).
        return BigDecimal(d) to 64 * Math.ulp(d)
    }

    private fun sqrtBig(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) return BigDecimal.ZERO
        var r = BigDecimal(kotlin.math.sqrt(x.toDouble()))
        if (r.signum() == 0) r = BigDecimal.ONE.movePointLeft(150)
        repeat(8) { r = r.add(x.divide(r, MC), MC).divide(BigDecimal(2), MC) }
        return r
    }

    private fun mulMat(a: Array<Array<BigDecimal>>, b: Array<Array<BigDecimal>>): Array<Array<BigDecimal>> =
        Array(a.size) { i -> Array(b[0].size) { j -> var s = BigDecimal.ZERO; for (k in b.indices) s = s.add(a[i][k].multiply(b[k][j], MC), MC); s } }

    private fun transposed(a: Array<Array<BigDecimal>>): Array<Array<BigDecimal>> = Array(a[0].size) { j -> Array(a.size) { i -> a[i][j] } }

    /** The Frobenius norm of a − b (b null: of a − I), as a double rounded up. */
    private fun distance(a: Array<Array<BigDecimal>>, b: Array<Array<BigDecimal>>?): Double {
        var s = BigDecimal.ZERO
        for (i in a.indices) for (j in a[i].indices) {
            val want = b?.get(i)?.get(j) ?: (if (i == j) BigDecimal.ONE else BigDecimal.ZERO)
            val d = a[i][j].subtract(want, MC)
            s = s.add(d.multiply(d, MC), MC)
        }
        return kotlin.math.sqrt(s.toDouble()) * (1 + 1e-12) + 1e-58
    }

    /** σ rounded to ten significant digits, if every number within [bound] of it rounds alike; else null. */
    private fun tenDigits(x: BigDecimal, bound: Double): BigDecimal? {
        val ten = MathContext(10, RoundingMode.HALF_EVEN)
        val b = BigDecimal(bound)
        val lo = x.subtract(b, MC).round(ten)
        val hi = x.add(b, MC).round(ten)
        return if (lo.compareTo(hi) == 0) hi else null
    }

    /**
     * The SVD (m ≥ n) in 60 digits: the cyclic Jacobi method on AᵀA for V and the singular values, u = A·v/σ for the rank's
     * columns (the rank found exactly by elimination), the rest of U by Gram–Schmidt. Certified: |σᵢ − σ̃ᵢ| ≤ ‖A − ŨΣ̃Ṽᵀ‖ +
     * σ̃ᵢ((1 + ‖ŨᵀŨ − I‖)(1 + ‖ṼᵀṼ − I‖) − 1) + ‖A − Ã‖; a value is shown only where that bound leaves its ten digits.
     */
    private fun numericSvd(a: Expr.Mat): Svd? {
        val m = a.size
        val n = a.columns
        var inputError = 0.0
        val am = Array(m) { i -> Array(n) { j -> val (b, err) = big(a.rows[i][j]) ?: return null; inputError += err * err; b } }
        inputError = kotlin.math.sqrt(inputError) * 2
        val rank = LinAlg.rank(a)
        val g = mulMat(transposed(am), am)
        val v = Array(n) { i -> Array(n) { j -> if (i == j) BigDecimal.ONE else BigDecimal.ZERO } }
        // Cyclic Jacobi rotations until every off-diagonal entry is below 10⁻⁵⁵ of the diagonal's size.
        var scale = BigDecimal.ZERO
        for (i in 0 until n) scale = scale.max(g[i][i].abs())
        if (scale.signum() == 0) scale = BigDecimal.ONE
        val tol = scale.multiply(TINY, MC)
        for (sweep in 0 until 60) {
            var off = false
            for (p in 0 until n) for (q in p + 1 until n) {
                Budget.check()
                if (g[p][q].abs() <= tol) continue
                off = true
                val theta = g[q][q].subtract(g[p][p], MC).divide(g[p][q].multiply(BigDecimal(2), MC), MC)
                val root = sqrtBig(theta.multiply(theta, MC).add(BigDecimal.ONE, MC))
                val t = BigDecimal.ONE.divide(theta.abs().add(root, MC), MC).let { if (theta.signum() < 0) it.negate() else it }
                val c = BigDecimal.ONE.divide(sqrtBig(t.multiply(t, MC).add(BigDecimal.ONE, MC)), MC)
                val s = t.multiply(c, MC)
                for (k in 0 until n) {
                    val gkp = g[k][p]
                    val gkq = g[k][q]
                    g[k][p] = c.multiply(gkp, MC).subtract(s.multiply(gkq, MC), MC)
                    g[k][q] = s.multiply(gkp, MC).add(c.multiply(gkq, MC), MC)
                }
                for (k in 0 until n) {
                    val gpk = g[p][k]
                    val gqk = g[q][k]
                    g[p][k] = c.multiply(gpk, MC).subtract(s.multiply(gqk, MC), MC)
                    g[q][k] = s.multiply(gpk, MC).add(c.multiply(gqk, MC), MC)
                }
                for (k in 0 until n) {
                    val vkp = v[k][p]
                    val vkq = v[k][q]
                    v[k][p] = c.multiply(vkp, MC).subtract(s.multiply(vkq, MC), MC)
                    v[k][q] = s.multiply(vkp, MC).add(c.multiply(vkq, MC), MC)
                }
            }
            if (!off) break
        }
        val order = (0 until n).sortedByDescending { g[it][it] }
        val sigma = order.map { k -> sqrtBig(g[k][k].max(BigDecimal.ZERO)) }
        val vCols = order.map { k -> Array(n) { i -> v[i][k] } }
        // U: A·v/σ for the rank's columns, then Gram–Schmidt (twice) on the unit vectors for the rest.
        val uCols = ArrayList<Array<BigDecimal>>()
        for (k in 0 until rank) {
            if (sigma[k].signum() == 0) return null
            uCols += Array(m) { i -> var s = BigDecimal.ZERO; for (j in 0 until n) s = s.add(am[i][j].multiply(vCols[k][j], MC), MC); s.divide(sigma[k], MC) }
        }
        var e = 0
        while (uCols.size < m && e < m) {
            var w = Array(m) { i -> if (i == e) BigDecimal.ONE else BigDecimal.ZERO }
            repeat(2) {
                for (q in uCols) {
                    var d = BigDecimal.ZERO
                    for (i in 0 until m) d = d.add(q[i].multiply(w[i], MC), MC)
                    w = Array(m) { i -> w[i].subtract(d.multiply(q[i], MC), MC) }
                }
            }
            var len = BigDecimal.ZERO
            for (x in w) len = len.add(x.multiply(x, MC), MC)
            if (len > BigDecimal("1e-6")) {
                val l = sqrtBig(len)
                uCols += Array(m) { i -> w[i].divide(l, MC) }
            }
            e++
        }
        if (uCols.size != m) return null
        val sigmaShown = sigma.mapIndexed { k, s -> if (k >= rank) BigDecimal.ZERO else s }
        val uM = Array(m) { i -> Array(m) { j -> uCols[j][i] } }
        val vM = Array(n) { i -> Array(n) { j -> vCols[j][i] } }
        val sM = Array(m) { i -> Array(n) { j -> if (i == j) sigmaShown[i] else BigDecimal.ZERO } }
        // The certificate.
        val residual = distance(mulMat(mulMat(uM, sM), transposed(vM)), am)
        val eu = distance(mulMat(transposed(uM), uM), null)
        val ev = distance(mulMat(transposed(vM), vM), null)
        val values = ArrayList<Expr>()
        for (k in 0 until n) {
            if (k >= rank) {
                values += Expr.ZERO
                continue
            }
            val s = sigmaShown[k]
            val bound = residual + inputError + s.toDouble() * ((1 + eu) * (1 + ev) - 1) * 1.01
            if (bound > 1e-11 * s.toDouble()) return null
            values += Expr.Real((tenDigits(s, bound) ?: return null).toDouble())
        }
        // U and V to ten digits, as shown: they multiply back to A and are orthogonal within those digits.
        fun shown(x: BigDecimal): Expr = if (x.abs() < BigDecimal("1e-30")) Expr.ZERO else Expr.Real(x.round(MathContext(10, RoundingMode.HALF_EVEN)).toDouble())
        val u = Expr.Mat(uM.map { r -> r.map(::shown) })
        val vv = Expr.Mat(vM.map { r -> r.map(::shown) })
        val ss = Expr.Mat(List(m) { i -> List(n) { j -> if (i == j) values[i] else Expr.ZERO } })
        val norm = am.sumOf { r -> r.sumOf { it.toDouble() * it.toDouble() } }.let { kotlin.math.sqrt(it) }
        if (!nearProduct(u, ss, vv, a, 1e-8 * max(1.0, norm))) return null
        return Svd(u, ss, vv, values, rank, exact = false)
    }

    /** Whether U·Σ·Vᵀ is A within [tol] in every entry and U, V are orthogonal within 10⁻⁸ (in doubles). */
    private fun nearProduct(u: Expr.Mat, s: Expr.Mat, v: Expr.Mat, a: Expr.Mat, tol: Double): Boolean {
        fun d(m: Expr.Mat) = Array(m.size) { i -> DoubleArray(m.columns) { j -> Numeric.value(m.rows[i][j]) ?: Double.NaN } }
        fun mul(x: Array<DoubleArray>, y: Array<DoubleArray>) = Array(x.size) { i -> DoubleArray(y[0].size) { j -> (y.indices).sumOf { k -> x[i][k] * y[k][j] } } }
        fun t(x: Array<DoubleArray>) = Array(x[0].size) { j -> DoubleArray(x.size) { i -> x[i][j] } }
        val uu = d(u)
        val vv = d(v)
        val p = mul(mul(uu, d(s)), t(vv))
        val aa = d(a)
        for (i in aa.indices) for (j in aa[i].indices) if (!(abs(p[i][j] - aa[i][j]) <= tol)) return false
        for ((x, size) in listOf(uu to uu.size, vv to vv.size)) {
            val g = mul(t(x), x)
            for (i in 0 until size) for (j in 0 until size) if (!(abs(g[i][j] - if (i == j) 1.0 else 0.0) <= 1e-8)) return false
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The Jordan normal form.

    /**
     * J and P with A = P·J·P⁻¹. For each eigenvalue λ of algebraic multiplicity m, the ranks rₖ of (A − λI)ᵏ give the number of
     * blocks of size at least k, r₍ₖ₋₁₎ − rₖ; the chains are built from the largest size down: a top w in ker (A − λI)ᵏ that is
     * independent of ker (A − λI)ᵏ⁻¹ and of the chains already taken, below it (A − λI)w, …, (A − λI)ᵏ⁻¹w. Checked: A·P = P·J and
     * P·J·P⁻¹ = A. Null where the eigenvalues are not found exactly.
     */
    fun jordan(a: Expr.Mat): Jordan? {
        if (a.size != a.columns) throw CalcException(Failure.SIZES)
        if (a.symbols().isNotEmpty()) throw CalcException(Failure.NOT_POLYNOMIAL)
        val n = a.size
        if (n > 6) throw CalcException(Failure.TOO_LARGE)
        val p = LinAlg.characteristic(a)
        val roots = Solve.equation(p, Expr.ZERO, LinAlg.LAMBDA)
        if (roots.numeric) return null
        val values = (roots.values + roots.complex).distinct()
        val chains = ArrayList<Pair<Expr, List<Expr.Mat>>>()
        var counted = 0
        for (lambda in values) {
            Budget.check()
            var multiplicity = 0
            var d = p
            while (multiplicity < n && LinAlg.isNull(tidy(d.substitute(LinAlg.LAMBDA, lambda)))) {
                multiplicity++
                d = Calculus.derivative(d, LinAlg.LAMBDA)
            }
            if (multiplicity == 0) return null
            counted += multiplicity
            val shifted = Expr.Mat(a.rows.mapIndexed { i, r -> r.mapIndexed { j, x -> if (i == j) tidy(x - lambda) else x } })
            chains += chainsOf(shifted, multiplicity, n).map { lambda to it }
        }
        if (counted != n) return null
        // The larger blocks of an eigenvalue first, the eigenvalues in the order found.
        val ordered = chains.sortedWith(compareBy<Pair<Expr, List<Expr.Mat>>> { c -> values.indexOf(c.first) }.thenByDescending { it.second.size })
        val columnsList = ordered.flatMap { it.second }
        if (columnsList.size != n) return null
        val pm = Expr.Mat(List(n) { i -> columnsList.map { it.rows[i][0] } })
        val blocks = ordered.map { Block(it.first, it.second.size) }
        val j = Expr.Mat(List(n) { MutableList<Expr>(n) { Expr.ZERO } }.also { rows ->
            var at = 0
            for (b in blocks) {
                for (k in 0 until b.size) {
                    rows[at + k][at + k] = b.value
                    if (k + 1 < b.size) rows[at + k][at + k + 1] = Expr.ONE
                }
                at += b.size
            }
        })
        if (!same(LinAlg.times(a, pm) as Expr.Mat, LinAlg.times(pm, j) as Expr.Mat)) return null
        val inverse = try { LinAlg.inverse(pm) } catch (e: CalcException) { if (e.failure == Failure.TOO_LONG) throw e; return null }
        if (!same(LinAlg.times(LinAlg.times(pm, j), inverse) as Expr.Mat, a)) return null
        return Jordan(j, pm, inverse, blocks)
    }

    /** The Jordan chains of the nilpotent part N = A − λI on its generalized eigenspace, each from its eigenvector to its top. */
    private fun chainsOf(nm: Expr.Mat, multiplicity: Int, n: Int): List<List<Expr.Mat>> {
        // Ranks of N^k until they reach n − multiplicity.
        val powers = ArrayList<Expr.Mat>()
        powers += LinAlg.identity(n)
        val ranks = ArrayList<Int>()
        ranks += n
        while (ranks.last() > n - multiplicity) {
            Budget.check()
            powers += LinAlg.times(powers.last(), nm) as Expr.Mat
            ranks += LinAlg.rank(powers.last())
            if (powers.size > multiplicity + 1) throw CalcException(Failure.UNSOLVED)
        }
        val longest = ranks.size - 1
        // Blocks of size at least k: r_(k−1) − r_k.
        fun atLeast(k: Int) = if (k > longest) 0 else ranks[k - 1] - ranks[k]
        val chains = ArrayList<List<Expr.Mat>>()
        val taken = ArrayList<Expr.Mat>()
        for (k in longest downTo 1) {
            val wanted = atLeast(k) - atLeast(k + 1)
            if (wanted == 0) continue
            // What a new top must be independent of: ker N^(k−1) and the vectors of the chains at this level and below it.
            val avoid = ArrayList<Expr.Mat>()
            avoid += LinAlg.nullSpace(powers[k - 1])
            for (c in chains) {
                // The chain's vector at level k: N^(size − k) top.
                val size = c.size
                if (size >= k) avoid += c[k - 1]
            }
            var found = 0
            for (w in LinAlg.nullSpace(powers[k])) {
                if (found == wanted) break
                if (rankOf(avoid + w) <= rankOf(avoid)) continue
                avoid += w
                val chain = ArrayList<Expr.Mat>()
                var x = w
                chain += x
                repeat(k - 1) { x = LinAlg.times(nm, x) as Expr.Mat; chain += x }
                chains += chain.reversed()
                taken += w
                found++
            }
            if (found != wanted) throw CalcException(Failure.UNSOLVED)
        }
        return chains
    }

    private fun rankOf(vs: List<Expr.Mat>): Int = if (vs.isEmpty()) 0 else LinAlg.rank(Expr.Mat(List(vs[0].size) { i -> vs.map { it.rows[i][0] } }))
}
