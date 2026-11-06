package com.professoreuler.mathcalc

import kotlin.math.abs

/** One step of a worked solution: the formula after it, in LaTeX as the card writes it, and why it follows. */
data class Step(val latex: String, val why: StepText) {
    /** What the step says, so that tests can check it follows from the one before. */
    internal var form: StepForm? = null

    /** The step may bring in solutions the one before does not have (squaring, multiplying by an expression of x). */
    internal var widens: Boolean = false

    /** The step drops values the one before has, each checked in the original and found not to solve it. */
    internal var narrows: Boolean = false
}

/** Why a step follows: [key] picks the sentence the apps show, [args] are LaTeX formulas put into it. */
data class StepText(val key: StepKey, val args: List<String> = emptyList())

/** The sentences of the steps. The apps put each in words of their own; [args] fill its placeholders in order. */
enum class StepKey {
    // The start and the end.
    START, RESULT, SOLUTION, SOLUTIONS, SOLUTIONS_PERIODIC, NO_SOLUTION, NO_REAL_SOLUTION, ALWAYS,
    // Doing the same to both sides.
    EXPAND_SIDES, COLLECT, SUBTRACT_BOTH, ADD_BOTH, DIVIDE_BOTH, MULTIPLY_BOTH, SWAP_SIDES, BRING_TERMS, TAKE_OUT_LETTER, ISOLATE,
    RECIPROCAL_BOTH, ROOT_BOTH, SQUARE_ROOT_BOTH, RAISE_BOTH,
    // Quadratic and higher equations.
    STANDARD_FORM, FACTOR_SIDE, ZERO_PRODUCT, QUADRATIC_FORMULA, DISCRIMINANT, D_POSITIVE, D_ZERO, D_NEGATIVE,
    // Fractions, roots, absolute values.
    DOMAIN, MULTIPLY_DENOMINATOR, EXCLUDED, ISOLATE_ROOT, SQUARE_BOTH, CHECK, FALSE_SOLUTION, ABS_CASES, ABS_NEGATIVE,
    // Exponentials, logarithms, trigonometry.
    TAKE_LOG, TAKE_LN, EXPONENTIATE, COMBINE_LOGS, ARGUMENTS_EQUAL, EXPONENTS_EQUAL, SUBSTITUTE_U, BACK_SUBSTITUTE,
    INVERSE_TRIG, TRIG_RANGE,
    // Inequalities.
    FLIP, CRITICAL_POINTS, SIGN_CHART, SOLUTION_SET, ABS_LESS, ABS_GREATER,
    // Systems.
    SYSTEM, SOLVE_FOR_LETTER, SUBSTITUTE_INTO, BACK_SUBSTITUTE_VALUE,
    // Multiplying out and factoring.
    BINOMIAL_1, BINOMIAL_2, BINOMIAL_3, MULTIPLY_PAIR, MULTIPLY_OUT, COMMON_FACTOR, FACTOR_BINOMIAL_1, FACTOR_BINOMIAL_2, FACTOR_BINOMIAL_3,
    TRINOMIAL, ROOTS_FACTOR, GROUPING, RATIONAL_ROOT, POLYNOMIAL_DIVISION, IRREDUCIBLE, PRIME_DIVIDE,
    // Fractions with letters.
    COMMON_DENOMINATOR, FACTOR_PARTS, CANCEL,
    // Derivatives.
    SUM_RULE, CONSTANT_FACTOR, POWER_RULE, PRODUCT_RULE, QUOTIENT_RULE, CHAIN_RULE, TABLE_DERIVATIVE, SIMPLIFY, DERIVATIVE_AGAIN,
    // Integrals.
    INTEGRAL_LINEARITY, INTEGRAL_POWER, INTEGRAL_TABLE, INTEGRAL_SUBSTITUTION, INTEGRAL_PARTS, PARTIAL_FRACTIONS, PLUS_C,
    DEFINITE_BOUNDS,
    // Limits.
    LIMIT_SUBSTITUTE, LIMIT_INDETERMINATE, LIMIT_CANCEL, LHOPITAL, LIMIT_HIGHEST_POWER,
    // Comparing sides.
    SIDES_EQUAL, SIDES_DIFFER,
    // Matrices.
    CHARACTERISTIC, EIGENVALUES, EIGENVECTOR, DET_2X2, DET_EXPAND, INVERSE_2X2, INVERSE_GAUSS_JORDAN, INVERSE_CHECK,
    // Recurrences, congruences, whole numbers.
    REC_CHARACTERISTIC, REC_ROOTS, REC_GENERAL, REC_PARTICULAR, REC_CONSTANTS, CONG_GCD, CONG_LINE, CONG_TRIED, CRT,
    DIO_GCD, DIO_PARTICULAR, DIO_FAMILY, PELL, PELL_POWERS,
    // Triangles, statistics, the regression line, units, conics.
    TRI_COSINES, TRI_SINES, TRI_ANGLE_SUM, TRI_AMBIGUOUS, TRI_AREA, STAT_MEAN, STAT_SORTED, STAT_MEDIAN, STAT_VARIANCE, STAT_SD,
    REG_SUMS, REG_SLOPE, REG_INTERCEPT, UNIT_FACTOR, UNIT_OFFSET, CONIC_COMPLETE, CONIC_STANDARD,
    // Physical constants: the published values put in, and the bound of the result's uncertainty.
    CONSTANT_VALUES, UNCERTAINTY_BOUND,
    // The singular value decomposition, the Jordan form.
    SVD_GRAM, SVD_VALUES, SVD_V, SVD_U, SVD_CHECK, JORDAN_RANKS, JORDAN_CHAIN, JORDAN_FORM,
    // Systems of linear differential equations, boundary value problems, power series solutions.
    SYS_MATRIX, SYS_GENERAL, SYS_CONSTANTS, BVP_GENERAL, BVP_SYSTEM, BVP_UNIQUE, BVP_NONE, BVP_FAMILY, SERIES_ANSATZ,
    SERIES_RECURRENCE, SERIES_COEFFICIENTS,
    // Interpolation and splines; orders, primitive roots and discrete logarithms.
    INTERP_DIFFERENCES, INTERP_NEWTON, INTERP_CHECK, SPLINE_WIDTHS, SPLINE_SYSTEM, SPLINE_MOMENTS, SPLINE_PIECE,
    ORDER_PHI, ORDER_TEST, PRIMROOT_REJECT, PRIMROOT_ACCEPT, DLOG_ORDER, DLOG_PART,
    // Partial differential equations: the type and the canonical form; separation of variables.
    PDE_COEFFICIENTS, PDE_DISCRIMINANT, PDE_HYPERBOLIC, PDE_PARABOLIC, PDE_ELLIPTIC, PDE_CHARACTERISTIC, PDE_NEW_VARIABLES, PDE_CANONICAL,
    PDE_SEPARATE, PDE_EIGENVALUES, PDE_OTHER_FACTOR, PDE_SUPERPOSE, PDE_FOURIER_COEFFICIENTS, PDE_PARTIAL_SUMS, PDE_FINITE, PDE_STEADY_STATE,
    // Rank, trace, null space, column space, characteristic polynomial, adjugate ([AppliedSteps]).
    RANK_ECHELON, RANK_MINOR, RANK_CASE, TRACE_SUM, NULL_FREE, COLUMN_PIVOTS, CHARPOLY_DET, ADJ_COFACTORS, ADJ_TRANSPOSE, ADJ_CHECK,
    // Tangent and normal, arc length, area, volumes, mean value.
    DERIVATIVE_OF, TANGENT_POINT, TANGENT_SLOPE, TANGENT_FORMULA, NORMAL_SLOPE, NORMAL_FORMULA, NORMAL_VERTICAL, ARC_FORMULA, ARC_SQUARE,
    INTEGRAL_NUMERIC, AREA_CROSSINGS, AREA_PIECE, AREA_PIECES, VOLUME_DISCS, VOLUME_SHELLS, MEAN_FORMULA,
    // Linear programs.
    LP_CORNERS, LP_VALUES, LP_SIMPLEX, LP_DUAL, LP_UNBOUNDED, LP_INFEASIBLE,
    // Other bases, and the gcd and lcm of polynomials ([FunctionSteps]).
    BASE_DIVIDE, BASE_REMAINDERS, BASE_PLACE_VALUES, GCD_DIVIDE, GCD_LAST, LCM_PRODUCT,
}

/** What a step says, checkable: an equation, one of several, an inequality, a value, a system. */
internal sealed class StepForm {
    data class Equation(val lhs: Expr, val rhs: Expr) : StepForm()
    data class AnyOf(val equations: List<Equation>) : StepForm()
    data class Inequality(val lhs: Expr, val rel: Rel, val rhs: Expr) : StepForm()
    data class Value(val e: Expr) : StepForm()
}

/**
 * Worked steps for what Calculate shows: equations of every school kind, inequalities, systems, multiplying out,
 * factoring, fractions, derivatives, integrals and limits. Each step says what was done (a [StepKey] with its formulas) and
 * gives the formula after it; the last one is the answer the card shows. Where Calculate has no steps for a formula, null.
 */
object Steps {
    private val TOKEN = MathCalc.TOKEN

    /**
     * The steps for [latex] in [choice] (null: the likeliest, as [MathCalc.calculate] picks it), read with [assumptions], or null
     * where there are none.
     */
    fun of(latex: String, choice: Choice? = null, assumptions: Assumptions = Assumptions()): List<Step>? {
        val c = MathCalc.calculate(latex, choice, assumptions = assumptions)
        if (c.outcome is Outcome.Failed || c.outcome is Outcome.Graph) return null
        // The later areas, from what their outcomes hold.
        FunctionSteps.of(c, latex)?.let { return it }
        MoreSteps.of(c, latex, assumptions)?.let { return it }
        LinOdeSteps.of(c, latex, assumptions)?.let { return it }
        PdeSteps.of(c)?.let { return it }
        AppliedSteps.of(c, latex, assumptions)?.let { return it }
        return try {
            Budget.within(3000) {
                val statements = Parser(TOKEN.findAll(latex).map { it.value }.toList(), Flags(assumptions)).statements()
                Builder(c, latex.trim().removeSuffix("=").trim()).build(statements)?.takeIf { it.size >= 2 }
            }
        } catch (e: CalcException) {
            null
        } catch (e: ArithmeticException) {
            null
        } catch (e: StackOverflowError) {
            null
        }
    }

    private val GLUED = Regex("""\\(pi|infty|alpha|beta|gamma|delta|theta|lambda|mu|rho|sigma|tau|varphi|phi|omega)(?=[A-Za-z])""")

    /**
     * [latex] as KaTeX reads it: a letter after \pi apart from it (\pi r, not \pir), and no brackets around a whole
     * numerator or denominator (\frac{x - 1}{x + 2}, not \frac{(x - 1)}{(x + 2)}).
     */
    internal fun tidy(latex: String): String {
        val spaced = GLUED.replace(latex) { "\\${it.groupValues[1]} " }
        if ("\\frac{(" !in spaced && "}{(" !in spaced) return spaced
        val out = StringBuilder(spaced)
        var i = out.indexOf("\\frac{")
        while (i >= 0) {
            // The numerator's group, then the denominator's.
            var open = i + 5
            for (part in 0..1) {
                if (open >= out.length || out[open] != '{') break
                val close = matching(out, open, '{', '}') ?: break
                if (close - open > 2 && out[open + 1] == '(' && matching(out, open + 1, '(', ')') == close - 1) {
                    out.deleteCharAt(close - 1)
                    out.deleteCharAt(open + 1)
                    open = close - 1
                } else open = close + 1
            }
            i = out.indexOf("\\frac{", i + 1)
        }
        return out.toString()
    }

    /** Where the bracket [open] at [at] closes, or null. */
    private fun matching(s: CharSequence, at: Int, open: Char, close: Char): Int? {
        var depth = 0
        for (k in at until s.length) {
            if (s[k] == open) depth++
            if (s[k] == close && --depth == 0) return k
        }
        return null
    }
}

/** Steps kept even where their formula is the one before: a check, the values it or the domain rejects, equal sides. */
private val KEPT = setOf(StepKey.CHECK, StepKey.FALSE_SOLUTION, StepKey.EXCLUDED, StepKey.SIDES_EQUAL, StepKey.ALWAYS)

/** Builds the steps of one calculation, its formulas written in the calculation's style. */
private class Builder(private val c: Calculation, private val reading: String) {
    private val steps = ArrayList<Step>()

    /** The values a denominator of the equation excludes, once its domain is named. */
    private var domain: List<Expr> = emptyList()

    private fun l(e: Expr) = Format.expr(e, c, latex = true)
    private fun eq(a: Expr, b: Expr) = "${l(a)} = ${l(b)}"
    private fun v(e: Expr) = c.latexValue(e)

    private fun step(latex: String, key: StepKey, vararg args: String, form: StepForm? = null, widens: Boolean = false) {
        val shown = Steps.tidy(latex)
        // A step that changes nothing is left out, unless it says something of a formula already there ([KEPT]).
        if (steps.isNotEmpty() && steps.last().latex == shown && key !in KEPT) return
        steps += Step(shown, StepText(key, args.map { Steps.tidy(it) })).also { it.form = form; it.widens = widens }
    }

    private fun equation(a: Expr, b: Expr, key: StepKey, vararg args: String, widens: Boolean = false) =
        step(eq(a, b), key, *args, form = StepForm.Equation(a, b), widens = widens)

    fun build(statements: List<Statement>): List<Step>? {
        val o = c.outcome
        if (o is Outcome.SystemSolved) return system(statements, o)
        if (statements.size != 1) return null
        val s = statements[0]
        val written = s.sides.filterNotNull()
        val def = s.definition
        if (def != null) {
            val body = s.sides.getOrNull(1) ?: return null
            val x = Expr.Sym(def.variable)
            return when (c.mode) {
                Mode.DERIVATIVE -> derivative(body, x, (o as? Outcome.Expression)?.result ?: return null)
                Mode.INTEGRAL -> integral(body, x, (o as? Outcome.Expression)?.result ?: return null)
                Mode.SOLVE -> (o as? Outcome.Solved)?.let { equation(body, Expr.ZERO, x, it.solutions) }
                else -> null
            }
        }
        if (written.isEmpty()) return null
        return when (o) {
            is Outcome.Solved -> {
                if (written.size != 2) return null
                val x = Expr.Sym(o.solutions.variable)
                val letters = (written[0].symbols() + written[1].symbols())
                if (letters.size > 1) formula(written[0], written[1], x, o.solutions) else equation(written[0], written[1], x, o.solutions)
            }
            is Outcome.Intervals -> if (written.size == 2) inequality(written[0], s.relations[0], written[1], o.set) else null
            is Outcome.Expression -> {
                // One expression; a chain of them is checked line by line, not worked.
                if (written.size != 1) return null
                val last = written.last()
                when (c.mode) {
                    Mode.SIMPLIFY -> simplify(last, o.result)
                    Mode.EXPAND -> expand(last, o.result)
                    Mode.DERIVATIVE -> derivative(last, Expr.Sym(c.choice?.letter ?: return null), o.result)
                    Mode.INTEGRAL -> integral(last, Expr.Sym(c.choice?.letter ?: return null), o.result)
                    Mode.CALCULATE -> calculus(last, o.result)
                    else -> null
                }
            }
            is Outcome.Value -> {
                val last = written.last()
                if (last is Expr.Op) calculus(last, o.exact ?: (o.value as? Num.Exact)?.let { num(it.r) } ?: Expr.Real(o.value.double)) else null
            }
            is Outcome.Factored -> if (written.size == 1) factor(written[0], o.result) else null
            is Outcome.Compared -> compare(written, o)
            is Outcome.Primes -> primes(o)
            else -> null
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Equations in one unknown.

    private fun equation(lhs: Expr, rhs: Expr, x: Expr.Sym, sol: Solutions): List<Step>? {
        step(reading, StepKey.START, form = if (lhs.hasOp() || rhs.hasOp()) null else StepForm.Equation(lhs, rhs))
        if (sol.always) {
            val a = Algebra.simplify(lhs)
            val b = Algebra.simplify(rhs)
            equation(a, b, StepKey.SIMPLIFY)
            step(eq(a, b), StepKey.ALWAYS)
            return steps
        }
        if (sol.many) return null
        // Derivatives, integrals and limits in it worked out first.
        val l = Calculus.evaluate(lhs)
        val r = Calculus.evaluate(rhs)
        if (l != lhs || r != rhs) equation(l, r, StepKey.SIMPLIFY)
        if (l.hasOp() || r.hasOp()) return null
        solveBody(l, r, x, 0)
        finish(sol, x)
        return steps
    }

    /** The working for lhs = rhs: x once undone step by step, else by the kind of equation; on from where that stops. */
    private fun solveBody(lhs: Expr, rhs: Expr, x: Expr.Sym, depth: Int) {
        if (depth > 3) return
        val f = lhs - rhs
        if (occurrences(f, x) == 1) {
            // x on both sides, once when they are put together: everything to one side first.
            if (lhs.has(x) && rhs.has(x)) {
                equation(f, Expr.ZERO, StepKey.STANDARD_FORM)
                val (l, r) = isolate(f, Expr.ZERO, x)
                if (l != x && r.size == 1 && l != f) solveBody(l, r[0], x, depth + 1)
                return
            }
            val (l, r) = isolate(lhs, rhs, x)
            if (l != x && r.size == 1 && l != lhs) solveBody(l, r[0], x, depth + 1)
            return
        }
        when {
            hasAbs(f, x) -> absolute(lhs, rhs, x)
            radicalTerm(f, x) != null -> radical(lhs, rhs, x)
            denominators(f, x).isNotEmpty() -> fractions(lhs, rhs, x)
            polynomialIn(f, x) -> polynomial(lhs, rhs, x)
            else -> transcendental(lhs, rhs, x)
        }
    }

    /** The answer, as the card gives it: the solutions, their families in k, or none; what was rejected named before it. */
    private fun finish(sol: Solutions, x: Expr.Sym) {
        val name = c.latexName(x.name)
        // A value that makes a denominator 0 is outside the domain; another one rejected failed the check.
        fun outside(r: Expr) = Numeric.value(r)?.let { d -> domain.any { e -> Numeric.value(e)?.let { abs(it - d) < 1e-9 } == true } } == true
        for (r in sol.excluded) step("$name = ${v(r)}", StepKey.EXCLUDED, v(r))
        for (r in sol.rejected) step("$name = ${v(r)}", if (outside(r)) StepKey.EXCLUDED else StepKey.FALSE_SOLUTION, v(r))
        // Numbered as the card numbers them: not where the letter has an index of its own.
        val numbered = '_' !in x.name
        when {
            sol.periodic.isNotEmpty() -> step(sol.periodic.joinToString(" \\;\\lor\\; ") {
                "$name = ${if (sol.degrees) c.latexAngle(it) else l(it)}"
            } + ",\\; k \\in \\mathbb{Z}", StepKey.SOLUTIONS_PERIODIC)
            sol.values.isEmpty() && sol.complex.isNotEmpty() -> step(sol.complex.joinToString(",\\; ") { "$name = ${l(it)}" },
                StepKey.NO_REAL_SOLUTION, form = StepForm.AnyOf(emptyList()))
            sol.values.isEmpty() -> step("\\{\\}", StepKey.NO_SOLUTION, form = StepForm.AnyOf(emptyList()))
            sol.values.size == 1 -> step("$name = ${v(sol.values[0])}", StepKey.SOLUTION,
                form = StepForm.AnyOf(listOf(StepForm.Equation(x, sol.values[0]))))
            else -> step(sol.values.withIndex().joinToString(",\\; ") { (k, s) -> (if (numbered) "${name}_{${k + 1}}" else name) + " = ${v(s)}" },
                StepKey.SOLUTIONS,
                form = StepForm.AnyOf(sol.values.map { StepForm.Equation(x, it) }))
        }
        // After squaring or multiplying by a denominator, the answer keeps only the values that solve the original.
        if (sol.rejected.isNotEmpty() || sol.excluded.isNotEmpty() || steps.any { it.widens }) steps.last().narrows = true
    }

    /** A polynomial equation: multiplied out, then by its degree. */
    private fun polynomial(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val l = Algebra.expand(lhs)
        val r = Algebra.expand(rhs)
        if (l != lhs || r != rhs) equation(l, r, StepKey.EXPAND_SIDES)
        val degree = (Solve.coefficientsIn(Algebra.expand(l - r), x)?.dropLastWhile { isZero(it) }?.size ?: return) - 1
        when {
            // x drops out: what is left is true or false.
            degree <= 0 -> {
                val withX = Algebra.expand(r - (Solve.coefficientsIn(r, x)?.firstOrNull() ?: Expr.ZERO))
                if (!isZero(withX)) moveBoth(withX, l, r)
            }
            degree == 1 -> linear(l, r, x)
            degree == 2 -> quadratic(l, r, x)
            degree >= 3 -> higher(l, r, x)
        }
    }

    /** a x + b = c x + d: the x terms to one side, the numbers to the other, then divided. Also for formulas. */
    private fun linear(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        var l = lhs
        var r = rhs
        fun coefficient(e: Expr, k: Int) = Solve.coefficientsIn(Algebra.expand(e), x)?.getOrElse(k) { Expr.ZERO } ?: Expr.ZERO
        // x only on the right: the sides swapped first.
        if (!l.has(x) && r.has(x)) {
            l = rhs
            r = lhs
            equation(l, r, StepKey.SWAP_SIDES)
        }
        // Higher powers that cancel (x² on both sides): taken from both first.
        val high = Algebra.expand(r - coefficient(r, 0) - coefficient(r, 1) * x)
        if (!isZero(high)) moveBoth(high, l, r).let { (a, b) -> l = a; r = b }
        val xr = coefficient(r, 1)
        if (!isZero(xr)) moveBoth(Algebra.expand(xr * x), l, r).let { (a, b) -> l = a; r = b }
        val constant = coefficient(l, 0)
        if (!isZero(constant)) moveBoth(constant, l, r).let { (a, b) -> l = a; r = b }
        val a = coefficient(l, 1)
        if (!isOne(a) && !isZero(a)) {
            val value = Solve.simplifyValue(r / a)
            if (a == Expr.MINUS_ONE) equation(x, value, StepKey.MULTIPLY_BOTH, l(Expr.MINUS_ONE))
            else equation(x, value, StepKey.DIVIDE_BOTH, l(a))
        }
    }

    /** t taken from both sides (or −t added): the sides after it, collected. */
    private fun moveBoth(t: Expr, lhs: Expr, rhs: Expr): Pair<Expr, Expr> {
        val l = Algebra.expand(lhs - t)
        val r = Algebra.expand(rhs - t)
        if (Write.isNegative(t)) equation(l, r, StepKey.ADD_BOTH, l(neg(t))) else equation(l, r, StepKey.SUBTRACT_BOTH, l(t))
        return l to r
    }

    /** a x² + b x + c = 0: x² alone, x taken out, factors, or the quadratic formula with its discriminant. */
    private fun quadratic(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val p = standard(lhs, rhs, x)
        if (!isZero(rhs) || lhs != p) equation(p, Expr.ZERO, StepKey.STANDARD_FORM)
        val cs = Solve.coefficientsIn(p, x) ?: return
        val (k, b, a) = cs
        when {
            isZero(b) -> {
                // a x² + k = 0: x² = −k/a, then the root.
                var l: Expr = p
                var r: Expr = Expr.ZERO
                if (!isZero(k)) moveBoth(k, l, r).let { (u, w) -> l = u; r = w }
                val square = pow(x, Expr.TWO)
                if (!isOne(a)) {
                    r = Solve.simplifyValue(r / a)
                    l = square
                    equation(l, r, StepKey.DIVIDE_BOTH, l(a))
                }
                val rv = Numeric.value(r)
                if (rv != null && rv >= 0) {
                    val root = Solve.simplifyValue(sqrt(r))
                    step("${l(x)} = \\pm ${l(root)}", StepKey.SQUARE_ROOT_BOTH,
                        form = StepForm.AnyOf(listOf(StepForm.Equation(x, root), StepForm.Equation(x, neg(root)))))
                }
            }
            isZero(k) -> {
                // x(a x + b) = 0.
                val inner = Algebra.expand(a * x + b)
                step("${l(x)}${paren(inner)} = 0", StepKey.FACTOR_SIDE, form = StepForm.Equation(x * inner, Expr.ZERO))
                zeroProduct(listOf(x, inner))
            }
            else -> {
                val product = Algebra.factor(p)
                val factors = product?.first?.let { linearFactors(it, x) }
                if (factors != null && factors.size == 2) {
                    step("${l(product.first)} = 0", StepKey.FACTOR_SIDE, form = StepForm.Equation(product.first, Expr.ZERO))
                    zeroProduct(factors)
                } else {
                    formulaSteps(a, b, k, x)
                }
            }
        }
    }

    /** lhs − rhs multiplied out with its highest power positive: the polynomial of the standard form (… = 0). */
    private fun standard(lhs: Expr, rhs: Expr, x: Expr.Sym): Expr {
        var p = Algebra.expand(lhs - rhs)
        val lead = Solve.coefficientsIn(p, x)?.lastOrNull { !isZero(it) }
        if (lead != null && Write.isNegative(lead)) p = Algebra.expand(neg(p))
        return p
    }

    /** The quadratic formula with a, b, c, the discriminant, and the solutions it gives. */
    private fun formulaSteps(a: Expr, b: Expr, k: Expr, x: Expr.Sym) {
        val name = c.latexName(x.name)
        step("${name}_{1,2} = \\frac{-${paren(b)} \\pm \\sqrt{${paren(b)}^{2} - 4 \\cdot ${paren(a)} \\cdot ${paren(k)}}}{2 \\cdot ${paren(a)}}",
            StepKey.QUADRATIC_FORMULA, l(a), l(b), l(k))
        val d = Algebra.expand(b * b - num(4) * a * k)
        step("D = ${paren(b)}^{2} - 4 \\cdot ${paren(a)} \\cdot ${paren(k)} = ${l(d)}", StepKey.DISCRIMINANT)
        val dv = Numeric.value(d) ?: return
        when {
            dv < -1e-300 -> step("D < 0", StepKey.D_NEGATIVE, form = StepForm.AnyOf(emptyList()))
            abs(dv) <= 1e-300 -> {
                val r = Solve.simplifyValue(neg(b) / (num(2) * a))
                step("$name = ${l(r)}", StepKey.D_ZERO, form = StepForm.AnyOf(listOf(StepForm.Equation(x, r))))
            }
            else -> {
                val s = Solve.simplifyValue(sqrt(d))
                val r1 = Solve.simplifyValue((neg(b) - s) / (num(2) * a))
                val r2 = Solve.simplifyValue((neg(b) + s) / (num(2) * a))
                step("${name}_{1,2} = \\frac{${l(neg(b))} \\pm ${l(s)}}{${l(num(2) * a)}}", StepKey.D_POSITIVE,
                    form = StepForm.AnyOf(listOf(StepForm.Equation(x, r1), StepForm.Equation(x, r2))))
            }
        }
    }

    /** A product equal to 0: one of its factors is; in the order of their roots, as the card orders the brackets. */
    private fun zeroProduct(factors: List<Expr>) {
        fun root(f: Expr): Double {
            val x = f.symbols().singleOrNull()?.let { Expr.Sym(it) } ?: return Double.MAX_VALUE
            val cs = Solve.coefficientsIn(Algebra.expand(f), x)?.takeIf { it.size == 2 } ?: return Double.MAX_VALUE
            return Numeric.value(neg(cs[0]) / cs[1]) ?: Double.MAX_VALUE
        }
        val parts = factors.distinct().sortedBy { root(it) }.map { StepForm.Equation(it, Expr.ZERO) }
        step(parts.joinToString(" \\;\\lor\\; ") { "${l(it.lhs)} = 0" }, StepKey.ZERO_PRODUCT, form = StepForm.AnyOf(parts))
    }

    /** The factors of a product that hold x, each of degree one, or null. */
    private fun linearFactors(product: Expr, x: Expr.Sym): List<Expr>? {
        val factors = (if (product is Expr.Mul) product.factors else listOf(product)).filter { it.has(x) }
            .map { if (it is Expr.Pow && integer(it.exp) != null) it.base else it }
        if (factors.any { (Solve.coefficientsIn(Algebra.expand(it), x)?.size ?: 0) != 2 }) return null
        return factors
    }

    /** Degree three and more: in standard form, factored where it can be, each factor 0. */
    private fun higher(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val p = standard(lhs, rhs, x)
        if (!isZero(rhs) || lhs != p) equation(p, Expr.ZERO, StepKey.STANDARD_FORM)
        val product = Algebra.factor(p) ?: return
        if (product.second?.trivial == true) return
        val factors = (if (product.first is Expr.Mul) (product.first as Expr.Mul).factors else listOf(product.first)).filter { it.has(x) }
            .map { if (it is Expr.Pow && integer(it.exp) != null) it.base else it }
        if (factors.size < 2) return
        step("${l(product.first)} = 0", StepKey.FACTOR_SIDE, form = StepForm.Equation(product.first, Expr.ZERO))
        zeroProduct(factors)
    }

    /** Fractions with x below: where it is defined, multiplied by the common denominator, then solved. */
    private fun fractions(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val dens = denominators(lhs - rhs, x)
        val excluded = dens.flatMap { d -> Solve.equation(d, Expr.ZERO, x).values }.distinctBy { Numeric.value(it) }
            .sortedBy { Numeric.value(it) ?: 0.0 }
        domain = excluded
        if (excluded.isNotEmpty()) step(excluded.joinToString(",\\; ") { "${c.latexName(x.name)} \\neq ${l(it)}" }, StepKey.DOMAIN)
        // The common denominator: each distinct denominator (as factors) to its highest power.
        val factors = LinkedHashMap<Expr, Int>()
        for (d in dens) {
            val (product, _) = Algebra.factor(d) ?: (d to null)
            for (f in (if (product is Expr.Mul) product.factors else listOf(product))) {
                if (!f.has(x)) continue
                val (base, n) = if (f is Expr.Pow && integer(f.exp) != null) f.base to integer(f.exp)!!.toInt() else f to 1
                factors[base] = maxOf(factors[base] ?: 0, n)
            }
        }
        val lcd = mul(factors.map { (b, n) -> pow(b, num(n.toLong())) })
        val l = Algebra.cancel(lhs * lcd)?.let { Algebra.expand(it) } ?: return
        val r = Algebra.cancel(rhs * lcd)?.let { Algebra.expand(it) } ?: return
        if (denominators(l - r, x).isNotEmpty()) return
        equation(l, r, StepKey.MULTIPLY_DENOMINATOR, l(lcd), widens = true)
        polynomial(l, r, x)
    }

    /** One root with x: alone on one side, squared, solved, checked. */
    private fun radical(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val f = lhs - rhs
        val (root, rest) = radicalTerm(f, x) ?: return
        val base = (root as Expr.Pow).base
        val n = rational(root.exp)!!.den.toInt()
        val other = Solve.simplifyValue(rest)
        if (!(lhs == root && rhs == other)) equation(root, other, StepKey.ISOLATE_ROOT)
        val l = Algebra.expand(base)
        val r = Algebra.expand(pow(other, num(n.toLong())))
        if (n == 2) equation(l, r, StepKey.SQUARE_BOTH, widens = true) else equation(l, r, StepKey.RAISE_BOTH, l(num(n.toLong())), widens = true)
        if (polynomialIn(l - r, x)) polynomial(l, r, x)
        step(eq(lhs, rhs), StepKey.CHECK)
    }

    /** |A| = B: B < 0 has none; otherwise A = B or A = −B, each solved and checked. */
    private fun absolute(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val f = lhs - rhs
        val terms = if (f is Expr.Add) f.terms else listOf(f)
        val absTerm = terms.singleOrNull { it.has(x) && containsAbs(it) } ?: return
        val (k, a) = splitCoefficient(absTerm)
        if (a !is Expr.Fn || a.f != Func.ABS) return
        val other = Solve.simplifyValue(neg(add(terms.filter { it !== absTerm })) / k)
        if (!(lhs == a && rhs == other)) equation(a, other, StepKey.ISOLATE, l(a))
        val ov = if (other.has(x)) null else Numeric.value(other)
        if (ov != null && ov < 0) {
            step("${l(a)} \\geq 0", StepKey.ABS_NEGATIVE, form = StepForm.AnyOf(emptyList()))
            return
        }
        val inside = a.args[0]
        val cases = listOf(StepForm.Equation(inside, other), StepForm.Equation(inside, Solve.simplifyValue(neg(other))))
        step(cases.joinToString(" \\;\\lor\\; ") { eq(it.lhs, it.rhs) }, StepKey.ABS_CASES, form = StepForm.AnyOf(cases), widens = other.has(x))
        if (other.has(x)) step(eq(lhs, rhs), StepKey.CHECK)
    }

    /** Exponentials, logarithms and trigonometry where x occurs more than once: logs combined, substitution, same base. */
    private fun transcendental(lhs: Expr, rhs: Expr, x: Expr.Sym) {
        val f = lhs - rhs
        // ln a + ln b on one: ln(ab) = c, then the logarithm undone.
        val combined = Algebra.combineLogs(lhs) to Algebra.combineLogs(rhs)
        if (combined.first != lhs || combined.second != rhs) {
            // ln(x(x − 1)) is defined for more x than ln x + ln(x − 1): checked at the end.
            equation(combined.first, combined.second, StepKey.COMBINE_LOGS, widens = true)
            val (a, b) = combined
            if (a is Expr.Fn && a.f == Func.LN && b is Expr.Fn && b.f == Func.LN) {
                equation(a.args[0], b.args[0], StepKey.ARGUMENTS_EQUAL, widens = true)
                if (polynomialIn(a.args[0] - b.args[0], x)) polynomial(a.args[0], b.args[0], x)
                step(eq(lhs, rhs), StepKey.CHECK)
                return
            }
            if (occurrences(a - b, x) == 1 || (a is Expr.Fn && !b.has(x))) {
                val (l, r) = isolate(a, b, x)
                if (l != x && r.size == 1 && polynomialIn(l - r[0], x)) polynomial(l, r[0], x)
                step(eq(lhs, rhs), StepKey.CHECK)
                return
            }
        }
        // The same base on both sides: the exponents are equal.
        if (lhs is Expr.Pow && rhs is Expr.Pow && lhs.base == rhs.base && !lhs.base.has(x)) {
            equation(lhs.exp, rhs.exp, StepKey.EXPONENTS_EQUAL)
            if (polynomialIn(lhs.exp - rhs.exp, x)) polynomial(lhs.exp, rhs.exp, x)
            return
        }
        // A polynomial in one kernel of x: u for it, solved, then u put back.
        val kernel = singleKernel(Algebra.expand(f), x) ?: return
        val u = Expr.Sym("u")
        val inU = Algebra.expand(substituteKernel(Algebra.expand(f), kernel, x, u) ?: return)
        if (inU.has(x) || !polynomialIn(inU, u)) return
        step("u = ${l(kernel)}:\\quad ${l(inU)} = 0", StepKey.SUBSTITUTE_U, l(kernel))
        polynomial(inU, Expr.ZERO, u)
        val roots = Solve.equation(inU, Expr.ZERO, u).values
        if (roots.isEmpty()) return
        val back = roots.map { StepForm.Equation(kernel, it) }
        step(back.joinToString(" \\;\\lor\\; ") { "${l(it.lhs)} = ${l(it.rhs)}" }, StepKey.BACK_SUBSTITUTE, form = StepForm.AnyOf(back))
        if (kernel is Expr.Fn && (kernel.f == Func.SIN || kernel.f == Func.COS) && roots.any { (Numeric.value(it) ?: 0.0).let { r -> abs(r) > 1 } }) {
            step("-1 \\leq ${l(kernel)} \\leq 1", StepKey.TRIG_RANGE)
        }
    }

    /**
     * lhs = rhs with x once: what is done to x undone, outermost first, on every branch at once (± after a square,
     * two solutions per period after sin): x = … at the end.
     */
    private fun isolate(lhs0: Expr, rhs0: Expr, x: Expr.Sym): Pair<Expr, List<Expr>> {
        var lhs = lhs0
        var rhs = listOf(rhs0)
        if (!lhs.has(x) && rhs0.has(x)) {
            lhs = rhs0
            rhs = listOf(lhs0)
            equation(lhs, rhs[0], StepKey.SWAP_SIDES)
        }
        fun show(key: StepKey, vararg args: String, widens: Boolean = false) {
            val parts = rhs.map { StepForm.Equation(lhs, it) }
            val latex = parts.joinToString(" \\;\\lor\\; ") { eq(it.lhs, it.rhs) }
            step(latex, key, *args, form = if (rhs.any { it.has(K) }) null else if (parts.size == 1) parts[0] else StepForm.AnyOf(parts), widens = widens)
        }
        var guard = 0
        while (lhs != x && guard++ < 12) {
            val cur = lhs
            val before = rhs
            when {
                cur is Expr.Add -> {
                    val (with, without) = cur.terms.partition { it.has(x) }
                    if (with.size != 1) return lhs to rhs
                    val t = add(without)
                    lhs = with[0]
                    rhs = rhs.map { Solve.simplifyValue(it - t) }
                    if (Write.isNegative(t)) show(StepKey.ADD_BOTH, l(neg(t))) else show(StepKey.SUBTRACT_BOTH, l(t))
                }
                cur is Expr.Mul -> {
                    val (with, without) = cur.factors.partition { it.has(x) }
                    if (with.size != 1) return lhs to rhs
                    val k = mul(without)
                    lhs = with[0]
                    rhs = rhs.map { Solve.simplifyValue(it / k) }
                    val kr = rational(k)
                    if (kr != null && !kr.isInteger && kr.num.abs() == java.math.BigInteger.ONE) show(StepKey.MULTIPLY_BOTH, l(num(Rational.ONE / kr)))
                    else show(StepKey.DIVIDE_BOTH, l(k))
                }
                cur is Expr.Pow && cur.base.has(x) && !cur.exp.has(x) -> {
                    val r = rational(cur.exp) ?: return lhs to rhs
                    // An even power is never negative: nothing to take the root of.
                    if (r.isInteger && !r.num.testBit(0) && rhs.any { (Numeric.value(it) ?: 0.0) < 0 }) return lhs to rhs
                    lhs = cur.base
                    when {
                        r == Rational.of(-1) -> { rhs = rhs.map { Solve.simplifyValue(Expr.ONE / it) }; show(StepKey.RECIPROCAL_BOTH) }
                        r.isInteger && !r.num.testBit(0) -> {
                            rhs = rhs.flatMap { v ->
                                val root = Solve.simplifyValue(pow(v, num(Rational.ONE / r)))
                                if (isZero(root)) listOf(root) else listOf(neg(root), root)
                            }.map { Solve.simplifyValue(it) }
                            if (r == Rational.of(2)) show(StepKey.SQUARE_ROOT_BOTH) else show(StepKey.ROOT_BOTH, l(num(r)))
                        }
                        else -> {
                            rhs = rhs.map { Solve.simplifyValue(pow(it, num(Rational.ONE / r))) }
                            when {
                                r.isInteger -> show(StepKey.ROOT_BOTH, l(num(r)))
                                Rational.ONE / r == Rational.of(2) -> show(StepKey.SQUARE_BOTH, widens = true)
                                else -> show(StepKey.RAISE_BOTH, l(num(Rational.ONE / r)), widens = true)
                            }
                        }
                    }
                }
                cur is Expr.Pow && !cur.base.has(x) -> {
                    lhs = cur.exp
                    if (cur.base == Expr.E) {
                        rhs = rhs.map { Solve.simplifyValue(ln(it)) }
                        show(StepKey.TAKE_LN)
                    } else {
                        rhs = rhs.map { Solve.simplifyValue(fn(Func.LOG, cur.base, it)) }
                        show(StepKey.TAKE_LOG, l(cur.base))
                    }
                }
                cur is Expr.Fn -> {
                    val inner = cur.args.last()
                    lhs = inner
                    when (cur.f) {
                        Func.LN -> { rhs = rhs.map { Solve.simplifyValue(exp(it)) }; show(StepKey.EXPONENTIATE, "e", widens = true) }
                        Func.LOG -> { rhs = rhs.map { Solve.simplifyValue(pow(cur.args[0], it)) }; show(StepKey.EXPONENTIATE, l(cur.args[0]), widens = true) }
                        Func.SIN, Func.COS, Func.TAN -> {
                            // sin and cos lie between −1 and 1.
                            if (cur.f != Func.TAN && before.any { abs(Numeric.value(it) ?: 0.0) > 1 + 1e-12 }) {
                                lhs = cur
                                step("-1 \\leq ${l(cur)} \\leq 1", StepKey.TRIG_RANGE, form = StepForm.AnyOf(emptyList()))
                                return lhs to rhs
                            }
                            val inverse = when (cur.f) { Func.SIN -> Func.ASIN; Func.COS -> Func.ACOS; else -> Func.ATAN }
                            val twoKPi = num(2) * K * Expr.PI
                            rhs = rhs.flatMap { v ->
                                val a = Solve.simplifyValue(fn(inverse, v))
                                when (cur.f) {
                                    Func.SIN -> listOf(a + twoKPi, Solve.simplifyValue(Expr.PI - a) + twoKPi)
                                    Func.COS -> listOf(a + twoKPi, neg(a) + twoKPi)
                                    else -> listOf(a + K * Expr.PI)
                                }
                            }.map { Algebra.expand(it) }.distinct()
                            show(StepKey.INVERSE_TRIG, l(fn(inverse, before[0])))
                        }
                        Func.ABS -> {
                            // An absolute value is never negative.
                            if (before.any { (Numeric.value(it) ?: 0.0) < 0 }) {
                                lhs = cur
                                step("${l(cur)} \\geq 0", StepKey.ABS_NEGATIVE, form = StepForm.AnyOf(emptyList()))
                                return lhs to rhs
                            }
                            rhs = rhs.flatMap { listOf(it, neg(it)) }.map { Solve.simplifyValue(it) }.distinct()
                            show(StepKey.ABS_CASES)
                        }
                        else -> {
                            lhs = cur
                            return lhs to rhs
                        }
                    }
                }
                else -> return lhs to rhs
            }
        }
        return lhs to rhs
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Formulas solved for a letter.

    private fun formula(lhs: Expr, rhs: Expr, x: Expr.Sym, sol: Solutions): List<Step>? {
        if (lhs.hasOp() || rhs.hasOp()) return null
        step(reading, StepKey.START, form = StepForm.Equation(lhs, rhs))
        val f = lhs - rhs
        if (occurrences(f, x) == 1) isolate(lhs, rhs, x)
        else {
            val l = Algebra.expand(lhs)
            val r = Algebra.expand(rhs)
            if (l != lhs || r != rhs) equation(l, r, StepKey.EXPAND_SIDES)
            val cs = Solve.coefficientsIn(Algebra.expand(l - r), x) ?: return null
            if (cs.size != 2) return null
            // The terms with x to the left, the others to the right; x taken out; divided.
            val left = Algebra.expand(cs[1] * x)
            val right = Solve.simplifyValue(neg(cs[0]))
            equation(left, right, StepKey.BRING_TERMS, l(x))
            val factor = Solve.simplifyValue(cs[1])
            if (factor !is Expr.Num) step("${l(x)}${paren(factor)} = ${l(right)}", StepKey.TAKE_OUT_LETTER, l(x), form = StepForm.Equation(x * factor, right))
        }
        val name = c.latexName(x.name)
        val values = sol.values
        if (values.isEmpty()) return null
        val conditions = if (sol.conditions.isEmpty()) "" else ",\\quad " + sol.conditions.joinToString(",\\; ") { "${l(it)} \\neq 0" }
        step(values.joinToString(" \\;\\lor\\; ") { "$name = ${l(it)}" } + conditions, if (values.size == 1) StepKey.SOLUTION else StepKey.SOLUTIONS,
            form = StepForm.AnyOf(values.map { StepForm.Equation(x, it) }))
        return steps
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Inequalities.

    private fun inequality(lhs: Expr, rel: Rel, rhs: Expr, set: IntervalSet): List<Step>? {
        if (lhs.hasOp() || rhs.hasOp()) return null
        val x = Expr.Sym(set.variable)
        fun ineq(a: Expr, r: Rel, b: Expr, key: StepKey, vararg args: String) =
            step("${l(a)} ${r.latex} ${l(b)}", key, *args, form = StepForm.Inequality(a, r, b))
        step(reading, StepKey.START, form = StepForm.Inequality(lhs, rel, rhs))
        val f = Algebra.expand(lhs - rhs)
        val cs = Solve.coefficientsIn(f, x)
        if (cs != null && cs.dropLastWhile { isZero(it) }.size == 2 && denominators(lhs - rhs, x).isEmpty() && !hasAbs(lhs - rhs, x)) {
            // Linear: like an equation, turned round when dividing by a negative number.
            var l = Algebra.expand(lhs)
            var r = Algebra.expand(rhs)
            if (l != lhs || r != rhs) ineq(l, rel, r, StepKey.EXPAND_SIDES)
            var cr = Solve.coefficientsIn(r, x) ?: return null
            // Higher powers that cancel (x² on both sides): taken from both first.
            val high = Algebra.expand(r - cr[0] - cr.getOrElse(1) { Expr.ZERO } * x)
            if (!isZero(high)) {
                l = Algebra.expand(l - high); r = Algebra.expand(r - high)
                if (Write.isNegative(high)) ineq(l, rel, r, StepKey.ADD_BOTH, l(neg(high))) else ineq(l, rel, r, StepKey.SUBTRACT_BOTH, l(high))
                cr = Solve.coefficientsIn(r, x) ?: return null
            }
            val xr = cr.getOrElse(1) { Expr.ZERO }
            if (!isZero(xr)) {
                val t = Algebra.expand(xr * x)
                l = Algebra.expand(l - t); r = Algebra.expand(r - t)
                if (Write.isNegative(t)) ineq(l, rel, r, StepKey.ADD_BOTH, l(neg(t))) else ineq(l, rel, r, StepKey.SUBTRACT_BOTH, l(t))
            }
            val k = Solve.coefficientsIn(l, x)?.getOrElse(0) { Expr.ZERO } ?: return null
            if (!isZero(k)) {
                l = Algebra.expand(l - k); r = Algebra.expand(r - k)
                if (Write.isNegative(k)) ineq(l, rel, r, StepKey.ADD_BOTH, l(neg(k))) else ineq(l, rel, r, StepKey.SUBTRACT_BOTH, l(k))
            }
            val a = Solve.coefficientsIn(l, x)?.getOrNull(1) ?: return null
            val av = Numeric.value(a) ?: return null
            if (!isOne(a)) {
                val value = Solve.simplifyValue(r / a)
                if (av < 0) ineq(x, rel.flipped, value, StepKey.FLIP, l(a))
                else ineq(x, rel, value, StepKey.DIVIDE_BOTH, l(a))
            }
        } else if (!absoluteInequality(lhs, rel, rhs, x)) {
            val kernels = Algebra.Kernels()
            val one = Algebra.cancel(lhs - rhs) ?: f
            // Already written as … ≷ 0: nothing to bring over.
            if (!(isZero(rhs) && (Algebra.cancel(lhs) ?: lhs) == one)) ineq(one, rel, Expr.ZERO, StepKey.STANDARD_FORM)
            val zeros = Solve.equation(lhs, rhs, x).values
            val poles = denominators(lhs - rhs, x).flatMap { Solve.equation(it, Expr.ZERO, x).values }
            val points = (zeros + poles).distinctBy { Numeric.value(it)?.let { d -> Math.round(d * 1e9) } }.sortedBy { Numeric.value(it) ?: 0.0 }
            if (kernels.isEmpty && points.isNotEmpty()) {
                step(points.joinToString(",\\; ") { "${c.latexName(x.name)} = ${v(it)}" }, StepKey.CRITICAL_POINTS)
                step(signChart(one, x, points), StepKey.SIGN_CHART)
            }
        }
        step(c.latex(set), StepKey.SOLUTION_SET)
        return steps
    }

    /**
     * |a x + b| < c with c > 0 as −c < a x + b < c, |a x + b| > c as a x + b < −c or a x + b > c; then each part solved at
     * once. False where the inequality is not of this kind.
     */
    private fun absoluteInequality(lhs: Expr, rel: Rel, rhs: Expr, x: Expr.Sym): Boolean {
        if (lhs !is Expr.Fn || lhs.f != Func.ABS || rhs.has(x) || rel == Rel.EQ || rel == Rel.NE) return false
        if ((Numeric.value(rhs) ?: return false) <= 0) return false
        val inside = Algebra.expand(lhs.args[0])
        val (b, a) = Solve.coefficientsIn(inside, x)?.takeIf { it.size == 2 && it[1].isConstant() } ?: return false
        val av = Numeric.value(a) ?: return false
        val less = rel == Rel.LT || rel == Rel.LE
        var lo = Solve.simplifyValue(neg(rhs))
        var hi = rhs
        var middle = inside
        fun show(key: StepKey, vararg args: String) = step(
            if (less) "${l(lo)} ${rel.latex} ${l(middle)} ${rel.latex} ${l(hi)}"
            else "${l(middle)} ${rel.flipped.latex} ${l(lo)} \\;\\lor\\; ${l(middle)} ${rel.latex} ${l(hi)}", key, *args)
        show(if (less) StepKey.ABS_LESS else StepKey.ABS_GREATER)
        if (!isZero(b)) {
            lo = Solve.simplifyValue(lo - b)
            hi = Solve.simplifyValue(hi - b)
            middle = Algebra.expand(middle - b)
            if (Write.isNegative(b)) show(StepKey.ADD_BOTH, l(neg(b))) else show(StepKey.SUBTRACT_BOTH, l(b))
        }
        if (!isOne(a)) {
            lo = Solve.simplifyValue(lo / a)
            hi = Solve.simplifyValue(hi / a)
            middle = x
            // Divided by a negative number, the bounds change places.
            if (av < 0) {
                lo = hi.also { hi = lo }
                show(StepKey.FLIP, l(a))
            } else show(StepKey.DIVIDE_BOTH, l(a))
        }
        return true
    }

    /** The sign of f between and at its critical points: (−∞, −1): +, … */
    private fun signChart(f: Expr, x: Expr.Sym, points: List<Expr>): String {
        val values = points.map { Numeric.value(it)!! }
        val out = ArrayList<String>()
        fun sign(t: Double): String {
            val d = Numeric.eval(f, mapOf(x.name to t))
            return when {
                d.isNaN() -> "\\text{—}"
                d > 0 -> "+"
                d < 0 -> "-"
                else -> "0"
            }
        }
        for (k in 0..values.size) {
            val lo = values.getOrNull(k - 1)
            val hi = values.getOrNull(k)
            val t = when {
                lo == null -> hi!! - 1 - abs(hi) * 0.1
                hi == null -> lo + 1 + abs(lo) * 0.1
                else -> (lo + hi) / 2
            }
            val left = lo?.let { l(points[k - 1]) } ?: "-\\infty"
            val right = hi?.let { l(points[k]) } ?: "\\infty"
            out += "($left, $right)\\!: ${sign(t)}"
        }
        return out.joinToString(",\\quad ")
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Systems.

    private fun system(statements: List<Statement>, o: Outcome.SystemSolved): List<Step>? {
        val equations = statements.map { s ->
            val sides = s.sides.filterNotNull()
            if (sides.size != 2) return null
            if (sides.any { it.hasOp() }) return null
            sides[0] to sides[1]
        }
        step("\\begin{cases}" + equations.joinToString(" \\\\ ") { (a, b) -> eq(a, b) } + "\\end{cases}", StepKey.SYSTEM)
        var rest = equations.map { (a, b) -> Algebra.expand(a) to Algebra.expand(b) }
        val solvedFor = ArrayList<Pair<String, Expr>>()
        val letters = o.letters.toMutableList()
        while (letters.size > 1 && rest.size > 1) {
            // An equation linear in a letter with a number before it: preferably 1 or −1.
            var pick: Triple<Int, String, Expr>? = null
            var pickUnit = false
            for ((i, e) in rest.withIndex()) for (v in letters) {
                val cs = Solve.coefficientsIn(Algebra.expand(e.first - e.second), Expr.Sym(v)) ?: continue
                if (cs.size != 2 || !cs[1].isConstant() || isZero(cs[1])) continue
                val unit = abs(Numeric.value(cs[1]) ?: 2.0) == 1.0
                if (pick != null && (pickUnit || !unit)) continue
                pick = Triple(i, v, Solve.simplifyValue(Algebra.expand(neg(cs[0]) / cs[1])))
                pickUnit = unit
            }
            val (i, v, value) = pick ?: return null
            step("${c.latexName(v)} = ${l(value)}", StepKey.SOLVE_FOR_LETTER, c.latexName(v))
            solvedFor += v to value
            letters -= v
            // Put into each other equation, each side multiplied out.
            val letter = Expr.Sym(v)
            rest = rest.filterIndexed { k, _ -> k != i }.map { (a, b) ->
                Algebra.expand(a.substitute(letter, value)) to Algebra.expand(b.substitute(letter, value))
            }
            step(rest.joinToString(",\\quad ") { (a, b) -> eq(a, b) }, StepKey.SUBSTITUTE_INTO, c.latexName(v),
                form = rest.singleOrNull()?.let { (a, b) -> StepForm.Equation(a, b) })
        }
        if (letters.size == 1 && rest.isNotEmpty() && o.solutions.isNotEmpty()) {
            // One equation in one letter: solved as any other, its values named.
            val x = Expr.Sym(letters[0])
            val (a, b) = rest[0]
            solveBody(a, b, x, 0)
            val values = o.solutions.map { it.getValue(x.name) }.distinctBy { Numeric.value(it)?.let { d -> Math.round(d * 1e9) } }
                .sortedBy { Numeric.value(it) ?: 0.0 }
            val name = c.latexName(x.name)
            step(if (values.size == 1) "$name = ${v(values[0])}" else values.withIndex().joinToString(",\\; ") { (k, s) -> "${name}_{${k + 1}} = ${v(s)}" },
                if (values.size == 1) StepKey.SOLUTION else StepKey.SOLUTIONS, form = StepForm.AnyOf(values.map { StepForm.Equation(x, it) }))
        }
        if (solvedFor.isNotEmpty() && o.solutions.isNotEmpty()) {
            // Each value put back into what the letters solved for are, the last solved for first.
            val lines = o.solutions.map { s ->
                val given = letters.joinToString(",\\; ") { "${c.latexName(it)} = ${v(s.getValue(it))}" }
                val found = solvedFor.reversed().joinToString(",\\; ") { (name, e) ->
                    val value = v(s.getValue(name))
                    val shown = l(e)
                    "${c.latexName(name)} = " + if (shown == value) value else "$shown = $value"
                }
                if (given.isEmpty()) found else "$given:\\quad $found"
            }
            step(lines.joinToString(";\\qquad "), StepKey.BACK_SUBSTITUTE_VALUE)
        }
        val final = if (o.solutions.isEmpty()) "\\{\\}" else o.solutions.joinToString(";\\quad ") { s ->
            o.letters.joinToString(",\\; ") { "${c.latexName(it)} = ${v(s.getValue(it))}" }
        }
        step(final, if (o.solutions.isEmpty()) StepKey.NO_SOLUTION else StepKey.SOLUTION)
        return steps
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Multiplying out, simplifying, factoring.

    private fun expand(e: Expr, result: Expr): List<Step>? {
        step(reading.ifEmpty { l(e) }, StepKey.START, form = StepForm.Value(e))
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        if (terms.size == 1) firstProduct(terms[0])
        // Each term multiplied out on its own, the binomial formula named where one term is one.
        var key = StepKey.MULTIPLY_OUT
        val pieces = terms.map { t ->
            val (k, rest) = splitCoefficient(t)
            val base = (rest as? Expr.Pow)?.base
            if (base is Expr.Add && base.terms.size == 2 && rest.exp == Expr.TWO) {
                key = if (base.terms.any { Write.isNegative(it) }) StepKey.BINOMIAL_2 else StepKey.BINOMIAL_1
            }
            // (a + b)(a − b): two sums of two terms whose product has two terms.
            if (rest is Expr.Mul && rest.factors.size == 2 && rest.factors.all { it is Expr.Add && it.terms.size == 2 } &&
                (Algebra.expand(rest) as? Expr.Add)?.terms?.size == 2) {
                key = StepKey.BINOMIAL_3
            }
            k to Algebra.expand(rest)
        }
        val shown = pieces.withIndex().joinToString("") { (i, p) ->
            val (k, inner) = p
            val negative = Write.isNegative(k)
            val kk = if (negative) neg(k) else k
            // A sum in brackets where something stands before it: -(x² − 1), 2(x + 3), or another term.
            val body = if (isOne(kk)) (if (inner is Expr.Add && (terms.size > 1 || negative)) paren(inner) else l(inner)) else l(kk) + paren(inner)
            when {
                i == 0 -> (if (negative) "-" else "") + body
                negative -> " - $body"
                else -> " + $body"
            }
        }
        // Only where there is a bracket to multiply out.
        fun bracket(f: Expr) = f is Expr.Add || (f is Expr.Pow && f.base is Expr.Add)
        if (terms.any { t -> splitCoefficient(t).second.let { bracket(it) || (it is Expr.Mul && it.factors.any(::bracket)) } }) {
            step(shown, key, form = StepForm.Value(add(pieces.map { (k, p) -> k * p })))
        }
        step(l(result), StepKey.COLLECT, form = StepForm.Value(result))
        return steps
    }

    /**
     * Three brackets or more, or a power above 2: two of them multiplied out first, a pair (a + b)(a − b) where there is
     * one: (x + 1)(x − 1)(x + 2) = (x² − 1)(x + 2), (a − b)³ = (a² − 2ab + b²)(a − b), (x − 1)⁴ = (x² − 2x + 1)².
     */
    private fun firstProduct(t: Expr) {
        val (k, rest) = splitCoefficient(t)
        val shown: String
        val value: Expr
        val key: StepKey
        val n = (rest as? Expr.Pow)?.let { integer(it.exp)?.toInt() }
        val sum = (rest as? Expr.Pow)?.base
        if (sum is Expr.Add && n != null && n in 3..6) {
            // (A²)^(n/2), times A once more for an odd n.
            val square = Algebra.expand(pow(sum, Expr.TWO))
            val half = n / 2
            shown = paren(square) + (if (half > 1) "^{$half}" else "") + (if (n % 2 == 1) paren(sum) else "")
            value = pow(square, num(half.toLong())) * (if (n % 2 == 1) sum else Expr.ONE)
            key = if (sum.terms.size != 2) StepKey.MULTIPLY_PAIR else if (sum.terms.any { Write.isNegative(it) }) StepKey.BINOMIAL_2 else StepKey.BINOMIAL_1
        } else if (rest is Expr.Mul && rest.factors.count { it is Expr.Add } >= 3) {
            val sums = rest.factors.filter { it is Expr.Add }
            val pairs = sums.indices.flatMap { i -> (i + 1 until sums.size).map { j -> sums[i] to sums[j] } }
            val conjugate = pairs.firstOrNull { (a, b) -> (a as Expr.Add).terms.size == 2 && (b as Expr.Add).terms.size == 2 &&
                (Algebra.expand(a * b) as? Expr.Add)?.terms?.size == 2 }
            val (a, b) = conjugate ?: pairs[0]
            val first = Algebra.expand(a * b)
            val others = rest.factors.filter { it !== a && it !== b }
            fun factor(f: Expr) = when {
                f is Expr.Add -> paren(f)
                f is Expr.Pow && f.base is Expr.Add -> "${paren(f.base)}^{${l(f.exp)}}"
                else -> l(f)
            }
            shown = paren(first) + others.joinToString("") { factor(it) }
            value = first * mul(others)
            key = if (conjugate != null) StepKey.BINOMIAL_3 else StepKey.MULTIPLY_PAIR
        } else return
        val number = when {
            isOne(k) -> ""
            k == Expr.MINUS_ONE -> "-"
            else -> l(k)
        }
        step(number + shown, key, form = StepForm.Value(k * value))
    }

    private fun simplify(e: Expr, result: Expr): List<Step>? {
        val kernels = Algebra.Kernels()
        val fraction = Algebra.fraction(e, kernels)
        if (fraction == null || fraction.second.isConstant) {
            // A polynomial: multiplying out is simplifying it.
            return if (Algebra.expand(e) == result) expand(e, result) else null
        }
        step(reading.ifEmpty { l(e) }, StepKey.START, form = StepForm.Value(e))
        val terms = if (e is Expr.Add) e.terms else listOf(e)
        if (terms.size > 1 && kernels.isEmpty) {
            // One denominator for all: each term's numerator times what its denominator lacks.
            val (num0, den0) = fraction
            val den = Algebra.toExpr(den0, kernels)
            val numerator = Algebra.toExpr(num0, kernels)
            step("\\frac{${l(numerator)}}{${l(factored(den))}}", StepKey.COMMON_DENOMINATOR, l(factored(den)),
                form = StepForm.Value(numerator / den))
        }
        // Numerator and denominator in factors, before what they share is cancelled.
        val common = cancelled(e, result)
        if (common != null && kernels.isEmpty) {
            val (n0, d0) = fraction
            fun factors(p: Polynomial): Expr = Factor.of(p).let { pr ->
                mul(listOf(num(pr.constant)) + pr.factors.map { (f, k) -> pow(Algebra.toExpr(f, kernels), num(k.toLong())) })
            }
            step("\\frac{${l(factors(n0))}}{${l(factors(d0))}}", StepKey.FACTOR_PARTS, form = StepForm.Value(e))
        }
        step(l(result), if (common != null) StepKey.CANCEL else StepKey.SIMPLIFY, *listOfNotNull(common?.let { l(it) }).toTypedArray(),
            form = StepForm.Value(result))
        return steps
    }

    /** What was cancelled between a fraction and its simplest form: the original denominator over the result's, factored. */
    private fun cancelled(original: Expr, result: Expr): Expr? {
        val kernels = Algebra.Kernels()
        val d0 = Algebra.fraction(original, kernels)?.second ?: return null
        val d1 = Algebra.fraction(result, kernels)?.second ?: return null
        val g = d0.divideExactly(d1) ?: return null
        if (g.isConstant) return null
        return factored(Algebra.toExpr(g, kernels))
    }

    /** A denominator as its factors. */
    private fun factored(d: Expr): Expr = Algebra.factor(d)?.first ?: d

    private fun factor(e: Expr, result: Expr): List<Step>? {
        step(reading.ifEmpty { l(e) }, StepKey.START, form = StepForm.Value(e))
        val kernels = Algebra.Kernels()
        val p = Algebra.polynomial(Algebra.expand(e), kernels) ?: return null
        if (!kernels.isEmpty) {
            step(l(result), StepKey.RESULT, form = StepForm.Value(result))
            return steps
        }
        val letters = p.unknowns.toList()
        // Written with brackets, as (x + 2)²: multiplied out first, then factored from there.
        val expanded = Algebra.expand(e)
        if (expanded != e && expanded is Expr.Add) step(l(expanded), StepKey.MULTIPLY_OUT, form = StepForm.Value(expanded))
        // The number and the letters in common.
        val terms = expanded.let { if (it is Expr.Add) it.terms else listOf(it) }
        val trivial = (c.outcome as? Outcome.Factored)?.trivial == true
        // A single term is a product already.
        if (terms.size < 2) {
            step(l(result), if (trivial) StepKey.IRREDUCIBLE else StepKey.RESULT, form = StepForm.Value(result))
            return steps
        }
        val content = commonFactor(terms)
        var rest = Algebra.expand(e)
        var prefix: Expr = Expr.ONE
        if (!isOne(content)) {
            rest = Algebra.expand(rest / content)
            prefix = content
            val shown = if (content == Expr.MINUS_ONE) "-" else l(content)
            step("$shown${paren(rest)}", StepKey.COMMON_FACTOR, l(content), form = StepForm.Value(content * rest))
        }
        val restTerms = if (rest is Expr.Add) rest.terms else listOf(rest)
        if (letters.size <= 2 && restTerms.size in 2..3) {
            binomialOrTrinomial(rest, letters)?.let { (key, args, f) ->
                step(l(prefix * f), key, *args.toTypedArray(), form = StepForm.Value(prefix * f))
            }
        } else if (letters.size > 1 && restTerms.size == 4) {
            step(l(result), StepKey.GROUPING, form = StepForm.Value(result))
        } else if (letters.size == 1) {
            val x = Expr.Sym(letters[0])
            val cs = Solve.coefficientsIn(rest, x)
            // Degree 3 to 6: a rational root r, (x − r) divided out, and the quotient factored where a formula does it.
            if (cs != null && cs.size in 4..7) {
                val roots = Solve.equation(rest, Expr.ZERO, x).values.filter { it is Expr.Num }
                val r = roots.firstOrNull()
                if (r != null) {
                    step("${c.latexName(x.name)} = ${l(r)}", StepKey.RATIONAL_ROOT, l(r))
                    val divisor = x - r
                    val quotient = Algebra.cancel(rest / divisor)?.let { Algebra.expand(it) }
                    if (quotient != null) {
                        step(l(prefix * divisor * quotient), StepKey.POLYNOMIAL_DIVISION, l(divisor), form = StepForm.Value(prefix * divisor * quotient))
                        val quotientTerms = (quotient as? Expr.Add)?.terms?.size ?: 1
                        if (quotientTerms in 2..3) binomialOrTrinomial(quotient, letters)?.let { (key, args, f) ->
                            step(l(prefix * divisor * f), key, *args.toTypedArray(), form = StepForm.Value(prefix * divisor * f))
                        }
                    }
                }
            }
        }
        step(l(result), if (trivial) StepKey.IRREDUCIBLE else StepKey.RESULT, form = StepForm.Value(result))
        return steps
    }

    /** The number (with the first term's sign) and the power of each letter that divide every term. */
    private fun commonFactor(terms: List<Expr>): Expr {
        val kernels = Algebra.Kernels()
        val polys = terms.map { Algebra.polynomial(it, kernels) ?: return Expr.ONE }
        val p = polys.fold(Polynomial.constant(Rational.ZERO)) { a, b -> a + b }
        val product = Factor.of(p)
        var g = num(product.constant)
        for ((f, k) in product.factors) if (f.size == 1) g = g * pow(Algebra.toExpr(f, kernels), num(k.toLong()))
        return g
    }

    /** A² ± 2AB + B², A² − B², or x² + bx + c with whole roots: the formula and its factors. */
    private fun binomialOrTrinomial(rest: Expr, letters: List<String>): Triple<StepKey, List<String>, Expr>? {
        val (product, info) = Algebra.factor(rest) ?: return null
        if (info?.trivial == true) return null
        val factors = if (product is Expr.Mul) product.factors else listOf(product)
        val terms = (rest as? Expr.Add)?.terms ?: return null
        if (factors.size == 1 && product is Expr.Pow && product.exp == Expr.TWO && terms.size == 3) {
            val minus = terms.any { Write.isNegative(it) }
            return Triple(if (minus) StepKey.FACTOR_BINOMIAL_2 else StepKey.FACTOR_BINOMIAL_1, emptyList(), product)
        }
        if (terms.size == 2 && terms.count { Write.isNegative(it) } == 1 && factors.size == 2) {
            return Triple(StepKey.FACTOR_BINOMIAL_3, emptyList(), product)
        }
        if (terms.size == 3 && letters.size == 1) {
            val x = Expr.Sym(letters[0])
            val cs = Solve.coefficientsIn(rest, x) ?: return null
            if (cs.size == 3 && isOne(cs[2])) {
                return Triple(StepKey.TRINOMIAL, listOf(l(cs[0]), l(cs[1])), product)
            }
            if (cs.size == 3) {
                val roots = Solve.equation(rest, Expr.ZERO, x).values
                if (roots.isNotEmpty()) return Triple(StepKey.ROOTS_FACTOR, listOf(roots.joinToString(",\\; ") { l(it) }), product)
            }
        }
        return null
    }

    private fun compare(written: List<Expr>, o: Outcome.Compared): List<Step>? {
        if (written.size != 2) return null
        step(eq(written[0], written[1]), StepKey.START)
        if (o.sides.size == 2) step(eq(o.sides[0], o.sides[1]), StepKey.SIMPLIFY)
        // Where they differ, a few digits as the card gives them.
        val at = o.at?.entries?.joinToString(",\\; ") { (k, d) -> "${c.latexName(k)} = ${l(Expr.Real(Math.round(d * 1000) / 1000.0))}" }
        if (o.holds) step(eq(o.sides[0], o.sides[0]), StepKey.SIDES_EQUAL)
        else step("${l(o.sides[0])} \\neq ${l(o.sides[1])}" + (at?.let { ",\\quad $it" } ?: ""), StepKey.SIDES_DIFFER)
        return steps
    }

    /** A number divided by its primes, smallest first, until 1 is left. */
    private fun primes(o: Outcome.Primes): List<Step>? {
        step(o.n.toString(), StepKey.START)
        var n = o.n.abs()
        for ((p, k) in o.factors) repeat(k) {
            val q = n / p
            step("$n : $p = $q", StepKey.PRIME_DIVIDE, p.toString())
            n = q
        }
        step(c.latex(o), StepKey.RESULT)
        return steps
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Calculus written in the formula.

    private fun calculus(e: Expr, result: Expr): List<Step>? {
        if (e !is Expr.Op) return null
        return when (e.kind) {
            OpKind.DERIVATIVE -> derivative(e.body, e.variable, result, integer(e.a ?: Expr.ONE)?.toInt() ?: 1)
            OpKind.INTEGRAL -> if (e.a == null) integral(e.body, e.variable, result) else definite(e, result)
            OpKind.LIMIT -> limit(e, result)
            else -> null
        }
    }

    /** d/dx of [e], a sum or a product in brackets: d/dx(x² sin x). */
    private fun d(e: Expr, x: Expr.Sym) = "\\frac{d}{d${c.latexName(x.name)}}" +
        if (e is Expr.Mul && e.factors.count { it.has(x) } > 1) bracketed(l(e)) else paren(e)

    /** The derivative of f by its rules; of a higher [order], the first one so, then differentiated again. */
    private fun derivative(f: Expr, x: Expr.Sym, result: Expr, order: Int = 1): List<Step>? {
        step(reading.takeIf { it.isNotEmpty() && !it.contains("=") } ?: d(f, x), StepKey.START)
        if (order < 1 || order > 6) return null
        val first = if (order == 1) result else Calculus.tidy(Calculus.diff(f, x))
        rules(f, x, first)
        var current = first
        for (k in 2..order) {
            current = if (k == order) result else Calculus.tidy(Calculus.diff(current, x))
            step(l(current), StepKey.DERIVATIVE_AGAIN)
        }
        return steps
    }

    /** The first derivative's working: the sum, a constant in front, or the rule for the whole; then its simplest form. */
    private fun rules(f0: Expr, x: Expr.Sym, result: Expr) {
        // A fraction that cancels, cancelled first: (x² − 1)/(x − 1) is x + 1.
        var f = f0
        val cancelled = Algebra.cancel(f)
        if (cancelled != null && cancelled != f && denominatorDegree(cancelled, x) < denominatorDegree(f, x)) {
            f = Algebra.expand(cancelled)
            step(d(f, x), StepKey.SIMPLIFY)
        }
        val (k, g) = constantFactor(f, x)
        when {
            f is Expr.Add -> {
                step(written(f.terms, x).joinToString(" + ") { d(it, x) }, StepKey.SUM_RULE)
                val parts = f.terms.map { Calculus.tidy(Calculus.diff(it, x)) }
                step(l(add(parts)), StepKey.POWER_RULE.takeIf { polynomialIn(f, x) } ?: StepKey.TABLE_DERIVATIVE,
                    form = StepForm.Value(add(parts)))
            }
            !isOne(k) && g.has(x) -> {
                step(l(k) + " \\cdot " + d(g, x), StepKey.CONSTANT_FACTOR)
                rule(g, x, k)
            }
            else -> rule(f, x, Expr.ONE)
        }
        step(l(result), StepKey.SIMPLIFY, form = StepForm.Value(result))
    }

    /** The rule for f (times k): power, product, quotient, chain, or the table. */
    private fun rule(f: Expr, x: Expr.Sym, k: Expr) {
        // The polynomial factor first, as a learner reads x² eˣ: u = x², v = eˣ.
        val factors = (if (f is Expr.Mul) f.factors.filter { it.has(x) } else listOf(f)).sortedBy { !polynomialIn(it, x) }
        val (top, bottom) = factors.partition { !(it is Expr.Pow && rational(it.exp)?.signum == -1 && it.base.has(x)) }
        fun times(e: Expr) = if (isOne(k)) e else k * e
        // What the rule gives, times the number taken out before it.
        fun scaled(s: String, sum: Boolean) = if (isOne(k)) s else l(k) + " \\cdot " + (if (sum) "\\left($s\\right)" else s)
        when {
            bottom.isNotEmpty() && top.isNotEmpty() -> {
                val u = mul(top)
                val v = mul(bottom.map { pow((it as Expr.Pow).base, neg(it.exp)) })
                val du = Calculus.tidy(Calculus.diff(u, x))
                val dv = Calculus.tidy(Calculus.diff(v, x))
                step(scaled("\\frac{${paren(du)} \\cdot ${paren(v)} - ${paren(u)} \\cdot ${paren(dv)}}{${paren(v)}^{2}}", sum = false),
                    StepKey.QUOTIENT_RULE, l(u), l(v), form = StepForm.Value(times((du * v - u * dv) / pow(v, Expr.TWO))))
            }
            factors.size >= 2 -> {
                val u = factors[0]
                val v = mul(factors.drop(1))
                val du = Calculus.tidy(Calculus.diff(u, x))
                val dv = Calculus.tidy(Calculus.diff(v, x))
                step(scaled("${paren(du)} \\cdot ${paren(v)} + ${paren(u)} \\cdot ${paren(dv)}", sum = true), StepKey.PRODUCT_RULE, l(u), l(v),
                    form = StepForm.Value(times(du * v + u * dv)))
            }
            f == x || (f is Expr.Pow && f.base == x && !f.exp.has(x)) -> {
                val r = Calculus.tidy(Calculus.diff(f, x))
                step(l(times(r)), StepKey.POWER_RULE, form = StepForm.Value(times(r)))
            }
            (f is Expr.Pow && f.base.has(x) && f.base != x && !f.exp.has(x)) -> {
                // The outer power's derivative as it stands, its bracket not multiplied out: 5(2x + 1)⁴ · 2.
                val inner = f.base
                val power = Solve.simplifyValue(f.exp - Expr.ONE)
                val di = Calculus.tidy(Calculus.diff(inner, x))
                val outer = mul(f.exp, pow(inner, power))
                val shown = (if (f.exp == Expr.MINUS_ONE) "-" else l(f.exp)) + paren(inner) + (if (isOne(power)) "" else "^{${l(power)}}")
                step(scaled("$shown \\cdot ${paren(di)}", sum = false), StepKey.CHAIN_RULE, l(pow(Expr.Sym("u"), f.exp)), l(inner),
                    form = StepForm.Value(times(outer * di)))
            }
            f is Expr.Fn && f.args.last() != x && f.args.last().has(x) -> {
                val inner = f.args.last()
                val u = Expr.Sym("u")
                val outerFn = Solve.substituteExact(f, inner, u)
                val outer = Calculus.tidy(Calculus.diff(outerFn, u)).substitute(u, inner)
                val di = Calculus.tidy(Calculus.diff(inner, x))
                step(scaled("${paren(outer)} \\cdot ${paren(di)}", sum = false), StepKey.CHAIN_RULE, l(outerFn), l(inner),
                    form = StepForm.Value(times(outer * di)))
            }
            f is Expr.Pow && !f.base.has(x) && f.exp != x && f.exp.has(x) -> {
                val inner = f.exp
                val outer = if (f.base == Expr.E) f else f * ln(f.base)
                val di = Calculus.tidy(Calculus.diff(inner, x))
                step(scaled("${paren(outer)} \\cdot ${paren(di)}", sum = false), StepKey.CHAIN_RULE, l(pow(f.base, Expr.Sym("u"))), l(inner),
                    form = StepForm.Value(times(outer * di)))
            }
            else -> {
                val r = Calculus.tidy(Calculus.diff(f, x))
                step(l(times(r)), StepKey.TABLE_DERIVATIVE, form = StepForm.Value(times(r)))
            }
        }
    }

    private fun integral(f0: Expr, x: Expr.Sym, result: Expr): List<Step>? {
        val dx = "\\,d${c.latexName(x.name)}"
        fun int(e: Expr) = "\\int " + (if (e is Expr.Add) paren(e) else l(e)) + dx
        step(reading.takeIf { it.isNotEmpty() && !it.contains("=") } ?: int(f0), StepKey.START)
        // A fraction that cancels, cancelled first; a product of polynomials multiplied out, where no substitution fits.
        var f = f0
        val cancelled = Algebra.cancel(f)
        if (cancelled != null && cancelled != f && denominatorDegree(cancelled, x) < denominatorDegree(f, x)) {
            f = Algebra.expand(cancelled)
            step(int(f), StepKey.SIMPLIFY)
        } else if (f !is Expr.Add && polynomialIn(f, x) && substitution(f, x) == null && Algebra.expand(f) is Expr.Add) {
            f = Algebra.expand(f)
            step(int(f), StepKey.EXPAND_SIDES)
        }
        val (k, g) = constantFactor(f, x)
        when {
            f is Expr.Add -> {
                step(written(f.terms, x).joinToString(" + ") { int(it) }, StepKey.INTEGRAL_LINEARITY)
                val parts = f.terms.map { Calculus.antiderivative(it, x) ?: return null }
                step(add(parts).let { l(it) }, if (polynomialIn(f, x)) StepKey.INTEGRAL_POWER else StepKey.INTEGRAL_TABLE,
                    form = StepForm.Value(add(parts)))
            }
            // A constant taken out, unless a substitution takes it in: ∫ 2x e^(x²) dx is ∫ eᵘ du.
            !isOne(k) && g.has(x) && substitution(f, x) == null -> {
                step(l(k) + " \\cdot " + int(g), StepKey.CONSTANT_FACTOR)
                integralMethod(g, x, k, result)
            }
            else -> integralMethod(f, x, Expr.ONE, result)
        }
        step(l(result) + " + C", StepKey.PLUS_C)
        return steps
    }

    /** How the integral of f (times k) comes about: power rule, table, substitution, parts, partial fractions. */
    private fun integralMethod(f: Expr, x: Expr.Sym, k: Expr, result: Expr) {
        val dx = "\\,d${c.latexName(x.name)}"
        // A differential: dx for 1 dx, else 2x dx.
        fun differential(e: Expr) = if (isOne(e)) "d${c.latexName(x.name)}" else l(e) + dx
        // What the method gives, times the number taken out before it.
        fun scaled(s: String, sum: Boolean) = if (isOne(k)) s else l(k) + " \\cdot " + (if (sum) "\\left($s\\right)" else s)
        // xⁿ by the power rule; 1/x is the exception, from the table.
        if (f == x || (f is Expr.Pow && f.base == x && !f.exp.has(x) && f.exp != Expr.MINUS_ONE)) {
            step(l(result), StepKey.INTEGRAL_POWER)
            return
        }
        // Partial fractions: distinct linear factors below, by the cover-up rule.
        partialFractions(f, x)?.let { parts ->
            step(scaled("\\int \\left(${signed(parts)}\\right)$dx", sum = false), StepKey.PARTIAL_FRACTIONS)
            return
        }
        // u = g(x) where f is F(g)·g′.
        substitution(f, x)?.let { (g, inU) ->
            val u = Expr.Sym("u")
            val dg = differential(Calculus.tidy(Calculus.diff(g, x)))
            step("u = ${l(g)},\\quad du = $dg:\\quad " + scaled("\\int ${l(inU)}\\,du", sum = false), StepKey.INTEGRAL_SUBSTITUTION, l(g), dg)
            val inner = Calculus.antiderivative(inU, u)
            if (inner != null) step(scaled(l(inner), sum = inner is Expr.Add) + ",\\quad u = ${l(g)}", StepKey.BACK_SUBSTITUTE)
            return
        }
        // Parts: a polynomial times eˣ, sin, cos, or times ln, arctan.
        parts(f, x)?.let { (u, dv) ->
            val vv = Calculus.antiderivative(dv, x) ?: return@let
            val du = Calculus.tidy(Calculus.diff(u, x))
            val remaining = if (isOne(du)) "\\int ${l(vv)}$dx" else "\\int ${paren(vv)} \\cdot ${paren(du)}$dx"
            step(scaled("${paren(u)} \\cdot ${paren(vv)} - $remaining", sum = true), StepKey.INTEGRAL_PARTS,
                l(u), differential(dv))
            return
        }
        step(l(result), StepKey.INTEGRAL_TABLE)
    }

    /** A sum written term by term, a minus for a negative one: -\frac{1}{2(x + 1)} + \frac{1}{2(x - 1)}. */
    private fun signed(terms: List<Expr>): String = terms.withIndex().joinToString("") { (i, t) ->
        val negative = Write.isNegative(t)
        val body = l(if (negative) neg(t) else t)
        when {
            i == 0 -> (if (negative) "-" else "") + body
            negative -> " - $body"
            else -> " + $body"
        }
    }

    /** f = A/(x − r) + B/(x − s) + … for a denominator with distinct linear factors: A = numerator(r)/denominator′(r). */
    private fun partialFractions(f: Expr, x: Expr.Sym): List<Expr>? {
        val kernels = Algebra.Kernels()
        val (n, d) = Algebra.fraction(f, kernels) ?: return null
        if (!kernels.isEmpty || d.isConstant || n.degreeIn(x.name) >= d.degreeIn(x.name) || d.unknowns != setOf(x.name)) return null
        val num = Algebra.toExpr(n, kernels)
        val den = Algebra.toExpr(d, kernels)
        val roots = Solve.equation(den, Expr.ZERO, x)
        // Two linear factors at least: 1/x is no splitting.
        if (roots.values.size < 2 || roots.values.size != d.degreeIn(x.name) || roots.values.any { it !is Expr.Num }) return null
        val dd = Calculus.diff(den, x)
        return roots.values.map { r -> Solve.simplifyValue(num.substitute(x, r) / dd.substitute(x, r)) / (x - r) }
    }

    private fun substitution(f: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        val u = Expr.Sym("u")
        val candidates = LinkedHashSet<Expr>()
        fun walk(t: Expr) {
            when (t) {
                is Expr.Fn -> { if (t.args.last().has(x) && t.args.last() != x) candidates += t.args.last(); if (t.f == Func.SIN || t.f == Func.COS || t.f == Func.LN) candidates += t; t.args.forEach(::walk) }
                is Expr.Pow -> { if (t.base.has(x) && t.base != x) candidates += t.base; if (t.exp.has(x) && t.exp != x) candidates += t.exp; walk(t.base); walk(t.exp) }
                is Expr.Add -> t.terms.forEach(::walk)
                is Expr.Mul -> t.factors.forEach(::walk)
                else -> Unit
            }
        }
        walk(f)
        for (g in candidates.sortedByDescending { it.size() }) {
            val dg = Calculus.diff(g, x)
            if (isZero(dg)) continue
            // Divided by a number as it stands, so that (x + 2)² keeps its bracket for u = x + 2.
            val ratio = if (dg.isConstant()) f / dg else try { Algebra.simplify(f / dg) } catch (e: CalcException) { continue }
            val inU = Solve.substituteExact(ratio, g, u)
            if (inU.has(x)) continue
            if (Calculus.antiderivative(inU, u) == null) continue
            return g to inU
        }
        return null
    }

    private fun parts(f: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        val factors = if (f is Expr.Mul) f.factors else listOf(f)
        val poly = factors.filter { Solve.coefficientsIn(Algebra.expand(it), x) != null && it.has(x) }
        val others = factors.filter { it !in poly && it.has(x) }
        if (others.size != 1) return null
        val t = others[0]
        val p = mul(poly + factors.filter { !it.has(x) })
        // u is the logarithm or inverse function; or, beside a polynomial, dv is the exponential, sine or cosine.
        val exponential = t is Expr.Pow && !t.base.has(x) && Solve.coefficientsIn(Algebra.expand(t.exp), x)?.size == 2
        val trigonometric = t is Expr.Fn && (t.f == Func.SIN || t.f == Func.COS) && Solve.coefficientsIn(Algebra.expand(t.args.last()), x)?.size == 2
        return when {
            t is Expr.Fn && t.f in setOf(Func.LN, Func.LOG, Func.ATAN, Func.ASIN, Func.ACOS) -> t to p
            poly.isNotEmpty() && (exponential || trigonometric) -> p to t
            else -> null
        }
    }

    private fun definite(o: Expr.Op, result: Expr): List<Step>? {
        val x = o.variable
        val f = Calculus.antiderivative(o.body, x) ?: return null
        val a = o.a ?: return null
        val b = o.b ?: return null
        step(reading.takeIf { it.isNotEmpty() && !it.contains("=") } ?: l(o), StepKey.START)
        step("\\left[${l(f)}\\right]_{${l(a)}}^{${l(b)}}", StepKey.INTEGRAL_TABLE)
        if (!a.hasInfinity() && !b.hasInfinity()) {
            // F(b) − F(a): F(a) in brackets where it is a sum or negative.
            val lower = Solve.simplifyValue(f.substitute(x, a))
            val subtracted = if (lower is Expr.Add || Write.isNegative(lower)) paren(lower) else l(lower)
            step("${l(Solve.simplifyValue(f.substitute(x, b)))} - $subtracted", StepKey.DEFINITE_BOUNDS, l(f))
        }
        step(v(result), StepKey.RESULT)
        return steps
    }

    private fun limit(o: Expr.Op, result: Expr): List<Step>? {
        val x = o.variable
        val a = o.a ?: return null
        val f = o.body
        step(reading.takeIf { it.isNotEmpty() && !it.contains("=") } ?: l(o), StepKey.START)
        val point = l(a)
        // From one side where the formula says so: x → 0⁺.
        val side = when (o.b?.let { rational(it)?.signum }) { -1 -> "^{-}"; 1 -> "^{+}"; else -> "" }
        val lim = "\\lim_{${c.latexName(x.name)} \\to $point$side} "
        val direct = try { Calculus.tidy(f.substitute(x, a)) } catch (e: CalcException) { null }
        if (direct != null && !a.hasInfinity() && Numeric.value(direct)?.isFinite() == true) {
            step(l(direct), StepKey.LIMIT_SUBSTITUTE, point)
        } else if (a.hasInfinity()) {
            val kernels = Algebra.Kernels()
            val fr = Algebra.fraction(f, kernels)
            if (fr != null && kernels.isEmpty && !fr.second.isConstant) {
                val n = maxOf(fr.first.degreeIn(x.name), fr.second.degreeIn(x.name))
                val power = pow(x, num(n.toLong()))
                // Each term divided by xⁿ, the one that stays first: 2 + 1/x² over 1 − 3/x².
                fun divided(p: Polynomial): String {
                    val e = Algebra.expand(Algebra.toExpr(p, kernels) / power)
                    val terms = (if (e is Expr.Add) e.terms else listOf(e)).sortedByDescending { degree(it, x) }
                    return signed(terms)
                }
                step(lim + "\\frac{${divided(fr.first)}}{${divided(fr.second)}}", StepKey.LIMIT_HIGHEST_POWER, l(power))
            }
        } else {
            val factors = if (f is Expr.Mul) f.factors else listOf(f)
            val top = mul(factors.filter { !(it is Expr.Pow && rational(it.exp)?.signum == -1) })
            val bottom = mul(factors.filter { it is Expr.Pow && rational(it.exp)?.signum == -1 }.map { pow((it as Expr.Pow).base, neg(it.exp)) })
            fun at(t: Expr) = try { Numeric.value(Solve.simplifyValue(t.substitute(x, a))) } catch (e: CalcException) { null }
            // Only 0/0 is undecided; 1/0 and the like go to the result straight away.
            if (at(top)?.let { abs(it) < 1e-12 } == true && at(bottom)?.let { abs(it) < 1e-12 } == true) {
                step("\\frac{0}{0}", StepKey.LIMIT_INDETERMINATE, "\\frac{0}{0}")
                val cancelled = Algebra.cancel(f)
                if (cancelled != null && cancelled != f && Algebra.Kernels().let { k -> Algebra.fraction(f, k) != null && k.isEmpty }) {
                    step(lim + paren(cancelled), StepKey.LIMIT_CANCEL)
                } else {
                    val dt = Calculus.tidy(Calculus.diff(top, x))
                    val db = Calculus.tidy(Calculus.diff(bottom, x))
                    step(lim + "\\frac{${l(dt)}}{${l(db)}}", StepKey.LHOPITAL)
                }
            }
        }
        step(v(result), StepKey.RESULT)
        return steps
    }

    // ---------------------------------------------------------------------------------------------------------------------
    // Looking at formulas.

    /** [e] in brackets where it needs them, as the card writes them: growing with a fraction or a root inside. */
    private fun paren(e: Expr): String {
        val s = l(e)
        if (!(e is Expr.Add || Write.isNegative(e) || (e is Expr.Num && !e.value.isInteger))) return s
        return bracketed(s)
    }

    private fun bracketed(s: String) = if (listOf("\\frac", "\\sqrt", "\\sum", "\\int").any { it in s }) "\\left($s\\right)" else "($s)"

    /** A sum's terms as a learner writes them: the highest power of x first, numbers last. */
    private fun written(terms: List<Expr>, x: Expr.Sym): List<Expr> = terms.sortedByDescending { t ->
        if (!t.has(x)) -1 else (Solve.coefficientsIn(Algebra.expand(t), x)?.size ?: 2)
    }

    /** f as a constant, all its factors without x, times the rest: y · x² for x²y. */
    private fun constantFactor(f: Expr, x: Expr.Sym): Pair<Expr, Expr> =
        if (f is Expr.Mul) mul(f.factors.filter { !it.has(x) }) to mul(f.factors.filter { it.has(x) }) else Expr.ONE to f

    /** The degree in x of [e]'s denominator as one fraction of polynomials; 0 for a polynomial or anything else. */
    private fun denominatorDegree(e: Expr, x: Expr.Sym): Int {
        val kernels = Algebra.Kernels()
        val fraction = Algebra.fraction(e, kernels) ?: return 0
        return if (kernels.isEmpty) fraction.second.degreeIn(x.name) else 0
    }

    /** The power of x in a term: 2 in 3x², −2 in 1/x², 0 in a number. */
    private fun degree(t: Expr, x: Expr.Sym): Double = when {
        t == x -> 1.0
        t is Expr.Pow && t.base == x -> Numeric.value(t.exp) ?: 0.0
        t is Expr.Mul -> t.factors.sumOf { degree(it, x) }
        else -> 0.0
    }

    private fun polynomialIn(e: Expr, x: Expr.Sym): Boolean = Solve.coefficientsIn(Algebra.expand(e), x)?.all { !it.has(x) } == true

    private fun occurrences(e: Expr, x: Expr.Sym): Int = when (e) {
        is Expr.Sym -> if (e == x) 1 else 0
        is Expr.Add -> e.terms.sumOf { occurrences(it, x) }
        is Expr.Mul -> e.factors.sumOf { occurrences(it, x) }
        is Expr.Pow -> occurrences(e.base, x) + occurrences(e.exp, x)
        is Expr.Fn -> e.args.sumOf { occurrences(it, x) }
        else -> 0
    }

    /** The bases of the powers with x below a fraction bar in [f]. */
    private fun denominators(f: Expr, x: Expr.Sym): List<Expr> {
        val out = ArrayList<Expr>()
        fun walk(e: Expr) {
            when (e) {
                is Expr.Pow -> {
                    if (e.base.has(x) && rational(e.exp)?.let { it.signum < 0 } == true) out += pow(e.base, num(-rational(e.exp)!!))
                    walk(e.base)
                }
                is Expr.Add -> e.terms.forEach(::walk)
                is Expr.Mul -> e.factors.forEach(::walk)
                is Expr.Fn -> Unit
                else -> Unit
            }
        }
        walk(f)
        return out.distinct()
    }

    private fun hasAbs(f: Expr, x: Expr.Sym): Boolean = when (f) {
        is Expr.Fn -> (f.f == Func.ABS && f.args[0].has(x)) || f.args.any { hasAbs(it, x) }
        is Expr.Add -> f.terms.any { hasAbs(it, x) }
        is Expr.Mul -> f.factors.any { hasAbs(it, x) }
        is Expr.Pow -> hasAbs(f.base, x)
        else -> false
    }

    private fun containsAbs(e: Expr): Boolean = when (e) {
        is Expr.Fn -> e.f == Func.ABS
        is Expr.Mul -> e.factors.any { containsAbs(it) }
        else -> false
    }

    /** The one root of something with x in f, and what it equals when alone: (root, value). */
    private fun radicalTerm(f: Expr, x: Expr.Sym): Pair<Expr, Expr>? {
        val terms = if (f is Expr.Add) f.terms else listOf(f)
        val roots = terms.filter { t ->
            val (_, rest) = splitCoefficient(t)
            rest is Expr.Pow && rest.base.has(x) && rational(rest.exp)?.let { !it.isInteger && it.num.abs() == java.math.BigInteger.ONE } == true
        }
        if (roots.size != 1) return null
        val t = roots[0]
        val (k, rest) = splitCoefficient(t)
        val others = add(terms.filter { it !== t })
        return rest to neg(others) / k
    }

    /** The one subexpression with x that x occurs only in (eˣ in e²ˣ − 3eˣ + 2, sin x), if exactly one. */
    private fun singleKernel(e: Expr, x: Expr.Sym): Expr? {
        val kernels = Algebra.Kernels()
        val p = Algebra.polynomial(e, kernels) ?: return null
        val names = p.unknowns.filter { kernels.expr(it).has(x) }
        if (x.name in p.unknowns || names.size > 2 || names.isEmpty()) return null
        if (names.size == 1) return kernels.expr(names[0])
        // e^(2x) and e^x: the smaller one.
        val (a, b) = names.map { kernels.expr(it) }
        if (a is Expr.Pow && b is Expr.Pow && a.base == b.base) {
            val ra = splitCoefficient(a.exp)
            val rb = splitCoefficient(b.exp)
            if (ra.second == rb.second) return if (abs(numValue(ra.first)) <= abs(numValue(rb.first))) a else b
        }
        return null
    }

    /** [e] with [kernel] as u, its powers (e^(2x) = (eˣ)²) included; null where some x is left. */
    private fun substituteKernel(e: Expr, kernel: Expr, x: Expr.Sym, u: Expr.Sym): Expr? {
        fun walk(t: Expr): Expr {
            if (t == kernel) return u
            if (kernel is Expr.Pow && t is Expr.Pow && t.base == kernel.base) {
                val (kt, gt) = splitCoefficient(t.exp)
                val (kk, gk) = splitCoefficient(kernel.exp)
                val ratio = rational(kt)?.let { a -> rational(kk)?.let { a / it } }
                if (gt == gk && ratio != null && ratio.isInteger) return pow(u, num(ratio))
            }
            return t.mapChildren(::walk)
        }
        return walk(e).takeIf { !it.has(x) }
    }
}
