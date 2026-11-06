package com.professoreuler.mathcalc

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Worked steps for fixed-point iteration, the Jacobi and Gauss–Seidel methods, norms and condition numbers, Gram–Schmidt and
 * projections, from what their outcomes hold: the iteration's formulas and the tests that decide whether it converges, a norm's
 * sums, the inverse a condition number takes, the vectors of Gram–Schmidt one by one. Each step says what was done and shows
 * the formula after it.
 */
internal object IterativeSteps {
    fun of(c: Calculation, latex: String, assumptions: Assumptions): List<Step>? {
        if (c.mode !in Iterative.MODES) return null
        return try {
            Budget.within(MathCalc.LIMIT_MS) { Builder(c, latex, assumptions).build()?.takeIf { it.size >= 2 } }
        } catch (e: CalcException) {
            null
        } catch (e: ArithmeticException) {
            null
        } catch (e: Imprecise) {
            null
        }
    }

    private class Builder(val c: Calculation, val latex: String, val assumptions: Assumptions) {
        val out = ArrayList<Step>()

        fun l(e: Expr) = Format.expr(e, c, latex = true)
        fun v(e: Expr) = c.latexValue(e)

        /** "= value", or "≈ value" where only a decimal is known. */
        fun eqv(e: Expr) = v(e).let { if (it.startsWith("\\approx")) it else "= $it" }

        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        /** The formula's lines, their calculus worked out. */
        fun lines(): List<List<Expr>> {
            val statements = Parser(MathCalc.TOKEN.findAll(latex).map { it.value }.toList(), Flags(assumptions)).statements()
            return statements.map { s -> s.sides.filterNotNull().map { Calculus.evaluate(it) } }
        }

        fun build(): List<Step>? {
            when (val o = c.outcome) {
                is Outcome.Orthonormalized -> gramSchmidt(o.basis)
                is Outcome.Expression -> if (c.mode == Mode.PROJECTION) projection(o.result as? Expr.Mat ?: return null) else return null
                is Outcome.Value -> when (c.mode) {
                    Mode.NORM -> norm(o)
                    Mode.CONDITION -> condition(o)
                    else -> return null
                }
                is Outcome.Iterated -> if (c.mode == Mode.FIXED_POINT) fixedPoint(o.iteration) else linear(o.iteration)
                else -> return null
            }
            return out
        }

        fun vec(name: String, k: Int) = "\\vec{$name}_{$k}"

        // --------------------------------------------------------------------------------------------------------------
        // Gram–Schmidt and projections.

        fun gramSchmidt(b: Orthogonal.Basis) {
            step(b.vectors.indices.joinToString(",\\ ") { vec("v", it + 1) + " = " + l(b.vectors[it]) }, StepKey.START)
            val kept = ArrayList<Expr.Mat>()
            for ((j, vj) in b.vectors.withIndex()) {
                if (j in b.dependent) {
                    step(vec("v", j + 1) + projections(vj, kept) + " = \\vec{0}", StepKey.ORTHO_DEPENDENT, vec("v", j + 1))
                    continue
                }
                val u = b.orthogonal[kept.size]
                val name = vec("u", kept.size + 1)
                if (kept.isEmpty()) step("$name = ${vec("v", j + 1)} = " + l(u), StepKey.ORTHO_FIRST)
                else step("$name = ${vec("v", j + 1)}" + projections(vj, kept) + " = " + l(u), StepKey.ORTHO_SUBTRACT, vec("v", j + 1))
                kept += u
            }
            for ((k, e) in b.orthonormal.withIndex()) {
                val length = LinAlg.norm(b.orthogonal[k])
                step("${vec("e", k + 1)} = \\frac{${vec("u", k + 1)}}{\\left|${vec("u", k + 1)}\\right|} = \\frac{1}{${l(length)}}" + l(b.orthogonal[k]) +
                    " = " + l(e), StepKey.ORTHO_NORMALIZE)
            }
            if (b.orthonormal.isNotEmpty()) step("\\vec{e}_{i}\\cdot\\vec{e}_{j} = \\begin{cases}1 & i = j\\\\0 & i \\neq j\\end{cases}", StepKey.ORTHO_CHECK)
        }

        /** − c₁·u₁ − c₂·u₂ …: the projections of [v] onto the vectors before, with their coefficients (v·uᵢ)/(uᵢ·uᵢ). */
        fun projections(v: Expr.Mat, us: List<Expr.Mat>): String = us.withIndex().joinToString("") { (i, u) ->
            val coefficient = Calculus.tidy(LinAlg.dot(v, u) / LinAlg.dot(u, u))
            when {
                isZero(coefficient) -> " - 0\\,${vec("u", i + 1)}"
                Write.isNegative(coefficient) -> " + " + coefficientLatex(Calculus.tidy(neg(coefficient))) + vec("u", i + 1)
                else -> " - " + coefficientLatex(coefficient) + vec("u", i + 1)
            }
        }

        fun coefficientLatex(e: Expr) = if (isOne(e)) "" else l(e).let { if (it.contains("+") || it.contains(" - ")) "\\left($it\\right)" else it } + "\\,"

        fun projection(p: Expr.Mat) {
            val vectors = lines().map { it.single() as Expr.Mat }
            val v = vectors[0]
            val onto = vectors.drop(1)
            step("\\vec{v} = " + l(v) + ",\\ " + onto.indices.joinToString(",\\ ") { (if (onto.size == 1) "\\vec{u}" else vec("u", it + 1)) + " = " + l(onto[it]) },
                StepKey.START)
            val basis = if (onto.size == 1) onto else Orthogonal.gramSchmidt(onto).orthogonal
            val names = if (onto.size == 1) listOf("\\vec{u}") else basis.indices.map { vec("w", it + 1) }
            if (onto.size > 1) step(basis.indices.joinToString(",\\ ") { names[it] + " = " + l(basis[it]) }, StepKey.PROJ_BASIS)
            val coefficients = basis.map { w -> Calculus.tidy(LinAlg.dot(v, w) / LinAlg.dot(w, w)) }
            for ((k, w) in basis.withIndex()) {
                // The quotient of the two dot products, and the number it is where that is written otherwise.
                val quotient = "\\frac{${l(LinAlg.dot(v, w))}}{${l(LinAlg.dot(w, w))}}"
                val shown = if (quotient == l(coefficients[k])) quotient else quotient + " = " + l(coefficients[k])
                step("\\frac{\\vec{v}\\cdot ${names[k]}}{${names[k]}\\cdot ${names[k]}} = " + shown, StepKey.PROJ_COEFFICIENT, names[k])
            }
            step("\\vec{p} = " + basis.indices.joinToString(" + ") { coefficientLatex(coefficients[it]) + names[it] } + " = " + l(p), StepKey.RESULT)
            val rest = LinAlg.column(v.rows.indices.map { Calculus.tidy(v.rows[it][0] - p.rows[it][0]) })
            val onEach = onto.indices.joinToString(",\\ ") { "\\left(\\vec{v} - \\vec{p}\\right)\\cdot " + (if (onto.size == 1) "\\vec{u}" else vec("u", it + 1)) + " = 0" }
            step("\\vec{v} - \\vec{p} = " + l(rest) + ",\\ " + onEach, StepKey.PROJ_REST)
        }

        // --------------------------------------------------------------------------------------------------------------
        // Norms and condition numbers.

        fun p(): String = c.choice?.point ?: "2"

        fun index(p: String) = if (p == "\\infty") "\\infty" else p

        fun value(o: Outcome.Value): String = o.exact?.let { eqv(it) } ?: ("= " + c.latex(o.value))

        fun norm(o: Outcome.Value) {
            val m = lines().single().single() as Expr.Mat
            val p = p()
            if (m.columns == 1) {
                val entries = m.rows.map { it[0] }
                step("\\vec{v} = " + l(m), StepKey.START)
                val abs = entries.joinToString(" + ") { "\\left|${l(it)}\\right|" }
                val shown = when (p) {
                    "1" -> abs
                    "2", "F" -> "\\sqrt{" + entries.joinToString(" + ") { "${paren(it)}^{2}" } + "}"
                    "\\infty" -> "\\max\\left(" + entries.joinToString(", ") { "\\left|${l(it)}\\right|" } + "\\right)"
                    else -> "\\left(" + entries.joinToString(" + ") { "\\left|${l(it)}\\right|^{$p}" } + "\\right)^{1/$p}"
                }
                step("\\lVert\\vec{v}\\rVert_{${index(p)}} = $shown " + value(o), StepKey.NORM_VECTOR)
                return
            }
            step("A = " + l(m), StepKey.START)
            normOf(m, "A", p, o)
        }

        fun paren(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" ") || it.contains("\\frac")) "\\left($it\\right)" else it }

        /** The steps of ‖[m]‖ in [p], named [name]; its value from [o] where it is the card's answer. */
        fun normOf(m: Expr.Mat, name: String, p: String, o: Outcome.Value?) {
            val norm = "\\lVert $name\\rVert_{${index(p)}}"
            when (p) {
                "1" -> {
                    val sums = (0 until m.columns).map { j -> Calculus.tidy(add(m.rows.map { Calculus.tidy(fn(Func.ABS, it[j])) })) }
                    step(sums.indices.joinToString(",\\ ") { "\\sum_{i} \\left|${name.lowercase()}_{i${it + 1}}\\right| = " + l(sums[it]) }, StepKey.NORM_COLUMNS)
                    step("$norm = " + (o?.let { value(it).removePrefix("= ") } ?: l(Norms.exactNorm(m, p))), StepKey.RESULT)
                }
                "\\infty" -> {
                    val sums = m.rows.map { r -> Calculus.tidy(add(r.map { Calculus.tidy(fn(Func.ABS, it)) })) }
                    step(sums.indices.joinToString(",\\ ") { "\\sum_{j} \\left|${name.lowercase()}_{${it + 1}j}\\right| = " + l(sums[it]) }, StepKey.NORM_ROWS)
                    step("$norm = " + (o?.let { value(it).removePrefix("= ") } ?: l(Norms.exactNorm(m, p))), StepKey.RESULT)
                }
                "F" -> step("$norm = \\sqrt{" + m.rows.flatten().joinToString(" + ") { "${paren(it)}^{2}" } + "} " +
                    (o?.let { value(it) } ?: eqv(Norms.exactNorm(m, p))), StepKey.NORM_FROBENIUS)
                else -> {
                    step("A^{T}A = " + l(LinAlg.times(LinAlg.transpose(m), m)), StepKey.NORM_GRAM)
                    step("$norm = \\sqrt{\\lambda_{\\max}\\left(A^{T}A\\right)} " + (o?.let { value(it) } ?: ""), StepKey.NORM_SPECTRAL)
                }
            }
        }

        fun condition(o: Outcome.Value) {
            val m = lines().single().single() as Expr.Mat
            val p = p()
            step("A = " + l(m), StepKey.START)
            if (p == "2") {
                step("A^{T}A = " + l(LinAlg.times(LinAlg.transpose(m), m)), StepKey.NORM_GRAM)
                step("\\operatorname{cond}_{2}(A) = \\frac{\\sigma_{\\max}}{\\sigma_{\\min}} = \\sqrt{\\frac{\\lambda_{\\max}\\left(A^{T}A\\right)}{\\lambda_{\\min}\\left(A^{T}A\\right)}} " +
                    value(o), StepKey.COND_SINGULAR)
                return
            }
            val inverse = LinAlg.inverse(m)
            step("A^{-1} = " + l(inverse), StepKey.COND_INVERSE)
            val a = Norms.exactNorm(m, p)
            val b = Norms.exactNorm(inverse, p)
            normOf(m, "A", p, null)
            normOf(inverse, "A^{-1}", p, null)
            step("\\operatorname{cond}_{${index(p)}}(A) = \\lVert A\\rVert_{${index(p)}}\\,\\lVert A^{-1}\\rVert_{${index(p)}} = " + paren(a) + "\\cdot " + paren(b) +
                " " + value(o), StepKey.COND_PRODUCT)
        }

        // --------------------------------------------------------------------------------------------------------------
        // Iterations.

        fun fixedPoint(it: Iterative.Iteration) {
            val sides = lines().single()
            val x = Expr.Sym(it.letters[0])
            val g = when {
                sides.size == 1 -> sides[0]
                sides[0] == x -> sides[1]
                else -> sides[0]
            }
            val name = Format.name(x.name, latex = true)
            step("$name = g($name),\\ g($name) = " + l(g), StepKey.START)
            step("g'($name) = " + l(Calculus.tidy(Calculus.derivative(g, x))), StepKey.FP_DERIVATIVE)
            for (t in it.tests) {
                val s = t as? Iterative.Test.Slope ?: continue
                val at = v(s.at).let { a -> if (a.startsWith("\\approx")) a else "= $a" }
                val size = s.exact?.let { e -> v(e) } ?: s.digits?.let { "\\approx " + decimal(it) } ?: continue
                val (key, rel) = when (s.kind) {
                    Iterative.Kind.ATTRACTING -> StepKey.FP_ATTRACTING to "< 1"
                    Iterative.Kind.REPELLING -> StepKey.FP_REPELLING to "> 1"
                    // |g′(x*)| = 1 shown exactly is said once.
                    Iterative.Kind.NEUTRAL -> StepKey.FP_NEUTRAL to (if (s.exact != null) "" else "= 1")
                }
                val sizeShown = if (size.startsWith("\\approx")) size else "= $size"
                step("$name^{*} $at:\\ \\left|g'($name^{*})\\right| $sizeShown $rel".trimEnd(), key, "$name^{*} $at")
            }
            it.result?.let { r -> step("$name^{*} " + v(Expr.Real(r[0])).let { a -> if (a.startsWith("\\approx")) a else "\\approx $a" }, StepKey.RESULT) }
        }

        fun linear(it: Iterative.Iteration) {
            val (a, b) = system() ?: return
            val n = a.size
            val names = it.letters.map { Format.name(it, latex = true) }
            step("A = " + l(Expr.Mat(a.map { r -> r.map { num(it) } })) + ",\\ \\vec{b} = " + l(LinAlg.column(b.map { num(it) })), StepKey.START)
            val gaussSeidel = c.mode == Mode.GAUSS_SEIDEL
            for (i in 0 until n) {
                val terms = StringBuilder()
                if (b[i].signum != 0 || (0 until n).all { j -> j == i || a[i][j].signum == 0 }) terms.append(l(num(b[i])))
                for (j in 0 until n) {
                    if (j == i || a[i][j].signum == 0) continue
                    val k = if (gaussSeidel && j < i) "(k+1)" else "(k)"
                    val coefficient = a[i][j].abs()
                    val sign = if (a[i][j].signum > 0) " - " else " + "
                    if (terms.isEmpty() && sign == " - ") terms.append("-") else if (terms.isNotEmpty()) terms.append(sign)
                    if (coefficient != Rational.ONE) terms.append(l(num(coefficient)))
                    terms.append("${names[j]}^{$k}")
                }
                val shown = if (a[i][i] == Rational.ONE) terms.toString() else "\\frac{$terms}{${l(num(a[i][i]))}}"
                step("${names[i]}^{(k+1)} = $shown", if (gaussSeidel) StepKey.SEIDEL_ROW else StepKey.JACOBI_ROW, (i + 1).toString(), names[i])
            }
            for (t in it.tests) when (t) {
                is Iterative.Test.Dominance -> if (t.holds) {
                    step(a.indices.joinToString(",\\ ") { i -> "${abs(a[i][i])} > " + others(a, i) }, StepKey.ITER_DOMINANT)
                } else {
                    val i = t.row!!
                    step("${abs(a[i][i])} \\le " + others(a, i), StepKey.ITER_NOT_DOMINANT, (i + 1).toString())
                }
                is Iterative.Test.Radius -> {
                    val rho = (t.exact?.let { e -> eqv(e) } ?: ("\\approx " + decimal(t.digits)))
                    step("T = " + l(t.matrix) + ",\\ \\rho(T) $rho " + (if (t.below) "< 1" else "\\ge 1"),
                        if (t.below) StepKey.ITER_RADIUS_BELOW else StepKey.ITER_RADIUS_ABOVE)
                }
                else -> {}
            }
            it.exact?.let { e -> step("\\vec{x}^{*} = A^{-1}\\vec{b} = " + l(LinAlg.column(e)), StepKey.ITER_EXACT) }
            it.result?.let { r -> step("\\vec{x} \\approx \\left(" + r.joinToString(", ") { d -> Format.decimal(BigDecimal(d), c, latex = true) } + "\\right)", StepKey.RESULT) }
        }

        fun abs(r: Rational) = "\\left|${l(num(r))}\\right|"

        fun others(a: List<List<Rational>>, i: Int) = a.indices.filter { it != i }.joinToString(" + ") { abs(a[i][it]) }

        /** A and b: from the matrix and the choice's vector, or from the equations. */
        fun system(): Pair<List<List<Rational>>, List<Rational>>? {
            val lines = lines()
            val first = lines[0].singleOrNull()
            if (lines.size == 1 && first is Expr.Mat) {
                val bLatex = c.choice?.point?.split(';')?.firstOrNull() ?: return null
                val bv = Iterative.vectorOf(bLatex) ?: return null
                return first.rows.map { r -> r.map { rational(it) ?: return null } } to bv.rows.map { rational(it[0]) ?: return null }
            }
            val equations = lines.map { s -> Calculus.tidy(s[0] - s[1]) }
            val letters = equations.flatMap { it.symbols() }.toSortedSet().toList()
            val rows = equations.map { e ->
                val zero = letters.fold(e) { acc, x -> acc.substitute(Expr.Sym(x), Expr.ZERO) }
                letters.map { x -> rational(Calculus.tidy(Calculus.derivative(e, Expr.Sym(x)))) ?: return null } to -(rational(Calculus.tidy(zero)) ?: return null)
            }
            return rows.map { it.first } to rows.map { it.second }
        }

        /** A certified decimal in LaTeX: its digits as the formula writes numbers. */
        fun decimal(d: Decimal): String = when (d) {
            Decimal.Zero -> "0"
            is Decimal.Digits -> {
                var x = BigDecimal(BigInteger(d.digits), d.digits.length - 1 - d.exponent)
                if (d.negative) x = x.negate()
                Format.decimal(x, c, latex = true, exact = true)
            }
            // Never made by the iterations' tests, whose digits are certified: not shown.
            is Decimal.Small -> throw CalcException(Failure.UNSOLVED)
        }
    }
}
