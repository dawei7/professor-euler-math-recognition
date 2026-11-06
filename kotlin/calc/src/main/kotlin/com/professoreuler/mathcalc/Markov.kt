package com.professoreuler.mathcalc

import com.professoreuler.mathcalc.StatCommands.Arg
import java.math.BigInteger

/**
 * Markov chains with a transition matrix P whose row i holds the probabilities of going from state i to each state (each row
 * adds up to 1), and distributions as rows: π after one step is π·P. `stationary(P)`: the π with π·P = π whose entries add up to 1,
 * where there is exactly one; `markov(P, π₀, n)`: π₀·Pⁿ, the distribution after n steps. Exact in fractions, and checked: π·P = π
 * and Σπ = 1 put back; π₀·Pⁿ worked out a second way (step by step, or split in two powers).
 */
internal object Markov {
    /** A transition matrix: square, at least 2 × 2, its entries fractions from 0 to 1, each row adding up to 1. */
    private fun transition(e: Expr): List<List<Rational>> {
        val m = e as? Expr.Mat ?: throw CalcException(Failure.ARGUMENTS)
        if (m.size != m.columns || m.size < 2) throw CalcException(Failure.SIZES)
        if (m.size > 30) throw CalcException(Failure.TOO_LARGE)
        val rows = m.rows.map { row -> row.map { rational(it) ?: throw CalcException(Failure.UNDEFINED) } }
        for (row in rows) {
            if (row.any { it.signum < 0 || it > Rational.ONE }) throw CalcException(Failure.UNDEFINED)
            if (row.fold(Rational.ZERO) { s, x -> s + x } != Rational.ONE) throw CalcException(Failure.UNDEFINED)
        }
        return rows
    }

    private fun row(values: List<Rational>): Expr.Mat = LinAlg.matrix(listOf(values.map { num(it) }))

    private fun times(v: List<Rational>, p: List<List<Rational>>): List<Rational> =
        p.indices.map { j -> v.indices.fold(Rational.ZERO) { s, i -> s + v[i] * p[i][j] } }

    private fun product(a: List<List<Rational>>, b: List<List<Rational>>): List<List<Rational>> =
        a.indices.map { i -> b.indices.map { j -> a.indices.fold(Rational.ZERO) { s, k -> s + a[i][k] * b[k][j] }.also { Budget.check() } } }

    private fun power(p: List<List<Rational>>, n: Long): List<List<Rational>> {
        var result = p.indices.map { i -> p.indices.map { j -> if (i == j) Rational.ONE else Rational.ZERO } }
        var base = p
        var k = n
        while (k > 0) {
            if (k and 1L == 1L) result = product(result, base)
            k = k shr 1
            if (k > 0) base = product(base, base)
        }
        return result
    }

    /**
     * The stationary distribution: π·(P − I) = 0 and π₁ + … + πₖ = 1 solved by elimination; where the solution is not unique (the
     * chain has more than one closed class of states) [Failure.MANY_SOLUTIONS].
     */
    fun stationary(e: Expr): Expr.Mat {
        val p = transition(e)
        val k = p.size
        // k + 1 equations in k unknowns: column j of P − I, and the sum.
        val rows = ArrayList<MutableList<Rational>>()
        for (j in 0 until k) rows += (0 until k).map { i -> p[i][j] - if (i == j) Rational.ONE else Rational.ZERO }.toMutableList<Rational>().also { it += Rational.ZERO }
        rows += MutableList(k) { Rational.ONE }.also { it += Rational.ONE }
        var rank = 0
        val pivots = ArrayList<Int>()
        for (col in 0 until k) {
            val pivot = (rank until rows.size).firstOrNull { rows[it][col].signum != 0 } ?: continue
            val t = rows[pivot]; rows[pivot] = rows[rank]; rows[rank] = t
            val lead = rows[rank][col]
            for (j in 0..k) rows[rank][j] = rows[rank][j] / lead
            for (i in rows.indices) {
                if (i == rank || rows[i][col].signum == 0) continue
                val f = rows[i][col]
                for (j in 0..k) rows[i][j] = rows[i][j] - f * rows[rank][j]
            }
            pivots += col
            rank++
            Budget.check()
        }
        // Inconsistent cannot happen for a stochastic P; fewer pivots than states means a family of solutions.
        if ((rank until rows.size).any { rows[it][k].signum != 0 }) throw CalcException(Failure.UNSOLVED)
        if (rank < k) throw CalcException(Failure.MANY_SOLUTIONS)
        val pi = (0 until k).map { rows[it][k] }
        // The check: π·P = π, Σπ = 1, every πᵢ ≥ 0.
        if (times(pi, p) != pi || pi.fold(Rational.ZERO) { s, x -> s + x } != Rational.ONE || pi.any { it.signum < 0 }) {
            throw CalcException(Failure.UNVERIFIED)
        }
        return row(pi)
    }

    /** markov(P, π₀, n): the distribution π₀·Pⁿ after n steps. */
    fun after(args: List<Arg>): Expr.Mat {
        if (args.size != 3) throw CalcException(Failure.ARGUMENTS)
        val values = args.map { (it as? Arg.Value)?.e ?: throw CalcException(Failure.ARGUMENTS) }
        val p = transition(values[0])
        val start = Describe.entries(values[1])?.map { rational(it) ?: throw CalcException(Failure.UNDEFINED) } ?: throw CalcException(Failure.ARGUMENTS)
        if (start.size != p.size) throw CalcException(Failure.SIZES)
        if (start.any { it.signum < 0 } || start.fold(Rational.ZERO) { s, x -> s + x } != Rational.ONE) throw CalcException(Failure.UNDEFINED)
        if (values[2] is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val n = integer(values[2]) ?: throw CalcException(Failure.UNDEFINED)
        if (n.signum() < 0) throw CalcException(Failure.UNDEFINED)
        if (n > BigInteger.valueOf(100_000)) throw CalcException(Failure.TOO_LARGE)
        val steps = n.toLong()
        val result = times(start, power(p, steps))
        // The check, a second way: step by step where n is small, else π₀·P^m then P^(n − m).
        val again = if (steps <= 2000) {
            var v = start
            repeat(steps.toInt()) { v = times(v, p); Budget.check() }
            v
        } else {
            val m = steps / 3
            times(times(start, power(p, m)), power(p, steps - m))
        }
        if (again != result || result.fold(Rational.ZERO) { s, x -> s + x } != Rational.ONE) throw CalcException(Failure.UNVERIFIED)
        return row(result)
    }
}
