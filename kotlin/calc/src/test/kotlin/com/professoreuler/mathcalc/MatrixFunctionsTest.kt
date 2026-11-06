package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * rank, trace, nullspace, columnspace, charpoly and adjugate: textbook values, and seeded matrices checked by this test's own
 * arithmetic (determinants by Laplace expansion in fractions, independent of the engine): the rank as the largest size of a
 * minor that is not 0, a null vector multiplied out, the characteristic polynomial at n + 1 values of t against det(tI − A),
 * the adjugate's cofactors one by one.
 */
class MatrixFunctionsTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    private fun outcome(text: String): Outcome = Reports.of(TextInput.read(text)).result.outcome

    // -----------------------------------------------------------------------------------------------------------------
    // The test's own arithmetic.

    private fun det(m: List<List<Rational>>): Rational {
        if (m.size == 1) return m[0][0]
        var sum = Rational.ZERO
        for (j in m.indices) {
            if (m[0][j].signum == 0) continue
            val minor = m.drop(1).map { r -> r.filterIndexed { k, _ -> k != j } }
            val term = m[0][j] * det(minor)
            sum = if (j % 2 == 0) sum + term else sum - term
        }
        return sum
    }

    private fun subsets(n: Int, k: Int): List<List<Int>> =
        if (k == 0) listOf(emptyList()) else (0 until n).flatMap { i -> subsets(n, k - 1).filter { it.all { j -> j > i } }.map { listOf(i) + it } }

    /** The largest k with a k × k minor that is not 0. */
    private fun rankByMinors(m: List<List<Rational>>): Int {
        for (k in minOf(m.size, m[0].size) downTo 1) {
            for (rows in subsets(m.size, k)) for (cols in subsets(m[0].size, k)) {
                if (det(rows.map { i -> cols.map { j -> m[i][j] } }).signum != 0) return k
            }
        }
        return 0
    }

    private fun text(m: List<List<Rational>>) = "[" + m.joinToString(", ") { r -> "[" + r.joinToString(", ") + "]" } + "]"

    private fun random(r: Random, rows: Int, cols: Int, rank: Int): List<List<Rational>> {
        // A product of rows × rank and rank × cols integer matrices: rank at most [rank].
        val a = List(rows) { List(rank) { Rational.of(r.nextLong(-3, 4)) } }
        val b = List(rank) { List(cols) { Rational.of(r.nextLong(-3, 4)) } }
        return List(rows) { i -> List(cols) { j -> (0 until rank).fold(Rational.ZERO) { s, k -> s + a[i][k] * b[k][j] } } }
    }

    private fun matrixOf(e: Expr): List<List<Rational>> = (e as Expr.Mat).rows.map { r -> r.map { rational(it)!! } }

    // -----------------------------------------------------------------------------------------------------------------

    @Test
    fun textbookValues() {
        val cases = listOf(
            "rank([[1, 2], [2, 4]])" to "1",
            "rank([[1, 2, 3], [4, 5, 6], [7, 8, 9]])" to "2",
            "rank([[0, 0], [0, 0]])" to "0",
            "rank([[5]])" to "1",
            "trace([[1, 2], [3, 4]])" to "5",
            "trace([[a, b], [c, d]])" to "a + d",
            "nullspace([[1, 2, 3], [4, 5, 6]])" to "ker dim 1 of 3 span (1, -2, 1)",
            "nullspace([[1, 2], [3, 4]])" to "ker dim 0 of 2 {0}",
            "columnspace([[1, 2, 3], [4, 5, 6]])" to "col dim 2 of 2 span (1, 4) (2, 5)",
            "charpoly([[2, 1], [1, 2]])" to "λ^2 - 4λ + 3",
            "charpoly([[2, 1], [1, 2]], x)" to "x^2 - 4x + 3",
            "adjugate([[1, 2], [3, 4]])" to "[[4, -2], [-3, 1]]",
            "adjugate([[a, b], [c, d]])" to "[[d, -b], [-c, a]]",
            "adjugate([[1, 2, 3], [2, 4, 6], [3, 6, 9]])" to "[[0, 0, 0], [0, 0, 0], [0, 0, 0]]",
            // With a letter: det = 4 − k², so the rank falls at k = ±2; the classic 3 × 3 at k = 1 and k = −2.
            "rank([[1, k], [k, 4]])" to "rank 2 | k = -2: rank 1 | k = 2: rank 1",
            "rank([[k, 1, 1], [1, k, 1], [1, 1, k]])" to "rank 3 | k = -2: rank 2 | k = 1: rank 1",
            "rank([[1, 1/(k - 1)], [0, k]])" to "rank 2 | k = 0: rank 1 | undefined at 1",
            // What they do not take.
            "trace([[1, 2, 3], [4, 5, 6]])" to "FAILED SIZES",
            "charpoly([[1, 2], [3, 4], [5, 6]])" to "FAILED SIZES",
            "nullspace([[1, k], [k, 4]])" to "FAILED UNSOLVED",
            "rank(5)" to "FAILED ARGUMENTS",
            "rank([[1, 2]], x)" to "FAILED ARGUMENTS",
        )
        val wrong = cases.mapNotNull { (t, want) -> answer(t).takeIf { it != want }?.let { "$t: $it, not $want" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun seededMatricesAgreeWithTheTestsOwnArithmetic() {
        val r = Random(20261106)
        val wrong = ArrayList<String>()
        repeat(60) {
            val rows = r.nextInt(1, 5)
            val cols = r.nextInt(1, 5)
            val m = random(r, rows, cols, r.nextInt(1, minOf(rows, cols) + 1))
            val t = text(m)
            // The rank against the largest minor that is not 0.
            val rank = (outcome("rank($t)") as? Outcome.Value)?.value?.rational?.num?.toInt()
            val want = rankByMinors(m)
            if (rank != want) wrong += "rank($t) = $rank, not $want"
            // The null space: each vector multiplied out to 0, as many as cols − rank, independent.
            val kernel = outcome("nullspace($t)") as? Outcome.Basis
            if (kernel == null || kernel.vectors.size != cols - want) wrong += "nullspace($t): ${kernel?.vectors?.size}"
            else {
                val vs = kernel.vectors.map { v -> v.rows.map { rational(it[0])!! } }
                for (v in vs) if (m.any { row -> row.indices.fold(Rational.ZERO) { s, j -> s + row[j] * v[j] }.signum != 0 }) wrong += "nullspace($t): $v"
                if (vs.isNotEmpty() && rankByMinors(List(cols) { j -> vs.map { it[j] } }) != vs.size) wrong += "nullspace($t) dependent"
            }
            // The column space: columns of A, as many as the rank, independent.
            val col = outcome("columnspace($t)") as? Outcome.Basis
            if (col == null || col.vectors.size != want) wrong += "columnspace($t): ${col?.vectors?.size}"
            else {
                val vs = col.vectors.map { v -> v.rows.map { rational(it[0])!! } }
                for (v in vs) if ((0 until cols).none { j -> m.indices.all { i -> m[i][j] == v[i] } }) wrong += "columnspace($t): $v no column"
                if (vs.isNotEmpty() && rankByMinors(List(rows) { i -> vs.map { it[i] } }) != vs.size) wrong += "columnspace($t) dependent"
            }
        }
        repeat(40) {
            val n = r.nextInt(1, 5)
            val m = List(n) { List(n) { Rational.of(r.nextLong(-4, 5)) } }
            val t = text(m)
            // The trace.
            val tr = (outcome("trace($t)") as? Outcome.Value)?.value?.rational
            if (tr != (0 until n).fold(Rational.ZERO) { s, i -> s + m[i][i] }) wrong += "trace($t) = $tr"
            // The characteristic polynomial against det(tI − A) at n + 1 values of t.
            val p = (outcome("charpoly($t)") as? Outcome.Expression)?.result
            if (p == null) wrong += "charpoly($t) failed" else for (v in 0..n) {
                val shifted = List(n) { i -> List(n) { j -> (if (i == j) Rational.of(v.toLong()) else Rational.ZERO) - m[i][j] } }
                if (rational(Calculus.tidy(p.substitute(LinAlg.LAMBDA, num(v.toLong())))) != det(shifted)) wrong += "charpoly($t) at $v"
            }
            // The adjugate: (−1)^(i+j) times the minor without row j and column i, entry by entry.
            val adj = (outcome("adjugate($t)") as? Outcome.Expression)?.result
            if (adj == null) wrong += "adjugate($t) failed" else {
                val got = matrixOf(adj)
                for (i in 0 until n) for (j in 0 until n) {
                    val want = if (n == 1) Rational.ONE else {
                        val minor = m.filterIndexed { k, _ -> k != j }.map { row -> row.filterIndexed { k, _ -> k != i } }
                        if ((i + j) % 2 == 0) det(minor) else -det(minor)
                    }
                    if (got[i][j] != want) wrong += "adjugate($t)[$i][$j] = ${got[i][j]}, not $want"
                }
            }
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun aRankWithALetterIsRightAtEveryValue() {
        // At the special values and at others, against the test's own minors.
        val wrong = ArrayList<String>()
        val o = outcome("rank([[k, 1, 1], [1, k, 1], [1, 1, k]])") as Outcome.RankCases
        for (v in -5..5) {
            val m = List(3) { i -> List(3) { j -> if (i == j) Rational.of(v.toLong()) else Rational.ONE } }
            val want = rankByMinors(m)
            val got = o.special.firstOrNull { rational(it.first) == Rational.of(v.toLong()) }?.second ?: o.generic
            if (got != want) wrong += "k = $v: $got, not $want"
        }
        assertEquals("", wrong.joinToString("\n"))
    }

    @Test
    fun matrixFunctionsAreQuick() {
        val start = System.nanoTime()
        for (t in listOf("rank([[1, 2, 3, 4, 5], [2, 3, 4, 5, 6], [3, 4, 5, 6, 7], [1, 0, 2, 0, 3], [0, 1, 0, 2, 0]])",
            "charpoly([[1, 2, 3, 4, 5], [2, 3, 4, 5, 6], [3, 4, 5, 6, 7], [1, 0, 2, 0, 3], [0, 1, 0, 2, 0]])",
            "adjugate([[1, 2, 3, 4], [2, 3, 4, 5], [3, 4, 5, 6], [1, 0, 2, 0]])")) {
            assertTrue(t, !answer(t).startsWith("FAILED"))
        }
        assertTrue("took ${(System.nanoTime() - start) / 1_000_000} ms", System.nanoTime() - start < 3_000_000_000L)
    }
}
