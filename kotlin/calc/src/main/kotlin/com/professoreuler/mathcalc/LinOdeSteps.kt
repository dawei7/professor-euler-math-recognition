package com.professoreuler.mathcalc

/**
 * Worked steps for the singular value decomposition, the Jordan form, systems of linear differential equations, boundary value
 * problems and power series solutions, from what their outcomes hold. Each step says what was done and shows the formula after
 * it; the last is the answer the card shows.
 */
internal object LinOdeSteps {
    fun of(c: Calculation, latex: String, assumptions: Assumptions): List<Step>? = try {
        Builder(c, latex, assumptions).build()?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private class Builder(val c: Calculation, val latex: String, val assumptions: Assumptions) {
        val out = ArrayList<Step>()

        fun l(e: Expr) = Format.expr(e, c, latex = true)
        fun v(e: Expr) = c.latexValue(e)
        fun name(s: String) = Format.name(s, latex = true)

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        /** The formula's matrix, its calculus worked out, or null. */
        fun matrix(): Expr.Mat? = try {
            val statements = Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList(), Flags(assumptions)).statements()
            statements.singleOrNull()?.sides?.filterNotNull()?.firstOrNull()?.let { Calculus.evaluate(it) } as? Expr.Mat
        } catch (e: CalcException) {
            null
        }

        fun build(): List<Step>? {
            when (val o = c.outcome) {
                is Outcome.SingularValues -> svd(o.svd)
                is Outcome.JordanForm -> jordan(o.jordan)
                is Outcome.OdeSystemSolved -> system(o.system)
                is Outcome.BoundaryValueSolved -> boundary(o.problem)
                is Outcome.SeriesSolved -> series(o.solution)
                else -> return null
            }
            return out
        }

        fun svd(s: MatrixForms.Svd) {
            val a = matrix() ?: return
            step("A = " + l(a), StepKey.START)
            val g = LinAlg.times(LinAlg.transpose(a), a) as Expr.Mat
            step("A^{T}A = " + l(g), StepKey.SVD_GRAM)
            val shown = s.values.indices.joinToString(",\\ ") { k -> "\\sigma_{${k + 1}} " + eq(s.values[k]) }
            step(shown, StepKey.SVD_VALUES)
            step("V = " + l(s.v), StepKey.SVD_V)
            step("U = " + l(s.u), StepKey.SVD_U)
            step("A = U\\Sigma V^{T},\\quad \\Sigma = " + l(s.sigma), StepKey.SVD_CHECK)
        }

        /** "= value", or "≈ value" where only a decimal is known. */
        fun eq(e: Expr) = v(e).let { if (it.startsWith("\\approx")) it else "= $it" }

        fun jordan(j: MatrixForms.Jordan) {
            val a = matrix() ?: return
            step("A = " + l(a), StepKey.START)
            step("\\det(\\lambda I - A) = " + l(LinAlg.characteristic(a)) + " = 0", StepKey.CHARACTERISTIC)
            val values = j.blocks.groupBy { it.value }
            step(values.entries.joinToString(",\\ ") { (lambda, bs) ->
                val m = bs.sumOf { it.size }
                "\\lambda = " + v(lambda) + if (m > 1) "\\ (${m}\\times)" else ""
            }, StepKey.EIGENVALUES)
            var at = 0
            val starts = j.blocks.map { b -> at.also { at += b.size } }
            for ((lambda, bs) in values) {
                val m = bs.sumOf { it.size }
                if (m < 2) continue
                val shifted = Expr.Mat(a.rows.mapIndexed { i, r -> r.mapIndexed { k, x -> if (i == k) Calculus.tidy(x - lambda) else x } })
                val ranks = ArrayList<String>()
                var power = shifted
                for (k in 1..bs.maxOf { it.size }) {
                    ranks += "\\operatorname{rank}\\left(${shift(lambda)}\\right)" + (if (k > 1) "^{$k}" else "") + " = " + LinAlg.rank(power)
                    power = LinAlg.times(power, shifted) as Expr.Mat
                }
                step(ranks.joinToString(",\\ "), StepKey.JORDAN_RANKS, "\\lambda = " + v(lambda))
                for ((k, b) in j.blocks.withIndex()) {
                    if (b.value != lambda || b.size < 2) continue
                    val vectors = (0 until b.size).map { q -> Expr.Mat(j.p.rows.map { listOf(it[starts[k] + q]) }) }
                    step(vectors.indices.joinToString(",\\ ") { q -> "\\vec{v}_{${q + 1}} = " + l(vectors[q]) }, StepKey.JORDAN_CHAIN, "\\lambda = " + v(lambda))
                }
            }
            step("A = PJP^{-1},\\quad J = " + l(j.j) + ",\\quad P = " + l(j.p), StepKey.JORDAN_FORM)
        }

        /** A − λI as it reads best: A − I, A + 2I, A. */
        fun shift(lambda: Expr): String = when {
            isZero(lambda) -> "A"
            isOne(lambda) -> "A - I"
            rational(lambda)?.let { it.signum < 0 } == true -> "A + " + (if (lambda == Expr.MINUS_ONE) "" else l(neg(lambda))) + "I"
            else -> "A - " + p(lambda) + "I"
        }

        /** A number in brackets where it is a sum or negative. */
        fun p(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" + ") || it.contains(" - ")) "\\left($it\\right)" else it }

        fun components(unknowns: List<String>, values: List<Expr>) = unknowns.indices.joinToString(",\\ ") { "${name(unknowns[it])} = " + l(values[it]) }

        fun system(s: OdeSystems.Solution) {
            step(latex, StepKey.START)
            val vector = "\\vec{x}"
            val forced = s.forcing.any { !LinAlg.isNull(it) }
            step("$vector' = A$vector" + (if (forced) " + \\vec{b},\\quad \\vec{b} = " + l(LinAlg.column(s.forcing)) else "") + ",\\quad A = " + l(s.matrix),
                StepKey.SYS_MATRIX)
            step("\\det(\\lambda I - A) = " + l(LinAlg.characteristic(s.matrix)) + " = 0", StepKey.CHARACTERISTIC)
            step(s.eigen.joinToString(",\\ ") { "\\lambda = " + v(it.value) + if (it.multiplicity > 1) "\\ (${it.multiplicity}\\times)" else "" }, StepKey.EIGENVALUES)
            for (e in s.eigen) for (vec in e.vectors) step("\\vec{v} = " + l(vec), StepKey.EIGENVECTOR, "\\lambda = " + v(e.value))
            step(components(s.unknowns, s.general), StepKey.SYS_GENERAL)
            val values = s.values ?: return
            val particular = s.particular ?: return
            step(s.constants.joinToString(",\\ ") { name(it) + " = " + v(values.getValue(it)) }, StepKey.SYS_CONSTANTS)
            step(components(s.unknowns, particular), StepKey.RESULT)
        }

        fun boundary(b: BoundaryValue.Problem) {
            step(latex, StepKey.START)
            val y = name(b.y)
            step("$y = " + l(b.general.explicit ?: return), StepKey.BVP_GENERAL)
            step(b.system.joinToString(",\\ ") { l(it) + " = 0" }, StepKey.BVP_SYSTEM)
            when (b.kind) {
                BoundaryValue.Kind.UNIQUE -> step("$y = " + l(b.solution!!), StepKey.BVP_UNIQUE)
                BoundaryValue.Kind.NONE -> step("$y \\in \\emptyset", StepKey.BVP_NONE)
                BoundaryValue.Kind.INFINITE -> step("$y = " + l(b.solution!!), StepKey.BVP_FAMILY, b.free.joinToString(",\\ ") { name(it) } + " \\in \\mathbb{R}")
            }
        }

        fun series(s: SeriesOde.Solution) {
            step(latex, StepKey.START)
            val h = s.seriesIn
            val power = if (h is Expr.Sym) l(h) + "^{j}" else "\\left(" + l(h) + "\\right)^{j}"
            step("${name(s.y)} = \\sum_{j=0}^{\\infty} a_{j}$power", StepKey.SERIES_ANSATZ)
            s.recurrence?.let { (lhs, rhs) -> step(l(lhs) + " = " + l(rhs), StepKey.SERIES_RECURRENCE) }
            val coefficients = (s.n..s.order).map { k -> k to (s.terms.firstOrNull { it.second == k }?.first ?: Expr.ZERO) }
            step(coefficients.joinToString(",\\ ") { (k, a) -> "a_{$k} = " + l(a) }, StepKey.SERIES_COEFFICIENTS)
            step("${name(s.y)} = " + c.latexResult(Outcome.Expression(Expr.ZERO, series = s.terms, seriesIn = h, remainder = pow(h, num(s.order + 1L)))),
                StepKey.RESULT)
        }
    }
}
