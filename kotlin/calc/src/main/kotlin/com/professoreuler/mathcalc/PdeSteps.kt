package com.professoreuler.mathcalc

/**
 * Worked steps for partial differential equations, from what their outcomes hold ([Pde]): the type from the discriminant and
 * the change to the canonical form; the separation of variables, its eigenvalue problem, the other factor, the superposition,
 * the Fourier coefficients and the check of the initial values. Each step's formula is in its LaTeX; the sentences take no
 * formulas of their own.
 */
internal object PdeSteps {
    fun of(c: Calculation): List<Step>? = try {
        when (val o = c.outcome) {
            is Outcome.PdeType -> Builder(c).type(o.classification)
            is Outcome.PdeSeries -> Builder(c).series(o.solution)
            else -> null
        }?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private class Builder(val c: Calculation) {
        val out = ArrayList<Step>()

        fun l(e: Expr) = Format.expr(e, c, latex = true)

        fun step(shown: String, key: StepKey) {
            out += Step(Steps.tidy(shown), StepText(key))
        }

        /** A coefficient and what it multiplies, as a learner writes it: 4u_{xy}, -u, x\,u_{yy}, (x + 1)u_{x}. */
        fun term(co: Expr, what: Expr): String {
            val w = l(what)
            return when {
                isOne(co) -> w
                LinAlg.isNull(tidy(co + Expr.ONE)) -> "-$w"
                co is Expr.Num -> l(co) + w
                co is Expr.Add -> "\\left(" + l(co) + "\\right)" + w
                else -> l(co) + "\\," + w
            }
        }

        /** A formula in brackets where it is a sum or negative: what is subtracted. */
        fun p(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" + ") || it.contains(" - ")) "\\left($it\\right)" else it }

        /** Signed pieces joined: u_{xx} + 4u_{xy} - u. */
        fun signed(pieces: List<String>): String {
            if (pieces.isEmpty()) return "0"
            val b = StringBuilder(pieces[0])
            for (s in pieces.drop(1)) if (s.startsWith("-")) b.append(" - ").append(s.substring(1)) else b.append(" + ").append(s)
            return b.toString()
        }

        /** Terms in the order given, each with its sign. */
        fun joined(terms: List<Expr>): String = signed(terms.map { l(it) })

        fun type(k: Pde.Classification): List<Step> {
            val q = k.coefficients
            val m = { v: String -> Pde.marker(v) }
            val terms = listOf(q.a to m(q.x + q.x), q.b to m(q.x + q.y), q.c to m(q.y + q.y), q.d to m(q.x), q.e to m(q.y), q.f to Expr.Sym(Pde.U))
                .filter { !isZero(it.first) }.map { (co, s) -> term(co, s) }
            step(signed(terms) + " = " + l(q.g), StepKey.START)
            step("a = ${l(q.a)},\\ b = ${l(q.b)},\\ c = ${l(q.c)}", StepKey.PDE_COEFFICIENTS)
            step("b^{2} - 4ac = " + l(k.discriminant), StepKey.PDE_DISCRIMINANT)
            val sign = mapOf(Rel.GT to ">", Rel.EQ to "=", Rel.LT to "<")
            val key = mapOf(Pde.Type.HYPERBOLIC to StepKey.PDE_HYPERBOLIC, Pde.Type.PARABOLIC to StepKey.PDE_PARABOLIC,
                Pde.Type.ELLIPTIC to StepKey.PDE_ELLIPTIC)
            if (k.type != null) {
                val rel = when (k.type) { Pde.Type.HYPERBOLIC -> ">"; Pde.Type.PARABOLIC -> "="; Pde.Type.ELLIPTIC -> "<" }
                step(l(k.discriminant) + " $rel 0", key.getValue(k.type))
            } else {
                for (r in k.regions) {
                    val where = when {
                        r.set != null -> "\\iff " + c.latex(r.set)
                        r.points.isNotEmpty() -> "\\iff " + Format.name(r.letter!!, latex = true) + " = " + r.points.joinToString(",\\ ") { l(it) }
                        else -> ""
                    }
                    step(l(k.discriminant) + " ${sign.getValue(r.relation)} 0 " + where, key.getValue(r.type))
                }
            }
            val f = k.canonical ?: return out
            if (f.characteristic.isNotEmpty()) {
                val lambda = Expr.Sym("\\lambda")
                val equation = tidy(add(mul(q.a, pow(lambda, Expr.TWO)), mul(neg(q.b), lambda), q.c))
                step(l(equation) + " = 0:\\ \\lambda = " + f.characteristic.joinToString(",\\ ") { l(it) }, StepKey.PDE_CHARACTERISTIC)
            }
            step("\\xi = ${l(f.xi)},\\ \\eta = ${l(f.eta)}", StepKey.PDE_NEW_VARIABLES)
            step(joined(f.terms) + " = " + l(f.rhs), StepKey.PDE_CANONICAL)
            return out
        }

        fun series(s: Pde.Separated): List<Step> {
            val t = Expr.Sym(s.t)
            val equation = when (s.kind) {
                Pde.Kind.HEAT -> "u_{t} = " + term(s.constant!!, Pde.marker("xx"))
                Pde.Kind.WAVE -> "u_{tt} = " + term(tidy(pow(s.constant!!, Expr.TWO)), Pde.marker("xx"))
                Pde.Kind.LAPLACE -> "u_{xx} + u_{yy} = 0"
            }
            step(equation + ",\\ " + s.given.joinToString(",\\ ") { condition(s, it) }, StepKey.START)
            s.steady?.let { v -> step("v(x) = " + l(v) + ",\\ u = v + w,\\ w(0, t) = w(" + l(s.length) + ", t) = 0", StepKey.PDE_STEADY_STATE) }
            // The initial values are the steady state itself: nothing to separate.
            if (s.coefficients.isEmpty()) {
                step("u = " + (s.sum?.let { joined(s.terms) } ?: "0"), StepKey.RESULT)
                return out
            }
            val e = s.eigen.first()
            fun over(k: Expr) = if (isOne(k)) "T" else l(k) + "\\,T"
            val separated = when (s.kind) {
                Pde.Kind.HEAT -> "u = X(x)\\,T(t):\\ \\frac{T'}{${over(s.constant!!)}} = \\frac{X''}{X} = -\\lambda"
                Pde.Kind.WAVE -> "u = X(x)\\,T(t):\\ \\frac{T''}{${over(tidy(pow(s.constant!!, Expr.TWO)))}} = \\frac{X''}{X} = -\\lambda"
                Pde.Kind.LAPLACE -> if (e.variable == s.x) "u = X(x)\\,Y(y):\\ \\frac{X''}{X} = -\\frac{Y''}{Y} = -\\lambda"
                    else "u = X(x)\\,Y(y):\\ \\frac{Y''}{Y} = -\\frac{X''}{X} = -\\lambda"
            }
            step(separated, StepKey.PDE_SEPARATE)
            for (ei in s.eigen) {
                val name = if (ei.variable == s.x) "X" else "Y"
                fun end(at: String, end: Pde.End) = if (end == Pde.End.NEUMANN) "$name'($at)" else "$name($at)"
                val ends = end("0", ei.left) + " = " + end(l(ei.length), ei.right) + " = 0"
                val from = if (ei.constant) "n = 0, 1, 2, \\ldots" else "n = 1, 2, 3, \\ldots"
                step("$name'' + \\lambda $name = 0,\\ $ends:\\ \\lambda_{n} = ${l(ei.lambda)},\\ ${name}_{n} = ${l(ei.function)},\\ $from",
                    StepKey.PDE_EIGENVALUES)
            }
            // The other factor of each family: the n-th term divided by Xₙ.
            val factors = when (s.kind) {
                Pde.Kind.HEAT -> "T_{n} = " + l(exp(neg(mul(s.constant!!, e.lambda, t))))
                Pde.Kind.WAVE -> {
                    val w = mul(s.constant!!, e.frequency, t)
                    "T_{n} = a_{n}\\cos\\left(${l(w)}\\right) + b_{n}\\sin\\left(${l(w)}\\right)"
                }
                // Laplace: the factor across the rectangle of each side's series, sinh(μₙy)/sinh(μₙb).
                Pde.Kind.LAPLACE -> s.coefficients.zip(s.eigen).joinToString(",\\ ") { (k, ei) ->
                    (if (ei.variable == s.x) "Y" else "X") + "_{n} = " + l(tidy(k.factor / ei.function))
                }
            }
            step(factors, StepKey.PDE_OTHER_FACTOR)
            step("u = " + l(s.series), StepKey.PDE_SUPERPOSE)
            for (k in s.coefficients) {
                val extra = k.exceptions.joinToString("") { (m, v) -> ",\\ ${k.name}_{$m} = " + l(v) } +
                    (k.zero?.let { ",\\ ${k.name}_{0} = " + l(it) } ?: "")
                step("${k.name}_{n} = " + l(k.weight) + l(k.integral) + " = " + l(k.formula) + extra, StepKey.PDE_FOURIER_COEFFICIENTS)
            }
            for (k in s.checks) {
                val cond = condition(s, Pde.Given(k.variable, k.point, k.value, k.derivative != null))
                if (k.exact) {
                    step(cond, StepKey.PDE_FINITE)
                } else {
                    val v = Format.name(if (k.variable == s.x) s.t else s.x, latex = true)
                    val letters = if (k.letters.isEmpty()) "" else ",\\ " + k.letters.joinToString(" = ") { Format.name(it, latex = true) } +
                        " = " + k.samples.joinToString(",\\ ") { c.latexValue(it) }
                    step("\\left|S_{${k.terms}} - " + p(k.value) + "\\right| < ${k.digit} \\cdot 10^{${k.exponent}},\\ $v = " +
                        k.points.joinToString(",\\ ") { l(it) } + letters, StepKey.PDE_PARTIAL_SUMS)
                }
            }
            val shown = s.sum?.let { joined(s.terms) } ?: (joined(s.terms) + " + \\ldots")
            step("u = $shown", StepKey.RESULT)
            return out
        }

        /** u(0, t) = 0, u_x(π, t) = 0, u_t(x, 0) = g(x) as the problem states it. */
        fun condition(s: Pde.Separated, g: Pde.Given): String {
            val name = if (g.derivative) "u_{${g.variable}}" else "u"
            val args = if (g.variable == s.x) l(g.point) + ", " + s.t else s.x + ", " + l(g.point)
            return "$name($args) = " + l(g.value)
        }

        private fun tidy(e: Expr) = Calculus.tidy(e)
    }
}
