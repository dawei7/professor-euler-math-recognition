package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * The areas the first written corpus did not cover (7 November 2026), in the same style: each task's answer known without the
 * engine (exact fractions, the test's own numerics, brute force, textbook values, mpmath's values at 30 digits in [WrittenTables]).
 */
internal object WrittenLater {
    fun all(): List<Task> = WrittenFinance.all() + WrittenPde.all() + WrittenApplied.all() + WrittenLearning.all() + WrittenGraphs.all() + WrittenInference.all() + WrittenMultivar.all() + WrittenFunctions.all() + WrittenIterations.all() + WrittenAlgorithms.all() + WrittenPlots.all() + WrittenForms.all() + WrittenResidues.all()
}

/** A fraction as a decimal the keys type (it must terminate): 3/4 is 0.75. */
internal fun decimalText(v: Q): String = BigDecimal(v.n).divide(BigDecimal(v.d)).stripTrailingZeros().toPlainString()

/** A fraction in BigDecimal, to [digits] significant digits. */
internal fun big(v: Q, digits: Int = 40): BigDecimal = BigDecimal(v.n).divide(BigDecimal(v.d), MathContext(digits, RoundingMode.HALF_EVEN))

/** The exact number the engine gives as a fraction of its own (a value, an expression without letters); null otherwise. */
internal fun rationalOf(e: Expr): Q? = rational(e)?.let { Q.of(it.num, it.den) }

/** The value of an expression without letters, as a double (NaN where it has none). */
internal fun valueOf(e: Expr): Double = at(e, emptyMap())

internal fun BigInteger.q(): Q = Q.of(this)

/** Matrices of the test's own fractions: products, reduced row echelon forms, ranks, determinants, inverses. */
internal object QM {
    fun of(m: List<List<Long>>): List<List<Q>> = m.map { row -> row.map { q(it) } }

    fun typed(m: List<List<Long>>) = m.joinToString(", ", "[", "]") { row -> row.joinToString(", ", "[", "]") }

    fun typedQ(m: List<List<Q>>) = m.joinToString(", ", "[", "]") { row -> row.joinToString(", ", "[", "]") { it.typed() } }

    fun times(a: List<List<Q>>, b: List<List<Q>>): List<List<Q>> =
        a.map { row -> b[0].indices.map { j -> row.indices.fold(Q.ZERO) { s, k -> s + row[k] * b[k][j] } } }

    fun identity(n: Int): List<List<Q>> = List(n) { i -> List(n) { j -> if (i == j) Q.ONE else Q.ZERO } }

    fun transpose(a: List<List<Q>>): List<List<Q>> = a[0].indices.map { j -> a.map { it[j] } }

    fun rref(m0: List<List<Q>>): Pair<List<List<Q>>, Int> {
        val m = m0.map { it.toMutableList() }.toMutableList()
        var row = 0
        for (col in m[0].indices) {
            if (row == m.size) break
            val p = (row until m.size).firstOrNull { !m[it][col].isZero } ?: continue
            m[row] = m[p].also { m[p] = m[row] }
            val pivot = m[row][col]
            m[row] = m[row].map { it / pivot }.toMutableList()
            for (i in m.indices) if (i != row && !m[i][col].isZero) {
                val f = m[i][col]
                m[i] = m[i].indices.map { j -> m[i][j] - f * m[row][j] }.toMutableList()
            }
            row++
        }
        return m to row
    }

    fun rank(m: List<List<Q>>): Int = rref(m).second

    fun det(m: List<List<Q>>): Q {
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

    fun inverse(m: List<List<Q>>): List<List<Q>> {
        val n = m.size
        val id = identity(n)
        return rref(m.indices.map { i -> m[i] + id[i] }).first.map { it.drop(n) }
    }

    /** The minor without row [i] and column [j]. */
    fun minor(m: List<List<Q>>, i: Int, j: Int): List<List<Q>> = m.filterIndexed { r, _ -> r != i }.map { row -> row.filterIndexed { c, _ -> c != j } }

    /** The engine's matrix as fractions (null where an entry is no fraction). */
    fun read(e: Expr.Mat): List<List<Q>>? = e.rows.map { row -> row.map { rationalOf(it) ?: return null } }

    /** A vector (a column or a row) as fractions. */
    fun vector(e: Expr.Mat): List<Q>? = (if (e.rows.size == 1) e.rows[0] else e.rows.map { it.singleOrNull() ?: return null }).map { rationalOf(it) ?: return null }
}

/** Polynomials of the test's own fractions, lowest power first. */
internal object QP {
    fun at(p: List<Q>, x: Q): Q = p.foldRight(Q.ZERO) { c, acc -> acc * x + c }
    fun at(p: List<Q>, x: Double): Double = p.foldRight(0.0) { c, acc -> acc * x + c.toDouble() }
    fun derivative(p: List<Q>): List<Q> = if (p.size <= 1) listOf(Q.ZERO) else (1 until p.size).map { k -> p[k] * q(k.toLong()) }
    fun antiderivative(p: List<Q>): List<Q> = listOf(Q.ZERO) + p.indices.map { k -> p[k] / q(k + 1L) }
    fun integral(p: List<Q>, a: Q, b: Q): Q = antiderivative(p).let { at(it, b) - at(it, a) }
    fun times(a: List<Q>, b: List<Q>): List<Q> = List(a.size + b.size - 1) { k -> (0..k).fold(Q.ZERO) { s, i -> if (i < a.size && k - i < b.size) s + a[i] * b[k - i] else s } }
    fun plus(a: List<Q>, b: List<Q>): List<Q> = List(maxOf(a.size, b.size)) { k -> (a.getOrNull(k) ?: Q.ZERO) + (b.getOrNull(k) ?: Q.ZERO) }
    fun scale(a: List<Q>, c: Q): List<Q> = a.map { it * c }
    /** Typed with whole or fraction coefficients: 2x^3 - (1/2)x + 5. */
    fun text(p: List<Q>, x: String = "x"): String {
        val sb = StringBuilder()
        for (k in p.indices.reversed()) {
            val c = p[k]
            if (c.isZero) continue
            val size = if (c < Q.ZERO) -c else c
            val body = when (k) { 0 -> ""; 1 -> x; else -> "$x^$k" }
            val coefficient = when {
                size == Q.ONE && body.isNotEmpty() -> ""
                size.isInteger -> "$size"
                else -> "(${size.n}/${size.d})"
            }
            val t = coefficient + body
            sb.append(if (sb.isEmpty()) (if (c < Q.ZERO) "-$t" else t) else if (c < Q.ZERO) " - $t" else " + $t")
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }
    fun ofLongs(c: List<Long>): List<Q> = c.map { q(it) }
}

/** Gauss–Legendre quadrature of the test's own: 20 nodes (found by Newton's method on P₂₀), on [panels] equal pieces. */
internal object Gauss {
    private val nodes: DoubleArray
    private val weights: DoubleArray

    init {
        val n = 20
        nodes = DoubleArray(n)
        weights = DoubleArray(n)
        for (i in 0 until n) {
            var x = kotlin.math.cos(Math.PI * (i + 0.75) / (n + 0.5))
            var dp = 0.0
            repeat(100) {
                var p0 = 1.0
                var p1 = x
                for (k in 2..n) { val p2 = ((2 * k - 1) * x * p1 - (k - 1) * p0) / k; p0 = p1; p1 = p2 }
                dp = n * (x * p1 - p0) / (x * x - 1)
                x -= p1 / dp
            }
            nodes[i] = x
            weights[i] = 2 / ((1 - x * x) * dp * dp)
        }
    }

    fun integrate(a: Double, b: Double, panels: Int = 12, f: (Double) -> Double): Double {
        var total = 0.0
        val h = (b - a) / panels
        for (p in 0 until panels) {
            val mid = a + h * (p + 0.5)
            for (i in nodes.indices) total += weights[i] * f(mid + h / 2 * nodes[i])
        }
        return total * h / 2
    }
}

private val SHOWN_DECIMAL = Regex("""(-?)(\d+)(?:\.(\d+))?(?: · 10\^(-?\d+))?""")

/**
 * A number equal to [ref], which the test knows to about [rel] of the larger of itself and a millionth of [scale] (the size of
 * what was summed: an integral's ∫|f|, so a value 0 by symmetry, which quadrature gives as 10⁻¹⁷, is 0). An exact form must be
 * the value; a decimal shown must be right to its last digit (a certified decimal may have fewer digits, never wrong ones).
 */
internal fun isValue(ref: Double, scale: Double = kotlin.math.abs(ref), rel: Double = 1e-9): (Calculation) -> Boolean = { c ->
    val v = numberOf(c)
    val shown = shownOf(c)
    val tol = rel * maxOf(kotlin.math.abs(ref), 1e-6 * scale) + 1e-300
    if (v == null || !v.isReal || shown == null) false else {
        val at = shown.indexOf('≈')
        val exactPart = when (val o = c.outcome) {
            is Outcome.Value -> o.exact ?: (o.value as? Num.Exact)?.let { num(it.r) }
            is Outcome.DefiniteIntegral -> o.exact
            is Outcome.Expression -> o.result
            else -> null
        }?.takeIf { exact(it) }
        val exactOk = exactPart == null || kotlin.math.abs(read(exactPart).re - ref) <= tol
        val decimal = (if (at >= 0) shown.substring(at + 1) else if (exactPart == null) shown else null)?.trim()
        val digitsOk = decimal == null || SHOWN_DECIMAL.matchEntire(decimal)?.let { m ->
            val (sign, whole, fraction, power) = m.destructured
            val mantissa = (whole + fraction).trimStart('0')
            val d = "$sign$whole.${fraction.ifEmpty { "0" }}".toDouble() * Math.pow(10.0, power.toDoubleOrNull() ?: 0.0)
            if (mantissa.isEmpty()) kotlin.math.abs(ref) <= 1e-3 * tol / rel else {
                val unit = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(d))) - (mantissa.length - 1))
                kotlin.math.abs(d - ref) <= unit * 1.01 + tol
            }
        } ?: false
        exactOk && digitsOk
    }
}
