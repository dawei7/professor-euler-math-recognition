package com.professoreuler.mathcalc

/**
 * Worked steps for the functions of machine learning ([Learning], [Decisions]) where a formula is one call of them: softmax of a
 * vector, the information measures, value iteration, policy evaluation and gradient descent. Each step says what was done and
 * shows the formula after it, from the call's arguments and its outcome; every number in them is exact (the iteration's own
 * decimals only in the last step of gradient descent, said with ≈).
 */
internal object LearningSteps {
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

        /** "= value", or "≈ value" where only a decimal is known. */
        fun eqv(e: Expr) = v(e).let { if (it.startsWith("\\approx")) it else "= $it" }

        /** A formula in brackets where it is a sum or negative. */
        fun p(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" + ") || it.contains(" - ")) "\\left($it\\right)" else it }

        fun vector(entries: List<Expr>) = "\\left(" + entries.joinToString(",\\ ") { l(it) } + "\\right)"

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        fun build(): List<Step>? {
            val tokens = MathCalc.TOKEN.findAll(latex).map { it.value }.toList()
            val (name, raw) = Learning.callOf(tokens, Learning.FUNCTIONS.keys + Learning.COMMANDS.keys) ?: return null
            if (raw.any { it.isEmpty() }) return null
            val flags = Flags(assumptions)
            // Gradient descent reads its formula with its letters; the others' arguments are numbers, vectors and matrices.
            if (name == "gradientdescent") {
                descent((c.outcome as? Outcome.Descent) ?: return null, raw.map { Learning.argument(it, flags) })
                return out
            }
            val args = raw.map { Learning.argument(it, flags) }
            when (name) {
                "softmax" -> softmax(args, c.outcome as? Outcome.Expression ?: return null)
                "entropy", "crossentropy", "kldivergence", "mutualinformation" -> information(name, args, c.outcome as? Outcome.Value ?: return null)
                "valueiteration" -> valueIteration(args, c.outcome as? Outcome.DecisionProcess ?: return null)
                "policyevaluation" -> policyEvaluation(args, c.outcome as? Outcome.Expression ?: return null)
                else -> return null
            }
            return out
        }

        /** The entries of a vector argument, or null. */
        fun entriesOf(e: Expr): List<Expr>? = (e as? Expr.Mat)?.let { m ->
            when {
                m.columns == 1 -> m.rows.map { it[0] }
                m.size == 1 -> m.rows[0]
                else -> null
            }
        }

        fun softmax(args: List<Expr>, o: Outcome.Expression) {
            val z = entriesOf(args[0]) ?: return
            if (z.any { it.symbols().isNotEmpty() }) return
            val result = entriesOf(o.result) ?: return
            val exps = z.map { Calculus.tidy(exp(it)) }
            val sum = Calculus.tidy(add(exps))
            step("z = " + vector(z), StepKey.START)
            step("e^{z} = " + vector(exps), StepKey.SOFTMAX_EXP)
            step("\\operatorname{softmax}(z)_i = \\frac{e^{z_i}}{${l(sum)}}", StepKey.SOFTMAX_DIVIDE)
            step("\\operatorname{softmax}(z) = " + vector(result), StepKey.RESULT)
            step("\\sum_i \\operatorname{softmax}(z)_i = 1", StepKey.SOFTMAX_CHECK)
        }

        /** log to the base as it is written in a step: \ln, \log_{2}. */
        fun log(b: Expr?) = if (b == null || b == Expr.E) "\\ln" else "\\log_{${l(b)}}"

        fun term(weight: Expr, of: Expr, b: Expr?) = "${p(weight)}\\,${log(b)}${p(of).let { if (it.startsWith("\\left(")) it else "\\left($it\\right)" }}"

        fun information(name: String, args: List<Expr>, o: Outcome.Value) {
            if (o.steps.isNotEmpty() || o.others.isNotEmpty()) return
            val value = Learning.call(name, args)
            val all = args.map { a -> (a as? Expr.Mat)?.rows?.flatten() }
            if (all.any { it != null && it.any { e -> e.symbols().isNotEmpty() } }) return
            fun inverse(e: Expr) = Calculus.tidy(pow(e, Expr.MINUS_ONE))
            fun nonzero(e: Expr) = !(e is Expr.Num && e.value.signum == 0)
            when (name) {
                "entropy" -> {
                    val pv = all[0] ?: return
                    val b = args.getOrNull(1)
                    step("p = " + vector(pv), StepKey.START)
                    step("H(p) = -\\sum_i p_i ${log(b)} p_i = " + pv.filter(::nonzero).joinToString(" + ") { term(it, inverse(it), b) },
                        StepKey.ENTROPY_SUM)
                    step("H(p) " + eqv(value), StepKey.RESULT)
                }
                "crossentropy", "kldivergence" -> {
                    val pv = all[0] ?: return
                    val qv = all[1] ?: return
                    val b = args.getOrNull(2)
                    step("p = " + vector(pv) + ",\\ q = " + vector(qv), StepKey.START)
                    val h = Learning.call("entropy", listOfNotNull(args[0], b))
                    if (name == "crossentropy") {
                        step("H(p, q) = -\\sum_i p_i ${log(b)} q_i = " +
                            pv.indices.filter { nonzero(pv[it]) }.joinToString(" + ") { term(pv[it], inverse(qv[it]), b) }, StepKey.CROSS_ENTROPY_SUM)
                        val d = Learning.call("kldivergence", listOfNotNull(args[0], args[1], b))
                        step("H(p, q) = H(p) + D(p \\| q) = ${p(h)} + ${p(d)}", StepKey.INFO_CHECK)
                        step("H(p, q) " + eqv(value), StepKey.RESULT)
                    } else {
                        step("D(p \\| q) = \\sum_i p_i ${log(b)} \\frac{p_i}{q_i} = " +
                            pv.indices.filter { nonzero(pv[it]) }.joinToString(" + ") { term(pv[it], Calculus.tidy(mul(pv[it], inverse(qv[it]))), b) },
                            StepKey.KL_SUM)
                        val hq = Learning.call("crossentropy", listOfNotNull(args[0], args[1], b))
                        step("D(p \\| q) = H(p, q) - H(p) = ${p(hq)} - ${p(h)}", StepKey.INFO_CHECK)
                        step("D(p \\| q) " + eqv(value), StepKey.RESULT)
                    }
                }
                "mutualinformation" -> {
                    val m = args[0] as? Expr.Mat ?: return
                    val b = args.getOrNull(1)
                    val rows = m.rows.map { Calculus.tidy(add(it)) }
                    val columns = (0 until m.columns).map { j -> Calculus.tidy(add(m.rows.map { it[j] })) }
                    step("p(x, y) = " + l(m), StepKey.START)
                    step("p(x) = " + vector(rows) + ",\\ p(y) = " + vector(columns), StepKey.MI_MARGINALS)
                    val terms = ArrayList<String>()
                    for (i in m.rows.indices) for (j in 0 until m.columns) {
                        val pij = m.rows[i][j]
                        if (nonzero(pij)) terms += term(pij, Calculus.tidy(mul(pij, inverse(Calculus.tidy(mul(rows[i], columns[j]))))), b)
                    }
                    step("I(X; Y) = \\sum_{x, y} p(x, y)\\,${log(b)}\\frac{p(x, y)}{p(x)\\,p(y)} = " + terms.joinToString(" + "), StepKey.MI_SUM)
                    val hx = Learning.call("entropy", listOfNotNull(LinAlg.column(rows), b))
                    val hy = Learning.call("entropy", listOfNotNull(LinAlg.column(columns), b))
                    val hxy = Learning.call("entropy", listOfNotNull(m, b))
                    step("I(X; Y) = H(X) + H(Y) - H(X, Y) = ${p(hx)} + ${p(hy)} - ${p(hxy)}", StepKey.INFO_CHECK)
                    step("I(X; Y) " + eqv(value), StepKey.RESULT)
                }
            }
        }

        fun rationals(values: List<Rational>) = "\\left(" + values.joinToString(",\\ ") { l(num(it)) } + "\\right)"

        fun matrix(rows: List<List<Rational>>) = l(LinAlg.matrix(rows.map { r -> r.map { num(it) } }))

        fun valueIteration(args: List<Expr>, o: Outcome.DecisionProcess) {
            val pr = Decisions.process(args)
            step((1..pr.m).joinToString(",\\ ") { "P_{$it} = " + matrix(pr.p[it - 1]) } + ",\\ R = " + matrix(pr.r) + ",\\ \\gamma = " + l(num(pr.gamma)),
                StepKey.START)
            step("v_{k+1}(s) = \\max_a \\left[r(s, a) + \\gamma \\sum_{s'} p(s' \\mid s, a)\\, v_k(s')\\right]", StepKey.MDP_BELLMAN)
            // The first sweeps with fractions, as by hand: v₁ = maxₐ r(s, a), v₂, v₃.
            var vk = List(pr.n) { Rational.ZERO }
            val sweeps = ArrayList<String>()
            sweeps += "v_{0} = " + rationals(vk)
            for (k in 1..3) {
                vk = Decisions.q(pr, vk).map { row -> row.filterNotNull().max() }
                sweeps += "v_{$k} = " + rationals(vk)
            }
            step(sweeps.joinToString(",\\ "), StepKey.MDP_SWEEPS)
            val actions = o.policy.map { it.first() - 1 }
            step("\\pi = " + "\\left(" + actions.joinToString(",\\ ") { "a_{${it + 1}}" } + "\\right):\\ v_\\pi = (I - \\gamma P_\\pi)^{-1} r_\\pi = " +
                vector(o.values), StepKey.MDP_EVALUATE)
            step("q^*(s, a) = " + l(LinAlg.matrix(o.actions.map { r -> r.map { it ?: Expr.Sym("—") } })) +
                ",\\ v^*(s) = \\max_a q^*(s, a)", StepKey.MDP_OPTIMAL)
            step("v^* = " + vector(o.values) + ",\\ \\pi^* = \\left(" +
                o.policy.joinToString(",\\ ") { acts -> acts.joinToString(" / ") { "a_{$it}" } } + "\\right)", StepKey.RESULT)
        }

        fun policyEvaluation(args: List<Expr>, o: Outcome.Expression) {
            val (pPi, rPi) = Decisions.policyParts(args) ?: return
            val pr = Decisions.process(args.dropLast(1))
            step((1..pr.m).joinToString(",\\ ") { "P_{$it} = " + matrix(pr.p[it - 1]) } + ",\\ R = " + matrix(pr.r) + ",\\ \\gamma = " +
                l(num(pr.gamma)) + ",\\ \\pi = " + l(args.last()), StepKey.START)
            step("P_\\pi = " + matrix(pPi) + ",\\ r_\\pi = " + rationals(rPi), StepKey.MDP_POLICY_PARTS)
            val system = List(pr.n) { s -> List(pr.n) { t -> (if (s == t) Rational.ONE else Rational.ZERO) - pr.gamma * pPi[s][t] } }
            step("(I - \\gamma P_\\pi)\\, v_\\pi = r_\\pi:\\ " + matrix(system) + " v_\\pi = " + rationals(rPi), StepKey.MDP_LINEAR)
            step("v_\\pi = " + vector(entriesOf(o.result) ?: return), StepKey.RESULT)
        }

        fun descent(o: Outcome.Descent, args: List<Expr>) {
            val f = args[0]
            val letters = o.letters
            val xs = letters.map { Expr.Sym(it) }
            val start = if (letters.size == 1) listOf(args[1]) else entriesOf(args[1]) ?: return
            val eta = args[2]
            val gradient = xs.map { Calculus.tidy(Calculus.derivative(f, it)) }
            fun point(values: List<Expr>) = if (values.size == 1) l(values[0]) else vector(values)
            step("f = " + l(f) + ",\\ x_0 = " + point(start) + ",\\ \\eta = " + l(eta), StepKey.START)
            step("\\nabla f = " + point(gradient), StepKey.GD_GRADIENT)
            // The first step exactly: x₁ = x₀ − η·∇f(x₀).
            val at = gradient.map { g -> Calculus.tidy(letters.indices.fold(g) { acc, k -> acc.substitute(xs[k], start[k]) }) }
            if (at.any { it.symbols().isNotEmpty() }) return
            val next = start.indices.map { Calculus.tidy(add(start[it], neg(mul(eta, at[it])))) }
            step("x_1 = x_0 - \\eta \\nabla f(x_0) = " + point(start) + " - " + p(eta) + "\\cdot " + (if (at.size == 1) p(at[0]) else point(at)) + " = " + point(next), StepKey.GD_STEP)
            val last = o.table.rows.last()
            val k = last[0].toLong()
            val reached = o.point
            if (reached != null) {
                val shown = reached.map { Format.decimal(java.math.BigDecimal(it), c, latex = true) }
                step("x_{$k} \\approx " + (if (shown.size == 1) shown[0] else "\\left(" + shown.joinToString(",\\ ") + "\\right)"), StepKey.RESULT)
            }
            o.minimum?.let { m ->
                step("\\nabla f = 0:\\ " + point(letters.map { m.at.getValue(it) }) + ",\\ f " + eqv(m.value), StepKey.GD_MINIMUM)
            }
        }
    }
}
