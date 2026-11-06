package com.professoreuler.mathcalc

/**
 * Worked steps for [Applied]'s functions, from what their outcomes hold and the work behind them ([MatrixFunctions],
 * [Applications], [LinearProgram]): the echelon form a rank is read from, the free variables of a null space, the pivot
 * columns of a column space, the determinant of λI − A, the cofactors of an adjugate; a tangent's point, slope and line; an
 * integral's formula (arc length, discs, shells, mean value) with the integral's own steps; an area's crossings and pieces; a
 * linear program's corners and values (two letters) or its simplex point, and its certificate. The last step is the answer.
 */
internal object AppliedSteps {
    fun of(c: Calculation, latex: String, assumptions: Assumptions): List<Step>? {
        val mode = c.choice?.mode ?: return null
        if (mode !in Applied.MODES) return null
        return try {
            Budget.within(MathCalc.LIMIT_MS) { Builder(c, latex, assumptions).build(mode) }?.takeIf { it.size >= 2 }
        } catch (e: CalcException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    private class Builder(val c: Calculation, val latex: String, val assumptions: Assumptions) {
        val out = ArrayList<Step>()
        fun l(e: Expr) = Format.expr(e, c, latex = true)
        fun v(e: Expr) = c.latexValue(e)
        fun eqv(e: Expr) = v(e).let { if (it.startsWith("\\approx")) it else "= $it" }
        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        val statements: List<Statement> by lazy {
            Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList(), Flags(assumptions)).statements()
        }

        fun build(mode: Mode): List<Step>? {
            when (mode) {
                Mode.RANK -> rank()
                Mode.TRACE -> trace()
                Mode.NULL_SPACE, Mode.COLUMN_SPACE -> basis(mode == Mode.NULL_SPACE)
                Mode.CHARACTERISTIC_POLYNOMIAL -> characteristic()
                Mode.ADJUGATE -> adjugate()
                Mode.TANGENT_LINE, Mode.NORMAL_LINE -> tangent(c.outcome as? Outcome.TangentLine ?: return null)
                Mode.ARC_LENGTH -> arcLength()
                Mode.AREA_BETWEEN -> area()
                Mode.VOLUME_X, Mode.VOLUME_Y -> volume(mode == Mode.VOLUME_X)
                Mode.MEAN_VALUE -> mean()
                Mode.MAXIMIZE, Mode.MINIMIZE -> program((c.outcome as? Outcome.Optimized)?.program ?: return null)
                else -> return null
            }
            return out
        }

        // --------------------------------------------------------------------------------------------------------------
        // Matrices.

        fun matrix(): Expr.Mat = Applied.matrixOf(statements)

        fun rank() {
            val m = matrix()
            step("A = " + l(m), StepKey.START)
            val e = LinAlg.echelon(m)
            step("A \\sim " + l(e.form), StepKey.RANK_ECHELON, e.rank.toString())
            when (val o = c.outcome) {
                is Outcome.Value -> step("\\operatorname{rank} A = " + c.latex(o.value), StepKey.RESULT)
                is Outcome.RankCases -> {
                    val cert = MatrixFunctions.certified(m)
                    if (o.generic > 0) {
                        val minor = Calculus.tidy(LinAlg.det(Expr.Mat(cert.rows.map { i -> cert.pivots.map { j -> m.rows[i][j] } })))
                        step("D = " + l(minor), StepKey.RANK_MINOR, l(minor))
                    }
                    val name = c.latexName(o.parameter)
                    for ((value, r) in o.special) step("$name = ${v(value)}:\\ \\operatorname{rank} A = $r", StepKey.RANK_CASE)
                    val except = (o.special.map { it.first } + o.undefined).joinToString(",\\ ") { v(it) }
                    step("\\operatorname{rank} A = ${o.generic}" + (if (except.isNotEmpty()) "\\quad ($name \\ne $except)" else ""), StepKey.RESULT)
                }
                else -> {}
            }
        }

        fun trace() {
            val m = matrix()
            step("A = " + l(m), StepKey.START)
            val diagonal = m.rows.indices.map { m.rows[it][it] }
            val t = LinAlg.trace(m)
            val sum = diagonal.joinToString(" + ") { p(it) }
            step("\\operatorname{tr} A = " + sum + then(sum, t), StepKey.TRACE_SUM)
        }

        /** A formula's part in brackets where it is a sum or negative. */
        fun p(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" + ") || it.contains(" - ")) "\\left($it\\right)" else it }

        /** e², with brackets where e is more than a letter or a whole number: (2x)², (√x)². */
        fun sq(e: Expr) = if (e is Expr.Sym || (e is Expr.Num && e.value.signum >= 0 && e.value.isInteger)) l(e) + "^{2}" else "\\left(${l(e)}\\right)^{2}"

        /** A value as short as it is written: the exact form, or its decimal where it has none. */
        fun short(e: Expr) = if (e is Expr.Real || e.hasDecimal()) v(e).removePrefix("\\approx ") else l(e)

        /** " = value" (or " ≈ value") after [shown]; only its decimal where [shown] is its exact form already. */
        fun then(shown: String, e: Expr): String {
            val value = v(e)
            return when {
                value == shown -> ""
                value.startsWith("$shown ") -> value.removePrefix(shown)
                else -> " " + eqv(e)
            }
        }

        fun basis(nullSpace: Boolean) {
            val m = matrix()
            val o = c.outcome as? Outcome.Basis ?: return
            step("A = " + l(m), StepKey.START)
            val e = LinAlg.echelon(m)
            step("A \\sim " + l(e.form), StepKey.RANK_ECHELON, e.rank.toString())
            val span = if (o.vectors.isEmpty()) "\\{0\\}" else "\\operatorname{span}\\left\\{" + o.vectors.joinToString(",\\ ") { l(it) } + "\\right\\}"
            if (nullSpace) {
                val free = (0 until m.columns).filter { it !in e.pivots }
                if (free.isNotEmpty()) {
                    val names = free.joinToString(",\\ ") { "x_{${it + 1}}" }
                    step(names + " \\in \\mathbb{R}", StepKey.NULL_FREE, names)
                }
                step("\\ker A = $span", StepKey.RESULT)
            } else {
                val columns = e.pivots.joinToString(",\\ ") { (it + 1).toString() }
                step("\\operatorname{col} A = \\operatorname{span}\\left\\{" + e.pivots.joinToString(",\\ ") { "a_{${it + 1}}" } + "\\right\\}",
                    StepKey.COLUMN_PIVOTS, columns)
                step("\\operatorname{col} A = $span", StepKey.RESULT)
            }
        }

        fun characteristic() {
            val m = matrix()
            val o = c.outcome as? Outcome.Expression ?: return
            val lambda = c.choice?.letter?.let { Expr.Sym(it) } ?: LinAlg.LAMBDA
            step("A = " + l(m), StepKey.START)
            val shifted = Expr.Mat(m.rows.mapIndexed { i, row -> row.mapIndexed { j, x -> Calculus.tidy(if (i == j) lambda - x else neg(x)) } })
            val name = l(lambda)
            step("p($name) = \\det($name I - A) = \\det" + l(shifted), StepKey.CHARPOLY_DET)
            step("p($name) = " + l(o.result), StepKey.RESULT)
        }

        fun adjugate() {
            val m = matrix()
            val o = c.outcome as? Outcome.Expression ?: return
            val adj = o.result as? Expr.Mat ?: return
            step("A = " + l(m), StepKey.START)
            step("C = " + l(LinAlg.transpose(adj)), StepKey.ADJ_COFACTORS)
            step("\\operatorname{adj} A = C^{T} = " + l(adj), StepKey.ADJ_TRANSPOSE)
            step("A \\cdot \\operatorname{adj} A = " + p(Calculus.tidy(LinAlg.det(m))) + " \\cdot I", StepKey.ADJ_CHECK)
        }

        // --------------------------------------------------------------------------------------------------------------
        // Curves.

        fun formula(): Expr = Applied.formulaOf(statements)

        fun letter(f: Expr): Expr.Sym = Expr.Sym(c.choice?.letter ?: Applied.mainLetter(f.symbols()))

        fun tangent(o: Outcome.TangentLine) {
            val x = Expr.Sym(o.x)
            val name = c.latexName(o.x)
            val a = l(o.at)
            step("f($name) = " + l(o.function), StepKey.START)
            step("f($a) = " + l(o.value), StepKey.TANGENT_POINT, a)
            step("f'($name) = " + l(Calculus.tidy(Calculus.derivative(o.function, x))), StepKey.DERIVATIVE_OF)
            step("m = f'($a) = " + l(o.slope), StepKey.TANGENT_SLOPE)
            val shifted = if (isZero(o.at)) name else "\\left($name - ${p(o.at)}\\right)"
            if (!o.normal) {
                step("y = ${l(o.value)} + ${p(o.slope)} \\cdot $shifted", StepKey.TANGENT_FORMULA)
            } else if (o.line.first == "x") {
                step("x = $a", StepKey.NORMAL_VERTICAL)
                return
            } else {
                val perpendicular = Calculus.tidy(Expr.MINUS_ONE / o.slope)
                val written = "-\\frac{1}{${l(o.slope)}}"
                step("m_{\\perp} = $written" + then(written, perpendicular), StepKey.NORMAL_SLOPE)
                step("y = ${l(o.value)} + ${p(perpendicular)} \\cdot $shifted", StepKey.NORMAL_FORMULA)
            }
            step("${o.line.first} = " + l(o.line.second), StepKey.RESULT)
        }

        /** The integral of [integrand] from [lo] to [hi], step by step as the calculator works an integral out, or one step. */
        fun integral(integrand: Expr, x: Expr.Sym, lo: Expr, hi: Expr, value: Expr) {
            val text = "\\int_{${l(lo)}}^{${l(hi)}} ${l(integrand)} \\, d${c.latexName(x.name)}"
            val inner = try { Steps.of(text) } catch (e: CalcException) { null }
            val middle = inner?.drop(1)?.dropLast(1)
            if (middle != null && middle.isNotEmpty() && inner.last().why.key == StepKey.RESULT) out += middle
            else if (value is Expr.Real || value.hasDecimal()) step("$text \\approx " + v(value).removePrefix("\\approx "), StepKey.INTEGRAL_NUMERIC)
        }

        fun arcLength() {
            val f = formula()
            val x = letter(f)
            val (a, b) = Applied.boundsOf(c.choice?.point ?: return)
            val w = Applications.arcWork(f, x, a, b)
            val name = c.latexName(x.name)
            step("f($name) = " + l(f), StepKey.START)
            step("f'($name) = " + l(w.derivative), StepKey.DERIVATIVE_OF)
            val bounds = "_{${l(w.lo)}}^{${l(w.hi)}}"
            step("L = \\int$bounds \\sqrt{1 + ${sq(w.derivative)}} \\, d$name", StepKey.ARC_FORMULA)
            if (w.integrand != sqrt(w.radicand)) {
                step("\\sqrt{${l(w.radicand)}} = " + l(w.integrand), StepKey.ARC_SQUARE)
            }
            integral(w.integrand, x, w.lo, w.hi, w.length)
            step("L " + eqv(w.length), StepKey.RESULT)
        }

        fun area() {
            if (statements.size != 2) return
            val f = Applied.formulaOf(listOf(statements[0]))
            val g = Applied.formulaOf(listOf(statements[1]))
            val x = Expr.Sym(c.choice?.letter ?: Applied.mainLetter(f.symbols() + g.symbols()))
            val bounds = c.choice?.point?.let { Applied.boundsOf(it) }
            val w = Applications.areaWork(f, g, x, bounds?.first, bounds?.second)
            val name = c.latexName(x.name)
            step("f($name) = ${l(f)},\\quad g($name) = ${l(g)}", StepKey.START)
            if (w.crossings.isNotEmpty()) {
                step("${l(f)} = ${l(g)} \\iff " + w.crossings.joinToString(" \\lor ") { "$name " + eqv(it) }, StepKey.AREA_CROSSINGS)
            }
            val d = p(w.difference)
            for ((lo, hi, signed) in w.pieces.pieces) {
                step("\\int_{${l(lo)}}^{${l(hi)}} $d \\, d$name " + eqv(signed), StepKey.AREA_PIECE)
            }
            if (w.pieces.pieces.size > 1) {
                step("A = " + w.pieces.pieces.joinToString(" + ") { "\\left|" + short(it.third) + "\\right|" }, StepKey.AREA_PIECES)
            }
            step("A " + eqv(w.pieces.total), StepKey.RESULT)
        }

        fun volume(discs: Boolean) {
            val f = formula()
            val x = letter(f)
            val (a, b) = Applied.boundsOf(c.choice?.point ?: return)
            val name = c.latexName(x.name)
            step("f($name) = " + l(f), StepKey.START)
            if (discs) {
                val w = Applications.volumeXWork(f, x, a, b)
                step("V = \\pi \\int_{${l(w.lo)}}^{${l(w.hi)}} ${sq(f)} \\, d$name = \\pi \\int_{${l(w.lo)}}^{${l(w.hi)}} ${p(w.integrand)} \\, d$name", StepKey.VOLUME_DISCS)
                integral(w.integrand, x, w.lo, w.hi, w.integral)
                val product = "\\pi \\cdot ${p(w.integral)}"
                step("V = $product" + then(product, w.volume), StepKey.RESULT)
            } else {
                val w = Applications.volumeYWork(f, x, a, b)
                step("V = 2\\pi \\int_{${l(w.lo)}}^{${l(w.hi)}} \\left|${l(w.integrand)}\\right| \\, d$name", StepKey.VOLUME_SHELLS)
                val pieces = w.pieces
                if (pieces != null) {
                    val d = p(w.integrand)
                    if (pieces.pieces.size == 1) {
                        val (lo, hi, signed) = pieces.pieces[0]
                        integral(w.integrand, x, lo, hi, signed)
                    } else {
                        for ((lo, hi, signed) in pieces.pieces) step("\\int_{${l(lo)}}^{${l(hi)}} $d \\, d$name " + eqv(signed), StepKey.AREA_PIECE)
                    }
                }
                val product = "2\\pi \\cdot ${p(w.integral)}"
                step("V = $product" + then(product, w.volume), StepKey.RESULT)
            }
        }

        fun mean() {
            val f = formula()
            val x = letter(f)
            val (a, b) = Applied.boundsOf(c.choice?.point ?: return)
            val w = Applications.meanWork(f, x, a, b)
            val name = c.latexName(x.name)
            step("f($name) = " + l(f), StepKey.START)
            step("\\bar{f} = \\frac{1}{${l(w.length)}} \\int_{${l(a)}}^{${l(b)}} ${p(f)} \\, d$name", StepKey.MEAN_FORMULA)
            integral(f, x, a, b, w.integral)
            val quotient = "\\frac{${short(w.integral)}}{${l(w.length)}}"
            step("\\bar{f} = $quotient" + then(quotient, w.mean), StepKey.RESULT)
        }

        // --------------------------------------------------------------------------------------------------------------
        // Linear programs.

        fun program(s: LinearProgram.Solution) {
            val letters = s.letters.map { Expr.Sym(it) }
            fun lhs(k: LinearProgram.Constraint) = l(Calculus.tidy(add(k.coefficients.indices.map { mul(num(k.coefficients[it]), letters[it]) })))
            fun row(k: LinearProgram.Constraint) = lhs(k) + " " + k.relation.latex + " " + l(num(k.bound))
            fun point(p: List<Expr>) = "\\left(" + p.joinToString(",\\ ") { l(it) } + "\\right)"
            val sense = if (s.maximize) "\\max" else "\\min"
            step("$sense\\ " + l(s.objective) + "\\quad " + s.constraints.joinToString(",\\ ") { row(it) }, StepKey.START)
            val picture = s.picture
            if (picture != null && picture.corners.isNotEmpty()) {
                step(picture.corners.joinToString(",\\ ") { (p, q) -> "\\left(${l(p)},\\ ${l(q)}\\right)" }, StepKey.LP_CORNERS)
                step(picture.corners.indices.joinToString(",\\ ") { k ->
                    "z\\left(${l(picture.corners[k].first)},\\ ${l(picture.corners[k].second)}\\right) = ${l(picture.values[k])}"
                }, StepKey.LP_VALUES)
            }
            when (s.status) {
                LinearProgram.Status.OPTIMAL -> {
                    if (picture == null || picture.corners.isEmpty()) step(point(s.point!!), StepKey.LP_SIMPLEX)
                    // The certificate: the objective as Σ λᵢ·(left side ᵢ), which bounds it by Σ λᵢ·(right side ᵢ).
                    val used = s.constraints.indices.filter { !isZero(s.multipliers!![it]) }
                    val combination = if (used.isEmpty()) "0" else used.joinToString(" + ") { "${p(s.multipliers!![it])}\\left(${lhs(s.constraints[it])}\\right)" }
                    val bound = l(Calculus.tidy(add(used.map { mul(s.multipliers!![it], num(s.constraints[it].bound)) })))
                    // The objective's part in the letters (its constant term changes no bound).
                    val linear = Calculus.tidy(s.objective - constant(s))
                    step(l(linear) + " = " + combination + (if (s.maximize) " \\le " else " \\ge ") + bound, StepKey.LP_DUAL)
                    val z = s.letters.indices.joinToString(",\\ ") { "${c.latexName(s.letters[it])} = ${l(s.point!![it])}" }
                    step("z_{${if (s.maximize) "max" else "min"}} = ${l(s.value!!)}\\quad ($z)", StepKey.RESULT)
                }
                LinearProgram.Status.UNBOUNDED -> step(point(s.point!!) + " + t" + point(s.ray!!) + ",\\ t \\ge 0", StepKey.LP_UNBOUNDED)
                LinearProgram.Status.INFEASIBLE -> {
                    val y = s.multipliers!!
                    val used = s.constraints.indices.filter { !isZero(y[it]) }
                    val sum = l(Calculus.tidy(add(used.map { mul(y[it], num(s.constraints[it].bound)) })))
                    step(used.joinToString(" + ") { "${p(y[it])}\\left(${lhs(s.constraints[it])}\\right)" } + " = 0 \\le " + sum, StepKey.LP_INFEASIBLE)
                }
            }
        }

        /** The objective's constant term (its value with every letter 0). */
        fun constant(s: LinearProgram.Solution): Expr = Calculus.tidy(s.letters.fold(s.objective) { acc, v -> acc.substitute(Expr.Sym(v), Expr.ZERO) })
    }
}
