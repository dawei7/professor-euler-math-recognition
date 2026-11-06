package com.professoreuler.mathcalc

/**
 * Linear programs: a linear objective maximized or minimized under linear constraints (≤, ≥, =), worked out exactly in
 * fractions by the simplex method, in two phases, with Bland's rule (the entering column and the leaving row of smallest index),
 * which never cycles. Its letters are free: only the constraints written bound them (x ≥ 0 is a constraint to write, not
 * assumed; the solution names the letters no written constraint keeps from being negative). Strict inequalities and ≠ are not
 * taken (an optimum on a strict bound is not attained).
 *
 * Every answer comes with its certificate, checked exactly:
 * - an optimum: the point satisfies every constraint, and multipliers λ (the dual solution, worked out as a linear program of its
 *   own) write the objective as Σ λᵢ·(constraint i's left side) with the signs that make each a bound in the right direction,
 *   and Σ λᵢ·(its right side) is the optimum: no feasible point can do better (weak duality);
 * - unbounded: a feasible point and a direction that keeps every constraint and improves the objective without end;
 * - no feasible point: multipliers with Σ λᵢ·(left side) = 0 but Σ λᵢ·(right side) < 0 in the direction of the constraints
 *   (Farkas' lemma), a contradiction for any point.
 *
 * Two letters also give the feasible region's corners in order, the objective at each, and its rays where it is unbounded, for
 * the picture.
 */
object LinearProgram {
    enum class Status { OPTIMAL, UNBOUNDED, INFEASIBLE }

    /** A constraint as read: Σ [coefficients]ᵢ·letterᵢ [relation] [bound] (≤, ≥ or =). */
    class Constraint(val coefficients: List<Rational>, val relation: Rel, val bound: Rational)

    /**
     * The feasible region of a program in two letters: its [corners] in order along the boundary, the objective's [values] there,
     * the corners where the optimum is attained ([optimal], two of them where a whole edge is optimal), whether it is
     * [bounded], and where it is not the [rays] that leave it, each from the corner of its index in a direction (dx, dy).
     */
    class Picture(val corners: List<Pair<Expr, Expr>>, val values: List<Expr>, val optimal: List<Int>, val bounded: Boolean,
        val rays: List<Pair<Int, Pair<Expr, Expr>>>)

    /**
     * A program worked out: whether it was to [maximize], its [letters], [objective] and [constraints], its [status]; the optimum
     * [value] and a [point] where it is attained (for an unbounded one the feasible point its [ray] starts from); the
     * [multipliers] that certify it (one for each constraint: an optimum's dual solution, the shadow prices, ∂ optimum/∂ bound;
     * for no feasible point Farkas' multipliers); the [free] letters, which no constraint written as a bound keeps from being
     * negative; and the [picture] of a program in two letters.
     */
    class Solution(val maximize: Boolean, val letters: List<String>, val objective: Expr, val constraints: List<Constraint>, val status: Status,
        val value: Expr?, val point: List<Expr>?, val ray: List<Expr>?, val multipliers: List<Expr>?, val free: List<String>, val picture: Picture?)

    /** At most this many letters and constraints: beyond them exact fractions take too long. */
    private const val MOST_LETTERS = 12
    private const val MOST_CONSTRAINTS = 40

    // ------------------------------------------------------------------------------------------------------------------
    // Reading.

    /** The program of [statements]: the objective, then a constraint on each line (a chain a ≤ b ≤ c is two). */
    fun of(statements: List<Statement>, maximize: Boolean): Outcome {
        if (statements.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        val head = statements[0]
        val objectiveSides = head.sides.filterNotNull()
        if (objectiveSides.size != 1 || head.relations.isNotEmpty()) throw CalcException(Failure.ARGUMENTS)
        val objective = Calculus.evaluate(objectiveSides[0])
        val relations = ArrayList<Triple<Expr, Rel, Expr>>()
        for (s in statements.drop(1)) {
            val sides = s.sides.filterNotNull()
            if (sides.size < 2 || s.relations.size < sides.size - 1) throw CalcException(Failure.ARGUMENTS)
            for (k in 0 until sides.size - 1) {
                val rel = s.relations[k]
                if (rel != Rel.LE && rel != Rel.GE && rel != Rel.EQ) throw CalcException(Failure.ARGUMENTS)
                relations += Triple(Calculus.evaluate(sides[k]), rel, Calculus.evaluate(sides[k + 1]))
            }
        }
        if (relations.size > MOST_CONSTRAINTS) throw CalcException(Failure.TOO_LARGE)
        val letters = (objective.symbols() + relations.flatMap { it.first.symbols() + it.third.symbols() }).sortedWith(LETTER_ORDER)
        if (letters.isEmpty()) throw CalcException(Failure.ARGUMENTS)
        if (letters.size > MOST_LETTERS) throw CalcException(Failure.TOO_LARGE)
        val (c, c0) = linear(objective, letters)
        val constraints = relations.map { (lhs, rel, rhs) ->
            val (a, a0) = linear(tidy(lhs - rhs), letters)
            // 0 ≤ x and 4 ≥ x + y read as x ≥ 0 and x + y ≤ 4: turned round where every letter has a minus.
            if (a.all { it.signum <= 0 } && a.any { it.signum < 0 }) Constraint(a.map { -it }, rel.flipped, a0) else Constraint(a, rel, -a0)
        }
        return Outcome.Optimized(solve(maximize, letters, objective, c, c0, constraints))
    }

    private fun tidy(e: Expr) = Calculus.tidy(e)

    /** x before y, x_2 before x_10. */
    private val LETTER_ORDER = Comparator<String> { a, b ->
        val (ba, na) = a.substringBefore('_') to a.substringAfter('_', "").toIntOrNull()
        val (bb, nb) = b.substringBefore('_') to b.substringAfter('_', "").toIntOrNull()
        when {
            ba != bb -> ba.compareTo(bb)
            na != null && nb != null -> na.compareTo(nb)
            else -> a.compareTo(b)
        }
    }

    /** [e] as Σ aᵢ·letterᵢ + a₀ with rational a: its coefficients and constant; it must be exactly that. */
    private fun linear(e: Expr, letters: List<String>): Pair<List<Rational>, Rational> {
        if (e is Expr.Mat || e.hasInfinity() || e.hasOp()) throw CalcException(Failure.ARGUMENTS)
        val coefficients = letters.map { v ->
            val d = tidy(Calculus.derivative(e, Expr.Sym(v)))
            if (d.symbols().isNotEmpty()) throw CalcException(Failure.NOT_LINEAR)
            rational(d) ?: throw CalcException(if (d.hasDecimal() || Numeric.value(d) != null) Failure.UNSOLVED else Failure.NOT_LINEAR)
        }
        val constant = rational(tidy(letters.fold(e) { acc, v -> acc.substitute(Expr.Sym(v), Expr.ZERO) })) ?: throw CalcException(Failure.NOT_LINEAR)
        // Nothing else in it: e − (Σ aᵢ·xᵢ + a₀) is 0.
        val rebuilt = add(letters.indices.map { mul(num(coefficients[it]), Expr.Sym(letters[it])) } + num(constant))
        if (!isZero(tidy(Algebra.expand(tidy(e - rebuilt))))) throw CalcException(Failure.NOT_LINEAR)
        return coefficients to constant
    }

    // ------------------------------------------------------------------------------------------------------------------
    // Solving, with certificates.

    private fun solve(maximize: Boolean, letters: List<String>, objective: Expr, c: List<Rational>, c0: Rational, rows: List<Constraint>): Solution {
        val n = letters.size
        // As a maximum: min cᵀx is −max (−c)ᵀx.
        val cMax = if (maximize) c else c.map { -it }
        val free = letters.filterIndexed { j, _ -> rows.none { r -> boundsBelow(r, j) } }
        val primal = Simplex.solve(cMax, rows)
        val picture = if (n == 2) picture(c, c0, rows) else null
        fun z(x: List<Rational>) = c.indices.fold(c0) { acc, j -> acc + c[j] * x[j] }
        return when (primal) {
            is Simplex.Result.Optimal -> {
                val x = primal.x
                if (!rows.all { feasible(it, x) }) throw CalcException(Failure.UNSOLVED)
                // The dual: min βᵀy with Aᵀy = c, y ≥ 0 on ≤, y ≤ 0 on ≥, free on =; its value must be the optimum.
                val dual = Simplex.solve(rows.map { -it.bound }, dualRows(rows, cMax))
                val y = (dual as? Simplex.Result.Optimal)?.x ?: throw CalcException(Failure.UNSOLVED)
                if (!certifiesOptimum(rows, cMax, x, y)) throw CalcException(Failure.UNSOLVED)
                // Multipliers of the objective as asked: c = Σ λᵢ aᵢ, z − c₀ = Σ λᵢ βᵢ (for a minimum λ = −y).
                val lambda = if (maximize) y else y.map { -it }
                val best = num(z(x))
                // The picture's corners where the optimum is attained (two: the whole edge between them).
                val marked = picture?.let { p -> Picture(p.corners, p.values, p.values.indices.filter { p.values[it] == best }, p.bounded, p.rays) }
                Solution(maximize, letters, objective, rows, Status.OPTIMAL, best, x.map { num(it) }, null, lambda.map { num(it) }, free, marked)
            }
            is Simplex.Result.Unbounded -> {
                val x = primal.x
                val d = primal.direction
                if (!rows.all { feasible(it, x) } || !rows.all { recedes(it, d) }) throw CalcException(Failure.UNSOLVED)
                if (d.indices.fold(Rational.ZERO) { acc, j -> acc + cMax[j] * d[j] }.signum <= 0) throw CalcException(Failure.UNSOLVED)
                Solution(maximize, letters, objective, rows, Status.UNBOUNDED, null, x.map { num(it) }, d.map { num(it) }, null, free, picture)
            }
            Simplex.Result.Infeasible -> {
                // Farkas: y with the signs of the dual, Aᵀy = 0 and βᵀy = −1.
                val zero = List(n) { Rational.ZERO }
                val farkas = dualRows(rows, zero) + Constraint(rows.map { it.bound }, Rel.EQ, Rational.of(-1))
                val y = (Simplex.solve(List(rows.size) { Rational.ZERO }, farkas) as? Simplex.Result.Optimal)?.x ?: throw CalcException(Failure.UNSOLVED)
                if (!certifiesInfeasible(rows, n, y)) throw CalcException(Failure.UNSOLVED)
                Solution(maximize, letters, objective, rows, Status.INFEASIBLE, null, null, null, y.map { num(it) }, free, picture)
            }
        }
    }

    /** Whether a constraint is a bound of letter [j] alone that keeps it from being negative: a·x ≥ β with β/a ≥ 0 (a > 0). */
    private fun boundsBelow(r: Constraint, j: Int): Boolean {
        if (r.coefficients.withIndex().any { (k, a) -> k != j && a.signum != 0 }) return false
        val a = r.coefficients[j]
        if (a.signum == 0) return false
        val limit = r.bound / a
        val below = (r.relation == Rel.GE && a.signum > 0) || (r.relation == Rel.LE && a.signum < 0) || r.relation == Rel.EQ
        return below && limit.signum >= 0
    }

    private fun dot(a: List<Rational>, x: List<Rational>) = a.indices.fold(Rational.ZERO) { acc, j -> acc + a[j] * x[j] }

    private fun feasible(r: Constraint, x: List<Rational>): Boolean {
        val v = dot(r.coefficients, x)
        return when (r.relation) {
            Rel.LE -> v <= r.bound
            Rel.GE -> v >= r.bound
            else -> v == r.bound
        }
    }

    /** x + t·d keeps the constraint for every t ≥ 0. */
    private fun recedes(r: Constraint, d: List<Rational>): Boolean {
        val v = dot(r.coefficients, d)
        return when (r.relation) {
            Rel.LE -> v.signum <= 0
            Rel.GE -> v.signum >= 0
            else -> v.signum == 0
        }
    }

    /** The dual's constraints: Σᵢ aᵢⱼ yᵢ = cⱼ for every letter j, and yᵢ ≥ 0 for a ≤ row, yᵢ ≤ 0 for a ≥ row. */
    private fun dualRows(rows: List<Constraint>, c: List<Rational>): List<Constraint> {
        val m = rows.size
        val out = ArrayList<Constraint>()
        for (j in c.indices) out += Constraint(rows.map { it.coefficients[j] }, Rel.EQ, c[j])
        for ((i, r) in rows.withIndex()) {
            val unit = List(m) { if (it == i) Rational.ONE else Rational.ZERO }
            when (r.relation) {
                Rel.LE -> out += Constraint(unit, Rel.GE, Rational.ZERO)
                Rel.GE -> out += Constraint(unit, Rel.LE, Rational.ZERO)
                else -> {}
            }
        }
        return out
    }

    private fun signsHold(rows: List<Constraint>, y: List<Rational>) = rows.indices.all { i ->
        when (rows[i].relation) {
            Rel.LE -> y[i].signum >= 0
            Rel.GE -> y[i].signum <= 0
            else -> true
        }
    }

    /** Aᵀy = c with the dual's signs, and βᵀy = cᵀx: then cᵀx' ≤ βᵀy = cᵀx for every feasible x'. */
    private fun certifiesOptimum(rows: List<Constraint>, c: List<Rational>, x: List<Rational>, y: List<Rational>): Boolean {
        if (!signsHold(rows, y)) return false
        for (j in c.indices) if (rows.indices.fold(Rational.ZERO) { acc, i -> acc + rows[i].coefficients[j] * y[i] } != c[j]) return false
        return rows.indices.fold(Rational.ZERO) { acc, i -> acc + rows[i].bound * y[i] } == dot(c, x)
    }

    /** Aᵀy = 0 with the dual's signs and βᵀy < 0: then 0 = yᵀAx ≤ βᵀy < 0 for any feasible x, so there is none. */
    private fun certifiesInfeasible(rows: List<Constraint>, n: Int, y: List<Rational>): Boolean {
        if (!signsHold(rows, y)) return false
        for (j in 0 until n) if (rows.indices.fold(Rational.ZERO) { acc, i -> acc + rows[i].coefficients[j] * y[i] }.signum != 0) return false
        return rows.indices.fold(Rational.ZERO) { acc, i -> acc + rows[i].bound * y[i] }.signum < 0
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The picture of two letters.

    private fun picture(c: List<Rational>, c0: Rational, rows: List<Constraint>): Picture {
        // Every corner: two constraint lines that cross, at a point that keeps every constraint.
        val corners = ArrayList<Pair<Rational, Rational>>()
        for (i in rows.indices) for (j in i + 1 until rows.size) {
            Budget.check()
            val (a1, b1) = rows[i].coefficients
            val (a2, b2) = rows[j].coefficients
            val det = a1 * b2 - a2 * b1
            if (det.signum == 0) continue
            val p = (rows[i].bound * b2 - rows[j].bound * b1) / det
            val q = (a1 * rows[j].bound - a2 * rows[i].bound) / det
            if (rows.all { feasible(it, listOf(p, q)) } && corners.none { it.first == p && it.second == q }) corners += p to q
        }
        if (corners.isEmpty()) return Picture(emptyList(), emptyList(), emptyList(), bounded = false, rays = emptyList())
        // The edges that run off: along a constraint's line through a corner, in a direction that keeps every constraint,
        // from the last corner on that line in that direction (two parallel ones where the region is a U).
        val rays = ArrayList<Pair<Int, Pair<Rational, Rational>>>()
        for (r in rows) {
            val (a, b) = r.coefficients
            if (a.signum == 0 && b.signum == 0) continue
            for (d in listOf(-b to a, b to -a)) {
                if (!rows.all { recedes(it, listOf(d.first, d.second)) }) continue
                val start = corners.indices.filter { onLine(r, corners[it]) }
                    .maxByOrNull { k -> corners[k].first * d.first + corners[k].second * d.second } ?: continue
                if (rays.none { it.first == start && parallel(it.second, d) }) rays += start to d
            }
        }
        val bounded = rays.isEmpty()
        val order = boundaryOrder(corners, rays)
        val ordered = order.map { corners[it] }
        val values = ordered.map { (p, q) -> c0 + c[0] * p + c[1] * q }
        return Picture(ordered.map { num(it.first) to num(it.second) }, values.map { num(it) }, emptyList(), bounded,
            rays.map { (k, d) -> order.indexOf(k) to (num(d.first) to num(d.second)) })
    }

    private fun onLine(r: Constraint, p: Pair<Rational, Rational>) = dot(r.coefficients, listOf(p.first, p.second)) == r.bound

    private fun parallel(a: Pair<Rational, Rational>, b: Pair<Rational, Rational>): Boolean =
        (a.first * b.second - a.second * b.first).signum == 0 && (a.first * b.first + a.second * b.second).signum > 0

    /** The corners' indices in order along the boundary: by angle about a point inside (far points on the rays included). */
    private fun boundaryOrder(corners: List<Pair<Rational, Rational>>, rays: List<Pair<Int, Pair<Rational, Rational>>>): List<Int> {
        if (corners.size <= 2 && rays.isEmpty()) return corners.indices.sortedWith(compareBy({ corners[it].first }, { corners[it].second }))
        val points = corners.map { it.first.toDouble() to it.second.toDouble() }.toMutableList()
        val span = points.maxOf { maxOf(Math.abs(it.first), Math.abs(it.second)) } + 1.0
        val far = rays.map { (k, d) ->
            val len = Math.hypot(d.first.toDouble(), d.second.toDouble())
            (points[k].first + 10 * span * d.first.toDouble() / len) to (points[k].second + 10 * span * d.second.toDouble() / len)
        }
        val all = points + far
        val cx = all.sumOf { it.first } / all.size
        val cy = all.sumOf { it.second } / all.size
        val angles = all.map { Math.atan2(it.second - cy, it.first - cx) }
        val cyclic = all.indices.sortedBy { angles[it] }
        if (far.isEmpty()) return cyclic
        // Start just after a far point, so the corners run from one ray to the other.
        val firstFar = cyclic.indexOfFirst { it >= points.size }
        val rotated = cyclic.drop(firstFar + 1) + cyclic.take(firstFar + 1)
        return rotated.filter { it < points.size }
    }

    // ------------------------------------------------------------------------------------------------------------------
    // The simplex method.

    /**
     * max cᵀx subject to [Constraint]s, x free: in standard form with x = p − q (p, q ≥ 0), a slack for each inequality and the
     * rows' right sides made nonnegative; phase one minimizes the artificial variables (none left: a feasible basis), phase two the
     * objective, both by Bland's rule in exact fractions.
     */
    internal object Simplex {
        sealed class Result {
            class Optimal(val x: List<Rational>) : Result()
            class Unbounded(val x: List<Rational>, val direction: List<Rational>) : Result()
            object Infeasible : Result()
        }

        fun solve(c: List<Rational>, rows: List<Constraint>): Result {
            val n = c.size
            val m = rows.size
            val slackOf = IntArray(m) { -1 }
            var columns = 2 * n
            for (i in 0 until m) if (rows[i].relation != Rel.EQ) slackOf[i] = columns++
            val structural = columns
            // The tableau: m rows of structural + m artificial columns and the right side.
            val width = structural + m + 1
            val t = Array(m) { Array(width) { Rational.ZERO } }
            for (i in 0 until m) {
                val r = rows[i]
                for (j in 0 until n) {
                    t[i][j] = r.coefficients[j]
                    t[i][n + j] = -r.coefficients[j]
                }
                if (slackOf[i] >= 0) t[i][slackOf[i]] = if (r.relation == Rel.LE) Rational.ONE else Rational.of(-1)
                t[i][width - 1] = r.bound
                if (r.bound.signum < 0) for (j in 0 until width) t[i][j] = -t[i][j]
                t[i][structural + i] = Rational.ONE
            }
            val basis = IntArray(m) { structural + it }
            val live = BooleanArray(m) { true }
            // Phase one: the artificials' sum made least.
            val phaseOne = Array(width - 1) { if (it >= structural) Rational.ONE else Rational.ZERO }
            if (run(t, basis, live, phaseOne, width - 1) != null) throw CalcException(Failure.UNSOLVED)
            val infeasibility = (0 until m).filter { live[it] && basis[it] >= structural }.fold(Rational.ZERO) { acc, i -> acc + t[i][width - 1] }
            if (infeasibility.signum != 0) return Result.Infeasible
            // Artificials still in the basis (at 0) are pivoted out, or their row is redundant and dropped.
            for (i in 0 until m) {
                if (!live[i] || basis[i] < structural) continue
                val j = (0 until structural).firstOrNull { t[i][it].signum != 0 }
                if (j == null) live[i] = false else pivot(t, basis, live, i, j)
            }
            // Phase two: max cᵀ(p − q) as min (−c)ᵀp + cᵀq, over the structural columns only.
            val cost = Array(width - 1) { j ->
                when {
                    j < n -> -c[j]
                    j < 2 * n -> c[j - n]
                    else -> Rational.ZERO
                }
            }
            val unbounded = run(t, basis, live, cost, structural)
            val y = point(t, basis, live, structural)
            val x = List(n) { y[it] - y[n + it] }
            if (unbounded != null) {
                val dy = Array(structural) { Rational.ZERO }
                dy[unbounded] = Rational.ONE
                for (i in 0 until m) if (live[i]) dy[basis[i]] = -t[i][unbounded]
                return Result.Unbounded(x, List(n) { dy[it] - dy[n + it] })
            }
            return Result.Optimal(x)
        }

        private fun point(t: Array<Array<Rational>>, basis: IntArray, live: BooleanArray, columns: Int): Array<Rational> {
            val y = Array(columns) { Rational.ZERO }
            val rhs = t[0].size - 1
            for (i in t.indices) if (live[i] && basis[i] < columns) y[basis[i]] = t[i][rhs]
            return y
        }

        /**
         * Minimizes [cost] over the columns below [allowed] by Bland's rule; null at an optimum, or the entering column along
         * which the objective falls without bound.
         */
        private fun run(t: Array<Array<Rational>>, basis: IntArray, live: BooleanArray, cost: Array<Rational>, allowed: Int): Int? {
            val rhs = t[0].size - 1
            val m = t.size
            while (true) {
                Budget.check()
                var entering = -1
                for (j in 0 until allowed) {
                    if ((0 until m).any { live[it] && basis[it] == j }) continue
                    var reduced = cost[j]
                    for (i in 0 until m) if (live[i] && t[i][j].signum != 0) reduced -= cost[basis[i]] * t[i][j]
                    if (reduced.signum < 0) { entering = j; break }
                }
                if (entering < 0) return null
                var leaving = -1
                var best: Rational? = null
                for (i in 0 until m) {
                    if (!live[i] || t[i][entering].signum <= 0) continue
                    val ratio = t[i][rhs] / t[i][entering]
                    val b = best
                    if (b == null || ratio < b || (ratio == b && basis[i] < basis[leaving])) { best = ratio; leaving = i }
                }
                if (leaving < 0) return entering
                pivot(t, basis, live, leaving, entering)
            }
        }

        private fun pivot(t: Array<Array<Rational>>, basis: IntArray, live: BooleanArray, r: Int, c: Int) {
            val p = t[r][c]
            for (j in t[r].indices) t[r][j] = t[r][j] / p
            for (i in t.indices) {
                if (i == r || !live[i] || t[i][c].signum == 0) continue
                val f = t[i][c]
                for (j in t[i].indices) if (t[r][j].signum != 0) t[i][j] = t[i][j] - f * t[r][j]
            }
            basis[r] = c
        }
    }
}
