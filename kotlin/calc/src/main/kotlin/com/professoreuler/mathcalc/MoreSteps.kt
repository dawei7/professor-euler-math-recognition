package com.professoreuler.mathcalc

import java.math.BigInteger

/**
 * Worked steps for the Calculator's later areas, from what their outcomes hold: eigenvalues, determinants and inverses,
 * recurrences, congruences, whole-number equations, triangles, statistics, the regression line, units and conics. Each step says
 * what was done and shows the formula after it; the last is the answer the card shows.
 */
internal object MoreSteps {
    private val TOKEN = MathCalc.TOKEN

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

        /** A formula's part in brackets where it is a sum or negative. */
        fun p(e: Expr) = l(e).let { if (it.startsWith("-") || it.contains(" + ") || it.contains(" - ")) "\\left($it\\right)" else it }
        fun step(shown: String, key: StepKey, vararg args: String) {
            out += Step(Steps.tidy(shown), StepText(key, args.map { Steps.tidy(it) }))
        }

        /** The formula's first side, its calculus worked out: the matrix of a matrix task, or null. */
        fun matrix(): Expr.Mat? = try {
            val statements = Parser(TOKEN.findAll(latex).map { it.value }.toList(), Flags(assumptions)).statements()
            statements.singleOrNull()?.sides?.filterNotNull()?.firstOrNull()?.let { Calculus.evaluate(it) } as? Expr.Mat
        } catch (e: CalcException) {
            null
        }

        fun build(): List<Step>? {
            when (val o = c.outcome) {
                is Outcome.Eigenvalues -> eigen(o)
                is Outcome.Value, is Outcome.Expression -> when (c.mode) {
                    Mode.DETERMINANT -> determinant()
                    Mode.INVERSE -> inverse(o as? Outcome.Expression ?: return null)
                    else -> return null
                }
                is Outcome.RecurrenceSolved -> recurrence(o.recurrence)
                is Outcome.Congruence -> congruence(o.solution)
                is Outcome.WholeSolutions -> whole(o.solution)
                is Outcome.Triangles -> o.triangles.forEach { triangle(it, o.triangles.size > 1) }
                is Outcome.Statistics -> statistics(o)
                is Outcome.Regression -> regression(o)
                is Outcome.Converted -> units(o)
                is Outcome.ConicSection -> conic(o.conic)
                else -> return null
            }
            return out
        }

        fun eigen(o: Outcome.Eigenvalues) {
            val m = matrix() ?: return
            step("A = " + l(m), StepKey.START)
            // The characteristic polynomial is monic, det(λI − A); the step (and its sentence) is det(A − λI), which for odd n is
            // its negative: shown as what it says.
            val det = if (m.size % 2 == 1) Calculus.tidy(Algebra.expand(neg(o.characteristic))) else o.characteristic
            step("\\det(A - \\lambda I) = " + l(det) + " = 0", StepKey.CHARACTERISTIC)
            step(o.values.joinToString(",\\ ") { "\\lambda = " + v(it.value) + if (it.multiplicity > 1) "\\ (${it.multiplicity}\\times)" else "" },
                StepKey.EIGENVALUES)
            for (e in o.values) for (vec in e.vectors) step("\\vec{v} = " + l(vec), StepKey.EIGENVECTOR, "\\lambda = " + v(e.value))
        }

        fun determinant() {
            val m = matrix() ?: return
            if (m.size != m.columns || m.size !in 2..3) return
            val r = m.rows
            step("A = " + l(m), StepKey.START)
            if (m.size == 2) {
                step("\\det A = ${p(r[0][0])}\\cdot ${p(r[1][1])} - ${p(r[0][1])}\\cdot ${p(r[1][0])}", StepKey.DET_2X2)
            } else {
                val minors = (0..2).map { j -> LinAlg.det(Expr.Mat(r.drop(1).map { row -> row.filterIndexed { k, _ -> k != j } })) }
                step("\\det A = ${p(r[0][0])}\\cdot ${p(minors[0])} - ${p(r[0][1])}\\cdot ${p(minors[1])} + ${p(r[0][2])}\\cdot ${p(minors[2])}",
                    StepKey.DET_EXPAND)
            }
            step("\\det A = " + v(LinAlg.det(m)), StepKey.RESULT)
        }

        fun inverse(o: Outcome.Expression) {
            val m = matrix() ?: return
            val inv = o.result as? Expr.Mat ?: return
            step("A = " + l(m), StepKey.START)
            if (m.size == 2 && m.columns == 2) {
                val r = m.rows
                val adj = Expr.Mat(listOf(listOf(r[1][1], neg(r[0][1])), listOf(neg(r[1][0]), r[0][0])).map { row -> row.map { Calculus.tidy(it) } })
                step("A^{-1} = \\frac{1}{${l(LinAlg.det(m))}}" + l(adj), StepKey.INVERSE_2X2)
            } else {
                step("\\left(A \\mid I\\right) \\sim \\left(I \\mid A^{-1}\\right)", StepKey.INVERSE_GAUSS_JORDAN)
            }
            step("A^{-1} = " + l(inv), StepKey.RESULT)
            // Multiplied back: A·A⁻¹ is the identity.
            val product = LinAlg.times(m, inv) as? Expr.Mat ?: return
            val identity = product.rows.withIndex().all { (i, row) ->
                row.withIndex().all { (j, x) -> LinAlg.isNull(Calculus.tidy(x - if (i == j) Expr.ONE else Expr.ZERO)) }
            }
            if (identity) step("A\\,A^{-1} = I", StepKey.INVERSE_CHECK)
        }

        fun recurrence(r: Discrete.Recurrence) {
            val p = r.characteristic ?: return
            val a = r.name
            step(l(p) + " = 0", StepKey.REC_CHARACTERISTIC)
            step(r.roots.joinToString(",\\ ") { (rho, k) -> "r = " + v(rho) + if (k > 1) "\\ (${k}\\times)" else "" }, StepKey.REC_ROOTS)
            val general = r.generalForm ?: return
            step("${a}_{n} = " + l(general), StepKey.REC_GENERAL)
            if (!isZero(r.particular)) step("${a}_{n}^{(p)} = " + l(r.particular), StepKey.REC_PARTICULAR)
            if (r.constants.isNotEmpty()) {
                step(r.constants.entries.sortedBy { it.key }.joinToString(",\\ ") { (k, x) -> Format.name(k, latex = true) + " = " + v(x) }, StepKey.REC_CONSTANTS)
                step("${a}_{n} = " + l(r.closed), StepKey.RESULT)
            }
        }

        fun congruence(k: Discrete.Congruences) {
            val x = Expr.Sym(k.letter)
            val name = c.latexName(k.letter)
            for (part in k.parts) {
                val poly = Calculus.tidy(add(part.coefficients.mapIndexed { j, a -> mul(num(Rational.of(a)), pow(x, num(j.toLong()))) }))
                step(l(poly) + " \\equiv 0 \\pmod{${part.m}}", StepKey.START)
                val degree = part.coefficients.indexOfLast { it.mod(part.m).signum() != 0 }
                if (degree == 1) {
                    val g = part.coefficients[1].mod(part.m).gcd(part.m)
                    step("\\gcd(${part.coefficients[1].mod(part.m)},\\ ${part.m}) = $g", StepKey.CONG_GCD)
                } else if (degree > 1) {
                    step(l(poly) + " \\equiv 0 \\pmod{${part.m}}", StepKey.CONG_TRIED, part.m.toString())
                }
                if (part.residues.isEmpty()) {
                    step("$name \\in \\emptyset", StepKey.NO_SOLUTION)
                    return
                }
                step("$name \\equiv " + part.residues.joinToString(",\\ ") + " \\pmod{${part.step}}", StepKey.CONG_LINE)
            }
            if (k.residues.isEmpty()) return
            step("$name \\equiv " + k.residues.joinToString(",\\ ") + " \\pmod{${k.modulus}}", if (k.parts.size > 1) StepKey.CRT else StepKey.RESULT)
        }

        fun whole(s: Discrete.Integers) {
            when (s) {
                is Discrete.Integers.Linear -> {
                    if (s.a.signum() == 0 && s.b.signum() == 0) return
                    val x = c.latexName(s.x)
                    val y = c.latexName(s.y)
                    val equation = Calculus.tidy(add(mul(num(Rational.of(s.a)), Expr.Sym(s.x)), mul(num(Rational.of(s.b)), Expr.Sym(s.y))))
                    step(l(equation) + " = ${s.c}", StepKey.START)
                    step("\\gcd(${s.a},\\ ${s.b}) = ${s.a.gcd(s.b)}", StepKey.DIO_GCD)
                    step("$x = ${s.x0},\\ $y = ${s.y0}", StepKey.DIO_PARTICULAR)
                    step("$x = ${term(s.x0, s.sx)},\\ $y = ${term(s.y0, s.sy)}", StepKey.DIO_FAMILY)
                }
                is Discrete.Integers.Pell -> {
                    val x = c.latexName(s.x)
                    val y = c.latexName(s.y)
                    step("$x^{2} - ${s.d}$y^{2} = 1", StepKey.START)
                    step("$x = ${s.x1},\\ $y = ${s.y1}", StepKey.PELL)
                    step("${x}_{k} + ${y}_{k}\\sqrt{${s.d}} = \\left(${s.x1} + ${s.y1}\\sqrt{${s.d}}\\right)^{k}", StepKey.PELL_POWERS)
                }
                Discrete.Integers.None -> return
            }
        }

        fun term(a: BigInteger, s: BigInteger): String = when {
            s.signum() == 0 -> a.toString()
            a.signum() == 0 -> "${s}t"
            s.signum() < 0 -> "$a - ${s.negate()}t"
            else -> "$a + ${s}t"
        }.replace(" 1t", " t").replace(Regex("^1t"), "t").replace("-1t", "-t")

        fun triangle(t: Geometry.Triangle, several: Boolean) {
            val side = listOf("a", "b", "c")
            val angle = listOf("\\alpha", "\\beta", "\\gamma")
            fun deg(e: Expr) = l(e) + "^{\\circ}"
            if (out.isEmpty()) step((t.given.sorted().joinToString(",\\ ") { k ->
                if (k < 3) "${side[k]} = ${l(t.sides[k])}" else "${angle[k - 3]} = ${deg(t.angles[k - 3])}"
            }), StepKey.START)
            val s = t.sides
            val a = t.angles
            val stated = t.given.filter { it >= 3 }.map { it - 3 }.toMutableSet()
            fun cosine(k: Int) {
                val (i, j) = (0..2).filter { it != k }
                step("\\cos ${angle[k]} = \\frac{${p(s[i])}^{2} + ${p(s[j])}^{2} - ${p(s[k])}^{2}}{2\\cdot ${p(s[i])}\\cdot ${p(s[j])}},\\quad ${angle[k]} = ${deg(a[k])}",
                    StepKey.TRI_COSINES)
                stated += k
            }
            fun sine(m: Int, from: Int) = step("${side[m]} = \\frac{${p(s[from])}\\sin ${deg(a[m])}}{\\sin ${deg(a[from])}} ${eqv(s[m])}", StepKey.TRI_SINES)
            when (t.case) {
                Geometry.Case.SSS -> { cosine(0); cosine(1) }
                Geometry.Case.SAS -> {
                    val k = stated.single()
                    val (i, j) = (0..2).filter { it != k }
                    step("${side[k]}^{2} = ${p(s[i])}^{2} + ${p(s[j])}^{2} - 2\\cdot ${p(s[i])}\\cdot ${p(s[j])}\\cos ${deg(a[k])},\\quad ${side[k]} ${eqv(s[k])}",
                        StepKey.TRI_COSINES)
                    cosine(i)
                }
                Geometry.Case.ASA -> {
                    val k = (0..2).first { it !in stated }
                    step("${angle[k]} = 180^{\\circ} - " + (0..2).filter { it != k }.joinToString(" - ") { deg(a[it]) } + " = ${deg(a[k])}", StepKey.TRI_ANGLE_SUM)
                    stated += k
                    val known = (0..2).first { it in t.given }
                    for (m in (0..2).filter { it != known }) sine(m, known)
                }
                Geometry.Case.SSA -> {
                    val k = stated.single()
                    val j = (0..2).first { it != k && it in t.given }
                    step("\\sin ${angle[j]} = \\frac{${p(s[j])}\\sin ${deg(a[k])}}{${p(s[k])}},\\quad ${angle[j]} = ${deg(a[j])}", StepKey.TRI_SINES)
                    stated += j
                    if (several) step("${angle[j]} = ${deg(a[j])}", StepKey.TRI_AMBIGUOUS)
                }
            }
            // The last angle from the sum, and an SSA triangle's last side by the law of sines.
            (0..2).firstOrNull { it !in stated }?.let { rest ->
                step("${angle[rest]} = 180^{\\circ} - " + (0..2).filter { it != rest }.joinToString(" - ") { deg(a[it]) } + " = ${deg(a[rest])}",
                    StepKey.TRI_ANGLE_SUM)
            }
            if (t.case == Geometry.Case.SSA) {
                val k = t.given.first { it >= 3 } - 3
                val m = (0..2).first { it !in t.given }
                sine(m, k)
            }
            step("A = \\tfrac{1}{2}\\cdot ${p(s[1])}\\cdot ${p(s[2])}\\cdot\\sin ${deg(a[0])} ${eqv(t.area)}", StepKey.TRI_AREA)
        }

        fun statistics(o: Outcome.Statistics) {
            val s = o.summary
            step(o.values.joinToString(",\\ ") { l(it) }, StepKey.START)
            step("\\bar{x} = \\frac{${l(s.sum)}}{${s.n}} = ${v(s.mean)}", StepKey.STAT_MEAN)
            val sorted = o.values.sortedBy { Numeric.value(it) ?: 0.0 }
            step(sorted.joinToString(" \\le ") { l(it) }, StepKey.STAT_SORTED)
            step("\\tilde{x} = ${v(s.median)}", StepKey.STAT_MEDIAN)
            val sv = s.sampleVariance ?: return
            val squares = Calculus.tidy(mul(sv, num((s.n - 1).toLong())))
            val fraction = "\\frac{${l(squares)}}{${s.n - 1}}"
            val value = v(sv)
            step("s^{2} = \\frac{\\sum (x_i - \\bar{x})^{2}}{n - 1} = $fraction " + if (value.startsWith(fraction)) value.removePrefix(fraction).trim().ifEmpty { "" } else "= $value",
                StepKey.STAT_VARIANCE)
            s.sampleSd?.let { step("s = \\sqrt{${l(sv)}} = ${v(it)}", StepKey.STAT_SD) }
        }

        fun regression(o: Outcome.Regression) {
            val n = o.xs.size
            fun sum(f: (Int) -> Expr) = Calculus.tidy(add((0 until n).map(f)))
            val sx = sum { o.xs[it] }
            val sy = sum { o.ys[it] }
            val sxy = sum { mul(o.xs[it], o.ys[it]) }
            val sxx = sum { pow(o.xs[it], Expr.TWO) }
            step("\\sum x = ${l(sx)},\\ \\sum y = ${l(sy)},\\ \\sum xy = ${l(sxy)},\\ \\sum x^{2} = ${l(sxx)},\\ n = $n", StepKey.REG_SUMS)
            step("a = \\frac{$n\\cdot ${l(sxy)} - ${l(sx)}\\cdot ${l(sy)}}{$n\\cdot ${l(sxx)} - ${l(sx)}^{2}} = ${v(o.line.slope)}", StepKey.REG_SLOPE)
            step("b = \\frac{${l(sy)}}{$n} - ${l(o.line.slope)}\\cdot\\frac{${l(sx)}}{$n} = ${v(o.line.intercept)}", StepKey.REG_INTERCEPT)
            step("y = ${l(o.line.slope)}\\,x + ${l(o.line.intercept)}".replace("+ -", "- "), StepKey.RESULT)
        }

        fun units(o: Outcome.Converted) {
            val a = Units.unit(o.from) ?: return
            val b = Units.unit(o.to) ?: return
            fun u(s: String) = "\\,\\mathrm{" + s.replace("°", "{}^{\\circ}").replace("µ", "\\mu ").replace("Ω", "\\Omega") + "}"
            if (a.offset != null || b.offset != null) {
                val kelvin = Calculus.tidy(if (a.offset != null) add(mul(o.value, a.factor), a.offset) else mul(o.value, a.factor))
                step("${l(o.value)}${u(o.from)} = ${v(kelvin)}\\,\\mathrm{K}", StepKey.UNIT_OFFSET)
            } else {
                val ratio = Calculus.tidy(a.factor / b.factor)
                step("1${u(o.from)} = ${v(ratio)}${u(o.to)}", StepKey.UNIT_FACTOR)
            }
            step("${l(o.value)}${u(o.from)} = ${v(o.result)}${u(o.to)}", StepKey.RESULT)
        }

        fun conic(k: Geometry.Conic) {
            val (lhs, rhs) = k.standard ?: return
            val f = try {
                val s = Parser(TOKEN.findAll(latex).map { it.value }.toList(), Flags(assumptions)).statements().single().sides.filterNotNull()
                Calculus.tidy(Calculus.evaluate(s[0]) - Calculus.evaluate(s[1]))
            } catch (e: RuntimeException) {
                return
            }
            if (k.kind == Geometry.ConicKind.PARABOLA) {
                step(l(f) + " = 0", StepKey.START)
                step(l(lhs) + " = " + l(rhs), StepKey.CONIC_STANDARD)
                return
            }
            // Ax² + Cy² + Dx + Ey + F = 0 with its squares completed: A(x − h)² + C(y − k)² = R.
            val q = Geometry.quadratic(f) ?: return
            val centre = k.centre ?: return
            val x = Expr.Sym("x")
            val y = Expr.Sym("y")
            val left = add(mul(num(q[0]), pow(add(x, neg(centre.x)), Expr.TWO)), mul(num(q[2]), pow(add(y, neg(centre.y)), Expr.TWO)))
            val right = Calculus.tidy(add(mul(num(q[0]), pow(centre.x, Expr.TWO)), mul(num(q[2]), pow(centre.y, Expr.TWO)), neg(num(q[5]))))
            step(l(f) + " = 0", StepKey.START)
            if (q[3].signum != 0 || q[4].signum != 0) step(l(left) + " = " + l(right), StepKey.CONIC_COMPLETE)
            step(l(lhs) + " = " + l(rhs), StepKey.CONIC_STANDARD)
        }
    }
}
