package com.professoreuler.mathcalc

/**
 * Worked steps for financial mathematics ([Finance]): a value function's formula in letters, the values put in as they were
 * written, and the result; a table's regular amount, its first row and its last; the internal rate of return's polynomial and
 * its rates. The steps say what the Calculator did; every number in them is one the outcome already holds or that its check
 * worked out.
 */
internal object FinanceSteps {
    private val TOKEN = MathCalc.TOKEN

    fun of(c: Calculation, latex: String): List<Step>? = try {
        build(c, latex)?.takeIf { it.size >= 2 }
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    private fun build(c: Calculation, latex: String): List<Step>? {
        val out = ArrayList<Step>()
        fun step(shown: String, key: StepKey) {
            out += Step(Steps.tidy(shown), StepText(key))
        }
        when (val o = c.outcome) {
            is Outcome.FinanceSchedule -> schedule(o.schedule, ::step)
            is Outcome.Solved -> if (c.mode == Mode.IRR) irr(c, latex, o.solutions, ::step) else return null
            is Outcome.Value -> if (c.mode == Mode.CALCULATE) value(c, latex, o, ::step) else return null
            else -> return null
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // The value functions.

    /** A call written as its function's name and its arguments, each argument's LaTeX as written. */
    private class Call(val name: String, val args: List<String>, val values: List<Expr>)

    private val CALL = Regex("""^([a-z]+)\s*\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)

    private fun call(latex: String): Call? {
        val plain = latex.trim().removeSuffix("=").trim()
            .replace(Regex("""\\operatorname\{([A-Za-z]+)\}"""), "$1")
            .replace("\\left(", "(").replace("\\right)", ")")
        val m = CALL.matchEntire(plain) ?: return null
        val name = m.groupValues[1]
        if (name !in Finance.NAMES) return null
        val args = split(m.groupValues[2]) ?: return null
        val values = args.map { a ->
            val st = Parser(TOKEN.findAll(a).map { it.value }.toList()).statements()
            val side = st.singleOrNull()?.sides?.singleOrNull() ?: return null
            Calculus.evaluate(side)
        }
        if (values.any { it.symbols().isNotEmpty() }) return null
        return Call(name, args.map { it.trim() }, values)
    }

    /** The arguments, split at the commas outside brackets; null where the brackets do not close. */
    private fun split(inner: String): List<String>? {
        val out = ArrayList<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in inner) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
            }
            if (depth < 0) return null
            if (ch == ',' && depth == 0) {
                out += current.toString()
                current.setLength(0)
            } else {
                current.append(ch)
            }
        }
        if (depth != 0) return null
        out += current.toString()
        return out.map { it.trim() }.takeIf { it.none(String::isEmpty) }
    }

    /** An argument as written, in brackets where it is more than a number: (0.04/12), (-1000). */
    private fun w(a: String): String = if (Regex("""^[0-9.]+(\\%)?$""").matches(a)) a else "\\left($a\\right)"

    private fun value(c: Calculation, latex: String, o: Outcome.Value, step: (String, StepKey) -> Unit) {
        val k = call(latex) ?: return
        val a = k.args
        val m = a.getOrNull(3)
        val infinite = k.values.getOrNull(3) == Expr.INF
        val zeroRate = { j: Int -> isZero(k.values[j]) }
        val result = o.exact?.let { c.latexValue(it) } ?: c.latex(o.value)
        val shown = if (result.startsWith("\\approx")) result else "= $result"
        when (k.name) {
            "simpleinterest" -> {
                step("I = P\\cdot r\\cdot t", StepKey.FIN_SIMPLE_INTEREST)
                step("I = ${w(a[0])}\\cdot ${w(a[1])}\\cdot ${w(a[2])}", StepKey.FIN_SUBSTITUTE)
                step("I $shown", StepKey.RESULT)
            }
            "futurevalue" -> {
                when {
                    m == null -> step("F = P\\left(1 + r\\right)^{n}", StepKey.FIN_COMPOUND)
                    infinite -> step("F = P\\,e^{r n}", StepKey.FIN_CONTINUOUS)
                    else -> step("F = P\\left(1 + \\frac{r}{m}\\right)^{m n}", StepKey.FIN_COMPOUND_SUBPERIODS)
                }
                step("F = " + grown(a, m, infinite, w(a[0])), StepKey.FIN_SUBSTITUTE)
                step("F $shown", StepKey.RESULT)
            }
            "presentvalue" -> {
                step(when {
                    m == null -> "P = \\frac{F}{\\left(1 + r\\right)^{n}}"
                    infinite -> "P = F\\,e^{-r n}"
                    else -> "P = \\frac{F}{\\left(1 + \\frac{r}{m}\\right)^{m n}}"
                }, StepKey.FIN_DISCOUNT)
                step("P = " + if (infinite) "${w(a[0])}\\,e^{-${w(a[1])}\\cdot ${w(a[2])}}" else "\\frac{${a[0]}}{" + grown(a, m, false, "") + "}",
                    StepKey.FIN_SUBSTITUTE)
                step("P $shown", StepKey.RESULT)
            }
            "interestrate" -> {
                step(when {
                    m == null -> "r = \\sqrt[n]{\\frac{F}{P}} - 1"
                    infinite -> "r = \\frac{1}{n}\\ln\\frac{F}{P}"
                    else -> "r = m\\left(\\sqrt[m n]{\\frac{F}{P}} - 1\\right)"
                }, StepKey.FIN_RATE)
                step("r = " + when {
                    m == null -> "\\sqrt[${a[2]}]{\\frac{${a[1]}}{${a[0]}}} - 1"
                    infinite -> "\\frac{1}{${a[2]}}\\ln\\frac{${a[1]}}{${a[0]}}"
                    else -> "${w(m)}\\left(\\sqrt[${w(m)}\\cdot ${w(a[2])}]{\\frac{${a[1]}}{${a[0]}}} - 1\\right)"
                }, StepKey.FIN_SUBSTITUTE)
                step("r $shown", StepKey.RESULT)
            }
            "periods" -> {
                step(when {
                    m == null -> "n = \\frac{\\ln\\frac{F}{P}}{\\ln\\left(1 + r\\right)}"
                    infinite -> "n = \\frac{1}{r}\\ln\\frac{F}{P}"
                    else -> "n = \\frac{\\ln\\frac{F}{P}}{m\\ln\\left(1 + \\frac{r}{m}\\right)}"
                }, StepKey.FIN_PERIODS)
                step("n = " + when {
                    m == null -> "\\frac{\\ln\\frac{${a[1]}}{${a[0]}}}{\\ln\\left(1 + ${a[2]}\\right)}"
                    infinite -> "\\frac{1}{${a[2]}}\\ln\\frac{${a[1]}}{${a[0]}}"
                    else -> "\\frac{\\ln\\frac{${a[1]}}{${a[0]}}}{${w(m)}\\ln\\left(1 + \\frac{${a[2]}}{$m}\\right)}"
                }, StepKey.FIN_SUBSTITUTE)
                step("n $shown", StepKey.RESULT)
            }
            "effectiverate" -> {
                val times = a[1]
                step(if (infinite(k, 1)) "r_{\\mathrm{eff}} = e^{r} - 1" else "r_{\\mathrm{eff}} = \\left(1 + \\frac{r}{m}\\right)^{m} - 1", StepKey.FIN_EFFECTIVE)
                step("r_{\\mathrm{eff}} = " + if (infinite(k, 1)) "e^{${a[0]}} - 1" else "\\left(1 + \\frac{${a[0]}}{$times}\\right)^{$times} - 1",
                    StepKey.FIN_SUBSTITUTE)
                step("r_{\\mathrm{eff}} $shown", StepKey.RESULT)
            }
            "nominalrate" -> {
                val times = a[1]
                step(if (infinite(k, 1)) "r = \\ln\\left(1 + r_{\\mathrm{eff}}\\right)" else "r = m\\left(\\sqrt[m]{1 + r_{\\mathrm{eff}}} - 1\\right)", StepKey.FIN_NOMINAL)
                step("r = " + if (infinite(k, 1)) "\\ln\\left(1 + ${a[0]}\\right)" else "$times\\left(\\sqrt[$times]{1 + ${a[0]}} - 1\\right)",
                    StepKey.FIN_SUBSTITUTE)
                step("r $shown", StepKey.RESULT)
            }
            "annuityfv", "annuityduefv", "annuitypv", "annuityduepv" -> {
                val future = k.name.endsWith("fv")
                val due = "due" in k.name
                val letter = if (future) "F" else "P"
                val factor = if (zeroRate(1)) "n" else if (future) "\\frac{\\left(1 + r\\right)^{n} - 1}{r}" else "\\frac{1 - \\left(1 + r\\right)^{-n}}{r}"
                val dueFactor = if (due) "\\left(1 + r\\right)" else ""
                step("$letter = R$dueFactor\\cdot $factor",
                    if (due) StepKey.FIN_ANNUITY_DUE else if (future) StepKey.FIN_ANNUITY_FV else StepKey.FIN_ANNUITY_PV)
                val r = a[1]
                val n = a[2]
                val put = if (zeroRate(1)) n else if (future) "\\frac{\\left(1 + $r\\right)^{$n} - 1}{$r}" else "\\frac{1 - \\left(1 + $r\\right)^{-$n}}{$r}"
                step("$letter = ${w(a[0])}" + (if (due) "\\left(1 + $r\\right)" else "") + "\\cdot $put", StepKey.FIN_SUBSTITUTE)
                step("$letter $shown", StepKey.RESULT)
            }
            "loanpayment" -> {
                step(if (zeroRate(1)) "A = \\frac{P}{n}" else "A = P\\cdot\\frac{r}{1 - \\left(1 + r\\right)^{-n}}", StepKey.FIN_LOAN_PAYMENT)
                step("A = " + if (zeroRate(1)) "\\frac{${a[0]}}{${a[2]}}" else "${w(a[0])}\\cdot\\frac{${a[1]}}{1 - \\left(1 + ${a[1]}\\right)^{-${a[2]}}}",
                    StepKey.FIN_SUBSTITUTE)
                step("A $shown", StepKey.RESULT)
            }
            "loanterm" -> {
                step(if (zeroRate(1)) "n = \\frac{P}{A}" else "n = \\frac{\\ln\\frac{A}{A - r P}}{\\ln\\left(1 + r\\right)}", StepKey.FIN_LOAN_TERM)
                step("n = " + if (zeroRate(1)) "\\frac{${a[0]}}{${a[2]}}" else
                    "\\frac{\\ln\\frac{${a[2]}}{${a[2]} - ${w(a[1])}\\cdot ${w(a[0])}}}{\\ln\\left(1 + ${a[1]}\\right)}", StepKey.FIN_SUBSTITUTE)
                step("n $shown", StepKey.RESULT)
            }
            "npv" -> {
                step("\\mathrm{NPV} = \\sum_{t=0}^{n} \\frac{CF_{t}}{\\left(1 + r\\right)^{t}}", StepKey.FIN_NPV)
                val flows = a.drop(1)
                val terms = flows.withIndex().map { (t, f) -> if (t == 0) f else "\\frac{$f}{\\left(1 + ${a[0]}\\right)^{$t}}" }
                val written = if (terms.size <= 7) terms.joinToString(" + ") else (terms.take(3) + "\\dots" + terms.takeLast(2)).joinToString(" + ")
                step("\\mathrm{NPV} = " + written.replace("+ -", "- "), StepKey.FIN_SUBSTITUTE)
                step("\\mathrm{NPV} $shown", StepKey.RESULT)
            }
        }
    }

    private fun infinite(k: Call, j: Int) = k.values.getOrNull(j) == Expr.INF

    /** P·(1 + r)ⁿ with the arguments put in: [p] in front (empty: the factor alone). */
    private fun grown(a: List<String>, m: String?, infinite: Boolean, p: String): String = when {
        infinite -> "$p\\,e^{${w(a[1])}\\cdot ${w(a[2])}}"
        m == null -> "$p\\left(1 + ${a[1]}\\right)^{${a[2]}}"
        else -> "$p\\left(1 + \\frac{${a[1]}}{$m}\\right)^{${w(m)}\\cdot ${w(a[2])}}"
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Tables and the internal rate of return.

    private fun money(x: Rational) = Finance.money(x)

    private fun rate(r: Rational): String = Format.expr(Expr.Num(r), Style.of(decimalComma = false, decimals = true, fractions = false), latex = true)

    private fun schedule(s: Finance.Schedule, step: (String, StepKey) -> Unit) {
        val n = s.periods
        val rows = s.rows
        val first = rows.first()
        val last = rows.last()
        when (s.plan) {
            Finance.Plan.ANNUITY_LOAN, Finance.Plan.EQUAL_PRINCIPAL -> {
                val j = s.columns
                val start = j.indexOf(Finance.Column.DEBT_START)
                val pay = j.indexOf(Finance.Column.PAYMENT)
                val interest = j.indexOf(Finance.Column.INTEREST)
                val principal = j.indexOf(Finance.Column.PRINCIPAL)
                val end = j.indexOf(Finance.Column.DEBT_END)
                val exact = s.exact?.let { rational(it) }
                if (s.plan == Finance.Plan.ANNUITY_LOAN) {
                    val value = if (exact != null && exact == s.regular) "= " + money(s.regular!!) else "\\approx " + money(s.regular!!)
                    step(if (s.rate.signum == 0) "A = \\frac{P}{n} = \\frac{${money(s.start)}}{$n} $value"
                        else "A = P\\cdot\\frac{r}{1 - \\left(1 + r\\right)^{-n}} = ${money(s.start)}\\cdot\\frac{${rate(s.rate)}}{1 - \\left(1 + ${rate(s.rate)}\\right)^{-$n}} $value",
                        StepKey.FIN_LOAN_PAYMENT)
                } else {
                    val value = if (exact != null && exact == s.regular) "= " + money(s.regular!!) else "\\approx " + money(s.regular!!)
                    step("T = \\frac{P}{n} = \\frac{${money(s.start)}}{$n} $value", StepKey.FIN_EQUAL_PRINCIPAL)
                }
                step("I_{1} = ${money(first[start])}\\cdot ${rate(s.rate)} = ${money(first[interest])},\\quad " +
                    "D_{1} = ${money(first[start])} - ${money(first[principal])} = ${money(first[end])}", StepKey.FIN_SCHEDULE_ROW)
                if (n > 1) {
                    step("I_{$n} = ${money(last[interest])},\\quad " +
                        "A_{$n} = ${money(last[principal])} + ${money(last[interest])} = ${money(last[pay])},\\quad D_{$n} = ${money(last[end])}",
                        StepKey.FIN_SCHEDULE_LAST)
                }
            }
            Finance.Plan.STRAIGHT_LINE -> {
                val dep = s.columns.indexOf(Finance.Column.DEPRECIATION)
                val end = s.columns.indexOf(Finance.Column.VALUE_END)
                val salvage = last[end]
                val exact = s.exact?.let { rational(it) }
                val value = if (exact != null && exact == s.regular) "= " + money(s.regular!!) else "\\approx " + money(s.regular!!)
                step("d = \\frac{C - S}{n} = \\frac{${money(s.start)} - ${money(salvage)}}{$n} $value", StepKey.FIN_STRAIGHT_LINE)
                step("d_{$n} = ${money(last[dep])},\\quad B_{$n} = ${money(salvage)}", StepKey.FIN_DEPRECIATION_LAST)
            }
            Finance.Plan.DECLINING_BALANCE -> {
                val start = s.columns.indexOf(Finance.Column.VALUE_START)
                val dep = s.columns.indexOf(Finance.Column.DEPRECIATION)
                val end = s.columns.indexOf(Finance.Column.VALUE_END)
                step("d_{1} = B_{0}\\cdot ${rate(s.rate)} = ${money(first[start])}\\cdot ${rate(s.rate)} = ${money(first[dep])}", StepKey.FIN_DECLINING)
                step("B_{$n} = ${money(last[end])} \\approx C\\left(1 - ${rate(s.rate)}\\right)^{$n}", StepKey.RESULT)
            }
        }
    }

    private fun irr(c: Calculation, latex: String, solutions: Solutions, step: (String, StepKey) -> Unit) {
        val st = Parser(TOKEN.findAll(latex).map { it.value }.toList()).statements()
        val m = st.singleOrNull()?.sides?.firstOrNull()?.let { Calculus.evaluate(it) } as? Expr.Mat ?: return
        val flows = m.rows.map { it[0] }
        step("CF = \\left(" + flows.joinToString(",\\ ") { c.latex(it) } + "\\right)", StepKey.START)
        val trimmed = flows.dropLastWhile { isZero(it) }
        val n = trimmed.size - 1
        val q = Expr.Sym("q")
        val polynomial = add(trimmed.mapIndexed { t, f -> mul(f, pow(q, num((n - t).toLong()))) })
        step(Format.expr(polynomial, c, latex = true) + " = 0", StepKey.FIN_IRR_POLYNOMIAL)
        if (solutions.values.isEmpty()) {
            step("r \\in \\emptyset", StepKey.NO_SOLUTION)
            return
        }
        step(solutions.values.joinToString(",\\ ") { "r = " + c.latexValue(it) }, StepKey.FIN_IRR_RATES)
    }
}
