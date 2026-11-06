package com.professoreuler.mathcalc

/**
 * Worked steps for the statistics commands, from what their results hold: a test's hypotheses, sample, statistic, degrees of
 * freedom, p-value and decision; an interval's critical value, margin and ends; a fit's linearization or normal equations, curve and
 * R²; a Markov chain's equations, distribution and check. The last step is the answer the card shows.
 */
internal object StatSteps {
    fun of(c: Calculation, latex: String): List<Step>? = try {
        Builder(c, latex).build()?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private class Builder(val c: Calculation, val latex: String) {
        val out = ArrayList<Step>()

        fun l(e: Expr) = Format.expr(e, c, latex = true)
        fun v(e: Expr) = c.latexValue(e)

        /** "= value", or "≈ value" where only a decimal is known. */
        fun eqv(e: Expr) = v(e).let { if (it.startsWith("\\approx")) it else "= $it" }

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        fun build(): List<Step>? {
            val o = c.outcome
            when {
                o is Outcome.Statistical -> statistical(o.result)
                o is Outcome.Expression && c.mode == Mode.STATIONARY -> stationary(o.result)
                o is Outcome.Expression && c.mode == Mode.MARKOV -> markov(o.result)
                else -> return null
            }
            return out
        }

        fun statistical(r: StatResult) {
            when (r.mode) {
                Mode.Z_TEST, Mode.T_TEST, Mode.WELCH_TEST -> meanTest(r)
                Mode.CHI_SQUARE_FIT, Mode.CHI_SQUARE_INDEPENDENCE -> chiSquare(r)
                Mode.Z_INTERVAL, Mode.T_INTERVAL -> meanInterval(r)
                Mode.PROPORTION_INTERVAL -> proportionInterval(r)
                else -> fit(r)
            }
        }

        fun row(q: Quantity, group: Int = 0) = r.value(q, group) ?: throw CalcException(Failure.UNREADABLE)
        lateinit var r: StatResult

        fun sample(group: Int, sd: Boolean): String {
            val i = if (group > 0) "_{$group}" else ""
            val parts = mutableListOf("n$i = ${l(row(Quantity.N, group))}", "\\bar{x}$i ${eqv(row(Quantity.MEAN, group))}")
            if (sd) parts += "s$i ${eqv(row(Quantity.SD, group))}"
            return parts.joinToString(",\\ ")
        }

        fun relation(a: Alternative?) = when (a) {
            Alternative.LESS -> "<"
            Alternative.GREATER -> ">"
            else -> "\\ne"
        }

        fun decision(result: StatResult) {
            val p = v(row(Quantity.P_VALUE))
            val alpha = v(row(Quantity.ALPHA))
            when (result.reject) {
                true -> step((if (p.startsWith("\\approx")) "p $p" else "p = $p") + " < $alpha", StepKey.TEST_REJECT, alpha)
                false -> step((if (p.startsWith("\\approx")) "p $p" else "p = $p") + " \\ge $alpha", StepKey.TEST_KEEP, alpha)
                null -> {}
            }
        }

        fun meanTest(result: StatResult) {
            r = result
            val welch = result.mode == Mode.WELCH_TEST
            val mu0 = result.nullValue?.let { l(it) } ?: "0"
            val rel = relation(result.alternative)
            step(if (welch) "H_{0}\\colon \\mu_{1} = \\mu_{2},\\quad H_{1}\\colon \\mu_{1} $rel \\mu_{2}" else "H_{0}\\colon \\mu = $mu0,\\quad H_{1}\\colon \\mu $rel $mu0",
                StepKey.TEST_HYPOTHESES)
            if (welch) step(sample(1, true) + ";\\quad " + sample(2, true), StepKey.TEST_SAMPLE)
            else step(sample(0, result.mode == Mode.T_TEST), StepKey.TEST_SAMPLE)
            val statistic = row(Quantity.STATISTIC)
            when (result.mode) {
                Mode.Z_TEST -> step("z = \\frac{\\bar{x} - \\mu_{0}}{\\sigma/\\sqrt{n}} = \\frac{${l(row(Quantity.MEAN))} - $mu0}{${l(row(Quantity.STANDARD_ERROR))}} ${eqv(statistic)}",
                    StepKey.TEST_STATISTIC)
                Mode.T_TEST -> step("t = \\frac{\\bar{x} - \\mu_{0}}{s/\\sqrt{n}} ${eqv(statistic)}", StepKey.TEST_STATISTIC)
                else -> step("t = \\frac{\\bar{x}_{1} - \\bar{x}_{2}}{\\sqrt{s_{1}^{2}/n_{1} + s_{2}^{2}/n_{2}}} ${eqv(statistic)}", StepKey.TEST_STATISTIC)
            }
            if (result.mode == Mode.T_TEST) step("\\nu = n - 1 = ${l(row(Quantity.DF))}", StepKey.TEST_DF)
            if (welch) step("\\nu = \\frac{\\left(s_{1}^{2}/n_{1} + s_{2}^{2}/n_{2}\\right)^{2}}{\\frac{(s_{1}^{2}/n_{1})^{2}}{n_{1} - 1} + \\frac{(s_{2}^{2}/n_{2})^{2}}{n_{2} - 1}} ${eqv(row(Quantity.DF))}",
                StepKey.TEST_DF)
            val name = if (result.mode == Mode.Z_TEST) "Z" else "T"
            val s = if (result.mode == Mode.Z_TEST) "z" else "t"
            val tail = when (result.alternative) {
                Alternative.LESS -> "P($name \\le $s)"
                Alternative.GREATER -> "P($name \\ge $s)"
                else -> "2\\,P($name \\ge |$s|)"
            }
            step("p = $tail ${eqv(row(Quantity.P_VALUE))}", StepKey.TEST_P_VALUE)
            decision(result)
        }

        fun chiSquare(result: StatResult) {
            r = result
            val e = result.expected ?: return
            step("e = " + l(if (result.mode == Mode.CHI_SQUARE_FIT) LinAlg.transpose(e) else e), StepKey.CHI_EXPECTED)
            step("\\chi^{2} = \\sum \\frac{(o - e)^{2}}{e} ${eqv(row(Quantity.STATISTIC))}", StepKey.TEST_STATISTIC)
            step(if (result.mode == Mode.CHI_SQUARE_FIT) "\\nu = k - 1 = ${l(row(Quantity.DF))}" else "\\nu = (r - 1)(c - 1) = ${l(row(Quantity.DF))}", StepKey.TEST_DF)
            step("p = P(\\chi^{2}_{\\nu} \\ge ${l(row(Quantity.STATISTIC))}) ${eqv(row(Quantity.P_VALUE))}", StepKey.TEST_P_VALUE)
            decision(result)
        }

        fun level() = v(row(Quantity.LEVEL))

        fun ends(lower: Expr, upper: Expr) = "\\left[" + v(lower).removePrefix("\\approx ") + ",\\ " + v(upper).removePrefix("\\approx ") + "\\right]"

        fun meanInterval(result: StatResult) {
            r = result
            step(sample(0, result.mode == Mode.T_INTERVAL), StepKey.TEST_SAMPLE)
            val share = "\\frac{1 + ${level()}}{2}"
            if (result.mode == Mode.Z_INTERVAL) {
                step("z^{*} = \\Phi^{-1}\\left($share\\right) ${eqv(row(Quantity.CRITICAL))}", StepKey.INTERVAL_CRITICAL, level())
                step("E = z^{*}\\cdot\\frac{\\sigma}{\\sqrt{n}} ${eqv(row(Quantity.MARGIN))}", StepKey.INTERVAL_MARGIN)
            } else {
                step("t^{*} = t_{${l(row(Quantity.DF))}}^{-1}\\left($share\\right) ${eqv(row(Quantity.CRITICAL))}", StepKey.INTERVAL_CRITICAL, level())
                step("E = t^{*}\\cdot\\frac{s}{\\sqrt{n}} ${eqv(row(Quantity.MARGIN))}", StepKey.INTERVAL_MARGIN)
            }
            step("\\bar{x} \\pm E = " + ends(row(Quantity.LOWER), row(Quantity.UPPER)), StepKey.RESULT)
        }

        fun proportionInterval(result: StatResult) {
            r = result
            step("\\hat{p} = \\frac{${l(row(Quantity.SUCCESSES))}}{${l(row(Quantity.N))}} ${eqv(row(Quantity.PROPORTION))}", StepKey.TEST_SAMPLE)
            step("z^{*} = \\Phi^{-1}\\left(\\frac{1 + ${level()}}{2}\\right) ${eqv(row(Quantity.CRITICAL))}", StepKey.INTERVAL_CRITICAL, level())
            step("E = z^{*}\\sqrt{\\frac{\\hat{p}(1 - \\hat{p})}{n}} ${eqv(row(Quantity.MARGIN))}", StepKey.INTERVAL_MARGIN)
            step("\\hat{p} \\pm E = " + ends(row(Quantity.LOWER), row(Quantity.UPPER)), StepKey.RESULT)
            step(ends(row(Quantity.WILSON_LOWER), row(Quantity.WILSON_UPPER)), StepKey.INTERVAL_WILSON)
        }

        fun coefficient(symbol: String) = r.rows.first { it.quantity == Quantity.COEFFICIENT && it.symbol == symbol }.value

        fun fit(result: StatResult) {
            r = result
            val model = result.model ?: return
            when (result.mode) {
                Mode.FIT_POLYNOMIAL -> step("X^{T}X\\,\\vec{a} = X^{T}\\vec{y}", StepKey.FIT_NORMAL_EQUATIONS)
                Mode.FIT_EXPONENTIAL -> {
                    step("\\ln y = \\ln a + k\\,x", StepKey.FIT_LINEARIZE, "\\ln y = \\ln a + k\\,x")
                    step("k ${eqv(coefficient("k"))},\\ a ${eqv(coefficient("a"))}", StepKey.FIT_NORMAL_EQUATIONS)
                }
                Mode.FIT_POWER -> {
                    step("\\ln y = \\ln a + b\\,\\ln x", StepKey.FIT_LINEARIZE, "\\ln y = \\ln a + b\\,\\ln x")
                    step("b ${eqv(coefficient("b"))},\\ a ${eqv(coefficient("a"))}", StepKey.FIT_NORMAL_EQUATIONS)
                }
                Mode.FIT_LOGARITHMIC -> step("b ${eqv(coefficient("b"))},\\ a ${eqv(coefficient("a"))}", StepKey.FIT_NORMAL_EQUATIONS)
                else -> step("c ${eqv(coefficient("c"))},\\ a ${eqv(coefficient("a"))},\\ k ${eqv(coefficient("k"))}", StepKey.FIT_NUMERICAL)
            }
            result.value(Quantity.R_SQUARED)?.let { step("R^{2} = 1 - \\frac{SS_{res}}{SS_{tot}} ${eqv(it)}", StepKey.FIT_R_SQUARED) }
            step("y = " + l(model), StepKey.RESULT)
        }

        /** The transition matrix of the call: its first argument. */
        fun transition(): Expr.Mat? = try {
            (StatCommands.arguments(latex, Flags()).firstOrNull() as? StatCommands.Arg.Value)?.e as? Expr.Mat
        } catch (e: CalcException) {
            null
        }

        fun stationary(pi: Expr) {
            val p = transition() ?: return
            step("P = " + l(p), StepKey.START)
            val k = p.size
            step("\\pi P = \\pi,\\quad " + (1..k).joinToString(" + ") { "\\pi_{$it}" } + " = 1", StepKey.MARKOV_SYSTEM)
            step("\\pi = " + l(pi), StepKey.RESULT)
            step("\\pi P = \\pi", StepKey.MARKOV_CHECK)
        }

        fun markov(pi: Expr) {
            val p = transition() ?: return
            val args = StatCommands.arguments(latex, Flags())
            val n = (args.getOrNull(2) as? StatCommands.Arg.Value)?.e ?: return
            step("P = " + l(p), StepKey.START)
            step("\\pi_{${l(n)}} = \\pi_{0}\\,P^{${l(n)}}", StepKey.MARKOV_POWER, l(n))
            step("\\pi_{${l(n)}} = " + l(pi), StepKey.RESULT)
        }
    }
}
