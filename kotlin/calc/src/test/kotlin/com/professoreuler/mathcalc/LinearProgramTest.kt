package com.professoreuler.mathcalc

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * maximize and minimize: textbook programs with known optima (the classic production problem, a diet problem, Chvátal's and
 * Beale's examples on which the simplex method cycles without Bland's rule), unbounded and infeasible ones, and seeded
 * programs checked by this test's own vertex enumeration in fractions (every choice of n constraints as equations solved by
 * Gaussian elimination here, the best feasible vertex) and by the certificates checked here again.
 */
class LinearProgramTest {
    private fun answer(text: String): String {
        val r = Reports.of(TextInput.read(text)).result
        return if (r.outcome is Outcome.Failed) "FAILED ${(r.outcome as Outcome.Failed).failure}" else answerOf(r)
    }

    private fun solution(text: String): LinearProgram.Solution? =
        (Reports.of(TextInput.read(text)).result.outcome as? Outcome.Optimized)?.program

    @Test
    fun textbookPrograms() {
        val cases = listOf(
            // The production problem: corners (0, 0), (4, 0), (3, 1), (0, 2).
            "maximize(3x + 2y, x + y <= 4, x + 3y <= 6, x >= 0, y >= 0)" to "max 12 at x = 4, y = 0",
            "minimize(2x + 3y, x + y >= 4, x + 3y >= 6, x >= 0, y >= 0)" to "min 9 at x = 3, y = 1",
            "maximize(x + y, x + 2y <= 4, 3x + y <= 6, 0 <= x, 0 <= y)" to "max 14/5 at x = 8/5, y = 6/5",
            "maximize(x + 2y, x + y = 3, x >= 0, y >= 0)" to "max 6 at x = 0, y = 3",
            "maximize(3x + 2y + 5, x + y <= 4, x + 3y <= 6, x >= 0, y >= 0)" to "max 17 at x = 4, y = 0",
            "maximize(0.5x + y, x + y <= 1.5, x >= 0, y >= 0)" to "max 3/2 at x = 0, y = 3/2",
            "maximize(2x + 3y + 4z, x + y + z <= 10, 2x + y <= 12, y + 3z <= 15, x >= 0, y >= 0, z >= 0)" to "max 65/2 at x = 0, y = 15/2, z = 5/2",
            // Chvátal's cycling example (optimum 1 at x₁ = x₃ = 1), Beale's (−1/20 at x₄ = 1/25, x₆ = 1).
            "maximize(10a - 57b - 9c - 24d, 0.5a - 5.5b - 2.5c + 9d <= 0, 0.5a - 1.5b - 0.5c + d <= 0, a <= 1, a >= 0, b >= 0, c >= 0, d >= 0)"
                to "max 1 at a = 1, b = 0, c = 1, d = 0",
            "minimize(-3/4 a + 150 b - 1/50 c + 6 d, 1/4 a - 60 b - 1/25 c + 9 d <= 0, 1/2 a - 90 b - 1/50 c + 3 d <= 0, c <= 1, a >= 0, b >= 0, c >= 0, d >= 0)"
                to "min -1/20 at a = 1/25, b = 0, c = 1, d = 0",
            // Letters without a sign bound are free and said to be.
            "minimize(x + y, x + y >= 2)" to "min 2 at x = 2, y = 0 (free x, y)",
            "minimize(x - y, x >= 1, y <= 2, x + y >= 0)" to "min -1 at x = 1, y = 2 (free y)",
            "maximize(x, x <= 3)" to "max 3 at x = 3 (free x)",
            "maximize(x + y, x >= 0, y >= 0)" to "unbounded",
            "maximize(x + y, x + y <= 2, x + y >= 3)" to "no feasible point (free x, y)",
            // What they do not take.
            "maximize(x y, x + y <= 4)" to "FAILED NOT_LINEAR",
            "maximize(x^2, x <= 4)" to "FAILED NOT_LINEAR",
            "maximize(x + y, x + y < 4)" to "FAILED ARGUMENTS",
            "maximize(x + y, x + y != 4)" to "FAILED ARGUMENTS",
            "maximize(x + y, 4)" to "FAILED ARGUMENTS",
            "maximize(x + sqrt(2) y, x + y <= 1)" to "FAILED UNSOLVED",
        )
        val wrong = cases.mapNotNull { (t, want) -> answer(t).takeIf { it != want }?.let { "$t: $it, not $want" } }
        assertEquals("", wrong.joinToString("\n"))
    }

    private fun pt(p: Pair<Expr, Expr>) = "(${rational(p.first)}, ${rational(p.second)})"

    @Test
    fun thePictureOfTwoLetters() {
        val p = solution("maximize(3x + 2y, x + y <= 4, x + 3y <= 6, x >= 0, y >= 0)")!!.picture!!
        assertEquals("(0, 0) (4, 0) (3, 1) (0, 2)", p.corners.joinToString(" ") { pt(it) })
        assertEquals("0 12 11 4", p.values.joinToString(" ") { "${rational(it)}" })
        assertEquals(listOf(1), p.optimal)
        assertEquals(true, p.bounded)
        // A whole optimal edge: both its corners marked.
        val edge = solution("maximize(2x + 2y, x + y <= 4, x <= 3, y <= 3, x >= 0, y >= 0)")!!.picture!!
        assertEquals(listOf("(3, 1)", "(1, 3)"), edge.optimal.map { pt(edge.corners[it]) })
        // Unbounded: the rays leave from their corners.
        val open = solution("minimize(x - y, x >= 1, y <= 2, x + y >= 0)")!!.picture!!
        assertEquals(false, open.bounded)
        assertEquals("(1, 2) (1, -1)", open.corners.joinToString(" ") { pt(it) })
        assertEquals("0:(1, 0) 1:(1, -1)", open.rays.joinToString(" ") { "${it.first}:" + pt(it.second) })
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Seeded programs against vertex enumeration.

    /** x with A·x = b by Gaussian elimination in fractions, or null where A is singular. */
    private fun solve(a: List<List<Rational>>, b: List<Rational>): List<Rational>? {
        val n = b.size
        val m = a.mapIndexed { i, r -> (r + b[i]).toMutableList() }.toMutableList()
        for (c in 0 until n) {
            val p = (c until n).firstOrNull { m[it][c].signum != 0 } ?: return null
            val t = m[p]; m[p] = m[c]; m[c] = t
            for (i in 0 until n) if (i != c && m[i][c].signum != 0) {
                val f = m[i][c] / m[c][c]
                for (j in c..n) m[i][j] = m[i][j] - f * m[c][j]
            }
        }
        return List(n) { m[it][n] / m[it][it] }
    }

    private fun combinations(n: Int, k: Int): List<List<Int>> =
        if (k == 0) listOf(emptyList()) else (0 until n).flatMap { i -> combinations(n, k - 1).filter { it.all { j -> j > i } }.map { listOf(i) + it } }

    @Test
    fun seededProgramsAgreeWithVertexEnumeration() {
        val r = Random(1106)
        val wrong = ArrayList<String>()
        var infeasible = 0
        repeat(120) {
            val n = r.nextInt(2, 4)
            val letters = listOf("x", "y", "z").take(n)
            // Random rows a·x ≤ / ≥ b, then 0 ≤ xᵢ ≤ 8 so the region is bounded and has corners.
            val rows = ArrayList<Triple<List<Long>, String, Long>>()
            repeat(r.nextInt(1, 5)) { rows += Triple(List(n) { r.nextLong(-3, 5) }, if (r.nextInt(4) == 0) ">=" else "<=", r.nextLong(-2, 15)) }
            for (j in 0 until n) {
                rows += Triple(List(n) { if (it == j) 1L else 0L }, ">=", 0L)
                rows += Triple(List(n) { if (it == j) 1L else 0L }, "<=", 8L)
            }
            val c = List(n) { r.nextLong(-4, 6) }
            val maximize = r.nextBoolean()
            fun lin(a: List<Long>) = a.indices.joinToString(" + ") { "(${a[it]})${letters[it]}" }
            val task = (if (maximize) "maximize(" else "minimize(") + lin(c) + ", " + rows.joinToString(", ") { "${lin(it.first)} ${it.second} ${it.third}" } + ")"
            // Every vertex: n rows as equations, solved here, kept where every row holds.
            var best: Rational? = null
            for (choice in combinations(rows.size, n)) {
                val x = solve(choice.map { i -> rows[i].first.map { Rational.of(it) } }, choice.map { Rational.of(rows[it].third) }) ?: continue
                val ok = rows.all { (a, rel, b) ->
                    val v = a.indices.fold(Rational.ZERO) { s, j -> s + Rational.of(a[j]) * x[j] }
                    if (rel == "<=") v <= Rational.of(b) else v >= Rational.of(b)
                }
                if (!ok) continue
                val z = c.indices.fold(Rational.ZERO) { s, j -> s + Rational.of(c[j]) * x[j] }
                if (best == null || (maximize && z > best!!) || (!maximize && z < best!!)) best = z
            }
            val s = solution(task)
            when {
                s == null -> wrong += "$task: ${answer(task)}"
                best == null -> { infeasible++; if (s.status != LinearProgram.Status.INFEASIBLE) wrong += "$task: ${s.status}, none feasible" }
                s.status != LinearProgram.Status.OPTIMAL || rational(s.value!!) != best -> wrong += "$task: ${answer(task)}, not $best"
                else -> {
                    // The certificate again: c = Σ λᵢaᵢ with λ's signs, Σ λᵢbᵢ = z*, and the point in every row.
                    val lambda = s.multipliers!!.map { rational(it)!! }
                    val x = s.point!!.map { rational(it)!! }
                    for (j in 0 until n) {
                        val sum = s.constraints.indices.fold(Rational.ZERO) { acc, i -> acc + lambda[i] * s.constraints[i].coefficients[j] }
                        if (sum != Rational.of(c[j])) wrong += "$task: λ does not give c"
                    }
                    if (s.constraints.indices.fold(Rational.ZERO) { acc, i -> acc + lambda[i] * s.constraints[i].bound } != best) wrong += "$task: λ·b ≠ z"
                    for ((i, k) in s.constraints.withIndex()) {
                        val sign = if ((k.relation == Rel.LE) == maximize) 1 else -1
                        if (k.relation != Rel.EQ && lambda[i].signum * sign < 0) wrong += "$task: λ$i has the wrong sign"
                        val v = k.coefficients.indices.fold(Rational.ZERO) { acc, j -> acc + k.coefficients[j] * x[j] }
                        if (!(if (k.relation == Rel.LE) v <= k.bound else if (k.relation == Rel.GE) v >= k.bound else v == k.bound)) wrong += "$task: row $i"
                    }
                }
            }
        }
        assertEquals("", wrong.joinToString("\n"))
        println("$infeasible of 120 had no feasible point")
    }
}
