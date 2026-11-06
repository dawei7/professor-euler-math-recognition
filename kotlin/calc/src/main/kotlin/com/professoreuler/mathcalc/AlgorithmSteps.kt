package com.professoreuler.mathcalc

/**
 * Worked steps for the algorithms' outcomes, from what they hold: the master theorem (a, b and f read off, n^(log_b a), the case,
 * the order), Akra–Bazzi (p, the integral, the order), a comparison of growth (the limit and what it says) and a Huffman code (the
 * merges, the code words, the average length). The traces and tables are their own steps and have none here.
 */
internal object AlgorithmSteps {
    fun of(c: Calculation): List<Step>? = try {
        Builder(c).build()?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private class Builder(val c: Calculation) {
        val out = ArrayList<Step>()

        fun l(e: Expr) = Format.expr(e, c, latex = true)

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        fun build(): List<Step>? {
            when (val o = c.outcome) {
                is Outcome.DivideAndConquer -> recurrence(o.analysis)
                is Outcome.Growth -> growth(o.growth)
                is Outcome.HuffmanCode -> huffman(o.code)
                else -> return null
            }
            return out
        }

        /** T(n) = a·T(n/b) + … + f(n), as the analysis read it. */
        fun written(a: DivideConquer.Analysis): String {
            val n = a.variable
            val calls = a.terms.joinToString(" + ") { (k, b) ->
                val coefficient = if (isOne(k)) "" else l(k) + "\\,"
                val arg = rational(b)!!.let { r -> if (r.num == java.math.BigInteger.ONE) "\\frac{$n}{${r.den}}" else "\\frac{${r.num}$n}{${r.den}}" }
                "$coefficient${a.function}\\left($arg\\right)"
            }
            val f = if (isZero(a.driving)) "" else " + " + l(a.driving)
            return "${a.function}($n) = $calls$f"
        }

        fun theta(e: Expr) = "\\Theta\\left(" + l(e) + "\\right)"

        fun recurrence(a: DivideConquer.Analysis) {
            val n = Expr.Sym(a.variable)
            step(written(a), StepKey.START)
            if (a.method == DivideConquer.Method.MASTER) {
                if (a.gap == DivideConquer.Gap.SEVERAL_SIZES) {
                    step(a.terms.joinToString(",\\ ") { (_, b) -> "\\frac{${a.variable}}{" + l(Calculus.tidy(pow(b, Expr.MINUS_ONE))) + "}" },
                        StepKey.MASTER_SEVERAL)
                    return
                }
                val (k, b) = a.terms.single()
                val bb = Calculus.tidy(pow(b, Expr.MINUS_ONE))
                step("a = " + l(k) + ",\\ b = " + l(bb) + ",\\ f(${a.variable}) = " + l(a.driving), StepKey.MASTER_PARTS)
                val critical = a.critical ?: return
                step("${a.variable}^{\\log_{${l(bb)}} ${l(k)}} = " + l(Calculus.tidy(pow(n, critical))), StepKey.MASTER_CRITICAL)
                val (p, q) = a.drivingOrder ?: (Expr.ZERO to Expr.ZERO)
                val fOrder = if (a.drivingOrder == null) "f(${a.variable}) = 0" else "f(${a.variable}) = " + theta(order(n, p, q))
                when {
                    a.gap == DivideConquer.Gap.BETWEEN_CASES_1_AND_2 -> step(fOrder, StepKey.MASTER_GAP, l(q))
                    a.case == 1 -> step(fOrder, StepKey.MASTER_CASE_1)
                    a.case == 2 -> step(fOrder, StepKey.MASTER_CASE_2, l(q))
                    a.case == 3 -> step(fOrder, StepKey.MASTER_CASE_3, l(a.regularity!!))
                }
            } else {
                val p = a.critical?.let { l(it) } ?: a.criticalDigits?.let { "p \\approx " + Algorithms.decimal(it, c, latex = true) }.orEmpty()
                val sum = a.terms.joinToString(" + ") { (k, b) -> (if (isOne(k)) "" else l(k) + "\\cdot ") + "\\left(" + l(b) + "\\right)^{p}" }
                step("$sum = 1,\\ " + (if (a.critical != null) "p = $p" else p), StepKey.AKRA_P)
                step("\\int_{1}^{${a.variable}} \\frac{g(u)}{u^{p+1}}\\,du = " + theta(a.integral ?: return), StepKey.AKRA_INTEGRAL)
            }
            val order = a.order ?: return
            step("${a.function}(${a.variable}) = " + theta(order), StepKey.RESULT)
        }

        fun order(n: Expr, p: Expr, q: Expr): Expr = Calculus.tidy(mul(pow(n, p), pow(fn(Func.LOG, num(2), n), q)))

        fun growth(g: Asymptotics.Growth) {
            val x = g.variable
            for ((k, l0) in g.limits.withIndex()) {
                val f = g.functions[g.order[k]]
                val h = g.functions[g.order[k + 1]]
                step("\\lim_{$x \\to \\infty} \\frac{${l(f)}}{${l(h)}} = " + l(l0), StepKey.GROWTH_LIMIT)
                if (!g.sorted) {
                    when {
                        l0 == Expr.INF -> step(l(f) + " \\in \\omega\\left(" + l(h) + "\\right)", StepKey.GROWTH_INFINITE)
                        isZero(l0) -> step(l(f) + " \\in o\\left(" + l(h) + "\\right)", StepKey.GROWTH_ZERO)
                        else -> step(l(f) + " \\in \\Theta\\left(" + l(h) + "\\right)", StepKey.GROWTH_FINITE)
                    }
                }
            }
            if (g.sorted) {
                val chain = g.order.indices.joinToString("") { k ->
                    (if (k == 0) "" else if (isZero(g.limits[k - 1])) " \\prec " else " \\asymp ") + l(g.functions[g.order[k]])
                }
                step(chain, StepKey.RESULT)
            }
        }

        fun huffman(h: Huffman.Code) {
            val names = h.symbols.toMutableList()
            val weights = h.weights.toMutableList()
            for (m in h.merges) {
                step(l(weights[m.first]) + " + " + l(weights[m.second]) + " = " + l(m.weight), StepKey.HUFFMAN_MERGE, l(weights[m.first]),
                    l(weights[m.second]))
                names += names[m.first] + names[m.second]
                weights += m.weight
            }
            step(h.symbols.indices.joinToString(",\\ ") { "\\text{${h.symbols[it]}} \\mapsto \\texttt{${h.codes[it]}}" }, StepKey.HUFFMAN_CODES)
            step("L = \\sum p_i\\,l_i = " + l(h.average) + ",\\ H " + c.latexValue(h.entropy).let { if (it.startsWith("\\approx")) it else "= $it" },
                StepKey.HUFFMAN_AVERAGE)
        }
    }
}
