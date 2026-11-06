package com.professoreuler.mathcalc

/**
 * Orthogonality: the Gram–Schmidt process, which makes vectors v₁, …, vₖ orthonormal (uⱼ = vⱼ − Σᵢ (vⱼ·uᵢ)/(uᵢ·uᵢ)·uᵢ over the
 * uᵢ before it, eⱼ = uⱼ/|uⱼ|, a vector in the span of those before it left out), and the orthogonal projection of a vector onto
 * another, p = (v·u)/(u·u)·u, or onto the span of several (the sum of its projections onto an orthogonal basis of the span).
 * Exact, with roots: every eₖ is checked, eᵢ·eⱼ = 1 for i = j and 0 otherwise, and every vₖ is the sum of its projections onto
 * the e's; every projection is checked, its rest v − p orthogonal to every vector it was projected onto.
 */
object Orthogonal {
    /**
     * Gram–Schmidt on [vectors]: the [orthogonal] uⱼ and the [orthonormal] eⱼ = uⱼ/|uⱼ| of those kept, in order; [dependent] the
     * (0-based) places of the vectors that lie in the span of those before them (their u is 0), which are left out.
     */
    class Basis(val vectors: List<Expr.Mat>, val orthogonal: List<Expr.Mat>, val orthonormal: List<Expr.Mat>, val dependent: List<Int>)

    internal fun work(statements: List<Statement>, c: Choice): Iterative.Work {
        val vectors = statements.map { s ->
            if (s.relations.isNotEmpty() || s.sides.size != 1 || s.sides[0] == null) throw CalcException(Failure.ARGUMENTS)
            (Calculus.evaluate(s.sides[0]!!) as? Expr.Mat)?.takeIf { it.columns == 1 } ?: throw CalcException(Failure.ARGUMENTS)
        }
        if (vectors.map { it.size }.distinct().size != 1) throw CalcException(Failure.SIZES)
        if (c.mode == Mode.PROJECTION) {
            if (vectors.size < 2) throw CalcException(Failure.ARGUMENTS)
            return Iterative.Work(listOf(c)) { Outcome.Expression(projection(vectors[0], vectors.drop(1))) }
        }
        return Iterative.Work(listOf(c)) { Outcome.Orthonormalized(gramSchmidt(vectors)) }
    }

    private fun tidy(e: Expr) = Calculus.tidy(e)

    private fun Expr.Mat.entries() = rows.map { it[0] }

    private fun isZeroVector(v: Expr.Mat) = v.entries().all { LinAlg.isNull(it) }

    /** Real numbers only: a vector with letters or i is refused (whether it is 0 could not be decided, and dot products would need conjugates). */
    private fun numbers(v: Expr.Mat) {
        if (v.hasI()) throw CalcException(Failure.UNSOLVED)
        if (v.symbols().isNotEmpty() || v.entries().any { Numeric.value(it)?.isFinite() != true }) throw CalcException(Failure.UNSOLVED)
    }

    /** v − c·u, each entry simplified. */
    private fun minus(v: Expr.Mat, c: Expr, u: Expr.Mat): Expr.Mat = LinAlg.column(v.entries().indices.map { i -> tidy(v.rows[i][0] - mul(c, u.rows[i][0])) })

    /** The classical Gram–Schmidt process, exactly, and its result checked. */
    fun gramSchmidt(vectors: List<Expr.Mat>): Basis {
        vectors.forEach { numbers(it) }
        val us = ArrayList<Expr.Mat>()
        val dependent = ArrayList<Int>()
        for ((j, v) in vectors.withIndex()) {
            Budget.check()
            var u = v
            for (w in us) u = minus(u, tidy(LinAlg.dot(v, w) / LinAlg.dot(w, w)), w)
            // Entries that are 0 only by their numbers are written as 0.
            u = LinAlg.column(u.entries().map { if (LinAlg.isNull(it)) Expr.ZERO else it })
            if (isZeroVector(u)) dependent += j else us += u
        }
        val es = us.map { u ->
            val length = LinAlg.norm(u)
            LinAlg.column(u.entries().map { Algebra.rationalize(tidy(it / length)) })
        }
        // Checked: orthonormal, and every vector the sum of its projections onto the e's.
        for (i in es.indices) for (k in es.indices) {
            val want = if (i == k) Expr.ONE else Expr.ZERO
            if (!LinAlg.isNull(tidy(LinAlg.dot(es[i], es[k]) - want))) throw CalcException(Failure.UNSOLVED)
        }
        for (v in vectors) {
            var rest = v
            for (e in es) rest = minus(rest, LinAlg.dot(v, e), e)
            if (!isZeroVector(rest)) throw CalcException(Failure.UNSOLVED)
        }
        return Basis(vectors, us, es, dependent)
    }

    /**
     * The orthogonal projection of [v] onto [onto]: onto one vector u (not 0) p = (v·u)/(u·u)·u; onto the span of several the sum
     * of v's projections onto an orthogonal basis of it (Gram–Schmidt). [v] may have letters; the vectors it is projected onto are
     * numbers. Checked: v − p is orthogonal to every one of them.
     */
    fun projection(v: Expr.Mat, onto: List<Expr.Mat>): Expr.Mat {
        if (v.hasI()) throw CalcException(Failure.UNSOLVED)
        if (onto.any { it.size != v.size }) throw CalcException(Failure.SIZES)
        onto.forEach { numbers(it) }
        val basis = if (onto.size == 1) listOf(onto[0]) else gramSchmidt(onto).orthogonal
        if (basis.isEmpty() || basis.any { isZeroVector(it) }) throw CalcException(Failure.DIVISION_BY_ZERO)
        var p = LinAlg.column(List(v.size) { Expr.ZERO })
        for (w in basis) {
            val c = tidy(LinAlg.dot(v, w) / LinAlg.dot(w, w))
            p = LinAlg.column(p.entries().indices.map { i -> tidy(p.rows[i][0] + mul(c, w.rows[i][0])) })
        }
        // Checked: the rest is orthogonal to every vector.
        val rest = LinAlg.column(v.entries().indices.map { i -> tidy(v.rows[i][0] - p.rows[i][0]) })
        for (u in onto) if (!LinAlg.isNull(tidy(Algebra.expand(LinAlg.dot(rest, u))))) throw CalcException(Failure.UNSOLVED)
        return LinAlg.column(p.entries().map { Algebra.rationalize(it) })
    }
}
