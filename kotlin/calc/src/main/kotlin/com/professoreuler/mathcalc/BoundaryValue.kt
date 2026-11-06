package com.professoreuler.mathcalc

/**
 * Boundary value problems of linear differential equations: values of y (or y′) given at two different points, y(0) = a,
 * y(L) = b. The general solution is linear in its constants, so the conditions are a linear system for them, decided exactly by
 * elimination: one solution, none (the system is inconsistent: 0 = c with c ≠ 0), or infinitely many (a family in the constants
 * left free). Every solution, a family with its free constants as letters, is put back into the equation and every condition.
 */
object BoundaryValue {
    enum class Kind { UNIQUE, NONE, INFINITE }

    /**
     * The problem solved: its [general] solution and [conditions]; for [Kind.UNIQUE] the [solution], for [Kind.INFINITE] the
     * family in its [free] constants, for [Kind.NONE] no solution. [system] is the conditions as equations in the constants
     * (each "= 0"), whose elimination decides the kind.
     */
    class Problem(val y: String, val x: String, val general: Ode.Solution, val conditions: List<Ode.Condition>, val system: List<Expr>,
        val kind: Kind, val solution: Expr?, val free: List<String>)

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** Whether [conditions] stand at more than one point: a boundary value problem rather than an initial value problem. */
    fun isBoundary(conditions: List<Ode.Condition>): Boolean =
        conditions.size >= 2 && conditions.any { c -> !LinAlg.isNull(tidy(c.at - conditions[0].at)) }

    fun solve(equation: Expr, general: Ode.Solution, conditions: List<Ode.Condition>, y: String, x: String): Problem {
        val f = general.explicit ?: throw CalcException(Failure.UNSOLVED)
        val linear = general.method == Ode.Method.CONSTANT_COEFFICIENTS || general.method == Ode.Method.CAUCHY_EULER || general.method == Ode.Method.LINEAR
        if (!linear) throw CalcException(Failure.UNSOLVED)
        val xs = Expr.Sym(x)
        val constants = general.constants
        val cs = constants.map { Expr.Sym(it) }
        // Each condition: y⁽ᵏ⁾(x₀) − v = 0, a linear equation in the constants.
        val system = conditions.map { c ->
            var d = f
            repeat(c.k) { d = Calculus.derivative(d, xs) }
            tidy(Algebra.expand(d.substitute(xs, c.at) - c.value))
        }
        val rows = ArrayList<List<Expr>>()
        for (e in system) {
            Budget.check()
            val row = cs.map { tidy(Calculus.derivative(e, it)) }
            if (row.any { it.symbols().isNotEmpty() || Numeric.value(it) == null }) throw CalcException(Failure.UNSOLVED)
            val rest = tidy(cs.fold(e) { acc, k -> acc.substitute(k, Expr.ZERO) })
            if (rest.symbols().isNotEmpty() || Numeric.value(rest) == null) throw CalcException(Failure.UNSOLVED)
            if (!LinAlg.isNull(tidy(Algebra.expand(e - add(cs.indices.map { mul(row[it], cs[it]) }) - rest)))) throw CalcException(Failure.UNSOLVED)
            rows += row + tidy(neg(rest))
        }
        val augmented = Expr.Mat(rows)
        val echelon = LinAlg.echelon(augmented)
        val n = constants.size
        if (n in echelon.pivots) return Problem(y, x, general, conditions, system, Kind.NONE, null, emptyList())
        val freeColumns = (0 until n).filter { it !in echelon.pivots }
        val values = HashMap<String, Expr>()
        for ((row, pc) in echelon.pivots.withIndex()) {
            var v: Expr = echelon.form.rows[row][n]
            for (column in freeColumns) v = v - mul(echelon.form.rows[row][column], cs[column])
            values[constants[pc]] = tidy(v)
        }
        val solution = tidy(Algebra.expand(values.entries.fold(f) { acc, (k, v) -> acc.substitute(Expr.Sym(k), v) }))
        // Put back: into the equation and every condition, the free constants as letters.
        val check = Ode.Solution(solution, null, freeColumns.map { constants[it] }, general.method)
        if (!Ode.holds(equation, check, y, x)) throw CalcException(Failure.UNSOLVED)
        for (c in conditions) {
            var d = solution
            repeat(c.k) { d = Calculus.derivative(d, xs) }
            if (!OdeSystems.vanishes(d.substitute(xs, c.at) - c.value)) throw CalcException(Failure.UNSOLVED)
        }
        val free = freeColumns.map { constants[it] }
        return Problem(y, x, general, conditions, system, if (free.isEmpty()) Kind.UNIQUE else Kind.INFINITE, solution, free)
    }
}
