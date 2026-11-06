package com.professoreuler.mathcalc

import kotlin.math.abs
import kotlin.math.max

/**
 * Finite Markov decision processes as Sutton and Barto write them (Reinforcement Learning, chapters 3 and 4), typed as
 * matrices: states 1 … n, actions 1 … m, for each action a its transition matrix Pₐ (row s holds the probabilities p(s′ | s, a)
 * of the next state s′, a row of zeros where action a is not available in state s), the reward matrix R (row s, column a: the
 * expected reward r(s, a) of taking a in s) and the discount γ, 0 ≤ γ < 1.
 *
 * - `valueiteration(P₁, …, Pₘ, R, γ)`: value iteration from v₀ = 0, v_{k+1}(s) = maxₐ [r(s, a) + γ Σ p(s′ | s, a) v_k(s′)], as
 *   the table of its iterations (the method's own approximation), and the optimal values v* exactly: the greedy policy of the
 *   iteration, evaluated exactly and improved (policy iteration) until no action is better, then v* checked by the Bellman
 *   optimality equation v*(s) = maxₐ q*(s, a), worked out again on its own, and against the iteration's last values.
 * - `policyevaluation(P₁, …, Pₘ, R, γ, π)`: v_π of a fixed policy π (row s, column a: the probability π(a | s)), the linear system
 *   (I − γP_π) v = r_π solved exactly and put back into v = r_π + γ P_π v.
 *
 * Every number is a fraction (a decimal typed is one); every value is exact.
 */
object Decisions {
    /** The largest process worked out. */
    private const val MOST_STATES = 25
    private const val MOST_ACTIONS = 10

    /** At most this many sweeps of value iteration. */
    private const val MOST_SWEEPS = 100_000

    /** A process read from its matrices: p[a][s][s′], r[s][a], whether action a is available in state s. */
    internal class Process(val n: Int, val m: Int, val p: List<List<List<Rational>>>, val r: List<List<Rational>>,
        val gamma: Rational, val available: List<List<Boolean>>)

    private fun rationalOf(e: Expr): Rational = rational(e) ?: throw CalcException(Failure.ARGUMENTS)

    private fun entries(e: Expr): List<List<Rational>> = when (e) {
        is Expr.Mat -> e.rows.map { r -> r.map { rationalOf(it) } }
        else -> listOf(listOf(rationalOf(e)))
    }

    /** The process of P₁, …, Pₘ, R and γ, each checked: Pₐ square with rows of probabilities (or of zeros), R n × m, 0 ≤ γ < 1. */
    internal fun process(args: List<Expr>): Process {
        if (args.size < 3) throw CalcException(Failure.ARGUMENTS)
        val gammaE = args.last()
        if (gammaE is Expr.Mat) throw CalcException(Failure.ARGUMENTS)
        val gamma = rationalOf(gammaE)
        val rewards = entries(args[args.size - 2])
        val ps = args.subList(0, args.size - 2).map { entries(it.takeIf { m -> m is Expr.Mat } ?: throw CalcException(Failure.ARGUMENTS)) }
        val n = ps[0].size
        val m = ps.size
        if (n > MOST_STATES || m > MOST_ACTIONS) throw CalcException(Failure.TOO_LARGE)
        if (ps.any { p -> p.size != n || p.any { it.size != n } }) throw CalcException(Failure.SIZES)
        if (rewards.size != n || rewards.any { it.size != m }) throw CalcException(Failure.SIZES)
        if (gamma.signum < 0 || gamma >= Rational.ONE) throw CalcException(Failure.UNDEFINED)
        val available = List(n) { s ->
            List(m) { a ->
                val row = ps[a][s]
                if (row.any { it.signum < 0 }) throw CalcException(Failure.NOT_DISTRIBUTION)
                val sum = row.fold(Rational.ZERO) { acc, x -> acc + x }
                when (sum) {
                    Rational.ONE -> true
                    Rational.ZERO -> false
                    else -> throw CalcException(Failure.NOT_DISTRIBUTION)
                }
            }.also { if (it.none { ok -> ok }) throw CalcException(Failure.NOT_DISTRIBUTION) }
        }
        return Process(n, m, ps, rewards, gamma, available)
    }

    /** q(s, a) = r(s, a) + γ Σ p(s′ | s, a) v(s′), exactly; null where a is not available in s. */
    private fun q(pr: Process, v: List<Rational>): List<List<Rational?>> = List(pr.n) { s ->
        List(pr.m) { a ->
            if (!pr.available[s][a]) null else {
                var sum = Rational.ZERO
                for (t in 0 until pr.n) {
                    val pst = pr.p[a][s][t]
                    if (pst.signum != 0) sum += pst * v[t]
                }
                pr.r[s][a] + pr.gamma * sum
            }
        }
    }

    /** The solution of a·x = b by Gaussian elimination with fractions, or null where a is singular. */
    private fun solve(a0: List<List<Rational>>, b0: List<Rational>): List<Rational>? {
        val n = b0.size
        val a = Array(n) { i -> Array(n + 1) { j -> if (j < n) a0[i][j] else b0[i] } }
        for (col in 0 until n) {
            Budget.check()
            val pivot = (col until n).firstOrNull { a[it][col].signum != 0 } ?: return null
            if (pivot != col) { val t = a[pivot]; a[pivot] = a[col]; a[col] = t }
            val p = a[col][col]
            for (j in col..n) a[col][j] = a[col][j] / p
            for (i in 0 until n) {
                if (i == col || a[i][col].signum == 0) continue
                val f = a[i][col]
                for (j in col..n) a[i][j] = a[i][j] - f * a[col][j]
            }
        }
        return List(n) { a[it][n] }
    }

    /** v_π of a (stochastic) policy π[s][a]: (I − γP_π) v = r_π solved exactly, and put back into v = r_π + γ P_π v. */
    private fun evaluate(pr: Process, pi: List<List<Rational>>): List<Rational> {
        val n = pr.n
        val pPi = List(n) { s -> List(n) { t -> (0 until pr.m).fold(Rational.ZERO) { acc, a -> if (pi[s][a].signum == 0) acc else acc + pi[s][a] * pr.p[a][s][t] } } }
        val rPi = List(n) { s -> (0 until pr.m).fold(Rational.ZERO) { acc, a -> if (pi[s][a].signum == 0) acc else acc + pi[s][a] * pr.r[s][a] } }
        val system = List(n) { s -> List(n) { t -> (if (s == t) Rational.ONE else Rational.ZERO) - pr.gamma * pPi[s][t] } }
        val v = solve(system, rPi) ?: throw CalcException(Failure.SINGULAR)
        // Put back: v = r_π + γ P_π v, row by row.
        for (s in 0 until n) {
            val back = rPi[s] + pr.gamma * (0 until n).fold(Rational.ZERO) { acc, t -> acc + pPi[s][t] * v[t] }
            if (back != v[s]) throw CalcException(Failure.UNSOLVED)
        }
        return v
    }

    /** A deterministic policy (an action in each state) as π[s][a]. */
    private fun deterministic(pr: Process, actions: IntArray): List<List<Rational>> =
        List(pr.n) { s -> List(pr.m) { a -> if (a == actions[s]) Rational.ONE else Rational.ZERO } }

    /** valueiteration(P₁, …, Pₘ, R, γ). */
    fun valueIteration(args: List<Expr>): Outcome {
        val pr = process(args)
        val n = pr.n
        val gamma = pr.gamma.toDouble()
        val p = pr.p.map { a -> a.map { row -> row.map { it.toDouble() } } }
        val r = pr.r.map { row -> row.map { it.toDouble() } }
        fun sweep(v: DoubleArray): DoubleArray = DoubleArray(n) { s ->
            (0 until pr.m).filter { pr.available[s][it] }.maxOf { a -> r[s][a] + gamma * (0 until n).sumOf { t -> p[a][s][t] * v[t] } }
        }
        // Value iteration in doubles, from v₀ = 0, until a sweep changes no value by more than 10⁻¹² (relatively).
        val all = ArrayList<List<Double>>()
        var v = DoubleArray(n)
        all += listOf(0.0) + v.toList() + listOf(Double.NaN)
        var delta = Double.NaN
        var k = 0
        while (k < MOST_SWEEPS) {
            Budget.check()
            val next = sweep(v)
            delta = (0 until n).maxOf { abs(next[it] - v[it]) }
            k++
            v = next
            all += listOf(k.toDouble()) + v.toList() + listOf(delta)
            if (delta <= 1e-12 * max(1.0, v.maxOf { abs(it) })) break
        }
        // The greedy policy of its last values, then policy iteration with fractions until no action is better.
        val actions = IntArray(n) { s ->
            (0 until pr.m).filter { pr.available[s][it] }.maxByOrNull { a -> r[s][a] + gamma * (0 until n).sumOf { t -> p[a][s][t] * v[t] } }!!
        }
        var exact: List<Rational>? = null
        var values: List<List<Rational?>>? = null
        for (round in 0 until 200) {
            Budget.check()
            val vPi = evaluate(pr, deterministic(pr, actions))
            val qs = q(pr, vPi)
            var changed = false
            for (s in 0 until n) {
                val best = qs[s].filterNotNull().max()
                if (best > qs[s][actions[s]]!!) {
                    actions[s] = qs[s].indexOfFirst { it == best }
                    changed = true
                }
            }
            if (!changed) { exact = vPi; values = qs; break }
        }
        val vStar = exact ?: throw CalcException(Failure.UNSOLVED)
        val qStar = values!!
        // The Bellman optimality equation, worked out again on its own: v*(s) = maxₐ q*(s, a) in every state.
        val again = q(pr, vStar)
        for (s in 0 until n) if (again[s].filterNotNull().max() != vStar[s]) throw CalcException(Failure.UNSOLVED)
        // And value iteration's last values lie within its error bound γΔ/(1 − γ) of v*.
        val bound = (if (delta.isNaN()) 0.0 else gamma * delta / (1 - gamma)) + 1e-9 * max(1.0, vStar.maxOf { abs(it.toDouble()) })
        if ((0 until n).any { abs(v[it] - vStar[it].toDouble()) > bound }) throw CalcException(Failure.UNSOLVED)
        val policy = List(n) { s -> (0 until pr.m).filter { qStar[s][it] == vStar[s] }.map { it + 1 } }
        val rows = if (all.size <= 16) all else all.take(11) + listOf(all.last())
        val columns = listOf("k") + (1..n).map { "v_k(s_{$it})" } + listOf("\\Delta_k")
        val table = Numerics.Table(Numerics.Method.VALUE_ITERATION, columns, rows, null)
        return Outcome.DecisionProcess(vStar.map { num(it) }, qStar.map { row -> row.map { it?.let { x -> num(x) } } }, policy, table,
            num(pr.gamma), k)
    }

    /** policyevaluation(P₁, …, Pₘ, R, γ, π): v_π, exactly. */
    fun policyEvaluation(args: List<Expr>): Outcome {
        if (args.size < 4) throw CalcException(Failure.ARGUMENTS)
        val pr = process(args.dropLast(1))
        val pi = entries(args.last().takeIf { it is Expr.Mat || pr.n * pr.m == 1 } ?: throw CalcException(Failure.ARGUMENTS))
        if (pi.size != pr.n || pi.any { it.size != pr.m }) throw CalcException(Failure.SIZES)
        for (s in 0 until pr.n) {
            if (pi[s].any { it.signum < 0 }) throw CalcException(Failure.NOT_DISTRIBUTION)
            if (pi[s].fold(Rational.ZERO) { acc, x -> acc + x } != Rational.ONE) throw CalcException(Failure.NOT_DISTRIBUTION)
            if ((0 until pr.m).any { a -> !pr.available[s][a] && pi[s][a].signum != 0 }) throw CalcException(Failure.NOT_DISTRIBUTION)
        }
        val v = evaluate(pr, pi)
        return Outcome.Expression(LinAlg.column(v.map { num(it) }))
    }

    /** The policy's transition matrix P_π and reward vector r_π, for the steps. */
    internal fun policyParts(args: List<Expr>): Pair<List<List<Rational>>, List<Rational>>? = try {
        val pr = process(args.dropLast(1))
        val pi = entries(args.last())
        val pPi = List(pr.n) { s -> List(pr.n) { t -> (0 until pr.m).fold(Rational.ZERO) { acc, a -> acc + pi[s][a] * pr.p[a][s][t] } } }
        val rPi = List(pr.n) { s -> (0 until pr.m).fold(Rational.ZERO) { acc, a -> acc + pi[s][a] * pr.r[s][a] } }
        pPi to rPi
    } catch (e: CalcException) {
        null
    }
}
